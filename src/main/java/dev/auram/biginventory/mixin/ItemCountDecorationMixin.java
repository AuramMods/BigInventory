package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ItemCount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiGraphics.class)
abstract class ItemCountDecorationMixin {
    @Redirect(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int bigInventory$drawCount(GuiGraphics graphics, Font font, String label, int x, int y, int color, boolean shadow) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>)) {
            return graphics.drawString(font, label, x, y, color, shadow);
        }
        String count = ItemCount.compactLabel(label);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x + font.width(label), y + font.lineHeight, 0);
            pose.scale(0.75f, 0.75f, 1);
            return graphics.drawString(font, count, -font.width(count), -font.lineHeight, color, shadow);
        } finally {
            pose.popPose();
        }
    }
}
