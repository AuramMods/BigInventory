package dev.auram.biginventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StorageTransfers {
    static void moveToContainer(Inventory inventory, AbstractContainerMenu menu, ItemStack proposal) {
        moveToContainer(inventory, menu, proposal, null);
    }

    public static Map<Slot, ItemStack> previewToContainer(Inventory inventory, AbstractContainerMenu menu, ItemStack proposal) {
        Map<Slot, ItemStack> preview = new HashMap<>();
        moveToContainer(inventory, menu, proposal, preview);
        return Map.copyOf(preview);
    }

    public static Map<Slot, ItemStack> previewToHotbar(Inventory inventory, AbstractContainerMenu menu, ItemStack proposal) {
        Map<Slot, ItemStack> preview = new HashMap<>();
        Slot[] hotbar = new Slot[Inventory.getSelectionSize()];
        for (Slot slot : menu.slots) {
            int index = MenuPlayerSlots.index(slot, inventory);
            if (Inventory.isHotbarSlot(index)) hotbar[index] = slot;
        }
        Slot selected = hotbar[inventory.selected];
        if (selected != null && Backpack.matches(selected.getItem(), proposal)) insertInto(selected, proposal, preview);
        for (Slot slot : hotbar) {
            if (slot != null && slot != selected && Backpack.matches(slot.getItem(), proposal)) insertInto(slot, proposal, preview);
        }
        for (Slot slot : hotbar) {
            if (proposal.isEmpty()) break;
            if (slot != null && !slot.hasItem()) insertInto(slot, proposal, preview);
        }
        return Map.copyOf(preview);
    }

    /** Leaves the unaccepted items in the proposal. A preview records changes without mutating any slots. */
    private static void moveToContainer(Inventory inventory, AbstractContainerMenu menu, ItemStack proposal,
                                        @Nullable Map<Slot, ItemStack> preview) {
        List<Slot> targets = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.isActive() && MenuPlayerSlots.index(slot, inventory) < 0) targets.add(slot);
        }
        for (Slot slot : targets) {
            if (proposal.isEmpty()) return;
            if (Backpack.matches(slot.getItem(), proposal)) insertInto(slot, proposal, preview);
        }
        for (Slot slot : targets) {
            if (proposal.isEmpty()) return;
            if (!slot.hasItem()) insertInto(slot, proposal, preview);
        }
    }

    private static void insertInto(Slot slot, ItemStack proposal, @Nullable Map<Slot, ItemStack> preview) {
        if (!slot.mayPlace(proposal)) return;
        ItemStack current = preview == null ? slot.getItem() : preview.getOrDefault(slot, slot.getItem());
        int moved;
        if (slot instanceof SlotItemHandler wrapped) {
            int offered = Math.min(proposal.getCount(), slot.getMaxStackSize() - current.getCount());
            if (offered <= 0) return;
            ItemStack remainder = wrapped.getItemHandler().insertItem(slot.getSlotIndex(), proposal.copyWithCount(offered), preview != null);
            moved = offered - remainder.getCount();
        } else {
            moved = Math.min(proposal.getCount(), slot.getMaxStackSize(proposal) - current.getCount());
            if (moved > 0 && preview == null) slot.setByPlayer(combined(current, proposal, moved));
        }
        if (moved <= 0) return;
        if (preview != null) preview.put(slot, combined(current, proposal, moved));
        proposal.shrink(moved);
    }

    private static ItemStack combined(ItemStack current, ItemStack incoming, int moved) {
        return (current.isEmpty() ? incoming : current).copyWithCount(current.getCount() + moved);
    }

    private StorageTransfers() {}
}
