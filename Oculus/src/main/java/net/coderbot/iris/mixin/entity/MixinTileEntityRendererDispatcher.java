package net.coderbot.iris.mixin.entity;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;

/**
 * Tracks which block entity is currently being rendered for the blockEntityId uniform.
 */
@Mixin(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
            at = @At("HEAD"))
    private void iris$beforeRenderBlockEntity(TileEntity te, float partialTicks, int destroyStage, CallbackInfo ci) {
        if (te == null) return;

        Object2IntFunction<NamespacedId> entityIds = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIds == null) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
            return;
        }

        // Get the block entity's registry name
        ResourceLocation regName = TileEntity.getKey(te.getClass());
        if (regName != null) {
            NamespacedId id = new NamespacedId(regName.getNamespace(), regName.getPath());
            int entityId = entityIds.getInt(id);
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(entityId);
        } else {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        }
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V",
            at = @At("RETURN"))
    private void iris$afterRenderBlockEntity(TileEntity te, float partialTicks, int destroyStage, CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
    }
}
