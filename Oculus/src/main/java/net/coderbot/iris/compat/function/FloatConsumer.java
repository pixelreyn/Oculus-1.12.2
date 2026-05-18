package net.coderbot.iris.compat.function;

/**
 * Compatibility replacement for it.unimi.dsi.fastutil.floats.FloatConsumer.
 * A primitive float-accepting consumer.
 */
@FunctionalInterface
public interface FloatConsumer {
    void accept(float value);
}
