package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ClientStorage;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerScreen.class)
abstract class StorageScreenPredictionMixin {
    @Shadow @Final protected AbstractContainerMenu menu;

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
}
