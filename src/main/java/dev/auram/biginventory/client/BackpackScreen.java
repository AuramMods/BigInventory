package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

final class BackpackScreen extends InventoryScreen {
    private static final int PANEL_WIDTH = 188;
    private static final int MAX_ROWS = 5;
    private static final int STORAGE_TOP = 86 + StorageGrid.SEARCH_HEIGHT + 4;
    private static final int FOOTER = 25;
    private static final int PREVIEW_LEFT = 25;
    private static final int PREVIEW_TOP = 7;
    private static final int PREVIEW_WIDTH = 50;
    private static final int PREVIEW_HEIGHT = 72;
    private final Player player;
    private ClientStorage.View view;
    private PlayerSlots playerSlots;
    private StorageGrid grid;
    // JEI recognizes focused EditBox fields on a screen when routing keyboard input.
    private StorageSearchBox search;
    private ImageButton recipeButton;
    private int rows;

    BackpackScreen(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    protected void init() {
        imageWidth = PANEL_WIDTH;
        rows = Mth.clamp((height - 12 - STORAGE_TOP - FOOTER) / StorageGrid.CELL, 1, MAX_ROWS);
        imageHeight = STORAGE_TOP + rows * StorageGrid.CELL + FOOTER;
        recipeButton = null;
        super.init();
        if (minecraft.gameMode.hasInfiniteItems()) return;

        for (var child : children()) {
            if (child instanceof ImageButton button) {
                recipeButton = button;
                break;
            }
        }
        boolean opening = view == null;
        if (opening) view = ClientStorage.open(menu);
        if (playerSlots == null) playerSlots = new PlayerSlots(menu, player.getInventory());
        titleLabelX = menu.getSlot(InventoryMenu.CRAFT_SLOT_START).x - 1;
        int gridLeft = (imageWidth - StorageGrid.WIDTH) / 2;
        playerSlots.arrange(gridLeft - 7, imageHeight - 22);
        String query = search == null ? "" : search.getValue();
        grid = new StorageGrid(view, font, query);
        search = addRenderableWidget(grid.search());
        layoutControls();
        if (opening && search.visible) setInitialFocus(search);
    }

    private boolean showGrid() {
        return grid != null && !(getRecipeBookComponent().isVisible() && width < 379);
    }

    private void layoutControls() {
        if (grid == null) return;
        if (recipeButton != null) {
            Slot offhand = menu.getSlot(InventoryMenu.SHIELD_SLOT);
            recipeButton.setPosition(leftPos + offhand.x + 19, topPos + offhand.y - 1);
        }
        grid.bounds(leftPos + (imageWidth - StorageGrid.WIDTH) / 2, topPos + STORAGE_TOP, rows);
        search.visible = showGrid();
        if (!search.visible) {
            grid.cancelAnimation();
            if (getFocused() == search) setFocused(null);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int mouseX, int mouseY) {
        layoutControls();
        InventorySkin.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        InventorySkin.well(graphics, leftPos + PREVIEW_LEFT, topPos + PREVIEW_TOP,
                PREVIEW_WIDTH, PREVIEW_HEIGHT, InventorySkin.Tone.PREVIEW);
        Slot result = menu.getSlot(InventoryMenu.RESULT_SLOT);
        Slot lastInputInRow = menu.getSlot(InventoryMenu.CRAFT_SLOT_START + menu.getGridWidth() - 1);
        // JEI's vanilla inventory hotspot lines up with the tip of the arrow.
        InventorySkin.craftingArrow(graphics, leftPos + lastInputInRow.x + 21,
                leftPos + result.x - 12, topPos + result.y + 8);
        InventorySkin.well(graphics, leftPos + result.x - 4, topPos + result.y - 4, 24, 24, InventorySkin.Tone.SLOT);
        for (int index = 1; index < menu.slots.size(); index++) {
            Slot slot = menu.getSlot(index);
            if (slot.x >= 0 && slot.isActive()) InventorySkin.slot(graphics, leftPos, topPos, slot);
        }
        int previewX = leftPos + PREVIEW_LEFT + PREVIEW_WIDTH / 2;
        int previewY = topPos + PREVIEW_TOP + PREVIEW_HEIGHT - 3;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, previewX, previewY, 30,
                previewX - mouseX, previewY - 50 - mouseY, player);
        if (showGrid()) grid.render(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        super.render(graphics, mouseX, mouseY, time);
        if (showGrid()) grid.tooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (search != null) search.tick();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (showGrid() && grid.click(x, y, button)) {
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
        if (showGrid() && getFocused() == search && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            search.keyPressed(key, scanCode, modifiers);
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
