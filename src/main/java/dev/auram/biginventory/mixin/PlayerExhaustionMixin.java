package dev.auram.biginventory.mixin;

import dev.auram.biginventory.StorageEquipment;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
abstract class PlayerExhaustionMixin {
    @ModifyArg(method = {"jumpFromGround", "checkMovementStatistics"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"))
    private float bigInventory$bagExhaustion(float amount) {
        return StorageEquipment.applyMovementExhaustion((Player) (Object) this, amount);
    }
}
