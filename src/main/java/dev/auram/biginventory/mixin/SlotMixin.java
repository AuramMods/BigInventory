package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class SlotMixin {
    @Shadow @Final public Container container;
    @Shadow @Final private int slot;

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
    private void bigInventory$weightLimit(ItemStack incoming, CallbackInfoReturnable<Integer> cir) {
        if (container instanceof Inventory inventory && !inventory.player.level().isClientSide
                && Backpack.isStorageSlot(slot)) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), Backpack.of(inventory).slotLimit(slot, incoming)));
        }
    }
}
