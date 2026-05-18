package net.coderbot.iris.compat.sodium.impl;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Optional;

/**
 * Manages Iris-patched shader programs for Vintagium/Sodium terrain rendering.
 * Replaces Sodium's forward-rendering terrain shader with Iris's gbuffer shaders
 * that have been patched by SodiumTerrainTransformer to use Sodium's vertex format.
 *
 * Based on the original Oculus 1.16 IrisChunkProgramOverrides.
 */
public class IrisChunkProgramOverrides {
    private static final ShaderConstants EMPTY_CONSTANTS = ShaderConstants.builder().build();

    private final EnumMap<IrisTerrainPass, IrisChunkProgram> programs = new EnumMap<>(IrisTerrainPass.class);
    private int versionCounterForSodiumShaderReload = -1;

    @Nullable
    private GlShader createVertexShader(RenderDevice device, IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        Optional<String> source;
        switch (pass) {
            case SHADOW:       source = pipeline.getShadowVertexShaderSource(); break;
            case SOLID:        source = pipeline.getTerrainVertexShaderSource(); break;
            case TRANSLUCENT:  source = pipeline.getTranslucentVertexShaderSource(); break;
            default: throw new IllegalArgumentException("Unknown pass: " + pass);
        }
        if (!source.isPresent()) return null;
        // Apply GLSL 120 compatibility fixes
        String fixedSource = net.coderbot.iris.gl.shader.GlShader.fixIntegerModulo(source.get());
        fixedSource = net.coderbot.iris.gl.shader.GlShader.fixIntegerMinMax(fixedSource);
        return new GlShader(device, ShaderType.VERTEX,
                new ResourceLocation("oculus", "sodium-terrain-" + pass.name().toLowerCase() + ".vsh"),
                fixedSource, EMPTY_CONSTANTS);
    }

    @Nullable
    private GlShader createFragmentShader(RenderDevice device, IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        Optional<String> source;
        switch (pass) {
            case SHADOW:       source = pipeline.getShadowFragmentShaderSource(); break;
            case SOLID:        source = pipeline.getTerrainFragmentShaderSource(); break;
            case TRANSLUCENT:  source = pipeline.getTranslucentFragmentShaderSource(); break;
            default: throw new IllegalArgumentException("Unknown pass: " + pass);
        }
        if (!source.isPresent()) return null;
        // Apply GLSL 120 compatibility fixes
        String fixedSource = net.coderbot.iris.gl.shader.GlShader.fixIntegerModulo(source.get());
        fixedSource = net.coderbot.iris.gl.shader.GlShader.fixIntegerMinMax(fixedSource);
        return new GlShader(device, ShaderType.FRAGMENT,
                new ResourceLocation("oculus", "sodium-terrain-" + pass.name().toLowerCase() + ".fsh"),
                fixedSource, EMPTY_CONSTANTS);
    }

    @Nullable
    private IrisChunkProgram createShader(RenderDevice device, IrisTerrainPass pass, SodiumTerrainPipeline pipeline) {
        GlShader vertShader = createVertexShader(device, pass, pipeline);
        GlShader fragShader = createFragmentShader(device, pass, pipeline);

        if (vertShader == null || fragShader == null) {
            if (vertShader != null) vertShader.delete();
            if (fragShader != null) fragShader.delete();
            return null;
        }

        try {
            return GlProgram.builder(new ResourceLocation("oculus", "chunk_shader_" + pass.name().toLowerCase()))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    // Bind Iris attribute names to Sodium/Vintagium's vertex attribute indices
                    .bindAttribute("iris_Pos", ChunkShaderBindingPoints.POSITION)
                    .bindAttribute("iris_Color", ChunkShaderBindingPoints.COLOR)
                    .bindAttribute("iris_TexCoord", ChunkShaderBindingPoints.TEX_COORD)
                    .bindAttribute("iris_LightCoord", ChunkShaderBindingPoints.LIGHT_COORD)
                    .bindAttribute("iris_ModelOffset", ChunkShaderBindingPoints.MODEL_OFFSET)
                    // Extended attributes for shader packs (block ID, mid-tex, tangent)
                    .bindAttribute("mc_Entity", ChunkShaderBindingPoints.ENTITY_DATA)
                    .bindAttribute("mc_midTexCoord", ChunkShaderBindingPoints.MID_TEX_COORD)
                    .bindAttribute("at_tangent", ChunkShaderBindingPoints.TANGENT)
                    .build((name, program) -> {
                        ProgramUniforms uniforms = pipeline.initUniforms(program);
                        ProgramSamplers samplers = null;
                        try {
                            if (pass == IrisTerrainPass.SHADOW) {
                                samplers = pipeline.initShadowSamplers(program);
                            } else {
                                samplers = pipeline.initTerrainSamplers(program);
                            }
                        } catch (Exception e) {
                            Iris.logger.warn("Failed to init terrain samplers for pass {}", pass, e);
                        }

                        return new IrisChunkProgram(device, name, program, uniforms, samplers);
                    });
        } catch (Exception e) {
            Iris.logger.error("Failed to create Iris terrain shader for pass {}", pass, e);
            return null;
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    /**
     * Creates a built-in GLSL 110 shadow shader as fallback when the shader pack's
     * shadow shader requires a higher GLSL version (e.g. 130+ unsupported on macOS).
     */
    @Nullable
    private IrisChunkProgram createFallbackShadowShader(RenderDevice device) {
        // BSL (and most modern packs) RENDER the shadow map with a non-linear
        // distortion (more resolution near the player) in their shadow .vsh and
        // SAMPLE it back with the same distortion in composite. On macOS BSL's
        // GLSL-130 shadow shader fails to compile and we fall back to this
        // GLSL-110 shader; if it renders UNDISTORTED while composite samples
        // DISTORTED, the lookup is only correct near the projection centre and
        // warps badly outward (blocky/tiny shadows, rectangular bands on flat
        // ground, sun-tracking). Replicate BSL's exact distortion here:
        //   program/shadow.glsl:  dist=length(clip.xy);
        //                         distortFactor = dist*shadowMapBias + (1-bias);
        //                         clip.xy /= distortFactor;  clip.z *= 0.2;
        //   settings.glsl:        shadowMapBias = 1.0 - 25.6 / shadowDistance;
        // shadowDistance == ortho half-plane == 1.0 / ShadowRenderer.PROJECTION.m00.
        float halfPlaneLength = 256.0f; // BSL default; only used if PROJECTION unset
        try {
            net.coderbot.iris.vendored.joml.Matrix4f p =
                    net.coderbot.iris.pipeline.ShadowRenderer.PROJECTION;
            if (p != null && Math.abs(p.m00()) > 1.0e-6f) {
                halfPlaneLength = 1.0f / p.m00();
            }
        } catch (Throwable ignored) {}
        float shadowMapBias = 1.0f - 25.6f / halfPlaneLength;
        String SMB = Float.toString(shadowMapBias);

        String vertSource =
                "#version 110\n" +
                "attribute vec3 a_Pos;\n" +
                "attribute vec2 a_TexCoord;\n" +
                "attribute vec4 d_ModelOffset;\n" +
                "varying vec2 v_TexCoord;\n" +
                "uniform mat4 u_ModelViewProjectionMatrix;\n" +
                "uniform vec3 u_ModelScale;\n" +
                "uniform vec2 u_TextureScale;\n" +
                "void main() {\n" +
                "    vec3 pos = (a_Pos * u_ModelScale) + d_ModelOffset.xyz;\n" +
                "    vec4 clip = u_ModelViewProjectionMatrix * vec4(pos, 1.0);\n" +
                // --- BSL shadow distortion (must match program/shadow.glsl) ---
                "    float dist = sqrt(clip.x * clip.x + clip.y * clip.y);\n" +
                "    float distortFactor = dist * " + SMB + " + (1.0 - " + SMB + ");\n" +
                "    clip.xy *= 1.0 / distortFactor;\n" +
                "    clip.z *= 0.2;\n" +
                "    gl_Position = clip;\n" +
                "    v_TexCoord = a_TexCoord * u_TextureScale;\n" +
                "}\n";

        String fragSource =
                "#version 110\n" +
                "varying vec2 v_TexCoord;\n" +
                "uniform sampler2D u_BlockTex;\n" +
                "uniform sampler2D u_LightTex;\n" +
                "void main() {\n" +
                "    vec4 texColor = texture2D(u_BlockTex, v_TexCoord);\n" +
                "    if (texColor.a < 0.1) discard;\n" +
                // shadowtex0 (depth) is written automatically from gl_FragCoord.z with
                // the correct light-space MVP; shadowcolor0 carries surface albedo so
                // shader packs can tint colored/translucent shadows (matches Iris).
                "    gl_FragColor = texColor;\n" +
                "}\n";

        GlShader vertShader = new GlShader(device, ShaderType.VERTEX,
                new ResourceLocation("oculus", "shadow-fallback.vsh"), vertSource, EMPTY_CONSTANTS);
        GlShader fragShader = new GlShader(device, ShaderType.FRAGMENT,
                new ResourceLocation("oculus", "shadow-fallback.fsh"), fragSource, EMPTY_CONSTANTS);

        try {
            return GlProgram.builder(new ResourceLocation("oculus", "chunk_shader_shadow_fallback"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Pos", ChunkShaderBindingPoints.POSITION)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.TEX_COORD)
                    .bindAttribute("d_ModelOffset", ChunkShaderBindingPoints.MODEL_OFFSET)
                    .build((name, program) ->
                            new IrisChunkProgram(device, name, program, null, null));
        } catch (Exception e) {
            Iris.logger.error("Failed to create fallback shadow shader", e);
            return null;
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    public void createShaders(SodiumTerrainPipeline pipeline, RenderDevice device) {
        deleteShaders();

        if (pipeline == null) return;

        for (IrisTerrainPass pass : IrisTerrainPass.values()) {
            if (pass == IrisTerrainPass.SHADOW && !pipeline.hasShadowPass()) {
                this.programs.put(pass, null);
                continue;
            }
            IrisChunkProgram program = null;
            try {
                program = createShader(device, pass, pipeline);
            } catch (Exception e) {
                Iris.logger.error("Failed to create Iris terrain shader for pass {}", pass, e);
            }
            if (program == null && pass == IrisTerrainPass.SHADOW) {
                // Shader pack's shadow shader failed (likely GLSL version too high).
                // Use built-in GLSL 110 fallback that outputs greyscale depth.
                try {
                    program = createFallbackShadowShader(device);
                    if (program != null) {
                        Iris.logger.info("Using built-in GLSL 110 fallback shadow shader");
                    }
                } catch (Exception e) {
                    Iris.logger.error("Failed to create fallback shadow shader", e);
                }
            }
            this.programs.put(pass, program);
        }

        Iris.logger.info("Created Iris terrain shaders: solid={}, translucent={}, shadow={}",
                programs.get(IrisTerrainPass.SOLID) != null,
                programs.get(IrisTerrainPass.TRANSLUCENT) != null,
                programs.get(IrisTerrainPass.SHADOW) != null);
    }

    @Nullable
    public IrisChunkProgram getProgramOverride(RenderDevice device, boolean translucent) {
        WorldRenderingPipeline worldPipeline = Iris.getPipelineManager().getPipelineNullable();
        SodiumTerrainPipeline sodiumPipeline = null;

        if (worldPipeline != null) {
            sodiumPipeline = worldPipeline.getSodiumTerrainPipeline();
        }

        int currentVersion = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
        if (versionCounterForSodiumShaderReload != currentVersion) {
            versionCounterForSodiumShaderReload = currentVersion;
            deleteShaders();
            createShaders(sodiumPipeline, device);
        }

        return programs.get(translucent ? IrisTerrainPass.TRANSLUCENT : IrisTerrainPass.SOLID);
    }

    @Nullable
    public IrisChunkProgram getShadowProgramOverride(RenderDevice device) {
        // Ensure shaders are created (same version check as getProgramOverride)
        WorldRenderingPipeline worldPipeline = Iris.getPipelineManager().getPipelineNullable();
        SodiumTerrainPipeline sodiumPipeline = null;
        if (worldPipeline != null) {
            sodiumPipeline = worldPipeline.getSodiumTerrainPipeline();
        }

        int currentVersion = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
        if (versionCounterForSodiumShaderReload != currentVersion) {
            versionCounterForSodiumShaderReload = currentVersion;
            deleteShaders();
            createShaders(sodiumPipeline, device);
        }

        return programs.get(IrisTerrainPass.SHADOW);
    }

    public void deleteShaders() {
        for (IrisChunkProgram program : programs.values()) {
            if (program != null) {
                program.delete();
            }
        }
        programs.clear();
    }

    public enum IrisTerrainPass {
        SOLID, TRANSLUCENT, SHADOW
    }
}
