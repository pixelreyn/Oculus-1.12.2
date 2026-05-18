package net.coderbot.iris.gl.state;

import net.coderbot.iris.Iris;
import net.minecraft.client.renderer.GlStateManager;

import java.lang.reflect.Field;

/**
 * Provides access to GlStateManager's private static fields using reflection,
 * replacing the GlStateManagerAccessor mixin which cannot be loaded as a late mixin
 * because GlStateManager is loaded too early in MC 1.12.2.
 *
 * Field references are cached for performance. Both MCP (dev) and SRG (production)
 * field names are tried to ensure compatibility in both environments.
 */
public final class GlStateManagerHelper {

    // Cached Field references for GlStateManager's static fields
    private static final Field ACTIVE_TEXTURE_UNIT;
    private static final Field BLEND_STATE;
    private static final Field DEPTH_STATE;
    private static final Field FOG_STATE;
    private static final Field TEXTURE_STATE;
    private static final Field COLOR_MASK_STATE;

    // Cached Field reference for BooleanState.currentState
    private static final Field BOOLEAN_STATE_CURRENT_STATE;
    // Cached Field reference for TextureState.textureName
    private static final Field TEXTURE_STATE_TEXTURE_NAME;

    static {
        ACTIVE_TEXTURE_UNIT = findField(GlStateManager.class, "activeTextureUnit", "field_179162_o");
        BLEND_STATE = findField(GlStateManager.class, "blendState", "field_179157_e");
        DEPTH_STATE = findField(GlStateManager.class, "depthState", "field_179154_f");
        FOG_STATE = findField(GlStateManager.class, "fogState", "field_179155_g");
        TEXTURE_STATE = findField(GlStateManager.class, "textureState", "field_179174_p");
        COLOR_MASK_STATE = findField(GlStateManager.class, "colorMaskState", "field_179171_s");

        // BooleanState is a package-private inner class; find it by checking inner classes
        Class<?> booleanStateClass = null;
        for (Class<?> inner : GlStateManager.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("BooleanState")) {
                booleanStateClass = inner;
                break;
            }
        }
        if (booleanStateClass != null) {
            BOOLEAN_STATE_CURRENT_STATE = findField(booleanStateClass, "currentState", "field_179201_b");
        } else {
            Iris.logger.error("[GlStateManagerHelper] Could not find GlStateManager.BooleanState inner class!");
            BOOLEAN_STATE_CURRENT_STATE = null;
        }

        // Find TextureState inner class and its textureName field
        Class<?> textureStateClass = null;
        for (Class<?> inner : GlStateManager.class.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("TextureState")) {
                textureStateClass = inner;
                break;
            }
        }
        if (textureStateClass != null) {
            TEXTURE_STATE_TEXTURE_NAME = findField(textureStateClass, "textureName", "field_179059_b");
        } else {
            Iris.logger.error("[GlStateManagerHelper] Could not find GlStateManager.TextureState inner class!");
            TEXTURE_STATE_TEXTURE_NAME = null;
        }
    }

    private GlStateManagerHelper() {
        // no construction
    }

    /**
     * Returns the active texture unit index (0-based, e.g. 0 for GL_TEXTURE0).
     */
    public static int getActiveTexture() {
        try {
            return ACTIVE_TEXTURE_UNIT.getInt(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.activeTextureUnit", e);
        }
    }

    /**
     * Returns the BlendState from GlStateManager.
     */
    public static GlStateManager.BlendState getBlendState() {
        try {
            return (GlStateManager.BlendState) BLEND_STATE.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.blendState", e);
        }
    }

    /**
     * Returns the DepthState from GlStateManager.
     */
    public static GlStateManager.DepthState getDepthState() {
        try {
            return (GlStateManager.DepthState) DEPTH_STATE.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.depthState", e);
        }
    }

    /**
     * Returns the FogState from GlStateManager.
     */
    public static GlStateManager.FogState getFogState() {
        try {
            return (GlStateManager.FogState) FOG_STATE.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.fogState", e);
        }
    }

    // GlStateManager only tracks 8 texture units in 1.12.2
    private static final int MAX_TRACKED_UNITS = 8;

    /**
     * Gets the texture name (GL texture ID) bound to the given texture unit.
     * Returns 0 for units beyond GlStateManager's tracking range.
     */
    public static int getTextureName(int unit) {
        if (unit >= MAX_TRACKED_UNITS) return 0;
        try {
            Object[] textureStates = (Object[]) TEXTURE_STATE.get(null);
            return TEXTURE_STATE_TEXTURE_NAME.getInt(textureStates[unit]);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.textureState[" + unit + "].textureName", e);
        }
    }

    /**
     * Sets the texture name (GL texture ID) for the given texture unit in GlStateManager's tracking.
     * Silently ignored for units beyond GlStateManager's tracking range.
     */
    public static void setTextureName(int unit, int texture) {
        if (unit >= MAX_TRACKED_UNITS) return;
        try {
            Object[] textureStates = (Object[]) TEXTURE_STATE.get(null);
            TEXTURE_STATE_TEXTURE_NAME.setInt(textureStates[unit], texture);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write GlStateManager.textureState[" + unit + "].textureName", e);
        }
    }

    /**
     * Returns the ColorMask from GlStateManager.
     */
    public static GlStateManager.ColorMask getColorMask() {
        try {
            return (GlStateManager.ColorMask) COLOR_MASK_STATE.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GlStateManager.colorMaskState", e);
        }
    }

    /**
     * Reads the currentState field from a BooleanState instance.
     * This replaces the BooleanStateAccessor mixin interface cast.
     *
     * @param booleanState A GlStateManager.BooleanState instance (e.g., blendState.blend, fogState.fog)
     * @return true if the boolean state is currently enabled
     */
    public static boolean isBooleanStateEnabled(Object booleanState) {
        if (BOOLEAN_STATE_CURRENT_STATE == null) {
            throw new RuntimeException("BooleanState.currentState field not found");
        }
        try {
            return BOOLEAN_STATE_CURRENT_STATE.getBoolean(booleanState);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read BooleanState.currentState", e);
        }
    }

    /**
     * Finds a field by trying multiple names (MCP name first, then SRG name).
     * Makes the field accessible.
     */
    private static Field findField(Class<?> clazz, String mcpName, String srgName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(mcpName);
        } catch (NoSuchFieldException e) {
            try {
                field = clazz.getDeclaredField(srgName);
            } catch (NoSuchFieldException e2) {
                Iris.logger.error("[GlStateManagerHelper] Could not find field '{}' or '{}' in {}", mcpName, srgName, clazz.getName());
                throw new RuntimeException("Could not find field '" + mcpName + "' or '" + srgName + "' in " + clazz.getName(), e2);
            }
        }
        field.setAccessible(true);
        return field;
    }
}
