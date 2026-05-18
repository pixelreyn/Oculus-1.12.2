package net.coderbot.iris.fantastic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Custom particle render types for Iris shader compatibility.
 * Adapted for 1.12.2's particle rendering system.
 */
public class IrisParticleRenderTypes {

    /**
     * Opaque terrain particle rendering - disables blending for proper depth.
     */
    public static void beginOpaqueTerrain() {
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.003921569F);

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
    }

    /**
     * Ends opaque terrain particle rendering.
     */
    public static void endOpaqueTerrain() {
        Tessellator.getInstance().draw();
        GlStateManager.enableBlend();
    }

    /**
     * Checks if the given particle type should be rendered as opaque.
     * In 1.12.2, we use a simplified approach.
     */
    public static boolean isOpaqueParticle(int particleType) {
        // Block breaking particles are typically opaque
        // This is a simplified heuristic
        return false; // Default to transparent for compatibility
    }
}
