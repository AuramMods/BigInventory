package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackViewport;
import dev.auram.biginventory.MenuPlayerSlots;
import dev.auram.biginventory.MenuStorageAccess;
import dev.auram.biginventory.SlotBinding;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerMenu.class)
abstract class ContainerMenuMixin implements MenuStorageAccess {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow protected abstract Slot addSlot(Slot slot);
    @Shadow protected abstract void resetQuickCraft();
    @Shadow protected abstract boolean moveItemStackTo(ItemStack stack, int from, int to, boolean reverse);
    @Shadow public abstract ItemStack getCarried();
    @Shadow public abstract boolean canTakeItemForPickAll(ItemStack stack, Slot slot);
    @Shadow private int quickcraftStatus;
    @Unique private Inventory bigInventory$exportingInventory;
    @Unique private ItemStack bigInventory$borrowedCursor = ItemStack.EMPTY;
    @Unique private ItemStack bigInventory$pickupCandidate = ItemStack.EMPTY;
    @Unique private int bigInventory$beforeClickCount;
    @Unique private long bigInventory$beforeCollect = -1;

    @Override
    public void bigInventory$reserveViewportSlots(Inventory inventory) {
        for (int i = BackpackViewport.NATIVE_CELLS; i < BackpackViewport.MAX_CELLS; i++) {
            Slot cell = new Slot(inventory, Backpack.slotAt(i), -30000, -30000);
            ((SlotBinding) cell).bigInventory$reserve();
            addSlot(cell);
        }
    }

    @Override
    public void bigInventory$resetQuickCraft() {
        resetQuickCraft();
    }

    @Inject(method = "clicked", at = @At("HEAD"))
    private void bigInventory$startCursorClick(int index, int button, ClickType type, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer)) return;
        ItemStack carried = getCarried();
        bigInventory$beforeClickCount = carried.getCount();
        bigInventory$pickupCandidate = ItemStack.EMPTY;
        bigInventory$beforeCollect = -1;
        if (quickcraftStatus != 0 || index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        if (!BackpackViewport.isBound(slot) || slot.container != player.getInventory()) return;
        if (type == ClickType.PICKUP && slot.hasItem() && !Backpack.matches(slot.getItem(), carried)) {
            bigInventory$pickupCandidate = slot.getItem().copy();
        } else if (type == ClickType.PICKUP_ALL && !carried.isEmpty()
                && (!slot.hasItem() || !slot.mayPickup(player)) && canTakeItemForPickAll(carried, slot)) {
            bigInventory$beforeCollect = Backpack.of(player.getInventory()).countStored(carried);
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void bigInventory$finishCursorClick(int index, int button, ClickType type, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer)) return;
        ItemStack carried = getCarried();
        int borrowed = Backpack.matches(carried, bigInventory$borrowedCursor)
                ? bigInventory$borrowedCursor.getCount() - Math.max(0, bigInventory$beforeClickCount - carried.getCount()) : 0;
        if (Backpack.matches(carried, bigInventory$pickupCandidate)) {
            ItemStack left = slots.get(index).getItem();
            int remaining = Backpack.matches(carried, left) ? left.getCount() : 0;
            borrowed = bigInventory$pickupCandidate.getCount() - remaining;
        }
        if (bigInventory$beforeCollect >= 0 && !carried.isEmpty()) {
            Backpack backpack = Backpack.of(player.getInventory());
            ItemStack extra = backpack.takeMatching(carried, Backpack.storageStackLimit(carried) - carried.getCount());
            carried.grow(extra.getCount());
            long withdrawn = bigInventory$beforeCollect - backpack.countStored(carried);
            borrowed += (int) Math.max(0, Math.min(withdrawn, carried.getCount() - bigInventory$beforeClickCount));
        }
        borrowed = Math.min(borrowed, carried.getCount());
        bigInventory$borrowedCursor = borrowed > 0 ? carried.copyWithCount(borrowed) : ItemStack.EMPTY;
        bigInventory$pickupCandidate = ItemStack.EMPTY;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void bigInventory$returnCursorOnClose(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) bigInventory$recoverCursor(serverPlayer, false);
    }

    @Override
    public void bigInventory$recoverCursor(ServerPlayer player, boolean disconnecting) {
        ItemStack carried = getCarried();
        if (player.isAlive() && Backpack.matches(carried, bigInventory$borrowedCursor)) {
            Backpack.of(player.getInventory()).recoverCursor(carried, bigInventory$borrowedCursor.getCount(), disconnecting);
        }
        bigInventory$borrowedCursor = ItemStack.EMPTY;
    }

    @Override
    public void bigInventory$runStorageExport(Inventory inventory, Runnable transfer) {
        Inventory previous = bigInventory$exportingInventory;
        bigInventory$exportingInventory = inventory;
        try {
            transfer.run();
        } finally {
            bigInventory$exportingInventory = previous;
        }
    }

    @Inject(method = {"broadcastChanges", "broadcastFullState", "sendAllDataToRemote"},
            at = @At("HEAD"), cancellable = true)
    private void bigInventory$deferStorageExportSync(CallbackInfo ci) {
        if (bigInventory$exportingInventory != null) ci.cancel();
    }

    @Inject(method = "doClick", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true)
    private void bigInventory$storeHotbarStack(int slotIndex, int button, ClickType click, Player player, CallbackInfo ci) {
        if (!((Object) this instanceof InventoryMenu)) return;
        Inventory inventory = player.getInventory();
        Slot slot = slots.get(slotIndex);
        if (slot.container != inventory) return;
        if (Inventory.isHotbarSlot(slot.getSlotIndex())) {
            if (((SlotBinding) slots.get(InventoryMenu.INV_SLOT_START)).bigInventory$binding() != SlotBinding.UNBOUND) {
                ItemStack stack = slot.getItem();
                int original = stack.getCount();
                BackpackViewport viewport = BackpackViewport.find((InventoryMenu) (Object) this, inventory);
                if (viewport != null) bigInventory$fillVisibleSlots(stack, viewport.cells());
                if (!stack.isEmpty() && !player.level().isClientSide) Backpack.of(inventory).insert(stack, stack.getCount());
                if (original != stack.getCount()) {
                    if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                    else slot.setChanged();
                    slot.onTake(player, stack);
                }
            } else if (!player.level().isClientSide) {
                Backpack.of(inventory).storeHotbarStack(slot);
            }
            ci.cancel();
        } else if (BackpackViewport.isBound(slot)) {
            ItemStack stack = slot.getItem();
            if (moveItemStackTo(stack, InventoryMenu.USE_ROW_SLOT_START, InventoryMenu.USE_ROW_SLOT_END, false)) {
                if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                else slot.setChanged();
                slot.onTake(player, stack);
            }
            ci.cancel();
        }
    }

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void bigInventory$protectStorageSwap(int slotIndex, int button, ClickType click, Player player, CallbackInfo ci) {
        if (slotIndex >= 0 && slotIndex < slots.size() && BackpackViewport.isInactive(slots.get(slotIndex))) {
            ci.cancel();
            return;
        }
        if (click != ClickType.SWAP) return;
        if (!Inventory.isHotbarSlot(button) && button != 40) {
            ci.cancel();
            return;
        }
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        Slot slot = slots.get(slotIndex);
        Inventory inventory = player.getInventory();
        ItemStack incoming = inventory.getItem(button);
        ItemStack stored = slot.getItem();
        if (slot.container == inventory && BackpackViewport.acceptsLargeStacks(slot)
                && stored.getCount() > Backpack.stackLimit(stored)) {
            if (incoming.isEmpty() || Backpack.matches(incoming, stored)) {
                int room = Backpack.stackLimit(stored) - incoming.getCount();
                if (room > 0) {
                    ItemStack part = slot.safeTake(room, room, player);
                    if (incoming.isEmpty()) inventory.setItem(button, part);
                    else incoming.grow(part.getCount());
                    inventory.setChanged();
                }
            }
            ci.cancel();
            return;
        }
        // A partial swap of an occupied backpack slot would displace the original stack.
        if (slot.container == player.getInventory() && Backpack.isStorageSlot(slot.getSlotIndex())
                && (!player.level().isClientSide || BackpackViewport.isBound(slot)) && slot.hasItem() && !incoming.isEmpty()
                && incoming.getCount() > Backpack.of(player.getInventory()).replacementLimit(slot.getSlotIndex(), incoming)) {
            ci.cancel();
        }
    }

    @Inject(method = "canItemQuickReplace", at = @At("HEAD"), cancellable = true)
    private static void bigInventory$canDistribute(Slot slot, ItemStack stack, boolean ignoreCount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (slot == null || !slot.hasItem() || !(slot.container instanceof Inventory)
                || !Backpack.isStorageSlot(slot.getSlotIndex()) || slot.getMaxStackSize() != Backpack.MAX_STACK) return;
        ItemStack current = slot.getItem();
        cir.setReturnValue(Backpack.matches(current, stack)
                && (long) current.getCount() + (ignoreCount ? 0 : stack.getCount()) <= slot.getMaxStackSize(stack));
    }

    @Redirect(method = "doClick", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 0))
    private int bigInventory$distributeLimit(int itemLimit, int slotLimit) {
        return slotLimit;
    }

    @Redirect(method = "doClick", slice = @Slice(from = @At(value = "FIELD",
            target = "Lnet/minecraft/world/inventory/ClickType;PICKUP_ALL:Lnet/minecraft/world/inventory/ClickType;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int bigInventory$collectLimit(ItemStack stack, int index, int button, ClickType type, Player player) {
        return BackpackViewport.isBound(slots.get(index)) ? Backpack.storageStackLimit(stack) : stack.getMaxStackSize();
    }

    @Redirect(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity bigInventory$dropStacks(Player player, ItemStack stack, boolean random) {
        return Backpack.toss(player, stack, random);
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void bigInventory$quickMove(ItemStack input, int from, int to, boolean reverse,
                                        CallbackInfoReturnable<Boolean> cir) {
        Inventory exporting = bigInventory$exportingInventory;
        Inventory destination = null;
        boolean filterPlayerSlots = false;
        for (int i = from; i < to; i++) {
            Slot slot = slots.get(i);
            if (exporting != null) {
                if (MenuPlayerSlots.index(slot, exporting) >= 0) {
                    filterPlayerSlots = true;
                    break;
                }
            } else if (slot.container instanceof Inventory inv
                    && (!inv.player.level().isClientSide
                        || ((SlotBinding) slot).bigInventory$binding() != SlotBinding.UNBOUND)
                    && Backpack.isStorageSlot(slot.getSlotIndex())) {
                destination = inv;
                break;
            }
        }
        if (destination == null && !filterPlayerSlots && input.getCount() <= Backpack.stackLimit(input)) return;

        int original = input.getCount();
        int start = reverse ? to - 1 : from;
        int end = reverse ? from - 1 : to;
        int step = reverse ? -1 : 1;
        for (int i = start; i != end && !input.isEmpty() && input.isStackable(); i += step) {
            Slot slot = slots.get(i);
            if (filterPlayerSlots && MenuPlayerSlots.index(slot, exporting) >= 0) continue;
            if (slot.getItem() != input && Backpack.matches(slot.getItem(), input)) bigInventory$moveToSlot(input, slot);
        }
        for (int i = start; i != end && !input.isEmpty(); i += step) {
            Slot slot = slots.get(i);
            if (filterPlayerSlots && MenuPlayerSlots.index(slot, exporting) >= 0) continue;
            if (slot.getItem() != input && slot.getItem().isEmpty()) bigInventory$moveToSlot(input, slot);
        }
        if (destination != null && !destination.player.level().isClientSide && !input.isEmpty()) {
            boolean sourceInBackpack = false;
            for (int i = Backpack.FIRST; i < destination.getContainerSize(); i++) {
                if (Backpack.isStorageSlot(i) && destination.getItem(i) == input) {
                    sourceInBackpack = true;
                    break;
                }
            }
            if (!sourceInBackpack) Backpack.of(destination).insert(input, input.getCount());
        }
        cir.setReturnValue(original != input.getCount());
    }

    @Unique
    private static void bigInventory$fillVisibleSlots(ItemStack input, List<Slot> cells) {
        for (Slot slot : cells) {
            if (input.isEmpty()) return;
            if (Backpack.matches(slot.getItem(), input)) bigInventory$moveToSlot(input, slot);
        }
        for (Slot slot : cells) {
            if (input.isEmpty()) return;
            if (slot.getItem().isEmpty()) bigInventory$moveToSlot(input, slot);
        }
    }

    @Unique
    private static void bigInventory$moveToSlot(ItemStack input, Slot slot) {
        if (!slot.mayPlace(input)) return;
        ItemStack current = slot.getItem();
        int count = Math.min(input.getCount(), slot.getMaxStackSize(input) - current.getCount());
        if (count <= 0) return;
        if (current.isEmpty()) slot.setByPlayer(input.split(count));
        else {
            input.shrink(count);
            current.grow(count);
            slot.setChanged();
        }
    }
}
