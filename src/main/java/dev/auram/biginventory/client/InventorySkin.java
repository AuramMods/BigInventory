package dev.auram.biginventory.client;

import dev.auram.biginventory.BigInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

final class InventorySkin {
    static final int FACE = 0xffc6c6c6;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(BigInventory.MOD_ID,
            "textures/gui/inventory.png");

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blitNineSliced(FRAME, x, y, width, height, 4, 32, 32, 0, 0);
    }

    static void slot(GuiGraphics graphics, int left, int top, Slot slot) {
        well(graphics, left + slot.x - 1, top + slot.y - 1, 18, 18, 0xff8c8c8c);
    }

    static void well(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, 0xffeeeeee);
        graphics.fill(x, y, x + width - 1, y + height - 1, 0xff5d5d5d);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
    }

    static void vanillaSlot(GuiGraphics graphics, int x, int y, int size, boolean hovered) {
        int darkEdge = 0xff373737;
        int lightEdge = 0xffffffff;
        int right = x + size - 1;
        int bottom = y + size - 1;
        graphics.fill(x, y, right + 1, bottom + 1, hovered ? 0xffb1b1b1 : 0xff8b8b8b);
        graphics.hLine(x, right - 1, y, darkEdge);
        graphics.vLine(x, y, bottom, darkEdge);
        graphics.hLine(x + 1, right - 1, bottom, lightEdge);
        graphics.vLine(right, y, bottom, lightEdge);
    }

    static void craftingArrow(GuiGraphics graphics, int x, int headX, int y) {
        int color = 0xff7a7a7a;
        graphics.fill(x, y - 2, headX + 1, y + 3, color);
        for (int i = 0; i < 6; i++) {
            graphics.fill(headX + i, y - 5 + i, headX + i + 1, y + 6 - i, color);
        }
    }

    static void trash(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 4, y + 4, x + 12, y + 5, 0xffdadada);
        graphics.fill(x + 5, y + 6, x + 11, y + 12, 0xffdadada);
        graphics.fill(x + 7, y + 7, x + 8, y + 11, 0xff6b6b6b);
        graphics.fill(x + 9, y + 7, x + 10, y + 11, 0xff6b6b6b);
    }

    private InventorySkin() {}
}
