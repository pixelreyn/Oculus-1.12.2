package net.coderbot.batchedentityrendering.impl.ordering;

import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;

import java.util.LinkedHashSet;

/**
 * Simple render order manager that preserves insertion order.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public class SimpleRenderOrderManager implements RenderOrderManager {
    private final LinkedHashSet<IrisRenderLayer> renderTypes;

    public SimpleRenderOrderManager() {
        renderTypes = new LinkedHashSet<>();
    }

    @Override
    public void begin(IrisRenderLayer type) {
        renderTypes.add(type);
    }

    @Override
    public void startGroup() {
        // no-op
    }

    @Override
    public boolean maybeStartGroup() {
        // no-op
        return false;
    }

    @Override
    public void endGroup() {
        // no-op
    }

    @Override
    public void reset() {
        renderTypes.clear();
    }

    @Override
    public Iterable<IrisRenderLayer> getRenderOrder() {
        return renderTypes;
    }
}
