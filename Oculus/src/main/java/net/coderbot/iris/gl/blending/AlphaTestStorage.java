package net.coderbot.iris.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Stores and restores alpha test state.
 * In 1.12.2, we track state manually since GlStateManager.AlphaState fields are private.
 */
public class AlphaTestStorage {
    private static boolean originalAlphaTestEnable;
    private static AlphaTest originalAlphaTest = new AlphaTest(AlphaTestFunction.ALWAYS, 0.0f);
    private static boolean alphaTestLocked;

    // Track the current alpha state ourselves
    private static boolean currentAlphaEnabled = false;
    private static int currentAlphaFunc = GL11.GL_ALWAYS;
    private static float currentAlphaRef = 0.0f;

    public static boolean isAlphaTestLocked() {
        return alphaTestLocked;
    }

    /**
     * Called when alpha test is enabled/disabled by external code.
     * Used by mixins to track state changes.
     */
    public static void trackAlphaEnabled(boolean enabled) {
        if (!alphaTestLocked) {
            currentAlphaEnabled = enabled;
        }
    }

    /**
     * Called when alpha func is changed by external code.
     * Used by mixins to track state changes.
     */
    public static void trackAlphaFunc(int func, float ref) {
        if (!alphaTestLocked) {
            currentAlphaFunc = func;
            currentAlphaRef = ref;
        }
    }

    public static void overrideAlphaTest(AlphaTest override) {
        if (!alphaTestLocked) {
            // Only save the previous state if the alpha test wasn't already locked
            originalAlphaTestEnable = currentAlphaEnabled;
            originalAlphaTest = new AlphaTest(AlphaTestFunction.fromGlId(currentAlphaFunc).orElse(AlphaTestFunction.ALWAYS), currentAlphaRef);
        }

        alphaTestLocked = false;

        if (override == null) {
            GlStateManager.disableAlpha();
        } else {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(override.getFunction().getGlId(), override.getReference());
        }

        alphaTestLocked = true;
    }

    public static void deferAlphaTestToggle(boolean enabled) {
        originalAlphaTestEnable = enabled;
    }

    public static void deferAlphaFunc(int function, float reference) {
        originalAlphaTest = new AlphaTest(AlphaTestFunction.fromGlId(function).orElse(AlphaTestFunction.ALWAYS), reference);
    }

    public static void restoreAlphaTest() {
        if (!alphaTestLocked) {
            return;
        }

        alphaTestLocked = false;

        if (originalAlphaTestEnable) {
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
        }

        GlStateManager.alphaFunc(originalAlphaTest.getFunction().getGlId(), originalAlphaTest.getReference());
    }
}
