package dev.auram.biginventory.mixin;

import dev.auram.biginventory.BackpackItemHandler;
import dev.auram.biginventory.ChestControls;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.violetmoon.quark.base.handler.InventoryTransferHandler$Restock", remap = false)
abstract class QuarkRestockMixin {
    @ModifyArg(method = "transfer", index = 0, at = @At(value = "INVOKE",
            target = "Lorg/violetmoon/quark/base/handler/InventoryTransferHandler$Restock;insertInHandler(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Lorg/violetmoon/quark/base/handler/InventoryTransferHandler$TransferPredicate;)Lnet/minecraft/world/item/ItemStack;"))
    private IItemHandler bigInventory$exposeMatchingStacks(IItemHandler handler) {
        if (handler instanceof InvWrapper wrapper && wrapper.getInv() instanceof Inventory inventory
                && ChestControls.hasBackpack(inventory.player)) {
            return new BackpackItemHandler(inventory, false);
        }
        return handler;
    }
}
