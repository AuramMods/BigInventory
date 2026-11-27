package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.StorageNetwork.Hud;
import dev.auram.biginventory.StorageTransfers;
import dev.auram.biginventory.StorageWeight;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A display-only estimate for a single request. Native slots and the menu cursor are server-controlled. */
record StoragePrediction(List<Entry> entries, @Nullable ItemStack cursor, Map<Slot, ItemStack> slots,
                         @Nullable Transfer transfer) {
    StoragePrediction(List<Entry> entries, @Nullable ItemStack cursor) {
        this(entries, cursor, Map.of(), null);
    }

    static StoragePrediction preview(List<Entry> entries, Gesture gesture, int index, AbstractContainerMenu menu,
                                     Inventory inventory, @Nullable Hud rules) {
        ItemStack carried = menu.getCarried();
        return switch (gesture) {
            case DEPOSIT, DEPOSIT_ONE -> deposit(entries, carried, gesture == Gesture.DEPOSIT_ONE ? 1 : carried.getCount(), rules, null);
            case STORE_HOTBAR -> {
                Slot hotbar = menu.getSlot(index);
                ItemStack stack = hotbar.getItem();
                yield deposit(entries, stack, stack.getCount(), rules, hotbar);
            }
            case TAKE, TAKE_HALF, SHIFT_TAKE -> withdraw(entries, gesture, index, menu, inventory);
            case BULK_TAKE -> new StoragePrediction(entries, null);
        };
    }

    private static StoragePrediction deposit(List<Entry> entries, ItemStack stack, int requested,
                                             @Nullable Hud rules, @Nullable Slot source) {
        if (rules == null || stack.isEmpty()) return new StoragePrediction(entries, null);
        var key = Backpack.variantKey(stack);
        int index = -1;
        double used = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            used += StorageWeight.variantWeight(rules.footprintFactor(), entry.icon().getMaxStackSize(), entry.count(), rules.endant());
            if (entry.icon().is(stack.getItem()) && key.equals(Backpack.variantKey(entry.icon()))) index = i;
        }
        long count = index < 0 ? 0 : entries.get(index).count();
        int added = StorageWeight.fit(rules.capacity(), used, rules.footprintFactor(), stack.getMaxStackSize(),
                count, requested, rules.endant());
        if (added == 0) return new StoragePrediction(entries, null);

        List<Entry> result = new ArrayList<>(entries);
        Entry entry = index < 0 ? new Entry(-1, stack.copyWithCount(1), added, 0L) : entries.get(index);
        Entry updated = new Entry(entry.id(), entry.icon(), count + added, entry.overflowCount());
        if (index < 0) result.add(updated);
        else result.set(index, updated);
        ItemStack remainder = stack.copyWithCount(stack.getCount() - added);
        Transfer transfer = new Transfer(updated, added);
        return source == null ? new StoragePrediction(List.copyOf(result), remainder, Map.of(), transfer)
                : new StoragePrediction(List.copyOf(result), null, Map.of(source, remainder), transfer);
    }

    private static StoragePrediction withdraw(List<Entry> entries, Gesture gesture, int index, AbstractContainerMenu menu,
                                              Inventory inventory) {
        Entry entry = entries.get(index);
        int available = (int) Math.min(entry.count(), Backpack.stackLimit(entry.icon()));
        int moved = gesture == Gesture.TAKE_HALF ? (available + 1) / 2 : available;
        Map<Slot, ItemStack> slots = Map.of();
        if (gesture == Gesture.SHIFT_TAKE) {
            // A terminal's quick-move handler can deposit into storage that the client cannot inspect.
            if (!(menu instanceof InventoryMenu)) return new StoragePrediction(entries, null);
            ItemStack proposal = entry.icon().copyWithCount(available);
            slots = StorageTransfers.previewToHotbar(inventory, menu, proposal);
            moved -= proposal.getCount();
        }
        if (moved == 0) return new StoragePrediction(entries, null);

        List<Entry> result = new ArrayList<>(entries);
        long remaining = entry.count() - moved;
        if (remaining == 0) result.remove(index);
        else result.set(index, new Entry(entry.id(), entry.icon(), remaining, entry.overflowCount()));
        ItemStack cursor = gesture == Gesture.SHIFT_TAKE ? null : entry.icon().copyWithCount(moved);
        return new StoragePrediction(List.copyOf(result), cursor, slots, new Transfer(entry, -moved));
    }

    // Deposits refer to the resulting entry. Withdrawals refer to the original entry.
    record Transfer(Entry entry, int delta) {
        long expectedCount() { return entry.count() + Math.min(0, delta); }
    }
}
