package net.coderbot.iris.texture.mipmap;

import java.awt.image.BufferedImage;

/**
 * Abstract mipmap generator adapted for 1.12.2 using BufferedImage.
 * BufferedImage uses ARGB format (0xAARRGGBB).
 */
public abstract class AbstractMipmapGenerator implements CustomMipmapGenerator {
    @Override
    public BufferedImage[] generateMipLevels(BufferedImage image, int mipLevel) {
        BufferedImage[] images = new BufferedImage[mipLevel + 1];
        images[0] = image;
        if (mipLevel > 0) {
            for (int level = 1; level <= mipLevel; ++level) {
                BufferedImage prevMipmap = images[level - 1];
                int newWidth = Math.max(1, prevMipmap.getWidth() >> 1);
                int newHeight = Math.max(1, prevMipmap.getHeight() >> 1);
                BufferedImage mipmap = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                int width = mipmap.getWidth();
                int height = mipmap.getHeight();
                for (int x = 0; x < width; ++x) {
                    for (int y = 0; y < height; ++y) {
                        int x2 = Math.min(x * 2, prevMipmap.getWidth() - 1);
                        int y2 = Math.min(y * 2, prevMipmap.getHeight() - 1);
                        int x2p1 = Math.min(x * 2 + 1, prevMipmap.getWidth() - 1);
                        int y2p1 = Math.min(y * 2 + 1, prevMipmap.getHeight() - 1);
                        mipmap.setRGB(x, y, blend(
                                prevMipmap.getRGB(x2, y2),
                                prevMipmap.getRGB(x2p1, y2),
                                prevMipmap.getRGB(x2, y2p1),
                                prevMipmap.getRGB(x2p1, y2p1)
                        ));
                    }
                }
                images[level] = mipmap;
            }
        }
        return images;
    }

    public abstract int blend(int c0, int c1, int c2, int c3);
}
