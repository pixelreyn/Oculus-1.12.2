package net.coderbot.iris.rendertarget;

import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.shaderpack.texture.CustomTextureData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * A custom texture backed by PNG data, adapted for 1.12.2.
 */
public class NativeImageBackedCustomTexture {
    private final int textureId;
    private final int width;
    private final int height;

    public NativeImageBackedCustomTexture(CustomTextureData.PngData textureData) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(textureData.getContent()));
        this.width = image.getWidth();
        this.height = image.getHeight();

        this.textureId = GlStateManager.generateTexture();
        GlStateManager.bindTexture(textureId);

        // Upload the texture
        TextureUtil.uploadTextureImage(textureId, image);

        // Set filtering parameters
        if (textureData.getFilteringData().shouldBlur()) {
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } else {
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        if (textureData.getFilteringData().shouldClamp()) {
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            IrisRenderSystem.texParameteri(textureId, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }
    }

    public int getId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void destroy() {
        GlStateManager.deleteTexture(textureId);
    }
}
