package net.coderbot.iris.mixin;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into RenderGlobal to set rendering phases for the Iris pipeline.
 * This tells the pipeline what type of geometry is currently being rendered,
 * so it can bind the correct shader program.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    /**
     * Before rendering a terrain block layer: set the appropriate phase and
     * call beginTranslucents() if this is the translucent pass.
     */
    @Inject(method = "renderBlockLayer", at = @At("HEAD"))
    private void iris$onRenderBlockLayerStart(BlockRenderLayer layer, double partialTicks, int pass,
                                               Entity entity, CallbackInfoReturnable<Integer> cir) {
        // Don't interfere during shadow rendering
        if (ShadowRenderer.ACTIVE) return;

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        // Before the translucent layer, run deferred passes then bind water shader
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            pipeline.beginTranslucents();
        }

        // Set the rendering phase based on the block layer
        WorldRenderingPhase phase = WorldRenderingPhase.fromBlockRenderLayer(layer);
        pipeline.setPhase(phase);
        pipeline.syncProgram();
    }

    /**
     * After rendering a terrain block layer: reset the phase.
     */
    @Inject(method = "renderBlockLayer", at = @At("RETURN"))
    private void iris$onRenderBlockLayerEnd(BlockRenderLayer layer, double partialTicks, int pass,
                                             Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (ShadowRenderer.ACTIVE) return;

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
    }

    /**
     * Before rendering entities: set the ENTITIES phase.
     */
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void iris$onRenderEntitiesStart(Entity renderViewEntity, ICamera camera,
                                             float partialTicks, CallbackInfo ci) {
        if (ShadowRenderer.ACTIVE) return;

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.ENTITIES);
        pipeline.syncProgram();
    }

    /**
     * Before block entities render (inside renderEntities): switch to BLOCK_ENTITIES phase.
     */
    @Inject(method = "renderEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/tileentity/TileEntityRendererDispatcher;preDrawBatch()V"))
    private void iris$onBlockEntitiesStart(Entity renderViewEntity, ICamera camera,
                                            float partialTicks, CallbackInfo ci) {
        if (ShadowRenderer.ACTIVE) return;

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
        pipeline.syncProgram();
    }

    /**
     * After rendering entities: reset the phase.
     */
    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void iris$onRenderEntitiesEnd(Entity renderViewEntity, ICamera camera,
                                           float partialTicks, CallbackInfo ci) {
        if (ShadowRenderer.ACTIVE) return;

        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
    }

    /**
     * Before rendering the sky: set the SKY phase.
     * Target the 2-arg overload: renderSky(float, int) not renderSky(BufferBuilder, float, boolean)
     */
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"))
    private void iris$onRenderSkyStart(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.SKY);
        pipeline.syncProgram();
    }

    /**
     * After rendering the sky: reset the phase.
     */
    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void iris$onRenderSkyEnd(float partialTicks, int pass, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
    }

    /**
     * Before rendering clouds: set the CLOUDS phase.
     */
    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void iris$onRenderCloudsStart(float partialTicks, int pass, double x, double y, double z,
                                           CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        pipeline.syncProgram();
    }

    /**
     * After rendering clouds: reset the phase.
     */
    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void iris$onRenderCloudsEnd(float partialTicks, int pass, double x, double y, double z,
                                         CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return;

        pipeline.setPhase(WorldRenderingPhase.NONE);
        pipeline.syncProgram();
    }
}
