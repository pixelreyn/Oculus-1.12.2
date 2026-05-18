package net.coderbot.batchedentityrendering.impl;

import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Represents a segment of a buffer for deferred rendering.
 * Adapted for 1.12.2 - uses IrisRenderLayer instead of RenderType,
 * and stores vertex metadata directly instead of using DrawState.
 */
public class BufferSegment {
    private final ByteBuffer slice;
    private final IrisRenderLayer type;
    private final int vertexCount;
    private final VertexFormat format;
    private final int drawMode;

    public BufferSegment(ByteBuffer slice, IrisRenderLayer type, int vertexCount, VertexFormat format, int drawMode) {
        this.slice = slice;
        this.type = type;
        this.vertexCount = vertexCount;
        this.format = format;
        this.drawMode = drawMode;
    }

    public ByteBuffer getSlice() {
        return slice;
    }

    public IrisRenderLayer getRenderType() {
        return type;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public VertexFormat getFormat() {
        return format;
    }

    public int getDrawMode() {
        return drawMode;
    }
}
