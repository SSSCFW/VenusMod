package dev.ssscfw.venusmod.client;

import dev.ssscfw.venusmod.compat.YamatoCompat;
import dev.ssscfw.venusmod.yamato.YamatoNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** 閻魔刀の次元移動。サーバーの保存データと画面の未送信入力を分けて保持する。 */
public final class YamatoTravelScreen extends Screen {
    private static final String PREFIX = "gui.venusmod.yamato.";
    private static final int TEXT = 0xFFE7EFFF;
    private static final int MUTED = 0xFFAABCD3;
    private static final int ACCENT = 0xFFB1C8FF;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Rift> rifts = new ArrayList<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private String currentFolder = "";
    private int selectedTab;
    private int page;
    private int sort;
    private long snapshotMillis;
    private int draftAccess;
    private int draftLifetime;
    private String draftSeconds = "1";
    private boolean settingsEdited;
    private int[] pendingSettings;
    private boolean applyingSnapshot;
    private YamatoScreenLayout layout;
    private SettingsControls sidebarControls;
    private YamatoDropdown sortDropdown;
    private Button previous;
    private Button next;
    private Button back;
    private Button breadcrumb;
    private Button destinationsTab;
    private Button riftsTab;

    public YamatoTravelScreen(CompoundTag data) {
        super(tr("title"));
        receiveSnapshot(data);
    }

    static Component tr(String key, Object... arguments) {
        return Component.translatable(PREFIX + key, arguments);
    }

    private static List<Component> options(String group, int count) {
        List<Component> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(tr(group + "." + index));
        return result;
    }

    private static void request(String action, CompoundTag request) {
        YamatoNetwork.sendAction(action, request);
    }

    private static CompoundTag identifier(UUID id) {
        CompoundTag data = new CompoundTag();
        data.putUUID("id", id);
        return data;
    }

    /** 一覧のみ差し替える。名前編集や秒数入力へ定期スナップショットを上書きしない。 */
    public void receiveSnapshot(CompoundTag data) {
        List<Entry> previousEntries = List.copyOf(entries);
        List<Rift> previousRifts = List.copyOf(rifts);
        int previousSort = sort;
        entries.clear();
        ListTag entryTags = data.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < entryTags.size(); index++) {
            CompoundTag tag = entryTags.getCompound(index);
            if (tag.hasUUID("id")) entries.add(new Entry(tag.getUUID("id"), tag.getString("parent"),
                    tag.getString("name"), tag.getBoolean("folder"), tag.getString("dimension"),
                    tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getLong("created")));
        }
        rifts.clear();
        ListTag riftTags = data.getList("active", Tag.TAG_COMPOUND);
        for (int index = 0; index < riftTags.size(); index++) {
            CompoundTag tag = riftTags.getCompound(index);
            if (tag.hasUUID("id")) rifts.add(new Rift(tag.getUUID("id"), tag.getString("name"),
                    tag.getString("sourceDimension"), tag.getDouble("sourceX"), tag.getDouble("sourceY"), tag.getDouble("sourceZ"),
                    tag.getString("targetDimension"), tag.getDouble("targetX"), tag.getDouble("targetY"), tag.getDouble("targetZ"),
                    tag.getInt("lifetime"), tag.getInt("seconds"), tag.getInt("remaining")));
        }
        snapshotMillis = System.currentTimeMillis();
        sort = Math.max(0, Math.min(4, data.getInt("sort")));
        if (!currentFolder.isEmpty() && entries.stream().noneMatch(e -> e.folder && e.id.toString().equals(currentFolder))) {
            currentFolder = "";
            page = 0;
        }
        CompoundTag settings = data.getCompound("settings");
        int access = Math.max(0, Math.min(3, settings.getInt("access")));
        int lifetime = Math.max(0, Math.min(2, settings.getInt("lifetime")));
        int seconds = Math.max(1, Math.min(180, settings.getInt("seconds")));
        if (pendingSettings != null && pendingSettings[0] == access && pendingSettings[1] == lifetime
                && pendingSettings[2] == seconds && draftAccess == access && draftLifetime == lifetime
                && Integer.toString(seconds).equals(draftSeconds)) {
            settingsEdited = false;
            pendingSettings = null;
        }
        if (!settingsEdited) {
            draftAccess = access;
            draftLifetime = lifetime;
            draftSeconds = Integer.toString(seconds);
        }
        if (sortDropdown != null) sortDropdown.setValue(sort);
        if (sidebarControls != null) sidebarControls.sync();
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SettingsScreen settingsScreen && settingsScreen.parent() == this && settingsScreen.controls != null) {
            settingsScreen.controls.sync();
        }
        if (layout != null) {
            riftsTab.setMessage(tr("active_count", rifts.size()));
            if (selectedTab == 0 ? !previousEntries.equals(entries) || previousSort != sort : !previousRifts.equals(rifts)) refreshRows();
        }
    }

    @Override
    protected void init() {
        layout = YamatoScreenLayout.forWindow(width, height);
        rowWidgets.clear();
        sidebarControls = null;
        addRenderableWidget(Button.builder(tr("close_screen"), b -> onClose())
                .bounds(layout.left() + layout.width() - 44, layout.top() + 5, 38, 18).build());
        if (!layout.sidebar()) {
            addRenderableWidget(Button.builder(tr("settings"), b -> show(new SettingsScreen(this)))
                    .bounds(layout.left() + layout.width() - 122, layout.top() + 5, 74, 18).build());
        } else {
            sidebarControls = new SettingsControls(this::addRenderableWidget,
                    layout.sideX() + 4, layout.settingsY(), layout.sideWidth() - 8,
                    layout.settingsStep(), layout.settingsControlOffset(), layout.applyY());
            addRenderableWidget(Button.builder(tr("save_current"), b -> create(false))
                    .bounds(layout.sideX() + 4, layout.saveY(), layout.sideWidth() - 8, 20)
                    .tooltip(Tooltip.create(tr("save_current_hint"))).build());
            addRenderableWidget(Button.builder(tr("new_folder"), b -> create(true))
                    .bounds(layout.sideX() + 4, layout.folderY(), layout.sideWidth() - 8, 20)
                    .tooltip(Tooltip.create(tr("new_folder"))).build());
        }
        int x = layout.listX(), listWidth = layout.listWidth();
        int tabWidth = (listWidth - 4) / 2;
        destinationsTab = addRenderableWidget(Button.builder(tr("destinations"), b -> selectTab(0))
                .bounds(x, layout.top() + 28, tabWidth, 20).build());
        riftsTab = addRenderableWidget(Button.builder(tr("active"), b -> selectTab(1))
                .bounds(x + tabWidth + 4, layout.top() + 28, listWidth - tabWidth - 4, 20)
                .tooltip(Tooltip.create(tr("active_hint"))).build());
        back = addRenderableWidget(Button.builder(Component.literal("‹"), b -> goBackFolder())
                .bounds(x, layout.top() + 52, 22, 20).tooltip(Tooltip.create(tr("parent_folder"))).build());
        breadcrumb = addRenderableWidget(Button.builder(Component.empty(), b -> openFolder(""))
                .bounds(x + 26, layout.top() + 52, listWidth - 134, 20).build());
        sortDropdown = addRenderableWidget(new YamatoDropdown(x + listWidth - 104, layout.top() + 52, 104,
                options("sort", 5), sort, tr("sort_hint"), value -> {
                    sort = value;
                    page = 0;
                    CompoundTag data = new CompoundTag();
                    data.putInt("sort", value);
                    request("sort", data);
                    refreshRows();
                }));
        previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> turnPage(-1))
                .bounds(x + listWidth - 86, layout.footerY(), 22, 20)
                .tooltip(Tooltip.create(tr("previous_page"))).build());
        next = addRenderableWidget(Button.builder(Component.literal("›"), b -> turnPage(1))
                .bounds(x + listWidth - 22, layout.footerY(), 22, 20)
                .tooltip(Tooltip.create(tr("next_page"))).build());
        refreshRows();
    }

    private void show(Screen screen) {
        closeDropdowns();
        if (minecraft != null) minecraft.setScreen(screen);
    }

    private void create(boolean folder) {
        show(new NameScreen(this, folder, currentFolder));
    }

    private void selectTab(int value) {
        selectedTab = value;
        page = 0;
        closeDropdowns();
        refreshRows();
    }

    private void openFolder(String parent) {
        currentFolder = parent;
        page = 0;
        refreshRows();
    }

    private void goBackFolder() {
        Entry folder = find(currentFolder);
        openFolder(folder == null ? "" : folder.parent);
    }

    private void turnPage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        refreshRows();
    }

    private Entry find(String id) {
        return entries.stream().filter(entry -> entry.id.toString().equals(id)).findFirst().orElse(null);
    }

    private String path(String parent) {
        List<String> names = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (!parent.isEmpty() && visited.add(parent)) {
            Entry entry = find(parent);
            if (entry == null) break;
            names.add(0, entry.name);
            parent = entry.parent;
        }
        names.add(0, tr("root_folder").getString());
        return String.join(" / ", names);
    }

    private List<Entry> visibleEntries() {
        List<Entry> result = new ArrayList<>(entries.stream().filter(e -> e.parent.equals(currentFolder)).toList());
        Comparator<Entry> name = Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER);
        Comparator<Entry> ordering = switch (sort) {
            case 1 -> name;
            case 2 -> name.reversed();
            case 3 -> Comparator.comparingLong((Entry e) -> e.created).reversed().thenComparing(name);
            case 4 -> Comparator.comparing((Entry e) -> e.dimension).thenComparing(name);
            default -> null;
        };
        if (ordering != null) result.sort(Comparator.comparing((Entry e) -> !e.folder).thenComparing(ordering));
        return result;
    }

    private int pageCount() {
        int size = selectedTab == 0 ? visibleEntries().size() : rifts.size();
        return Math.max(1, (size + layout.rowCount() - 1) / layout.rowCount());
    }

    private void refreshRows() {
        int focusedRow = rowWidgets.indexOf(getFocused());
        for (AbstractWidget row : rowWidgets) removeWidget(row);
        rowWidgets.clear();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        previous.active = page > 0;
        next.active = page + 1 < pageCount();
        destinationsTab.active = selectedTab != 0;
        riftsTab.active = selectedTab != 1;
        riftsTab.setMessage(tr("active_count", rifts.size()));
        back.visible = breadcrumb.visible = sortDropdown.visible = selectedTab == 0;
        back.active = !currentFolder.isEmpty();
        breadcrumb.setMessage(Component.literal(path(currentFolder)));
        breadcrumb.setTooltip(Tooltip.create(tr("root_folder_hint", path(currentFolder))));
        int offset = page * layout.rowCount();
        int x = layout.listX(), listWidth = layout.listWidth();
        if (selectedTab == 0) {
            List<Entry> visible = visibleEntries();
            for (int row = 0; row < layout.rowCount() && offset + row < visible.size(); row++) {
                Entry entry = visible.get(offset + row);
                int y = layout.rowsY() + row * YamatoScreenLayout.ROW_HEIGHT;
                Component detail = entry.folder ? tr("folder") : Component.literal(entry.dimension + " · " + coordinates(entry.x, entry.y, entry.z));
                row(new EntryButton(x, y, listWidth - 32, Component.literal(entry.name), detail,
                        b -> { if (entry.folder) openFolder(entry.id.toString()); else travel(entry); },
                        entry.folder ? tr("open_folder_hint", entry.name) : tr("travel_hint", entry.name, entry.dimension, coordinates(entry.x, entry.y, entry.z))));
                row(Button.builder(tr("edit_short"), b -> show(new EditScreen(this, entry)))
                        .bounds(x + listWidth - 28, y, 28, 32).tooltip(Tooltip.create(tr("edit_hint", entry.name))).build());
            }
            if (!layout.sidebar()) {
                row(Button.builder(tr("save_short"), b -> create(false)).bounds(x, layout.footerY(), 84, 20)
                        .tooltip(Tooltip.create(tr("save_current_hint"))).build());
                row(Button.builder(tr("new_folder"), b -> create(true)).bounds(x + 88, layout.footerY(), 84, 20).build());
            }
        } else {
            for (int row = 0; row < layout.rowCount() && offset + row < rifts.size(); row++) {
                Rift rift = rifts.get(offset + row);
                int y = layout.rowsY() + row * YamatoScreenLayout.ROW_HEIGHT;
                row(new EntryButton(x, y, listWidth - 50, Component.literal(rift.name),
                        Component.empty().append(remaining(rift)).append(" · " + rift.targetDimension),
                        b -> show(new RiftDetailsScreen(this, rift)), tr("rift_details_hint")));
                row(Button.builder(tr("close_rift_short"), b -> request("close", identifier(rift.id)))
                        .bounds(x + listWidth - 46, y, 46, 32).tooltip(Tooltip.create(tr("close_rift_hint"))).build());
            }
            Button closeAll = Button.builder(tr("close_all"), b -> request("close_all", new CompoundTag()))
                    .bounds(x, layout.footerY(), Math.min(140, listWidth - 96), 20)
                    .tooltip(Tooltip.create(tr("close_all"))).build();
            closeAll.active = !rifts.isEmpty();
            row(closeAll);
        }
        if (focusedRow >= 0) setFocused(focusedRow < rowWidgets.size() ? rowWidgets.get(focusedRow) : null);
    }

    private void row(AbstractWidget widget) {
        rowWidgets.add(addRenderableWidget(widget));
    }

    private void travel(Entry entry) {
        if (!applySettings()) {
            if (!layout.sidebar()) show(new SettingsScreen(this));
            return;
        }
        if (minecraft != null) minecraft.setScreen(null);
        request("travel", identifier(entry.id));
    }

    private boolean applySettings() {
        Integer seconds = validSeconds();
        if (seconds == null) return false;
        if (!settingsEdited) return true;
        draftSeconds = Integer.toString(seconds);
        if (sidebarControls != null) sidebarControls.sync();
        CompoundTag data = new CompoundTag();
        data.putInt("access", draftAccess);
        data.putInt("lifetime", draftLifetime);
        data.putInt("seconds", seconds);
        pendingSettings = new int[]{draftAccess, draftLifetime, seconds};
        request("settings", data);
        return true;
    }

    private Integer validSeconds() {
        try {
            int value = Integer.parseInt(draftSeconds);
            if (value >= 1 && value <= 180) return value;
            return draftLifetime == 2 ? 1 : null;
        } catch (NumberFormatException invalid) {
            return draftLifetime == 2 ? 1 : null;
        }
    }

    private List<YamatoDropdown> dropdowns() {
        List<YamatoDropdown> result = new ArrayList<>();
        if (sortDropdown != null) result.add(sortDropdown);
        if (sidebarControls != null) result.addAll(sidebarControls.dropdowns());
        return result;
    }

    private void closeDropdowns() {
        dropdowns().forEach(YamatoDropdown::close);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (YamatoDropdown dropdown : dropdowns()) {
            if (dropdown.expanded()) return dropdown.clickMenu(mouseX, mouseY, button, width, height);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (dropdowns().stream().anyMatch(YamatoDropdown::expanded)) return true;
        if (mouseX >= layout.listX() && mouseX < layout.listX() + layout.listWidth()
                && mouseY >= layout.rowsY() && mouseY < layout.footerY()) {
            turnPage(vertical > 0 ? -1 : vertical < 0 ? 1 : 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (YamatoDropdown dropdown : dropdowns()) if (dropdown.menuKey(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (!hasBlade()) onClose();
        if (sidebarControls != null) sidebarControls.updateButtons();
    }

    private static boolean hasBlade() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.isAlive() && !YamatoCompat.heldYamato(client.player).isEmpty();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        panel(graphics, layout.left(), layout.top(), layout.width(), layout.height());
        graphics.drawString(font, title, layout.left() + 8, layout.top() + 10, ACCENT, false);
        graphics.fill(layout.listX(), layout.rowsY() - 1, layout.listX() + layout.listWidth(), layout.footerY() - 3, 0xA00A1020);
        if (layout.sidebar()) {
            graphics.fill(layout.sideX(), layout.top() + 27, layout.sideX() + layout.sideWidth(), layout.top() + layout.height() - 5, 0xA00A1020);
            sidebarControls.renderLabels(graphics);
            int hintY = layout.folderY() + 28;
            int hintHeight = font.split(tr("settings_auto_hint"), layout.sideWidth() - 8).size() * 10;
            if (hintY + hintHeight <= layout.top() + layout.height() - 8) {
                drawWrapped(graphics, tr("settings_auto_hint"), layout.sideX() + 4, hintY, layout.sideWidth() - 8, MUTED);
            }
        }
        if (selectedTab == 1) graphics.drawString(font, shortened(tr("active_hint"), layout.listWidth() - 6),
                layout.listX() + 3, layout.top() + 59, MUTED, false);
        boolean empty = selectedTab == 0 ? visibleEntries().isEmpty() : rifts.isEmpty();
        if (empty) drawWrapped(graphics, tr(selectedTab == 0 ? "empty_destinations" : "empty_rifts"),
                layout.listX() + 12, layout.rowsY() + 18, layout.listWidth() - 24, MUTED);
        graphics.drawCenteredString(font, Component.literal((page + 1) + "/" + pageCount()),
                layout.listX() + layout.listWidth() - 43, layout.footerY() + 6, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (YamatoDropdown dropdown : dropdowns()) dropdown.renderMenu(graphics, mouseX, mouseY, width, height);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xF5141C2C);
        graphics.fill(x, y, x + width, y + 1, 0xFF748DAC);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF748DAC);
        graphics.fill(x, y, x + 1, y + height, 0xFF748DAC);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF748DAC);
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    private String shortened(Component text, int availableWidth) {
        String value = text.getString();
        if (font.width(value) <= availableWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, availableWidth - font.width("…"))) + "…";
    }

    private static String coordinates(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    private Component remaining(Rift rift) {
        if (rift.lifetime == 2) return tr("unlimited");
        if (rift.remaining < 0) return tr("awaiting_first", rift.seconds);
        long elapsed = Math.max(0, (System.currentTimeMillis() - snapshotMillis) / 1000);
        return tr("remaining", Math.max(0, rift.remaining - elapsed));
    }

    private record Entry(UUID id, String parent, String name, boolean folder, String dimension,
                         double x, double y, double z, long created) {}

    private record Rift(UUID id, String name, String sourceDimension, double sourceX, double sourceY, double sourceZ,
                        String targetDimension, double targetX, double targetY, double targetZ,
                        int lifetime, int seconds, int remaining) {}

    private static final class EntryButton extends Button {
        private final Component detail;

        private EntryButton(int x, int y, int width, Component name, Component detail, OnPress action, Component tooltip) {
            super(x, y, width, 32, name, action, DEFAULT_NARRATION);
            this.detail = detail;
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() ? 0xFF344963 : 0xFF202D42);
            if (isFocused()) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, ACCENT);
                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), ACCENT);
            }
            graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), getWidth() - 12), getX() + 6, getY() + 5, TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), getWidth() - 12), getX() + 6, getY() + 19, MUTED, false);
        }
    }

    private final class SettingsControls {
        private final int x;
        private final int y;
        private final int width;
        private final int step;
        private final YamatoDropdown access;
        private final YamatoDropdown lifetime;
        private final EditBox seconds;
        private final Button apply;

        private SettingsControls(Consumer<AbstractWidget> add, int x, int y, int width, int step, int offset, int applyY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.step = step;
            access = new YamatoDropdown(x, y + offset, width, options("access", 4), draftAccess, tr("access_hint"), value -> {
                draftAccess = value;
                settingsEdited = true;
            });
            lifetime = new YamatoDropdown(x, y + step + offset, width, options("lifetime", 3), draftLifetime, tr("lifetime_hint"), value -> {
                draftLifetime = value;
                settingsEdited = true;
                updateButtons();
            });
            seconds = new EditBox(font, x, y + step * 2 + offset, width, 20, tr("seconds"));
            seconds.setMaxLength(3);
            seconds.setFilter(text -> text.matches("[0-9]{0,3}"));
            seconds.setValue(draftSeconds);
            seconds.setResponder(text -> {
                if (applyingSnapshot) return;
                draftSeconds = text;
                settingsEdited = true;
                updateButtons();
            });
            seconds.setTooltip(Tooltip.create(tr("seconds_hint")));
            apply = Button.builder(tr("apply_settings"), b -> applySettings()).bounds(x, applyY, width, 20)
                    .tooltip(Tooltip.create(tr("settings_auto_hint"))).build();
            add.accept(access);
            add.accept(lifetime);
            add.accept(seconds);
            add.accept(apply);
            updateButtons();
        }

        private List<YamatoDropdown> dropdowns() {
            return List.of(access, lifetime);
        }

        private void sync() {
            applyingSnapshot = true;
            access.setValue(draftAccess);
            lifetime.setValue(draftLifetime);
            if (!seconds.getValue().equals(draftSeconds)) seconds.setValue(draftSeconds);
            applyingSnapshot = false;
            updateButtons();
        }

        private void updateButtons() {
            seconds.setEditable(draftLifetime != 2);
            apply.active = settingsEdited && validSeconds() != null;
            seconds.setTextColor(validSeconds() == null ? 0xFFFF7777 : TEXT);
        }

        private void renderLabels(GuiGraphics graphics) {
            graphics.drawString(font, shortened(tr("access_label"), width), x, y, MUTED, false);
            graphics.drawString(font, shortened(tr("lifetime_label"), width), x, y + step, MUTED, false);
            graphics.drawString(font, shortened(tr("seconds"), width), x, y + step * 2, MUTED, false);
        }
    }

    /** 編集・確認画面も同じ親データを受け取り、画面を閉じずに定期更新へ追従する。 */
    public abstract static class Child extends Screen {
        protected final YamatoTravelScreen parent;
        protected final Screen backScreen;
        protected int panelX;
        protected int panelY;
        protected int panelWidth;
        protected int panelHeight;

        protected Child(YamatoTravelScreen parent, Screen backScreen, Component title) {
            super(title);
            this.parent = parent;
            this.backScreen = backScreen;
        }

        public final YamatoTravelScreen parent() {
            return parent;
        }

        protected void sizePanel(int desiredWidth, int desiredHeight) {
            panelWidth = Math.min(width - 16, desiredWidth);
            panelHeight = Math.min(height - 16, desiredHeight);
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
        }

        @Override
        public void onClose() {
            if (minecraft != null) minecraft.setScreen(backScreen);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void tick() {
            super.tick();
            if (!hasBlade() && minecraft != null) minecraft.setScreen(null);
        }

        protected void background(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            panel(graphics, panelX, panelY, panelWidth, panelHeight);
            graphics.drawCenteredString(font, title, width / 2, panelY + 12, ACCENT);
        }
    }

    private static final class SettingsScreen extends Child {
        private SettingsControls controls;

        private SettingsScreen(YamatoTravelScreen parent) {
            super(parent, parent, tr("settings"));
        }

        @Override
        protected void init() {
            sizePanel(344, 238);
            controls = parent.new SettingsControls(this::addRenderableWidget, panelX + 12, panelY + 26, panelWidth - 24,
                    40, 12, panelY + 148);
            addRenderableWidget(Button.builder(tr("done"), b -> onClose())
                    .bounds(panelX + 12, panelY + panelHeight - 28, panelWidth - 24, 20)
                    .tooltip(Tooltip.create(tr("settings_auto_hint"))).build());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (YamatoDropdown dropdown : controls.dropdowns()) {
                if (dropdown.expanded()) return dropdown.clickMenu(mouseX, mouseY, button, width, height);
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (YamatoDropdown dropdown : controls.dropdowns()) if (dropdown.menuKey(keyCode)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void tick() {
            super.tick();
            controls.updateButtons();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            controls.renderLabels(graphics);
            if (panelHeight >= 238) parent.drawWrapped(graphics, tr("settings_auto_hint"), panelX + 12, panelY + 174, panelWidth - 24, MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            for (YamatoDropdown dropdown : controls.dropdowns()) dropdown.renderMenu(graphics, mouseX, mouseY, width, height);
        }
    }

    private static final class NameScreen extends Child {
        private final boolean folder;
        private final String targetParent;
        private String name;
        private Button save;

        private NameScreen(YamatoTravelScreen parent, boolean folder, String targetParent) {
            super(parent, parent, tr(folder ? "new_folder" : "save_current"));
            this.folder = folder;
            this.targetParent = targetParent;
            name = tr(folder ? "default_folder" : "default_place", parent.entries.size() + 1).getString();
        }

        @Override
        protected void init() {
            sizePanel(360, 170);
            EditBox input = addRenderableWidget(new EditBox(font, panelX + 12, panelY + 56, panelWidth - 24, 20, tr("name")));
            input.setMaxLength(64);
            input.setValue(name);
            input.setResponder(value -> { name = value; updateSave(); });
            save = addRenderableWidget(Button.builder(tr("save"), b -> save())
                    .bounds(panelX + 12, panelY + panelHeight - 32, (panelWidth - 28) / 2, 20).build());
            addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                    .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 32, (panelWidth - 28) / 2, 20).build());
            updateSave();
            setInitialFocus(input);
        }

        private void updateSave() {
            if (save != null) save.active = !name.isBlank();
        }

        private void save() {
            if (name.isBlank()) return;
            CompoundTag data = new CompoundTag();
            data.putString("name", name.trim());
            data.putString("parent", targetParent);
            request(folder ? "folder" : "save", data);
            onClose();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                save();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, tr("name"), panelX + 12, panelY + 42, MUTED, false);
            graphics.drawString(font, font.plainSubstrByWidth(tr("save_in_folder", parent.path(targetParent)).getString(), panelWidth - 24),
                    panelX + 12, panelY + 88, MUTED, false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class EditScreen extends Child {
        private final UUID entryId;
        private final boolean folder;
        private String name;
        private String targetParent;
        private Button save;
        private Button folderButton;
        private Button up;
        private Button down;

        private EditScreen(YamatoTravelScreen parent, Entry entry) {
            super(parent, parent, tr("edit_title"));
            entryId = entry.id;
            folder = entry.folder;
            name = entry.name;
            targetParent = entry.parent;
        }

        @Override
        protected void init() {
            sizePanel(400, 238);
            EditBox input = addRenderableWidget(new EditBox(font, panelX + 12, panelY + 44, panelWidth - 24, 20, tr("name")));
            input.setMaxLength(64);
            input.setValue(name);
            input.setResponder(value -> { name = value; updateButtons(); });
            folderButton = addRenderableWidget(Button.builder(Component.literal(parent.path(targetParent)), b ->
                            parent.show(new FolderScreen(parent, this, entryId, targetParent, selected -> {
                                targetParent = selected;
                                parent.show(this);
                            })))
                    .bounds(panelX + 12, panelY + 82, panelWidth - 24, 20).tooltip(Tooltip.create(tr("move_hint"))).build());
            int half = (panelWidth - 28) / 2;
            up = addRenderableWidget(Button.builder(tr("move_up"), b -> reorder(-1)).bounds(panelX + 12, panelY + 121, half, 20)
                    .tooltip(Tooltip.create(tr("manual_hint"))).build());
            down = addRenderableWidget(Button.builder(tr("move_down"), b -> reorder(1)).bounds(panelX + panelWidth / 2 + 2, panelY + 121, half, 20)
                    .tooltip(Tooltip.create(tr("manual_hint"))).build());
            addRenderableWidget(Button.builder(tr("delete"), b -> {
                Entry entry = parent.find(entryId.toString());
                if (entry != null) parent.show(new DeleteScreen(parent, this, entry));
            }).bounds(panelX + 12, panelY + panelHeight - 56, panelWidth - 24, 20).build());
            save = addRenderableWidget(Button.builder(tr("save_changes"), b -> save())
                    .bounds(panelX + 12, panelY + panelHeight - 28, half, 20).build());
            addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                    .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 28, half, 20).build());
            updateButtons();
            setInitialFocus(input);
        }

        private void updateButtons() {
            Entry entry = parent.find(entryId.toString());
            if (save != null) save.active = !name.isBlank() && entry != null;
            if (folderButton != null) folderButton.setMessage(Component.literal(parent.path(targetParent)));
            if (up != null) {
                List<Entry> siblings = entry == null ? List.of() : parent.entries.stream().filter(e -> e.parent.equals(entry.parent)).toList();
                int index = siblings.indexOf(entry);
                boolean manual = parent.sort == 0 && entry != null && targetParent.equals(entry.parent);
                up.active = manual && index > 0;
                down.active = manual && index >= 0 && index + 1 < siblings.size();
            }
        }

        private void reorder(int delta) {
            CompoundTag data = identifier(entryId);
            data.putInt("delta", delta);
            request("reorder", data);
        }

        private void save() {
            Entry entry = parent.find(entryId.toString());
            if (entry == null || name.isBlank()) return;
            if (!entry.name.equals(name.trim())) {
                CompoundTag data = identifier(entryId);
                data.putString("name", name.trim());
                request("rename", data);
            }
            if (!entry.parent.equals(targetParent)) {
                CompoundTag data = identifier(entryId);
                data.putString("parent", targetParent);
                request("move", data);
            }
            onClose();
        }

        @Override
        public void tick() {
            super.tick();
            updateButtons();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, tr("name"), panelX + 12, panelY + 31, MUTED, false);
            graphics.drawString(font, tr("folder_location"), panelX + 12, panelY + 70, MUTED, false);
            graphics.drawString(font, tr("manual_order"), panelX + 12, panelY + 109, MUTED, false);
            if (panelHeight >= 218) graphics.drawString(font, font.plainSubstrByWidth(tr("manual_hint").getString(), panelWidth - 24), panelX + 12, panelY + 147, MUTED, false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class FolderScreen extends Child {
        private final Consumer<String> choose;
        private final Set<String> forbidden = new HashSet<>();
        private String folder;
        private int page;
        private int rows;

        private FolderScreen(YamatoTravelScreen parent, Screen backScreen, UUID movingId, String folder, Consumer<String> choose) {
            super(parent, backScreen, tr("choose_folder"));
            this.choose = choose;
            this.folder = folder;
            forbidden.add(movingId.toString());
            boolean changed;
            do {
                changed = false;
                for (Entry entry : parent.entries) if (forbidden.contains(entry.parent)) changed |= forbidden.add(entry.id.toString());
            } while (changed);
        }

        private List<Entry> folders() {
            return parent.entries.stream().filter(entry -> entry.folder && entry.parent.equals(folder) && !forbidden.contains(entry.id.toString()))
                    .sorted(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER)).toList();
        }

        @Override
        protected void init() {
            sizePanel(420, 340);
            rows = Math.max(1, (panelHeight - 132) / 24);
            List<Entry> folders = folders();
            int pages = Math.max(1, (folders.size() + rows - 1) / rows);
            page = Math.min(page, pages - 1);
            Button up = addRenderableWidget(Button.builder(Component.literal("‹"), b -> {
                Entry current = parent.find(folder);
                folder = current == null ? "" : current.parent;
                page = 0;
                rebuildWidgets();
            }).bounds(panelX + 12, panelY + 32, 24, 20).tooltip(Tooltip.create(tr("parent_folder"))).build());
            up.active = !folder.isEmpty();
            addRenderableWidget(Button.builder(Component.literal(parent.path(folder)), b -> { folder = ""; page = 0; rebuildWidgets(); })
                    .bounds(panelX + 40, panelY + 32, panelWidth - 52, 20).tooltip(Tooltip.create(tr("root_folder_hint", parent.path(folder)))).build());
            for (int row = 0; row < rows && page * rows + row < folders.size(); row++) {
                Entry entry = folders.get(page * rows + row);
                addRenderableWidget(Button.builder(Component.literal(entry.name), b -> { folder = entry.id.toString(); page = 0; rebuildWidgets(); })
                        .bounds(panelX + 12, panelY + 60 + row * 24, panelWidth - 24, 20).build());
            }
            Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> { page--; rebuildWidgets(); })
                    .bounds(panelX + panelWidth - 76, panelY + panelHeight - 62, 28, 20).build());
            previous.active = page > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> { page++; rebuildWidgets(); })
                    .bounds(panelX + panelWidth - 40, panelY + panelHeight - 62, 28, 20).build());
            next.active = page + 1 < pages;
            addRenderableWidget(Button.builder(tr("choose_this_folder"), b -> choose.accept(folder))
                    .bounds(panelX + 12, panelY + panelHeight - 32, (panelWidth - 28) / 2, 20).build());
            addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                    .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 32, (panelWidth - 28) / 2, 20).build());
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            int pages = Math.max(1, (folders().size() + rows - 1) / rows);
            page = Math.max(0, Math.min(pages - 1, page + (vertical > 0 ? -1 : 1)));
            rebuildWidgets();
            return true;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            if (folders().isEmpty()) parent.drawWrapped(graphics, tr("empty_folders"), panelX + 12, panelY + 68, panelWidth - 24, MUTED);
            graphics.drawString(font, tr("page", page + 1, Math.max(1, (folders().size() + rows - 1) / rows)), panelX + 12, panelY + panelHeight - 55, MUTED, false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class DeleteScreen extends Child {
        private final Entry entry;

        private DeleteScreen(YamatoTravelScreen parent, Screen backScreen, Entry entry) {
            super(parent, backScreen, tr("delete_title"));
            this.entry = entry;
        }

        @Override
        protected void init() {
            sizePanel(420, 202);
            int half = (panelWidth - 28) / 2;
            addRenderableWidget(Button.builder(tr("confirm_delete"), b -> {
                request("delete", identifier(entry.id));
                parent.show(parent);
            }).bounds(panelX + 12, panelY + panelHeight - 32, half, 20).build());
            addRenderableWidget(Button.builder(tr("cancel"), b -> onClose())
                    .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 32, half, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            parent.drawWrapped(graphics, tr("delete_prompt", entry.name), panelX + 12, panelY + 44, panelWidth - 24, TEXT);
            if (entry.folder) parent.drawWrapped(graphics, tr("delete_folder_warning"), panelX + 12, panelY + 94, panelWidth - 24, 0xFFFFAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class RiftDetailsScreen extends Child {
        private final UUID riftId;
        private final Rift original;

        private RiftDetailsScreen(YamatoTravelScreen parent, Rift rift) {
            super(parent, parent, tr("rift_details"));
            riftId = rift.id;
            original = rift;
        }

        @Override
        protected void init() {
            sizePanel(440, 224);
            int half = (panelWidth - 28) / 2;
            addRenderableWidget(Button.builder(tr("close_rift"), b -> { request("close", identifier(riftId)); onClose(); })
                    .bounds(panelX + 12, panelY + panelHeight - 32, half, 20).build());
            addRenderableWidget(Button.builder(tr("done"), b -> onClose())
                    .bounds(panelX + panelWidth / 2 + 2, panelY + panelHeight - 32, half, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            background(graphics, mouseX, mouseY, partialTick);
            Rift rift = parent.rifts.stream().filter(current -> current.id.equals(riftId)).findFirst().orElse(original);
            graphics.drawString(font, font.plainSubstrByWidth(rift.name, panelWidth - 24), panelX + 12, panelY + 35, TEXT, false);
            graphics.drawString(font, tr("source"), panelX + 12, panelY + 57, ACCENT, false);
            graphics.drawString(font, font.plainSubstrByWidth(rift.sourceDimension, panelWidth - 24), panelX + 12, panelY + 71, TEXT, false);
            graphics.drawString(font, coordinates(rift.sourceX, rift.sourceY, rift.sourceZ), panelX + 12, panelY + 84, MUTED, false);
            graphics.drawString(font, tr("target"), panelX + 12, panelY + 107, ACCENT, false);
            graphics.drawString(font, font.plainSubstrByWidth(rift.targetDimension, panelWidth - 24), panelX + 12, panelY + 121, TEXT, false);
            graphics.drawString(font, coordinates(rift.targetX, rift.targetY, rift.targetZ), panelX + 12, panelY + 134, MUTED, false);
            graphics.drawString(font, parent.remaining(rift), panelX + 12, panelY + 159, TEXT, false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
