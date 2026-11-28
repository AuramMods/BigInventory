package dev.auram.biginventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.stream.IntStream;

/** Connects chest transfer controls to the player's current backpack view. */
public final class ChestControls {
    public static boolean hasBackpack(Player player) {
        return player instanceof ServerPlayer server && server.containerMenu != server.inventoryMenu
                && (ViewportSessions.hasView(server) || StorageSessions.hasView(server));
    }

    public static int[] insertSlots(Player player) {
        if (!hasBackpack(player)) return null;
        int[] visible = ViewportSessions.visibleSlots((ServerPlayer) player);
        return visible != null ? visible : IntStream.range(Backpack.FIRST, Backpack.LEGACY_END).toArray();
    }

    public static boolean sortBackpack(Player player) {
        if (!hasBackpack(player)) return false;
        var server = (ServerPlayer) player;
        Backpack.of(server.getInventory()).sort();
        server.containerMenu.broadcastChanges();
        ViewportSessions.afterChestSort(server);
        StorageSessions.afterChestSort(server);
        return true;
    }

    public static ItemStack restock(Inventory inventory, ItemStack offered) {
        ItemStack remainder = offered.copy();
        Backpack.of(inventory).receiveFromChest(remainder);
        return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
    }

    private ChestControls() {}
}
