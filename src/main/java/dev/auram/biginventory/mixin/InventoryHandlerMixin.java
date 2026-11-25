package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InvWrapper.class, remap = false)
abstract class InventoryHandlerMixin {
    @Shadow @Final private Container inv;

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void bigInventory$insert(int slot, ItemStack input, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (!(inv instanceof Inventory inventory) || inventory.player.level().isClientSide
                || !Backpack.isStorageSlot(slot) || input.isEmpty()) return;
        if (slot >= inventory.getContainerSize()) {
            cir.setReturnValue(input);
            return;
        }
        Backpack storage = Backpack.of(inventory);
        ItemStack current = inventory.getItem(slot);
        if (!current.isEmpty() && !Backpack.matches(current, input)) {
            cir.setReturnValue(input);
            return;
        }
        int moved = Math.min(input.getCount(), storage.slotLimit(slot, input) - current.getCount());
        if (moved <= 0) {
            cir.setReturnValue(input);
            return;
        }
        ItemStack remainder = input.copy();
        if (simulate) remainder.shrink(moved);
        else storage.insertAt(slot, remainder, moved);
        cir.setReturnValue(remainder.isEmpty() ? ItemStack.EMPTY : remainder);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"))
    private void bigInventory$validateSet(int slot, ItemStack stack, CallbackInfo ci) {
        if (!(inv instanceof Inventory inventory) || inventory.player.level().isClientSide
                || !Backpack.isStorageSlot(slot) || stack.isEmpty()) return;
        Backpack backpack = Backpack.of(inventory);
        ItemStack old = inventory.getItem(slot);
        // Allow a caller to write back an in-place item update, even while encumbered.
        boolean existing = stack.getItem() == old.getItem() && backpack.weight(stack) <= backpack.weight(old);
        if (slot >= inventory.getContainerSize() || stack.getCount() > Backpack.storageStackLimit(stack)
                || (!existing && stack.getCount() > backpack.replacementLimit(slot, stack))) {
            throw new IllegalArgumentException("Player storage cannot accept the requested slot contents");
        }
    }

    @Inject(method = "getSlotLimit", at = @At("HEAD"), cancellable = true)
    private void bigInventory$storageLimit(int slot, CallbackInfoReturnable<Integer> cir) {
        if (inv instanceof Inventory && Backpack.isStorageSlot(slot)) cir.setReturnValue(Backpack.MAX_STACK);
    }
}
