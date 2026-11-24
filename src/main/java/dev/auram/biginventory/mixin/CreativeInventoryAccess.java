package dev.auram.biginventory.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeInventoryAccess {
    @Nullable
    @Accessor("destroyItemSlot")
    Slot bigInventory$trashSlot();
}
