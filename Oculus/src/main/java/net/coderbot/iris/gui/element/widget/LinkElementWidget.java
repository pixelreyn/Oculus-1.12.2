package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * A clickable link that opens a sub-screen of shader pack options.
 */
public class LinkElementWidget extends AbstractElementWidget<OptionMenuLinkElement> {
    private NavigationController navigation;
    private String label;

    public LinkElementWidget(OptionMenuLinkElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.navigation = navigation;
        this.label = GuiUtil.translateOrDefault(element.targetScreenId, "screen." + element.targetScreenId);
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int btnY = y + (height - 20) / 2;
        GuiUtil.drawButton(x, btnY, width, 20, hovered, false);

        String displayText = label;
        int maxWidth = width - 16;
        if (font.getStringWidth(displayText) > maxWidth) {
            displayText = GuiUtil.shortenText(font, displayText, maxWidth);
        }

        font.drawStringWithShadow(displayText,
                x + (width - font.getStringWidth(displayText)) / 2 - 4,
                btnY + 6, 0xFFFFFF);

        font.drawStringWithShadow(">", x + width - 10, btnY + 6, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && navigation != null) {
            navigation.open(element.targetScreenId);
            GuiUtil.playButtonClickSound();
            return true;
        }
        return false;
    }

    @Override
    public String getCommentTitle() {
        return label;
    }

    @Override
    public String getCommentBody() {
        return GuiUtil.getShaderPackTranslation("screen." + element.targetScreenId + ".comment");
    }
}
