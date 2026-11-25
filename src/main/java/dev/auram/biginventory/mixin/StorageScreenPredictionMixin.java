package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ClientStorage;
import dev.auram.biginventory.client.ViewportScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
abstract class StorageScreenPredictionMixin implements ViewportScreen {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow @Final protected Set<Slot> quickCraftSlots;
    @Shadow protected Slot hoveredSlot;
    @Shadow protected boolean isQuickCrafting;
    @Shadow private Slot lastClickSlot;
    @Shadow private long lastClickTime;
    @Shadow private boolean doubleclick;
    @Shadow private boolean skipNextRelease;

    @Override
    public void bigInventory$resetPageGesture() {
        hoveredSlot = null;
        lastClickSlot = null;
        lastClickTime = 0;
        doubleclick = false;
        quickCraftSlots.clear();
        isQuickCrafting = false;
        skipNextRelease = true;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$renderCarried(AbstractContainerMenu menu) {
        return ClientStorage.renderedCarried(menu, true);
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getCarried()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$tooltipCarried(AbstractContainerMenu menu) {
        return ClientStorage.renderedCarried(menu, false);
    }

    @Redirect(method = "renderSlot", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$renderSlot(Slot slot) {
        return ClientStorage.renderedSlot(menu, slot, true);
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bigInventory$tooltipSlot(Slot slot) {
        return ClientStorage.renderedSlot(menu, slot, false);
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;hasItem()Z"))
    private boolean bigInventory$slotHasDisplayedItem(Slot slot) {
        return !ClientStorage.renderedSlot(menu, slot, false).isEmpty();
    }

    @Redirect(method = {"renderSlot", "recalculateQuickCraftRemaining"}, at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;min(II)I", ordinal = 0))
    private int bigInventory$distributeLimit(int itemLimit, int slotLimit) {
        return slotLimit;
    }
}
