package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ClientStorage;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class StorageClickPredictionMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void bigInventory$predictStorageClick(int menuId, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (ClientStorage.interceptMenuClick((MultiPlayerGameMode) (Object) this, menuId, slot, button, type, player)) ci.cancel();
    }
}
