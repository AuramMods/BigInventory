package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.List;

interface BackpackGrid {
    StorageSearchBox search();
    AutoSortButton autoSort();

    default List<AbstractWidget> widgets() {
        return List.of(search(), autoSort());
    }

    default boolean ownsFocus(GuiEventListener focused) {
        return focused == search() || focused == autoSort();
    }

    default void searchBounds(int x, int y, boolean visible) {
        search().setPosition(x, y);
        search().visible = visible;
        autoSort().setPosition(x + StorageGrid.WIDTH - AutoSortButton.WIDTH, y);
        autoSort().visible = visible;
    }

    void bounds(int x, int y, int left, int top, int rows, boolean visible, int searchGap);

    void render(GuiGraphics graphics, int x, int y);

    default void tooltip(GuiGraphics graphics, int x, int y) {}

    boolean click(double x, double y, int button);

    boolean scroll(double x, double y, double delta);

    boolean drag(double y);

    boolean release();

    void close();
}
