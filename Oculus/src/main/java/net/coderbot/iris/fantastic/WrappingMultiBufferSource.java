package net.coderbot.iris.fantastic;

import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;

import java.util.function.Function;

/**
 * Interface for buffer sources that support render type wrapping.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public interface WrappingMultiBufferSource {
    void pushWrappingFunction(Function<IrisRenderLayer, IrisRenderLayer> wrappingFunction);

    void popWrappingFunction();

    void assertWrapStackEmpty();
}
