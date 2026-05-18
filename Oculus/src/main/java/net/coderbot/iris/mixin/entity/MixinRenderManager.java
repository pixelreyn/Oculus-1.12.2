package net.coderbot.iris.mixin.entity;

import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;

/**
 * Tracks which entity is currently being rendered for the entityId uniform.
 */
@Mixin(RenderManager.class)
public class MixinRenderManager {

    @Inject(method = "renderEntity",
            at = @At("HEAD"))
    private void iris$beforeRenderEntity(Entity entity, double x, double y, double z,
                                          float yaw, float partialTicks, boolean debug,
                                          CallbackInfo ci) {
        if (entity == null) return;

        Object2IntFunction<NamespacedId> entityIds = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIds == null) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(0);
            return;
        }

        // Get the entity's registry name
        ResourceLocation regName = net.minecraftforge.fml.common.registry.EntityRegistry.getEntry(entity.getClass()) != null
                ? net.minecraftforge.fml.common.registry.EntityRegistry.getEntry(entity.getClass()).getRegistryName()
                : null;

        if (regName != null) {
            NamespacedId id = new NamespacedId(regName.getNamespace(), regName.getPath());
            int entityId = entityIds.getInt(id);
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
        } else {
            CapturedRenderingState.INSTANCE.setCurrentEntity(0);
        }
    }

    @Inject(method = "renderEntity",
            at = @At("RETURN"))
    private void iris$afterRenderEntity(Entity entity, double x, double y, double z,
                                         float yaw, float partialTicks, boolean debug,
                                         CallbackInfo ci) {
        CapturedRenderingState.INSTANCE.setCurrentEntity(0);
    }
}
