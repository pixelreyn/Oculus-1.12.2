package net.coderbot.iris.texture.util;

import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;

/**
 * Utility class for exporting textures to disk.
 * Adapted for 1.12.2 using BufferedImage.
 */
public class TextureExporter {
    public static void exportTextures(String directory, String filename, int textureId, int mipLevel, int width, int height) {
        String extension = FilenameUtils.getExtension(filename);
        String baseName = filename.substring(0, filename.length() - extension.length() - 1);
        for (int level = 0; level <= mipLevel; ++level) {
            exportTexture(directory, baseName + "_" + level + "." + extension, textureId, level, width >> level, height >> level);
        }
    }

    public static void exportTexture(String directory, String filename, int textureId, int level, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        // Bind texture and download pixels
        GlStateManager.bindTexture(textureId);

        IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, level, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

        // Convert to BufferedImage
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        buffer.get(pixels);

        // Convert BGRA to ARGB
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int b = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int r = pixel & 0xFF;
            int a = (pixel >> 24) & 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // Flip vertically (OpenGL textures are upside down)
        for (int y = 0; y < height / 2; y++) {
            for (int x = 0; x < width; x++) {
                int temp = pixels[y * width + x];
                pixels[y * width + x] = pixels[(height - 1 - y) * width + x];
                pixels[(height - 1 - y) * width + x] = temp;
            }
        }

        image.setRGB(0, 0, width, height, pixels, 0, width);

        // Save on a separate thread
        File dir = new File(Minecraft.getMinecraft().gameDir, directory);
        dir.mkdirs();
        File file = new File(dir, filename);

        final BufferedImage finalImage = image;
        new Thread(() -> {
            try {
                ImageIO.write(finalImage, FilenameUtils.getExtension(filename), file);
            } catch (Exception e) {
                Iris.logger.error("Failed to export texture", e);
            }
        }).start();
    }
}
