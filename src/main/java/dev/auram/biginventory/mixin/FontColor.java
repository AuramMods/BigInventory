package dev.auram.biginventory.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Font.class)
public interface FontColor {
    @Invoker("adjustColor")
    static int bigInventory$adjustColor(int color) {
        throw new AssertionError();
    }
}
