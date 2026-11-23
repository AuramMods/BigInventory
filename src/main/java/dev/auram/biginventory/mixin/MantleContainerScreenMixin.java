package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ContainerBackpack;
import dev.auram.biginventory.client.ContainerPanelBounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "slimeknights.mantle.client.screen.MultiModuleScreen", remap = false)
abstract class MantleContainerScreenMixin implements ContainerPanelBounds {
    @Shadow public int cornerX;
    @Shadow public int cornerY;
    @Shadow public int realWidth;
    @Shadow public int realHeight;

    @Override
    public Rect2i bigInventory$mainPanel() {
        return new Rect2i(cornerX, cornerY, realWidth, realHeight);
    }

    @Inject(method = "drawPlayerInventoryName", at = @At("HEAD"), cancellable = true, require = 0)
    private void bigInventory$hideInventoryLabel(CallbackInfo ci) {
        if (ContainerBackpack.replacesPlayerInventory((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
