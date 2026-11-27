package dev.auram.biginventory.client;

import net.minecraft.client.gui.screens.Screen;

/** A container that draws its own floating windows above the backpack. */
public interface ContainerWindows {
    boolean bigInventory$hasWindowAt(double x, double y);

    static boolean allowsBackpackInput(Screen screen, double x, double y) {
        return !(screen instanceof ContainerWindows windows && windows.bigInventory$hasWindowAt(x, y));
    }
}
