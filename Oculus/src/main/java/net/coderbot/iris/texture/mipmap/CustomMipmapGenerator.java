package net.coderbot.iris.texture.mipmap;

import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * Interface for custom mipmap generation.
 * Adapted for 1.12.2 using BufferedImage instead of NativeImage.
 */
public interface CustomMipmapGenerator {
    BufferedImage[] generateMipLevels(BufferedImage image, int mipLevel);

    interface Provider {
        @Nullable
        CustomMipmapGenerator getMipmapGenerator(String spriteName, int atlasWidth, int atlasHeight);
    }
}
