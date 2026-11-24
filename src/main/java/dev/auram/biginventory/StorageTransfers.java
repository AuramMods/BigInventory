package dev.auram.biginventory;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class StorageTransfers {
    private static final Logger LOGGER = LogUtils.getLogger();

    static void moveToContainer(Inventory inventory, AbstractContainerMenu menu, Backpack storage,
                                ItemStack pattern, int limit) {
        var player = inventory.player;
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.getSlot(index);
            int inventorySlot = MenuPlayerSlots.index(slot, inventory);
            if (!Backpack.isStorageSlot(inventorySlot) || !slot.isActive() || !slot.mayPickup(player)) continue;

            ItemStack offered = storage.take(pattern, limit);
            if (offered.isEmpty()) return;
            int menuIndex = index;
            ((MenuStorageAccess) menu).bigInventory$runStorageExport(inventory, () -> {
                // Keep the original Slot object and menu index for menus that classify their own slots.
                ItemStack original = inventory.getItem(inventorySlot);
                inventory.setItem(inventorySlot, offered);
                try {
                    if (slot.mayPickup(player)) menu.quickMoveStack(player, menuIndex);
                } catch (RuntimeException failure) {
                    LOGGER.warn("Storage quick-move failed in {}", menu.getClass().getName(), failure);
                } finally {
                    // Some terminals return EMPTY even on success. The source holds the actual remainder.
                    ItemStack remainder = inventory.getItem(inventorySlot);
                    inventory.setItem(inventorySlot, original);
                    storage.returnTransferRemainder(remainder);
                }
            });
            return;
        }
    }

    public static Map<Slot, ItemStack> previewToHotbar(Inventory inventory, AbstractContainerMenu menu, ItemStack proposal) {
        Map<Slot, ItemStack> preview = new HashMap<>();
        Slot[] hotbar = new Slot[Inventory.getSelectionSize()];
        for (Slot slot : menu.slots) {
            int index = MenuPlayerSlots.index(slot, inventory);
            if (Inventory.isHotbarSlot(index)) hotbar[index] = slot;
        }
        Slot selected = hotbar[inventory.selected];
        if (selected != null && Backpack.matches(selected.getItem(), proposal)) previewInsert(selected, proposal, preview);
        for (Slot slot : hotbar) {
            if (slot != null && slot != selected && Backpack.matches(slot.getItem(), proposal)) previewInsert(slot, proposal, preview);
        }
        for (Slot slot : hotbar) {
            if (proposal.isEmpty()) break;
            if (slot != null && !slot.hasItem()) previewInsert(slot, proposal, preview);
        }
        return Map.copyOf(preview);
    }

    private static void previewInsert(Slot slot, ItemStack proposal, Map<Slot, ItemStack> preview) {
        if (!slot.mayPlace(proposal)) return;
        ItemStack current = preview.getOrDefault(slot, slot.getItem());
        int moved;
        if (slot instanceof SlotItemHandler wrapped) {
            int offered = Math.min(proposal.getCount(), slot.getMaxStackSize() - current.getCount());
            if (offered <= 0) return;
            ItemStack remainder = wrapped.getItemHandler().insertItem(slot.getSlotIndex(), proposal.copyWithCount(offered), true);
            moved = offered - remainder.getCount();
        } else {
            moved = Math.min(proposal.getCount(), slot.getMaxStackSize(proposal) - current.getCount());
        }
        if (moved <= 0) return;
        preview.put(slot, (current.isEmpty() ? proposal : current).copyWithCount(current.getCount() + moved));
        proposal.shrink(moved);
    }

    private StorageTransfers() {}
}
