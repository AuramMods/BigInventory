package dev.auram.biginventory;

import dev.auram.biginventory.StorageNetwork.*;
import dev.auram.biginventory.ViewportNetwork.AutoSort;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks only the current owner's inventory view and invalidates it with the active menu. */
final class StorageSessions {
    private static final Map<UUID, Viewer> VIEWERS = new java.util.HashMap<>();
    private static final Map<UUID, Hud> STATUS = new java.util.HashMap<>();

    static void view(ServerPlayer player, ViewRequest request) {
        if (player == null) return;
        Viewer old = VIEWERS.get(player.getUUID());
        if (!request.open()) {
            if (old != null && old.token == request.token()) {
                close(player, old);
            }
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != request.menu() || !validMenu(player, menu)) return;
        if (old != null) close(player, old);
        Viewer viewer = new Viewer(menu, request.token(), request.autoSort());
        VIEWERS.put(player.getUUID(), viewer);
        if (viewer.autoSort) Backpack.of(player.getInventory()).sort();
        viewer.refresh(player, true);
        viewer.sendPage(player);
    }

    static void remove(ServerPlayer player) {
        Viewer viewer = VIEWERS.remove(player.getUUID());
        if (viewer != null && viewer.autoSort && player.isAlive()) Backpack.of(player.getInventory()).sort();
        STATUS.remove(player.getUUID());
    }

    private static void close(ServerPlayer player, Viewer viewer) {
        VIEWERS.remove(player.getUUID());
        if (viewer.autoSort && player.isAlive()) {
            Backpack.of(player.getInventory()).sort();
            player.containerMenu.broadcastChanges();
        }
    }

    static void autoSort(ServerPlayer player, AutoSort request) {
        if (player == null) return;
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null || viewer.token != request.token() || viewer.menu.containerId != request.menu()
                || viewer.menu != player.containerMenu || !validMenu(player, viewer.menu)) return;
        if (viewer.autoSort == request.enabled()) return;
        viewer.autoSort = request.enabled();
        if (viewer.autoSort) {
            Backpack.of(player.getInventory()).sort();
            viewer.menu.broadcastChanges();
            viewer.refresh(player, false);
            viewer.sendPage(player);
        }
    }

    static void tick(ServerPlayer player, StorageWeight weight) {
        Backpack backpack = Backpack.of(player.getInventory());
        ItemStack held = player.getMainHandItem();
        long count = held.isEmpty() ? 0 : backpack.count(held);
        Hud status = new Hud(weight.used(), weight.capacity(), player.getInventory().selected, count, weight.factor(), weight.endant());
        if (!status.equals(STATUS.put(player.getUUID(), status))) StorageNetwork.send(player, status);
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null) return;
        if (viewer.menu != player.containerMenu || !validMenu(player, viewer.menu)) {
            close(player, viewer);
            return;
        }
        if (player.tickCount % 5 == 0 && viewer.sent == viewer.entries.size()) viewer.refresh(player, false);
        viewer.sendPage(player);
    }

    static void action(ServerPlayer player, Action action) {
        if (player == null) return;
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null || viewer.token != action.token() || viewer.menu != player.containerMenu
                || viewer.menu.containerId != action.menu() || !validMenu(player, viewer.menu)) {
            StorageNetwork.send(player, new ViewAck(action.token(), 0));
            return;
        }

        if (action.revision() == viewer.revision || action.gesture() == Gesture.STORE_HOTBAR) {
            Backpack store = Backpack.of(player.getInventory());
            ItemStack carried = viewer.menu.getCarried();
            switch (action.gesture()) {
                case DEPOSIT, DEPOSIT_ONE -> {
                    if (!carried.isEmpty()) {
                        store.insert(carried, action.gesture() == Gesture.DEPOSIT_ONE ? 1 : carried.getCount());
                        if (carried.isEmpty()) viewer.menu.setCarried(ItemStack.EMPTY);
                    }
                }
                case TAKE, TAKE_HALF, SHIFT_TAKE -> {
                    if (carried.isEmpty() && action.entry() >= 0 && action.entry() < viewer.sent) {
                        Entry selected = viewer.entries.get(action.entry());
                        take(player, viewer.menu, selected.icon(), action.gesture());
                    }
                }
                case BULK_TAKE -> {
                    if (carried.isEmpty() && action.entry() >= 0 && action.entry() < viewer.sent
                            && StorageTransfers.bulkFromEntry(player.getInventory(), viewer.menu, store,
                            viewer.entries.get(action.entry()).icon())) {
                        StorageTransfers.playBulkTransferSound(player);
                    }
                }
                case STORE_HOTBAR -> {
                    if (viewer.menu instanceof InventoryMenu && action.entry() >= 0 && action.entry() < viewer.menu.slots.size()) {
                        store.storeHotbarStack(viewer.menu.getSlot(action.entry()));
                    }
                }
            }
        }

        viewer.menu.broadcastChanges();
        viewer.refresh(player, false);
        viewer.sendPage(player);
        StorageNetwork.send(player, new ViewAck(viewer.token, viewer.revision));
    }

    private static void take(ServerPlayer player, AbstractContainerMenu menu, ItemStack pattern, Gesture action) {
        Inventory inventory = player.getInventory();
        Backpack store = Backpack.of(inventory);
        int offered = Backpack.stackLimit(pattern);
        switch (action) {
            case SHIFT_TAKE -> {
                if (menu instanceof InventoryMenu) {
                    store.putInHotbar(store.take(pattern, Math.min(offered, store.hotbarRoom(pattern))));
                } else {
                    StorageTransfers.moveToContainer(inventory, menu, store, pattern, offered);
                }
            }
            case TAKE_HALF -> menu.setCarried(store.take(pattern, (available(inventory, pattern, offered) + 1) / 2));
            case TAKE -> menu.setCarried(store.take(pattern, offered));
            default -> throw new IllegalArgumentException("Expected a storage withdrawal");
        }
    }

    private static int available(Inventory inventory, ItemStack pattern, int limit) {
        int count = 0;
        for (int slot = Backpack.FIRST; slot < inventory.getContainerSize() && count < limit; slot++) {
            if (Backpack.isStorageSlot(slot) && Backpack.matches(inventory.getItem(slot), pattern)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return Math.min(count, limit);
    }

    private static boolean validMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (!player.isAlive() || player.isSpectator() || !menu.stillValid(player)) return false;
        for (Slot slot : menu.slots) {
            if (Backpack.isStorageSlot(MenuPlayerSlots.index(slot, player.getInventory()))) return true;
        }
        return false;
    }

    private static final class Viewer {
        final AbstractContainerMenu menu;
        final int token;
        boolean autoSort;
        int revision;
        int sent;
        List<Entry> entries = List.of();
        List<CompoundTag> keys = List.of();

        Viewer(AbstractContainerMenu menu, int token, boolean autoSort) {
            this.menu = menu;
            this.token = token;
            this.autoSort = autoSort;
        }

        void refresh(ServerPlayer player, boolean force) {
            Inventory inv = player.getInventory();
            Map<CompoundTag, Entry> variants = new LinkedHashMap<>();
            for (int slot = Backpack.FIRST; slot < inv.getContainerSize(); slot++) {
                if (!Backpack.isStorageSlot(slot)) continue;
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) continue;
                CompoundTag key = Backpack.variantKey(stack);
                Entry existing = variants.get(key);
                long overflow = slot >= Backpack.OVERFLOW_START ? stack.getCount() : 0;
                if (existing == null) {
                    variants.put(key, new Entry(variants.size(), stack.copyWithCount(1), stack.getCount(), overflow));
                } else {
                    variants.put(key, new Entry(existing.id(), existing.icon(), existing.count() + stack.getCount(),
                            existing.overflowCount() + overflow));
                }
            }
            List<CompoundTag> nextKeys = new ArrayList<>(variants.keySet());
            List<Entry> next = new ArrayList<>(variants.values());
            if (!force && keys.equals(nextKeys) && countsMatch(next)) return;
            keys = nextKeys;
            entries = next;
            sent = 0;
            revision++;
            StorageNetwork.send(player, new ViewStart(token, revision, entries.size()));
        }

        private boolean countsMatch(List<Entry> next) {
            for (int i = 0; i < entries.size(); i++) {
                Entry previous = entries.get(i);
                Entry entry = next.get(i);
                if (previous.count() != entry.count() || previous.overflowCount() != entry.overflowCount()) return false;
            }
            return true;
        }

        void sendPage(ServerPlayer player) {
            if (sent >= entries.size()) return;
            int end = StorageNetwork.pageEnd(sent, entries.size());
            StorageNetwork.send(player, new ViewPage(token, revision, entries.subList(sent, end)));
            sent = end;
        }
    }

    private StorageSessions() {}
}
