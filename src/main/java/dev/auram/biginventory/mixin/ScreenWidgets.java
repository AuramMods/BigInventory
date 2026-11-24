package dev.auram.biginventory.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenWidgets {
    @Accessor("children")
    List<GuiEventListener> bigInventory$children();

    @Accessor("renderables")
    List<Renderable> bigInventory$renderables();

    @Accessor("narratables")
    List<NarratableEntry> bigInventory$narratables();
}
