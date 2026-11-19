package dev.auram.biginventory.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotPosition {
    @Mutable @Accessor("x")
    void bigInventory$setX(int x);

    @Mutable @Accessor("y")
    void bigInventory$setY(int y);
}
