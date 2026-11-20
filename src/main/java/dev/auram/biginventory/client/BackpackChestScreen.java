package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.lwjgl.glfw.GLFW;

final class BackpackChestScreen extends ContainerScreen {
    private final Inventory inventory;
    private final int chestBottom;
    private ClientStorage.View view;
    private PlayerSlots playerSlots;
    private StorageGrid grid;
    private boolean searchVisible;

    BackpackChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
        this.chestBottom = menu.getRowCount() * 18 + 17;
    }

    @Override
    protected void init() {
        // Double chests are nearly the full viewport at the largest GUI scale.
        searchVisible = height >= chestBottom + 109;
        imageHeight = chestBottom + (searchVisible ? 106 : 96);
        inventoryLabelY = -30000;
        super.init();
        if (view == null) view = ClientStorage.open(menu);
        if (playerSlots == null) playerSlots = new PlayerSlots(menu, inventory);
        playerSlots.arrange(0, imageHeight - 24);
        String query = searchVisible && grid != null ? grid.search().getValue() : "";
        grid = new StorageGrid(view, font, query);
        int gridTop = chestBottom + 11 + (searchVisible ? StorageGrid.SEARCH_HEIGHT : 0);
        grid.bounds(leftPos + 7, topPos + gridTop, 3);
        if (searchVisible) addRenderableWidget(grid.search());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int x, int y) {
        super.renderBg(graphics, time, x, y);
        int bottom = topPos + imageHeight;
        InventorySkin.panel(graphics, leftPos, topPos + chestBottom, imageWidth, imageHeight - chestBottom);
        for (int i = 0; i < 9; i++) {
            int px = leftPos + 7 + i * 18;
            InventorySkin.slot(graphics, px, bottom - 25);
        }
        grid.render(graphics, x, y);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float time) {
        super.render(graphics, x, y, time);
        grid.tooltip(graphics, x, y);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        grid.search().tick();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (grid.click(x, y, button)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return grid.scroll(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return grid.drag(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return grid.release() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == grid.search() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            grid.search().keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ClientStorage.close(view);
        view = null;
        if (playerSlots != null) playerSlots.restore();
        playerSlots = null;
        super.removed();
    }
}
