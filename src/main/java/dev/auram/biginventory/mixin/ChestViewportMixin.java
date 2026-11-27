package dev.auram.biginventory.mixin;

import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.BackpackViewport;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestMenu.class)
abstract class ChestViewportMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
            at = @At("RETURN"))
    private void bigInventory$reserveStorageRows(MenuType<?> type, int menuId, Inventory inventory,
                                                 Container container, int rows, CallbackInfo ci) {
        if (getClass() == (Class<?>) ChestMenu.class) {
            ((MenuStorageAccess) this).bigInventory$reserveViewportSlots(inventory, BackpackViewport.CONTAINER_CELLS);
        }
    }
}
