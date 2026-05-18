package net.coderbot.batchedentityrendering.impl;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.util.*;

/**
 * Legacy buffered multi-buffer source for 1.12.2.
 * Simplified from the 1.16+ version.
 */
public class OldFullyBufferedMultiBufferSource implements FlushableMultiBufferSource {
    private final Map<IrisRenderLayer, BufferBuilder> bufferBuilders;
    private final Object2IntMap<IrisRenderLayer> unused;
    private final Set<BufferBuilder> activeBuffers;
    private final Set<IrisRenderLayer> typesThisFrame;
    private final List<IrisRenderLayer> typesInOrder;
    private boolean flushed;

    public OldFullyBufferedMultiBufferSource() {
        this.bufferBuilders = new HashMap<>();
        this.unused = new Object2IntOpenHashMap<>();
        this.activeBuffers = new HashSet<>();
        this.flushed = false;

        this.typesThisFrame = new HashSet<>();
        this.typesInOrder = new ArrayList<>();
    }

    private TransparencyType getTransparencyType(IrisRenderLayer type) {
        while (type instanceof WrappableRenderType) {
            type = ((WrappableRenderType) type).unwrap();
        }

        if (type instanceof BlendingStateHolder) {
            return ((BlendingStateHolder) type).getTransparencyType();
        }

        return type.getTransparencyType();
    }

    /**
     * Gets a buffer for the specified render layer.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderLayer) {
        return getBuffer(renderLayer, 7, DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    /**
     * Gets a buffer for the specified render layer with format.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderLayer, int drawMode, VertexFormat format) {
        flushed = false;

        BufferBuilder buffer = bufferBuilders.computeIfAbsent(renderLayer, type -> new BufferBuilder(256 * 1024));

        if (activeBuffers.add(buffer)) {
            buffer.begin(drawMode, format);
        }

        if (this.typesThisFrame.add(renderLayer)) {
            this.typesInOrder.add(renderLayer);
        }

        unused.removeInt(renderLayer);

        return buffer;
    }

    /**
     * Ends the current batch and renders all collected geometry.
     */
    public void endBatch() {
        if (flushed) {
            return;
        }

        List<IrisRenderLayer> removedTypes = new ArrayList<>();

        unused.forEach((unusedType, unusedCount) -> {
            if (unusedCount < 10) {
                return;
            }

            BufferBuilder buffer = bufferBuilders.remove(unusedType);
            removedTypes.add(unusedType);

            if (activeBuffers.contains(buffer)) {
                throw new IllegalStateException(
                        "A buffer was simultaneously marked as inactive and as active");
            }
        });

        for (IrisRenderLayer removed : removedTypes) {
            unused.removeInt(removed);
        }

        // Sort by transparency type
        typesInOrder.sort(Comparator.comparing(this::getTransparencyType));

        for (IrisRenderLayer type : typesInOrder) {
            drawInternal(type);
        }

        typesInOrder.clear();
        typesThisFrame.clear();

        flushed = true;
    }

    @Override
    public void flushNonTranslucentContent() {
        // Simplified - no-op in this version
    }

    @Override
    public void flushTranslucentContent() {
        endBatch();
    }

    private void drawInternal(IrisRenderLayer type) {
        BufferBuilder buffer = bufferBuilders.get(type);

        if (buffer == null) {
            return;
        }

        if (activeBuffers.remove(buffer)) {
            buffer.finishDrawing();
            Tessellator.getInstance().draw();
            buffer.reset();
        } else {
            int unusedCount = unused.getOrDefault(type, 0);
            unusedCount += 1;
            unused.put(type, unusedCount);
        }
    }
}
