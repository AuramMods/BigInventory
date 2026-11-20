package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.StorageSearchBox;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EditBox.class)
abstract class EditBoxTextMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int bigInventory$drawText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        return (Object) this instanceof StorageSearchBox ? graphics.drawString(font, text, x, y, color, false)
                : graphics.drawString(font, text, x, y, color);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int bigInventory$drawCursor(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        return (Object) this instanceof StorageSearchBox ? graphics.drawString(font, text, x, y, color, false)
                : graphics.drawString(font, text, x, y, color);
    }
}
