package dev.auram.biginventory.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerLayoutAccess {
    @Accessor("imageHeight")
    int bigInventory$imageHeight();

    @Accessor("playerInventoryTitle")
    Component bigInventory$inventoryTitle();

    @Mutable @Accessor("playerInventoryTitle")
    void bigInventory$setInventoryTitle(Component title);
}
