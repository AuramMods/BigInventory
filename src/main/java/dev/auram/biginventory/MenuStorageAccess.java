package dev.auram.biginventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

import java.util.function.IntSupplier;

/** Backpack operations on a native container menu. */
public interface MenuStorageAccess {
    int bigInventory$runStorageExport(Inventory inventory, IntSupplier transfer);

    int bigInventory$moveToHotbar(Slot source, int limit);

    void bigInventory$reserveViewportSlots(Inventory inventory);

    void bigInventory$resetQuickCraft();

    void bigInventory$recoverCursor(ServerPlayer player, boolean disconnecting);
}
