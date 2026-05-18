package net.coderbot.iris.mixin.statelisteners;

/**
 * Stub interface for BooleanState accessor.
 * In 1.12.2, GlStateManager.BooleanState is a private inner class,
 * so we cannot use a mixin accessor for it.
 *
 * Instead, we use reflection or direct field access in the places
 * where this would have been used.
 */
public interface BooleanStateAccessor {
    boolean isEnabled();
}
