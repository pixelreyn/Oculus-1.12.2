package net.coderbot.batchedentityrendering.impl.ordering;

import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.batchedentityrendering.impl.WrappableRenderType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Render order manager that separates render types by transparency.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public class TranslucencyRenderOrderManager implements RenderOrderManager {
    private final EnumMap<TransparencyType, LinkedHashSet<IrisRenderLayer>> renderTypes;

    public TranslucencyRenderOrderManager() {
        renderTypes = new EnumMap<>(TransparencyType.class);

        for (TransparencyType type : TransparencyType.values()) {
            renderTypes.put(type, new LinkedHashSet<>());
        }
    }

    private static TransparencyType getTransparencyType(IrisRenderLayer type) {
        // Unwrap if needed
        while (type instanceof WrappableRenderType) {
            type = ((WrappableRenderType) type).unwrap();
        }

        if (type instanceof BlendingStateHolder) {
            return ((BlendingStateHolder) type).getTransparencyType();
        }

        // IrisRenderLayer already implements BlendingStateHolder, so this should work
        return type.getTransparencyType();
    }

    @Override
    public void begin(IrisRenderLayer type) {
        renderTypes.get(getTransparencyType(type)).add(type);
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
        renderTypes.forEach((type, set) -> {
            set.clear();
        });
    }

    @Override
    public Iterable<IrisRenderLayer> getRenderOrder() {
        int layerCount = 0;

        for (LinkedHashSet<IrisRenderLayer> set : renderTypes.values()) {
            layerCount += set.size();
        }

        List<IrisRenderLayer> allRenderTypes = new ArrayList<>(layerCount);

        for (LinkedHashSet<IrisRenderLayer> set : renderTypes.values()) {
            allRenderTypes.addAll(set);
        }

        return allRenderTypes;
    }
}
