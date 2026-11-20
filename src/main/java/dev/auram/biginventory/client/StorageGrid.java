package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.client.StorageSwapAnimation.Point;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StorageGrid {
    static final int CELL = 17;
    static final int COMPACT_CELL = 14;
    static final int SEARCH_HEIGHT = StorageSearchBox.HEIGHT;
    static final int HOTBAR_WIDTH = Inventory.getSelectionSize() * 18;
    private static final int RAIL_GAP = 2;
    private static final int MIN_RAIL_WIDTH = 6;
    private static final float COUNT_SCALE = 0.625f;
    private final ClientStorage.View view;
    private final Font font;
    private final StorageSearchBox search;
    private final int cellSize;
    private final int columns;
    private final int railWidth;
    private final boolean vanillaSlots;
    private List<Entry> filtered = List.of();
    private String query = "";
    private int displayedChange = -1;
    private int x;
    private int y;
    private int rows;
    private int rowOffset;
    private boolean dragging;
    private int thumbGrab;
    private StorageSwapAnimation animation;

    StorageGrid(ClientStorage.View view, Font font, String priorSearch) {
        this(view, font, priorSearch, CELL);
    }

    StorageGrid(ClientStorage.View view, Font font, String priorSearch, int cellSize) {
        this(view, font, priorSearch, cellSize, false);
    }

    StorageGrid(ClientStorage.View view, Font font, String priorSearch, int cellSize, boolean vanillaSlots) {
        this.view = view;
        this.font = font;
        this.cellSize = cellSize;
        this.vanillaSlots = vanillaSlots;
        columns = (HOTBAR_WIDTH - RAIL_GAP - MIN_RAIL_WIDTH) / cellSize;
        railWidth = HOTBAR_WIDTH - columns * cellSize - RAIL_GAP;
        search = new StorageSearchBox(font);
        search.setWidth(HOTBAR_WIDTH);
        search.setMaxLength(96);
        search.setValue(priorSearch);
        search.setResponder(text -> {
            cancelAnimation();
            query = text.toLowerCase(Locale.ROOT).strip();
            displayedChange = -1;
            rowOffset = 0;
        });
        query = priorSearch.toLowerCase(Locale.ROOT).strip();
        view.grid = this;
    }

    StorageSearchBox search() { return search; }

    void bounds(int x, int y, int rows) {
        if (this.x != x || this.y != y || this.rows != rows) cancelAnimation();
        this.x = x;
        this.y = y;
        this.rows = rows;
        search.setPosition(x, y - SEARCH_HEIGHT - 4);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        int hovered = indexUnderMouse(mouseX, mouseY);
        int iconInset = vanillaSlots ? 1 : 0;
        StorageSwapAnimation swap = animation();
        for (int cell = 0; cell < columns * rows; cell++) {
            int index = rowOffset * columns + cell;
            int px = x + cell % columns * cellSize;
            int py = y + cell / columns * cellSize;
            InventorySkin.storageSlot(graphics, px, py, cellSize, vanillaSlots, index == hovered);
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index);
            if (swap != null && swap.hides(entry)) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(px + iconInset, py + iconInset, 0);
            graphics.pose().scale(iconScale(), iconScale(), 1);
            graphics.renderItem(entry.icon(), 0, 0);
            graphics.renderItemDecorations(font, entry.icon(), 0, 0, "");
            graphics.pose().popPose();
            if (entry.count() > 1) renderCount(graphics, entry.count(), px, py);
        }
        int railX = railX();
        int length = gridHeight();
        InventorySkin.well(graphics, railX, y, railWidth, length, InventorySkin.Tone.RAIL);
        int thumb = thumbHeight();
        int offset = maxRow() == 0 ? 0 : (length - thumb) * rowOffset / maxRow();
        if (maxRow() > 0) {
            InventorySkin.fill(graphics, railX + 1, y + offset, railWidth - 2, thumb, InventorySkin.Tone.THUMB);
        }
    }

    void tooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!view.renderedCarried().isEmpty()) return;
        int index = indexUnderMouse(mouseX, mouseY);
        if (index < 0 || index >= filtered.size()) return;
        Entry entry = filtered.get(index);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.icon()));
        lines.add(Component.translatable("biginventory.stored", entry.count()).withStyle(ChatFormatting.GRAY));
        graphics.renderTooltip(font, lines, entry.icon().getTooltipImage(), entry.icon(), mouseX, mouseY);
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (!inside(mouseX, mouseY) || button > 1) return false;
        refresh();
        if (mouseX >= railX()) {
            if (button == 0 && maxRow() > 0) {
                int length = gridHeight();
                int height = thumbHeight();
                int top = y + (length - height) * rowOffset / maxRow();
                thumbGrab = mouseY >= top && mouseY < top + height ? (int) mouseY - top : height / 2;
                dragging = true;
                drag(mouseY);
            }
        } else if (!view.menu.getCarried().isEmpty()) {
            view.act(-1, button == 0 ? Gesture.DEPOSIT : Gesture.DEPOSIT_ONE);
        } else {
            int index = indexUnderMouse(mouseX, mouseY);
            if (index >= 0 && index < filtered.size()) {
                view.act(filtered.get(index).id(), Screen.hasShiftDown() ? Gesture.SHIFT_TAKE
                        : button == 0 ? Gesture.TAKE : Gesture.TAKE_HALF);
            }
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY) || delta == 0) return false;
        cancelAnimation();
        rowOffset = Mth.clamp(rowOffset + (delta < 0 ? 1 : -1), 0, maxRow());
        return true;
    }

    boolean drag(double mouseY) {
        if (!dragging) return false;
        cancelAnimation();
        int travel = gridHeight() - thumbHeight();
        rowOffset = travel <= 0 ? 0 : Mth.clamp((int) Math.round((mouseY - y - thumbGrab) * maxRow() / travel), 0, maxRow());
        return true;
    }

    boolean release() {
        boolean handled = dragging;
        dragging = false;
        return handled;
    }

    private void refresh() {
        if (displayedChange != view.changes) {
            displayedChange = view.changes;
            filtered = filter(view.displayedEntries());
        }
        rowOffset = Mth.clamp(rowOffset, 0, maxRow());
    }

    private List<Entry> filter(List<Entry> entries) {
        if (query.isEmpty()) return entries;
        return entries.stream().filter(entry -> entry.icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(ForgeRegistries.ITEMS.getKey(entry.icon().getItem())).contains(query)).toList();
    }

    void startAnimation(AbstractContainerScreen<?> screen, StoragePrediction preview) {
        cancelAnimation();
        var transfer = preview.transfer();
        if (transfer == null || rows <= 0) return;
        var timing = SmoothSwappingCompat.timing();
        if (timing == null) return;
        Point storage = position(transfer.entry(), transfer.delta() > 0 ? preview.entries() : view.entries);
        if (storage != null) animation = StorageSwapAnimation.start(screen, view.menu, preview, storage, timing);
    }

    void retargetDeposit(Entry entry, List<Entry> entries) {
        Point destination = position(entry, entries);
        if (destination == null) cancelAnimation();
        else animation = animation.retargetDeposit(destination);
    }

    @Nullable
    private Point position(Entry entry, List<Entry> entries) {
        List<Entry> visible = filter(entries);
        int first = Math.min(rowOffset, maxRow(visible.size())) * columns;
        int cell = visible.indexOf(entry) - first;
        if (cell < 0 || cell >= columns * rows) return null;
        float scale = iconScale();
        float center = (vanillaSlots ? 1 : 0) + 8 * scale;
        return new Point(x + cell % columns * cellSize + center,
                y + cell / columns * cellSize + center, scale);
    }

    @Nullable StorageSwapAnimation animation() {
        if (animation != null && !animation.active()) cancelAnimation();
        return animation;
    }

    void cancelAnimation() {
        animation = null;
    }

    private float iconScale() {
        return cellSize == COMPACT_CELL ? 0.75f : 1.0f;
    }

    private int indexUnderMouse(double mx, double my) {
        if (mx < x || my < y || mx >= x + columns * cellSize || my >= y + rows * cellSize) return -1;
        return (rowOffset + (int) (my - y) / cellSize) * columns + (int) (mx - x) / cellSize;
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + HOTBAR_WIDTH && my >= y && my < y + rows * cellSize;
    }

    private int railX() { return x + columns * cellSize + RAIL_GAP; }

    private int gridHeight() { return rows * cellSize - (vanillaSlots ? 0 : 1); }

    private int rowCount(int entryCount) { return (entryCount + columns - 1) / columns; }

    private int maxRow() { return maxRow(filtered.size()); }

    private int maxRow(int entryCount) { return Math.max(0, rowCount(entryCount) - rows); }

    private int thumbHeight() {
        int length = gridHeight();
        return Math.max(7, length * rows / Math.max(rows, rowCount(filtered.size())));
    }

    private void renderCount(GuiGraphics graphics, long count, int x, int y) {
        String label = ItemCount.format(count);
        int width = font.width(label);
        float scale = Math.min(COUNT_SCALE, (cellSize - 2) / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x + cellSize - 2, y + cellSize - 1, 200);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, -width, -font.lineHeight, 0xffffffff, true);
        graphics.pose().popPose();
    }
}
