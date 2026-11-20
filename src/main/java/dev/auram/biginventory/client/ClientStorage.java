package dev.auram.biginventory.client;

import com.mojang.logging.LogUtils;
import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.StorageNetwork;
import dev.auram.biginventory.StorageNetwork.Action;
import dev.auram.biginventory.StorageNetwork.Entry;
import dev.auram.biginventory.StorageNetwork.Gesture;
import dev.auram.biginventory.StorageNetwork.Hud;
import dev.auram.biginventory.StorageNetwork.ViewAck;
import dev.auram.biginventory.StorageNetwork.ViewPage;
import dev.auram.biginventory.StorageNetwork.ViewRequest;
import dev.auram.biginventory.StorageNetwork.ViewStart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ContainerScreenEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class ClientStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Hud hud;
    private static int tokens;
    private static View view;

    static Hud hud() { return hud; }

    public static void hud(Hud message) { hud = message; }

    static View open(AbstractContainerMenu menu) {
        return open(menu, null);
    }

    static View open(AbstractContainerMenu menu, @Nullable ItemStack creativeCursor) {
        if (view != null) close(view);
        view = new View(menu, ++tokens);
        StorageNetwork.request(new ViewRequest(menu.containerId, view.token, true, creativeCursor));
        return view;
    }

    static void close(View closing) {
        if (closing != null && view == closing) {
            StorageNetwork.request(new ViewRequest(view.menu.containerId, view.token, false, null));
            view = null;
        }
    }

    static void reset() {
        view = null;
        hud = null;
    }

    public static void start(ViewStart start) {
        if (view != null && view.token == start.token()) {
            view.incomingRevision = start.revision();
            view.total = start.entries();
            view.incoming.clear();
            view.applyCompleteView();
        }
    }

    public static void page(ViewPage page) {
        if (view != null && view.token == page.token() && view.incomingRevision == page.revision()) {
            view.incoming.addAll(page.entries());
            view.applyCompleteView();
        }
    }

    public static void ack(ViewAck ack) {
        if (view != null && view.token == ack.token()) view.acknowledge(ack.revision());
    }

    public static ItemStack renderedCarried(AbstractContainerMenu menu, boolean animate) {
        View visible = visibleView(menu);
        if (visible == null) return menu.getCarried();
        ItemStack stack = visible.renderedCarried();
        var animation = animate ? visible.animation() : null;
        return animation == null ? stack : animation.drawCursor(stack);
    }

    public static ItemStack renderedSlot(AbstractContainerMenu menu, Slot slot, boolean animate) {
        View visible = visibleView(menu);
        if (visible == null) return slot.getItem();
        Slot backing = slot;
        if (visible.menu != menu) {
            int index = menu.slots.indexOf(slot);
            if (index < 0 || index >= visible.menu.slots.size()) return slot.getItem();
            backing = visible.menu.getSlot(index);
        }
        ItemStack predicted = visible.predictedSlot(backing);
        ItemStack stack = predicted == null ? slot.getItem() : predicted;
        var animation = animate ? visible.animation() : null;
        return animation == null ? stack : animation.drawSlot(backing, stack);
    }

    static void renderTransfers(ContainerScreenEvent.Render.Foreground event) {
        var screen = event.getContainerScreen();
        View visible = visibleView(screen.getMenu());
        var animation = visible == null ? null : visible.animation();
        if (animation != null) {
            animation.render(event.getGuiGraphics(), screen, event.getMouseX(), event.getMouseY(), visible);
        }
    }

    @Nullable
    private static View visibleView(AbstractContainerMenu menu) {
        return view != null && (view.menu == menu || Minecraft.getInstance().screen instanceof BackpackCreativeScreen screen
                && screen.isStorageOpen() && screen.getMenu() == menu) ? view : null;
    }

    public static boolean interceptMenuClick(MultiPlayerGameMode gameMode, int menuId, int slot, int button, ClickType type, Player player) {
        if (view == null || view.menu != player.containerMenu || view.menu.containerId != menuId) return false;
        if (view.grid != null) view.grid.cancelAnimation();
        Slot target = slot >= 0 && slot < view.menu.slots.size() ? view.menu.getSlot(slot) : null;
        ItemStack proposedSlot = target == null ? null : view.predictedSlot(target);
        if (view.predictedCursor() != null || proposedSlot != null) {
            // Replay a follow-up pickup only if the server confirms the state it was aimed at.
            if (type == ClickType.PICKUP && target != null && view.deferredClick == null) {
                ItemStack expectedSlot = proposedSlot != null ? proposedSlot : target.getItem();
                view.deferredClick = new MenuClick(gameMode, target, button, player, view.renderedCarried().copy(), expectedSlot.copy());
            }
            return true;
        }
        if (type == ClickType.QUICK_MOVE && view.menu instanceof InventoryMenu && target != null
                && target.container == player.getInventory() && Inventory.isHotbarSlot(target.getSlotIndex())) {
            return view.act(slot, Gesture.STORE_HOTBAR) || view.prediction != null;
        }
        return false;
    }

    public static void accountOverflow(StackedContents contents) {
        if (view == null) return;
        for (Entry entry : view.entries) {
            ItemStack stack = entry.icon();
            if (entry.overflowCount() > 0 && !stack.isDamaged() && !stack.isEnchanted() && !stack.hasCustomHoverName()) {
                int count = (int) Math.min(Integer.MAX_VALUE, entry.overflowCount());
                contents.accountStack(stack.copyWithCount(count), count);
            }
        }
    }

    static final class View {
        final AbstractContainerMenu menu;
        final int token;
        final List<Entry> incoming = new ArrayList<>();
        List<Entry> entries = List.of();
        int incomingRevision;
        int revision;
        int total;
        int changes;
        StorageGrid grid;
        private StoragePrediction prediction;
        private int acknowledgedRevision = -1;
        private MenuClick deferredClick;

        View(AbstractContainerMenu menu, int token) {
            this.menu = menu;
            this.token = token;
        }

        boolean act(int id, Gesture gesture) {
            if (view != this || prediction != null || revision == 0 || revision != incomingRevision) return false;
            var player = Minecraft.getInstance().player;
            if (player == null) return false;
            try {
                prediction = StoragePrediction.preview(entries, gesture, id, menu, player.getInventory(), hud);
            } catch (RuntimeException failure) {
                // Some modded slots only support transfer queries on the server.
                LOGGER.debug("Cannot preview storage action {} in {}", gesture, menu.getClass().getName(), failure);
                prediction = new StoragePrediction(entries, null);
            }
            if (grid != null && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                    && visibleView(screen.getMenu()) == this) {
                try {
                    grid.startAnimation(screen, prediction);
                } catch (RuntimeException failure) {
                    LOGGER.debug("Cannot animate storage action {} in {}", gesture, menu.getClass().getName(), failure);
                }
            }
            acknowledgedRevision = -1;
            changes++;
            StorageNetwork.action(new Action(menu.containerId, token, revision, id, gesture));
            return true;
        }

        List<Entry> displayedEntries() {
            return prediction == null ? entries : prediction.entries();
        }

        ItemStack renderedCarried() {
            ItemStack cursor = predictedCursor();
            return cursor == null ? menu.getCarried() : cursor;
        }

        ItemStack renderedSlot(Slot slot) {
            ItemStack predicted = predictedSlot(slot);
            return predicted == null ? slot.getItem() : predicted;
        }

        @Nullable
        private StorageSwapAnimation animation() {
            return grid == null ? null : grid.animation();
        }

        @Nullable
        private ItemStack predictedCursor() {
            return prediction == null || acknowledgedRevision >= 0 ? null : prediction.cursor();
        }

        @Nullable
        private ItemStack predictedSlot(Slot slot) {
            return prediction == null || acknowledgedRevision >= 0 ? null : prediction.slots().get(slot);
        }

        void acknowledge(int receiptRevision) {
            if (prediction == null) return;
            acknowledgedRevision = receiptRevision;
            if (receiptRevision == 0) revision = 0;
            reconcilePrediction();

            MenuClick click = deferredClick;
            deferredClick = null;
            if (click != null && receiptRevision != 0 && click.player().containerMenu == menu
                    && ItemStack.matches(click.cursor(), menu.getCarried()) && ItemStack.matches(click.stack(), click.slot().getItem())) {
                int index = menu.slots.indexOf(click.slot());
                if (index >= 0) click.gameMode().handleInventoryMouseClick(menu.containerId, index, click.button(), ClickType.PICKUP, click.player());
            }
        }

        private void reconcilePrediction() {
            if (prediction != null && acknowledgedRevision >= 0 && revision >= acknowledgedRevision) {
                if (animation() != null) reconcileAnimation();
                prediction = null;
                changes++;
            }
        }

        private void reconcileAnimation() {
            var transfer = prediction.transfer();
            if (acknowledgedRevision == 0 || transfer == null) {
                grid.cancelAnimation();
                return;
            }
            var key = Backpack.variantKey(transfer.entry().icon());
            Entry received = entries.stream()
                    .filter(entry -> entry.icon().is(transfer.entry().icon().getItem())
                            && Backpack.variantKey(entry.icon()).equals(key))
                    .findFirst().orElse(null);
            long count = received == null ? 0 : received.count();
            if (count != transfer.expectedCount()) grid.cancelAnimation();
            else if (transfer.delta() > 0 && received != null) grid.retargetDeposit(received, entries);
        }

        void applyCompleteView() {
            if (incoming.size() == total) {
                entries = List.copyOf(incoming);
                revision = incomingRevision;
                changes++;
                reconcilePrediction();
                var player = Minecraft.getInstance().player;
                if (player != null) player.getInventory().setChanged();
            }
        }
    }

    private record MenuClick(MultiPlayerGameMode gameMode, Slot slot, int button, Player player, ItemStack cursor, ItemStack stack) {}

    private ClientStorage() {}
}
