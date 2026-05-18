package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.coderbot.iris.gl.uniform.DynamicUniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.shaderpack.IdMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.vendored.joml.Vector3f;
import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class IdMapUniforms {

    private IdMapUniforms() {
    }

    public static void addIdMapUniforms(FrameUpdateNotifier notifier, DynamicUniformHolder uniforms, IdMap idMap, boolean isOldHandLight) {
        HeldItemSupplier mainHandSupplier = new HeldItemSupplier(EnumHand.MAIN_HAND, idMap.getItemIdMap(), isOldHandLight);
        HeldItemSupplier offHandSupplier = new HeldItemSupplier(EnumHand.OFF_HAND, idMap.getItemIdMap(), false);
        notifier.addListener(mainHandSupplier::update);
        notifier.addListener(offHandSupplier::update);

        uniforms
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId", mainHandSupplier::getIntID)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "heldItemId2", offHandSupplier::getIntID)
                .uniform1i(PER_FRAME, "heldBlockLightValue", mainHandSupplier::getLightValue)
                .uniform1i(PER_FRAME, "heldBlockLightValue2", offHandSupplier::getLightValue);
        // TODO: Figure out API.
        //.uniformVanilla3f(PER_FRAME, "heldBlockLightColor", mainHandSupplier::getLightColor)
        //.uniformVanilla3f(PER_FRAME, "heldBlockLightColor2", offHandSupplier::getLightColor);

        uniforms.uniform1i("entityId", CapturedRenderingState.INSTANCE::getCurrentRenderedEntity,
                CapturedRenderingState.INSTANCE.getEntityIdNotifier());

        uniforms.uniform1i("blockEntityId", CapturedRenderingState.INSTANCE::getCurrentRenderedBlockEntity,
                CapturedRenderingState.INSTANCE.getBlockEntityIdNotifier());
    }

    /**
     * Provides the currently held item, and it's light value, in the given hand as a uniform. Uses the item.properties ID map to map the item
     * to an integer, and the old hand light value to map offhand to main hand.
     */
    private static class HeldItemSupplier {
        private final EnumHand hand;
        private final Object2IntFunction<NamespacedId> itemIdMap;
        private final boolean applyOldHandLight;
        private int intID;
        private int lightValue;
        private Vector3f lightColor;

        HeldItemSupplier(EnumHand hand, Object2IntFunction<NamespacedId> itemIdMap, boolean shouldApplyOldHandLight) {
            this.hand = hand;
            this.itemIdMap = itemIdMap;
            this.applyOldHandLight = shouldApplyOldHandLight && hand == EnumHand.MAIN_HAND;
        }

        private void invalidate() {
            intID = -1;
            lightValue = 0;
            lightColor = IrisItemLightProvider.DEFAULT_LIGHT_COLOR;
        }

        public void update() {
            EntityPlayerSP player = Minecraft.getMinecraft().player;

            if (player == null) {
                // Not valid when the player doesn't exist
                invalidate();
                return;
            }

            ItemStack heldStack = ((net.minecraft.entity.EntityLivingBase) player).getHeldItem(hand);

            if (heldStack == null || heldStack.isEmpty()) {
                invalidate();
                return;
            }

            Item heldItem = heldStack.getItem();

            if (heldItem == null) {
                invalidate();
                return;
            }

            ResourceLocation heldItemId = Item.REGISTRY.getNameForObject(heldItem);
            if (heldItemId != null) {
                intID = itemIdMap.getInt(new NamespacedId(heldItemId.getNamespace(), heldItemId.getPath()));
            } else {
                intID = -1;
            }

            // In 1.12.2, items don't implement IrisItemLightProvider by default
            // We need to calculate light value from block if it's a block item
            if (heldItem instanceof net.minecraft.item.ItemBlock) {
                net.minecraft.item.ItemBlock itemBlock = (net.minecraft.item.ItemBlock) heldItem;
                lightValue = itemBlock.getBlock().getDefaultState().getLightValue();
            } else {
                lightValue = 0;
            }

            lightColor = IrisItemLightProvider.DEFAULT_LIGHT_COLOR;

            if (applyOldHandLight) {
                applyOldHandLighting(player);
            }
        }

        private void applyOldHandLighting(@NotNull EntityPlayerSP player) {
            ItemStack offHandStack = player.getHeldItem(EnumHand.OFF_HAND);

            if (offHandStack == null || offHandStack.isEmpty()) {
                return;
            }

            Item offHandItem = offHandStack.getItem();

            if (offHandItem == null) {
                return;
            }

            int newEmission = 0;
            if (offHandItem instanceof net.minecraft.item.ItemBlock) {
                net.minecraft.item.ItemBlock itemBlock = (net.minecraft.item.ItemBlock) offHandItem;
                newEmission = itemBlock.getBlock().getDefaultState().getLightValue();
            }

            if (lightValue < newEmission) {
                lightValue = newEmission;
            }
        }

        public int getIntID() {
            return intID;
        }

        public int getLightValue() {
            return lightValue;
        }

        public Vector3f getLightColor() {
            return lightColor;
        }
    }
}
