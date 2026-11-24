package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackViewport;
import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.SlotBinding;
import dev.auram.biginventory.ViewportNetwork;
import dev.auram.biginventory.ViewportNetwork.Access;
import dev.auram.biginventory.ViewportNetwork.Index;
import dev.auram.biginventory.ViewportNetwork.Page;
import dev.auram.biginventory.ViewportNetwork.PageRequest;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

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
        BackpackViewport viewport = BackpackViewport.find(menu, inventory);
        if (viewport == null) return null;
        close(active);
        View view = new View(menu, inventory, viewport, ++tokens);
        active = view;
        VIEWS.put(view.token, view);
        ViewportNetwork.request(new Access(menu.containerId, view.token, true));
        return view;
    }

    static void close(View view) {
        if (view == null || active != view) return;
        active = null;
        if (!view.closed) ViewportNetwork.request(new Access(view.menu.containerId, view.token, false));
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
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen && screen.getMenu() == view.menu) {
            ((ViewportScreen) screen).bigInventory$resetPageGesture();
        }
        int[] previous = view.mapping;
        view.mapping = message.slots();
        for (int slot : previous) {
            if (slot >= 0 && !view.displays(slot)) view.write(slot, view.contents.getOrDefault(slot, ItemStack.EMPTY));
        }
        view.received = message.sequence();
    }

    public static void ready(ViewportNetwork.Ready message) {
        View view = VIEWS.get(message.token());
        if (view != null && message.sequence() == view.received) view.applied = message.sequence();
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

        void request(int[] next) {
            if (!receivedIndex || closed || sequence != applied || Arrays.equals(mapping, next)) return;
            if (requestContentVersion == contentVersion && Arrays.equals(requested, next)) return;
            requested = next;
            requestContentVersion = contentVersion;
            ViewportNetwork.request(new PageRequest(menu.containerId, token, ++sequence, next));
        }

        boolean displays(int slot) {
            for (int bound : mapping) {
                if (bound == slot && bound != SlotBinding.INACTIVE) return true;
            }
            return false;
        }

        void write(int slot, ItemStack stack) {
            Backpack.of(inventory).prepareSlot(slot, stack);
            inventory.setItem(slot, stack.copy());
        }
    }

    private ClientViewport() {}
}
