package net.coderbot.batchedentityrendering.impl.ordering;

import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;

/**
 * Interface for managing the order of render types.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public interface RenderOrderManager {
    void begin(IrisRenderLayer type);

    void startGroup();

    boolean maybeStartGroup();

    void endGroup();

    void reset();

    Iterable<IrisRenderLayer> getRenderOrder();
}
