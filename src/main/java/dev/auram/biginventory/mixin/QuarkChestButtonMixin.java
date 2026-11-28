package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ChestSideButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "org.violetmoon.quark.content.management.client.screen.widgets.MiniInventoryButton", remap = false)
abstract class QuarkChestButtonMixin implements ChestSideButton {
    @Shadow @Final private AbstractContainerScreen<?> parent;
    @Unique private boolean bigInventory$playerSide;

    @Inject(method = "setType", at = @At("RETURN"))
    private void bigInventory$identifyPlayerSide(@Coerce Object type, CallbackInfoReturnable<Object> result) {
        bigInventory$playerSide = type instanceof Enum<?> target && target.name().equals("CONTAINER_PLAYER_INVENTORY");
    }

    @Override
    public boolean bigInventory$isPlayerSide() {
        return bigInventory$playerSide;
    }

    @Inject(method = {"render", "m_88315_"}, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    })
    private void bigInventory$moveBesideBackpack(GuiGraphics graphics, int x, int y, float delta, CallbackInfo ci) {
        if (bigInventory$playerSide) ChestSideButton.place(parent, (AbstractWidget) (Object) this);
    }
}
