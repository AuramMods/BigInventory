package dev.auram.biginventory.client;

import dev.auram.biginventory.BigInventory;
import dev.auram.biginventory.StorageNetwork.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = BigInventory.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final ItemStack CHEST = new ItemStack(Items.CHEST);
    private static final int RING_RADIUS = 8;
    private static final List<RingPixel> RING_PIXELS = ringPixels();
    private static Screen sweepingScreen;

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        var player = Minecraft.getInstance().player;
        var screen = event.getNewScreen();
        if (player == null || screen == null || player.isSpectator()) return;
        if (screen.getClass() == InventoryScreen.class) {
            event.setNewScreen(new BackpackScreen(player));
        } else if (screen.getClass() == CreativeModeInventoryScreen.class) {
            event.setNewScreen(new BackpackCreativeScreen(player, player.connection.enabledFeatures(),
                    Minecraft.getInstance().options.operatorItemsTab().get()));
        } else if (screen.getClass() == ContainerScreen.class && screen instanceof ContainerScreen chest) {
            event.setNewScreen(new BackpackChestScreen(chest.getMenu(), player.getInventory(), chest.getTitle()));
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        sweepingScreen = null;
        ClientStorage.reset();
    }

    // Mouse Tweaks reads its settings on the native press event before we claim a storage sweep.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void startStorageSweep(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (sweepingScreen != null) {
            var previous = ClientStorage.grid(sweepingScreen);
            if (previous != null) previous.stopSweep();
            sweepingScreen = null;
        }
        if (!Screen.hasShiftDown()) return;
        var grid = ClientStorage.grid(event.getScreen());
        if (grid != null && grid.startSweep(event.getMouseX(), event.getMouseY())) {
            sweepingScreen = event.getScreen();
            sweepingScreen.setFocused(null);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void dragStorageSweep(ScreenEvent.MouseDragged.Pre event) {
        if (event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || sweepingScreen != event.getScreen()) return;
        var grid = ClientStorage.grid(event.getScreen());
        if (grid != null) grid.dragSweep(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void finishStorageSweep(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || sweepingScreen != event.getScreen()) return;
        var grid = ClientStorage.grid(event.getScreen());
        if (grid != null) grid.stopSweep();
        sweepingScreen = null;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void closeStorageSweep(ScreenEvent.Closing event) {
        if (sweepingScreen == event.getScreen()) sweepingScreen = null;
    }

    @SubscribeEvent
    public static void renderTransfers(ContainerScreenEvent.Render.Foreground event) {
        ClientStorage.renderTransfers(event);
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        Hud status = ClientStorage.hud();
        if (status == null || minecraft.player == null || minecraft.player.isSpectator()
                || minecraft.options.hideGui) return;
        int middle = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() - 11;
        boolean offhand = !minecraft.player.getOffhandItem().isEmpty();
        boolean mainRight = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        int x = Math.max(RING_RADIUS + 1, middle - 104 - (offhand && mainRight ? 28 : 0));
        long denominator = Math.max(1, status.capacity());
        double used = status.used() / (double) denominator;
        double fill = Math.min(1, used);
        int storageColor = status.used() > status.capacity() ? 0xffff5555 : 0xffffffff;
        GuiGraphics graphics = event.getGuiGraphics();
        if (fill > 0) {
            for (RingPixel pixel : RING_PIXELS) {
                if (pixel.turn() < fill) {
                    int px = x + pixel.x();
                    int py = y + pixel.y();
                    graphics.fill(px, py, px + 1, py + 1, storageColor);
                }
            }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.625f, 0.625f, 1);
        graphics.renderItem(CHEST, -8, -8);
        graphics.pose().popPose();
        if (minecraft.player.isCrouching()) {
            String percent = (long) (status.used() * 100.0 / denominator) + "%";
            graphics.pose().pushPose();
            graphics.pose().translate(x - RING_RADIUS - 3, y, 0);
            graphics.pose().scale(0.75f, 0.75f, 1);
            graphics.drawString(minecraft.font, percent, -minecraft.font.width(percent), -4, storageColor, true);
            graphics.pose().popPose();
        }

        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.isEmpty() && !isTool(held) && status.slot() == minecraft.player.getInventory().selected
                && status.heldCount() > 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(middle + 97 + (offhand && !mainRight ? 28 : 0), y - 3, 0);
            graphics.pose().scale(0.75f, 0.75f, 1);
            graphics.drawString(minecraft.font, "x" + ItemCount.format(status.heldCount()), 0, 0, 0xffffffff, true);
            graphics.pose().popPose();
        }
    }

    private static List<RingPixel> ringPixels() {
        List<RingPixel> pixels = new ArrayList<>();
        for (int y = -RING_RADIUS; y <= RING_RADIUS; y++) {
            for (int x = -RING_RADIUS; x <= RING_RADIUS; x++) {
                double distance = Math.hypot(x, y);
                if (distance >= RING_RADIUS - 0.8 && distance <= RING_RADIUS + 0.35) {
                    double angle = Math.atan2(x, -y) / (2 * Math.PI);
                    pixels.add(new RingPixel(x, y, angle < 0 ? angle + 1 : angle));
                }
            }
        }
        return List.copyOf(pixels);
    }

    private record RingPixel(int x, int y, double turn) {}

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof TieredItem || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof ShearsItem || stack.getItem() instanceof ShieldItem
                || stack.canPerformAction(ToolActions.PICKAXE_DIG) || stack.canPerformAction(ToolActions.AXE_DIG)
                || stack.canPerformAction(ToolActions.SHOVEL_DIG) || stack.canPerformAction(ToolActions.HOE_DIG)
                || stack.canPerformAction(ToolActions.SWORD_DIG);
    }

    private ClientEvents() {}
}
