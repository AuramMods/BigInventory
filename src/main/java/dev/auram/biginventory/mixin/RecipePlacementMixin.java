package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlaceRecipe.class)
abstract class RecipePlacementMixin {
    @Shadow @Final protected RecipeBookMenu<?> menu;
    @Shadow protected Inventory inventory;

    @Inject(method = "testClearGrid", at = @At("HEAD"), cancellable = true)
    private void bigInventory$canReturnIngredients(CallbackInfoReturnable<Boolean> cir) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < menu.getGridWidth() * menu.getGridHeight() + 1; i++) {
            if (i != menu.getResultSlotIndex()) ingredients.add(menu.getSlot(i).getItem());
        }
        cir.setReturnValue(Backpack.of(inventory).canReturn(ingredients));
    }
}
