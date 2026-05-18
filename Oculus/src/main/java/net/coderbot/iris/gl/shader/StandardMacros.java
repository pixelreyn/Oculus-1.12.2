package net.coderbot.iris.gl.shader;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.shaderpack.StringPair;
import net.coderbot.iris.texture.format.TextureFormat;
import net.coderbot.iris.texture.format.TextureFormatLoader;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.Util;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StandardMacros {
    private static final Pattern SEMVER_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.*(?<bugfix>\\d*)(.*)");

    private static void define(List<StringPair> defines, String key) {
        defines.add(new StringPair(key, ""));
    }

    private static void define(List<StringPair> defines, String key, String value) {
        defines.add(new StringPair(key, value));
    }

    public static Iterable<StringPair> createStandardEnvironmentDefines() {
        ArrayList<StringPair> standardDefines = new ArrayList<>();

        define(standardDefines, "MC_VERSION", getMcVersion());
        define(standardDefines, "MC_GL_VERSION", getGlVersion(GL11.GL_VERSION));
        define(standardDefines, "MC_GLSL_VERSION", getGlVersion(GL20.GL_SHADING_LANGUAGE_VERSION));
        define(standardDefines, getOsString());
        define(standardDefines, getVendor());
        define(standardDefines, getRenderer());

        for (String glExtension : getGlExtensions()) {
            define(standardDefines, glExtension);
        }

        define(standardDefines, "MC_NORMAL_MAP");
        define(standardDefines, "MC_SPECULAR_MAP");
        define(standardDefines, "MC_RENDER_QUALITY", "1.0");
        define(standardDefines, "MC_SHADOW_QUALITY", "1.0");
        define(standardDefines, "MC_HAND_DEPTH", Float.toString(HandRenderer.DEPTH));

        TextureFormat textureFormat = TextureFormatLoader.getFormat();
        if (textureFormat != null) {
            for (String define : textureFormat.getDefines()) {
                define(standardDefines, define);
            }
        }

        getRenderStages().forEach((stage, index) -> define(standardDefines, stage, index));

        for (String irisDefine : getIrisDefines()) {
            define(standardDefines, irisDefine);
        }

        return ImmutableList.copyOf(standardDefines);
    }

    /**
     * Gets the current mc version String in a 5 digit format
     * For 1.12.2, returns "11202"
     */
    public static String getMcVersion() {
        // Hardcoded for 1.12.2
        return "11202";
    }

    /**
     * Returns the current GL Version using regex
     */
    public static String getGlVersion(int name) {
        String info = GL11.glGetString(name);

        Matcher matcher = SEMVER_PATTERN.matcher(Objects.requireNonNull(info));

        if (!matcher.matches()) {
            throw new IllegalStateException("Could not parse GL version from \"" + info + "\"");
        }

        String major = group(matcher, "major");
        String minor = group(matcher, "minor");
        String bugfix = group(matcher, "bugfix");

        if (bugfix == null) {
            bugfix = "0";
        }

        if (major == null || minor == null) {
            throw new IllegalStateException("Could not parse GL version from \"" + info + "\"");
        }

        return major + minor + bugfix;
    }

    public static String group(Matcher matcher, String name) {
        try {
            return matcher.group(name);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    /**
     * Returns the current OS String
     */
    public static String getOsString() {
        switch (Util.getOSType()) {
            case OSX:
                return "MC_OS_MAC";
            case LINUX:
                return "MC_OS_LINUX";
            case WINDOWS:
                return "MC_OS_WINDOWS";
            case SOLARIS:
            case UNKNOWN:
            default:
                return "MC_OS_UNKNOWN";
        }
    }

    /**
     * Returns a string indicating the graphics card being used
     */
    public static String getVendor() {
        String vendor = Objects.requireNonNull(GL11.glGetString(GL11.GL_VENDOR)).toLowerCase(Locale.ROOT);
        if (vendor.startsWith("ati")) {
            return "MC_GL_VENDOR_ATI";
        } else if (vendor.startsWith("intel")) {
            return "MC_GL_VENDOR_INTEL";
        } else if (vendor.startsWith("nvidia")) {
            return "MC_GL_VENDOR_NVIDIA";
        } else if (vendor.startsWith("amd")) {
            return "MC_GL_VENDOR_AMD";
        } else if (vendor.startsWith("x.org")) {
            return "MC_GL_VENDOR_XORG";
        }
        return "MC_GL_VENDOR_OTHER";
    }

    /**
     * Returns the graphics driver being used
     */
    public static String getRenderer() {
        String renderer = Objects.requireNonNull(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase(Locale.ROOT);
        if (renderer.startsWith("amd")) {
            return "MC_GL_RENDERER_RADEON";
        } else if (renderer.startsWith("ati")) {
            return "MC_GL_RENDERER_RADEON";
        } else if (renderer.startsWith("radeon")) {
            return "MC_GL_RENDERER_RADEON";
        } else if (renderer.startsWith("gallium")) {
            return "MC_GL_RENDERER_GALLIUM";
        } else if (renderer.startsWith("intel")) {
            return "MC_GL_RENDERER_INTEL";
        } else if (renderer.startsWith("geforce")) {
            return "MC_GL_RENDERER_GEFORCE";
        } else if (renderer.startsWith("nvidia")) {
            return "MC_GL_RENDERER_GEFORCE";
        } else if (renderer.startsWith("quadro")) {
            return "MC_GL_RENDERER_QUADRO";
        } else if (renderer.startsWith("nvs")) {
            return "MC_GL_RENDERER_QUADRO";
        } else if (renderer.startsWith("mesa")) {
            return "MC_GL_RENDERER_MESA";
        }
        return "MC_GL_RENDERER_OTHER";
    }

    /**
     * Returns the list of currently enabled GL extensions
     */
    public static Set<String> getGlExtensions() {
        String[] extensions = Objects.requireNonNull(GL11.glGetString(GL11.GL_EXTENSIONS)).split("\\s+");
        return Arrays.stream(extensions).map(s -> "MC_" + s).collect(Collectors.toSet());
    }

    public static Map<String, String> getRenderStages() {
        Map<String, String> stages = new HashMap<>();
        for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
            stages.put("MC_RENDER_STAGE_" + phase.name(), String.valueOf(phase.ordinal()));
        }
        return stages;
    }

    /**
     * Returns the list of Iris-exclusive uniforms supported in the current version of Iris.
     */
    public static List<String> getIrisDefines() {
        List<String> defines = new ArrayList<>();
        return defines;
    }
}
