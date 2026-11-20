package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.MenuPlayerSlots;
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
            int index = MenuPlayerSlots.index(slot, inventory);
            if (Inventory.isHotbarSlot(index) || Backpack.isStorageSlot(index)) {
                slots.add(new Position(slot, index, slot.x, slot.y));
            }
        }
    }

    void arrange(int hotbarOffsetX, int hotbarY) {
        for (Position pos : slots) {
            SlotPosition access = (SlotPosition) pos.slot();
            if (Inventory.isHotbarSlot(pos.index())) {
                access.bigInventory$setX(pos.x() + hotbarOffsetX);
                access.bigInventory$setY(hotbarY);
            } else {
                access.bigInventory$setX(-30000);
            }
        }
    }

    void hideBackpack() {
        for (Position pos : slots) {
            if (Backpack.isStorageSlot(pos.index())) {
                ((SlotPosition) pos.slot()).bigInventory$setX(-30000);
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

    private record Position(Slot slot, int index, int x, int y) {}
}
