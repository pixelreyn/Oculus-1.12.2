package net.coderbot.iris.mixin;

import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.Camera;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.vendored.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/**
 * Hooks into EntityRenderer to drive the Iris rendering pipeline lifecycle.
 * <p>
 * The rendering flow in MC 1.12.2's renderWorldPass:
 * 1. Camera setup → we capture matrices here
 * 2. Sky rendering
 * 3. Terrain (SOLID, CUTOUT_MIPPED, CUTOUT)
 * 4. Entities
 * 5. Particles, weather
 * 6. Translucent terrain
 * 7. Hand
 */
@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    private static final FloatBuffer iris$MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    /**
     * At the start of renderWorldPass: initialize the pipeline for this frame.
     */
    @Inject(method = "renderWorldPass", at = @At("HEAD"))
    private void iris$beginLevelRendering(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        // Ensure deferred pipeline init happens on the first render frame
        Iris.ensurePipelineReady();

        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.beginLevelRendering();

            // Render shadow maps before the main world pass
            Camera camera = new Camera(partialTicks);
            pipeline.renderShadows(
                    (LevelRendererAccessor) Minecraft.getMinecraft().renderGlobal,
                    camera);
        }
    }

    /**
     * Capture the GL modelview and projection matrices right after camera setup,
     * BEFORE sky rendering begins. The camera matrix is fully set up after
     * setupCameraTransform returns. Capturing here (instead of before setupTerrain)
     * ensures sky shaders get correct view matrices.
     */
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V",
                    shift = At.Shift.AFTER))
    private void iris$captureMatrices(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        iris$updateMatrices();

        // Update ActiveRenderInfo's cached matrices so Vintagium's GameRendererContext
        // picks up the correct camera MVP (not stale shadow matrices from the shadow pass)
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.ActiveRenderInfo.updateRenderInfo(
                mc.getRenderViewEntity(),
                mc.gameSettings.thirdPersonView == 2);
    }

    /**
     * Read the current GL matrices and store them for shader uniforms.
     * Called from multiple injection points.
     */
    private static void iris$updateMatrices() {
        // Read GL modelview matrix
        iris$MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, iris$MATRIX_BUFFER);
        iris$MATRIX_BUFFER.rewind();
        Matrix4f modelView = new Matrix4f();
        modelView.set(iris$MATRIX_BUFFER);
        CapturedRenderingState.INSTANCE.setGbufferModelView(modelView);

        // Read GL projection matrix
        iris$MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, iris$MATRIX_BUFFER);
        iris$MATRIX_BUFFER.rewind();
        Matrix4f projection = new Matrix4f();
        projection.set(iris$MATRIX_BUFFER);
        CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
    }

    /**
     * Before lit particles: set PARTICLES phase (tracking only, no shader override).
     * Particles use vanilla rendering - binding a gbuffer shader breaks their
     * texture/blend state and they render as white cubes.
     */
    /**
     * Before block damage overlay: set DESTROY phase.
     */
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V"))
    private void iris$beginDestroy(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(net.coderbot.iris.pipeline.WorldRenderingPhase.DESTROY);
            pipeline.syncProgram();
        }
    }

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V"))
    private void iris$beginParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(net.coderbot.iris.pipeline.WorldRenderingPhase.PARTICLES);
            pipeline.syncProgram();
        }
    }

    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V"))
    private void iris$beginWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(net.coderbot.iris.pipeline.WorldRenderingPhase.RAIN_SNOW);
        }
    }

    /**
     * Before world border: set WORLD_BORDER phase.
     */
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderWorldBorder(Lnet/minecraft/entity/Entity;F)V"))
    private void iris$beginWorldBorder(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(net.coderbot.iris.pipeline.WorldRenderingPhase.WORLD_BORDER);
        }
    }

    /**
     * Before hand rendering: notify the pipeline to copy depth and set hand phase.
     */
    @Inject(method = "renderWorldPass",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V"))
    private void iris$beginHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            // Reset weather phase before hand
            pipeline.setPhase(net.coderbot.iris.pipeline.WorldRenderingPhase.NONE);
            pipeline.syncProgram();
            pipeline.beginHand();
        }
    }

    /**
     * At the end of renderWorldPass: finalize pipeline (run composite/final passes).
     */
    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void iris$finalizeLevelRendering(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null) {
            pipeline.finalizeLevelRendering();
        }
    }
}
