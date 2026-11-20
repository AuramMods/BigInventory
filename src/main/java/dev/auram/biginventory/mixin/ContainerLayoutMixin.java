package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ContainerBackpack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
abstract class ContainerLayoutMixin {
    @Shadow
    protected abstract boolean hasClickedOutside(double x, double y, int left, int top, int button);

    @Inject(method = "getYSize", at = @At("RETURN"), cancellable = true, remap = false)
    private void bigInventory$reportPlayerSectionHeight(CallbackInfoReturnable<Integer> result) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        int height = result.getReturnValueI();
        int extended = ContainerBackpack.extendedHeight(screen, height);
        if (extended != height) result.setReturnValue(extended);
    }

    @Redirect(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDIII)Z"))
    private boolean bigInventory$includePlayerSection(AbstractContainerScreen<?> screen, double x, double y,
                                                      int left, int top, int button) {
        return !ContainerBackpack.insideExtension(screen, x, y) && hasClickedOutside(x, y, left, top, button);
    }
}
