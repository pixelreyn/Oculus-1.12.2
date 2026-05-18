package net.coderbot.iris.shadow;

import net.coderbot.iris.vendored.joml.Matrix4f;
import net.coderbot.iris.vendored.joml.Vector3f;

import java.nio.FloatBuffer;

/**
 * Shadow matrix calculation utilities.
 * Adapted for 1.12.2 using JOML matrices instead of PoseStack.
 */
public class ShadowMatrices {
    private static final float NEAR = 0.05f;
    private static final float FAR = 256.0f;

    // NB: These matrices are in column-major order, not row-major order like what you'd expect!

    public static float[] createOrthoMatrix(float halfPlaneLength) {
        return new float[]{
                // column 1
                1.0f / halfPlaneLength, 0f, 0f, 0f,
                // column 2
                0f, 1.0f / halfPlaneLength, 0f, 0f,
                // column 3
                0f, 0f, 2.0f / (NEAR - FAR), 0f,
                // column 4
                0f, 0f, -(FAR + NEAR) / (FAR - NEAR), 1f
        };
    }

    public static float[] createPerspectiveMatrix(float fov) {
        // This converts from degrees to radians.
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov) * 0.5f));
        return new float[]{
                // column 1
                yScale, 0f, 0f, 0f,
                // column 2
                0f, yScale, 0f, 0f,
                // column 3
                0f, 0f, (FAR + NEAR) / (NEAR - FAR), -1.0F,
                // column 4
                0f, 0f, 2.0F * FAR * NEAR / (NEAR - FAR), 1f
        };
    }

    public static void createBaselineModelViewMatrix(Matrix4f target, float shadowAngle, float sunPathRotation) {
        float skyAngle;

        if (shadowAngle < 0.25f) {
            skyAngle = shadowAngle + 0.75f;
        } else {
            skyAngle = shadowAngle - 0.25f;
        }

        target.identity();
        target.translate(0.0f, 0.0f, -100.0f);
        target.rotateX((float) Math.toRadians(90.0f));
        target.rotateZ((float) Math.toRadians(skyAngle * -360.0f));
        target.rotateX((float) Math.toRadians(sunPathRotation));
    }

    public static void snapModelViewToGrid(Matrix4f target, float shadowIntervalSize, double cameraX, double cameraY, double cameraZ) {
        if (Math.abs(shadowIntervalSize) == 0.0F) {
            // Avoid a division by zero - semantically, this just means that the snapping does not take place,
            // if the shadow interval (size of each grid "cell") is zero.
            return;
        }

        // Calculate where we are within each grid "cell"
        // These values will be in the range of (-shadowIntervalSize, shadowIntervalSize)
        float offsetX = (float) cameraX % shadowIntervalSize;
        float offsetY = (float) cameraY % shadowIntervalSize;
        float offsetZ = (float) cameraZ % shadowIntervalSize;

        // Halve the size of each grid cell in order to move to the center of it.
        float halfIntervalSize = shadowIntervalSize / 2.0f;

        // Shift by -halfIntervalSize
        offsetX -= halfIntervalSize;
        offsetY -= halfIntervalSize;
        offsetZ -= halfIntervalSize;

        target.translate(offsetX, offsetY, offsetZ);
    }

    public static void createModelViewMatrix(Matrix4f target, float shadowAngle, float shadowIntervalSize,
                                             float sunPathRotation, double cameraX, double cameraY, double cameraZ) {
        createBaselineModelViewMatrix(target, shadowAngle, sunPathRotation);
        snapModelViewToGrid(target, shadowIntervalSize, cameraX, cameraY, cameraZ);
    }
}
