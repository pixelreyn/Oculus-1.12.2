package net.coderbot.batchedentityrendering.impl;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Renders buffer segments for 1.12.2.
 * Simplified from 1.16+ - directly renders using OpenGL instead of BufferUploader.
 */
public class BufferSegmentRenderer {

    public BufferSegmentRenderer() {
    }

    /**
     * Draws a buffer segment.
     */
    public void draw(BufferSegment segment) {
        drawInner(segment);
    }

    /**
     * Draws the buffer segment's vertex data.
     */
    public void drawInner(BufferSegment segment) {
        ByteBuffer buffer = segment.getSlice();
        VertexFormat format = segment.getFormat();
        int drawMode = segment.getDrawMode();
        int vertexCount = segment.getVertexCount();

        if (vertexCount <= 0) {
            return;
        }

        // Rewind the buffer to start
        buffer.rewind();

        // Set up vertex format attributes manually in 1.12.2
        int stride = format.getSize();
        List<VertexFormatElement> elements = format.getElements();
        int offset = 0;

        for (int i = 0; i < elements.size(); i++) {
            VertexFormatElement element = elements.get(i);
            buffer.position(offset);

            switch (element.getUsage()) {
                case POSITION:
                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    GL11.glVertexPointer(element.getElementCount(), element.getType().getGlConstant(), stride, buffer);
                    break;
                case NORMAL:
                    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                    GL11.glNormalPointer(element.getType().getGlConstant(), stride, buffer);
                    break;
                case COLOR:
                    GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
                    GL11.glColorPointer(element.getElementCount(), element.getType().getGlConstant(), stride, buffer);
                    break;
                case UV:
                    if (element.getIndex() == 0) {
                        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        GL11.glTexCoordPointer(element.getElementCount(), element.getType().getGlConstant(), stride, buffer);
                    } else {
                        GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + element.getIndex());
                        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        GL11.glTexCoordPointer(element.getElementCount(), element.getType().getGlConstant(), stride, buffer);
                        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
                    }
                    break;
                case GENERIC:
                    GL20.glEnableVertexAttribArray(element.getIndex());
                    GL20.glVertexAttribPointer(element.getIndex(), element.getElementCount(), element.getType().getGlConstant(), false, stride, buffer);
                    break;
                default:
                    break;
            }

            offset += element.getSize();
        }

        // Draw the vertices
        buffer.position(0);
        GL11.glDrawArrays(drawMode, 0, vertexCount);

        // Clean up - disable vertex arrays
        for (int i = 0; i < elements.size(); i++) {
            VertexFormatElement element = elements.get(i);

            switch (element.getUsage()) {
                case POSITION:
                    GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case NORMAL:
                    GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
                    break;
                case COLOR:
                    GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                    break;
                case UV:
                    if (element.getIndex() == 0) {
                        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    } else {
                        GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + element.getIndex());
                        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
                    }
                    break;
                case GENERIC:
                    GL20.glDisableVertexAttribArray(element.getIndex());
                    break;
                default:
                    break;
            }
        }
    }
}
