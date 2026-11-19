package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Gesture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

public final class BackpackCreativeScreen extends CreativeModeInventoryScreen {
    private static final int COLUMNS = 9;
    private static final int ROWS = 3;
    private static final int GRID_TOP = 52;

    private final Player player;
    private ClientStorage.View view;
    private StorageGrid grid;
    private Slot firstSlot;
    private boolean gridClick;

    BackpackCreativeScreen(Player player, FeatureFlagSet features, boolean showOperatorTab) {
        super(player, features, showOperatorTab);
        this.player = player;
    }

    public boolean isStorageOpen() {
        return view != null && isInventoryOpen();
    }

    @Override
    protected void init() {
        super.init();
        syncInventoryTab();
        if (grid != null) addRenderableWidget(grid.search());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (minecraft.screen == this) {
            if (syncInventoryTab()) addRenderableWidget(grid.search());
            if (grid != null) grid.search().tick();
        }
    }

    /** The native picker builds fresh wrapper slots each time the player tab is selected. */
    private boolean syncInventoryTab() {
        if (!isInventoryOpen()) {
            if (view != null) {
                closeStorage();
                if (player.containerMenu == player.inventoryMenu) player.containerMenu = menu;
            }
            return false;
        }
        if (firstSlot != menu.slots.get(0)) {
            firstSlot = menu.slots.get(0);
            new PlayerSlots(menu, player.getInventory()).hideBackpack();
        }
        boolean opened = view == null;
        if (opened) {
            // The picker still renders its native slots and tabs. Inventory clicks use menu zero.
            player.containerMenu = player.inventoryMenu;
            view = ClientStorage.open(player.inventoryMenu.containerId, menu.getCarried());
            grid = new StorageGrid(view, font, "", StorageGrid.COMPACT_CELL);
        }
        int gridLeft = (imageWidth - COLUMNS * StorageGrid.COMPACT_CELL - StorageGrid.SCROLLBAR_SPACE) / 2;
        grid.bounds(leftPos + gridLeft, topPos + GRID_TOP, COLUMNS, ROWS);
        grid.searchBelowGrid();
        return opened;
    }

    private void closeStorage() {
        ClientStorage.close(view);
        if (view != null) player.inventoryMenu.setCarried(ItemStack.EMPTY);
        if (grid != null) {
            if (getFocused() == grid.search()) setFocused(null);
            removeWidget(grid.search());
        }
        view = null;
        grid = null;
        firstSlot = null;
        gridClick = false;
    }

    @Override
    public void removed() {
        closeStorage();
        if (player.containerMenu == menu) player.containerMenu = player.inventoryMenu;
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int mouseX, int mouseY) {
        if (!isStorageOpen()) {
            super.renderBg(graphics, time, mouseX, mouseY);
            return;
        }
        var tabs = getCurrentPage().getVisibleTabs();
        for (CreativeModeTab tab : tabs) {
            if (tab.getType() != CreativeModeTab.Type.INVENTORY) renderTabButton(graphics, tab);
        }
        InventorySkin.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        InventorySkin.well(graphics, leftPos + 72, topPos + 6, 34, 43, 0xff202020);
        for (Slot slot : menu.slots) {
            if (slot.x >= 0 && slot.isActive()) InventorySkin.slot(graphics, leftPos, topPos, slot);
        }
        Slot trash = menu.getSlot(player.inventoryMenu.slots.size());
        InventorySkin.trash(graphics, leftPos + trash.x, topPos + trash.y);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 88, topPos + 45, 20,
                leftPos + 88 - mouseX, topPos + 15 - mouseY, player);
        grid.render(graphics, mouseX, mouseY);
        for (CreativeModeTab tab : tabs) {
            if (tab.getType() == CreativeModeTab.Type.INVENTORY) renderTabButton(graphics, tab);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        super.render(graphics, mouseX, mouseY, time);
        if (isStorageOpen()) grid.tooltip(graphics, menu.getCarried(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (isStorageOpen() && grid.click(x, y, button, menu.getCarried())) {
            setFocused(null);
            gridClick = true;
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        if (syncInventoryTab()) addRenderableWidget(grid.search());
        return handled;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (gridClick) {
            gridClick = false;
            grid.release();
            return true;
        }
        boolean handled = super.mouseReleased(x, y, button);
        if (syncInventoryTab()) addRenderableWidget(grid.search());
        return handled;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return isStorageOpen() && grid.drag(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return isStorageOpen() && grid.scroll(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (isStorageOpen() && getFocused() == grid.search() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            grid.search().keyPressed(key, scan, modifiers);
            return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        if (minecraft.screen == this && syncInventoryTab()) addRenderableWidget(grid.search());
        return handled;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (isStorageOpen() && getFocused() == grid.search()) {
            return grid.search().charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int id, int button, ClickType click) {
        if (!isStorageOpen()) {
            super.slotClicked(slot, id, button, click);
            return;
        }
        if (view.revision == 0 || view.pending) return;
        int index = slot == null ? id : menu.slots.indexOf(slot);
        int inventorySize = player.inventoryMenu.slots.size();
        if (slot != null && index == inventorySize) {
            view.act(-1, click == ClickType.QUICK_MOVE ? Gesture.CREATIVE_CLEAR : Gesture.CREATIVE_TRASH);
        } else if (slot == null || index >= 0 && index < inventorySize) {
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, index, button, click, player);
        }
    }
}
