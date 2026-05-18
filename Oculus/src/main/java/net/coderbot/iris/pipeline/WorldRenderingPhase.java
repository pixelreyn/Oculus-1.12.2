package net.coderbot.iris.pipeline;

import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;
import net.minecraft.util.BlockRenderLayer;

public enum WorldRenderingPhase {
    NONE,
    SKY,
    SUNSET,
    CUSTOM_SKY, // Unused, just here to match OptiFine ordinals
    SUN,
    MOON,
    STARS,
    VOID,
    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,
    ENTITIES,
    BLOCK_ENTITIES,
    DESTROY,
    OUTLINE,
    DEBUG,
    HAND_SOLID,
    TERRAIN_TRANSLUCENT,
    TRIPWIRE,
    PARTICLES,
    CLOUDS,
    RAIN_SNOW,
    WORLD_BORDER,
    HAND_TRANSLUCENT;

    /**
     * Convert from 1.12.2's BlockRenderLayer to WorldRenderingPhase.
     */
    public static WorldRenderingPhase fromBlockRenderLayer(BlockRenderLayer layer) {
        switch (layer) {
            case SOLID:
                return WorldRenderingPhase.TERRAIN_SOLID;
            case CUTOUT:
                return WorldRenderingPhase.TERRAIN_CUTOUT;
            case CUTOUT_MIPPED:
                return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
            case TRANSLUCENT:
                return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            default:
                throw new IllegalStateException("Illegal render layer: " + layer);
        }
    }

    /**
     * Convert from IrisRenderLayer to WorldRenderingPhase.
     */
    public static WorldRenderingPhase fromIrisRenderLayer(IrisRenderLayer renderLayer) {
        if (renderLayer == IrisRenderLayer.SOLID) {
            return WorldRenderingPhase.TERRAIN_SOLID;
        } else if (renderLayer == IrisRenderLayer.CUTOUT) {
            return WorldRenderingPhase.TERRAIN_CUTOUT;
        } else if (renderLayer == IrisRenderLayer.CUTOUT_MIPPED) {
            return WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
        } else if (renderLayer == IrisRenderLayer.TRANSLUCENT) {
            return WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        } else if (renderLayer == IrisRenderLayer.TRIPWIRE) {
            return WorldRenderingPhase.TRIPWIRE;
        } else {
            // Default for entity/other layers
            return WorldRenderingPhase.ENTITIES;
        }
    }

    /**
     * Legacy method for compatibility - delegates to fromIrisRenderLayer.
     */
    public static WorldRenderingPhase fromTerrainRenderType(IrisRenderLayer renderLayer) {
        return fromIrisRenderLayer(renderLayer);
    }
}
