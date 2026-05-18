package net.coderbot.batchedentityrendering.impl;

import net.minecraft.util.BlockRenderLayer;

/**
 * Abstraction layer bridging 1.12.2's BlockRenderLayer with code expecting RenderType.
 * This replaces the 1.16+ RenderType system for the batched entity rendering package.
 */
public class IrisRenderLayer implements BlendingStateHolder {
    // Standard render layers matching BlockRenderLayer
    public static final IrisRenderLayer SOLID = new IrisRenderLayer("solid", BlockRenderLayer.SOLID, TransparencyType.OPAQUE);
    public static final IrisRenderLayer CUTOUT = new IrisRenderLayer("cutout", BlockRenderLayer.CUTOUT, TransparencyType.OPAQUE);
    public static final IrisRenderLayer CUTOUT_MIPPED = new IrisRenderLayer("cutout_mipped", BlockRenderLayer.CUTOUT_MIPPED, TransparencyType.OPAQUE);
    public static final IrisRenderLayer TRANSLUCENT = new IrisRenderLayer("translucent", BlockRenderLayer.TRANSLUCENT, TransparencyType.GENERAL_TRANSPARENT);

    // Additional render layers for special cases
    public static final IrisRenderLayer ENTITY_SOLID = new IrisRenderLayer("entity_solid", null, TransparencyType.OPAQUE);
    public static final IrisRenderLayer ENTITY_TRANSLUCENT = new IrisRenderLayer("entity_translucent", null, TransparencyType.GENERAL_TRANSPARENT);
    public static final IrisRenderLayer ENTITY_CUTOUT = new IrisRenderLayer("entity_cutout", null, TransparencyType.OPAQUE);
    public static final IrisRenderLayer GLINT = new IrisRenderLayer("glint", null, TransparencyType.DECAL);
    public static final IrisRenderLayer WATER_MASK = new IrisRenderLayer("water_mask", null, TransparencyType.WATER_MASK);
    public static final IrisRenderLayer LINES = new IrisRenderLayer("lines", null, TransparencyType.LINES);
    public static final IrisRenderLayer TRIPWIRE = new IrisRenderLayer("tripwire", BlockRenderLayer.TRANSLUCENT, TransparencyType.GENERAL_TRANSPARENT);

    private final String name;
    private final BlockRenderLayer vanillaLayer;
    private final TransparencyType transparencyType;

    public IrisRenderLayer(String name, BlockRenderLayer vanillaLayer, TransparencyType transparencyType) {
        this.name = name;
        this.vanillaLayer = vanillaLayer;
        this.transparencyType = transparencyType;
    }

    public String getName() {
        return name;
    }

    public BlockRenderLayer getVanillaLayer() {
        return vanillaLayer;
    }

    @Override
    public TransparencyType getTransparencyType() {
        return transparencyType;
    }

    public boolean isTranslucent() {
        return transparencyType == TransparencyType.GENERAL_TRANSPARENT;
    }

    /**
     * Unwraps this render layer. Since IrisRenderLayer doesn't wrap anything,
     * this just returns itself.
     */
    public IrisRenderLayer unwrap() {
        return this;
    }

    /**
     * Gets the appropriate IrisRenderLayer for a given BlockRenderLayer.
     */
    public static IrisRenderLayer fromBlockRenderLayer(BlockRenderLayer layer) {
        if (layer == null) {
            return SOLID;
        }
        switch (layer) {
            case SOLID:
                return SOLID;
            case CUTOUT:
                return CUTOUT;
            case CUTOUT_MIPPED:
                return CUTOUT_MIPPED;
            case TRANSLUCENT:
                return TRANSLUCENT;
            default:
                return SOLID;
        }
    }

    @Override
    public String toString() {
        return "IrisRenderLayer[" + name + "]";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        IrisRenderLayer other = (IrisRenderLayer) obj;
        return name.equals(other.name);
    }
}
