package dev.auram.biginventory.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class StorageSearchBox extends EditBox {
    static final int HEIGHT = 12;
    private static final int ICON_X = 4;
    private static final int TEXT_X = 14;
    private static final int RIGHT_PADDING = 4;
    private static final int INK = 0xff303030;
    private static final float TEXT_SCALE = 0.75f;
    private final Font font;
    private int textWidth;
    private long focusedAt;

    StorageSearchBox(Font font) {
        super(font, 0, 0, 125, HEIGHT, Component.translatable("biginventory.search"));
        this.font = font;
        setWidth(getWidth());
        setBordered(false);
        setTextColor(INK);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        textWidth = Math.max(0, (int) ((width - TEXT_X - RIGHT_PADDING) / TEXT_SCALE));
    }

    @Override
    public int getInnerWidth() {
        return textWidth;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        double textX = Math.max(0, mouseX - getX() - TEXT_X) / TEXT_SCALE;
        super.onClick(getX() + textX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !isFocused()) focusedAt = Util.getMillis();
        super.setFocused(focused);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        int x = getX();
        int y = getY();
        int outerWidth = getWidth();
        graphics.fill(x, y, x + outerWidth, y + HEIGHT, isFocused() ? 0xffeeeeee : 0xffd5d5d5);
        graphics.fill(x, y + HEIGHT - 1, x + outerWidth, y + HEIGHT, isFocused() ? 0xff707070 : 0xffa5a5a5);
        renderMagnifier(graphics, x + ICON_X, y + 2);

        graphics.pose().pushPose();
        graphics.pose().translate(x + TEXT_X, y + 3, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.pose().translate(-x, -y, 0);
        // EditBox clips its selection to the widget width in the unscaled text coordinates.
        width = textWidth;
        try {
            if (getValue().isEmpty()) {
                graphics.drawString(font, getMessage(), x, y, 0xff606060, false);
                if (isFocused() && (Util.getMillis() - focusedAt) / 300 % 2 == 0) {
                    graphics.fill(x - 2, y - 1, x - 1, y + 9, INK);
                }
            } else {
                super.renderWidget(graphics, mouseX, mouseY, time);
            }
        } finally {
            width = outerWidth;
            graphics.pose().popPose();
        }
    }

    private static void renderMagnifier(GuiGraphics graphics, int x, int y) {
        graphics.hLine(x + 1, x + 3, y, INK);
        graphics.hLine(x + 1, x + 3, y + 4, INK);
        graphics.vLine(x, y, y + 4, INK);
        graphics.vLine(x + 4, y, y + 4, INK);
        graphics.fill(x + 4, y + 4, x + 5, y + 5, INK);
        graphics.fill(x + 5, y + 5, x + 6, y + 6, INK);
        graphics.fill(x + 6, y + 6, x + 7, y + 7, INK);
    }
}
