package dev.auram.biginventory.client;

import dev.auram.biginventory.BigInventory;
import dev.auram.biginventory.StorageNetwork.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BigInventory.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final ItemStack CHEST = new ItemStack(Items.CHEST);
    private static final int RING_STEPS = 80;
    private static final int[] CIRCLE_X = new int[RING_STEPS];
    private static final int[] CIRCLE_Y = new int[RING_STEPS];

    static {
        for (int i = 0; i < RING_STEPS; i++) {
            double angle = i * 2 * Math.PI / RING_STEPS;
            CIRCLE_X[i] = (int) Math.round(12 * Math.sin(angle));
            CIRCLE_Y[i] = (int) Math.round(-12 * Math.cos(angle));
        }
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        var player = Minecraft.getInstance().player;
        var screen = event.getNewScreen();
        if (player == null || screen == null || player.isSpectator()) return;
        if (screen.getClass() == InventoryScreen.class) {
            event.setNewScreen(new BackpackScreen(player));
        } else if (screen.getClass() == ContainerScreen.class && screen instanceof ContainerScreen chest) {
            event.setNewScreen(new BackpackChestScreen(chest.getMenu(), player.getInventory(), chest.getTitle()));
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStorage.reset();
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        Hud status = ClientStorage.hud();
        if (status == null || minecraft.player == null || minecraft.player.isSpectator()
                || minecraft.options.hideGui) return;
        int middle = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() - 14;
        boolean offhand = !minecraft.player.getOffhandItem().isEmpty();
        boolean mainRight = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
        int x = Math.max(14, middle - 111 - (offhand && mainRight ? 28 : 0));
        double remaining = status.capacity() == 0 ? 0 : Math.max(0, status.capacity() - status.used()) / (double) status.capacity();
        int percent = (int) Math.floor(remaining * 100);
        GuiGraphics graphics = event.getGuiGraphics();
        int color = status.used() > status.capacity() ? 0xffff907e : 0xff99e5c3;
        for (int i = 0; i < RING_STEPS; i++) {
            int dotX = x + CIRCLE_X[i];
            int dotY = y + CIRCLE_Y[i];
            graphics.fill(dotX, dotY, dotX + 2, dotY + 2, i < remaining * RING_STEPS ? color : 0xff35444a);
        }
        graphics.renderItem(CHEST, x - 7, y - 7);
        graphics.drawCenteredString(minecraft.font, percent + "%", x, y - 24, color);

        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.isEmpty() && !isTool(held) && status.slot() == minecraft.player.getInventory().selected
                && status.heldCount() > 0) {
            graphics.drawString(minecraft.font, "x" + status.heldCount(), middle + 97 + (offhand && !mainRight ? 28 : 0),
                    y - 4, 0xfff6faf5, true);
        }
    }

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
