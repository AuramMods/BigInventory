package dev.auram.biginventory;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** The first 27 cells are the legacy inventory slots. Overflow follows the equipment indices. */
public final class Backpack {
    public static final int FIRST = 9;
    public static final int LEGACY_END = 36;
    public static final int OVERFLOW_START = 41;
    public static final String SAVE_KEY = "biginventory:backpack";
    private static final int SAVE_VERSION = 2;

    private final Inventory inventory;
    private final NonNullList<ItemStack> overflow;
    private StorageNetwork.Hud predictionRules;

    public Backpack(Inventory inventory, NonNullList<ItemStack> overflow) {
        this.inventory = inventory;
        this.overflow = overflow;
    }

    public static Backpack of(Inventory inventory) {
        return ((BackpackAccess) inventory).bigInventory$backpack();
    }

    public static boolean isStorageSlot(int slot) {
        return (slot >= FIRST && slot < LEGACY_END) || slot >= OVERFLOW_START;
    }

    public static int slotAt(int position) {
        return position < LEGACY_END - FIRST ? FIRST + position
                : OVERFLOW_START + position - (LEGACY_END - FIRST);
    }

    public static int positionOf(int slot) {
        return slot < LEGACY_END ? slot - FIRST : slot - OVERFLOW_START + (LEGACY_END - FIRST);
    }

    public int size() {
        return LEGACY_END - FIRST + overflow.size();
    }

    /** A vacant cell can be exposed before it needs a backing ItemStack. */
    public void prepareSlot(int slot, ItemStack stack) {
        if (slot < OVERFLOW_START || stack.isEmpty()) return;
        int index = slot - OVERFLOW_START;
        while (overflow.size() <= index) overflow.add(ItemStack.EMPTY);
    }

    public static int footprint(int factor, int stackMax) {
        return 1 + (factor - 1) / Math.max(1, stackMax);
    }

    public static int stackLimit(ItemStack stack) {
        return Math.max(1, Math.min(64, stack.getMaxStackSize()));
    }

    public static boolean matches(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b);
    }

    public static CompoundTag variantKey(ItemStack stack) {
        CompoundTag key = stack.save(new CompoundTag());
        key.remove("Count");
        return key;
    }

    public long capacity() {
        return StorageEquipment.equipped(inventory.player).capacity(StorageConfig.BASE_CAPACITY.get());
    }

    public long weight(ItemStack stack) {
        return stack.isEmpty() ? 0
                : (long) stack.getCount() * footprint(StorageConfig.FOOTPRINT_FACTOR.get(), stack.getMaxStackSize());
    }

    public long usedWeight() {
        return weightBudget().used();
    }

    public int slotLimit(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) return stackLimit(incoming);
        return slotLimit(slot, incoming, true);
    }

    public int replacementLimit(int slot, ItemStack stack) {
        return slotLimit(slot, stack, false);
    }

    private int slotLimit(int slot, ItemStack incoming, boolean merge) {
        ItemStack current = inventory.getItem(slot);
        StorageWeight budget = weightBudget();
        if (budget.used() > budget.capacity()) {
            return merge && matches(current, incoming) ? current.getCount() : 0;
        }
        budget.change(current, -current.getCount());
        return budget.fit(incoming, stackLimit(incoming));
    }

    StorageWeight weightBudget() {
        if (predictionRules != null) {
            return new StorageWeight(inventory, predictionRules.capacity(), predictionRules.footprintFactor(),
                    predictionRules.endant());
        }
        return new StorageWeight(inventory, StorageEquipment.equipped(inventory.player));
    }

    public void setPredictionRules(StorageNetwork.Hud rules) {
        predictionRules = rules;
    }

    /** Inserts into a specific slot while leaving the mutable input as the remainder. */
    public int insertAt(int slot, ItemStack input, int requested) {
        if (input.isEmpty() || requested <= 0) return 0;
        ItemStack current = inventory.getItem(slot);
        if (current == input || (!current.isEmpty() && !matches(current, input))) return 0;
        int limit = isStorageSlot(slot) ? slotLimit(slot, input) : stackLimit(input);
        return put(slot, input, Math.min(requested, limit - current.getCount()));
    }

    public int insert(ItemStack input, int requested) {
        if (input.isEmpty() || requested <= 0) return 0;
        int fit = weightBudget().fit(input, Math.min(requested, input.getCount()));
        if (fit == 0) return 0;
        store(input, fit);
        return fit;
    }

    /** Return the unused portion of an already-withdrawn stack, including while over capacity. */
    void returnTransferRemainder(ItemStack remainder) {
        if (!remainder.isEmpty()) store(remainder, remainder.getCount());
    }

    private void store(ItemStack input, int count) {
        int remaining = count;
        int size = inventory.getContainerSize();
        for (int slot = FIRST; slot < size && remaining > 0; slot++) {
            if (isStorageSlot(slot)) {
                ItemStack current = inventory.getItem(slot);
                if (current != input && matches(current, input) && current.isStackable()) {
                    remaining -= put(slot, input, Math.min(remaining, stackLimit(current) - current.getCount()));
                }
            }
        }
        for (int slot = FIRST; slot < size && remaining > 0; slot++) {
            if (isStorageSlot(slot) && inventory.getItem(slot).isEmpty()) {
                remaining -= put(slot, input, Math.min(remaining, stackLimit(input)));
            }
        }
        while (remaining > 0) {
            ItemStack stack = input.split(Math.min(remaining, stackLimit(input)));
            overflow.add(stack);
            remaining -= stack.getCount();
        }
        inventory.setChanged();
    }

    public void storeHotbarStack(Slot slot) {
        if (slot.container != inventory || !Inventory.isHotbarSlot(slot.getSlotIndex()) || !slot.mayPickup(inventory.player)) return;
        ItemStack stack = slot.getItem();
        if (insert(stack, stack.getCount()) > 0) {
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            slot.onTake(inventory.player, stack);
        }
    }

    private int put(int slot, ItemStack input, int count) {
        int moved = Math.min(input.getCount(), count);
        if (moved <= 0) return 0;
        ItemStack current = inventory.getItem(slot);
        if (current.isEmpty()) {
            inventory.setItem(slot, input.split(moved));
        } else {
            input.shrink(moved);
            current.grow(moved);
        }
        inventory.setChanged();
        return moved;
    }

    /** Insert into an explicit slot or choose a destination for an untargeted addition. */
    public int add(int slot, ItemStack input, boolean preferStored) {
        int initial = input.getCount();
        if (slot != -1) {
            if (slot >= 0 && slot < inventory.getContainerSize()) insertAt(slot, input, initial);
            return initial - input.getCount();
        }

        boolean storageFirst = preferStored
                && inventory.items.subList(0, FIRST).stream().noneMatch(stack -> matches(stack, input))
                && !find(stack -> matches(stack, input)).isEmpty();
        if (storageFirst) insert(input, initial);

        insertMatchingHotbar(input);
        for (int i = 0; i < FIRST && !input.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) insertAt(i, input, input.getCount());
        }
        if (!storageFirst) insert(input, input.getCount());
        return initial - input.getCount();
    }

    public int insertMatchingHotbar(ItemStack input) {
        int initial = input.getCount();
        if (matches(inventory.getSelected(), input)) insertAt(inventory.selected, input, input.getCount());
        if (matches(inventory.getItem(40), input)) insertAt(40, input, input.getCount());
        for (int i = 0; i < FIRST && !input.isEmpty(); i++) {
            if (i != inventory.selected && matches(inventory.items.get(i), input)) insertAt(i, input, input.getCount());
        }
        return initial - input.getCount();
    }

    public int hotbarRoom(ItemStack pattern) {
        int room = 0;
        for (int i = 0; i < FIRST; i++) {
            ItemStack current = inventory.items.get(i);
            if (current.isEmpty()) room += stackLimit(pattern);
            else if (matches(current, pattern) && current.isStackable()) {
                room += Math.max(0, stackLimit(current) - current.getCount());
            }
        }
        return room;
    }

    public void putInHotbar(ItemStack input) {
        if (matches(inventory.getSelected(), input)) insertAt(inventory.selected, input, input.getCount());
        for (int i = 0; i < FIRST && !input.isEmpty(); i++) {
            if (i != inventory.selected && matches(inventory.items.get(i), input)) insertAt(i, input, input.getCount());
        }
        for (int i = 0; i < FIRST && !input.isEmpty(); i++) {
            if (inventory.items.get(i).isEmpty()) insertAt(i, input, input.getCount());
        }
    }

    public ItemStack find(Predicate<ItemStack> match) {
        for (int slot = FIRST; slot < inventory.getContainerSize(); slot++) {
            if (isStorageSlot(slot)) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty() && match.test(stack)) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack take(ItemStack pattern, int requested) {
        int remaining = Math.min(requested, stackLimit(pattern));
        ItemStack taken = ItemStack.EMPTY;
        for (int slot = FIRST; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            if (isStorageSlot(slot) && matches(inventory.getItem(slot), pattern)) {
                ItemStack part = inventory.removeItem(slot, remaining);
                remaining -= part.getCount();
                if (taken.isEmpty()) taken = part;
                else taken.grow(part.getCount());
            }
        }
        if (!taken.isEmpty()) inventory.setChanged();
        return taken;
    }

    public long count(ItemStack pattern) {
        long count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot < FIRST || isStorageSlot(slot)) {
                ItemStack stack = inventory.getItem(slot);
                if (matches(stack, pattern)) count += stack.getCount();
            }
        }
        return count;
    }

    /** Check a batch of crafting-grid returns against simulated hotbar fills and one weight budget. */
    public boolean canReturn(Iterable<ItemStack> stacks) {
        ItemStack[] hotbar = new ItemStack[FIRST + 1];
        for (int i = 0; i < FIRST; i++) hotbar[i] = inventory.items.get(i).copy();
        hotbar[FIRST] = inventory.getItem(40).copy();
        StorageWeight budget = weightBudget();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            int count = stack.getCount();
            for (ItemStack current : hotbar) {
                if (matches(current, stack)) {
                    int move = Math.min(count, Math.max(0, stackLimit(current) - current.getCount()));
                    current.grow(move);
                    count -= move;
                }
            }
            for (int i = 0; i < FIRST && count > 0; i++) {
                if (hotbar[i].isEmpty()) {
                    int move = Math.min(count, stackLimit(stack));
                    hotbar[i] = stack.copyWithCount(move);
                    count -= move;
                }
            }
            if (budget.fit(stack, count) < count) return false;
            budget.change(stack, count);
        }
        return true;
    }

    public void trimEmptyTail() {
        while (!overflow.isEmpty() && overflow.get(overflow.size() - 1).isEmpty()) {
            overflow.remove(overflow.size() - 1);
        }
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        data.putInt("Version", SAVE_VERSION);
        ListTag stacks = new ListTag();
        for (int slot = 0; slot < overflow.size(); slot++) {
            ItemStack stack = overflow.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = stack.save(new CompoundTag());
            entry.putInt("Slot", slot);
            stacks.add(entry);
        }
        data.put("Overflow", stacks);
        return data;
    }

    public void load(CompoundTag data) {
        overflow.clear();
        ListTag stacks = data.getList("Overflow", Tag.TAG_COMPOUND);
        boolean indexed = data.getInt("Version") >= SAVE_VERSION;
        for (int i = 0; i < stacks.size(); i++) {
            CompoundTag entry = stacks.getCompound(i);
            ItemStack stack = ItemStack.of(entry);
            if (stack.isEmpty()) continue;
            // Version 1 already compacted its overflow. Keep that order on migration.
            int slot = indexed && entry.contains("Slot", Tag.TAG_INT) ? entry.getInt("Slot") : overflow.size();
            if (slot < 0 || slot >= Integer.MAX_VALUE - OVERFLOW_START
                    || (slot < overflow.size() && !overflow.get(slot).isEmpty())) {
                slot = overflow.size();
            }
            while (overflow.size() <= slot) overflow.add(ItemStack.EMPTY);
            overflow.set(slot, stack);
        }
    }

    public void copyOverflow(Backpack source) {
        if (source == this) return;
        overflow.clear();
        for (ItemStack stack : source.overflow) overflow.add(stack.copy());
    }
}
