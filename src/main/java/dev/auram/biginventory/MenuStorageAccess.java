package dev.auram.biginventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;

/** Backpack operations on a native container menu. */
public interface MenuStorageAccess {
    void bigInventory$runStorageExport(Inventory inventory, Runnable transfer);

    void bigInventory$reserveViewportSlots(Inventory inventory);

    void bigInventory$resetQuickCraft();

    void bigInventory$recoverCursor(ServerPlayer player, boolean disconnecting);
}
