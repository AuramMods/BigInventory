package dev.auram.biginventory;

import dev.auram.biginventory.ViewportNetwork.Access;
import dev.auram.biginventory.ViewportNetwork.CreativeTrash;
import dev.auram.biginventory.ViewportNetwork.Index;
import dev.auram.biginventory.ViewportNetwork.Page;
import dev.auram.biginventory.ViewportNetwork.PageRequest;
import dev.auram.biginventory.ViewportNetwork.Update;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Each connection can bind one active menu to its own backpack. */
final class ViewportSessions {
    private static final Map<ServerPlayer, Session> SESSIONS = new HashMap<>();
    private static final int[] UNMAPPED = {};

    static void access(ServerPlayer player, Access request) {
        if (player == null) return;
        Session previous = SESSIONS.get(player);
        if (!request.open()) {
            if (previous != null && previous.token == request.token()) close(player, previous);
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        ItemStack cursor = request.creativeCursor();
        boolean creative = cursor != null;
        if (menu.containerId != request.menu() || !valid(player, menu)
                || (creative && (!player.isCreative() || menu != player.inventoryMenu))) {
            StorageNetwork.send(player, new Page(request.token(), 0, true, UNMAPPED));
            return;
        }
        BackpackViewport slots = BackpackViewport.find(menu, player.getInventory());
        if (slots == null) {
            StorageNetwork.send(player, new Page(request.token(), 0, true, UNMAPPED));
            return;
        }
        if (previous != null) close(player, previous);
        if (creative && menu.getCarried().isEmpty() && !cursor.isEmpty()
                && cursor.getCount() <= Backpack.stackLimit(cursor) && cursor.isItemEnabled(player.level().enabledFeatures())) {
            menu.setCarried(cursor);
        }
        Session session = new Session(menu, slots, request.token(), creative);
        SESSIONS.put(player, session);
        session.sendIndex(player, true);
    }

    static void trash(ServerPlayer player, CreativeTrash request) {
        if (player == null || !player.isCreative()) return;
        Session session = SESSIONS.get(player);
        if (session == null || !session.creative || session.token != request.token() || !valid(player, session.menu)) return;
        if (request.clear()) {
            player.getInventory().clearContent();
            for (Slot slot : session.menu.slots) {
                if (slot.container != player.getInventory()) slot.setByPlayer(ItemStack.EMPTY);
            }
        } else {
            session.menu.setCarried(ItemStack.EMPTY);
        }
        session.menu.broadcastChanges();
        session.sendIndex(player, false);
    }

    static void page(ServerPlayer player, PageRequest request) {
        if (player == null) return;
        Session session = SESSIONS.get(player);
        if (session == null || session.token != request.token() || request.menu() != session.menu.containerId
                || !valid(player, session.menu)) {
            StorageNetwork.send(player, new Page(request.token(), request.sequence(), true, UNMAPPED));
            return;
        }
        if (request.sequence() <= session.sequence) return;
        session.sequence = request.sequence();
        Backpack storage = Backpack.of(player.getInventory());
        if (request.sort()) {
            if (session.mapping.length != 0 && session.menu.getCarried().isEmpty()) storage.sort();
        } else if (session.slots.validates(request.slots(), storage)) {
            ((MenuStorageAccess) session.menu).bigInventory$resetQuickCraft();
            session.slots.bind(request.slots());
            session.mapping = request.slots();
        }
        session.sendIndex(player, false);
        // Both connections process these messages in order. No native click can be sent
        // from the new page until the client has received this mapping.
        StorageNetwork.send(player, new Page(session.token, session.sequence, false, session.mapping));
        session.menu.broadcastFullState();
        StorageNetwork.send(player, new ViewportNetwork.Ready(session.token, session.sequence));
    }

    static void tick(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        if (session == null) return;
        if (valid(player, session.menu)) session.sendIndex(player, false);
        else close(player, session);
    }

    static void remove(ServerPlayer player) {
        Session session = SESSIONS.remove(player);
        if (session != null) session.slots.reset();
    }

    private static boolean valid(ServerPlayer player, AbstractContainerMenu menu) {
        return player.containerMenu == menu && player.isAlive() && !player.isSpectator() && menu.stillValid(player);
    }

    private static void close(ServerPlayer player, Session session) {
        SESSIONS.remove(player);
        if (session.creative && player.containerMenu == session.menu && player.isAlive()) session.menu.removed(player);
        session.sendIndex(player, false);
        session.slots.reset();
        ((MenuStorageAccess) session.menu).bigInventory$resetQuickCraft();
        StorageNetwork.send(player, new Page(session.token, session.sequence, true, UNMAPPED));
        if (player.containerMenu == session.menu) {
            if (session.creative) session.menu.broadcastChanges();
            else session.menu.broadcastFullState();
        }
    }

    private static final class Session {
        final AbstractContainerMenu menu;
        final BackpackViewport slots;
        final int token;
        final boolean creative;
        final Map<Integer, ItemStack> sent = new HashMap<>();
        int size;
        int sequence;
        int[] mapping = UNMAPPED;

        Session(AbstractContainerMenu menu, BackpackViewport slots, int token, boolean creative) {
            this.menu = menu;
            this.slots = slots;
            this.token = token;
            this.creative = creative;
        }

        void sendIndex(ServerPlayer player, boolean initial) {
            Inventory inventory = player.getInventory();
            int currentSize = Backpack.of(inventory).size();
            List<Update> updates = new ArrayList<>();
            for (int position = 0; position < Math.max(size, currentSize); position++) {
                int slot = Backpack.slotAt(position);
                ItemStack stack = inventory.getItem(slot);
                ItemStack previous = sent.getOrDefault(slot, ItemStack.EMPTY);
                if (ItemStack.matches(stack, previous)) continue;
                if (stack.isEmpty()) sent.remove(slot);
                else sent.put(slot, stack.copy());
                updates.add(new Update(slot, stack));
                if (updates.size() == ViewportNetwork.INDEX_BATCH_SIZE) {
                    StorageNetwork.send(player, new Index(token, false, List.copyOf(updates)));
                    updates.clear();
                }
            }
            size = currentSize;
            if (!updates.isEmpty() || initial) StorageNetwork.send(player, new Index(token, true, updates));
        }
    }

    private ViewportSessions() {}
}
