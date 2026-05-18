package net.coderbot.iris.texture.util;

import java.awt.image.BufferedImage;

/**
 * Utility for image scaling operations.
 * Adapted for 1.12.2 using BufferedImage instead of NativeImage.
 */
public class ImageManipulationUtil {
    public static BufferedImage scaleNearestNeighbor(BufferedImage image, int newWidth, int newHeight) {
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        float xScale = (float) newWidth / image.getWidth();
        float yScale = (float) newHeight / image.getHeight();
        for (int y = 0; y < newHeight; ++y) {
            for (int x = 0; x < newWidth; ++x) {
                float unscaledX = (x + 0.5f) / xScale;
                float unscaledY = (y + 0.5f) / yScale;
                scaled.setRGB(x, y, image.getRGB((int) unscaledX, (int) unscaledY));
            }
        }
        return scaled;
    }

    public static BufferedImage scaleBilinear(BufferedImage image, int newWidth, int newHeight) {
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        float xScale = (float) newWidth / image.getWidth();
        float yScale = (float) newHeight / image.getHeight();
        for (int y = 0; y < newHeight; ++y) {
            for (int x = 0; x < newWidth; ++x) {
                float unscaledX = (x + 0.5f) / xScale;
                float unscaledY = (y + 0.5f) / yScale;

                int x1 = Math.round(unscaledX);
                int y1 = Math.round(unscaledY);
                int x0 = x1 - 1;
                int y0 = y1 - 1;

                boolean x0valid = x0 >= 0;
                boolean y0valid = y0 >= 0;
                boolean x1valid = x1 < image.getWidth();
                boolean y1valid = y1 < image.getHeight();

                int finalColor;
                if (x0valid & y0valid & x1valid & y1valid) {
                    float leftWeight = (x1 + 0.5f) - unscaledX;
                    float rightWeight = unscaledX - (x0 + 0.5f);
                    float topWeight = (y1 + 0.5f) - unscaledY;
                    float bottomWeight = unscaledY - (y0 + 0.5f);

                    float weightTL = leftWeight * topWeight;
                    float weightTR = rightWeight * topWeight;
                    float weightBL = leftWeight * bottomWeight;
                    float weightBR = rightWeight * bottomWeight;

                    int colorTL = image.getRGB(x0, y0);
                    int colorTR = image.getRGB(x1, y0);
                    int colorBL = image.getRGB(x0, y1);
                    int colorBR = image.getRGB(x1, y1);

                    finalColor = blendColor(colorTL, colorTR, colorBL, colorBR, weightTL, weightTR, weightBL, weightBR);
                } else if (x0valid & x1valid) {
                    float leftWeight = (x1 + 0.5f) - unscaledX;
                    float rightWeight = unscaledX - (x0 + 0.5f);

                    int validY = y0valid ? y0 : y1;
                    int colorLeft = image.getRGB(x0, validY);
                    int colorRight = image.getRGB(x1, validY);

                    finalColor = blendColor(colorLeft, colorRight, leftWeight, rightWeight);
                } else if (y0valid & y1valid) {
                    float topWeight = (y1 + 0.5f) - unscaledY;
                    float bottomWeight = unscaledY - (y0 + 0.5f);

                    int validX = x0valid ? x0 : x1;
                    int colorTop = image.getRGB(validX, y0);
                    int colorBottom = image.getRGB(validX, y1);

                    finalColor = blendColor(colorTop, colorBottom, topWeight, bottomWeight);
                } else {
                    finalColor = image.getRGB(x0valid ? x0 : x1, y0valid ? y0 : y1);
                }
                scaled.setRGB(x, y, finalColor);
            }
        }
        return scaled;
    }

    // ARGB color manipulation helpers (BufferedImage format: 0xAARRGGBB)
    private static int getAlpha(int color) { return (color >> 24) & 0xFF; }
    private static int getRed(int color) { return (color >> 16) & 0xFF; }
    private static int getGreen(int color) { return (color >> 8) & 0xFF; }
    private static int getBlue(int color) { return color & 0xFF; }

    private static int combine(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int blendColor(int c0, int c1, int c2, int c3, float w0, float w1, float w2, float w3) {
        return combine(
                blendChannel(getAlpha(c0), getAlpha(c1), getAlpha(c2), getAlpha(c3), w0, w1, w2, w3),
                blendChannel(getRed(c0), getRed(c1), getRed(c2), getRed(c3), w0, w1, w2, w3),
                blendChannel(getGreen(c0), getGreen(c1), getGreen(c2), getGreen(c3), w0, w1, w2, w3),
                blendChannel(getBlue(c0), getBlue(c1), getBlue(c2), getBlue(c3), w0, w1, w2, w3)
        );
    }

    private static int blendChannel(int v0, int v1, int v2, int v3, float w0, float w1, float w2, float w3) {
        return Math.round(v0 * w0 + v1 * w1 + v2 * w2 + v3 * w3);
    }

    private static int blendColor(int c0, int c1, float w0, float w1) {
        return combine(
                blendChannel(getAlpha(c0), getAlpha(c1), w0, w1),
                blendChannel(getRed(c0), getRed(c1), w0, w1),
                blendChannel(getGreen(c0), getGreen(c1), w0, w1),
                blendChannel(getBlue(c0), getBlue(c1), w0, w1)
        );
    }

    private static int blendChannel(int v0, int v1, float w0, float w1) {
        return Math.round(v0 * w0 + v1 * w1);
    }
}
