package dev.auram.biginventory;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

public record StorageEquipment(long addedCapacity, int bags, int echoRings, boolean endant) {
    static final StorageEquipment NONE = new StorageEquipment(0, 0, 0, false);

    static StorageEquipment equipped(Player player) {
        return ModList.get().isLoaded("curios") ? CuriosCapacity.equipped(player, StorageConfig.CURIO_CAPACITY.get()) : NONE;
    }

    long capacity(long base) {
        double multiplier = 1.0 + echoRings * (StorageItems.RING_CAPACITY_PERCENT / 100.0);
        return (long) Math.floor(Math.max(0L, base + addedCapacity) * multiplier);
    }

    public static float applyMovementExhaustion(Player player, float amount) {
        if (amount <= 0 || player.level().isClientSide || player.getAbilities().invulnerable) return amount;
        return amount * (1 + equipped(player).bags() * (StorageItems.BAG_EXHAUSTION_PERCENT / 100.0F));
    }
}
