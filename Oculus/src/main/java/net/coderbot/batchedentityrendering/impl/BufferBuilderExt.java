package net.coderbot.batchedentityrendering.impl;

import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Extension interface for BufferBuilder in 1.12.2.
 * Simplified from 1.16+ version - no DrawState support.
 */
public interface BufferBuilderExt {
    /**
     * Sets up a buffer slice for rendering.
     * @param buffer The byte buffer containing vertex data
     * @param vertexCount Number of vertices
     * @param format The vertex format
     * @param drawMode The GL draw mode (GL_QUADS, GL_TRIANGLES, etc.)
     */
    void iris$setupBufferSlice(ByteBuffer buffer, int vertexCount, VertexFormat format, int drawMode);

    /**
     * Tears down the current buffer slice.
     */
    void iris$teardownBufferSlice();

    /**
     * Splits a triangle strip by adding degenerate triangles.
     */
    void iris$splitStrip();
}
