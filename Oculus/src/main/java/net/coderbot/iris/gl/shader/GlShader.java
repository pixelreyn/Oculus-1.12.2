// This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.

package net.coderbot.iris.gl.shader;

import net.coderbot.iris.gl.GLDebug;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

import java.util.Locale;

/**
 * A compiled OpenGL shader object.
 */
public class GlShader extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger(GlShader.class);

    private final String name;

    public GlShader(ShaderType type, String name, String src) {
        super(createShader(type, name, src));

        this.name = name;
    }

    private static int createShader(ShaderType type, String name, String src) {
        // Fix integer modulo for GLSL 120 (macOS GL 2.1) — '%' on ints requires GLSL 130+
        src = fixIntegerModulo(src);
        // Fix min/max with integer args — GLSL 120 only has float overloads
        src = fixIntegerMinMax(src);

        int handle = OpenGlHelper.glCreateShader(type.id);
        ShaderWorkarounds.safeShaderSource(handle, src);
        OpenGlHelper.glCompileShader(handle);

        GLDebug.nameObject(KHRDebug.GL_SHADER, handle, name + "(" + type.name().toLowerCase(Locale.ROOT) + ")");

        String log = IrisRenderSystem.getShaderInfoLog(handle);

        if (!log.isEmpty()) {
            LOGGER.warn("Shader compilation log for " + name + ": " + log);
        }

        int result = OpenGlHelper.glGetShaderi(handle, GL20.GL_COMPILE_STATUS);

        if (result != GL11.GL_TRUE) {
            throw new RuntimeException("Shader compilation failed, see log for details");
        }

        return handle;
    }

    /**
     * Replaces integer modulo operator '%' with an equivalent expression
     * for GLSL 120 compatibility. GLSL 120 (macOS GL 2.1) doesn't support
     * '%' on integer types — that requires GLSL 130+.
     *
     * Injects a helper function and replaces 'a % b' with 'iris_imod(a, b)'.
     */
    public static String fixIntegerModulo(String src) {
        if (!src.contains("%")) {
            return src;
        }

        // Check if the shader uses #version 120 or no version (defaults to 110)
        // Only apply the fix for versions < 130
        boolean needsFix = true;
        java.util.regex.Matcher versionMatcher = java.util.regex.Pattern
                .compile("#version\\s+(\\d+)").matcher(src);
        if (versionMatcher.find()) {
            int version = Integer.parseInt(versionMatcher.group(1));
            if (version >= 130) {
                needsFix = false;
            }
        }

        if (!needsFix) {
            return src;
        }

        // Replace 'a % b' patterns with 'iris_imod(a, b)'
        // This regex matches: expression % expression
        // We handle simple cases: identifier/number % identifier/number
        // and parenthesized expressions
        String modPattern = "(\\w+(?:\\([^)]*\\))?|\\([^)]+\\))\\s*%\\s*(\\w+(?:\\([^)]*\\))?|\\([^)]+\\))";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(modPattern);

        StringBuilder result = new StringBuilder(src);
        boolean hasReplacement = false;

        // Iterate and replace (process from end to preserve offsets)
        java.util.regex.Matcher m = pattern.matcher(src);
        java.util.List<int[]> replacements = new java.util.ArrayList<>();
        while (m.find()) {
            // Skip if inside a comment or preprocessor directive
            String before = src.substring(Math.max(0, m.start() - 2), m.start());
            if (before.contains("//") || before.contains("/*")) continue;

            // Check line context - skip preprocessor lines
            int lineStart = src.lastIndexOf('\n', m.start());
            String linePrefix = src.substring(lineStart + 1, m.start()).trim();
            if (linePrefix.startsWith("#")) continue;

            replacements.add(new int[]{m.start(), m.end()});
            hasReplacement = true;
        }

        if (!hasReplacement) {
            return src;
        }

        // Apply replacements from end to start
        StringBuilder sb = new StringBuilder(src);
        for (int i = replacements.size() - 1; i >= 0; i--) {
            int[] rep = replacements.get(i);
            java.util.regex.Matcher m2 = pattern.matcher(src.substring(rep[0], rep[1]));
            if (m2.matches()) {
                sb.replace(rep[0], rep[1], "iris_imod(" + m2.group(1) + ", " + m2.group(2) + ")");
            }
        }

        // Inject the helper function after all preprocessor directives at the top
        // (#version, #extension, #define etc. must come before non-preprocessor tokens)
        String helperFunc = "\nint iris_imod(int a, int b) { return a - b * (a / b); }\n";
        String[] lines = sb.toString().split("\n");
        int insertLine = 0;
        for (int idx = 0; idx < lines.length; idx++) {
            String trimmed = lines[idx].trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                insertLine = idx + 1;
            } else {
                break;
            }
        }
        // Rebuild with helper inserted at the right position
        StringBuilder finalSb = new StringBuilder();
        for (int idx = 0; idx < lines.length; idx++) {
            finalSb.append(lines[idx]).append('\n');
            if (idx + 1 == insertLine) {
                finalSb.append(helperFunc);
            }
        }
        if (insertLine == 0) {
            finalSb.insert(0, helperFunc);
        }
        return finalSb.toString();
    }

    /**
     * Fixes ambiguous min(int,int) / max(int,int) / clamp(int,int,int) / abs(int)
     * calls for GLSL 120 by enabling GL_EXT_gpu_shader4 which provides integer
     * overloads of these built-in functions.
     */
    public static String fixIntegerMinMax(String src) {
        // Only apply to GLSL 120 shaders
        if (!src.contains("#version 120")) {
            return src;
        }
        // Don't add if already present
        if (src.contains("GL_EXT_gpu_shader4")) {
            return src;
        }
        // Insert the extension enable right after the #version directive
        src = src.replace("#version 120",
                "#version 120\n#extension GL_EXT_gpu_shader4 : enable");
        return src;
    }

    public String getName() {
        return this.name;
    }

    public int getHandle() {
        return this.getGlId();
    }

    @Override
    protected void destroyInternal() {
        OpenGlHelper.glDeleteShader(this.getGlId());
    }
}
