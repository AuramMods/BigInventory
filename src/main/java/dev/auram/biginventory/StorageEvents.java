package dev.auram.biginventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class StorageEvents {
    private static final UUID SPEED_ID = UUID.fromString("bf2f62b5-d310-4b15-a977-05749c4fec88");
    private static final AttributeModifier SLOW = new AttributeModifier(SPEED_ID, "BigInventory encumbrance",
            -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private final Map<UUID, Refill> refills = new HashMap<>();

    @SubscribeEvent
    public void destroyed(PlayerDestroyItemEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND
                && StorageConfig.REFILL_HOTBAR.get() && !event.getOriginal().isEmpty() && !player.isCreative()) {
            refills.put(player.getUUID(), new Refill(player.getInventory().selected, event.getOriginal().copy()));
        }
    }

    @SubscribeEvent
    public void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public void cloned(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    private void clear(ServerPlayer player) {
        refills.remove(player.getUUID());
        StorageSessions.remove(player);
        ViewportSessions.remove(player);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Inventory inventory = player.getInventory();
        Backpack storage = Backpack.of(inventory);
        Refill refill = refills.remove(player.getUUID());
        if (refill != null && player.isAlive() && inventory.getItem(refill.slot()).isEmpty()) {
            ItemStack previous = refill.previous();
            ItemStack replacement = storage.find(stack -> previous.isDamageableItem()
                    ? ItemStack.isSameItem(stack, previous) : Backpack.matches(stack, previous));
            if (!replacement.isEmpty()) {
                inventory.setItem(refill.slot(), storage.take(replacement.copyWithCount(1), Backpack.stackLimit(replacement)));
                inventory.setChanged();
                player.containerMenu.broadcastChanges();
            }
        }
        storage.trimEmptyTail();
        StorageWeight weight = storage.weightBudget();
        long used = weight.used();
        long capacity = weight.capacity();
        updateEncumbrance(player, player.isAlive() && used > capacity);
        StorageSessions.tick(player, weight);
        ViewportSessions.tick(player);
    }

    private static void updateEncumbrance(ServerPlayer player, boolean overweight) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (overweight) {
            if (!speed.hasModifier(SLOW)) speed.addTransientModifier(SLOW);
            // Short leases expire on recovery and leave fatigue applied by other sources alone.
            if (!player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 12, 0, false, false, true));
            }
        } else if (speed.hasModifier(SLOW)) {
            speed.removeModifier(SLOW);
        }
    }

    private record Refill(int slot, ItemStack previous) {}
}
