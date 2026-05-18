package net.coderbot.iris.compat;

import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.vendored.joml.Vector3d;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/**
 * Static helper for camera operations.
 * In 1.16+, the camera position is obtained via GameRenderer.getMainCamera().getPosition().
 * In 1.12.2, we compute it from the render view entity.
 */
public class CameraHelper {
    private CameraHelper() {}

    /**
     * Get the camera position as a JOML Vector3d.
     * Uses the captured tick delta for interpolation.
     */
    public static Vector3d getCameraPosition() {
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();

        if (entity == null) {
            return new Vector3d(0, 0, 0);
        }

        float partialTicks = CapturedRenderingState.INSTANCE.getTickDelta();

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        return new Vector3d(x, y, z);
    }

    /**
     * Get the camera position for a specific partial tick value.
     */
    public static Vector3d getCameraPosition(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity entity = mc.getRenderViewEntity();

        if (entity == null) {
            return new Vector3d(0, 0, 0);
        }

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        return new Vector3d(x, y, z);
    }
}
