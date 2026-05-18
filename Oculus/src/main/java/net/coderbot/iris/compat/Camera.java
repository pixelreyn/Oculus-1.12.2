package net.coderbot.iris.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Compatibility class for 1.16+'s Camera class.
 * In 1.12.2, camera info is obtained from ActiveRenderInfo and the render view entity.
 */
public class Camera {
    private final Minecraft mc;
    private final float partialTicks;

    public Camera(float partialTicks) {
        this.mc = Minecraft.getMinecraft();
        this.partialTicks = partialTicks;
    }

    /**
     * Get the camera's position.
     */
    public Vec3d getPosition() {
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            return Vec3d.ZERO;
        }
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    /**
     * Get the block position the camera is in.
     */
    public BlockPos getBlockPosition() {
        Vec3d pos = getPosition();
        return new BlockPos(pos.x, pos.y, pos.z);
    }

    /**
     * Get the camera's X position.
     */
    public double getX() {
        return getPosition().x;
    }

    /**
     * Get the camera's Y position.
     */
    public double getY() {
        return getPosition().y;
    }

    /**
     * Get the camera's Z position.
     */
    public double getZ() {
        return getPosition().z;
    }

    /**
     * Get the camera's yaw (horizontal rotation).
     */
    public float getYaw() {
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            return 0;
        }
        return entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
    }

    /**
     * Get the camera's pitch (vertical rotation).
     */
    public float getPitch() {
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            return 0;
        }
        return entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks;
    }

    /**
     * Get the render view entity.
     */
    public Entity getEntity() {
        return mc.getRenderViewEntity();
    }

    /**
     * Get the look vector.
     */
    public Vec3d getLookVector() {
        // In 1.12.2, ActiveRenderInfo doesn't have getViewVector()
        // Compute it from rotation angles
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            return new Vec3d(0, 0, 1);
        }
        return entity.getLook(partialTicks);
    }

    /**
     * Get partial ticks value.
     */
    public float getPartialTicks() {
        return partialTicks;
    }

    /**
     * Is this a first-person view?
     */
    public boolean isFirstPerson() {
        return mc.gameSettings.thirdPersonView == 0;
    }
}
