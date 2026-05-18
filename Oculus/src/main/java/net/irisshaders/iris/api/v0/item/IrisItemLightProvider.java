package net.irisshaders.iris.api.v0.item;

import net.coderbot.iris.vendored.joml.Vector3f;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public interface IrisItemLightProvider {

    Vector3f DEFAULT_LIGHT_COLOR = new Vector3f(1, 1, 1);

    default int getLightEmission(EntityPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof ItemBlock) {
            ItemBlock item = (ItemBlock) stack.getItem();
            return item.getBlock().getDefaultState().getLightValue();
        }

        return 0;
    }

    default Vector3f getLightColor(EntityPlayer player, ItemStack stack) {
        return DEFAULT_LIGHT_COLOR;
    }
}
