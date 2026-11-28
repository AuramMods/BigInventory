package dev.auram.biginventory.mixin;

import dev.auram.biginventory.ChestControls;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.violetmoon.quark.base.handler.SortingHandler", remap = false)
abstract class QuarkSortMixin {
    @Shadow public static void sortInventory(IItemHandler handler, int from, int to, int[] locked) {
        throw new AssertionError();
    }

    @Redirect(method = "sortInventory(Lnet/minecraft/world/entity/player/Player;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/violetmoon/quark/base/handler/SortingHandler;sortInventory(Lnet/minecraftforge/items/IItemHandler;II[I)V"))
    private static void bigInventory$sortPlayerSide(IItemHandler handler, int from, int to, int[] locked,
                                                     Player player, boolean forcePlayer) {
        if (!forcePlayer || locked != null && locked.length != 0 || !ChestControls.sortBackpack(player)) {
            sortInventory(handler, from, to, locked);
        }
    }
}
