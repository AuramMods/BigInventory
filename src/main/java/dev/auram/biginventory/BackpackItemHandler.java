package dev.auram.biginventory;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Maps overflow after the published indices of both Forge player inventory views. */
public final class BackpackItemHandler extends InvWrapper {
    private final Inventory inventory;
    private final boolean includeEquipment;

    public BackpackItemHandler(Inventory inventory, boolean includeEquipment) {
        super(inventory);
        this.inventory = inventory;
        this.includeEquipment = includeEquipment;
    }

    int inventorySlot(int slot) {
        return includeEquipment || slot < Backpack.LEGACY_END ? slot : slot + Backpack.OVERFLOW_START - Backpack.LEGACY_END;
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < getSlots();
    }

    @Override
    public int getSlots() {
        return inventory.getContainerSize() - (includeEquipment ? 0 : Backpack.OVERFLOW_START - Backpack.LEGACY_END);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return valid(slot) ? super.getStackInSlot(inventorySlot(slot)) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return isItemValid(slot, stack) ? super.insertItem(inventorySlot(slot), stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return valid(slot) && amount > 0 ? super.extractItem(inventorySlot(slot), amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (valid(slot)) super.setStackInSlot(inventorySlot(slot), stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return valid(slot) ? super.getSlotLimit(inventorySlot(slot)) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!valid(slot)) return false;
        int index = inventorySlot(slot);
        if (index >= Backpack.LEGACY_END && index < 40) {
            EquipmentSlot equipment = EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, index - Backpack.LEGACY_END);
            return !stack.isEmpty() && stack.canEquip(equipment, inventory.player);
        }
        return super.isItemValid(index, stack);
    }
}
