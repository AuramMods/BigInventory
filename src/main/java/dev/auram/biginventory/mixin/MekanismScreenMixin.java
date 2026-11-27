package dev.auram.biginventory.mixin;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.client.ContainerBackpack;
import dev.auram.biginventory.client.ContainerWindows;
import dev.auram.biginventory.client.PlayerSlotDecoration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mekanism.client.gui.GuiMekanism", remap = false)
abstract class MekanismScreenMixin implements ContainerWindows {
    @Shadow public abstract Collection<? extends GuiEventListener> getWindows();

    @Override
    public boolean bigInventory$hasWindowAt(double x, double y) {
        for (GuiEventListener window : getWindows()) {
            if (window.isMouseOver(x, y)) return true;
        }
        return false;
    }

    @Inject(method = {"renderLabels", "m_280003_"}, at = @At(value = "INVOKE", target =
            "Lmekanism/client/gui/GuiMekanism;getWindowsDescendingIterator()Lmekanism/common/lib/collection/LRU$LRUIterator;"))
    private void bigInventory$drawSearchBehindWindows(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        ContainerBackpack.drawSearch((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }

    @Inject(method = "addWindow", at = @At("RETURN"))
    private void bigInventory$unfocusSearch(CallbackInfo ci) {
        ContainerBackpack.clearWidgetFocus((AbstractContainerScreen<?>) (Object) this);
    }

    // The second addRenderableWidget call creates the plain frame for a menu slot.
    @Inject(method = "addSlots", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
            target = "Lmekanism/client/gui/GuiMekanism;addRenderableWidget(Lmekanism/client/gui/element/GuiElement;)Lmekanism/client/gui/element/GuiElement;"),
            locals = LocalCapture.CAPTURE_FAILSOFT, require = 0)
    private void bigInventory$identifyPlayerFrame(CallbackInfo ci, int size, int i, Slot slot) {
        if (!(slot.container instanceof Inventory)) return;
        int index = slot.getSlotIndex();
        boolean hotbar = Inventory.isHotbarSlot(index);
        if (!hotbar && (index < Backpack.FIRST || index >= Backpack.LEGACY_END)) return;

        var screen = (AbstractContainerScreen<?>) (Object) this;
        var children = screen.children();
        if (!children.isEmpty() && children.get(children.size() - 1) instanceof PlayerSlotDecoration decoration) {
            decoration.bigInventory$setPlayerSlot(screen, hotbar);
        }
    }
}
