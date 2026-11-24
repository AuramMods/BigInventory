package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;

interface BackpackGrid {
    StorageSearchBox search();

    void bounds(int x, int y, int left, int top, int rows, boolean visible, int searchGap);

    void render(GuiGraphics graphics, int x, int y);

    default void tooltip(GuiGraphics graphics, int x, int y) {}

    boolean click(double x, double y, int button);

    boolean scroll(double x, double y, double delta);

    boolean drag(double y);

    boolean release();

    void close();
}
