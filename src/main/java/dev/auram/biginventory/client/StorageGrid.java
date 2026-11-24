package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.client.StorageSwapAnimation.Point;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

final class StorageGrid implements BackpackGrid {
    static final int CELL = 18;
    static final int COLUMNS = Inventory.getSelectionSize();
    static final int WIDTH = COLUMNS * CELL;
    static final int SEARCH_HEIGHT = StorageSearchBox.HEIGHT;
    static final int SEARCH_GAP = 4;
    static final int MAX_ROWS = 5;
    private static final int RAIL_GAP = 2;
    private static final int RAIL_WIDTH = 4;
    // The default creative animation halves the remaining distance every game tick.
    private static final int SCROLL_HALF_LIFE_MS = 50;
    private static final int SCROLL_TICK_INTERVAL_MS = 50;
    private final ClientStorage.View view;
    private final Font font;
    private final StorageSearchBox search;
    private List<Entry> filtered = List.of();
    private StorageSearchQuery query;
    private int displayedChange = -1;
    private int x;
    private int y;
    private int rows;
    private double targetRow;
    private double scrollPosition;
    private long lastScrollFrame;
    private long lastScrollTick = Util.getMillis() - SCROLL_TICK_INTERVAL_MS;
    private boolean dragging;
    private int thumbGrab;
    private StorageSwapAnimation animation;
    private StorageSweep sweep;

    StorageGrid(ClientStorage.View view, Font font, String priorSearch) {
        this.view = view;
        this.font = font;
        search = new StorageSearchBox(font);
        search.setWidth(WIDTH);
        search.setMaxLength(96);
        search.setValue(priorSearch);
        search.setResponder(text -> {
            cancelAnimation();
            stopSweep();
            dragging = false;
            query = StorageSearchQuery.parse(text);
            displayedChange = -1;
            targetRow = 0;
            scrollPosition = 0;
        });
        query = StorageSearchQuery.parse(priorSearch);
        view.grid = this;
    }

    @Override
    public StorageSearchBox search() { return search; }

    @Override
    public void bounds(int x, int y, int left, int top, int rows, boolean visible, int searchGap) {
        bounds(x, y, rows, searchGap);
        search.visible = visible;
        if (!visible) cancelAnimation();
    }

    @Override
    public void close() {
        ClientStorage.close(view);
    }

    void bounds(int x, int y, int rows) {
        bounds(x, y, rows, SEARCH_GAP);
    }

    void bounds(int x, int y, int rows, int searchGap) {
        if (this.x != x || this.y != y || this.rows != rows) {
            cancelAnimation();
            stopSweep();
            dragging = false;
        }
        this.x = x;
        this.y = y;
        this.rows = rows;
        search.setPosition(x, y - SEARCH_HEIGHT - searchGap);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        refresh();
        updateScroll(Util.getMillis());
        int scroll = scrollPixels();
        int firstRow = scroll / CELL;
        int partialRow = scroll % CELL;
        if (partialRow != 0) graphics.enableScissor(x, y, x + WIDTH, y + gridHeight());
        try {
            renderSlots(graphics, indexUnderMouse(mouseX, mouseY), firstRow, partialRow);
        } finally {
            if (partialRow != 0) graphics.disableScissor();
        }
        if (maxRow() > 0) {
            int thumb = thumbHeight();
            InventorySkin.scrollbar(graphics, railX(), y, RAIL_WIDTH, gridHeight(), thumbTop(thumb), thumb,
                    dragging || overRail(mouseX, mouseY));
        }
    }

    private void renderSlots(GuiGraphics graphics, int hovered, int firstRow, int partialRow) {
        StorageSwapAnimation swap = animation();
        int visibleRows = partialRow == 0 ? rows : rows + 1;
        for (int cell = 0; cell < COLUMNS * visibleRows; cell++) {
            int index = firstRow * COLUMNS + cell;
            int px = x + cell % COLUMNS * CELL;
            int py = y + cell / COLUMNS * CELL - partialRow;
            InventorySkin.slot(graphics, px, py, index == hovered);
            if (index >= filtered.size()) continue;
            Entry entry = filtered.get(index);
            if (entry == null || swap != null && swap.hides(entry)) continue;
            graphics.renderItem(entry.icon(), px + 1, py + 1);
            graphics.renderItemDecorations(font, entry.icon(), px + 1, py + 1, "");
            if (entry.count() > 1) renderCount(graphics, entry.count(), px, py);
        }
    }

    @Override
    public void tooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!view.renderedCarried().isEmpty()) return;
        Entry entry = entryAt(indexUnderMouse(mouseX, mouseY));
        if (entry == null) return;
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.icon()));
        lines.add(Component.translatable("biginventory.stored", entry.count()).withStyle(ChatFormatting.GRAY));
        graphics.renderTooltip(font, lines, entry.icon().getTooltipImage(), entry.icon(), mouseX, mouseY);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int button) {
        if (button > 1) return false;
        refresh();
        if (overRail(mouseX, mouseY)) {
            if (button != 0) return false;
            int thumb = thumbHeight();
            int top = thumbTop(thumb);
            dragging = true;
            cancelAnimation();
            if (mouseY >= top && mouseY < top + thumb) {
                thumbGrab = (int) mouseY - top;
                targetRow = scrollPosition / CELL;
                return true;
            }
            thumbGrab = thumb / 2;
            return drag(mouseY);
        }
        int index = indexUnderMouse(mouseX, mouseY);
        if (index < 0) return false;
        Entry entry = entryAt(index);
        if (!view.menu.getCarried().isEmpty()) {
            view.act(-1, button == 0 ? Gesture.DEPOSIT : Gesture.DEPOSIT_ONE);
        } else if (entry != null) {
            view.act(entry.id(), Screen.hasShiftDown() ? Gesture.SHIFT_TAKE
                    : button == 0 ? Gesture.TAKE : Gesture.TAKE_HALF);
        }
        return true;
    }

    boolean startSweep(double mouseX, double mouseY) {
        if (!search.visible || !inside(mouseX, mouseY) || dragging || !view.renderedCarried().isEmpty()
                || !MouseTweaksCompat.shiftDragEnabled()) return false;
        stopSweep();
        refresh();
        sweep = new StorageSweep(filtered);
        visitSweep(mouseX, mouseY);
        return true;
    }

    void dragSweep(double mouseX, double mouseY) {
        if (sweep != null && search.visible && Screen.hasShiftDown() && view.renderedCarried().isEmpty()) {
            visitSweep(mouseX, mouseY);
        }
    }

    void stopSweep() {
        if (sweep == null) return;
        sweep = null;
        displayedChange = -1;
    }

    private void visitSweep(double mouseX, double mouseY) {
        refresh();
        var variant = sweep.visit(indexUnderMouse(mouseX, mouseY));
        if (variant != null) view.queueShiftTake(variant);
    }

    @Nullable
    private Entry entryAt(int index) {
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double delta) {
        refresh();
        if (delta == 0 || maxRow() == 0 || !(inside(mouseX, mouseY) || overRail(mouseX, mouseY))) return false;
        if (dragging) return true;
        long previousRow = Math.round(targetRow);
        targetRow = Mth.clamp(targetRow - delta, 0.0, maxRow());
        if (Math.round(targetRow) != previousRow) {
            cancelAnimation();
            long now = Util.getMillis();
            if (scrollPosition == previousRow * CELL) lastScrollFrame = now;
            playScrollTick(now);
        }
        return true;
    }

    @Override
    public boolean drag(double mouseY) {
        refresh();
        if (!dragging) return false;
        cancelAnimation();
        int travel = gridHeight() - thumbHeight();
        double nextRow = travel <= 0 ? 0 : Mth.clamp((mouseY - y - thumbGrab) * maxRow() / travel, 0.0, maxRow());
        if (Math.round(nextRow) != Math.round(targetRow)) playScrollTick(Util.getMillis());
        targetRow = nextRow;
        scrollPosition = nextRow * CELL;
        return true;
    }

    @Override
    public boolean release() {
        if (!dragging) return false;
        dragging = false;
        targetRow = Math.round(targetRow);
        lastScrollFrame = Util.getMillis();
        return true;
    }

    private void refresh() {
        if (displayedChange != view.changes) {
            displayedChange = view.changes;
            filtered = filter(view.displayedEntries());
        }
        int last = maxRow();
        targetRow = Mth.clamp(targetRow, 0.0, last);
        scrollPosition = Mth.clamp(scrollPosition, 0.0, last * CELL);
        if (last == 0) dragging = false;
    }

    private void updateScroll(long now) {
        double target = Math.round(targetRow) * CELL;
        if (!dragging && scrollPosition != target) {
            double elapsed = (now - lastScrollFrame) / (double) SCROLL_HALF_LIFE_MS;
            double remaining = (scrollPosition - target) * Math.pow(0.5, elapsed);
            scrollPosition = Math.abs(remaining) < 0.5 ? target : target + remaining;
        }
        lastScrollFrame = now;
    }

    private void playScrollTick(long now) {
        if (now - lastScrollTick < SCROLL_TICK_INTERVAL_MS) return;
        lastScrollTick = now;
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f, 0.2f));
    }

    private List<Entry> filter(List<Entry> entries) {
        if (sweep != null) return sweep.layout(entries);
        if (query.isEmpty()) return entries;
        return entries.stream().filter(entry -> query.matches(entry.icon())).toList();
    }

    void startAnimation(AbstractContainerScreen<?> screen, StoragePrediction preview) {
        cancelAnimation();
        var transfer = preview.transfer();
        if (transfer == null || rows <= 0 || dragging || scrollPixels() != Math.round(targetRow) * CELL) return;
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
        int index = visible.indexOf(entry);
        if (index < 0) return null;
        int scroll = Math.min(scrollPixels(), maxRow(visible.size()) * CELL);
        int centerY = y + index / COLUMNS * CELL + CELL / 2 - scroll;
        if (centerY < y || centerY >= y + gridHeight()) return null;
        return new Point(x + index % COLUMNS * CELL + CELL / 2, centerY);
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
        return ((int) (my - y) + scrollPixels()) / CELL * COLUMNS + (int) (mx - x) / CELL;
    }

    private int scrollPixels() { return (int) Math.round(scrollPosition); }

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
        return y + (int) Math.round((gridHeight() - thumb) * scrollPosition / (maxRow() * CELL));
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
