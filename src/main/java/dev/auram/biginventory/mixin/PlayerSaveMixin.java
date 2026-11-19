package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackItemHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerSaveMixin {
    @Shadow public abstract Inventory getInventory();
    @Shadow(remap = false) @Final @Mutable private LazyOptional<IItemHandler> playerMainHandler;
    @Shadow(remap = false) @Final @Mutable private LazyOptional<IItemHandler> playerJoinedHandler;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bigInventory$handlers(Level level, BlockPos pos, float rotation, GameProfile profile, CallbackInfo ci) {
        playerMainHandler = LazyOptional.of(() -> new BackpackItemHandler(getInventory(), false));
        playerJoinedHandler = LazyOptional.of(() -> new BackpackItemHandler(getInventory(), true));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void bigInventory$save(CompoundTag tag, CallbackInfo ci) {
        // Legacy slots are already persisted in the vanilla Inventory list and are adopted in place.
        tag.put(Backpack.SAVE_KEY, Backpack.of(getInventory()).save());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void bigInventory$load(CompoundTag tag, CallbackInfo ci) {
        Backpack.of(getInventory()).load(tag.getCompound(Backpack.SAVE_KEY));
    }
}
