package net.coderbot.iris.gui.screen;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.NavigationController;
import net.coderbot.iris.gui.element.widget.*;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.option.menu.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shader pack selection and configuration screen for 1.12.2.
 */
public class ShaderPackScreen extends GuiScreen {
    /** Runnables queued during widget rendering, executed after everything else in drawScreen. */
    public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new LinkedHashSet<>();

    private final GuiScreen parent;
    private ShaderPackList packList;
    private ShaderOptionList optionList;
    private NavigationController navigation;

    private GuiButton applyButton;
    private GuiButton doneButton;
    private GuiButton folderButton;
    private GuiButton toggleButton;
    private GuiButton switchButton;

    private String selectedPack;
    private String appliedPack;
    private boolean shadersEnabled;
    private boolean optionMenuOpen;

    private String notification;
    private int notificationTimer;

    // Comment tooltip
    private String hoveredCommentTitle;
    private String hoveredCommentBody;
    private int commentTimer;

    private static final int BTN_DONE = 0;
    private static final int BTN_APPLY = 1;
    private static final int BTN_FOLDER = 2;
    private static final int BTN_TOGGLE = 3;
    private static final int BTN_SWITCH = 4;

    public ShaderPackScreen(GuiScreen parent) {
        this.parent = parent;
        this.shadersEnabled = Iris.getIrisConfig().areShadersEnabled();
        this.appliedPack = Iris.getIrisConfig().getShaderPackName().orElse(null);
        this.selectedPack = appliedPack;
        refreshNavigation();
    }

    private void refreshNavigation() {
        Iris.getCurrentPack().ifPresent(pack -> {
            OptionMenuContainer container = pack.getMenuContainer();
            navigation = new NavigationController(container);
        });
        if (!Iris.getCurrentPack().isPresent()) {
            navigation = null;
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int listTop = 32;
        int listBottom = height - 64;

        // Create both lists
        packList = new ShaderPackList(mc, width, height, listTop, listBottom);
        optionList = new ShaderOptionList(mc, width, height, listTop, listBottom);

        if (navigation != null) {
            navigation.setRebuildCallback(() -> optionList.rebuild());
            optionList.rebuild();
        }

        // Bottom row
        int bottomY = height - 27;
        doneButton = new GuiButton(BTN_DONE, width / 2 + 104, bottomY, 100, 20, "Done");
        applyButton = new GuiButton(BTN_APPLY, width / 2, bottomY, 100, 20, "Apply");

        // Top row
        int topY = height - 51;
        folderButton = new GuiButton(BTN_FOLDER, width / 2 - 154, topY, 152, 20, "Open Shader Pack Folder");
        toggleButton = new GuiButton(BTN_TOGGLE, width / 2 + 2, topY, 75, 20,
                shadersEnabled ? "\u00a7aON" : "\u00a7cOFF");
        switchButton = new GuiButton(BTN_SWITCH, width / 2 + 79, topY, 75, 20,
                optionMenuOpen ? "Pack List" : "Settings");
        switchButton.enabled = shadersEnabled && navigation != null;

        buttonList.add(doneButton);
        buttonList.add(applyButton);
        buttonList.add(folderButton);
        buttonList.add(toggleButton);
        buttonList.add(switchButton);

        updateApplyButton();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Update button visibility based on mode
        folderButton.visible = !optionMenuOpen;
        toggleButton.visible = !optionMenuOpen;

        // Draw the active list
        if (optionMenuOpen && optionList != null) {
            optionList.drawScreen(mouseX, mouseY, partialTicks);
        } else {
            packList.drawScreen(mouseX, mouseY, partialTicks);
        }

        // Draw opaque background behind button area to cover list overflow
        drawRect(0, height - 64, width, height, 0xC0101010);
        drawGradientRect(0, height - 68, width, height - 64, 0x00000000, 0xC0101010);

        // Title
        drawCenteredString(fontRenderer, "\u00a77\u00a7oShader Packs", width / 2, 8, 0xFFFFFF);

        // Subtitle or notification
        if (notificationTimer > 0) {
            drawCenteredString(fontRenderer, notification, width / 2, 21, 0xFFFF55);
        } else if (optionMenuOpen) {
            String screenId = navigation != null ? navigation.getCurrentScreen() : null;
            String navTitle = screenId != null
                    ? GuiUtil.translateOrDefault(screenId, "screen." + screenId) : "Shader Pack Settings";
            drawCenteredString(fontRenderer, navTitle, width / 2, 21, 0xAAAAAA);
        } else {
            drawCenteredString(fontRenderer, "Select a shader pack to apply", width / 2, 21, 0xAAAAAA);
        }

        // Buttons
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Comment tooltip (bottom center)
        if (hoveredCommentTitle != null && commentTimer > 20) {
            int panelWidth = 314;
            int panelX = (width - panelWidth) / 2;
            int panelY = height - 70;
            int panelHeight = 20;

            if (hoveredCommentBody != null) {
                List<String> lines = fontRenderer.listFormattedStringToWidth(hoveredCommentBody, panelWidth - 8);
                panelHeight += lines.size() * 10;
                panelY = height - 58 - panelHeight;

                GuiUtil.drawPanel(panelX, panelY, panelWidth, panelHeight);
                fontRenderer.drawStringWithShadow(hoveredCommentTitle, panelX + 4, panelY + 4, 0xFFFF55);
                int lineY = panelY + 16;
                for (String line : lines) {
                    fontRenderer.drawStringWithShadow(line, panelX + 4, lineY, 0xCCCCCC);
                    lineY += 10;
                }
            } else {
                GuiUtil.drawPanel(panelX, panelY, panelWidth, panelHeight);
                fontRenderer.drawStringWithShadow(hoveredCommentTitle, panelX + 4, panelY + 4, 0xFFFF55);
            }
        }

        // Execute top-layer renders (trimmed label tooltips, etc.)
        for (Runnable r : TOP_LAYER_RENDER_QUEUE) {
            r.run();
        }
        TOP_LAYER_RENDER_QUEUE.clear();
    }

    public boolean isDisplayingComment() {
        return hoveredCommentTitle != null && commentTimer > 20;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (notificationTimer > 0) notificationTimer--;
        commentTimer++;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BTN_DONE:
                mc.displayGuiScreen(parent);
                break;
            case BTN_APPLY:
                applyChanges();
                break;
            case BTN_FOLDER:
                openShaderPackFolder();
                break;
            case BTN_TOGGLE:
                shadersEnabled = !shadersEnabled;
                toggleButton.displayString = shadersEnabled ? "\u00a7aON" : "\u00a7cOFF";
                switchButton.enabled = shadersEnabled && navigation != null;
                if (!shadersEnabled) {
                    optionMenuOpen = false;
                    switchButton.displayString = "Settings";
                }
                updateApplyButton();
                break;
            case BTN_SWITCH:
                if (optionMenuOpen && navigation != null && navigation.hasHistory()) {
                    navigation.back();
                } else {
                    optionMenuOpen = !optionMenuOpen;
                    switchButton.displayString = optionMenuOpen ? "Pack List" : "Settings";
                    if (optionMenuOpen && optionList != null) {
                        optionList.rebuild();
                    }
                }
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (optionMenuOpen && navigation != null && navigation.hasHistory()) {
                navigation.back();
            } else if (optionMenuOpen) {
                optionMenuOpen = false;
                switchButton.displayString = "Settings";
            } else {
                mc.displayGuiScreen(parent);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        // Let GuiScreen handle buttons first
        super.handleMouseInput();

        // Route scroll wheel to the active list (clicks are handled by
        // elementClicked which is NOT called from handleMouseInput in our
        // custom GuiSlot — we override mouseClicked below instead)
        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0) {
            if (optionMenuOpen && optionList != null) {
                optionList.handleMouseInput();
            } else {
                packList.handleMouseInput();
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // Handle buttons
        super.mouseClicked(mouseX, mouseY, mouseButton);

        // Handle list clicks manually (avoids double-fire from handleMouseInput)
        if (optionMenuOpen && optionList != null) {
            optionList.listMouseClicked(mouseX, mouseY, mouseButton);
        } else if (packList != null) {
            packList.listMouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);

        if (optionMenuOpen && optionList != null) {
            optionList.listMouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    public void setHoveredComment(String title, String body) {
        if (!java.util.Objects.equals(title, hoveredCommentTitle)) {
            commentTimer = 0;
        }
        this.hoveredCommentTitle = title;
        this.hoveredCommentBody = body;
    }

    public void clearHoveredComment() {
        this.hoveredCommentTitle = null;
        this.hoveredCommentBody = null;
        this.commentTimer = 0;
    }

    private void applyChanges() {
        try {
            Iris.getIrisConfig().setShaderPackName(selectedPack);
            Iris.getIrisConfig().setShadersEnabled(shadersEnabled);
            Iris.getIrisConfig().save();
            Iris.reload();
            appliedPack = selectedPack;

            notification = "Shader pack applied!";
            notificationTimer = 100;

            refreshNavigation();
            if (navigation != null) {
                navigation.setRebuildCallback(() -> optionList.rebuild());
            }
            switchButton.enabled = shadersEnabled && navigation != null;

            packList.refreshEntries();
            if (optionList != null) optionList.rebuild();
            updateApplyButton();
        } catch (Exception e) {
            Iris.logger.error("Failed to apply shader pack", e);
            String msg = e.getMessage();
            if (msg != null && msg.contains("version") && msg.contains("not supported")) {
                notification = "\u00a7cShader pack requires a newer OpenGL version than available";
            } else {
                notification = "\u00a7cFailed to apply: " + (msg != null ? msg : e.getClass().getSimpleName());
            }
            notificationTimer = 200;
        }
    }

    private void openShaderPackFolder() {
        try {
            Path dir = Iris.getShaderpacksDirectory();
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", dir.toString()});
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer", dir.toString()});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", dir.toString()});
            }
        } catch (IOException e) {
            Iris.logger.error("Failed to open shader pack folder", e);
        }
    }

    private void updateApplyButton() {
        boolean changed = !java.util.Objects.equals(selectedPack, appliedPack)
                || shadersEnabled != Iris.getIrisConfig().areShadersEnabled()
                || !Iris.getShaderPackOptionQueue().isEmpty();
        applyButton.enabled = changed;
    }

    public void selectPack(String packName) {
        this.selectedPack = packName;
        updateApplyButton();
    }

    // ===== Shader Pack List =====

    private class ShaderPackList extends GuiSlot {
        private final List<PackEntry> entries = new ArrayList<>();

        public ShaderPackList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, bottom - top, top, bottom, 20);
            refreshEntries();
        }

        public void refreshEntries() {
            entries.clear();
            entries.add(new PackEntry(null, "(none) - No Shader Pack"));
            try {
                java.util.Collection<String> packs = Iris.getShaderpacksDirectoryManager().enumerate();
                for (String pack : packs) {
                    entries.add(new PackEntry(pack, pack));
                }
            } catch (Exception e) {
                Iris.logger.error("Failed to enumerate shader packs", e);
            }
        }

        @Override protected int getSize() { return entries.size(); }

        private long lastClickTime;
        private int lastClickIndex = -1;

        /**
         * Manual click handling — mirrors ShaderOptionList.listMouseClicked().
         * GuiSlot normally fires elementClicked from handleMouseInput, but our
         * handleMouseInput override only forwards scroll events, so we route
         * clicks here instead.
         */
        public void listMouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseButton != 0) return;
            if (mouseY < top || mouseY > bottom) return;

            int relativeY = mouseY - top + (int) amountScrolled - headerPadding;
            int index = relativeY / slotHeight;

            if (index < 0 || index >= entries.size()) return;

            long now = Minecraft.getSystemTime();
            boolean doubleClick = (index == lastClickIndex && now - lastClickTime < 250L);
            lastClickTime = now;
            lastClickIndex = index;

            elementClicked(index, doubleClick, mouseX, mouseY);
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            if (index >= 0 && index < entries.size()) {
                selectPack(entries.get(index).packName);
                if (doubleClick) applyChanges();
            }
        }

        @Override
        protected boolean isSelected(int index) {
            return index >= 0 && index < entries.size()
                    && java.util.Objects.equals(entries.get(index).packName, selectedPack);
        }

        @Override protected void drawBackground() {}

        @Override
        protected void drawSlot(int i, int x, int y, int h, int mouseX, int mouseY, float pt) {
            if (i < 0 || i >= entries.size()) return;
            PackEntry entry = entries.get(i);
            String text = entry.displayName;
            int color = 0xFFFFFF;
            boolean isApplied = java.util.Objects.equals(entry.packName, appliedPack);
            if (isApplied && shadersEnabled) color = 0xFFF263;
            else if (!shadersEnabled && entry.packName != null) color = 0xA2A2A2;
            if (java.util.Objects.equals(entry.packName, selectedPack)) text = "\u00a7l" + text;
            int maxW = getListWidth() - 8;
            if (fontRenderer.getStringWidth(text) > maxW) text = GuiUtil.shortenText(fontRenderer, text, maxW);
            drawCenteredString(fontRenderer, text, width / 2, y + 4, color);
        }

        @Override public int getListWidth() { return Math.min(308, width - 50); }
        @Override protected int getScrollBarX() { return width - 6; }
    }

    // ===== Shader Option List =====

    private class ShaderOptionList extends GuiSlot {
        private final List<OptionRow> rows = new ArrayList<>();

        public ShaderOptionList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, bottom - top, top, bottom, 24);
        }

        public void rebuild() {
            rows.clear();
            if (navigation == null) return;

            OptionMenuContainer container = navigation.getContainer();
            String currentScreenId = navigation.getCurrentScreen();

            OptionMenuElementScreen screen;
            if (currentScreenId != null && container.subScreens.containsKey(currentScreenId)) {
                screen = container.subScreens.get(currentScreenId);
            } else {
                screen = container.mainScreen;
            }

            if (screen == null) return;

            // Add back button if we have navigation history
            if (navigation.hasHistory()) {
                rows.add(new OptionRow(null, true));
            }

            int columns = screen.getColumnCount();
            List<AbstractElementWidget<?>> widgets = new ArrayList<>();

            for (OptionMenuElement element : screen.elements) {
                AbstractElementWidget<?> widget = createWidget(element);
                widget.init(ShaderPackScreen.this, navigation);
                widgets.add(widget);
            }

            // Group into rows
            for (int i = 0; i < widgets.size(); i += columns) {
                List<AbstractElementWidget<?>> rowWidgets = new ArrayList<>();
                for (int j = 0; j < columns && (i + j) < widgets.size(); j++) {
                    rowWidgets.add(widgets.get(i + j));
                }
                rows.add(new OptionRow(rowWidgets, false));
            }
        }

        private AbstractElementWidget<?> createWidget(OptionMenuElement element) {
            if (element instanceof OptionMenuBooleanOptionElement) {
                return new BooleanElementWidget((OptionMenuBooleanOptionElement) element);
            } else if (element instanceof OptionMenuStringOptionElement) {
                OptionMenuStringOptionElement strElement = (OptionMenuStringOptionElement) element;
                if (strElement.slider) {
                    return new SliderElementWidget(strElement);
                }
                return new StringElementWidget(strElement);
            } else if (element instanceof OptionMenuLinkElement) {
                return new LinkElementWidget((OptionMenuLinkElement) element);
            } else if (element instanceof OptionMenuProfileElement) {
                return new ProfileElementWidget((OptionMenuProfileElement) element);
            }
            return AbstractElementWidget.EMPTY;
        }

        @Override protected int getSize() { return rows.size(); }
        @Override protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            // Click handling is done via listMouseClicked to avoid double-fire
        }

        /**
         * Manual click handling to avoid double-fire from handleMouseInput.
         */
        public void listMouseClicked(int mouseX, int mouseY, int mouseButton) {
            if (mouseY < top || mouseY > bottom) return;

            int rowWidth = getListWidth();
            int startX = (width - rowWidth) / 2;

            // Calculate which row was clicked
            int relativeY = mouseY - top + (int) amountScrolled - headerPadding;
            int index = relativeY / slotHeight;

            if (index < 0 || index >= rows.size()) return;

            OptionRow row = rows.get(index);
            if (row.isBackButton) {
                if (navigation != null) navigation.back();
                GuiUtil.playButtonClickSound();
                return;
            }
            if (row.widgets == null) return;

            int widgetWidth = (rowWidth - (row.widgets.size() - 1) * 2) / row.widgets.size();
            for (int i = 0; i < row.widgets.size(); i++) {
                int wx = startX + i * (widgetWidth + 2);
                if (mouseX >= wx && mouseX < wx + widgetWidth) {
                    row.widgets.get(i).mouseClicked(mouseX, mouseY, mouseButton);
                    updateApplyButton();
                    return;
                }
            }
        }

        /**
         * Routes mouse release to all widgets (needed for slider drag release).
         */
        public void listMouseReleased(int mouseX, int mouseY, int mouseButton) {
            for (OptionRow row : rows) {
                if (row.widgets == null) continue;
                for (AbstractElementWidget<?> widget : row.widgets) {
                    widget.mouseReleased(mouseX, mouseY, mouseButton);
                }
            }
            updateApplyButton();
        }

        @Override protected boolean isSelected(int index) { return false; }

        @Override
        protected void drawBackground() {}

        // Accumulates the hovered widget's comment during drawSlot calls;
        // read once per frame in drawScreen after all slots have been drawn.
        private String pendingCommentTitle;
        private String pendingCommentBody;

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            // Clamp mouseY so hover states don't trigger below the list area
            int clampedMouseY = mouseY > bottom ? -1 : mouseY;

            // Reset pending comment before drawing slots
            pendingCommentTitle = null;
            pendingCommentBody = null;

            // Enable GL scissor to clip list content to its bounds
            net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
            int scale = sr.getScaleFactor();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(0, mc.displayHeight - bottom * scale, mc.displayWidth, (bottom - top) * scale);

            super.drawScreen(mouseX, clampedMouseY, partialTicks);

            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);

            // Update the screen's hovered comment once per frame, after all slots drawn
            if (pendingCommentTitle != null) {
                setHoveredComment(pendingCommentTitle, pendingCommentBody);
            } else {
                clearHoveredComment();
            }
        }

        @Override
        protected void drawSlot(int index, int x, int y, int h, int mouseX, int mouseY, float pt) {
            if (index < 0 || index >= rows.size()) return;
            OptionRow row = rows.get(index);

            if (row.isBackButton) {
                boolean hovered = mouseY >= y && mouseY < y + h && mouseX >= x && mouseX < x + getListWidth();
                int btnY = y + (h - 20) / 2;
                GuiUtil.drawButton(x, btnY, getListWidth(), 20, hovered, false);
                drawCenteredString(fontRenderer, "\u00a7o< Back", width / 2, btnY + 6, 0xFFFFFF);
                return;
            }

            if (row.widgets == null) return;

            int rowWidth = getListWidth();
            int startX = (width - rowWidth) / 2;
            int widgetWidth = (rowWidth - (row.widgets.size() - 1) * 2) / row.widgets.size();

            for (int i = 0; i < row.widgets.size(); i++) {
                int wx = startX + i * (widgetWidth + 2);
                boolean wHovered = mouseX >= wx && mouseX < wx + widgetWidth && mouseY >= y && mouseY < y + h;
                row.widgets.get(i).render(wx, y, widgetWidth, h, mouseX, mouseY, pt, wHovered);

                if (wHovered) {
                    AbstractElementWidget<?> widget = row.widgets.get(i);
                    String title = widget.getCommentTitle();
                    String body = widget.getCommentBody();
                    if (title != null) {
                        pendingCommentTitle = title;
                        pendingCommentBody = body;
                    }

                    // Trimmed label tooltip or shift-to-reset hint
                    boolean trimmed = false;
                    if (widget instanceof BooleanElementWidget) {
                        trimmed = ((BooleanElementWidget) widget).isLabelTrimmed();
                    } else if (widget instanceof StringElementWidget) {
                        trimmed = ((StringElementWidget) widget).isLabelTrimmed();
                    }

                    if (GuiScreen.isShiftKeyDown()) {
                        final int tx = mouseX + 2, ty = mouseY - 16;
                        TOP_LAYER_RENDER_QUEUE.add(() ->
                            GuiUtil.drawTextPanel(fontRenderer, "\u00a7aSet to Default", tx, ty));
                    } else if (trimmed && !isDisplayingComment() && title != null) {
                        final int tx = mouseX + 2, ty = mouseY - 16;
                        final String fullLabel = title;
                        TOP_LAYER_RENDER_QUEUE.add(() ->
                            GuiUtil.drawTextPanel(fontRenderer, fullLabel, tx, ty));
                    }
                }
            }
        }

        @Override public int getListWidth() { return Math.min(400, width - 12); }
        @Override protected int getScrollBarX() { return width - 6; }
    }

    private static class OptionRow {
        final List<AbstractElementWidget<?>> widgets;
        final boolean isBackButton;

        OptionRow(List<AbstractElementWidget<?>> widgets, boolean isBackButton) {
            this.widgets = widgets;
            this.isBackButton = isBackButton;
        }
    }

    private static class PackEntry {
        final String packName;
        final String displayName;
        PackEntry(String packName, String displayName) {
            this.packName = packName;
            this.displayName = displayName;
        }
    }
}
