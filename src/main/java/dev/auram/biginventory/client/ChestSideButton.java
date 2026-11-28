package dev.auram.biginventory.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public interface ChestSideButton {
    boolean bigInventory$isPlayerSide();

    static void place(AbstractContainerScreen<?> screen, AbstractWidget button) {
        StorageSearchBox search = null;
        int index = 0;
        int count = 0;
        for (var child : screen.children()) {
            if (child instanceof StorageSearchBox box && box.visible) search = box;
            if (child instanceof ChestSideButton side && side.bigInventory$isPlayerSide()) {
                if (child == button) index = count;
                count++;
            }
        }
        if (search == null) return;
        int left = screen.getGuiLeft();
        int right = left + screen.getXSize();
        int width = button.getWidth();
        int gap = 3;
        int x = search.getX() - width - gap;
        if (x < left + gap) x = left - width - gap;
        if (x < 0) {
            x = search.getX() + StorageGrid.WIDTH + 7;
            if (x + width > right - gap) x = right + gap;
            if (x + width > screen.width) return;
        }
        int step = button.getHeight() + 2;
        int top = Math.max(0, Math.min(search.getY(), screen.height - count * step));
        button.setPosition(x, top + index * step);
    }
}
