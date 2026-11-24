package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.SlotBinding;
import dev.auram.biginventory.mixin.SlotPosition;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.List;

import static dev.auram.biginventory.client.StorageGrid.CELL;
import static dev.auram.biginventory.client.StorageGrid.COLUMNS;
import static dev.auram.biginventory.client.StorageGrid.SEARCH_GAP;
import static dev.auram.biginventory.client.StorageGrid.SEARCH_HEIGHT;
import static dev.auram.biginventory.client.StorageGrid.WIDTH;

/** Draws the storage panel around native menu slots. It owns no item interactions. */
final class RealStorageGrid implements BackpackGrid {
    private static final int RAIL_WIDTH = 4;
    private static final int RAIL_GAP = 2;
    private static final int TICK_INTERVAL_MS = 50;
    private static final int SETTLE_AFTER_CLICK_MS = 300;
    private static final int SORT_WIDTH = 27;
    private static final int CONTROL_GAP = 2;
    private final ClientViewport.View view;
    private final StorageSearchBox search;
    private final Button sort;
    private StorageSearchQuery query;
    private List<Integer> matches = List.of();
    private int contentVersion = -1;
    private int x;
    private int y;
    private int rowLimit;
    private int rows = 1;
    private int drawnRows = 1;
    private int totalRows;
    private double target;
    private long lastTick;
    private boolean dragging;
    private boolean explicitChange = true;
    private int thumbGrab;

    RealStorageGrid(ClientViewport.View view, Font font, String priorSearch) {
        this.view = view;
        search = new StorageSearchBox(font);
        search.setWidth(WIDTH - SORT_WIDTH - CONTROL_GAP);
        search.setMaxLength(96);
        search.setValue(priorSearch);
        query = StorageSearchQuery.parse(priorSearch);
        search.setResponder(text -> {
            query = StorageSearchQuery.parse(text);
            target = 0;
            contentVersion = -1;
            explicitChange = true;
        });
        sort = Button.builder(Component.translatable("biginventory.sort"), button -> view.sort())
                .size(SORT_WIDTH, SEARCH_HEIGHT).build();
    }

    @Override
    public StorageSearchBox search() {
        return search;
    }

    @Override
    public List<AbstractWidget> widgets() {
        return List.of(search, sort);
    }

    @Override
    public void close() {
        ClientViewport.close(view);
    }

    int shownRows() {
        return drawnRows;
    }

    void bounds(int x, int y, int left, int top, int limit, boolean visible) {
        bounds(x, y, left, top, limit, visible, SEARCH_GAP);
    }

    @Override
    public void bounds(int x, int y, int left, int top, int limit, boolean visible, int gap) {
        this.x = x;
        this.y = y;
        rowLimit = Math.min(limit, view.slots.cells().size() / COLUMNS);
        search.setPosition(x, y - SEARCH_HEIGHT - gap);
        search.visible = visible;
        sort.setPosition(x + WIDTH - SORT_WIDTH, search.getY());
        sort.visible = visible;
        sort.active = view.interactive() && view.menu.getCarried().isEmpty();
        if (visible) {
            refresh();
            if (!mouseHeld() && (explicitChange || Util.getMillis() - view.lastClick >= SETTLE_AFTER_CLICK_MS)) {
                drawnRows = rows;
                view.request(mapping());
                explicitChange = false;
            }
        }
        for (int i = 0; i < view.slots.cells().size(); i++) {
            SlotPosition position = (SlotPosition) view.slots.cells().get(i);
            position.bigInventory$setX(visible && i < drawnRows * COLUMNS ? x - left + 1 + i % COLUMNS * CELL : -30000);
            position.bigInventory$setY(y - top + 1 + i / COLUMNS * CELL);
        }
    }

    private void refresh() {
        if (contentVersion != view.contentVersion) {
            contentVersion = view.contentVersion;
            if (!query.isEmpty()) {
                matches = view.contents.entrySet().stream().filter(entry -> query.matches(entry.getValue()))
                        .map(java.util.Map.Entry::getKey).toList();
            }
        }
        int count = query.isEmpty() ? unfilteredCells() : matches.size() + 1;
        totalRows = Math.max(1, (count + COLUMNS - 1) / COLUMNS);
        rows = Math.min(totalRows, rowLimit);
        target = Mth.clamp(target, 0, maxRow());
        if (maxRow() == 0) dragging = false;
    }

    private int unfilteredCells() {
        if (view.contents.isEmpty()) return 1;
        int last = Backpack.positionOf(view.contents.lastKey());
        int rowStart = last / COLUMNS * COLUMNS;
        for (int i = rowStart; i < rowStart + COLUMNS; i++) {
            if (!view.contents.containsKey(Backpack.slotAt(i))) return last + 1;
        }
        return last + 2;
    }

    private int[] mapping() {
        int[] indices = new int[view.slots.cells().size()];
        Arrays.fill(indices, SlotBinding.INACTIVE);
        int start = (int) Math.round(target) * COLUMNS;
        int vacancy = 0;
        if (!query.isEmpty()) {
            while (view.contents.containsKey(Backpack.slotAt(vacancy))) vacancy++;
        }
        for (int i = 0; i < rows * COLUMNS; i++) {
            int position = start + i;
            if (query.isEmpty()) indices[i] = Backpack.slotAt(position);
            else if (position < matches.size()) indices[i] = matches.get(position);
            else if (position == matches.size()) indices[i] = Backpack.slotAt(vacancy);
        }
        return indices;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < drawnRows * COLUMNS; i++) {
            InventorySkin.slot(graphics, x + i % COLUMNS * CELL, y + i / COLUMNS * CELL);
        }
        if (maxRow() > 0) {
            int thumb = thumbHeight();
            InventorySkin.scrollbar(graphics, railX(), y, RAIL_WIDTH, gridHeight(), thumbTop(thumb), thumb,
                    dragging || overRail(mouseX, mouseY));
        }
        view.present();
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overRail(mouseX, mouseY)) {
            int thumb = thumbHeight();
            int top = thumbTop(thumb);
            thumbGrab = mouseY >= top && mouseY < top + thumb ? (int) mouseY - top : thumb / 2;
            dragging = true;
            drag(mouseY);
            return true;
        }
        return !view.interactive() && inside(mouseX, mouseY);
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double delta) {
        if (delta == 0 || maxRow() == 0 || !(inside(mouseX, mouseY) || overRail(mouseX, mouseY))) return false;
        if (!dragging) changeTarget(Mth.clamp(target - delta, 0, maxRow()));
        return true;
    }

    @Override
    public boolean drag(double mouseY) {
        if (!dragging) return false;
        int travel = gridHeight() - thumbHeight();
        changeTarget(travel <= 0 ? 0 : Mth.clamp((mouseY - y - thumbGrab) * maxRow() / travel, 0, maxRow()));
        return true;
    }

    @Override
    public boolean release() {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    private void changeTarget(double next) {
        long now = Util.getMillis();
        if (Math.round(next) != Math.round(target)) {
            explicitChange = true;
            if (now - lastTick >= TICK_INTERVAL_MS) {
                lastTick = now;
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f, 0.2f));
            }
        }
        target = next;
    }

    private boolean mouseHeld() {
        if (dragging) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + gridHeight();
    }

    private boolean overRail(double mx, double my) {
        return maxRow() > 0 && mx >= railX() && mx < railX() + RAIL_WIDTH
                && my >= y && my < y + gridHeight();
    }

    private int railX() { return x + WIDTH + RAIL_GAP; }
    private int gridHeight() { return drawnRows * CELL; }
    private int maxRow() { return Math.max(0, totalRows - rows); }
    private int thumbHeight() { return Math.max(7, gridHeight() * rows / totalRows); }
    private int thumbTop(int thumb) { return y + (int) Math.round((gridHeight() - thumb) * target / maxRow()); }
}
