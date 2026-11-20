package dev.auram.biginventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** A storage snapshot that can also account for proposed changes without touching the items. */
final class StorageWeight {
    private final int factor = StorageConfig.FOOTPRINT_FACTOR.get();
    private final boolean endant;
    private final Map<Variant, Long> quantities;
    private final long capacity;
    private double weight;

    StorageWeight(Inventory inventory, StorageEquipment equipment) {
        endant = equipment.endant();
        quantities = endant ? new HashMap<>() : Map.of();
        capacity = equipment.capacity(StorageConfig.BASE_CAPACITY.get());
        for (int slot = Backpack.FIRST; slot < inventory.getContainerSize(); slot++) {
            if (Backpack.isStorageSlot(slot)) {
                ItemStack stack = inventory.getItem(slot);
                change(stack, stack.getCount());
            }
        }
    }

    long used() { return (long) Math.floor(weight); }

    long capacity() { return capacity; }

    void change(ItemStack stack, int count) {
        if (count == 0 || stack.isEmpty()) return;
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        int unit = Backpack.footprint(factor, maxStackSize);
        if (!endant) {
            weight += (double) count * unit;
            return;
        }

        Variant variant = new Variant(Backpack.variantKey(stack), maxStackSize);
        long oldCount = quantities.getOrDefault(variant, 0L);
        long newCount = oldCount + count;
        quantities.put(variant, newCount);
        long fullPriceChange = Math.min(newCount, maxStackSize) - Math.min(oldCount, maxStackSize);
        weight += (count + fullPriceChange) * (unit / 2.0);
    }

    int fit(ItemStack stack, int limit) {
        if (limit <= 0 || used() > capacity) return 0;
        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        int unit = Backpack.footprint(factor, maxStackSize);
        if (!endant) return (int) Math.min(limit, (capacity - used()) / unit);

        long currentCount = quantities.getOrDefault(new Variant(Backpack.variantKey(stack), maxStackSize), 0L);
        long fullPriceRemaining = Math.max(0, maxStackSize - currentCount);
        int low = 0;
        int high = limit;
        while (low < high) {
            int count = low + (int) (((long) high - low + 1) / 2);
            double addedWeight = (count + Math.min(count, fullPriceRemaining)) * (unit / 2.0);
            if (Math.floor(weight + addedWeight) <= capacity) low = count;
            else high = count - 1;
        }
        return low;
    }

    private record Variant(CompoundTag key, int maxStackSize) {}
}
