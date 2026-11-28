package dev.auram.biginventory.mixin;

import dev.auram.biginventory.ViewportSessions;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ViewportClickMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleContainerClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetLastActionTime()V"), cancellable = true)
    private void bigInventory$waitForDisplayedPage(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (ViewportSessions.blocksClick(player, packet.getContainerId(), packet.getSlotNum(), packet.getClickType())) {
            ci.cancel();
        }
    }
}
