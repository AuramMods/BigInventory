package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.SlotBinding;
import dev.auram.biginventory.mixin.SlotPosition;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
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
    private static final int SCROLL_ANIMATION_MS = 125;
    private final ClientViewport.View view;
    private final Font font;
    private final StorageSearchBox search;
    private StorageSearchQuery query;
    private List<Integer> matches = List.of();
    private int contentVersion = -1;
    private int x;
    private int y;
    private int rows = 1;
    private int totalRows;
    private double target;
    private long lastTick;
    private boolean dragging;
    private boolean explicitChange = true;
    private boolean scrollChange;
    private int thumbGrab;
    private Slide pendingSlide;
    private Slide activeSlide;
    private long slideStart;

    RealStorageGrid(ClientViewport.View view, Font font, String priorSearch) {
        this.view = view;
        this.font = font;
        search = new StorageSearchBox(font);
        search.setWidth(WIDTH);
        search.setMaxLength(96);
        search.setValue(priorSearch);
        query = StorageSearchQuery.parse(priorSearch);
        search.setResponder(text -> {
            query = StorageSearchQuery.parse(text);
            target = 0;
            contentVersion = -1;
            explicitChange = true;
            scrollChange = false;
        });
    }

    @Override
    public StorageSearchBox search() {
        return search;
    }

    @Override
    public void close() {
        ClientViewport.close(view);
    }

    void bounds(int x, int y, int left, int top, int limit, boolean visible) {
        bounds(x, y, left, top, limit, visible, SEARCH_GAP);
    }

    @Override
    public void bounds(int x, int y, int left, int top, int limit, boolean visible, int gap) {
        this.x = x;
        this.y = y;
        rows = Math.min(limit, view.slots.cells().size() / COLUMNS);
        search.setPosition(x, y - SEARCH_HEIGHT - gap);
        search.visible = visible;
        if (visible) {
            refresh();
            long now = Util.getMillis();
            updateSlide(now);
            if (activeSlide == null && !mouseHeld()
                    && (explicitChange || now - view.lastClick >= SETTLE_AFTER_CLICK_MS)) {
                int[] next = mapping();
                Slide outgoing = scrollChange && !dragging ? departingRow(next) : null;
                if (view.request(next)) pendingSlide = outgoing;
                explicitChange = false;
                scrollChange = false;
            }
        }
        for (int i = 0; i < view.slots.cells().size(); i++) {
            SlotPosition position = (SlotPosition) view.slots.cells().get(i);
            position.bigInventory$setX(visible && activeSlide == null && i < rows * COLUMNS
                    ? x - left + 1 + i % COLUMNS * CELL : -30000);
            position.bigInventory$setY(y - top + 1 + i / COLUMNS * CELL);
        }
    }

    private Slide departingRow(int[] next) {
        if (!view.interactive() || !query.isEmpty() || view.mapping[0] < 0) return null;
        int difference = Backpack.positionOf(next[0]) - Backpack.positionOf(view.mapping[0]);
        if (Math.abs(difference) != COLUMNS) return null;
        int direction = difference / COLUMNS;
        int first = direction > 0 ? 0 : (rows - 1) * COLUMNS;
        if (view.mapping[first] < 0) return null;
        ItemStack[] items = new ItemStack[COLUMNS];
        for (int i = 0; i < items.length; i++) items[i] = view.slots.cells().get(first + i).getItem().copy();
        return new Slide(next[0], direction, items);
    }

    private void updateSlide(long now) {
        if (pendingSlide != null && view.sequence == view.applied) {
            if (view.mapping.length != 0 && view.mapping[0] == pendingSlide.destination()) {
                activeSlide = pendingSlide;
                slideStart = now;
            }
            pendingSlide = null;
        }
        if (activeSlide != null && now - slideStart >= SCROLL_ANIMATION_MS) activeSlide = null;
    }

    private record Slide(int destination, int direction, ItemStack[] departing) {}

    private void refresh() {
        if (contentVersion != view.contentVersion) {
            contentVersion = view.contentVersion;
            if (!query.isEmpty()) {
                matches = view.contents.entrySet().stream().filter(entry -> query.matches(entry.getValue()))
                        .map(java.util.Map.Entry::getKey).toList();
            }
        }
        int count = query.isEmpty() ? unfilteredCells() : matches.size();
        totalRows = (count + COLUMNS - 1) / COLUMNS;
        target = Mth.clamp(target, 0, maxRow());
        if (maxRow() == 0) dragging = false;
    }

    private int unfilteredCells() {
        return view.contents.isEmpty() ? 0 : Backpack.positionOf(view.contents.lastKey()) + 1;
    }

    private int[] mapping() {
        int[] indices = new int[view.slots.cells().size()];
        Arrays.fill(indices, SlotBinding.INACTIVE);
        int start = (int) Math.round(target) * COLUMNS;
        int vacancy = 0;
        for (int i = 0; i < rows * COLUMNS; i++) {
            int position = start + i;
            if (query.isEmpty()) indices[i] = Backpack.slotAt(position);
            else if (position < matches.size()) indices[i] = matches.get(position);
            else {
                while (view.contents.containsKey(Backpack.slotAt(vacancy))) vacancy++;
                indices[i] = Backpack.slotAt(vacancy++);
            }
        }
        return indices;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeSlide != null) {
            drawSlide(graphics);
        } else {
            for (int i = 0; i < rows * COLUMNS; i++) {
                InventorySkin.slot(graphics, x + i % COLUMNS * CELL, y + i / COLUMNS * CELL);
            }
            view.present();
        }
        if (maxRow() > 0) {
            int thumb = thumbHeight();
            InventorySkin.scrollbar(graphics, railX(), y, RAIL_WIDTH, gridHeight(), thumbTop(thumb), thumb,
                    dragging || overRail(mouseX, mouseY));
        }
    }

    private void drawSlide(GuiGraphics graphics) {
        double remaining = Mth.clamp(1 - (Util.getMillis() - slideStart) / (double) SCROLL_ANIMATION_MS, 0, 1);
        int offset = activeSlide.direction() * (int) Math.round(CELL * remaining * remaining);
        int outgoingY = y + offset + (activeSlide.direction() > 0 ? -CELL : gridHeight());
        graphics.enableScissor(x, y, x + WIDTH, y + gridHeight());
        try {
            for (int i = 0; i < COLUMNS; i++) {
                drawMovingSlot(graphics, x + i * CELL, outgoingY, activeSlide.departing()[i]);
            }
            for (int i = 0; i < rows * COLUMNS; i++) {
                drawMovingSlot(graphics, x + i % COLUMNS * CELL, y + i / COLUMNS * CELL + offset,
                        view.slots.cells().get(i).getItem());
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawMovingSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        InventorySkin.slot(graphics, x, y);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        }
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
            scrollChange = true;
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
    private int gridHeight() { return rows * CELL; }
    private int maxRow() { return Math.max(0, totalRows - rows); }
    private int thumbHeight() { return Math.max(7, gridHeight() * rows / totalRows); }
    private int thumbTop(int thumb) { return y + (int) Math.round((gridHeight() - thumb) * target / maxRow()); }
}
