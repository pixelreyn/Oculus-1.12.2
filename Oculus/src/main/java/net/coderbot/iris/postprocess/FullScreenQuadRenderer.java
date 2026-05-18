package net.coderbot.iris.postprocess;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.coderbot.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 * Adapted for 1.12.2.
 */
public class FullScreenQuadRenderer {
    public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();
    private final int quadBuffer;

    private FullScreenQuadRenderer() {
        this.quadBuffer = createQuad();
    }

    public static void end() {
        // Disable vertex attributes
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        GlStateManager.enableDepth();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    /**
     * Creates and uploads a vertex buffer containing a single full-screen quad
     */
    private static int createQuad() {
        float[] vertices = new float[]{
                // Vertex 0: Top right corner
                1.0F, 1.0F, 0.0F,
                1.0F, 1.0F,
                // Vertex 1: Top left corner
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F,
                // Vertex 2: Bottom right corner
                1.0F, 0.0F, 0.0F,
                1.0F, 0.0F,
                // Vertex 3: Bottom left corner
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F
        };

        return IrisRenderSystem.bufferStorage(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
    }

    public void render() {
        begin();

        renderQuad();

        end();
    }

    public void begin() {
        GlStateManager.disableDepth();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        // scale the quad from [0, 1] to [-1, 1]
        GlStateManager.translate(-1.0F, -1.0F, 0.0F);
        GlStateManager.scale(2.0F, 2.0F, 1.0F);

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);

        // Set up vertex attributes manually
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

        // Position: 3 floats, stride = 20 bytes (3+2 floats * 4 bytes), offset = 0
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 20, 0);
        // TexCoord: 2 floats, stride = 20 bytes, offset = 12 bytes (3 floats * 4)
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 20, 12);
    }

    public void renderQuad() {
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }
}
