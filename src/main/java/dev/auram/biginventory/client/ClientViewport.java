package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackViewport;
import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.SlotBinding;
import dev.auram.biginventory.ViewportNetwork;
import dev.auram.biginventory.ViewportNetwork.Access;
import dev.auram.biginventory.ViewportNetwork.AutoSort;
import dev.auram.biginventory.ViewportNetwork.BulkMove;
import dev.auram.biginventory.ViewportNetwork.Index;
import dev.auram.biginventory.ViewportNetwork.Page;
import dev.auram.biginventory.ViewportNetwork.PageRequest;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Tracks the client's real slot bindings and the searchable index behind them. */
public final class ClientViewport {
    private static final int[] UNMAPPED = {};
    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static View active;
    private static int tokens;

    static View open(AbstractContainerMenu menu, Inventory inventory) {
        return open(menu, inventory, null);
    }

    static View open(AbstractContainerMenu menu, Inventory inventory, @Nullable ItemStack creativeCursor) {
        BackpackViewport viewport = BackpackViewport.find(menu, inventory);
        if (viewport == null) return null;
        close(active);
        View view = new View(menu, inventory, viewport, ++tokens);
        active = view;
        VIEWS.put(view.token, view);
        ViewportNetwork.request(new Access(menu.containerId, view.token, true, InventoryPreferences.autoSort(), creativeCursor));
        return view;
    }

    static void close(View view) {
        if (view == null || active != view) return;
        active = null;
        if (!view.closed) ViewportNetwork.request(new Access(view.menu.containerId, view.token, false, false, null));
    }

    static void reset() {
        for (View view : VIEWS.values()) view.slots.reset();
        VIEWS.clear();
        active = null;
    }

    public static boolean blocksNativeClick(AbstractContainerMenu menu, int id) {
        if (active == null || active.menu != menu || active.menu.containerId != id) return false;
        if (!active.interactive()) return true;
        active.lastClick = Util.getMillis();
        return false;
    }

    static boolean sortAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        View view = active;
        if (view == null || !represents(screen, view) || !view.menu.getCarried().isEmpty()) return false;
        if (view.cellAt(screen, mouseX, mouseY) < 0) return false;
        view.sort();
        return true;
    }

    static boolean bulkMoveAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        View view = active;
        if (view == null || !represents(screen, view) || !view.interactive() || !view.menu.getCarried().isEmpty()) return false;
        int cell = view.cellAt(screen, mouseX, mouseY);
        if (cell < 0) return false;
        ItemStack stack = view.slots.cells().get(cell).getItem();
        if (stack.getCount() <= Backpack.stackLimit(stack)) return false;
        view.lastClick = Util.getMillis();
        new BulkMove(view.token, view.applied, cell).send();
        return true;
    }

    private static boolean represents(AbstractContainerScreen<?> screen, View view) {
        return screen.getMenu() == view.menu
                || screen instanceof BackpackCreativeScreen creative && creative.displays(view);
    }

    public static void index(Index message) {
        View view = VIEWS.get(message.token());
        if (view == null) return;
        for (var change : message.updates()) {
            int slot = change.slot();
            ItemStack stack = change.item();
            if (stack.isEmpty()) view.contents.remove(slot);
            else view.contents.put(slot, stack);
            // Minecraft already reconciles displayed slots against click predictions.
            if (!view.displays(slot)) view.write(slot, stack);
        }
        if (message.complete() && !view.receivedIndex) {
            Backpack storage = Backpack.of(view.inventory);
            for (int position = 0; position < storage.size(); position++) {
                int slot = Backpack.slotAt(position);
                if (!view.contents.containsKey(slot)) view.inventory.setItem(slot, ItemStack.EMPTY);
            }
            storage.trimEmptyTail();
            view.receivedIndex = true;
        }
        if (!message.updates().isEmpty()) view.contentVersion++;
    }

    public static void page(Page message) {
        View view = VIEWS.get(message.token());
        if (view == null || message.sequence() < view.applied) return;
        if (message.closed()) {
            VIEWS.remove(message.token());
            view.closed = true;
            view.slots.reset();
        } else if (message.slots().length == view.slots.cells().size()) {
            view.slots.bind(message.slots());
        } else {
            view.slots.reset();
        }
        ((MenuStorageAccess) view.menu).bigInventory$resetQuickCraft();
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen && represents(screen, view)) {
            ((ViewportScreen) screen).bigInventory$resetPageGesture();
            if (screen instanceof BackpackCreativeScreen creative) creative.cancelQuickMoveAnimation();
        }
        int[] previous = view.mapping;
        view.mapping = message.slots();
        for (int slot : previous) {
            if (slot >= 0 && !view.displays(slot)) view.write(slot, view.contents.getOrDefault(slot, ItemStack.EMPTY));
        }
        view.received = message.sequence();
        if (view == active) SmoothSwappingCompat.rebase(view.menu);
    }

    public static void ready(ViewportNetwork.Ready message) {
        View view = VIEWS.get(message.token());
        if (view != null && message.sequence() == view.received && message.sequence() > view.applied) {
            view.applied = message.sequence();
            if (view == active) SmoothSwappingCompat.rebase(view.menu);
        }
    }

    static void beforeRender(AbstractContainerScreen<?> screen) {
        if (active != null && active.received != active.applied && represents(screen, active)) {
            SmoothSwappingCompat.rebase(active.menu);
        }
    }

    static final class View {
        final AbstractContainerMenu menu;
        final Inventory inventory;
        final BackpackViewport slots;
        final NavigableMap<Integer, ItemStack> contents = new TreeMap<>();
        final int token;
        int[] mapping = UNMAPPED;
        int contentVersion;
        long lastClick;
        int sequence;
        int received;
        int applied;
        int presented;
        boolean receivedIndex;
        boolean closed;
        private int[] requested = UNMAPPED;
        private int requestContentVersion;
        private boolean pendingSort;

        View(AbstractContainerMenu menu, Inventory inventory, BackpackViewport slots, int token) {
            this.menu = menu;
            this.inventory = inventory;
            this.slots = slots;
            this.token = token;
        }

        private boolean ready() {
            return mapping.length != 0 && sequence == applied && !closed;
        }

        boolean interactive() {
            return ready() && presented == applied;
        }

        void present() {
            if (ready()) presented = applied;
        }

        boolean request(int[] next) {
            if (!receivedIndex || closed || sequence != applied || Arrays.equals(mapping, next)) return false;
            if (requestContentVersion == contentVersion && Arrays.equals(requested, next)) return false;
            requested = next;
            requestContentVersion = contentVersion;
            ViewportNetwork.request(new PageRequest(menu.containerId, token, ++sequence, next, false));
            return true;
        }

        void sort() {
            if (interactive() && menu.getCarried().isEmpty()) {
                pendingSort = false;
                ViewportNetwork.request(new PageRequest(menu.containerId, token, ++sequence, UNMAPPED, true));
            }
        }

        void setAutoSort(boolean enabled) {
            if (active != this || closed) return;
            new AutoSort(menu.containerId, token, true, enabled).send();
            pendingSort = enabled;
        }

        boolean sendPendingSort(int[] next, boolean unfiltered) {
            if (!pendingSort || !interactive() || !menu.getCarried().isEmpty()) return false;
            pendingSort = false;
            ViewportNetwork.request(new PageRequest(menu.containerId, token, ++sequence, unfiltered ? next : UNMAPPED, true));
            return true;
        }

        int cellAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
            double x = mouseX - screen.getGuiLeft();
            double y = mouseY - screen.getGuiTop();
            for (int i = 0; i < slots.cells().size(); i++) {
                Slot slot = slots.cells().get(i);
                if (screen instanceof BackpackCreativeScreen creative) slot = creative.displayedSlot(slot);
                if (slot != null && slot.isActive() && slot.x >= 0
                        && x >= slot.x - 1 && x < slot.x + 17 && y >= slot.y - 1 && y < slot.y + 17) return i;
            }
            return -1;
        }

        boolean displays(int slot) {
            for (int bound : mapping) {
                if (bound == slot && bound != SlotBinding.INACTIVE) return true;
            }
            return false;
        }

        int occupiedCells() {
            int lastPosition = -1;
            // Visible slots already reflect native click prediction. The index can arrive a tick later.
            for (int i = 0; i < mapping.length; i++) {
                if (mapping[i] >= 0 && slots.cells().get(i).hasItem()) {
                    lastPosition = Math.max(lastPosition, Backpack.positionOf(mapping[i]));
                }
            }
            for (int slot : contents.descendingKeySet()) {
                int position = Backpack.positionOf(slot);
                if (position <= lastPosition) break;
                if (!displays(slot)) return position + 1;
            }
            return lastPosition + 1;
        }

        void write(int slot, ItemStack stack) {
            Backpack.of(inventory).prepareSlot(slot, stack);
            inventory.setItem(slot, stack.copy());
        }
    }

    private ClientViewport() {}
}
