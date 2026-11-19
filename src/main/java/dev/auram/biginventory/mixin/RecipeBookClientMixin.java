package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ClientStorage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeBookComponent.class)
abstract class RecipeBookClientMixin {
    @Redirect(method = {"initVisuals", "updateStackedContents"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;fillStackedContents(Lnet/minecraft/world/entity/player/StackedContents;)V"))
    private void bigInventory$includeOverflow(Inventory inventory, StackedContents contents) {
        inventory.fillStackedContents(contents);
        ClientStorage.accountOverflow(contents);
    }
}
