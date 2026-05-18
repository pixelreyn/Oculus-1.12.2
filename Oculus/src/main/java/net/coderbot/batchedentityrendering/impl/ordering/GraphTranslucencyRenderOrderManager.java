package net.coderbot.batchedentityrendering.impl.ordering;

import de.odysseus.ithaka.digraph.Digraph;
import de.odysseus.ithaka.digraph.Digraphs;
import de.odysseus.ithaka.digraph.MapDigraph;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSet;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetPolicy;
import de.odysseus.ithaka.digraph.util.fas.FeedbackArcSetProvider;
import de.odysseus.ithaka.digraph.util.fas.SimpleFeedbackArcSetProvider;
import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.batchedentityrendering.impl.WrappableRenderType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Graph-based render order manager with translucency ordering.
 * Uses digraph algorithms for cycle detection and topological sorting.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public class GraphTranslucencyRenderOrderManager implements RenderOrderManager {
    private final FeedbackArcSetProvider feedbackArcSetProvider;
    private final EnumMap<TransparencyType, Digraph<IrisRenderLayer>> types;
    private final EnumMap<TransparencyType, IrisRenderLayer> currentTypes;
    private boolean inGroup = false;

    public GraphTranslucencyRenderOrderManager() {
        feedbackArcSetProvider = new SimpleFeedbackArcSetProvider();
        types = new EnumMap<>(TransparencyType.class);
        currentTypes = new EnumMap<>(TransparencyType.class);

        for (TransparencyType type : TransparencyType.values()) {
            types.put(type, new MapDigraph<>());
        }
    }

    private static TransparencyType getTransparencyType(IrisRenderLayer type) {
        while (type instanceof WrappableRenderType) {
            type = ((WrappableRenderType) type).unwrap();
        }

        if (type instanceof BlendingStateHolder) {
            return ((BlendingStateHolder) type).getTransparencyType();
        }

        // IrisRenderLayer implements BlendingStateHolder
        return type.getTransparencyType();
    }

    @Override
    public void begin(IrisRenderLayer renderType) {
        TransparencyType transparencyType = getTransparencyType(renderType);
        Digraph<IrisRenderLayer> graph = types.get(transparencyType);
        graph.add(renderType);

        if (inGroup) {
            IrisRenderLayer previous = currentTypes.put(transparencyType, renderType);

            if (previous == null) {
                return;
            }

            int weight = graph.get(previous, renderType).orElse(0);
            weight += 1;
            graph.put(previous, renderType, weight);
        }
    }

    @Override
    public void startGroup() {
        if (inGroup) {
            throw new IllegalStateException("Already in a group");
        }

        currentTypes.clear();
        inGroup = true;
    }

    @Override
    public boolean maybeStartGroup() {
        if (inGroup) {
            return false;
        }

        currentTypes.clear();
        inGroup = true;
        return true;
    }

    @Override
    public void endGroup() {
        if (!inGroup) {
            throw new IllegalStateException("Not in a group");
        }

        currentTypes.clear();
        inGroup = false;
    }

    @Override
    public void reset() {
        types.clear();

        for (TransparencyType type : TransparencyType.values()) {
            types.put(type, new MapDigraph<>());
        }
    }

    @Override
    public Iterable<IrisRenderLayer> getRenderOrder() {
        int layerCount = 0;

        for (Digraph<IrisRenderLayer> graph : types.values()) {
            layerCount += graph.getVertexCount();
        }

        List<IrisRenderLayer> allLayers = new ArrayList<>(layerCount);

        for (Digraph<IrisRenderLayer> graph : types.values()) {
            // Use FAS algorithm to detect and break cycles
            FeedbackArcSet<IrisRenderLayer> arcSet =
                    feedbackArcSetProvider.getFeedbackArcSet(graph, graph, FeedbackArcSetPolicy.MIN_WEIGHT);

            if (arcSet.getEdgeCount() > 0) {
                // Dependency graph had cycles - remove them
                for (IrisRenderLayer source : arcSet.vertices()) {
                    for (IrisRenderLayer target : arcSet.targets(source)) {
                        graph.remove(source, target);
                    }
                }
            }

            allLayers.addAll(Digraphs.toposort(graph, false));
        }

        return allLayers;
    }
}
