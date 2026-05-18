package net.coderbot.iris.gui.element.widget;

import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.MathHelper;

public class SliderElementWidget extends StringElementWidget {
    private static final int PREVIEW_SLIDER_WIDTH = 4;
    private static final int ACTIVE_SLIDER_WIDTH = 6;

    private boolean mouseDown = false;

    public SliderElementWidget(OptionMenuStringOptionElement element) {
        super(element);
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int btnY = y + (height - 20) / 2;

        if (!hovered) {
            // Preview mode: show label + value box with a small slider indicator
            renderWithPreviewSlider(font, x, btnY, width, mouseX, mouseY);
        } else {
            // Active mode: full-width slider
            renderActiveSlider(font, x, btnY, width, mouseX, mouseY);
        }

        if (this.mouseDown && !hovered) {
            onReleased();
        }

        if (this.mouseDown) {
            whileDragging(x, width, mouseX);
        }
    }

    private void renderWithPreviewSlider(FontRenderer font, int x, int y, int width, int mouseX, int mouseY) {
        String displayLabel = label + ": ";
        int labelColor = isValueModified() ? 0xffc94a : 0xFFFFFF;

        String valueText = getValue();
        int valueWidth = Math.max(font.getStringWidth(valueText) + 8, 40);
        int maxLabelWidth = width - valueWidth - 8;
        boolean labelTrimmed = font.getStringWidth(displayLabel) > maxLabelWidth;
        if (labelTrimmed) {
            displayLabel = GuiUtil.shortenText(font, displayLabel, maxLabelWidth);
        }

        GuiUtil.drawButton(x, y, width, 20, false, false);

        // Value box
        int valueX = x + width - valueWidth - 2;
        GuiUtil.drawButton(valueX, y + 2, valueWidth, 16, false, true);

        // Preview slider inside value box
        if (valueCount > 1) {
            int sliderSpace = (valueWidth - 4) - PREVIEW_SLIDER_WIDTH;
            float progress = (float) valueIndex / (valueCount - 1);
            int sliderPos = (valueX + 2) + (int) (progress * sliderSpace);
            GuiUtil.drawButton(sliderPos, y + 4, PREVIEW_SLIDER_WIDTH, 12, false, false);
        }

        font.drawStringWithShadow(displayLabel, x + 4, y + 6, labelColor);
        font.drawStringWithShadow(valueText,
                valueX + (valueWidth - font.getStringWidth(valueText)) / 2,
                y + 6, 0x6688ff);

        // Store trimmed state for tooltip
        this.labelTrimmed = labelTrimmed;
    }

    private void renderActiveSlider(FontRenderer font, int x, int y, int width, int mouseX, int mouseY) {
        // Outer button background
        GuiUtil.drawButton(x, y, width, 20, false, false);
        // Inner slider track
        GuiUtil.drawButton(x + 2, y + 2, width - 4, 16, false, true);

        // Slider thumb
        if (valueCount > 1) {
            int sliderSpace = (width - 8) - ACTIVE_SLIDER_WIDTH;
            float progress = (float) valueIndex / (valueCount - 1);
            int sliderPos = (x + 4) + (int) (progress * sliderSpace);
            GuiUtil.drawButton(sliderPos, y + 4, ACTIVE_SLIDER_WIDTH, 12, this.mouseDown, false);
        }

        // Value label centered
        String valueText = getValue();
        font.drawStringWithShadow(valueText,
                x + (width - font.getStringWidth(valueText)) / 2,
                y + 6, 0xFFFFFF);

        this.labelTrimmed = false;
    }

    private void whileDragging(int x, int width, int mouseX) {
        float mousePositionAcrossWidget = MathHelper.clamp((float) (mouseX - (x + 4)) / (width - 8), 0, 1);
        int newValueIndex = Math.min(valueCount - 1, (int) (mousePositionAcrossWidget * valueCount));

        if (valueIndex != newValueIndex) {
            this.valueIndex = newValueIndex;
        }
    }

    private void onReleased() {
        mouseDown = false;
        queue();
        if (navigation != null) navigation.refresh();
        GuiUtil.playButtonClickSound();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (GuiScreen.isShiftKeyDown()) {
                applyOriginalValue();
                GuiUtil.playButtonClickSound();
                return true;
            }

            mouseDown = true;
            GuiUtil.playButtonClickSound();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && mouseDown) {
            onReleased();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    public void applyOriginalValue() {
        this.valueIndex = option.getAllowedValues().indexOf(option.getDefaultValue());
        queue();
        if (navigation != null) navigation.refresh();
    }
}
