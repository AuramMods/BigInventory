package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BigInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Keeps the native menu and replaces a conventional three-row player grid in place. */
@Mod.EventBusSubscriber(modid = BigInventory.MOD_ID, value = Dist.CLIENT)
public final class ContainerBackpack {
    private static Panel active;

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof BackpackScreen || screen instanceof BackpackChestScreen
                || screen instanceof CreativeModeInventoryScreen) return;
        String owner = screen.getClass().getPackageName();
        if (!owner.equals("net.minecraft.client.gui.screens.inventory")
                && !owner.equals("top.theillusivec4.curios.client.gui")) return;
        if (active != null) close();
        var player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        Inventory inventory = player.getInventory();
        Slot[] legacy = new Slot[Backpack.LEGACY_END - Backpack.FIRST];
        for (Slot slot : screen.getMenu().slots) {
            int index = slot.getSlotIndex();
            if (slot.container == inventory && index >= Backpack.FIRST && index < Backpack.LEGACY_END) {
                legacy[index - Backpack.FIRST] = slot;
            }
        }
        if (legacy[0] == null) return;
        int x = legacy[0].x;
        int y = legacy[0].y;
        for (int i = 0; i < legacy.length; i++) {
            if (legacy[i] == null || legacy[i].x != x + (i % 9) * 18 || legacy[i].y != y + (i / 9) * 18) return;
        }

        ClientStorage.View view = ClientStorage.open(screen.getMenu().containerId);
        PlayerSlots slots = new PlayerSlots(screen.getMenu(), inventory);
        slots.hideBackpack();
        StorageGrid grid = new StorageGrid(view, Minecraft.getInstance().font, "");
        event.addListener(grid.search());
        active = new Panel(screen, slots, grid, view, x, y);
        active.layout();
    }

    @SubscribeEvent
    public static void draw(ContainerScreenEvent.Render.Background event) {
        if (active == null || active.screen() != event.getContainerScreen()) return;
        active.layout();
        if (active.visible()) {
            int x = active.screen().getGuiLeft() + active.slotX();
            int y = active.screen().getGuiTop() + active.slotY();
            event.getGuiGraphics().fill(x - 1, y - 1, x + 161, y + 53, 0xff354853);
            active.grid().render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void tooltip(ScreenEvent.Render.Post event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()) {
            active.grid().tooltip(event.getGuiGraphics(), active.screen().getMenu().getCarried(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (active != null && active.screen() == event.getScreen() && active.visible()
                && active.grid().click(event.getMouseX(), event.getMouseY(), event.getButton(), active.screen().getMenu().getCarried())) {
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
        if (active != null && active.screen() == event.getScreen() && active.screen().getFocused() == active.grid().search()
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
        ClientStorage.close(active.view());
        active = null;
    }

    private record Panel(AbstractContainerScreen<?> screen, PlayerSlots slots, StorageGrid grid,
                         ClientStorage.View view, int slotX, int slotY) {
        boolean visible() {
            return !(screen instanceof RecipeUpdateListener recipe && recipe.getRecipeBookComponent().isVisible()
                    && screen.width < 379);
        }

        void layout() {
            int x = screen.getGuiLeft() + slotX;
            int y = screen.getGuiTop() + slotY;
            grid.bounds(x, y, 9, 3);
            int searchY = screen.getGuiTop() - StorageGrid.SEARCH_HEIGHT - 4;
            if (searchY < 0) searchY = screen.getGuiTop() + screen.getYSize() + 4;
            grid.search().setPosition(x, searchY);
            grid.search().visible = visible() && searchY + StorageGrid.SEARCH_HEIGHT <= screen.height;
            if (!grid.search().visible && screen.getFocused() == grid.search()) screen.setFocused(null);
        }
    }

    private ContainerBackpack() {}
}
