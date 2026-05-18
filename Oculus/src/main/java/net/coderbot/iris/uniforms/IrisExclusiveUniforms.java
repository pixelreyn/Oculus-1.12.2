package net.coderbot.iris.uniforms;

import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.gui.option.IrisVideoSettings;
import net.coderbot.iris.vendored.joml.Math;
import net.coderbot.iris.vendored.joml.Vector3d;
import net.coderbot.iris.vendored.joml.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;

import java.util.Objects;

/**
 * Iris-exclusive uniforms adapted for 1.12.2.
 */
public class IrisExclusiveUniforms {
    public static void addIrisExclusiveUniforms(UniformHolder uniforms) {
        WorldInfoUniforms.addWorldInfoUniforms(uniforms);

        uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentColorSpace", () -> IrisVideoSettings.colorSpace.ordinal());

        //All Iris-exclusive uniforms (uniforms which do not exist in either OptiFine or ShadersMod) should be registered here.
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "thunderStrength", IrisExclusiveUniforms::getThunderStrength);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", IrisExclusiveUniforms::getCurrentHealth);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", IrisExclusiveUniforms::getMaxHealth);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", IrisExclusiveUniforms::getCurrentHunger);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", IrisExclusiveUniforms::getCurrentAir);
        uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", IrisExclusiveUniforms::getMaxAir);
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "firstPersonCamera", IrisExclusiveUniforms::isFirstPersonCamera);
        uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", IrisExclusiveUniforms::isSpectator);
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "relativeEyePosition", () -> CameraUniforms.getUnshiftedCameraPosition().sub(getEyePosition()));
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerLookVector", IrisExclusiveUniforms::getLookVector);
        uniforms.uniform3d(UniformUpdateFrequency.PER_FRAME, "playerBodyVector", IrisExclusiveUniforms::getBodyVector);
        Vector4f zero = new Vector4f(0, 0, 0, 0);
        uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", () -> {
            WorldClient world = Minecraft.getMinecraft().world;
            if (world != null) {
                for (Entity entity : world.loadedEntityList) {
                    if (entity instanceof EntityLightningBolt) {
                        Vector3d unshiftedCameraPosition = CameraUniforms.getUnshiftedCameraPosition();
                        return new Vector4f(
                            (float) (entity.posX - unshiftedCameraPosition.x),
                            (float) (entity.posY - unshiftedCameraPosition.y),
                            (float) (entity.posZ - unshiftedCameraPosition.z),
                            1
                        );
                    }
                }
            }
            return zero;
        });
    }

    private static Vector3d getLookVector() {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity == null) return new Vector3d(0, 0, 1);
        Vec3d look = entity.getLookVec();
        return new Vector3d(look.x, look.y, look.z);
    }

    private static Vector3d getBodyVector() {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity == null) return new Vector3d(0, 0, 1);
        // In 1.12.2, use rotation yaw to get forward vector
        double yaw = Math.toRadians(-entity.rotationYaw);
        return new Vector3d(Math.sin(yaw), 0, Math.cos(yaw));
    }

    private static float getThunderStrength() {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) return 0;
        // Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
        return Math.clamp(0.0F, 1.0F,
                world.getThunderStrength(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    private static float getCurrentHealth() {
        if (Minecraft.getMinecraft().player == null || !isSurvival()) {
            return -1;
        }
        return Minecraft.getMinecraft().player.getHealth() / Minecraft.getMinecraft().player.getMaxHealth();
    }

    private static float getCurrentHunger() {
        if (Minecraft.getMinecraft().player == null || !isSurvival()) {
            return -1;
        }
        return Minecraft.getMinecraft().player.getFoodStats().getFoodLevel() / 20f;
    }

    private static float getCurrentAir() {
        if (Minecraft.getMinecraft().player == null || !isSurvival()) {
            return -1;
        }
        return (float) Minecraft.getMinecraft().player.getAir() / 300f; // 300 is max air in 1.12.2
    }

    private static float getMaxAir() {
        if (Minecraft.getMinecraft().player == null || !isSurvival()) {
            return -1;
        }
        return 300f; // Max air in 1.12.2
    }

    private static float getMaxHealth() {
        if (Minecraft.getMinecraft().player == null || !isSurvival()) {
            return -1;
        }
        return Minecraft.getMinecraft().player.getMaxHealth();
    }

    private static boolean isSurvival() {
        if (Minecraft.getMinecraft().playerController == null) return false;
        GameType gameType = Minecraft.getMinecraft().playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private static boolean isFirstPersonCamera() {
        // In 1.12.2, thirdPersonView: 0 = first person, 1 = third person back, 2 = third person front
        return Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
    }

    private static boolean isSpectator() {
        if (Minecraft.getMinecraft().playerController == null) return false;
        return Minecraft.getMinecraft().playerController.getCurrentGameType() == GameType.SPECTATOR;
    }

    private static Vector3d getEyePosition() {
        Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
        if (entity == null) return new Vector3d(0, 0, 0);
        float partialTicks = CapturedRenderingState.INSTANCE.getTickDelta();
        double x = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
        double y = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks + entity.getEyeHeight();
        double z = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;
        return new Vector3d(x, y, z);
    }

    public static class WorldInfoUniforms {
        public static void addWorldInfoUniforms(UniformHolder uniforms) {
            // TODO: Use level.dimensionType() coordinates for 1.18!
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "bedrockLevel", () -> 0);
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "cloudHeight", () -> 128.0f); // Default cloud height in 1.12.2
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "heightLimit", () -> 256);
            uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "logicalHeightLimit", () -> 256);
            uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasCeiling", () -> {
                WorldClient world = Minecraft.getMinecraft().world;
                if (world != null) {
                    return !world.provider.hasSkyLight();
                }
                return false;
            });
            uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hasSkylight", () -> {
                WorldClient world = Minecraft.getMinecraft().world;
                if (world != null) {
                    return world.provider.hasSkyLight();
                }
                return true;
            });
            uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "ambientLight", () -> {
                // In 1.12.2, ambient light varies by dimension
                WorldClient world = Minecraft.getMinecraft().world;
                if (world != null) {
                    // Nether and End have different ambient light
                    if (world.provider.getDimensionType().getId() == -1) {
                        return 0.1f; // Nether
                    } else if (world.provider.getDimensionType().getId() == 1) {
                        return 0.0f; // End
                    }
                }
                return 0f; // Overworld
            });
        }
    }
}
