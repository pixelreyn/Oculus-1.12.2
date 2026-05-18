package net.coderbot.iris.texture.mipmap;

/**
 * Mipmap generator that applies separate blend functions to each color channel.
 * Adapted for 1.12.2 using ARGB format (0xAARRGGBB).
 */
public class ChannelMipmapGenerator extends AbstractMipmapGenerator {
    protected final BlendFunction redFunc;
    protected final BlendFunction greenFunc;
    protected final BlendFunction blueFunc;
    protected final BlendFunction alphaFunc;

    public ChannelMipmapGenerator(BlendFunction redFunc, BlendFunction greenFunc, BlendFunction blueFunc, BlendFunction alphaFunc) {
        this.redFunc = redFunc;
        this.greenFunc = greenFunc;
        this.blueFunc = blueFunc;
        this.alphaFunc = alphaFunc;
    }

    @Override
    public int blend(int c0, int c1, int c2, int c3) {
        return combine(
                alphaFunc.blend(
                        getAlpha(c0),
                        getAlpha(c1),
                        getAlpha(c2),
                        getAlpha(c3)
                ),
                redFunc.blend(
                        getRed(c0),
                        getRed(c1),
                        getRed(c2),
                        getRed(c3)
                ),
                greenFunc.blend(
                        getGreen(c0),
                        getGreen(c1),
                        getGreen(c2),
                        getGreen(c3)
                ),
                blueFunc.blend(
                        getBlue(c0),
                        getBlue(c1),
                        getBlue(c2),
                        getBlue(c3)
                )
        );
    }

    // ARGB extraction methods (BufferedImage format: 0xAARRGGBB)
    private static int getAlpha(int color) {
        return (color >> 24) & 0xFF;
    }

    private static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int getBlue(int color) {
        return color & 0xFF;
    }

    // Combine ARGB components into a single int
    private static int combine(int alpha, int red, int green, int blue) {
        return ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
    }

    public interface BlendFunction {
        int blend(int v0, int v1, int v2, int v3);
    }
}
