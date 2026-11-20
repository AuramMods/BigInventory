package dev.auram.biginventory.client;

import dev.auram.biginventory.BigInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

final class InventorySkin {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(BigInventory.MOD_ID,
            "textures/gui/inventory.png");
    private static final ResourceLocation CONTROLS = ResourceLocation.fromNamespaceAndPath(BigInventory.MOD_ID,
            "textures/gui/inventory_controls.png");
    private static final int ATLAS_SIZE = 64;
    private static final int SWATCH_ROW = 34;

    // Textured controls use the same GUI shader as the inventory frame.
    enum Tone {
        PANEL(0), WELL_EDGE(1), WELL_SHADOW(2), PREVIEW(3), RAIL(4), THUMB(5), SLOT(6), ARROW(7),
        SEARCH_FOCUSED(8), SEARCH_IDLE(9), SEARCH_FOCUSED_EDGE(10), SEARCH_IDLE_EDGE(11);

        private final int u;

        Tone(int u) {
            this.u = u;
        }
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitNineSliced(FRAME, x, y, width, height, 4, 32, 32, 0, 0);
    }

    static void slot(GuiGraphics graphics, int left, int top, Slot slot) {
        slot(graphics, left + slot.x - 1, top + slot.y - 1);
    }

    static void slot(GuiGraphics graphics, int x, int y) {
        blit(graphics, x, y, 18, 18, 26, 16);
    }

    static void well(GuiGraphics graphics, int x, int y, int width, int height, Tone tone) {
        fill(graphics, x, y, width, height, Tone.WELL_EDGE);
        fill(graphics, x, y, width - 1, height - 1, Tone.WELL_SHADOW);
        fill(graphics, x + 1, y + 1, width - 2, height - 2, tone);
    }

    static void storageSlot(GuiGraphics graphics, int x, int y, int cellSize, boolean vanilla, boolean hovered) {
        if (vanilla) {
            blit(graphics, x, y, cellSize, cellSize, hovered ? 14 : 0, 0);
        } else if (cellSize == StorageGrid.COMPACT_CELL) {
            blit(graphics, x, y, 13, 13, hovered ? 13 : 0, 16);
        } else {
            blit(graphics, x, y, 16, 16, hovered ? 44 : 28, 0);
        }
    }

    static void craftingArrow(GuiGraphics graphics, int x, int headX, int y) {
        fill(graphics, x, y - 2, headX - x + 1, 5, Tone.ARROW);
        blit(graphics, headX, y - 5, 6, 11, 44, 16);
    }

    static void trash(GuiGraphics graphics, int x, int y) {
        blit(graphics, x + 4, y + 4, 8, 8, 50, 16);
    }

    static void fill(GuiGraphics graphics, int x, int y, int width, int height, Tone tone) {
        graphics.blit(CONTROLS, x, y, width, height, tone.u, SWATCH_ROW, 1, 1, ATLAS_SIZE, ATLAS_SIZE);
    }

    private static void blit(GuiGraphics graphics, int x, int y, int width, int height, int u, int v) {
        graphics.blit(CONTROLS, x, y, u, v, width, height, ATLAS_SIZE, ATLAS_SIZE);
    }

    private InventorySkin() {}
}
