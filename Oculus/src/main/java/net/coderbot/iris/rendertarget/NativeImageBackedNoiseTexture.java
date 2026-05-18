package net.coderbot.iris.rendertarget;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * A procedurally-generated noise texture, adapted for 1.12.2.
 */
public class NativeImageBackedNoiseTexture {
    private final int textureId;
    private final int size;

    public NativeImageBackedNoiseTexture(int size) {
        this.size = size;

        BufferedImage image = createNoiseImage(size);

        this.textureId = GlStateManager.generateTexture();
        GlStateManager.bindTexture(textureId);

        // Upload the texture with mipmaps
        TextureUtil.uploadTextureImageAllocate(textureId, image, true, false);

        // Set texture parameters for tiling
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private static BufferedImage createNoiseImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(0);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int r = random.nextInt(256);
                int g = random.nextInt(256);
                int b = random.nextInt(256);
                int a = 255;
                int color = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, color);
            }
        }

        return image;
    }

    public int getId() {
        return textureId;
    }

    public int getSize() {
        return size;
    }

    public void destroy() {
        GlStateManager.deleteTexture(textureId);
    }
}
