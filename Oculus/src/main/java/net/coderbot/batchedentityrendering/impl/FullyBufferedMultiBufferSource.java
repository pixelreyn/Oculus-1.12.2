package net.coderbot.batchedentityrendering.impl;

import net.coderbot.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager;
import net.coderbot.batchedentityrendering.impl.ordering.RenderOrderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.util.*;
import java.util.function.Function;

/**
 * A buffered render source for 1.12.2.
 * Simplified from the 1.16+ version - doesn't extend MultiBufferSource since that doesn't exist.
 * Instead, provides direct buffer access methods.
 */
public class FullyBufferedMultiBufferSource implements MemoryTrackingBuffer, Groupable, FlushableMultiBufferSource {
    private static final int NUM_BUFFERS = 32;

    private final RenderOrderManager renderOrderManager;
    private final SegmentedBufferBuilder[] builders;
    /**
     * An LRU cache mapping IrisRenderLayer objects to a relevant buffer.
     */
    private final LinkedHashMap<IrisRenderLayer, Integer> affinities;
    private final BufferSegmentRenderer segmentRenderer;
    private final List<Function<IrisRenderLayer, IrisRenderLayer>> wrappingFunctionStack;
    private int drawCalls;
    private int renderTypes;
    private Function<IrisRenderLayer, IrisRenderLayer> wrappingFunction = null;

    public FullyBufferedMultiBufferSource() {
        this.renderOrderManager = new GraphTranslucencyRenderOrderManager();
        this.builders = new SegmentedBufferBuilder[NUM_BUFFERS];

        for (int i = 0; i < this.builders.length; i++) {
            this.builders[i] = new SegmentedBufferBuilder();
        }

        // use accessOrder=true so our LinkedHashMap works as an LRU cache.
        this.affinities = new LinkedHashMap<>(32, 0.75F, true);

        this.drawCalls = 0;
        this.segmentRenderer = new BufferSegmentRenderer();
        this.wrappingFunctionStack = new ArrayList<>();
    }

    /**
     * Gets a buffer for the specified render layer.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderLayer) {
        return getBuffer(renderLayer, 7, DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    /**
     * Gets a buffer for the specified render layer with draw mode and format.
     */
    public BufferBuilder getBuffer(IrisRenderLayer renderLayer, int drawMode, VertexFormat format) {
        if (wrappingFunction != null) {
            renderLayer = wrappingFunction.apply(renderLayer);
        }

        renderOrderManager.begin(renderLayer);
        Integer affinity = affinities.get(renderLayer);

        if (affinity == null) {
            if (affinities.size() < builders.length) {
                affinity = affinities.size();
            } else {
                // We remove the element from the map that is used least-frequently.
                // With how we've configured our LinkedHashMap, that is the first element.
                Iterator<Map.Entry<IrisRenderLayer, Integer>> iterator = affinities.entrySet().iterator();
                Map.Entry<IrisRenderLayer, Integer> evicted = iterator.next();
                iterator.remove();

                // The previous type is no longer associated with this buffer ...
                affinities.remove(evicted.getKey());

                // ... since our new type is now associated with it.
                affinity = evicted.getValue();
            }

            affinities.put(renderLayer, affinity);
        }

        return builders[affinity].getBuffer(renderLayer, drawMode, format);
    }

    /**
     * Ends the current batch and renders all collected geometry.
     */
    public void endBatch() {
        Map<IrisRenderLayer, List<BufferSegment>> typeToSegment = new HashMap<>();

        for (SegmentedBufferBuilder builder : builders) {
            List<BufferSegment> segments = builder.getSegments();

            for (BufferSegment segment : segments) {
                typeToSegment.computeIfAbsent(segment.getRenderType(), (type) -> new ArrayList<>()).add(segment);
            }
        }

        Iterable<IrisRenderLayer> renderOrder = renderOrderManager.getRenderOrder();

        for (IrisRenderLayer type : renderOrder) {
            renderTypes += 1;

            for (BufferSegment segment : typeToSegment.getOrDefault(type, Collections.emptyList())) {
                segmentRenderer.drawInner(segment);
                drawCalls += 1;
            }
        }

        renderOrderManager.reset();
        affinities.clear();
    }

    @Override
    public void flushNonTranslucentContent() {
        // Simplified - just end the batch
        // In a full implementation, this would only flush opaque content
    }

    @Override
    public void flushTranslucentContent() {
        endBatch();
    }

    public int getDrawCalls() {
        return drawCalls;
    }

    public int getRenderTypes() {
        return renderTypes;
    }

    public void resetDrawCalls() {
        drawCalls = 0;
        renderTypes = 0;
    }

    @Override
    public int getAllocatedSize() {
        int size = 0;

        for (SegmentedBufferBuilder builder : builders) {
            size += builder.getAllocatedSize();
        }

        return size;
    }

    @Override
    public int getUsedSize() {
        int size = 0;

        for (SegmentedBufferBuilder builder : builders) {
            size += builder.getUsedSize();
        }

        return size;
    }

    @Override
    public void startGroup() {
        renderOrderManager.startGroup();
    }

    @Override
    public boolean maybeStartGroup() {
        return renderOrderManager.maybeStartGroup();
    }

    @Override
    public void endGroup() {
        renderOrderManager.endGroup();
    }

    /**
     * Pushes a wrapping function that transforms render layers.
     */
    public void pushWrappingFunction(Function<IrisRenderLayer, IrisRenderLayer> wrappingFunction) {
        if (this.wrappingFunction != null) {
            this.wrappingFunctionStack.add(this.wrappingFunction);
        }

        this.wrappingFunction = wrappingFunction;
    }

    /**
     * Pops the current wrapping function.
     */
    public void popWrappingFunction() {
        if (this.wrappingFunctionStack.isEmpty()) {
            this.wrappingFunction = null;
        } else {
            this.wrappingFunction = this.wrappingFunctionStack.remove(this.wrappingFunctionStack.size() - 1);
        }
    }

    /**
     * Asserts that the wrapping function stack is empty.
     */
    public void assertWrapStackEmpty() {
        if (!this.wrappingFunctionStack.isEmpty() || this.wrappingFunction != null) {
            throw new IllegalStateException("Wrapping function stack not empty!");
        }
    }
}
