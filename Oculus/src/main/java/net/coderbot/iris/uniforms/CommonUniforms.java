package net.coderbot.iris.uniforms;

import net.minecraft.client.renderer.GlStateManager;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.state.GlStateManagerHelper;
import net.coderbot.iris.shaderpack.IdMap;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.texture.TextureInfoCache;
import net.coderbot.iris.texture.TextureInfoCache.TextureInfo;
import net.coderbot.iris.texture.TextureTracker;
import net.coderbot.iris.uniforms.transforms.SmoothedFloat;
import net.coderbot.iris.uniforms.transforms.SmoothedVec2f;
import net.coderbot.iris.vendored.joml.Math;
import net.coderbot.iris.vendored.joml.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.coderbot.iris.pipeline.WorldRenderingPhase;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.*;

public final class CommonUniforms {
    private static final Minecraft client = Minecraft.getMinecraft();
    private static final Vector2i ZERO_VECTOR_2i = new Vector2i();
    private static final Vector4i ZERO_VECTOR_4i = new Vector4i(0, 0, 0, 0);
    private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

    private CommonUniforms() {
        // no construction allowed
    }

    // Needs to use a LocationalUniformHolder as we need it for the common uniforms
    public static void addCommonUniforms(DynamicUniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier) {
        CameraUniforms.addCameraUniforms(uniforms, updateNotifier);
        ViewportUniforms.addViewportUniforms(uniforms);
        WorldTimeUniforms.addWorldTimeUniforms(uniforms);
        SystemTimeUniforms.addSystemTimeUniforms(uniforms);
        new CelestialUniforms(directives.getSunPathRotation()).addCelestialUniforms(uniforms);
        IdMapUniforms.addIdMapUniforms(updateNotifier, uniforms, idMap, directives.isOldHandLight());
        IrisExclusiveUniforms.addIrisExclusiveUniforms(uniforms);
        MatrixUniforms.addMatrixUniforms(uniforms, directives);
        HardcodedCustomUniforms.addHardcodedCustomUniforms(uniforms, updateNotifier);
        FogUniforms.addFogUniforms(uniforms);

        // TODO: OptiFine doesn't think that atlasSize is a "dynamic" uniform,
        //       but we do. How will custom uniforms depending on atlasSize work?
        uniforms.uniform2i("atlasSize", () -> {
            int glId = GlStateManagerHelper.getTextureName(0);

            AbstractTexture texture = TextureTracker.INSTANCE.getTexture(glId);
            if (texture instanceof TextureMap) {
                TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
                return new Vector2i(info.getWidth(), info.getHeight());
            }

            return ZERO_VECTOR_2i;
        }, StateUpdateNotifiers.bindTextureNotifier);

        uniforms.uniform2i("gtextureSize", () -> {
            int glId = GlStateManagerHelper.getTextureName(0);

            TextureInfo info = TextureInfoCache.INSTANCE.getInfo(glId);
            return new Vector2i(info.getWidth(), info.getHeight());

        }, StateUpdateNotifiers.bindTextureNotifier);

        uniforms.uniform4i("blendFunc", () -> {
            GlStateManager.BlendState blend = GlStateManagerHelper.getBlendState();
            if (GlStateManagerHelper.isBooleanStateEnabled(blend.blend)) {
                return new Vector4i(blend.srcFactor, blend.dstFactor, blend.srcFactorAlpha, blend.dstFactorAlpha);
            } else {
                return ZERO_VECTOR_4i;
            }
        }, StateUpdateNotifiers.blendFuncNotifier);

        uniforms.uniform1i("renderStage", () -> WorldRenderingPhase.NONE.ordinal(), StateUpdateNotifiers.phaseChangeNotifier);

        CommonUniforms.generalCommonUniforms(uniforms, updateNotifier, directives);
    }

    public static void generalCommonUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier, PackDirectives directives) {
        ExternallyManagedUniforms.addExternallyManagedUniforms116(uniforms);

        SmoothedVec2f eyeBrightnessSmooth = new SmoothedVec2f(directives.getEyeBrightnessHalfLife(), directives.getEyeBrightnessHalfLife(), CommonUniforms::getEyeBrightness, updateNotifier);

        uniforms
                .uniform1b(PER_FRAME, "hideGUI", () -> client.gameSettings.hideGUI)
                .uniform1i(PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
                .uniform1f(PER_FRAME, "blindness", CommonUniforms::getBlindness)
                .uniform1f(PER_FRAME, "nightVision", CommonUniforms::getNightVision)
                .uniform1b(PER_FRAME, "is_sneaking", CommonUniforms::isSneaking)
                .uniform1b(PER_FRAME, "is_sprinting", CommonUniforms::isSprinting)
                .uniform1b(PER_FRAME, "is_hurt", CommonUniforms::isHurt)
                .uniform1b(PER_FRAME, "is_invisible", CommonUniforms::isInvisible)
                .uniform1b(PER_FRAME, "is_burning", CommonUniforms::isBurning)
                .uniform1b(PER_FRAME, "is_on_ground", CommonUniforms::isOnGround)
                // TODO: Do we need to clamp this to avoid fullbright breaking shaders? Or should shaders be able to detect
                //       that the player is trying to turn on fullbright?
                .uniform1f(PER_FRAME, "screenBrightness", () -> (float) client.gameSettings.gammaSetting)
                // just a dummy value for shaders where entityColor isn't supplied through a vertex attribute (and thus is
                // not available) - suppresses warnings. See AttributeShaderTransformer for the actual entityColor code.
                .uniform4f(ONCE, "entityColor", () -> new Vector4f(0, 0, 0, 0))
                .uniform1f(ONCE, "pi", () -> Math.PI)
                .uniform1f(PER_TICK, "playerMood", CommonUniforms::getPlayerMood)
                .uniform2i(PER_FRAME, "eyeBrightness", CommonUniforms::getEyeBrightness)
                .uniform2i(PER_FRAME, "eyeBrightnessSmooth", () -> {
                    Vector2f smoothed = eyeBrightnessSmooth.get();
                    return new Vector2i((int) smoothed.x(), (int) smoothed.y());
                })
                .uniform1f(PER_TICK, "rainStrength", CommonUniforms::getRainStrength)
                .uniform1f(PER_TICK, "wetness", new SmoothedFloat(directives.getWetnessHalfLife(), directives.getDrynessHalfLife(), CommonUniforms::getRainStrength, updateNotifier))
                .uniform3d(PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
                .uniform3d(PER_FRAME, "fogColor", CapturedRenderingState.INSTANCE::getFogColor);
    }

    private static boolean isOnGround() {
        return client.player != null && client.player.onGround;
    }

    private static boolean isHurt() {
        if (client.player != null) {
            return client.player.hurtTime > 0; // Do not use isHurt, that's not what we want!
        } else {
            return false;
        }
    }

    private static boolean isInvisible() {
        if (client.player != null) {
            return client.player.isInvisible();
        } else {
            return false;
        }
    }

    private static boolean isBurning() {
        if (client.player != null) {
            return client.player.isBurning();
        } else {
            return false;
        }
    }

    private static boolean isSneaking() {
        if (client.player != null) {
            return client.player.isSneaking();
        } else {
            return false;
        }
    }

    private static boolean isSprinting() {
        if (client.player != null) {
            return client.player.isSprinting();
        } else {
            return false;
        }
    }

    private static Vector3d getSkyColor() {
        if (client.world == null || client.getRenderViewEntity() == null) {
            return ZERO_VECTOR_3d;
        }

        Vec3d skyColor = client.world.getSkyColor(client.getRenderViewEntity(),
                CapturedRenderingState.INSTANCE.getTickDelta());
        return new Vector3d(skyColor.x, skyColor.y, skyColor.z);
    }

    static float getBlindness() {
        Entity cameraEntity = client.getRenderViewEntity();

        if (cameraEntity instanceof EntityLivingBase) {
            PotionEffect blindness = ((EntityLivingBase) cameraEntity).getActivePotionEffect(MobEffects.BLINDNESS);

            if (blindness != null) {
                // Guessing that this is what OF uses, based on how vanilla calculates the fog value in BackgroundRenderer
                // TODO: Add this to ShaderDoc
                return Math.clamp(0.0F, 1.0F, blindness.getDuration() / 20.0F);
            }
        }

        return 0.0F;
    }

    private static float getPlayerMood() {
        // 1.12.2 doesn't have player mood system, return 0
        return 0.0F;
    }

    static float getRainStrength() {
        if (client.world == null) {
            return 0f;
        }

        // Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
        return Math.clamp(0.0F, 1.0F,
                client.world.getRainStrength(CapturedRenderingState.INSTANCE.getTickDelta()));
    }

    private static Vector2i getEyeBrightness() {
        if (client.getRenderViewEntity() == null || client.world == null) {
            return ZERO_VECTOR_2i;
        }

        Entity entity = client.getRenderViewEntity();
        BlockPos eyeBlockPos = new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);

        int blockLight = client.world.getLightFor(EnumSkyBlock.BLOCK, eyeBlockPos);
        int skyLight = client.world.getLightFor(EnumSkyBlock.SKY, eyeBlockPos);

        return new Vector2i(blockLight * 16, skyLight * 16);
    }

    private static float getNightVision() {
        Entity cameraEntity = client.getRenderViewEntity();

        if (cameraEntity instanceof EntityLivingBase) {
            EntityLivingBase livingEntity = (EntityLivingBase) cameraEntity;
            PotionEffect nightVision = livingEntity.getActivePotionEffect(MobEffects.NIGHT_VISION);

            if (nightVision != null) {
                // Calculate night vision strength similar to vanilla
                int duration = nightVision.getDuration();
                float strength;
                if (duration > 200) {
                    strength = 1.0F;
                } else {
                    strength = 0.7F + (float) java.lang.Math.sin((duration - CapturedRenderingState.INSTANCE.getTickDelta()) * (float) java.lang.Math.PI * 0.2F) * 0.3F;
                }
                return Math.clamp(0.0F, 1.0F, strength);
            }
        }

        // 1.12.2 doesn't have conduit power, skip that check
        return 0.0F;
    }

    static int isEyeInWater() {
        Entity entity = client.getRenderViewEntity();
        if (entity == null) {
            return 0;
        }

        // Check what material the eyes are in
        BlockPos eyePos = new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);

        if (entity.isInsideOfMaterial(Material.WATER)) {
            return 1;
        } else if (entity.isInsideOfMaterial(Material.LAVA)) {
            return 2;
        } else {
            return 0;
        }
    }
}
