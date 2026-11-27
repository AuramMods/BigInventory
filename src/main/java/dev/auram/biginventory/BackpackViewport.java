package dev.auram.biginventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The bounded set of menu slots through which a player can access their backing inventory. */
public final class BackpackViewport {
    public static final int COLUMNS = 9;
    public static final int NATIVE_CELLS = 27;
    public static final int CONTAINER_CELLS = 45;
    public static final int MAX_CELLS = 54;

    private final List<Slot> cells;

    private BackpackViewport(Slot[] cells) {
        this.cells = List.copyOf(Arrays.asList(cells));
    }

    public static BackpackViewport find(AbstractContainerMenu menu, Inventory inventory) {
        Slot[] cells = new Slot[MAX_CELLS];
        for (Slot slot : menu.slots) {
            if (slot.getClass() != Slot.class || slot.container != inventory) continue;
            int index = ((SlotBinding) slot).bigInventory$originalIndex();
            if (!Backpack.isStorageSlot(index)) continue;
            int position = Backpack.positionOf(index);
            if (position >= MAX_CELLS) continue;
            if (cells[position] != null) return null;
            cells[position] = slot;
        }
        for (int i = 0; i < NATIVE_CELLS; i++) {
            if (cells[i] == null) return null;
        }
        int size = NATIVE_CELLS;
        while (size < cells.length && cells[size] != null) size++;
        return new BackpackViewport(Arrays.copyOf(cells, size));
    }

    public List<Slot> cells() {
        return cells;
    }

    public static int rowsFor(int cellCount, boolean spareRow) {
        if (cellCount == 0) return 0;
        return (cellCount - 1) / COLUMNS + (spareRow ? 2 : 1);
    }

    public boolean validates(int[] indices, Backpack backpack) {
        if (indices.length != cells.size()) return false;
        long lastRow = ((long) backpack.size() + COLUMNS - 1) / COLUMNS;
        // A filtered page may need up to one viewport of empty drop targets after the last row.
        long limit = lastRow * COLUMNS + cells.size();
        // The current page can stay open when removing the last occupied row shrinks the backing inventory.
        for (Slot cell : cells) {
            if (isBound(cell)) limit = Math.max(limit, (long) Backpack.positionOf(cell.getSlotIndex()) + 1);
        }
        Set<Integer> used = new HashSet<>();
        for (int index : indices) {
            if (index == SlotBinding.INACTIVE) continue;
            if (!Backpack.isStorageSlot(index) || Backpack.positionOf(index) >= limit || !used.add(index)) return false;
        }
        return true;
    }

    public void bind(int[] indices) {
        for (int i = 0; i < indices.length; i++) {
            ((SlotBinding) cells.get(i)).bigInventory$bind(indices[i]);
        }
    }

    public void reset() {
        for (Slot slot : cells) ((SlotBinding) slot).bigInventory$bind(SlotBinding.UNBOUND);
    }

    public static boolean isBound(Slot slot) {
        return ((SlotBinding) slot).bigInventory$binding() >= 0;
    }

    public static boolean acceptsLargeStacks(Slot slot) {
        return slot.getClass() == Slot.class && slot.container instanceof Inventory inventory
                && Backpack.isStorageSlot(slot.getSlotIndex()) && !isInactive(slot)
                && (!inventory.player.level().isClientSide || isBound(slot));
    }

    public static boolean isInactive(Slot slot) {
        return ((SlotBinding) slot).bigInventory$binding() == SlotBinding.INACTIVE;
    }
}
