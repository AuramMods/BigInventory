package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackItemHandler;
import dev.auram.biginventory.ChestControls;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.violetmoon.quark.base.handler.InventoryTransferHandler$Transfer", remap = false)
abstract class QuarkTransferMixin {
    @Shadow @Final public Player player;
    @Unique private int[] bigInventory$insertSlots;

    @Inject(method = "transfer", at = @At("HEAD"))
    private void bigInventory$selectBackpackSlots(CallbackInfo ci) {
        bigInventory$insertSlots = ChestControls.insertSlots(player);
    }

    @Redirect(method = "transfer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/NonNullList;size()I"))
    private int bigInventory$visibleSlotCount(NonNullList<?> slots) {
        return bigInventory$insertSlots == null ? slots.size() : Backpack.FIRST + bigInventory$insertSlots.length;
    }

    @Redirect(method = "transfer", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$readBackpackSlot(Inventory inventory, int slot) {
        return inventory.getItem(bigInventory$insertSlots == null ? slot : bigInventory$insertSlots[slot - Backpack.FIRST]);
    }

    @Redirect(method = "transfer", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private void bigInventory$writeBackpackRemainder(Inventory inventory, int slot, ItemStack remainder) {
        inventory.setItem(bigInventory$insertSlots == null ? slot : bigInventory$insertSlots[slot - Backpack.FIRST], remainder);
    }

    @Inject(method = "transfer", at = @At("RETURN"))
    private void bigInventory$compactAfterInsert(CallbackInfo ci) {
        if (bigInventory$insertSlots != null) ChestControls.sortBackpack(player);
        bigInventory$insertSlots = null;
    }

    @Redirect(method = "insertInHandler", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/ItemHandlerHelper;insertItemStacked(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$extractIntoBackpack(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (!simulate && handler instanceof BackpackItemHandler backpack && backpack.getInv() == player.getInventory()) {
            return ChestControls.restock(player.getInventory(), stack);
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }
}
