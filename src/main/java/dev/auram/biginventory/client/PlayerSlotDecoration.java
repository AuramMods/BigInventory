package dev.auram.biginventory.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** A separate widget that draws the frame for a native player inventory slot. */
public interface PlayerSlotDecoration {
    void bigInventory$setPlayerSlot(AbstractContainerScreen<?> screen, boolean hotbar);
}
