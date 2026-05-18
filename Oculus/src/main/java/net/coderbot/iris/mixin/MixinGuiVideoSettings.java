package net.coderbot.iris.mixin;

import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * Adds a "Shader Packs..." button to the video settings screen.
 */
@Mixin(GuiVideoSettings.class)
public abstract class MixinGuiVideoSettings extends GuiScreen {

    private static final int SHADER_BUTTON_ID = 7391;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void iris$addShaderButton(CallbackInfo ci) {
        // Add "Shader Packs..." button at the top right, above the options list
        int buttonWidth = 150;
        int x = this.width / 2 + 5;
        int y = this.height - 27;

        // Move the "Done" button left to make room
        for (GuiButton button : this.buttonList) {
            if (button.id == 200) { // Done button
                button.x = this.width / 2 - 155;
                button.width = 150;
            }
        }

        this.buttonList.add(new GuiButton(SHADER_BUTTON_ID, x, y, buttonWidth, 20, "Shader Packs..."));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void iris$onActionPerformed(GuiButton button, CallbackInfo ci) throws IOException {
        if (button.id == SHADER_BUTTON_ID) {
            this.mc.displayGuiScreen(new ShaderPackScreen(this));
            ci.cancel();
        }
    }
}
