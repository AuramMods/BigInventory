package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ContainerBackpack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "inzhefop.extrautilitiesrebirth.client.gui.NormalGUIScreen", remap = false)
abstract class ExtraUtilitiesGeneratorScreenMixin {
    // The optional target is unavailable to the annotation processor. Match both names of its vanilla override.
    @ModifyArg(method = {"renderLabels", "m_280003_"}, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0)
    private String bigInventory$hideInventoryLabel(String label) {
        return "Inventory".equals(label) && ContainerBackpack.replacesPlayerInventory((AbstractContainerScreen<?>) (Object) this)
                ? "" : label;
    }
}
