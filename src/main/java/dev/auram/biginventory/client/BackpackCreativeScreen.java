package dev.auram.biginventory.client;

import dev.auram.biginventory.ViewportNetwork.CreativeTrash;
import dev.auram.biginventory.mixin.CreativeInventoryAccess;
import dev.auram.biginventory.mixin.CreativeSlotAccess;
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
    private ClientViewport.View view;
    private RealStorageGrid grid;
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

    boolean displays(ClientViewport.View candidate) {
        return view == candidate && isInventoryOpen();
    }

    @Override
    protected void init() {
        imageHeight = pickerHeight;
        super.init();
        if (isStorageOpen()) for (var widget : grid.widgets()) addRenderableWidget(widget);
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
        Slot first = menu.slots.isEmpty() ? null : menu.slots.get(0);
        if (firstSlot != first) {
            firstSlot = first;
            new PlayerSlots(menu, player.getInventory()).arrange(0, HOTBAR_TOP);
        }
        Slot trash = trashSlot();
        if (trash != null) ((SlotPosition) trash).bigInventory$setY(HOTBAR_TOP);
        if (view == null) {
            // The picker still renders its native slots and tabs. Inventory clicks use menu zero.
            player.containerMenu = player.inventoryMenu;
            view = ClientViewport.open(player.inventoryMenu, player.getInventory(), menu.getCarried());
            if (view == null) return;
            grid = new RealStorageGrid(view, font, "");
            search = grid.search();
            for (var widget : grid.widgets()) addRenderableWidget(widget);
        }
        grid.bounds(leftPos + 8, topPos + GRID_TOP, leftPos, topPos, ROWS, true, SEARCH_PADDING);
        for (Slot slot : menu.slots) {
            if (slot instanceof CreativeSlotAccess wrapper && view.slots.cells().contains(wrapper.bigInventory$targetSlot())) {
                Slot target = wrapper.bigInventory$targetSlot();
                SlotPosition position = (SlotPosition) slot;
                position.bigInventory$setX(target.x);
                position.bigInventory$setY(target.y);
            }
        }
    }

    private void closeStorage() {
        ClientViewport.close(view);
        if (view != null) player.inventoryMenu.setCarried(ItemStack.EMPTY);
        if (search != null) {
            if (getFocused() == search) setFocused(null);
            for (var widget : grid.widgets()) removeWidget(widget);
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
        if (trash != null) InventorySkin.trash(graphics, leftPos + trash.x, topPos + trash.y);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 88, topPos + 45, 20,
                leftPos + 88 - mouseX, topPos + 15 - mouseY, player);
        grid.render(graphics, mouseX, mouseY);
        for (CreativeModeTab tab : tabs) {
            if (tab.getType() == CreativeModeTab.Type.INVENTORY) renderTabButton(graphics, tab);
        }
    }

    @Nullable
    private Slot trashSlot() {
        Slot trash = ((CreativeInventoryAccess) (CreativeModeInventoryScreen) this).bigInventory$trashSlot();
        return trash != null && menu.slots.contains(trash) ? trash : null;
    }

    @Nullable
    Slot displayedSlot(Slot backing) {
        for (Slot slot : menu.slots) {
            if (slot instanceof CreativeSlotAccess wrapper && wrapper.bigInventory$targetSlot() == backing) return slot;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        syncInventoryTab();
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
        if (minecraft.screen == this) syncInventoryTab();
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
        if (minecraft.screen == this) syncInventoryTab();
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
        if (!view.interactive()) return;
        if (slot != null && slot == trashSlot()) {
            new CreativeTrash(view.token, click == ClickType.QUICK_MOVE).send();
            return;
        }
        int index = id;
        if (slot != null) {
            if (!(slot instanceof CreativeSlotAccess wrapper)) return;
            Slot target = wrapper.bigInventory$targetSlot();
            index = target.index;
            if (index < 0 || index >= view.menu.slots.size() || view.menu.getSlot(index) != target) return;
        }
        minecraft.gameMode.handleInventoryMouseClick(view.menu.containerId, index, button, click, player);
    }
}
