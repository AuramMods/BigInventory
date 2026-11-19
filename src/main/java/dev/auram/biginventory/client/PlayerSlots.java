package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.mixin.SlotPosition;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Client coordinates only. Menu indices and server slot objects stay stable. */
final class PlayerSlots {
    private final List<Position> slots = new ArrayList<>();

    PlayerSlots(AbstractContainerMenu menu, Inventory inventory) {
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && (slot.getSlotIndex() < Backpack.FIRST
                    || Backpack.isStorageSlot(slot.getSlotIndex()))) {
                slots.add(new Position(slot, slot.x, slot.y));
            }
        }
    }

    void arrange(int hotbarOffsetX, int hotbarY) {
        for (Position pos : slots) {
            SlotPosition access = (SlotPosition) pos.slot();
            if (Inventory.isHotbarSlot(pos.slot().getSlotIndex())) {
                access.bigInventory$setX(pos.x() + hotbarOffsetX);
                access.bigInventory$setY(hotbarY);
            } else {
                access.bigInventory$setX(-30000);
            }
        }
    }

    void restore() {
        for (Position pos : slots) {
            SlotPosition access = (SlotPosition) pos.slot();
            access.bigInventory$setX(pos.x());
            access.bigInventory$setY(pos.y());
        }
    }

    private record Position(Slot slot, int x, int y) {}
}
