package net.coderbot.iris.gui.element.widget;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.coderbot.iris.shaderpack.option.StringOption;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

public class StringElementWidget extends AbstractElementWidget<OptionMenuStringOptionElement> {
    protected StringOption option;
    protected String appliedValue;
    protected int valueCount;
    protected int valueIndex;
    protected ShaderPackScreen screen;
    protected NavigationController navigation;
    protected String label;
    protected boolean labelTrimmed;

    public StringElementWidget(OptionMenuStringOptionElement element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.screen = screen;
        this.navigation = navigation;
        this.option = element.option;

        this.appliedValue = element.getAppliedOptionValues().getStringValue(option.getName()).orElse(option.getDefaultValue());

        // Check for pending value
        String pending = Iris.getShaderPackOptionQueue().get(option.getName());
        String currentValue = pending != null ? pending : appliedValue;

        ImmutableList<String> allowedValues = option.getAllowedValues();
        this.valueCount = allowedValues.size();
        this.valueIndex = allowedValues.indexOf(currentValue);
        if (this.valueIndex < 0) {
            this.valueIndex = allowedValues.indexOf(option.getDefaultValue());
        }

        this.label = GuiUtil.translateOrDefault(option.getName(), "option." + option.getName());
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int btnY = y + (height - 20) / 2;
        GuiUtil.drawButton(x, btnY, width, 20, hovered, false);

        String displayLabel = label + ": ";
        int labelColor = isValueModified() ? 0xffc94a : 0xFFFFFF;

        String valueText = getDisplayValue();
        int valueWidth = Math.max(font.getStringWidth(valueText) + 8, 40);
        int maxLabelWidth = width - valueWidth - 8;
        labelTrimmed = font.getStringWidth(displayLabel) > maxLabelWidth;
        if (labelTrimmed) {
            displayLabel = GuiUtil.shortenText(font, displayLabel, maxLabelWidth);
        }

        font.drawStringWithShadow(displayLabel, x + 4, btnY + 6, labelColor);

        int valueX = x + width - valueWidth - 2;
        GuiUtil.drawButton(valueX, btnY + 2, valueWidth, 16, false, true);
        font.drawStringWithShadow(valueText,
                valueX + (valueWidth - font.getStringWidth(valueText)) / 2,
                btnY + 6, 0x6688ff);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 || button == 1) {
            if (GuiScreen.isShiftKeyDown()) {
                applyOriginalValue();
            } else if (button == 0) {
                applyNextValue();
            } else {
                applyPreviousValue();
            }
            GuiUtil.playButtonClickSound();
            return true;
        }
        return false;
    }

    public String getValue() {
        if (valueIndex >= 0 && valueIndex < option.getAllowedValues().size()) {
            return option.getAllowedValues().get(valueIndex);
        }
        return appliedValue;
    }

    public String getDisplayValue() {
        String raw = getValue();
        return GuiUtil.translateOrDefault(raw, "value." + option.getName() + "." + raw);
    }

    protected void queue() {
        Iris.getShaderPackOptionQueue().put(option.getName(), getValue());
    }

    public void applyNextValue() {
        if (valueCount > 0) {
            valueIndex = Math.floorMod(valueIndex + 1, valueCount);
            queue();
            if (navigation != null) navigation.refresh();
        }
    }

    public void applyPreviousValue() {
        if (valueCount > 0) {
            valueIndex = Math.floorMod(valueIndex - 1, valueCount);
            queue();
            if (navigation != null) navigation.refresh();
        }
    }

    public void applyOriginalValue() {
        this.valueIndex = option.getAllowedValues().indexOf(option.getDefaultValue());
        queue();
        if (navigation != null) navigation.refresh();
    }

    public boolean isValueModified() {
        return !appliedValue.equals(getValue());
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
