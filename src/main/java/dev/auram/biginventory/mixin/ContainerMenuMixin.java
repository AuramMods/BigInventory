package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackViewport;
import dev.auram.biginventory.MenuPlayerSlots;
import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.SlotBinding;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin implements MenuStorageAccess {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow protected abstract Slot addSlot(Slot slot);
    @Shadow protected abstract void resetQuickCraft();
    @Shadow protected abstract boolean moveItemStackTo(ItemStack stack, int from, int to, boolean reverse);
    @Unique private Inventory bigInventory$exportingInventory;

    @Override
    public void bigInventory$reserveViewportSlots(Inventory inventory) {
        for (int i = BackpackViewport.NATIVE_CELLS; i < BackpackViewport.MAX_CELLS; i++) {
            Slot cell = new Slot(inventory, Backpack.slotAt(i), -30000, -30000);
            ((SlotBinding) cell).bigInventory$reserve();
            addSlot(cell);
        }
    }

    @Override
    public void bigInventory$resetQuickCraft() {
        resetQuickCraft();
    }

    @Override
    public void bigInventory$runStorageExport(Inventory inventory, Runnable transfer) {
        Inventory previous = bigInventory$exportingInventory;
        bigInventory$exportingInventory = inventory;
        try {
            transfer.run();
        } finally {
            bigInventory$exportingInventory = previous;
        }
    }

    @Inject(method = {"broadcastChanges", "broadcastFullState", "sendAllDataToRemote"},
            at = @At("HEAD"), cancellable = true)
    private void bigInventory$deferStorageExportSync(CallbackInfo ci) {
        if (bigInventory$exportingInventory != null) ci.cancel();
    }

    @Inject(method = "doClick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true)
    private void bigInventory$storeHotbarStack(int slotIndex, int button, ClickType click, Player player, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryMenu)) return;
        Inventory inventory = player.getInventory();
        Slot slot = slots.get(slotIndex);
        if (slot.container != inventory) return;
        if (Inventory.isHotbarSlot(slot.getSlotIndex())) {
            if (((SlotBinding) slots.get(InventoryMenu.INV_SLOT_START)).bigInventory$binding() != SlotBinding.UNBOUND) {
                ItemStack stack = slot.getItem();
                int original = stack.getCount();
                BackpackViewport viewport = BackpackViewport.find((InventoryMenu) (Object) this, inventory);
                if (viewport != null) bigInventory$fillVisibleSlots(stack, viewport.cells());
                if (!stack.isEmpty() && !player.level().isClientSide) Backpack.of(inventory).insert(stack, stack.getCount());
                if (original != stack.getCount()) {
                    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                    else slot.setChanged();
                    slot.onTake(player, stack);
                }
            } else if (!player.level().isClientSide) {
                Backpack.of(inventory).storeHotbarStack(slot);
            }
            ci.cancel();
        } else if (BackpackViewport.isBound(slot)) {
            ItemStack stack = slot.getItem();
            if (moveItemStackTo(stack, InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, false)) {
                if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                else slot.setChanged();
                slot.onTake(player, stack);
            }
            ci.cancel();
        }
    }

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void bigInventory$protectStorageSwap(int slotIndex, int button, ClickType click, Player player, CallbackInfo ci) {
        if (slotIndex >= 0 && slotIndex < slots.size() && BackpackViewport.isInactive(slots.get(slotIndex))) {
            ci.cancel();
            return;
        }
        if (click != ClickType.SWAP) return;
        if (!Inventory.isHotbarSlot(button) && button != 40) {
            ci.cancel();
            return;
        }
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        Slot slot = slots.get(slotIndex);
        ItemStack incoming = player.getInventory().getItem(button);
        // A partial swap of an occupied backpack slot would displace the original stack.
        if (slot.container == player.getInventory() && Backpack.isStorageSlot(slot.getSlotIndex())
                && (!player.level().isClientSide || BackpackViewport.isBound(slot)) && slot.hasItem() && !incoming.isEmpty()
                && incoming.getCount() > Backpack.of(player.getInventory()).replacementLimit(slot.getSlotIndex(), incoming)) {
            ci.cancel();
        }
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void bigInventory$quickMove(ItemStack input, int from, int to, boolean reverse,
                                        CallbackInfoReturnable<Boolean> cir) {
        Inventory exporting = bigInventory$exportingInventory;
        Inventory destination = null;
        boolean filterPlayerSlots = false;
        for (int i = from; i < to; i++) {
            Slot slot = slots.get(i);
            if (exporting != null) {
                if (MenuPlayerSlots.index(slot, exporting) >= 0) {
                    filterPlayerSlots = true;
                    break;
                }
            } else if (slot.container instanceof Inventory inv
                    && (!inv.player.level().isClientSide
                        || ((SlotBinding) slot).bigInventory$binding() != SlotBinding.UNBOUND)
                    && Backpack.isStorageSlot(slot.getSlotIndex())) {
                destination = inv;
                break;
            }
        }
        if (destination == null && !filterPlayerSlots) return;

        int original = input.getCount();
        int start = reverse ? to - 1 : from;
        int end = reverse ? from - 1 : to;
        int step = reverse ? -1 : 1;
        for (int i = start; i != end && !input.isEmpty() && input.isStackable(); i += step) {
            Slot slot = slots.get(i);
            if (filterPlayerSlots && MenuPlayerSlots.index(slot, exporting) >= 0) continue;
            if (slot.getItem() != input && Backpack.matches(slot.getItem(), input)) bigInventory$moveToSlot(input, slot);
        }
        for (int i = start; i != end && !input.isEmpty(); i += step) {
            Slot slot = slots.get(i);
            if (filterPlayerSlots && MenuPlayerSlots.index(slot, exporting) >= 0) continue;
            if (slot.getItem() != input && slot.getItem().isEmpty()) bigInventory$moveToSlot(input, slot);
        }
        if (destination != null && !destination.player.level().isClientSide && !input.isEmpty()) {
            boolean sourceInBackpack = false;
            for (int i = Backpack.FIRST; i < destination.getContainerSize(); i++) {
                if (Backpack.isStorageSlot(i) && destination.getItem(i) == input) {
                    sourceInBackpack = true;
                    break;
                }
            }
            if (!sourceInBackpack) Backpack.of(destination).insert(input, input.getCount());
        }
        cir.setReturnValue(original != input.getCount());
    }

    @Unique
    private static void bigInventory$fillVisibleSlots(ItemStack input, List<Slot> cells) {
        for (Slot slot : cells) {
            if (input.isEmpty()) return;
            if (Backpack.matches(slot.getItem(), input)) bigInventory$moveToSlot(input, slot);
        }
        for (Slot slot : cells) {
            if (input.isEmpty()) return;
            if (slot.getItem().isEmpty()) bigInventory$moveToSlot(input, slot);
        }
    }

    @Unique
    private static void bigInventory$moveToSlot(ItemStack input, Slot slot) {
        if (!slot.mayPlace(input)) return;
        ItemStack current = slot.getItem();
        int count = Math.min(input.getCount(), slot.getMaxStackSize(input) - current.getCount());
        if (count <= 0) return;
        if (current.isEmpty()) slot.setByPlayer(input.split(count));
        else {
            input.shrink(count);
            current.grow(count);
            slot.setChanged();
        }
    }
}
