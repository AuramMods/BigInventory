package dev.auram.biginventory;

import net.minecraft.world.entity.player.Inventory;

/** Keeps a menu's temporary quick-move source out of its player inventory sync. */
public interface MenuStorageAccess {
    void bigInventory$runStorageExport(Inventory inventory, Runnable transfer);
}
