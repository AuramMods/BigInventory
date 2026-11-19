package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.BackpackAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Inventory.class)
abstract class InventoryMixin implements BackpackAccess {
    @Shadow @Final @Mutable private List<NonNullList<ItemStack>> compartments;
    @Shadow @Final public Player player;
    @Unique private NonNullList<ItemStack> bigInventory$overflow;
    @Unique private Backpack bigInventory$storage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bigInventory$initialize(Player owner, CallbackInfo ci) {
        bigInventory$overflow = NonNullList.create();
        bigInventory$storage = new Backpack((Inventory) (Object) this, bigInventory$overflow);
        List<NonNullList<ItemStack>> lists = new ArrayList<>(compartments);
        lists.add(bigInventory$overflow);
        compartments = List.copyOf(lists);
    }

    @Override
    public Backpack bigInventory$backpack() {
        return bigInventory$storage;
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void bigInventory$add(int slot, ItemStack input, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide || input.isEmpty()) return;
        int moved = bigInventory$storage.add(slot, input);
        // Vanilla creative pickups can consume entities even when the inventory is full.
        if (moved == 0 && slot == -1 && player.getAbilities().instabuild) {
            input.setCount(0);
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(moved > 0);
        }
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void bigInventory$returnStack(ItemStack input, boolean sendUpdates, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        bigInventory$storage.add(-1, input);
        if (!input.isEmpty()) player.drop(input, false);
        // In particular, an open chest does not synchronize the offhand.
        if (sendUpdates) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 40,
                    serverPlayer.getOffhandItem()));
            serverPlayer.containerMenu.broadcastChanges();
        }
        ci.cancel();
    }

    @Inject(method = "replaceWith", at = @At("TAIL"))
    private void bigInventory$copy(Inventory oldInventory, CallbackInfo ci) {
        bigInventory$storage.copyOverflow(Backpack.of(oldInventory));
    }

    @Inject(method = "fillStackedContents", at = @At("TAIL"))
    private void bigInventory$recipeContents(StackedContents contents, CallbackInfo ci) {
        for (ItemStack stack : bigInventory$overflow) contents.accountSimpleStack(stack);
    }

    @Inject(method = "findSlotMatchingUnusedItem", at = @At("RETURN"), cancellable = true)
    private void bigInventory$recipeIngredient(ItemStack pattern, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) return;
        for (int i = 0; i < bigInventory$overflow.size(); i++) {
            ItemStack stack = bigInventory$overflow.get(i);
            if (Backpack.matches(stack, pattern) && !stack.isDamaged() && !stack.isEnchanted() && !stack.hasCustomHoverName()) {
                cir.setReturnValue(Backpack.OVERFLOW_START + i);
                return;
            }
        }
    }

    @Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true)
    private void bigInventory$empty(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && bigInventory$overflow.stream().anyMatch(stack -> !stack.isEmpty())) {
            cir.setReturnValue(false);
        }
    }
}
