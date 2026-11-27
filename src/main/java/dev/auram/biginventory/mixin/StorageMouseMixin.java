package dev.auram.biginventory.mixin;

import dev.auram.biginventory.client.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class StorageMouseMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract double xpos();
    @Shadow public abstract double ypos();

    // At this point Minecraft has not converted Control + left click into right click on macOS.
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void bigInventory$bulkMove(long handle, int button, int action, int modifiers, CallbackInfo ci) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || action != GLFW.GLFW_PRESS
                || (modifiers & GLFW.GLFW_MOD_CONTROL) == 0 || minecraft.getOverlay() != null) return;
        var window = minecraft.getWindow();
        if (handle != window.getWindow()) return;
        double x = xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        if (ClientEvents.bulkMoveAt(minecraft.screen, x, y)) ci.cancel();
    }
}
