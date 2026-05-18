package net.coderbot.iris.rendertarget;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;

/**
 * A 1x1 texture with a single color.
 * Adapted for 1.12.2.
 */
public class NativeImageBackedSingleColorTexture extends AbstractTexture {
    public NativeImageBackedSingleColorTexture(int red, int green, int blue, int alpha) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        int color = (alpha << 24) | (red << 16) | (green << 8) | blue;
        image.setRGB(0, 0, color);

        this.glTextureId = GlStateManager.generateTexture();
        GlStateManager.bindTexture(glTextureId);
        TextureUtil.uploadTextureImage(glTextureId, image);

        // Set nearest-neighbor filtering for single-pixel texture
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    public NativeImageBackedSingleColorTexture(int rgba) {
        this((rgba >> 24) & 0xFF, (rgba >> 16) & 0xFF, (rgba >> 8) & 0xFF, rgba & 0xFF);
    }

    public int getId() {
        return glTextureId;
    }

    @Override
    public void loadTexture(IResourceManager resourceManager) {
        // Texture is already loaded in constructor
    }

    public void destroy() {
        GlStateManager.deleteTexture(glTextureId);
    }
}
