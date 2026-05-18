package net.coderbot.batchedentityrendering.impl;

/**
 * Interface for render types that can be unwrapped to reveal their underlying type.
 * Adapted for 1.12.2 to use IrisRenderLayer instead of RenderType.
 */
public interface WrappableRenderType {
    /**
     * Returns the underlying wrapped IrisRenderLayer. Might return itself if this type doesn't wrap anything.
     */
    IrisRenderLayer unwrap();
}
