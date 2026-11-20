package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

final class BackpackScreen extends InventoryScreen {
    private static final int HEADER = 86;
    private static final int FOOTER = 26 + StorageGrid.SEARCH_HEIGHT + 3;
    private final Player player;
    private ClientStorage.View view;
    private PlayerSlots playerSlots;
    private StorageGrid grid;
    private ImageButton recipeButton;
    private int rows;

    BackpackScreen(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    protected void init() {
        rows = Math.max(1, (height - 12 - HEADER - FOOTER) / StorageGrid.COMPACT_CELL);
        imageHeight = HEADER + rows * StorageGrid.COMPACT_CELL + FOOTER;
        recipeButton = null;
        super.init();
        if (minecraft.gameMode.hasInfiniteItems()) return;

        for (var child : children()) {
            if (child instanceof ImageButton button) {
                recipeButton = button;
                break;
            }
        }
        if (view == null) view = ClientStorage.open(menu.containerId);
        if (playerSlots == null) playerSlots = new PlayerSlots(menu, player.getInventory());
        playerSlots.arrange(0, imageHeight - 22);
        String query = grid == null ? "" : grid.search().getValue();
        grid = new StorageGrid(view, font, query, StorageGrid.COMPACT_CELL);
        addRenderableWidget(grid.search());
        layoutControls();
    }

    private boolean showGrid() {
        return grid != null && !(getRecipeBookComponent().isVisible() && width < 379);
    }

    private void layoutControls() {
        if (grid == null) return;
        if (recipeButton != null) recipeButton.setPosition(leftPos + 104, topPos + 61);
        grid.bounds(leftPos + 7, topPos + HEADER, rows);
        grid.searchBelowGrid();
        grid.search().visible = showGrid();
        if (!showGrid() && getFocused() == grid.search()) setFocused(null);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int mouseX, int mouseY) {
        layoutControls();
        InventorySkin.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        InventorySkin.well(graphics, leftPos + 25, topPos + 6, 52, 72, 0xff202020);
        InventorySkin.craftingArrow(graphics, leftPos + 135, topPos + 36);
        Slot result = menu.getSlot(0);
        InventorySkin.well(graphics, leftPos + result.x - 4, topPos + result.y - 4, 24, 24, 0xff8c8c8c);
        for (int index = 1; index < menu.slots.size(); index++) {
            Slot slot = menu.getSlot(index);
            if (slot.x >= 0 && slot.isActive()) InventorySkin.slot(graphics, leftPos, topPos, slot);
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 51, topPos + 75, 30,
                leftPos + 51 - mouseX, topPos + 25 - mouseY, player);
        if (showGrid()) grid.render(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        super.render(graphics, mouseX, mouseY, time);
        if (showGrid()) grid.tooltip(graphics, menu.getCarried(), mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (grid != null) grid.search().tick();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (showGrid() && grid.click(x, y, button, menu.getCarried())) {
            setFocused(null);
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        layoutControls();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return showGrid() && grid.scroll(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return showGrid() && grid.drag(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return showGrid() && grid.release() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (showGrid() && getFocused() == grid.search() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
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
