package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StorageGrid {
    static final int CELL = 17;
    static final int COMPACT_CELL = 14;
    static final int SEARCH_HEIGHT = 16;
    private static final int RAIL_WIDTH = 7;
    private final ClientStorage.View view;
    private final Font font;
    private final EditBox search;
    private final int cellSize;
    private List<Entry> filtered = List.of();
    private String query = "";
    private int displayedChange = -1;
    private int x;
    private int y;
    private int columns;
    private int rows;
    private int rowOffset;
    private boolean dragging;
    private int thumbGrab;

    StorageGrid(ClientStorage.View view, Font font, String priorSearch) {
        this(view, font, priorSearch, CELL);
    }

    StorageGrid(ClientStorage.View view, Font font, String priorSearch, int cellSize) {
        this.view = view;
        this.font = font;
        this.cellSize = cellSize;
        search = new EditBox(font, 0, 0, 125, SEARCH_HEIGHT, Component.translatable("biginventory.search"));
        search.setMaxLength(96);
        search.setValue(priorSearch);
        search.setHint(Component.translatable("biginventory.search").withStyle(ChatFormatting.GRAY));
        search.setResponder(text -> {
            query = text.toLowerCase(Locale.ROOT).strip();
            displayedChange = -1;
            rowOffset = 0;
        });
        query = priorSearch.toLowerCase(Locale.ROOT).strip();
    }

    EditBox search() { return search; }

    void bounds(int x, int y, int columns, int rows) {
        this.x = x;
        this.y = y;
        this.columns = columns;
        this.rows = rows;
        search.setPosition(x, y - SEARCH_HEIGHT - 4);
        search.setWidth(columns * cellSize + RAIL_WIDTH - 2);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        int hovered = indexUnderMouse(mouseX, mouseY);
        for (int cell = 0; cell < columns * rows; cell++) {
            int index = rowOffset * columns + cell;
            int px = x + cell % columns * cellSize;
            int py = y + cell / columns * cellSize;
            graphics.fill(px, py, px + cellSize - 1, py + cellSize - 1,
                    index == hovered ? 0xff506977 : 0xff27353f);
            graphics.fill(px, py, px + cellSize - 1, py + 1, 0xff14222b);
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index);
            float scale = cellSize == COMPACT_CELL ? 0.75f : 1.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(px, py, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(entry.icon(), 0, 0);
            graphics.renderItemDecorations(font, entry.icon(), 0, 0, entry.count() > 1 ? abbreviate(entry.count()) : "");
            graphics.pose().popPose();
        }
        int railX = railX();
        int length = rows * cellSize - 1;
        graphics.fill(railX, y, railX + RAIL_WIDTH, y + length, 0xff1a252c);
        int thumb = thumbHeight();
        int offset = maxRow() == 0 ? 0 : (length - thumb) * rowOffset / maxRow();
        graphics.fill(railX + 1, y + offset, railX + RAIL_WIDTH - 1, y + offset + thumb,
                maxRow() == 0 ? 0xff3b4b52 : 0xff87a7ae);
    }

    void tooltip(GuiGraphics graphics, ItemStack carried, int mouseX, int mouseY) {
        if (!carried.isEmpty()) return;
        int index = indexUnderMouse(mouseX, mouseY);
        if (index < 0 || index >= filtered.size()) return;
        Entry entry = filtered.get(index);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.icon()));
        lines.add(Component.translatable("biginventory.stored", entry.count()).withStyle(ChatFormatting.GRAY));
        graphics.renderTooltip(font, lines, entry.icon().getTooltipImage(), entry.icon(), mouseX, mouseY);
    }

    boolean click(double mouseX, double mouseY, int button, ItemStack carried) {
        if (!inside(mouseX, mouseY) || button > 1) return false;
        refresh();
        if (mouseX >= railX()) {
            if (button == 0 && maxRow() > 0) {
                int length = rows * cellSize - 1;
                int height = thumbHeight();
                int top = y + (length - height) * rowOffset / maxRow();
                thumbGrab = mouseY >= top && mouseY < top + height ? (int) mouseY - top : height / 2;
                dragging = true;
                drag(mouseY);
            }
        } else if (!carried.isEmpty()) {
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
        int travel = rows * cellSize - 1 - thumbHeight();
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
            filtered = view.entries.stream().filter(entry -> query.isEmpty()
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
        return mx >= x && mx < railX() + RAIL_WIDTH && my >= y && my < y + rows * cellSize;
    }

    private int railX() { return x + columns * cellSize + 2; }

    private int totalRows() { return (filtered.size() + columns - 1) / columns; }

    private int maxRow() { return Math.max(0, totalRows() - rows); }

    private int thumbHeight() {
        int length = rows * cellSize - 1;
        return Math.max(7, length * rows / Math.max(rows, totalRows()));
    }

    private static String abbreviate(long count) {
        if (count >= 1_000_000) return count / 1_000_000 + "M";
        if (count >= 1_000) return count / 1_000 + "k";
        return Long.toString(count);
    }
}
