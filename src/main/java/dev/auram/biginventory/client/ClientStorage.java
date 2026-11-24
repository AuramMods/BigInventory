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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.nbt.CompoundTag;
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
import java.util.LinkedHashSet;
import java.util.List;

public final class ClientStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Hud hud;
    private static int tokens;
    private static View view;

    static Hud hud() { return hud; }

    public static void hud(Hud message) {
        hud = message;
        var player = Minecraft.getInstance().player;
        if (player != null) Backpack.of(player.getInventory()).setPredictionRules(message);
    }

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
        ClientViewport.reset();
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

    @Nullable
    static StorageGrid grid(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return null;
        View visible = visibleView(container.getMenu());
        return visible == null ? null : visible.grid;
    }

    public static boolean interceptMenuClick(MultiPlayerGameMode gameMode, int menuId, int slot, int button, ClickType type, Player player) {
        if (view == null || view.menu != player.containerMenu || view.menu.containerId != menuId) return false;
        if (view.grid != null) view.grid.cancelAnimation();
        Slot target = slot >= 0 && slot < view.menu.slots.size() ? view.menu.getSlot(slot) : null;
        boolean storeHotbar = type == ClickType.QUICK_MOVE && view.menu instanceof InventoryMenu && target != null
                && target.container == player.getInventory() && Inventory.isHotbarSlot(target.getSlotIndex());
        if (storeHotbar && view.revision != 0 && view.renderedCarried().isEmpty()) {
            view.queueHotbar(slot, view.renderedSlot(target));
            return true;
        }
        ItemStack proposedSlot = target == null ? null : view.predictedSlot(target);
        if (view.predictedCursor() != null || proposedSlot != null) {
            // Replay a follow-up pickup only if the server confirms the state it was aimed at.
            if (type == ClickType.PICKUP && target != null && view.deferredClick == null) {
                ItemStack expectedSlot = proposedSlot != null ? proposedSlot : target.getItem();
                view.deferredClick = new MenuClick(gameMode, target, button, player, view.renderedCarried().copy(), expectedSlot.copy());
            }
            return true;
        }
        if (storeHotbar) {
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
        @Nullable private Entry pendingContainerTransfer;
        private int acknowledgedRevision = -1;
        private MenuClick deferredClick;
        private final LinkedHashSet<ShiftRequest> queuedShifts = new LinkedHashSet<>();

        View(AbstractContainerMenu menu, int token) {
            this.menu = menu;
            this.token = token;
        }

        boolean act(int id, Gesture gesture) {
            if (!readyToAct()) return false;
            var player = Minecraft.getInstance().player;
            if (player == null) return false;
            try {
                prediction = StoragePrediction.preview(entries, gesture, id, menu, player.getInventory(), hud);
                if (gesture == Gesture.SHIFT_TAKE && !(menu instanceof InventoryMenu)) {
                    pendingContainerTransfer = entries.get(id);
                }
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
            // Virtual slots bypass the vanilla click that Sounds listens for.
            var transfer = prediction.transfer();
            if (transfer != null) SoundsCompat.playItemClick(transfer.entry().icon());
            return true;
        }

        private boolean readyToAct() {
            return view == this && prediction == null && revision != 0 && revision == incomingRevision;
        }

        void queueShiftTake(CompoundTag variant) {
            queuedShifts.add(new ShiftRequest(-1, variant, 0));
            sendNextShift();
        }

        private void queueHotbar(int slot, ItemStack expected) {
            if (expected.isEmpty()) return;
            queuedShifts.add(new ShiftRequest(slot, Backpack.variantKey(expected), expected.getCount()));
            sendNextShift();
        }

        private void sendNextShift() {
            if (queuedShifts.isEmpty() || !readyToAct()) return;
            if (!menu.getCarried().isEmpty()) {
                queuedShifts.clear();
                return;
            }
            var pending = queuedShifts.iterator();
            while (pending.hasNext()) {
                ShiftRequest request = pending.next();
                pending.remove();
                int index = request.resolve(menu, entries);
                if (index >= 0 && act(index, request.gesture())) return;
            }
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
            if (receiptRevision == 0) {
                revision = 0;
                queuedShifts.clear();
            }
            reconcilePrediction();

            MenuClick click = deferredClick;
            deferredClick = null;
            if (click != null && receiptRevision != 0 && click.player().containerMenu == menu
                    && ItemStack.matches(click.cursor(), menu.getCarried()) && ItemStack.matches(click.stack(), click.slot().getItem())) {
                int index = menu.slots.indexOf(click.slot());
                if (index >= 0) click.gameMode().handleInventoryMouseClick(menu.containerId, index, click.button(), ClickType.PICKUP, click.player());
            }
            sendNextShift();
        }

        private void reconcilePrediction() {
            if (prediction != null && acknowledgedRevision >= 0 && revision >= acknowledgedRevision) {
                if (animation() != null) reconcileAnimation();
                Entry before = pendingContainerTransfer;
                pendingContainerTransfer = null;
                if (before != null && acknowledgedRevision != 0) {
                    Entry after = findEntry(before.icon());
                    if (after == null || after.count() < before.count()) SoundsCompat.playItemClick(before.icon());
                }
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
            Entry received = findEntry(transfer.entry().icon());
            long count = received == null ? 0 : received.count();
            if (count != transfer.expectedCount()) grid.cancelAnimation();
            else if (transfer.delta() > 0 && received != null) grid.retargetDeposit(received, entries);
        }

        @Nullable
        private Entry findEntry(ItemStack pattern) {
            var key = Backpack.variantKey(pattern);
            return entries.stream()
                    .filter(entry -> entry.icon().is(pattern.getItem()) && Backpack.variantKey(entry.icon()).equals(key))
                    .findFirst().orElse(null);
        }

        void applyCompleteView() {
            if (incoming.size() == total) {
                entries = List.copyOf(incoming);
                revision = incomingRevision;
                changes++;
                reconcilePrediction();
                var player = Minecraft.getInstance().player;
                if (player != null) player.getInventory().setChanged();
                sendNextShift();
            }
        }
    }

    private record ShiftRequest(int hotbarSlot, CompoundTag variant, int expectedCount) {
        Gesture gesture() {
            return hotbarSlot < 0 ? Gesture.SHIFT_TAKE : Gesture.STORE_HOTBAR;
        }

        int resolve(AbstractContainerMenu menu, List<Entry> entries) {
            if (hotbarSlot >= 0) {
                if (hotbarSlot >= menu.slots.size()) return -1;
                ItemStack stack = menu.getSlot(hotbarSlot).getItem();
                return !stack.isEmpty() && stack.getCount() == expectedCount && variant.equals(Backpack.variantKey(stack))
                        ? hotbarSlot : -1;
            }
            // A server refresh can assign a different ID to every remaining entry.
            for (Entry entry : entries) {
                if (variant.equals(Backpack.variantKey(entry.icon()))) return entry.id();
            }
            return -1;
        }
    }

    private record MenuClick(MultiPlayerGameMode gameMode, Slot slot, int button, Player player, ItemStack cursor, ItemStack stack) {}

    private ClientStorage() {}
}
