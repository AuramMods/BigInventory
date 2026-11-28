package dev.auram.biginventory.mixin;

import dev.auram.biginventory.SlotBinding;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.violetmoon.quark.base.client.handler.InventoryButtonHandler", remap = false)
abstract class QuarkInventoryButtonsMixin {
    @Redirect(method = "lambda$initGui$2", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getSlotIndex()I"))
    private static int bigInventory$findPlayerAnchor(Slot slot) {
        return ((SlotBinding) slot).bigInventory$originalIndex();
    }
}
