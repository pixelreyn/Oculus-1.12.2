package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.BooleanOption;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

public class BooleanElementWidget extends AbstractElementWidget<OptionMenuBooleanOptionElement> {
    private BooleanOption option;
    private boolean appliedValue;
    private boolean value;
    private boolean defaultValue;
    private String label;
    private ShaderPackScreen screen;
    private NavigationController navigation;
    private boolean labelTrimmed;

    public BooleanElementWidget(OptionMenuBooleanOptionElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.screen = screen;
        this.navigation = navigation;
        this.option = element.option;

        this.appliedValue = element.getAppliedOptionValues().getBooleanValue(option.getName()).orElse(option.getDefaultValue());

        // Check for pending value
        String pending = Iris.getShaderPackOptionQueue().get(option.getName());
        if (pending != null) {
            this.value = Boolean.parseBoolean(pending);
        } else {
            this.value = appliedValue;
        }

        this.defaultValue = option.getDefaultValue();
        this.label = GuiUtil.translateOrDefault(option.getName(), "option." + option.getName());
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int btnY = y + (height - 20) / 2;
        GuiUtil.drawButton(x, btnY, width, 20, hovered, false);

        String displayLabel = label + ": ";
        int labelColor = isValueModified() ? 0xffc94a : 0xFFFFFF;

        String valueText;
        int valueColor;
        if (value) {
            valueText = "ON";
            valueColor = value == defaultValue ? 0xFFFFFF : 0x55FF55;
        } else {
            valueText = "OFF";
            valueColor = value == defaultValue ? 0xFFFFFF : 0xFF5555;
        }

        int valueWidth = font.getStringWidth(valueText) + 8;
        int maxLabelWidth = width - valueWidth - 8;
        labelTrimmed = font.getStringWidth(displayLabel) > maxLabelWidth;
        if (labelTrimmed) {
            displayLabel = GuiUtil.shortenText(font, displayLabel, maxLabelWidth);
        }

        font.drawStringWithShadow(displayLabel, x + 4, btnY + 6, labelColor);

        int valueX = x + width - valueWidth - 2;
        GuiUtil.drawButton(valueX, btnY + 2, valueWidth, 16, false, true);
        font.drawStringWithShadow(valueText, valueX + (valueWidth - font.getStringWidth(valueText)) / 2,
                btnY + 6, valueColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 || button == 1) {
            if (GuiScreen.isShiftKeyDown()) {
                value = defaultValue;
            } else {
                value = !value;
            }
            Iris.getShaderPackOptionQueue().put(option.getName(), Boolean.toString(value));
            GuiUtil.playButtonClickSound();
            return true;
        }
        return false;
    }

    public boolean isValueModified() {
        return value != appliedValue;
    }

    public boolean isLabelTrimmed() {
        return labelTrimmed;
    }

    @Override
    public String getCommentTitle() {
        return label;
    }

    @Override
    public String getCommentBody() {
        String langComment = GuiUtil.getShaderPackTranslation("option." + option.getName() + ".comment");
        if (langComment != null) return langComment;
        return option.getComment().orElse(null);
    }
}
