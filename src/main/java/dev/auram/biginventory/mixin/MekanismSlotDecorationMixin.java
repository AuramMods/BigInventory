package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ContainerBackpack;
import dev.auram.biginventory.client.PlayerSlotDecoration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.client.gui.element.slot.GuiSlot", remap = false)
abstract class MekanismSlotDecorationMixin implements PlayerSlotDecoration {
    @Unique private AbstractContainerScreen<?> bigInventory$playerScreen;
    @Unique private boolean bigInventory$hotbar;

    @Override
    public void bigInventory$setPlayerSlot(AbstractContainerScreen<?> screen, boolean hotbar) {
        bigInventory$playerScreen = screen;
        bigInventory$hotbar = hotbar;
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, require = 0)
    private void bigInventory$suppressReplacedFrame(GuiGraphics graphics, CallbackInfo ci) {
        if (bigInventory$playerScreen != null
                && ContainerBackpack.replacesPlayerSlot(bigInventory$playerScreen, bigInventory$hotbar)) ci.cancel();
    }
}
