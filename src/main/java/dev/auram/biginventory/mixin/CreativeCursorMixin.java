package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.BackpackCreativeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class CreativeCursorMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void bigInventory$syncCreativePlayerCursor(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        // Vanilla skips the creative cursor because the catalog is local. The player tab uses a real menu.
        if (packet.getContainerId() == -1 && minecraft.screen instanceof BackpackCreativeScreen screen
                && screen.isStorageOpen()) {
            minecraft.player.inventoryMenu.setCarried(packet.getItem());
        }
    }
}
