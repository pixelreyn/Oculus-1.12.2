package net.coderbot.iris.compat.function;

/**
 * Compatibility replacement for it.unimi.dsi.fastutil.booleans.BooleanConsumer.
 * A primitive boolean-accepting consumer.
 */
@FunctionalInterface
public interface BooleanConsumer {
    void accept(boolean value);
}
