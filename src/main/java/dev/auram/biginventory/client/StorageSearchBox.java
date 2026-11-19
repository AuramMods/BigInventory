package dev.auram.biginventory.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class StorageSearchBox extends EditBox {
    static final int HEIGHT = 12;
    private static final int PADDING = 4;
    private static final float TEXT_SCALE = 0.75f;
    private int textWidth;

    StorageSearchBox(Font font) {
        super(font, 0, 0, 125, HEIGHT, Component.translatable("biginventory.search"));
        setWidth(getWidth());
        setBordered(false);
        setTextColor(0xff303030);
        setHint(Component.translatable("biginventory.search").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        textWidth = Math.max(0, (int) ((width - 2 * PADDING) / TEXT_SCALE));
    }

    @Override
    public int getInnerWidth() {
        return textWidth;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        double textX = Math.max(0, mouseX - getX() - PADDING) / TEXT_SCALE;
        super.onClick(getX() + textX, mouseY);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        int x = getX();
        int y = getY();
        int outerWidth = getWidth();
        graphics.fill(x, y, x + outerWidth, y + HEIGHT, isFocused() ? 0xffeeeeee : 0xffd5d5d5);
        graphics.fill(x, y + HEIGHT - 1, x + outerWidth, y + HEIGHT, isFocused() ? 0xff707070 : 0xffa5a5a5);

        graphics.pose().pushPose();
        graphics.pose().translate(x + PADDING, y + 3, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.pose().translate(-x, -y, 0);
        // EditBox clips its selection to the widget width in the unscaled text coordinates.
        width = textWidth;
        try {
            super.renderWidget(graphics, mouseX, mouseY, time);
        } finally {
            width = outerWidth;
            graphics.pose().popPose();
        }
    }
}
