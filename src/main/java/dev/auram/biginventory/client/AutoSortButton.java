package dev.auram.biginventory.client;

import dev.auram.biginventory.mixin.FontColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class AutoSortButton extends AbstractButton {
    static final int WIDTH = 16;
    static final int GAP = 1;
    private final Consumer<Boolean> change;

    AutoSortButton(Consumer<Boolean> change) {
        super(0, 0, WIDTH, StorageSearchBox.HEIGHT, Component.empty());
        this.change = change;
        updateLabel(InventoryPreferences.autoSort());
    }

    @Override
    public void onPress() {
        boolean enabled = InventoryPreferences.toggleAutoSort();
        updateLabel(enabled);
        change.accept(enabled);
    }

    private void updateLabel(boolean enabled) {
        setMessage(Component.translatable(enabled ? "biginventory.auto_sort.on" : "biginventory.auto_sort.off"));
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return ContainerWindows.allowsBackpackInput(Minecraft.getInstance().screen, x, y)
                && super.mouseClicked(x, y, button);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        int x = getX();
        int y = getY();
        isHovered &= ContainerWindows.allowsBackpackInput(Minecraft.getInstance().screen, mouseX, mouseY);
        boolean hovered = isFocused() || isHovered;
        var fill = hovered ? InventorySkin.Tone.SEARCH_FOCUSED : InventorySkin.Tone.SEARCH_IDLE;
        InventorySkin.fill(graphics, x, y, WIDTH, StorageSearchBox.HEIGHT, fill);
        InventorySkin.fill(graphics, x, y + StorageSearchBox.HEIGHT - 1, WIDTH, 1,
                hovered ? InventorySkin.Tone.SEARCH_FOCUSED_EDGE : InventorySkin.Tone.SEARCH_IDLE_EDGE);
        int ink = FontColor.bigInventory$adjustColor(0xff303030);
        graphics.hLine(x + 3, x + 8, y + 2, ink);
        graphics.hLine(x + 3, x + 7, y + 4, ink);
        graphics.hLine(x + 3, x + 6, y + 6, ink);
        graphics.vLine(x + 11, y + 2, y + 8, ink);
        graphics.fill(x + 10, y + 7, x + 13, y + 8, ink);
        graphics.fill(x + 9, y + 6, x + 10, y + 7, ink);
        graphics.fill(x + 13, y + 6, x + 14, y + 7, ink);
        if (!InventoryPreferences.autoSort()) {
            for (int i = 0; i < 9; i++) {
                InventorySkin.fill(graphics, x + 3 + i, y + 1 + i, 3, 1, fill);
                graphics.fill(x + 4 + i, y + 1 + i, x + 5 + i, y + 2 + i, ink);
            }
        }
    }
}
