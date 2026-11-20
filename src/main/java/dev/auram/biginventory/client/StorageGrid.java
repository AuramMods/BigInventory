package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;

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
            query = text.toLowerCase(Locale.ROOT).strip();
            displayedChange = -1;
            rowOffset = 0;
        });
        query = priorSearch.toLowerCase(Locale.ROOT).strip();
    }

    StorageSearchBox search() { return search; }

    void bounds(int x, int y, int rows) {
        this.x = x;
        this.y = y;
        this.rows = rows;
        search.setPosition(x, y - SEARCH_HEIGHT - 4);
    }

    void searchBelowGrid() {
        search.setY(y + rows * cellSize + 3);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        int hovered = indexUnderMouse(mouseX, mouseY);
        int iconInset = vanillaSlots ? 1 : 0;
        for (int cell = 0; cell < columns * rows; cell++) {
            int index = rowOffset * columns + cell;
            int px = x + cell % columns * cellSize;
            int py = y + cell / columns * cellSize;
            if (vanillaSlots) {
                InventorySkin.vanillaSlot(graphics, px, py, cellSize, index == hovered);
            } else {
                InventorySkin.well(graphics, px, py, cellSize - 1, cellSize - 1,
                        index == hovered ? 0xffb1b1b1 : 0xff929292);
            }
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index);
            float scale = cellSize == COMPACT_CELL ? 0.75f : 1.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(px + iconInset, py + iconInset, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(entry.icon(), 0, 0);
            graphics.renderItemDecorations(font, entry.icon(), 0, 0, "");
            graphics.pose().popPose();
            if (entry.count() > 1) renderCount(graphics, entry.count(), px, py);
        }
        int railX = railX();
        int length = gridHeight();
        InventorySkin.well(graphics, railX, y, railWidth, length, 0xff858585);
        int thumb = thumbHeight();
        int offset = maxRow() == 0 ? 0 : (length - thumb) * rowOffset / maxRow();
        if (maxRow() > 0) {
            graphics.fill(railX + 1, y + offset, railX + railWidth - 1, y + offset + thumb, 0xffdcdcdc);
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
        rowOffset = Mth.clamp(rowOffset + (delta < 0 ? 1 : -1), 0, maxRow());
        return true;
    }

    boolean drag(double mouseY) {
        if (!dragging) return false;
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
            filtered = view.displayedEntries().stream().filter(entry -> query.isEmpty()
                    || entry.icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    || String.valueOf(ForgeRegistries.ITEMS.getKey(entry.icon().getItem())).contains(query)).toList();
        }
        rowOffset = Mth.clamp(rowOffset, 0, maxRow());
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

    private int totalRows() { return (filtered.size() + columns - 1) / columns; }

    private int maxRow() { return Math.max(0, totalRows() - rows); }

    private int thumbHeight() {
        int length = gridHeight();
        return Math.max(7, length * rows / Math.max(rows, totalRows()));
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
