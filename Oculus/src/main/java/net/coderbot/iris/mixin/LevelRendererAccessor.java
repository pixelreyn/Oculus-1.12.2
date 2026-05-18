package net.coderbot.iris.mixin;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Mixin accessor for RenderGlobal (the 1.12.2 equivalent of LevelRenderer).
 * Provides access to rendering internals needed by Iris.
 */
@Mixin(RenderGlobal.class)
public interface LevelRendererAccessor {
    @Accessor("world")
    WorldClient getWorld();

    @Accessor("renderManager")
    RenderManager getRenderManager();

    @Accessor("countEntitiesRendered")
    int getCountEntitiesRendered();

    @Accessor("countEntitiesRendered")
    void setCountEntitiesRendered(int count);

    @Accessor("renderEntitiesStartupCounter")
    int getRenderEntitiesStartupCounter();

    @Accessor("renderEntitiesStartupCounter")
    void setRenderEntitiesStartupCounter(int counter);

    @Accessor("cloudTickCounter")
    int getCloudTickCounter();

    /**
     * In 1.12.2, there's no direct setupRender equivalent.
     * Chunk rendering is handled differently.
     */

    /**
     * Invoke render chunk layer for terrain rendering.
     * In 1.12.2, this would be renderBlockLayer.
     */
    @Invoker("renderBlockLayer")
    int invokeRenderBlockLayer(BlockRenderLayer layer, double partialTicks, int pass, Entity entity);

    @Invoker("renderEntities")
    void invokeRenderEntities(Entity renderViewEntity, net.minecraft.client.renderer.culling.ICamera camera, float partialTicks);
}
