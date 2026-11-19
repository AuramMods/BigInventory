package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
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

@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void bigInventory$protectStorageSwap(int slotIndex, int button, ClickType click, Player player, CallbackInfo ci) {
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
                && !player.level().isClientSide && slot.hasItem() && !incoming.isEmpty()
                && incoming.getCount() > Backpack.of(player.getInventory()).replacementLimit(slot.getSlotIndex(), incoming)) {
            ci.cancel();
        }
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void bigInventory$quickMove(ItemStack input, int from, int to, boolean reverse,
                                        CallbackInfoReturnable<Boolean> cir) {
        Inventory destination = null;
        for (int i = from; i < to; i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory inv && !inv.player.level().isClientSide
                    && Backpack.isStorageSlot(slot.getSlotIndex())) {
                destination = inv;
                break;
            }
        }
        if (destination == null) return;

        int original = input.getCount();
        int start = reverse ? to - 1 : from;
        int end = reverse ? from - 1 : to;
        int step = reverse ? -1 : 1;
        for (int i = start; i != end && !input.isEmpty() && input.isStackable(); i += step) {
            Slot slot = slots.get(i);
            if (slot.getItem() != input && Backpack.matches(slot.getItem(), input)) bigInventory$moveToSlot(input, slot);
        }
        for (int i = start; i != end && !input.isEmpty(); i += step) {
            Slot slot = slots.get(i);
            if (slot.getItem() != input && slot.getItem().isEmpty()) bigInventory$moveToSlot(input, slot);
        }
        if (!input.isEmpty()) {
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
