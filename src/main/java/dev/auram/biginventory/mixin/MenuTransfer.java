package dev.auram.biginventory.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface MenuTransfer {
    @Invoker("moveItemStackTo")
    boolean bigInventory$moveTo(ItemStack stack, int from, int to, boolean reverse);
}
