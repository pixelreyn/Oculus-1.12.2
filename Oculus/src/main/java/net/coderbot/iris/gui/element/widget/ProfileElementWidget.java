package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.Profile;
import net.coderbot.iris.shaderpack.option.ProfileSet;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuProfileElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class ProfileElementWidget extends AbstractElementWidget<OptionMenuProfileElement> {
    private Profile next;
    private Profile previous;
    private String profileLabel;
    private NavigationController navigation;

    public ProfileElementWidget(OptionMenuProfileElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.navigation = navigation;

        ProfileSet profiles = element.profiles;
        ProfileSet.ProfileResult result = profiles.scan(element.options, element.getPendingOptionValues());

        this.next = result.next;
        this.previous = result.previous;

        if (result.current.isPresent()) {
            this.profileLabel = result.current.get().name;
        } else {
            this.profileLabel = "\u00a7eCustom";
        }
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int btnY = y + (height - 20) / 2;
        GuiUtil.drawButton(x, btnY, width, 20, hovered, false);

        String label = "Profile: ";
        font.drawStringWithShadow(label, x + 4, btnY + 6, 0xFFFFFF);

        int valueWidth = Math.max(font.getStringWidth(profileLabel) + 8, 60);
        int valueX = x + width - valueWidth - 2;
        GuiUtil.drawButton(valueX, btnY + 2, valueWidth, 16, false, true);
        font.drawStringWithShadow(profileLabel,
                valueX + (valueWidth - font.getStringWidth(profileLabel)) / 2,
                btnY + 6, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && next != null) {
            // Apply next profile's option values
            for (java.util.Map.Entry<String, String> entry : next.optionValues.entrySet()) {
                Iris.getShaderPackOptionQueue().put(entry.getKey(), entry.getValue());
            }
            if (navigation != null) navigation.refresh();
            GuiUtil.playButtonClickSound();
            return true;
        } else if (button == 1 && previous != null) {
            for (java.util.Map.Entry<String, String> entry : previous.optionValues.entrySet()) {
                Iris.getShaderPackOptionQueue().put(entry.getKey(), entry.getValue());
            }
            if (navigation != null) navigation.refresh();
            GuiUtil.playButtonClickSound();
            return true;
        }
        return false;
    }

    @Override
    public String getCommentTitle() {
        return "Profile";
    }

    @Override
    public String getCommentBody() {
        return "Select a preset configuration profile. Left-click for next, right-click for previous.";
    }
}
