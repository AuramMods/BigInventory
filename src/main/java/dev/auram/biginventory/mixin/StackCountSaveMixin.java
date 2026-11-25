package dev.auram.biginventory.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemStack.class)
abstract class StackCountSaveMixin {
    @Shadow private int count;

    @Redirect(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/item/ItemStack;count:I", opcode = Opcodes.PUTFIELD))
    private void bigInventory$loadCount(ItemStack stack, int legacy, CompoundTag tag) {
        count = tag.getInt("Count");
    }

    @Redirect(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;putByte(Ljava/lang/String;B)V"))
    private void bigInventory$saveCount(CompoundTag tag, String key, byte legacy) {
        if (count > Byte.MAX_VALUE) tag.putInt(key, count);
        else tag.putByte(key, legacy);
    }
}
