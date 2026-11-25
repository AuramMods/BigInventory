package dev.auram.biginventory.mixin;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FriendlyByteBuf.class)
abstract class StackCountNetworkMixin {
    // Ordinary counts retain their vanilla encoding. Negative counts cannot represent a live stack.
    @Redirect(method = "writeItemStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;"), remap = false)
    private ByteBuf bigInventory$writeCount(FriendlyByteBuf buffer, int count) {
        if (count <= Byte.MAX_VALUE) return buffer.writeByte(count);
        buffer.writeByte(-1);
        return buffer.writeVarInt(count);
    }

    @ModifyVariable(method = "readItem", at = @At("STORE"), ordinal = 0)
    private int bigInventory$readCount(int count) {
        if (count != -1) return count;
        int extended = ((FriendlyByteBuf) (Object) this).readVarInt();
        if (extended <= Byte.MAX_VALUE) throw new DecoderException("Invalid extended stack count");
        return extended;
    }
}
