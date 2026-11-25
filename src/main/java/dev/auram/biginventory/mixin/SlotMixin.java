package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackViewport;
import dev.auram.biginventory.SlotBinding;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slot.class)
abstract class SlotMixin implements SlotBinding {
    @Shadow @Final public Container container;
    @Shadow @Final @Mutable private int slot;
    @Unique private int bigInventory$original;
    @Unique private int bigInventory$target = UNBOUND;
    @Unique private boolean bigInventory$reserved;

    @Override
    public int bigInventory$originalIndex() {
        return bigInventory$target == UNBOUND ? slot : bigInventory$original;
    }

    @Override
    public int bigInventory$binding() {
        return bigInventory$target;
    }

    @Override
    public void bigInventory$bind(int index) {
        bigInventory$original = bigInventory$originalIndex();
        bigInventory$target = index == UNBOUND && bigInventory$reserved ? INACTIVE : index;
        slot = index >= 0 ? index : bigInventory$original;
    }

    @Override
    public void bigInventory$reserve() {
        bigInventory$reserved = true;
        bigInventory$bind(INACTIVE);
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void bigInventory$visible(CallbackInfoReturnable<Boolean> cir) {
        if (bigInventory$target == INACTIVE) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void bigInventory$canPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (bigInventory$target == INACTIVE) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void bigInventory$canTake(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (bigInventory$target == INACTIVE) cir.setReturnValue(false);
    }

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void bigInventory$readBoundSlot(CallbackInfoReturnable<ItemStack> cir) {
        if (bigInventory$target == INACTIVE) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void bigInventory$removeBoundSlot(int count, CallbackInfoReturnable<ItemStack> cir) {
        if (bigInventory$target == INACTIVE) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void bigInventory$writeBoundSlot(ItemStack stack, CallbackInfo ci) {
        if (bigInventory$target == INACTIVE) ci.cancel();
        else if (bigInventory$target >= Backpack.OVERFLOW_START && container instanceof Inventory inventory) {
            Backpack.of(inventory).prepareSlot(bigInventory$target, stack);
        }
    }

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"), cancellable = true)
    private void bigInventory$weightLimit(ItemStack incoming, CallbackInfoReturnable<Integer> cir) {
        if (bigInventory$target == INACTIVE) {
            cir.setReturnValue(0);
        } else if (container instanceof Inventory inventory && (!inventory.player.level().isClientSide
                || bigInventory$target >= 0)
                && Backpack.isStorageSlot(slot)) {
            int limit = Backpack.of(inventory).slotLimit(slot, incoming);
            cir.setReturnValue(BackpackViewport.acceptsLargeStacks((Slot) (Object) this)
                    ? limit : Math.min(cir.getReturnValueI(), limit));
        }
    }

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void bigInventory$slotMaximum(CallbackInfoReturnable<Integer> cir) {
        if (BackpackViewport.acceptsLargeStacks((Slot) (Object) this)) cir.setReturnValue(Backpack.MAX_STACK);
    }
}
