package dev.auram.biginventory.mixin;

import dev.auram.biginventory.BigInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.text.Word", remap = false)
abstract class PatchouliWordMixin {
    @Unique
    private static final ResourceLocation BIG_INVENTORY_BOOK =
            ResourceLocation.fromNamespaceAndPath(BigInventory.MOD_ID, "big_inventory");

    @Shadow @Final
    private Supplier<Boolean> onClick;

    @ModifyArg(method = "render", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),
            index = 1, require = 0)
    private Component bigInventory$lightenBookText(Component word) {
        if (!BIG_INVENTORY_BOOK.equals(PatchouliAPI.get().getOpenBookGui())) return word;

        // Patchouli embeds colors in styles, so the ordinary draw color adjustment misses them.
        int darkInk = 0xff292929;
        if (FontColor.bigInventory$adjustColor(darkInk) == darkInk) return word;

        var style = word.getStyle().withColor(0xffffff);
        if (onClick != null) style = style.withUnderlined(true);
        return word.copy().setStyle(style);
    }
}
