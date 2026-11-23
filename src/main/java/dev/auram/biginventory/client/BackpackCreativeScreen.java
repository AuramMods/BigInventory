package dev.auram.biginventory.client;

import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.mixin.SlotPosition;
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
    private static final int ROWS = 3;
    private static final int EQUIPMENT_BOTTOM = 50;
    private static final int SEARCH_PADDING = 3;
    private static final int GRID_TOP = EQUIPMENT_BOTTOM + SEARCH_PADDING + StorageGrid.SEARCH_HEIGHT + SEARCH_PADDING;
    private static final int HOTBAR_TOP = GRID_TOP + ROWS * StorageGrid.CELL + 4;
    private static final int INVENTORY_HEIGHT = HOTBAR_TOP + 22;

    private final Player player;
    private final int pickerHeight;
    private ClientStorage.View view;
    private StorageGrid grid;
    private StorageSearchBox search;
    private Slot firstSlot;
    private boolean gridClick;

    BackpackCreativeScreen(Player player, FeatureFlagSet features, boolean showOperatorTab) {
        super(player, features, showOperatorTab);
        this.player = player;
        pickerHeight = imageHeight;
    }

    public boolean isStorageOpen() {
        return view != null && isInventoryOpen();
    }

    @Override
    protected void init() {
        imageHeight = pickerHeight;
        super.init();
        if (isStorageOpen()) addRenderableWidget(search);
        syncInventoryTab();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (minecraft.screen == this) {
            syncInventoryTab();
            if (search != null) search.tick();
        }
    }

    /** The native picker builds fresh wrapper slots each time the player tab is selected. */
    private void syncInventoryTab() {
        boolean inventoryOpen = isInventoryOpen();
        imageHeight = inventoryOpen ? INVENTORY_HEIGHT : pickerHeight;
        topPos = (height - imageHeight) / 2;
        if (!inventoryOpen) {
            if (view != null) {
                closeStorage();
                if (player.containerMenu == player.inventoryMenu) player.containerMenu = menu;
            }
            return;
        }
        if (firstSlot != menu.slots.get(0)) {
            firstSlot = menu.slots.get(0);
            new PlayerSlots(menu, player.getInventory()).arrange(0, HOTBAR_TOP);
            ((SlotPosition) trashSlot()).bigInventory$setY(HOTBAR_TOP);
        }
        if (view == null) {
            // The picker still renders its native slots and tabs. Inventory clicks use menu zero.
            player.containerMenu = player.inventoryMenu;
            view = ClientStorage.open(player.inventoryMenu, menu.getCarried());
            grid = new StorageGrid(view, font, "");
            search = addRenderableWidget(grid.search());
        }
        grid.bounds(leftPos + 8, topPos + GRID_TOP, ROWS, SEARCH_PADDING);
    }

    private void closeStorage() {
        ClientStorage.close(view);
        if (view != null) player.inventoryMenu.setCarried(ItemStack.EMPTY);
        if (search != null) {
            if (getFocused() == search) setFocused(null);
            removeWidget(search);
        }
        view = null;
        grid = null;
        search = null;
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
        InventorySkin.well(graphics, leftPos + 72, topPos + 6, 34, 43, InventorySkin.Tone.PREVIEW);
        for (Slot slot : menu.slots) {
            if (slot.x >= 0 && slot.isActive()) InventorySkin.slot(graphics, leftPos, topPos, slot);
        }
        Slot trash = trashSlot();
        InventorySkin.trash(graphics, leftPos + trash.x, topPos + trash.y);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 88, topPos + 45, 20,
                leftPos + 88 - mouseX, topPos + 15 - mouseY, player);
        grid.render(graphics, mouseX, mouseY);
        for (CreativeModeTab tab : tabs) {
            if (tab.getType() == CreativeModeTab.Type.INVENTORY) renderTabButton(graphics, tab);
        }
    }

    private Slot trashSlot() {
        return menu.getSlot(player.inventoryMenu.slots.size());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        super.render(graphics, mouseX, mouseY, time);
        if (isStorageOpen()) grid.tooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (isStorageOpen() && grid.click(x, y, button)) {
            setFocused(null);
            gridClick = true;
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        syncInventoryTab();
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
        syncInventoryTab();
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
        if (isStorageOpen() && getFocused() == search && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            search.keyPressed(key, scan, modifiers);
            return true;
        }
        boolean handled = super.keyPressed(key, scan, modifiers);
        if (minecraft.screen == this) syncInventoryTab();
        return handled;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (isStorageOpen() && getFocused() == search) {
            return search.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int id, int button, ClickType click) {
        if (!isStorageOpen()) {
            super.slotClicked(slot, id, button, click);
            return;
        }
        if (view.revision == 0) return;
        int index = slot == null ? id : menu.slots.indexOf(slot);
        int inventorySize = player.inventoryMenu.slots.size();
        if (slot != null && index == inventorySize) {
            view.act(-1, click == ClickType.QUICK_MOVE ? Gesture.CREATIVE_CLEAR : Gesture.CREATIVE_TRASH);
        } else if (slot == null || index >= 0 && index < inventorySize) {
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, index, button, click, player);
        }
    }
}
