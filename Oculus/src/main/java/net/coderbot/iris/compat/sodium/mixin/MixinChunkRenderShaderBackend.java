package net.coderbot.iris.compat.sodium.mixin;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.impl.IrisChunkProgram;
import net.coderbot.iris.compat.sodium.impl.IrisChunkProgramOverrides;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Vintagium/Sodium's shader backend to replace the terrain shader
 * with Iris's patched gbuffer shader when the Iris pipeline is active.
 */
@Mixin(ChunkRenderShaderBackend.class)
public class MixinChunkRenderShaderBackend {
    @Shadow(remap = false)
    protected ChunkProgram activeProgram;

    @Unique
    private static final IrisChunkProgramOverrides iris$overrides = new IrisChunkProgramOverrides();

    @Unique
    private IrisChunkProgram iris$activeOverride;

    @Unique
    private int iris$savedProgram;

    /**
     * Before Sodium binds its shader, save the currently active Iris program.
     */
    @Inject(method = "begin", at = @At("HEAD"), remap = false)
    private void iris$beforeBegin(CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            iris$savedProgram = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        }
    }

    /**
     * After Sodium sets up its program in begin(), check if we should
     * replace it with an Iris override program.
     */
    @Inject(method = "begin", at = @At("RETURN"), remap = false)
    private void iris$afterBegin(CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        // During shadow rendering, use the shadow shader override
        if (net.coderbot.iris.pipeline.ShadowRenderer.ACTIVE) {
            IrisChunkProgram shadowOverride = iris$overrides.getShadowProgramOverride(RenderDevice.INSTANCE);
            if (shadowOverride != null) {
                iris$activeOverride = shadowOverride;
                shadowOverride.bind();
                ChunkVertexType vt = ((ChunkRenderShaderBackend<?>)(Object)this).getVertexType();
                shadowOverride.setup(vt.getModelScale(), vt.getTextureScale());
            } else {
                iris$activeOverride = null;
            }
            return;
        }

        // Determine if this is a translucent pass by checking the current pipeline phase
        boolean translucent = false;
        if (pipeline.getPhase() == net.coderbot.iris.pipeline.WorldRenderingPhase.TERRAIN_TRANSLUCENT
                || pipeline.getPhase() == net.coderbot.iris.pipeline.WorldRenderingPhase.TRIPWIRE) {
            translucent = true;
        }
        IrisChunkProgram override = iris$overrides.getProgramOverride(RenderDevice.INSTANCE, translucent);

        if (override != null) {
            iris$activeOverride = override;
            override.bind();
            ChunkVertexType vt = ((ChunkRenderShaderBackend<?>)(Object)this).getVertexType();
            override.setup(vt.getModelScale(), vt.getTextureScale());
        } else {
            iris$activeOverride = null;
        }
    }

    /**
     * After Sodium unbinds its program in end(), restore the Iris program.
     */
    @Inject(method = "end", at = @At("RETURN"), remap = false)
    private void iris$afterEnd(CallbackInfo ci) {
        if (iris$activeOverride != null) {
            iris$activeOverride = null;
        }
        // Always restore the Iris program that was active before Sodium's begin()
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null && iris$savedProgram != 0) {
            org.lwjgl.opengl.GL20.glUseProgram(iris$savedProgram);
            iris$savedProgram = 0;
        }
    }
}
