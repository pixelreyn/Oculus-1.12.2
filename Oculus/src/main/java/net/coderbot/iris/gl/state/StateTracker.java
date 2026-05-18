package net.coderbot.iris.gl.state;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.GlStateManager;

import java.lang.reflect.Field;

/**
 * Polls GlStateManager for fog/blend state changes and fires the
 * appropriate StateUpdateNotifier listeners. This replaces the mixin-based
 * notification approach from 1.16+, since GlStateManager loads too early
 * for late mixins in 1.12.2.
 *
 * Uses raw reflection to avoid IllegalAccessError on package-private inner classes.
 */
public class StateTracker {
    public static final StateTracker INSTANCE = new StateTracker();

    // Reflection fields for FogState
    private Field fogStateField;
    private Field fogEnabledField; // BooleanState fog.fog
    private Field fogModeField;
    private Field fogStartField;
    private Field fogEndField;
    private Field fogDensityField;

    // Reflection fields for BlendState
    private Field blendStateField;
    private Field blendSrcFactorField;
    private Field blendDstFactorField;
    private Field blendSrcFactorAlphaField;
    private Field blendDstFactorAlphaField;

    // Cached state
    private boolean lastFogEnabled;
    private int lastFogMode;
    private float lastFogStart;
    private float lastFogEnd;
    private float lastFogDensity;
    private int lastBlendSrcRgb;
    private int lastBlendDstRgb;
    private int lastBlendSrcAlpha;
    private int lastBlendDstAlpha;
    private int lastBoundTexture;

    // Listeners
    private Runnable fogToggleListener;
    private Runnable fogModeListener;
    private Runnable fogStartListener;
    private Runnable fogEndListener;
    private Runnable fogDensityListener;
    private Runnable blendFuncListener;
    private Runnable bindTextureListener;

    private boolean initialized = false;
    private boolean reflectionFailed = false;

    private StateTracker() {}

    public void init() {
        if (initialized) return;
        initialized = true;

        // Wire up the notifiers
        StateUpdateNotifiers.fogToggleNotifier = listener -> fogToggleListener = listener;
        StateUpdateNotifiers.fogModeNotifier = listener -> fogModeListener = listener;
        StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
        StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
        StateUpdateNotifiers.fogDensityNotifier = listener -> fogDensityListener = listener;
        StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
        StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;

        // Set up reflection fields
        try {
            fogStateField = findField(GlStateManager.class, "fogState", "field_179155_g");
            Object fogState = fogStateField.get(null);

            // FogState: find fields by type
            // Order: BooleanState fog, int mode, float density, float start, float end
            java.util.List<Field> fogBoolFields = new java.util.ArrayList<>();
            java.util.List<Field> fogIntFields = new java.util.ArrayList<>();
            java.util.List<Field> fogFloatFields = new java.util.ArrayList<>();
            for (Field f : fogState.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class) fogIntFields.add(f);
                else if (f.getType() == float.class) fogFloatFields.add(f);
                else if (!f.getType().isPrimitive() && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) fogBoolFields.add(f);
            }
            fogEnabledField = fogBoolFields.isEmpty() ? null : fogBoolFields.get(0); // BooleanState fog
            fogModeField = fogIntFields.isEmpty() ? null : fogIntFields.get(0); // int mode
            if (fogFloatFields.size() >= 3) {
                fogDensityField = fogFloatFields.get(0); // float density
                fogStartField = fogFloatFields.get(1); // float start
                fogEndField = fogFloatFields.get(2); // float end
            }

            blendStateField = findField(GlStateManager.class, "blendState", "field_179157_e");
            Object blendState = blendStateField.get(null);

            // BlendState has 4 int fields: srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha
            // Find them by type (int) since SRG names may vary
            java.util.List<Field> intFields = new java.util.ArrayList<>();
            for (Field f : blendState.getClass().getDeclaredFields()) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    intFields.add(f);
                }
            }
            if (intFields.size() >= 4) {
                blendSrcFactorField = intFields.get(0);
                blendDstFactorField = intFields.get(1);
                blendSrcFactorAlphaField = intFields.get(2);
                blendDstFactorAlphaField = intFields.get(3);
            } else {
                Iris.logger.warn("[StateTracker] BlendState has {} int fields, expected 4", intFields.size());
            }

            // Snapshot initial state
            snapshotState();
        } catch (Exception e) {
            Iris.logger.error("[StateTracker] Failed to set up reflection for GlStateManager state tracking", e);
            reflectionFailed = true;
        }
    }

    private void snapshotState() throws Exception {
        Object fogState = fogStateField.get(null);
        Object fogBoolean = fogEnabledField.get(fogState);
        lastFogEnabled = GlStateManagerHelper.isBooleanStateEnabled(fogBoolean);
        lastFogMode = fogModeField.getInt(fogState);
        lastFogStart = fogStartField.getFloat(fogState);
        lastFogEnd = fogEndField.getFloat(fogState);
        lastFogDensity = fogDensityField.getFloat(fogState);

        Object blendState = blendStateField.get(null);
        lastBlendSrcRgb = blendSrcFactorField.getInt(blendState);
        lastBlendDstRgb = blendDstFactorField.getInt(blendState);
        lastBlendSrcAlpha = blendSrcFactorAlphaField.getInt(blendState);
        lastBlendDstAlpha = blendDstFactorAlphaField.getInt(blendState);

        lastBoundTexture = GlStateManagerHelper.getTextureName(0);
    }

    public void poll() {
        if (!initialized || reflectionFailed) return;

        try {
            pollFog();
            pollBlend();
            pollTexture();
        } catch (Exception e) {
            // Silently ignore
        }
    }

    private void pollFog() throws Exception {
        Object fogState = fogStateField.get(null);

        Object fogBoolean = fogEnabledField.get(fogState);
        boolean fogEnabled = GlStateManagerHelper.isBooleanStateEnabled(fogBoolean);
        if (fogEnabled != lastFogEnabled) {
            lastFogEnabled = fogEnabled;
            if (fogToggleListener != null) fogToggleListener.run();
        }

        int mode = fogModeField.getInt(fogState);
        if (mode != lastFogMode) {
            lastFogMode = mode;
            if (fogModeListener != null) fogModeListener.run();
        }

        float start = fogStartField.getFloat(fogState);
        if (start != lastFogStart) {
            lastFogStart = start;
            if (fogStartListener != null) fogStartListener.run();
        }

        float end = fogEndField.getFloat(fogState);
        if (end != lastFogEnd) {
            lastFogEnd = end;
            if (fogEndListener != null) fogEndListener.run();
        }

        float density = fogDensityField.getFloat(fogState);
        if (density != lastFogDensity) {
            lastFogDensity = density;
            if (fogDensityListener != null) fogDensityListener.run();
        }
    }

    private void pollBlend() throws Exception {
        Object blendState = blendStateField.get(null);

        int src = blendSrcFactorField.getInt(blendState);
        int dst = blendDstFactorField.getInt(blendState);
        int srcA = blendSrcFactorAlphaField.getInt(blendState);
        int dstA = blendDstFactorAlphaField.getInt(blendState);

        if (src != lastBlendSrcRgb || dst != lastBlendDstRgb
                || srcA != lastBlendSrcAlpha || dstA != lastBlendDstAlpha) {
            lastBlendSrcRgb = src;
            lastBlendDstRgb = dst;
            lastBlendSrcAlpha = srcA;
            lastBlendDstAlpha = dstA;
            if (blendFuncListener != null) blendFuncListener.run();
        }
    }

    private void pollTexture() {
        int bound = GlStateManagerHelper.getTextureName(0);
        if (bound != lastBoundTexture) {
            lastBoundTexture = bound;
            if (bindTextureListener != null) bindTextureListener.run();
        }
    }

    private static Field findField(Class<?> clazz, String mcpName, String srgName) {
        try {
            Field f = clazz.getDeclaredField(mcpName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            try {
                Field f = clazz.getDeclaredField(srgName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e2) {
                throw new RuntimeException("Could not find field '" + mcpName + "' or '" + srgName + "' in " + clazz.getName(), e2);
            }
        }
    }
}
