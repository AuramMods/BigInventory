package dev.auram.biginventory;

import dev.auram.biginventory.StorageNetwork.*;
import dev.auram.biginventory.mixin.MenuTransfer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
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
            if (old != null && old.token == request.token()) VIEWERS.remove(player.getUUID());
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != request.menu() || !validMenu(player, menu)) return;
        Viewer viewer = new Viewer(menu, request.token());
        VIEWERS.put(player.getUUID(), viewer);
        viewer.refresh(player, true);
        viewer.sendPage(player);
    }

    static void remove(ServerPlayer player) {
        VIEWERS.remove(player.getUUID());
        STATUS.remove(player.getUUID());
    }

    static void tick(ServerPlayer player, long used, long capacity) {
        Backpack backpack = Backpack.of(player.getInventory());
        ItemStack held = player.getMainHandItem();
        long count = held.isEmpty() ? 0 : backpack.count(held);
        Hud status = new Hud(used, capacity, player.getInventory().selected, count);
        if (!status.equals(STATUS.put(player.getUUID(), status))) StorageNetwork.send(player, status);
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null) return;
        if (viewer.menu != player.containerMenu || !validMenu(player, viewer.menu)) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        if (player.tickCount % 5 == 0 && viewer.sent == viewer.entries.size()) viewer.refresh(player, false);
        viewer.sendPage(player);
    }

    static void action(ServerPlayer player, Action action) {
        if (player == null) return;
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer == null || viewer.token != action.token() || viewer.menu != player.containerMenu
                || viewer.menu.containerId != action.menu() || !validMenu(player, viewer.menu)) return;

        if (action.revision() == viewer.revision) {
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
            }
        }

        viewer.menu.broadcastChanges();
        viewer.refresh(player, false);
        viewer.sendPage(player);
        StorageNetwork.send(player, new ViewAck(viewer.token, viewer.revision));
    }

    private static void take(ServerPlayer player, AbstractContainerMenu menu, ItemStack pattern, Gesture action) {
        Backpack store = Backpack.of(player.getInventory());
        int offered = Backpack.stackLimit(pattern);
        if (action == Gesture.SHIFT_TAKE && menu instanceof ChestMenu chest) {
            // Try the vanilla chest destination first. Withdraw only what it actually accepted.
            int available = 0;
            Inventory inv = player.getInventory();
            for (int slot = Backpack.FIRST; slot < inv.getContainerSize() && available < offered; slot++) {
                if (Backpack.isStorageSlot(slot) && Backpack.matches(inv.getItem(slot), pattern)) {
                    available += inv.getItem(slot).getCount();
                }
            }
            if (available == 0) return;
            ItemStack proposal = pattern.copyWithCount(Math.min(offered, available));
            int before = proposal.getCount();
            ((MenuTransfer) chest).bigInventory$moveTo(proposal, 0, chest.getRowCount() * 9, false);
            store.take(pattern, before - proposal.getCount());
        } else if (action == Gesture.SHIFT_TAKE) {
            store.putInHotbar(store.take(pattern, Math.min(offered, store.hotbarRoom(pattern))));
        } else {
            if (action == Gesture.TAKE_HALF) {
                int available = 0;
                Inventory inv = player.getInventory();
                for (int slot = Backpack.FIRST; slot < inv.getContainerSize() && available < offered; slot++) {
                    if (Backpack.isStorageSlot(slot) && Backpack.matches(inv.getItem(slot), pattern)) {
                        available += inv.getItem(slot).getCount();
                    }
                }
                offered = (Math.min(available, offered) + 1) / 2;
            }
            menu.setCarried(store.take(pattern, offered));
        }
    }

    private static boolean validMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (!player.isAlive() || player.isSpectator() || !menu.stillValid(player)) return false;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && Backpack.isStorageSlot(slot.getSlotIndex())) return true;
        }
        return false;
    }

    private static final class Viewer {
        final AbstractContainerMenu menu;
        final int token;
        int revision;
        int sent;
        List<Entry> entries = List.of();
        List<CompoundTag> keys = List.of();

        Viewer(AbstractContainerMenu menu, int token) {
            this.menu = menu;
            this.token = token;
        }

        void refresh(ServerPlayer player, boolean force) {
            Inventory inv = player.getInventory();
            Map<CompoundTag, Entry> variants = new LinkedHashMap<>();
            for (int slot = Backpack.FIRST; slot < inv.getContainerSize(); slot++) {
                if (!Backpack.isStorageSlot(slot)) continue;
                ItemStack stack = inv.getItem(slot);
                if (stack.isEmpty()) continue;
                CompoundTag key = stack.save(new CompoundTag());
                key.remove("Count");
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
