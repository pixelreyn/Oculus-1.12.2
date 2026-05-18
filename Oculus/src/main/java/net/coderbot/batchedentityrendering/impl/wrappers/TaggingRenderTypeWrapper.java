package net.coderbot.batchedentityrendering.impl.wrappers;

import net.coderbot.batchedentityrendering.impl.BlendingStateHolder;
import net.coderbot.batchedentityrendering.impl.IrisRenderLayer;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.batchedentityrendering.impl.WrappableRenderType;
import net.minecraft.util.BlockRenderLayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A wrapper for IrisRenderLayer that adds a tag for identification.
 * Adapted for 1.12.2 - no longer extends RenderType since that doesn't exist.
 */
public class TaggingRenderTypeWrapper extends IrisRenderLayer implements WrappableRenderType {
    private final int tag;
    private final IrisRenderLayer wrapped;

    public TaggingRenderTypeWrapper(String name, IrisRenderLayer wrapped, int tag) {
        super("tagged_" + name, wrapped.getVanillaLayer(), wrapped.getTransparencyType());
        this.tag = tag;
        this.wrapped = wrapped;
    }

    @Override
    public IrisRenderLayer unwrap() {
        return this.wrapped;
    }

    @Override
    public TransparencyType getTransparencyType() {
        return this.wrapped.getTransparencyType();
    }

    @Override
    public BlockRenderLayer getVanillaLayer() {
        return this.wrapped.getVanillaLayer();
    }

    public int getTag() {
        return tag;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (object == null) {
            return false;
        }

        if (object.getClass() != this.getClass()) {
            return false;
        }

        TaggingRenderTypeWrapper other = (TaggingRenderTypeWrapper) object;

        return this.tag == other.tag && Objects.equals(this.wrapped, other.wrapped);
    }

    @Override
    public int hashCode() {
        // Add one so that we don't have the exact same hash as the wrapped object.
        return this.wrapped.hashCode() + 1;
    }

    @Override
    public String toString() {
        return "tagged(" + tag + "):" + this.wrapped.toString();
    }
}
