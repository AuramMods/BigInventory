package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BigInventory;
import dev.auram.biginventory.MenuPlayerSlots;
import dev.auram.biginventory.mixin.ContainerLayoutAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Extends the player section of a container while retaining its native menu and controls. */
@Mod.EventBusSubscriber(modid = BigInventory.MOD_ID, value = Dist.CLIENT)
public final class ContainerBackpack {
    private static final int NATIVE_ROWS = 3;
    private static final int BOTTOM_MARGIN = 4;
    private static final int UPPER_SLOT_GAP = 3;
    private static Panel active;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void init(ScreenEvent.Init.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof BackpackScreen || screen instanceof BackpackChestScreen
                || screen instanceof CreativeModeInventoryScreen) return;
        String query = active != null && active.screen() == screen ? active.grid().search().getValue() : "";
        if (active != null) close();
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        Inventory inventory = player.getInventory();
        Slot[] legacy = new Slot[Backpack.LEGACY_END - Backpack.FIRST];
        Slot[] hotbar = new Slot[StorageGrid.COLUMNS];
        for (Slot slot : screen.getMenu().slots) {
            int index = MenuPlayerSlots.index(slot, inventory);
            if (index >= Backpack.FIRST && index < Backpack.LEGACY_END) {
                legacy[index - Backpack.FIRST] = slot;
            } else if (Inventory.isHotbarSlot(index)) {
                hotbar[index] = slot;
            }
        }
        if (legacy[0] == null) return;
        int x = legacy[0].x;
        int y = legacy[0].y;
        for (int i = 0; i < legacy.length; i++) {
            if (legacy[i] == null || legacy[i].x != x + (i % StorageGrid.COLUMNS) * StorageGrid.CELL
                    || legacy[i].y != y + (i / StorageGrid.COLUMNS) * StorageGrid.CELL) return;
        }

        int nativeHeight = screen.getYSize();
        int hotbarY = hotbar[0] == null ? -1 : hotbar[0].y;
        boolean aligned = hotbarY >= y + NATIVE_ROWS * StorageGrid.CELL && hotbarY + 16 <= nativeHeight;
        for (int i = 0; aligned && i < hotbar.length; i++) {
            aligned = hotbar[i] != null && hotbar[i].x == x + i * StorageGrid.CELL && hotbar[i].y == hotbarY;
        }
        int gridX = x - 1;
        int gridY = y - 1;
        int available = Math.max(0, (screen.height - nativeHeight + 1) / 2 - BOTTOM_MARGIN);
        PlayerSection section = aligned ? PlayerSection.fit(available, searchInset(screen, gridX, gridY))
                : new PlayerSection(NATIVE_ROWS, 0, 0);
        var layout = (ContainerLayoutAccess) screen;
        Component label = layout.bigInventory$inventoryTitle();
        layout.bigInventory$setInventoryTitle(Component.empty());

        ClientStorage.View view = ClientStorage.open(screen.getMenu());
        PlayerSlots slots = new PlayerSlots(screen.getMenu(), inventory);
        // Some containers build slot backgrounds from these coordinates during their own init.
        if (aligned) slots.arrange(0, hotbarY + section.hotbarShift());
        else slots.hideBackpack();
        StorageGrid grid = new StorageGrid(view, Minecraft.getInstance().font, query);
        active = new Panel(screen, slots, grid, view, label, gridX, gridY, nativeHeight,
                aligned ? hotbarY : -1, section);
    }

    private static int searchInset(AbstractContainerScreen<?> screen, int gridX, int gridY) {
        int top = gridY - StorageGrid.SEARCH_HEIGHT;
        int searchY = top;
        for (Slot slot : screen.getMenu().slots) {
            int slotX = slot.x - 1;
            int slotY = slot.y - 1;
            if (slotY < gridY && slotX < gridX + StorageGrid.WIDTH && slotX + StorageGrid.CELL > gridX) {
                searchY = Math.max(searchY, slotY + StorageGrid.CELL + UPPER_SLOT_GAP);
            }
        }
        return searchY - top;
    }

    @SubscribeEvent
    public static void search(ScreenEvent.Init.Post event) {
        if (active == null || active.screen() != event.getScreen()) return;
        event.addListener(active.grid().search());
        active.layout();
    }

    @SubscribeEvent
    public static void layout(ScreenEvent.Render.Pre event) {
        if (active != null && active.screen() == event.getScreen()) active.layout();
    }

    @SubscribeEvent
    public static void draw(ContainerScreenEvent.Render.Background event) {
        if (active == null || active.screen() != event.getContainerScreen()) return;
        if (active.visible()) {
            active.draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void tooltip(ScreenEvent.Render.Post event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()) {
            active.grid().tooltip(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()
                && active.grid().click(event.getMouseX(), event.getMouseY(), event.getButton())) {
            active.screen().setFocused(null);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()
                && active.grid().scroll(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void drag(ScreenEvent.MouseDragged.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.grid().drag(event.getMouseY())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.grid().release()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()
                && active.screen().getFocused() == active.grid().search()
                && event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE && event.getKeyCode() != GLFW.GLFW_KEY_TAB) {
            active.grid().search().keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (active != null && active.screen() == event.getScreen()) close();
    }

    private static void close() {
        active.slots().restore();
        ((ContainerLayoutAccess) active.screen()).bigInventory$setInventoryTitle(active.label());
        ClientStorage.close(active.view());
        active = null;
    }

    public static int extendedHeight(AbstractContainerScreen<?> screen, int nativeHeight) {
        return active != null && active.screen() == screen && active.visible()
                ? nativeHeight + active.section().extension() : nativeHeight;
    }

    public static boolean insideExtension(AbstractContainerScreen<?> screen, double x, double y) {
        if (active == null || active.screen() != screen || !active.visible()) return false;
        int bottom = screen.getGuiTop() + active.nativeHeight();
        int left = screen.getGuiLeft();
        return y >= bottom && y < bottom + active.section().extension() && x >= left && x < left + screen.getXSize();
    }

    private record PlayerSection(int rows, int searchInset, int gap) {
        static PlayerSection fit(int available, int searchInset) {
            int gap = Mth.clamp(available - searchInset, 0, StorageGrid.SEARCH_GAP);
            int extra = Math.floorDiv(available - searchInset - gap, StorageGrid.CELL);
            return new PlayerSection(Mth.clamp(NATIVE_ROWS + extra, 1, StorageGrid.MAX_ROWS), searchInset, gap);
        }

        int gridInset() {
            return searchInset + gap;
        }

        int hotbarShift() {
            return gridInset() + (rows - NATIVE_ROWS) * StorageGrid.CELL;
        }

        int extension() {
            return Math.max(0, hotbarShift());
        }
    }

    private record Panel(AbstractContainerScreen<?> screen, PlayerSlots slots, StorageGrid grid,
                         ClientStorage.View view, Component label, int gridX, int nativeGridY,
                         int nativeHeight, int nativeHotbarY, PlayerSection section) {
        boolean visible() {
            return !(screen instanceof RecipeUpdateListener recipe && recipe.getRecipeBookComponent().isVisible()
                    && screen.width < 379);
        }

        void layout() {
            int x = screen.getGuiLeft() + gridX;
            int y = screen.getGuiTop() + nativeGridY;
            grid.bounds(x, y + section.gridInset(), section.rows(), section.gap());
            boolean gridVisible = visible();
            grid.search().visible = gridVisible;
            if (!gridVisible) grid.cancelAnimation();
            if (!grid.search().visible && screen.getFocused() == grid.search()) screen.setFocused(null);
        }

        void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            int x = left + gridX;
            int extension = section.extension();
            if (extension > 0) InventorySkin.extendPanel(graphics, left, top + nativeHeight, screen.getXSize(), extension);
            if (nativeHotbarY >= 0) {
                int nativeHotbarTop = top + nativeHotbarY - 1;
                int slotTop = nativeHotbarTop + section.hotbarShift();
                int sectionTop = top + nativeGridY;
                int backgroundBottom = Math.max(slotTop, nativeHotbarTop + StorageGrid.CELL);
                InventorySkin.fill(graphics, x, sectionTop, StorageGrid.WIDTH, backgroundBottom - sectionTop, InventorySkin.Tone.PANEL);
                for (int column = 0; column < StorageGrid.COLUMNS; column++) {
                    InventorySkin.slot(graphics, x + column * StorageGrid.CELL, slotTop);
                }
            }
            grid.render(graphics, mouseX, mouseY);
        }
    }

    private ContainerBackpack() {}
}
