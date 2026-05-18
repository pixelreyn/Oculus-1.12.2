package net.coderbot.iris.compat.sodium.mixin;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds a "Shader Packs..." button to Vintagium's video settings screen.
 */
@Mixin(value = SodiumOptionsGUI.class, remap = false)
public abstract class MixinSodiumOptionsGUI extends GuiScreen {

    @Inject(method = "rebuildGUI", at = @At("RETURN"))
    private void iris$addShaderButton(CallbackInfo ci) {
        // Position to the left of the undo/apply/done buttons
        int x = 6;
        int y = this.height - 26;

        FlatButtonWidget shaderButton = new FlatButtonWidget(
                new Dim2i(x, y, 110, 20),
                "Shader Packs...",
                () -> this.mc.displayGuiScreen(new ShaderPackScreen(this))
        );

        // Add to both children (input) and drawable (rendering) lists
        try {
            java.lang.reflect.Field childrenField = SodiumOptionsGUI.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            ((List) childrenField.get(this)).add(shaderButton);

            java.lang.reflect.Field drawableField = SodiumOptionsGUI.class.getDeclaredField("drawable");
            drawableField.setAccessible(true);
            ((List) drawableField.get(this)).add(shaderButton);
        } catch (Exception e) {
            net.coderbot.iris.Iris.logger.error("Failed to add shader button to Sodium options", e);
        }
    }
}
