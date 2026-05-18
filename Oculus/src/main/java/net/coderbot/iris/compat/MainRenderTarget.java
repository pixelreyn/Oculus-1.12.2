package net.coderbot.iris.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Compatibility wrapper for accessing the main render target in 1.12.2.
 * In 1.12.2, Minecraft uses a Framebuffer object for the main display.
 */
public class MainRenderTarget {
    private static int colorBufferVersion = 0;
    private static int depthBufferVersion = 0;
    private static int lastKnownColorTexture = -1;
    private static int lastKnownDepthTexture = -1;

    /**
     * Get the main Minecraft framebuffer.
     */
    public static Framebuffer get() {
        return Minecraft.getMinecraft().getFramebuffer();
    }

    /**
     * Get the framebuffer ID.
     */
    public static int getFramebufferId() {
        Framebuffer fb = get();
        return fb != null ? fb.framebufferObject : 0;
    }

    /**
     * Get the color texture ID of the main framebuffer.
     */
    public static int getColorTextureId() {
        Framebuffer fb = get();
        if (fb != null) {
            int tex = fb.framebufferTexture;
            if (tex != lastKnownColorTexture) {
                lastKnownColorTexture = tex;
                colorBufferVersion++;
            }
            return tex;
        }
        return 0;
    }

    /**
     * Get the depth texture ID of the main framebuffer.
     */
    public static int getDepthTextureId() {
        Framebuffer fb = get();
        if (fb != null) {
            int tex = fb.depthBuffer;
            if (tex != lastKnownDepthTexture) {
                lastKnownDepthTexture = tex;
                depthBufferVersion++;
            }
            return tex;
        }
        return 0;
    }

    /**
     * Get the width of the main framebuffer.
     */
    public static int getWidth() {
        Framebuffer fb = get();
        return fb != null ? fb.framebufferWidth : Minecraft.getMinecraft().displayWidth;
    }

    /**
     * Get the height of the main framebuffer.
     */
    public static int getHeight() {
        Framebuffer fb = get();
        return fb != null ? fb.framebufferHeight : Minecraft.getMinecraft().displayHeight;
    }

    /**
     * Bind the main framebuffer for writing.
     */
    public static void bindWrite(boolean setViewport) {
        Framebuffer fb = get();
        if (fb != null) {
            fb.bindFramebuffer(setViewport);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            if (setViewport) {
                GlStateManager.viewport(0, 0, Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
            }
        }
    }

    /**
     * Get the color buffer version for change tracking.
     */
    public static int getColorBufferVersion() {
        getColorTextureId(); // Update tracking
        return colorBufferVersion;
    }

    /**
     * Get the depth buffer version for change tracking.
     */
    public static int getDepthBufferVersion() {
        getDepthTextureId(); // Update tracking
        return depthBufferVersion;
    }
}
