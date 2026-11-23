package dev.auram.biginventory.client;

import net.minecraft.client.renderer.Rect2i;

/** The main panel of a screen whose reported GUI bounds also include side panels. */
public interface ContainerPanelBounds {
    Rect2i bigInventory$mainPanel();
}
