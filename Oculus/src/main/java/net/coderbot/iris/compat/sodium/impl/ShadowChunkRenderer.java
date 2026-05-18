package net.coderbot.iris.compat.sodium.impl;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.mixin.AccessorSodiumWorldRenderer;
import net.coderbot.iris.shadows.frustum.fallback.NonCullingFrustum;

/**
 * Drives Vintagium's chunk visibility for the shadow pass.
 *
 * <p>{@code renderShadows} runs at {@code renderWorldPass} HEAD, before Sodium's
 * once-per-frame {@code setupTerrain}/chunk-graph update. Without intervention the
 * shadow map would be drawn from the <em>previous frame's camera-frustum</em>
 * chunk list, producing shadows only where the camera recently looked.
 *
 * <p>{@link #rebuildForShadow} forces a chunk-graph rebuild using a
 * shadow-distance box frustum (360° around the player, occlusion culling off) so
 * every section that can cast a shadow is submitted. {@link #markCameraDirty}
 * then flags the manager dirty so the subsequent camera {@code setupTerrain}
 * rebuilds the proper camera list for the main pass even if the player is
 * stationary.
 */
public final class ShadowChunkRenderer {
    private ShadowChunkRenderer() {}

    // Vanilla frame numbers are always >= 0; shadow rebuilds use a distinct,
    // ever-changing negative value so Sodium's BFS never treats nodes touched by
    // the shadow pass as "already visited" during the camera pass (or vice versa).
    private static int shadowFrame = -2;

    private static ChunkRenderManager<?> getManager() {
        SodiumWorldRenderer swr = SodiumWorldRenderer.getInstanceNullable();
        if (swr == null) {
            return null;
        }
        return ((AccessorSodiumWorldRenderer) swr).getChunkRenderManager();
    }

    /**
     * Rebuilds Sodium's per-pass chunk render lists for the shadow camera.
     *
     * @return true if the rebuild ran (so the caller knows to restore the camera
     *         list afterwards via {@link #markCameraDirty}).
     */
    public static boolean rebuildForShadow(double camX, double camY, double camZ,
                                           float ticks, double shadowDistance) {
        try {
            ChunkRenderManager<?> crm = getManager();
            if (crm == null) {
                return false;
            }

            // A camera-centred BoxCuller cubes the Y axis too: fly up and the
            // world falls outside the cube, so terrain stops being rendered
            // into the shadow map (hard bright wedge + shadows that shift with
            // altitude). The shadow camera must cover all loaded sections
            // around the player regardless of height, so use a non-culling
            // frustum (Sodium's BFS still bounds it to render distance).
            // shadowDistance kept in the signature for a future
            // AdvancedShadowCullingFrustum (perf) pass.
            NonCullingFrustum frustum = new NonCullingFrustum();

            // BFS origin: use the shadow camera position rather than last frame's.
            crm.setCameraPosition(camX, camY, camZ);
            crm.markDirty();

            int sf = shadowFrame--;
            if (shadowFrame < -1_000_000_000) {
                shadowFrame = -2;
            }

            // FrustumExtended is mixed into net.minecraft Frustum at runtime;
            // NonCullingFrustum overrides fastAabbTest -> true (all sections).
            // spectator=true disables inter-chunk occlusion culling so occluders
            // behind hills/the camera still populate the shadow list.
            //
            // crm.update touches Sodium's RenderDevice, which throws outside a
            // managed-code scope. renderShadows runs at renderWorldPass HEAD,
            // before Sodium's own enterManagedCode in setupTerrain, so we must
            // establish the scope ourselves (mirrors MixinWorldRenderer).
            RenderDevice.enterManagedCode();
            try {
                crm.update(ticks, (FrustumExtended) frustum, sf, true);
            } finally {
                RenderDevice.exitManagedCode();
            }
            return true;
        } catch (Throwable t) {
            Iris.logger.error("[Shadow] Failed to rebuild Vintagium chunk list for shadow pass", t);
            return false;
        }
    }

    /**
     * Flags Sodium's chunk manager dirty so the camera {@code setupTerrain} that
     * runs after the shadow pass rebuilds the camera-frustum list for the main
     * pass (it otherwise skips the rebuild when the player hasn't moved).
     */
    public static void markCameraDirty() {
        try {
            ChunkRenderManager<?> crm = getManager();
            if (crm != null) {
                crm.markDirty();
            }
        } catch (Throwable t) {
            Iris.logger.error("[Shadow] Failed to mark Vintagium chunk list dirty after shadow pass", t);
        }
    }
}
