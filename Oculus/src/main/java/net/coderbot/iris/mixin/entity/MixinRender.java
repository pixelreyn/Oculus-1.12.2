package net.coderbot.iris.mixin.entity;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Render.class)
public class MixinRender {
    @Inject(method = "renderShadow",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void iris$cancelVanillaShadow(Entity entityIn, double x, double y, double z,
                                          float shadowAlpha, float partialTicks, CallbackInfo ci) {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows()) {
            ci.cancel();
        }
    }
}
