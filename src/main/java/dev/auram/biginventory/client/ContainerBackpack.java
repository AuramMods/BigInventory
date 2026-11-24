package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BigInventory;
import dev.auram.biginventory.MenuPlayerSlots;
import dev.auram.biginventory.mixin.ContainerLayoutAccess;
import dev.auram.biginventory.mixin.ScreenWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
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
    private static PendingPanel pending;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void init(ScreenEvent.Init.Pre event) {
        if (event.getScreen() != Minecraft.getInstance().screen
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof BackpackScreen || screen instanceof BackpackChestScreen
                || screen instanceof CreativeModeInventoryScreen) return;
        String query = active != null && active.screen() == screen ? active.grid().search().getValue() : "";
        if (active != null) close(true);
        pending = new PendingPanel(screen, query);
    }

    private static void open(AbstractContainerScreen<?> screen, String query) {
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

        Rect2i mainPanel = screen instanceof ContainerPanelBounds modular ? modular.bigInventory$mainPanel() : null;
        int nativeHeight = mainPanel == null ? screen.getYSize() : mainPanel.getHeight();
        int hotbarY = hotbar[0] == null ? -1 : hotbar[0].y;
        boolean aligned = hotbarY >= y + NATIVE_ROWS * StorageGrid.CELL && hotbarY + 16 <= nativeHeight;
        for (int i = 0; aligned && i < hotbar.length; i++) {
            aligned = hotbar[i] != null && hotbar[i].x == x + i * StorageGrid.CELL && hotbar[i].y == hotbarY;
        }
        int gridX = x - 1;
        int gridY = y - 1;
        int nativeBottom = mainPanel == null ? (screen.height - nativeHeight) / 2 + nativeHeight
                : mainPanel.getY() + nativeHeight;
        int available = screen.height - nativeBottom - BOTTOM_MARGIN;
        PlayerSection section = aligned ? PlayerSection.fit(available, searchInset(screen, gridX, gridY))
                : new PlayerSection(NATIVE_ROWS, 0, 0);
        var layout = (ContainerLayoutAccess) screen;
        Component label = layout.bigInventory$inventoryTitle();
        layout.bigInventory$setInventoryTitle(Component.empty());

        ClientViewport.View viewport = ClientViewport.open(screen.getMenu(), inventory);
        var font = Minecraft.getInstance().font;
        BackpackGrid grid;
        if (viewport != null) {
            section = section.fitNativeRows(viewport.slots.cells().size() / StorageGrid.COLUMNS);
            grid = new RealStorageGrid(viewport, font, query);
        } else {
            grid = new StorageGrid(ClientStorage.open(screen.getMenu()), font, query);
        }
        PlayerSlots slots = new PlayerSlots(screen.getMenu(), inventory);
        if (aligned) slots.arrange(0, hotbarY + section.hotbarShift());
        else slots.hideBackpack();
        active = new Panel(screen, slots, grid, label, gridX, gridY, nativeHeight, layout.bigInventory$imageHeight(),
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
        if (pending != null && pending.screen() == event.getScreen()) {
            PendingPanel ready = pending;
            pending = null;
            open(ready.screen(), ready.query());
        }
        if (active == null || active.screen() != event.getScreen()) return;
        var renderables = ((ScreenWidgets) active.screen()).bigInventory$renderables();
        for (var widget : active.grid().widgets()) {
            event.addListener(widget);
            renderables.remove(widget);
        }
        active.layout();
    }

    @SubscribeEvent
    public static void layout(ScreenEvent.Render.Pre event) {
        if (active == null || active.screen() != event.getScreen()) return;
        AbstractContainerScreen<?> screen = active.screen();
        // Some screens rebuild their native slots and widgets without firing Forge's init events.
        if (!screen.children().contains(active.grid().search())) {
            String query = active.grid().search().getValue();
            boolean nativeLayoutChanged = ((ContainerLayoutAccess) screen).bigInventory$imageHeight() != active.imageHeight();
            close(!nativeLayoutChanged);
            open(screen, query);
            if (active != null) {
                var widgets = (ScreenWidgets) screen;
                for (var widget : active.grid().widgets()) {
                    widgets.bigInventory$children().add(widget);
                    widgets.bigInventory$narratables().add(widget);
                }
            }
        }
        if (active != null) active.layout();
    }

    @SubscribeEvent
    public static void draw(ContainerScreenEvent.Render.Background event) {
        if (active == null || active.screen() != event.getContainerScreen()) return;
        if (active.visible()) {
            active.draw(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void drawSearch(ContainerScreenEvent.Render.Foreground event) {
        if (active == null || active.screen() != event.getContainerScreen() || !active.visible()) return;
        var screen = active.screen();
        var graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        try {
            // Several container screens draw their own inventory label after the normal widgets.
            graphics.pose().translate(-screen.getGuiLeft(), -screen.getGuiTop(), 0);
            for (var widget : active.grid().widgets()) {
                widget.render(graphics, event.getMouseX(), event.getMouseY(), Minecraft.getInstance().getFrameTime());
            }
        } finally {
            graphics.pose().popPose();
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
        if (pending != null && pending.screen() == event.getScreen()) pending = null;
        if (active != null && active.screen() == event.getScreen()) close(true);
    }

    private static void close(boolean restoreSlots) {
        if (restoreSlots) active.slots().restore();
        ((ContainerLayoutAccess) active.screen()).bigInventory$setInventoryTitle(active.label());
        active.grid().close();
        active = null;
    }

    public static boolean replacesPlayerInventory(AbstractContainerScreen<?> screen) {
        return active != null && active.screen() == screen && active.visible();
    }

    public static int extendedHeight(AbstractContainerScreen<?> screen, int nativeHeight) {
        if (!replacesPlayerInventory(screen) || active.section().extension() == 0) return nativeHeight;
        Rect2i panel = active.mainPanel();
        int bottom = panel.getY() + panel.getHeight() + active.section().extension();
        return Math.max(nativeHeight, bottom - screen.getGuiTop());
    }

    public static boolean insideExtension(AbstractContainerScreen<?> screen, double x, double y) {
        if (!replacesPlayerInventory(screen) || active.section().extension() == 0) return false;
        Rect2i panel = active.mainPanel();
        int bottom = panel.getY() + panel.getHeight();
        return y >= bottom && y < bottom + active.section().extension()
                && x >= panel.getX() && x < panel.getX() + panel.getWidth();
    }

    private record PendingPanel(AbstractContainerScreen<?> screen, String query) {}

    private record PlayerSection(int rows, int searchInset, int gap) {
        static PlayerSection fit(int available, int searchInset) {
            int gap = Mth.clamp(available - searchInset, 0, StorageGrid.SEARCH_GAP);
            int extra = Math.floorDiv(available - searchInset - gap, StorageGrid.CELL);
            return new PlayerSection(Mth.clamp(NATIVE_ROWS + extra, 1, StorageGrid.MAX_ROWS), searchInset, gap);
        }

        PlayerSection fitNativeRows(int limit) {
            int fittedRows = Math.min(rows, limit);
            return new PlayerSection(fittedRows, searchInset, fittedRows == NATIVE_ROWS && searchInset == 0 ? 0 : gap);
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

    private record Panel(AbstractContainerScreen<?> screen, PlayerSlots slots, BackpackGrid grid,
                         Component label, int gridX, int nativeGridY,
                         int nativeHeight, int imageHeight, int nativeHotbarY, PlayerSection section) {
        boolean visible() {
            return !(screen instanceof RecipeUpdateListener recipe && recipe.getRecipeBookComponent().isVisible()
                    && screen.width < 379);
        }

        Rect2i mainPanel() {
            return screen instanceof ContainerPanelBounds modular ? modular.bigInventory$mainPanel()
                    : new Rect2i(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), nativeHeight);
        }

        void layout() {
            Rect2i panel = mainPanel();
            int x = panel.getX() + gridX;
            int y = panel.getY() + nativeGridY;
            grid.bounds(x, y + section.gridInset(), screen.getGuiLeft(), screen.getGuiTop(),
                    section.rows(), visible(), section.gap());
            if (!grid.search().visible && screen.getFocused() == grid.search()) screen.setFocused(null);
        }

        void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            Rect2i panel = mainPanel();
            int left = panel.getX();
            int top = panel.getY();
            int x = left + gridX;
            int extension = section.extension();
            if (extension > 0) InventorySkin.extendPanel(graphics, left, top + panel.getHeight(), panel.getWidth(), extension);
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
