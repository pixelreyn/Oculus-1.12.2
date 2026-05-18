package net.coderbot.iris.uniforms;

import net.coderbot.iris.vendored.joml.Matrix4f;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shadow.ShadowMatrices;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class MatrixUniforms {
    private MatrixUniforms() {
    }

    public static void addMatrixUniforms(UniformHolder uniforms, PackDirectives directives) {
        addMatrix(uniforms, "ModelView", CapturedRenderingState.INSTANCE::getGbufferModelView);
        // TODO: In some cases, gbufferProjectionInverse takes on a value much different than OptiFine...
        // We need to audit Mojang's linear algebra.
        addMatrix(uniforms, "Projection", CapturedRenderingState.INSTANCE::getGbufferProjection);
        // CRITICAL: BSL must SAMPLE the shadow map with the EXACT matrices it was
        // RENDERED with. ShadowRenderer renders at renderWorldPass HEAD and stores
        // them in ShadowRenderer.MODELVIEW/PROJECTION. Recomputing here (PER_FRAME,
        // evaluated during composite) yields a DIFFERENT matrix: createShadowModelView
        // snaps to a (cameraPos % intervalSize) lattice and the camera has moved
        // since HEAD. The render-vs-sample lattice mismatch makes most surfaces
        // fetch the wrong shadow texel -> banded, player-centred, non-square
        // shadows. Use the stored render matrices (fallback only if unset).
        addShadowMatrix(uniforms, "ModelView", () -> {
            Matrix4f mv = ShadowRenderer.MODELVIEW;
            return mv != null ? mv
                    : ShadowRenderer.createShadowModelView(directives.getSunPathRotation(),
                            directives.getShadowDirectives().getIntervalSize());
        });
        addShadowMatrix(uniforms, "Projection", () -> {
            Matrix4f p = ShadowRenderer.PROJECTION;
            return p != null ? p
                    : new Matrix4f().set(ShadowMatrices.createOrthoMatrix(
                            directives.getShadowDirectives().getDistance()));
        });
    }

    private static void addMatrix(UniformHolder uniforms, String name, Supplier<Matrix4f> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "gbuffer" + name, supplier)
                .uniformJomlMatrix(PER_FRAME, "gbuffer" + name + "Inverse", new Inverted(supplier))
                .uniformMatrix(PER_FRAME, "gbufferPrevious" + name, new Previous(supplier));
    }

    private static void addShadowMatrix(UniformHolder uniforms, String name, Supplier<Matrix4f> supplier) {
        uniforms
                .uniformMatrix(PER_FRAME, "shadow" + name, supplier)
                .uniformJomlMatrix(PER_FRAME, "shadow" + name + "Inverse", new Inverted(supplier));
    }

    private static class Inverted implements Supplier<net.coderbot.iris.vendored.joml.Matrix4f> {
        private final Supplier<Matrix4f> parent;

        Inverted(Supplier<Matrix4f> parent) {
            this.parent = parent;
        }

        @Override
        public net.coderbot.iris.vendored.joml.Matrix4f get() {
            // PERF: Don't copy + allocate this matrix every time?
            Matrix4f copy = new Matrix4f(parent.get());

            FloatBuffer buffer = FloatBuffer.allocate(16);
            copy.get(buffer);
            buffer.rewind();

            net.coderbot.iris.vendored.joml.Matrix4f matrix4f = new net.coderbot.iris.vendored.joml.Matrix4f(buffer);
            matrix4f.invert();

            return matrix4f;
        }
    }

    private static class Previous implements Supplier<Matrix4f> {
        private final Supplier<Matrix4f> parent;
        private Matrix4f previous;

        Previous(Supplier<Matrix4f> parent) {
            this.parent = parent;
            this.previous = new Matrix4f();
        }

        @Override
        public Matrix4f get() {
            // PERF: Don't copy + allocate these matrices every time?
            Matrix4f copy = new Matrix4f(parent.get());
            Matrix4f previous = new Matrix4f(this.previous);

            this.previous = copy;

            return previous;
        }
    }
}
