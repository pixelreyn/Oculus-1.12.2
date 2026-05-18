package net.coderbot.batchedentityrendering.impl;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A simplified segmented buffer builder for 1.12.2.
 * This version doesn't do complex segment tracking like the 1.16+ version,
 * as 1.12.2 lacks the necessary DrawState infrastructure.
 */
public class SegmentedBufferBuilder implements MemoryTrackingBuffer {
    private final BufferBuilder buffer;
    private final List<IrisRenderLayer> usedTypes;
    private IrisRenderLayer currentType;
    private int currentDrawMode;
    private VertexFormat currentFormat;

    public SegmentedBufferBuilder() {
        // 2 MB initial allocation
        this.buffer = new BufferBuilder(512 * 1024);
        this.usedTypes = new ArrayList<>(256);
        this.currentType = null;
        this.currentDrawMode = 7; // GL_QUADS
        this.currentFormat = DefaultVertexFormats.POSITION_TEX_COLOR;
    }

    /**
     * Gets a buffer for the specified render layer.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderType) {
        return getBuffer(renderType, 7, DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    /**
     * Gets a buffer for the specified render layer with draw mode and format.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderType, int drawMode, VertexFormat format) {
        if (!Objects.equals(currentType, renderType)) {
            if (currentType != null) {
                buffer.finishDrawing();
                usedTypes.add(currentType);
            }

            buffer.begin(drawMode, format);
            currentType = renderType;
            currentDrawMode = drawMode;
            currentFormat = format;
        }

        // Use duplicate vertices to break up triangle strips
        if (RenderTypeUtil.isTriangleStripDrawMode(currentDrawMode)) {
            BufferBuilderExt ext = (BufferBuilderExt) buffer;
            ext.iris$splitStrip();
        }

        return buffer;
    }

    /**
     * Returns the segments of vertex data collected so far.
     * Note: In 1.12.2, this is simplified and may not work the same as 1.16+.
     */
    public List<BufferSegment> getSegments() {
        if (currentType == null) {
            return Collections.emptyList();
        }

        usedTypes.add(currentType);
        buffer.finishDrawing();
        currentType = null;

        // In 1.12.2, we can't easily segment the buffer like in 1.16+
        // Return a single segment with the entire buffer
        List<BufferSegment> segments = new ArrayList<>(1);

        ByteBuffer byteBuffer = buffer.getByteBuffer();
        byteBuffer.rewind();

        // Create a single segment with all the data
        if (!usedTypes.isEmpty()) {
            IrisRenderLayer type = usedTypes.get(usedTypes.size() - 1);
            segments.add(new BufferSegment(
                byteBuffer.slice(),
                type,
                buffer.getVertexCount(),
                currentFormat != null ? currentFormat : DefaultVertexFormats.POSITION_TEX_COLOR,
                currentDrawMode
            ));
        }

        usedTypes.clear();
        buffer.reset();

        return segments;
    }

    public BufferBuilder getInternalBuffer() {
        return buffer;
    }

    @Override
    public int getAllocatedSize() {
        // Access the internal buffer size
        return buffer.getByteBuffer().capacity();
    }

    @Override
    public int getUsedSize() {
        return buffer.getVertexCount() * (currentFormat != null ? currentFormat.getSize() : 28);
    }
}
