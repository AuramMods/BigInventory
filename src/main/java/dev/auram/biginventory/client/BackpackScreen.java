package dev.auram.biginventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

final class BackpackScreen extends InventoryScreen {
    private static final int HEADER = 106;
    private static final int FOOTER = 26;
    private final Player player;
    private ClientStorage.View view;
    private PlayerSlots playerSlots;
    private StorageGrid grid;
    private ImageButton recipeButton;
    private int columns;
    private int rows;

    BackpackScreen(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    protected void init() {
        int margin = width >= 440 && ModList.get().isLoaded("jei") ? 124 : 16;
        columns = Math.max(9, Math.min(18, (width - margin - 22) / StorageGrid.COMPACT_CELL));
        rows = Math.max(1, (height - 12 - HEADER - FOOTER) / StorageGrid.COMPACT_CELL);
        imageWidth = Math.max(176, columns * StorageGrid.COMPACT_CELL + 22);
        imageHeight = HEADER + rows * StorageGrid.COMPACT_CELL + FOOTER;
        recipeButton = null;
        super.init();
        if (minecraft.gameMode.hasInfiniteItems()) return;

        for (var child : children()) {
            if (child instanceof ImageButton button) {
                recipeButton = button;
                break;
            }
        }
        if (view == null) view = ClientStorage.open(menu.containerId);
        if (playerSlots == null) playerSlots = new PlayerSlots(menu, player.getInventory());
        playerSlots.arrange((imageWidth - 176) / 2, imageHeight - 22);
        String query = grid == null ? "" : grid.search().getValue();
        grid = new StorageGrid(view, font, query, StorageGrid.COMPACT_CELL);
        addRenderableWidget(grid.search());
        layoutControls();
    }

    private boolean showGrid() {
        return grid != null && !(getRecipeBookComponent().isVisible() && width < 379);
    }

    private void layoutControls() {
        if (grid == null) return;
        if (recipeButton != null) recipeButton.setPosition(leftPos + 104, topPos + 61);
        grid.bounds(leftPos + 6, topPos + HEADER, columns, rows);
        grid.search().visible = showGrid();
        if (!showGrid() && getFocused() == grid.search()) setFocused(null);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float time, int mouseX, int mouseY) {
        layoutControls();
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff101b21);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xff354853);
        // Reuse the game's equipment and crafting controls in the header.
        graphics.blit(INVENTORY_LOCATION, leftPos, topPos, 0, 0, 176, 84);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 51, topPos + 75, 30,
                leftPos + 51 - mouseX, topPos + 25 - mouseY, player);
        drawCapacity(graphics);
        int hotbarX = leftPos + (imageWidth - 176) / 2 + 7;
        graphics.blit(INVENTORY_LOCATION, hotbarX, topPos + imageHeight - 23, 7, 141, 162, 18);
        if (showGrid()) grid.render(graphics, mouseX, mouseY);
    }

    private void drawCapacity(GuiGraphics graphics) {
        var hud = ClientStorage.hud();
        int space = imageWidth - 176;
        if (hud == null || space < 72) return;
        int x = leftPos + 188;
        int y = topPos + 24;
        int barWidth = space - 24;
        graphics.drawString(font, Component.translatable("biginventory.storage"), x, y, 0xffb6c8cf, false);
        graphics.drawString(font, hud.used() + " / " + hud.capacity(), x, y + 13, 0xfff1f8fa, false);
        graphics.fill(x, y + 27, x + barWidth, y + 31, 0xff1b2a32);
        int filled = hud.capacity() == 0 ? barWidth
                : (int) Math.min(barWidth, hud.used() * (double) barWidth / hud.capacity());
        graphics.fill(x, y + 27, x + filled, y + 31, hud.used() > hud.capacity() ? 0xfff48a77 : 0xff89cbb2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float time) {
        super.render(graphics, mouseX, mouseY, time);
        if (showGrid()) grid.tooltip(graphics, menu.getCarried(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (showGrid() && grid.click(x, y, button, menu.getCarried())) {
            setFocused(null);
            return true;
        }
        boolean handled = super.mouseClicked(x, y, button);
        layoutControls();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return showGrid() && grid.scroll(x, y, delta) || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return showGrid() && grid.drag(y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return showGrid() && grid.release() || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (showGrid() && getFocused() == grid.search() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            grid.search().keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ClientStorage.close(view);
        view = null;
        if (playerSlots != null) playerSlots.restore();
        playerSlots = null;
        super.removed();
    }
}
