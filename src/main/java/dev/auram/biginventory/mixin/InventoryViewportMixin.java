package dev.auram.biginventory.mixin;

import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.BackpackViewport;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
abstract class InventoryViewportMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bigInventory$reserveStorageRows(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
        if (getClass() == (Class<?>) InventoryMenu.class) {
            ((MenuStorageAccess) this).bigInventory$reserveViewportSlots(inventory, BackpackViewport.MAX_CELLS);
        }
    }
}
