package net.coderbot.iris.compat.sodium.impl;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.render.GameRendererContext;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

/**
 * A shader program for terrain rendering that uses Iris's patched gbuffer shaders.
 * Extends GlProgram directly (not ChunkProgram) to avoid requiring Sodium-specific
 * uniforms that may be optimized away in the Iris-patched shader.
 */
public class IrisChunkProgram extends GlProgram {
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private final int uModelViewProjectionMatrix;
    private final int uModelScale;
    private final int uTextureScale;
    private final int uBlockTex;
    private final int uLightTex;
    // "Externally managed" uniforms that need explicit GL state reads
    private final int uIrisModelViewMatrix;
    private final int uIrisNormalMatrix;
    private final int uIrisLightmapTextureMatrix;
    private final int aIrisNormal; // attribute location for default normal
    private final ProgramUniforms irisUniforms;
    private final ProgramSamplers irisSamplers;

    public IrisChunkProgram(RenderDevice owner, ResourceLocation name, int handle,
                            ProgramUniforms irisUniforms, ProgramSamplers irisSamplers) {
        super(owner, name, handle);

        this.uModelViewProjectionMatrix = GL20.glGetUniformLocation(handle, "u_ModelViewProjectionMatrix");
        this.uBlockTex = GL20.glGetUniformLocation(handle, "u_BlockTex");
        this.uLightTex = GL20.glGetUniformLocation(handle, "u_LightTex");
        this.uModelScale = GL20.glGetUniformLocation(handle, "u_ModelScale");
        this.uTextureScale = GL20.glGetUniformLocation(handle, "u_TextureScale");
        this.uIrisModelViewMatrix = GL20.glGetUniformLocation(handle, "iris_ModelViewMatrix");
        this.uIrisNormalMatrix = GL20.glGetUniformLocation(handle, "iris_NormalMatrix");
        this.uIrisLightmapTextureMatrix = GL20.glGetUniformLocation(handle, "iris_LightmapTextureMatrix");
        this.aIrisNormal = GL20.glGetAttribLocation(handle, "iris_Normal");

        this.irisUniforms = irisUniforms;
        this.irisSamplers = irisSamplers;
    }

    public void setup(float modelScale, float textureScale) {
        // 0. Explicitly bind the block atlas (unit 0) and lightmap (unit 1).
        // The deferred/composite passes clear all texture bindings, so these must
        // be re-bound before terrain rendering. Latest Iris does this in SodiumShader.setupState().
        net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(
                net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
        net.minecraft.client.Minecraft.getMinecraft().renderEngine.bindTexture(
                new net.minecraft.util.ResourceLocation("dynamic/lightmap_1"));
        net.minecraft.client.renderer.OpenGlHelper.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

        // 1. Run Iris uniform/sampler updates
        if (irisUniforms != null) {
            irisUniforms.update();
        }
        if (irisSamplers != null) {
            irisSamplers.update();
        }

        // 2. Set Sodium-specific uniforms
        if (uBlockTex >= 0) GL20.glUniform1i(uBlockTex, 0);
        if (uLightTex >= 0) GL20.glUniform1i(uLightTex, 1);
        if (uModelScale >= 0) GL20.glUniform3f(uModelScale, modelScale, modelScale, modelScale);
        if (uTextureScale >= 0) GL20.glUniform2f(uTextureScale, textureScale, textureScale);
        if (uModelViewProjectionMatrix >= 0) {
            GL20.glUniformMatrix4(uModelViewProjectionMatrix, false,
                    GameRendererContext.getModelViewProjectionMatrix());
        }

        // 3. Override "externally managed" uniforms from GL state AFTER irisUniforms.
        // These override any values set by BuiltinReplacementUniforms with actual GL state.
        if (uIrisModelViewMatrix >= 0) {
            MATRIX_BUFFER.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
            MATRIX_BUFFER.rewind();
            GL20.glUniformMatrix4(uIrisModelViewMatrix, false, MATRIX_BUFFER);
        }
        if (uIrisNormalMatrix >= 0) {
            MATRIX_BUFFER.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
            MATRIX_BUFFER.rewind();
            GL20.glUniformMatrix4(uIrisNormalMatrix, false, MATRIX_BUFFER);
        }

        // Override iris_LightmapTextureMatrix with identity.
        // Vintagium's vertex format stores lightmap coords as normalized unsigned shorts
        // (already in [0, 1] range). The hardcoded matrix from BuiltinReplacementUniforms
        // assumes vanilla's [0, 240] range and would squish values to near-zero.
        if (uIrisLightmapTextureMatrix >= 0) {
            MATRIX_BUFFER.clear();
            new net.coderbot.iris.vendored.joml.Matrix4f().identity().get(MATRIX_BUFFER);
            MATRIX_BUFFER.rewind();
            GL20.glUniformMatrix4(uIrisLightmapTextureMatrix, false, MATRIX_BUFFER);
        }

        // 4. Set a default normal for terrain since Vintagium's vertex format
        // doesn't include per-vertex normals.
        if (aIrisNormal >= 0) {
            GL20.glVertexAttrib3f(aIrisNormal, 0.0f, 1.0f, 0.0f);
        }
    }
}
