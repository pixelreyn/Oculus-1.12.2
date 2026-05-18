package net.coderbot.iris.pipeline;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL13;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PipelineManager {
    private static PipelineManager instance;
    private final Function<NamespacedId, WorldRenderingPipeline> pipelineFactory;
    private final Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
    private WorldRenderingPipeline pipeline = new FixedFunctionWorldRenderingPipeline();
    private int versionCounterForSodiumShaderReload = 0;

    public PipelineManager(Function<NamespacedId, WorldRenderingPipeline> pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    public WorldRenderingPipeline preparePipeline(NamespacedId currentDimension) {
        if (!pipelinesPerDimension.containsKey(currentDimension)) {
            SystemTimeUniforms.COUNTER.reset();
            SystemTimeUniforms.TIMER.reset();

            Iris.logger.info("Creating pipeline for dimension {}", currentDimension);
            pipeline = pipelineFactory.apply(currentDimension);
            pipelinesPerDimension.put(currentDimension, pipeline);

            if (BlockRenderingSettings.INSTANCE.isReloadRequired()) {
                // In 1.12.2, use renderGlobal.loadRenderers() instead of levelRenderer.allChanged()
                if (Minecraft.getMinecraft().renderGlobal != null) {
                    Minecraft.getMinecraft().renderGlobal.loadRenderers();
                }

                BlockRenderingSettings.INSTANCE.clearReloadRequired();
            }
        } else {
            pipeline = pipelinesPerDimension.get(currentDimension);
        }

        return pipeline;
    }

    @Nullable
    public WorldRenderingPipeline getPipelineNullable() {
        return pipeline;
    }

    public Optional<WorldRenderingPipeline> getPipeline() {
        return Optional.ofNullable(pipeline);
    }

    /**
     * In IrisChunkProgramOverrides#getProgramOverride,
     * it uses version counter to check whether to reload sodium shaders.
     * This fixes a compat issue with Immersive Portals(#1188).
     * Immersive Portals may load multiple client dimensions at the same time,
     * and every dimension corresponds to a IrisChunkProgramOverrides object.
     * Multiple dimensions (mod dimensions that fallback to overworld shaders) may use the same pipeline.
     * This ensures that the sodium shader for each dimension will get properly reloaded.
     */
    public int getVersionCounterForSodiumShaderReload() {
        return versionCounterForSodiumShaderReload;
    }

    /**
     * Destroys all the current pipelines.
     *
     * <p>This method is <b>EXTREMELY DANGEROUS!</b> It is a huge potential source of hard-to-trace inconsistencies
     * in program state. You must make sure that you <i>immediately</i> re-prepare the pipeline after destroying
     * it to prevent the program from falling into an inconsistent state.</p>
     *
     * <p>In particular, </p>
     *
     * @see <a href="https://github.com/IrisShaders/Iris/issues/1330">this GitHub issue</a>
     */
    public void destroyPipeline() {
        pipelinesPerDimension.forEach((dimensionId, pipeline) -> {
            Iris.logger.info("Destroying pipeline {}", dimensionId);
            resetTextureState();
            pipeline.destroy();
        });

        pipelinesPerDimension.clear();
        pipeline = null;
        versionCounterForSodiumShaderReload++;
    }

    private void resetTextureState() {
        // Unbind all textures
        //
        // This is necessary because we don't want destroyed render target textures to remain bound to certain texture
        // units. Vanilla appears to properly rebind all textures as needed, and we do so too, so this does not cause
        // issues elsewhere.
        //
        // Without this code, there will be weird issues when reloading certain shaderpacks.
        // Use raw GL calls since GlStateManager only supports 8 texture units in 1.12.2
        for (int i = 0; i < 16; i++) {
            OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager.bindTexture(0);
        }

        OpenGlHelper.setActiveTexture(GL13.GL_TEXTURE0);
    }
}
