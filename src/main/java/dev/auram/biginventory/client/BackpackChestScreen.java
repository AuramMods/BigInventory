package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.lwjgl.glfw.GLFW;

final class BackpackChestScreen extends ContainerScreen {
    private static final int FOOTER = 29;
    private final Inventory inventory;
    private final int chestBottom;
    private final int gridTop;
    private ClientViewport.View view;
    private PlayerSlots playerSlots;
    private RealStorageGrid grid;
    private int rows;

    BackpackChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
        this.chestBottom = menu.getRowCount() * 18 + 17;
        this.gridTop = chestBottom + 11 + StorageGrid.SEARCH_HEIGHT;
    }

    @Override
    protected void init() {
        rows = Mth.clamp((height - gridTop - FOOTER - 4) / StorageGrid.CELL, 1, StorageGrid.MAX_ROWS);
        imageHeight = gridTop + StorageGrid.CELL + FOOTER;
        inventoryLabelY = -30000;
        super.init();
        if (view == null) view = ClientViewport.open(menu, inventory);
        if (view == null) return;
        if (playerSlots == null) playerSlots = new PlayerSlots(menu, inventory);
        playerSlots.arrange(0, imageHeight - 24);
        String query = grid != null ? grid.search().getValue() : "";
        grid = new RealStorageGrid(view, font, query);
        grid.bounds(leftPos + 7, topPos + gridTop, leftPos, topPos, rows, true);
        for (var widget : grid.widgets()) addRenderableWidget(widget);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float time) {
        if (grid != null) {
            grid.bounds(leftPos + 7, topPos + gridTop, leftPos, topPos, rows, true);
            int preferredHeight = gridTop + grid.shownRows() * StorageGrid.CELL + FOOTER;
            if (preferredHeight != imageHeight && menu.getCarried().isEmpty()) {
                imageHeight = preferredHeight;
                topPos = (height - imageHeight) / 2;
                playerSlots.arrange(0, imageHeight - 24);
                grid.bounds(leftPos + 7, topPos + gridTop, leftPos, topPos, rows, true);
            }
        }
        super.render(graphics, x, y, time);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int x, int y) {
        if (grid == null) {
            super.renderBg(graphics, time, x, y);
            return;
        }
        graphics.enableScissor(leftPos, topPos, leftPos + imageWidth, topPos + chestBottom);
        try {
            super.renderBg(graphics, time, x, y);
        } finally {
            graphics.disableScissor();
        }
        int bottom = topPos + imageHeight;
        InventorySkin.panel(graphics, leftPos, topPos + chestBottom, imageWidth, imageHeight - chestBottom);
        for (int i = 0; i < 9; i++) {
            int px = leftPos + 7 + i * 18;
            InventorySkin.slot(graphics, px, bottom - 25);
        }
        grid.render(graphics, x, y);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (grid != null) grid.search().tick();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (grid != null && grid.click(x, y, button)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return grid != null && grid.scroll(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return grid != null && grid.drag(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return grid != null && grid.release() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (grid != null && getFocused() == grid.search() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            grid.search().keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ClientViewport.close(view);
        view = null;
        if (playerSlots != null) playerSlots.restore();
        playerSlots = null;
        super.removed();
    }
}
