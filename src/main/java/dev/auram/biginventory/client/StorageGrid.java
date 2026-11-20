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
    static final int CELL = 18;
    static final int COLUMNS = Inventory.getSelectionSize();
    static final int WIDTH = COLUMNS * CELL;
    static final int SEARCH_HEIGHT = StorageSearchBox.HEIGHT;
    private static final int RAIL_GAP = 2;
    private static final int RAIL_WIDTH = 4;
    private final ClientStorage.View view;
    private final Font font;
    private final StorageSearchBox search;
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
        this.view = view;
        this.font = font;
        search = new StorageSearchBox(font);
        search.setWidth(WIDTH);
        search.setMaxLength(96);
        search.setValue(priorSearch);
        search.setResponder(text -> {
            cancelAnimation();
            dragging = false;
            query = text.toLowerCase(Locale.ROOT).strip();
            displayedChange = -1;
            rowOffset = 0;
        });
        query = priorSearch.toLowerCase(Locale.ROOT).strip();
        view.grid = this;
    }

    StorageSearchBox search() { return search; }

    void bounds(int x, int y, int rows) {
        if (this.x != x || this.y != y || this.rows != rows) {
            cancelAnimation();
            dragging = false;
        }
        this.x = x;
        this.y = y;
        this.rows = rows;
        search.setPosition(x, y - SEARCH_HEIGHT - 4);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        int hovered = indexUnderMouse(mouseX, mouseY);
        StorageSwapAnimation swap = animation();
        for (int cell = 0; cell < COLUMNS * rows; cell++) {
            int index = rowOffset * COLUMNS + cell;
            int px = x + cell % COLUMNS * CELL;
            int py = y + cell / COLUMNS * CELL;
            InventorySkin.slot(graphics, px, py, index == hovered);
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index);
            if (swap != null && swap.hides(entry)) continue;
            graphics.renderItem(entry.icon(), px + 1, py + 1);
            graphics.renderItemDecorations(font, entry.icon(), px + 1, py + 1, "");
            if (entry.count() > 1) renderCount(graphics, entry.count(), px, py);
        }
        if (maxRow() > 0) {
            int thumb = thumbHeight();
            InventorySkin.scrollbar(graphics, railX(), y, RAIL_WIDTH, gridHeight(), thumbTop(thumb), thumb,
                    dragging || overRail(mouseX, mouseY));
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
        if (button > 1) return false;
        refresh();
        if (overRail(mouseX, mouseY)) {
            if (button != 0) return false;
            int thumb = thumbHeight();
            int top = thumbTop(thumb);
            thumbGrab = mouseY >= top && mouseY < top + thumb ? (int) mouseY - top : thumb / 2;
            dragging = true;
            return drag(mouseY);
        }
        int index = indexUnderMouse(mouseX, mouseY);
        if (index < 0) return false;
        if (!view.menu.getCarried().isEmpty()) {
            view.act(-1, button == 0 ? Gesture.DEPOSIT : Gesture.DEPOSIT_ONE);
        } else if (index < filtered.size()) {
            view.act(filtered.get(index).id(), Screen.hasShiftDown() ? Gesture.SHIFT_TAKE
                    : button == 0 ? Gesture.TAKE : Gesture.TAKE_HALF);
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double delta) {
        refresh();
        if (delta == 0 || maxRow() == 0 || !(inside(mouseX, mouseY) || overRail(mouseX, mouseY))) return false;
        cancelAnimation();
        rowOffset = Mth.clamp(rowOffset + (delta < 0 ? 1 : -1), 0, maxRow());
        return true;
    }

    boolean drag(double mouseY) {
        refresh();
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
        int last = maxRow();
        rowOffset = Mth.clamp(rowOffset, 0, last);
        if (last == 0) dragging = false;
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
        int first = Math.min(rowOffset, maxRow(visible.size())) * COLUMNS;
        int cell = visible.indexOf(entry) - first;
        if (cell < 0 || cell >= COLUMNS * rows) return null;
        return new Point(x + cell % COLUMNS * CELL + CELL / 2,
                y + cell / COLUMNS * CELL + CELL / 2);
    }

    @Nullable StorageSwapAnimation animation() {
        if (animation != null && !animation.active()) cancelAnimation();
        return animation;
    }

    void cancelAnimation() {
        animation = null;
    }

    private int indexUnderMouse(double mx, double my) {
        if (!inside(mx, my)) return -1;
        return (rowOffset + (int) (my - y) / CELL) * COLUMNS + (int) (mx - x) / CELL;
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + gridHeight();
    }

    private int railX() { return x + WIDTH + RAIL_GAP; }

    private boolean overRail(double mx, double my) {
        return maxRow() > 0 && mx >= railX() && mx < railX() + RAIL_WIDTH
                && my >= y && my < y + gridHeight();
    }

    private int gridHeight() { return rows * CELL; }

    private int rowCount(int entryCount) { return (entryCount + COLUMNS - 1) / COLUMNS; }

    private int maxRow() { return maxRow(filtered.size()); }

    private int maxRow(int entryCount) { return Math.max(0, rowCount(entryCount) - rows); }

    private int thumbHeight() {
        int length = gridHeight();
        return Math.max(7, length * rows / Math.max(rows, rowCount(filtered.size())));
    }

    private int thumbTop(int thumb) {
        return y + (gridHeight() - thumb) * rowOffset / maxRow();
    }

    private void renderCount(GuiGraphics graphics, long count, int x, int y) {
        String label = ItemCount.format(count);
        int width = font.width(label);
        float scale = Math.min(1.0f, (CELL - 2) / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(x + CELL - 1, y + CELL - 1, 200);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, label, -width, -font.lineHeight, 0xffffffff, true);
        graphics.pose().popPose();
    }
}
