package dev.ssscfw.venusmod.client;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** マウス/矢印/Enterで選択できる実際のドロップダウン。画面端では上側へ展開する。 */
final class YamatoDropdown extends Button {
    private static final int OPTION_HEIGHT = 20;
    private final List<Component> options;
    private final IntConsumer changed;
    private int value;
    private int highlighted;
    private boolean expanded;

    YamatoDropdown(int x, int y, int width, List<Component> options, int value,
                   Component description, IntConsumer changed) {
        this(x, y, width, 20, options, value, description, changed);
    }

    YamatoDropdown(int x, int y, int width, int height, List<Component> options, int value,
                   Component description, IntConsumer changed) {
        super(x, y, width, height, Component.empty(), b -> ((YamatoDropdown)b).toggle(), DEFAULT_NARRATION);
        this.options = List.copyOf(options);
        this.changed = changed;
        setValue(value);
        setTooltip(Tooltip.create(description));
    }

    void setValue(int value) {
        this.value = Math.max(0, Math.min(options.size() - 1, value));
        setMessage(Component.empty().append(options.get(this.value)).append("  ▾"));
    }

    boolean expanded() {
        return expanded;
    }

    void close() {
        expanded = false;
    }

    private void toggle() {
        expanded = !expanded;
        highlighted = value;
    }

    private int menuWidth(int screenWidth) {
        int desired = getWidth();
        for (Component option : options) desired = Math.max(desired, Minecraft.getInstance().font.width(option) + 24);
        return Math.min(screenWidth - 8, desired);
    }

    private int menuX(int screenWidth) {
        return Math.max(4, Math.min(getX(), screenWidth - menuWidth(screenWidth) - 4));
    }

    private int menuY(int screenHeight) {
        int height = options.size() * OPTION_HEIGHT;
        int below = getY() + getHeight() + 2;
        return Math.max(4, below + height <= screenHeight - 4 ? below : getY() - height - 2);
    }

    void renderMenu(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!expanded) return;
        int x = menuX(screenWidth), y = menuY(screenHeight), width = menuWidth(screenWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.fill(x - 1, y - 1, x + width + 1, y + options.size() * OPTION_HEIGHT + 1, 0xFF8097B8);
        graphics.fill(x, y, x + width, y + options.size() * OPTION_HEIGHT, 0xFF141B2A);
        for (int index = 0; index < options.size(); index++) {
            int rowY = y + index * OPTION_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + OPTION_HEIGHT;
            if (hovered || index == highlighted) graphics.fill(x + 1, rowY, x + width - 1, rowY + OPTION_HEIGHT, 0xFF344861);
            String label = (index == value ? "✓ " : "  ") + options.get(index).getString();
            graphics.drawString(Minecraft.getInstance().font,
                    Minecraft.getInstance().font.plainSubstrByWidth(label, width - 8), x + 4, rowY + 6, 0xFFEAF3FF, false);
        }
        graphics.pose().popPose();
    }

    boolean clickMenu(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!expanded) return false;
        int x = menuX(screenWidth), y = menuY(screenHeight), width = menuWidth(screenWidth);
        if (button == 0 && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + options.size() * OPTION_HEIGHT) {
            select((int)((mouseY - y) / OPTION_HEIGHT));
        } else {
            close();
        }
        return true;
    }

    boolean menuKey(int keyCode) {
        if (!expanded) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) close();
        else if (keyCode == GLFW.GLFW_KEY_UP) highlighted = Math.floorMod(highlighted - 1, options.size());
        else if (keyCode == GLFW.GLFW_KEY_DOWN) highlighted = (highlighted + 1) % options.size();
        else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) select(highlighted);
        return true;
    }

    private void select(int selected) {
        close();
        if (selected == value) return;
        setValue(selected);
        changed.accept(value);
    }
}
