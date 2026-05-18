package net.coderbot.iris.gl.state;

/**
 * Holds some standard update notifiers for various elements of GL state. Currently, this class has a few listeners for
 * fog-related values.
 */
public class StateUpdateNotifiers {
    // No-op notifier for cases where GlStateManager mixins aren't available
    private static final ValueUpdateNotifier NOOP = listener -> {};

    public static ValueUpdateNotifier fogToggleNotifier = NOOP;
    public static ValueUpdateNotifier fogModeNotifier = NOOP;
    public static ValueUpdateNotifier fogStartNotifier = NOOP;
    public static ValueUpdateNotifier fogEndNotifier = NOOP;
    public static ValueUpdateNotifier fogDensityNotifier = NOOP;
    public static ValueUpdateNotifier blendFuncNotifier = NOOP;
    public static ValueUpdateNotifier bindTextureNotifier = NOOP;
    public static ValueUpdateNotifier normalTextureChangeNotifier = NOOP;
    public static ValueUpdateNotifier specularTextureChangeNotifier = NOOP;
    public static ValueUpdateNotifier phaseChangeNotifier = NOOP;
}
