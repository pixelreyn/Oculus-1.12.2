package net.coderbot.iris.gui;

import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.LanguageMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Map;

public final class GuiUtil {
    public static final ResourceLocation IRIS_WIDGETS_TEX = new ResourceLocation("oculus", "textures/gui/widgets.png");

    private GuiUtil() {}

    public static void bindIrisWidgetsTexture() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(IRIS_WIDGETS_TEX);
    }

    /**
     * Draws a button-style background. Uses 3-piece (left cap, middle stretch, right cap)
     * to support any width. Vanilla button texture is 200x20 in widgets.png.
     */
    public static void drawButton(int x, int y, int width, int height, boolean hovered, boolean disabled) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/widgets.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        int vOffset = disabled ? 46 : (hovered ? 86 : 66);
        Gui gui = new Gui();

        if (width <= 200) {
            // Fits in one texture — standard 2-half draw
            int halfW = width / 2;
            gui.drawTexturedModalRect(x, y, 0, vOffset, halfW, height);
            gui.drawTexturedModalRect(x + halfW, y, 200 - (width - halfW), vOffset, width - halfW, height);
        } else {
            // Wider than texture — draw left cap, stretched middle, right cap
            int cap = 4; // pixels from each edge
            int middleU = cap; // texture U for middle section
            int middleTexW = 200 - cap * 2; // texture width of middle section

            // Left cap
            gui.drawTexturedModalRect(x, y, 0, vOffset, cap, height);
            // Right cap
            gui.drawTexturedModalRect(x + width - cap, y, 200 - cap, vOffset, cap, height);

            // Middle — tile the middle section
            int middleWidth = width - cap * 2;
            int drawn = 0;
            while (drawn < middleWidth) {
                int toDraw = Math.min(middleTexW, middleWidth - drawn);
                gui.drawTexturedModalRect(x + cap + drawn, y, middleU, vOffset, toDraw, height);
                drawn += toDraw;
            }
        }

        GlStateManager.disableBlend();
    }

    /**
     * Draws a translucent panel with border.
     */
    public static void drawPanel(int x, int y, int width, int height) {
        // Border
        Gui.drawRect(x, y, x + width, y + 1, 0xDEDEDEDE);
        Gui.drawRect(x, y + height - 1, x + width, y + height, 0xDEDEDEDE);
        Gui.drawRect(x, y + 1, x + 1, y + height - 1, 0xDEDEDEDE);
        Gui.drawRect(x + width - 1, y + 1, x + width, y + height - 1, 0xDEDEDEDE);
        // Inner fill
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xDE000000);
    }

    /**
     * Draws a text panel (panel sized to fit text).
     */
    public static void drawTextPanel(FontRenderer font, String text, int x, int y) {
        int textWidth = font.getStringWidth(text);
        drawPanel(x - 4, y - 4, textWidth + 8, 16);
        font.drawStringWithShadow(text, x, y, 0xFFFFFF);
    }

    /**
     * Shortens text to fit within a given width, appending "..." if truncated.
     */
    public static String shortenText(FontRenderer font, String text, int maxWidth) {
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.getStringWidth(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.getStringWidth(sb.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb.toString() + ellipsis;
    }

    public static void playButtonClickSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    /**
     * Looks up a translation key in the current shader pack's lang files.
     * Returns null if no shader pack is loaded or the key doesn't exist.
     */
    public static String getShaderPackTranslation(String key) {
        return Iris.getCurrentPack().map(pack -> {
            LanguageMap langMap = pack.getLanguageMap();
            // Try current MC language, fall back to en_us
            String mcLang = Minecraft.getMinecraft().getLanguageManager()
                    .getCurrentLanguage().getLanguageCode().toLowerCase(java.util.Locale.ROOT);
            Map<String, String> translations = langMap.getTranslations(mcLang);
            if (translations != null && translations.containsKey(key)) {
                return translations.get(key);
            }
            // Fall back to en_us
            translations = langMap.getTranslations("en_us");
            if (translations != null && translations.containsKey(key)) {
                return translations.get(key);
            }
            return null;
        }).orElse(null);
    }

    /**
     * Returns the shader pack translation for a key, or the fallback if not found.
     */
    public static String translateOrDefault(String fallback, String key) {
        String translated = getShaderPackTranslation(key);
        return translated != null ? translated : fallback;
    }

    /**
     * Draws a filled rectangle with gradient. Utility wrapper.
     */
    public static void drawRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, bottom, color);
    }

    /**
     * Icon definitions for the Iris widgets texture.
     */
    public enum Icon {
        SEARCH(0, 0, 7, 8),
        CLOSE(7, 0, 5, 6),
        REFRESH(12, 0, 10, 10),
        EXPORT(22, 0, 7, 8),
        EXPORT_COLORED(29, 0, 7, 8),
        IMPORT(22, 8, 7, 8),
        IMPORT_COLORED(29, 8, 7, 8);

        private final int u, v, width, height;

        Icon(int u, int v, int width, int height) {
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
        }

        public void draw(int x, int y) {
            bindIrisWidgetsTexture();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 64, 64);
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
}
