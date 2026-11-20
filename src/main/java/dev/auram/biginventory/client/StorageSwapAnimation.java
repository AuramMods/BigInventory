package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.StorageNetwork.Entry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Screen-local animation for transfers the vanilla slot tracker cannot observe. */
record StorageSwapAnimation(SmoothSwappingCompat.Timing timing, long startMillis, List<Flight> flights,
                            @Nullable Entry newEntry, @Nullable ItemStack pickedUp) {
    @Nullable
    static StorageSwapAnimation start(AbstractContainerScreen<?> screen, AbstractContainerMenu menu,
                                      StoragePrediction preview, Point storage, SmoothSwappingCompat.Timing timing) {
        StoragePrediction.Transfer transfer = preview.transfer();
        ItemStack icon = transfer.entry().icon();
        List<Flight> flights = new ArrayList<>();
        Entry newEntry = null;
        ItemStack pickedUp = null;
        if (transfer.delta() > 0) {
            Point origin = cursorPosition();
            if (!preview.slots().isEmpty()) {
                Slot source = preview.slots().keySet().iterator().next();
                origin = slotPosition(screen, menu, source);
                if (origin == null) return null;
            }
            flights.add(new Flight(icon, origin, storage, null, null, ItemStack.EMPTY));
            if (transfer.entry().count() == transfer.delta()) newEntry = transfer.entry();
        } else if (preview.cursor() != null) {
            pickedUp = preview.cursor();
            flights.add(new Flight(icon, storage, null, null, pickedUp, ItemStack.EMPTY));
        } else {
            for (var change : preview.slots().entrySet()) {
                Slot slot = change.getKey();
                Point destination = slotPosition(screen, menu, slot);
                if (destination == null) continue;
                flights.add(new Flight(icon, storage, destination, slot, change.getValue(), slot.getItem().copy()));
            }
        }
        return flights.isEmpty() ? null : new StorageSwapAnimation(timing, Util.getMillis(), flights, newEntry, pickedUp);
    }

    boolean active() {
        return elapsed() < 1.0;
    }

    StorageSwapAnimation retargetDeposit(Point destination) {
        Flight flight = flights.get(0);
        if (destination.equals(flight.to())) return this;
        Flight retargeted = new Flight(flight.icon(), flight.from(), destination, null, null, ItemStack.EMPTY);
        return new StorageSwapAnimation(timing, startMillis, List.of(retargeted), newEntry, null);
    }

    boolean hides(Entry entry) {
        return newEntry != null && entry.count() == newEntry.count()
                && Backpack.matches(entry.icon(), newEntry.icon());
    }

    ItemStack drawSlot(Slot slot, ItemStack stack) {
        for (Flight flight : flights) {
            if (flight.slot() == slot && ItemStack.matches(flight.arrival(), stack)) return flight.previous();
        }
        return stack;
    }

    ItemStack drawCursor(ItemStack stack) {
        return pickedUp != null && ItemStack.matches(pickedUp, stack) ? ItemStack.EMPTY : stack;
    }

    void render(GuiGraphics graphics, AbstractContainerScreen<?> screen, int mouseX, int mouseY, ClientStorage.View view) {
        double elapsed = elapsed();
        if (elapsed >= 1.0) return;
        float progress = (float) timing.curve().applyAsDouble(elapsed);
        for (Flight flight : flights) {
            if (flight.slot() != null && !ItemStack.matches(flight.arrival(), view.renderedSlot(flight.slot()))) continue;
            if (flight.to() == null && !ItemStack.matches(flight.arrival(), view.renderedCarried())) continue;
            Point from = flight.from();
            Point to = flight.to() == null ? new Point(mouseX, mouseY) : flight.to();
            float x = Mth.lerp(progress, from.x(), to.x()) - screen.getGuiLeft();
            float y = Mth.lerp(progress, from.y(), to.y()) - screen.getGuiTop();
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 250);
                graphics.renderItem(flight.icon(), -8, -8);
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    private double elapsed() {
        return (Util.getMillis() - startMillis) / timing.durationMillis();
    }

    @Nullable
    private static Point slotPosition(AbstractContainerScreen<?> screen, AbstractContainerMenu menu, Slot slot) {
        if (screen.getMenu() != menu) {
            int index = menu.slots.indexOf(slot);
            if (index < 0 || index >= screen.getMenu().slots.size()) return null;
            slot = screen.getMenu().getSlot(index);
        }
        int x = screen.getGuiLeft() + slot.x + 8;
        int y = screen.getGuiTop() + slot.y + 8;
        return slot.isActive() && x >= 0 && x < screen.width && y >= 0 && y < screen.height
                ? new Point(x, y) : null;
    }

    private static Point cursorPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        float x = (float) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        float y = (float) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        return new Point(x, y);
    }

    record Point(float x, float y) {}

    private record Flight(ItemStack icon, Point from, @Nullable Point to, @Nullable Slot slot,
                          @Nullable ItemStack arrival, ItemStack previous) {}
}
