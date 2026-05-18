package net.coderbot.iris.gui;

import net.coderbot.iris.shaderpack.option.menu.OptionMenuContainer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Controls navigation between shader pack option sub-screens.
 */
public class NavigationController {
    private final OptionMenuContainer container;
    private Runnable rebuildCallback;
    private String currentScreen;
    private final Deque<String> history = new ArrayDeque<>();

    public NavigationController(OptionMenuContainer container) {
        this.container = container;
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }

    public void back() {
        if (!history.isEmpty()) {
            history.pop();
            currentScreen = history.isEmpty() ? null : history.peek();
        } else {
            currentScreen = null;
        }
        rebuild();
    }

    public void open(String screen) {
        currentScreen = screen;
        history.push(screen);
        rebuild();
    }

    private void rebuild() {
        if (rebuildCallback != null) {
            rebuildCallback.run();
        }
    }

    public void refresh() {
        // Refresh is used to update widgets without full rebuild
        if (rebuildCallback != null) {
            rebuildCallback.run();
        }
    }

    public boolean hasHistory() {
        return !history.isEmpty();
    }

    public String getCurrentScreen() {
        return currentScreen;
    }

    public OptionMenuContainer getContainer() {
        return container;
    }
}
