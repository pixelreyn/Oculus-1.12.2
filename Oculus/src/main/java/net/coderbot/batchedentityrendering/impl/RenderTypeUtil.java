package net.coderbot.batchedentityrendering.impl;

import org.lwjgl.opengl.GL11;

/**
 * Utility methods for render types in 1.12.2.
 */
public class RenderTypeUtil {
    /**
     * Checks if the given draw mode is a triangle strip.
     * In 1.12.2, we check the draw mode directly instead of using RenderType.
     */
    public static boolean isTriangleStripDrawMode(int drawMode) {
        return drawMode == GL11.GL_TRIANGLE_STRIP;
    }

    /**
     * Checks if the given IrisRenderLayer uses triangle strip mode.
     * Since IrisRenderLayer doesn't store draw mode info, this defaults to false.
     */
    public static boolean isTriangleStripDrawMode(IrisRenderLayer renderType) {
        // In 1.12.2, standard render layers don't use triangle strips
        return false;
    }
}
