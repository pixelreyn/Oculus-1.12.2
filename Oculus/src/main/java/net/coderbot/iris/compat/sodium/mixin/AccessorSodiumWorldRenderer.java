package net.coderbot.iris.compat.sodium.mixin;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Vintagium's private {@code chunkRenderManager} so the shadow pass can
 * re-run chunk visibility with a light-space (shadow-distance) frustum instead
 * of reusing the camera-frustum chunk list.
 */
@Mixin(SodiumWorldRenderer.class)
public interface AccessorSodiumWorldRenderer {
    @Accessor("chunkRenderManager")
    ChunkRenderManager<?> getChunkRenderManager();
}
