package dev.auram.biginventory;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.PlayerArmorInvWrapper;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

/** Resolves the actual player inventory index, independently of the menu's slot order. */
public final class MenuPlayerSlots {
    public static int index(Slot slot, Inventory inventory) {
        if (slot.container == inventory) return ((SlotBinding) slot).bigInventory$originalIndex();
        int index = slot.getSlotIndex();
        if (slot instanceof SlotItemHandler wrapped) {
            var handler = wrapped.getItemHandler();
            if (handler instanceof BackpackItemHandler backpack && backpack.getInv() == inventory) {
                return backpack.inventorySlot(index);
            }
            if (handler instanceof InvWrapper container && container.getInv() == inventory
                    || handler instanceof PlayerMainInvWrapper main && main.getInventoryPlayer() == inventory
                    || handler instanceof PlayerInvWrapper
                    && inventory.player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null) == handler) {
                return index;
            }
            if (handler instanceof PlayerArmorInvWrapper armor && armor.getInventoryPlayer() == inventory
                    || handler instanceof CombinedInvWrapper
                    && inventory.player.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null) == handler) {
                return Backpack.LEGACY_END + index;
            }
        }
        return -1;
    }

    private MenuPlayerSlots() {}
}
