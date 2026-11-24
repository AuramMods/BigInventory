package dev.auram.biginventory;

/** A menu slot keeps its protocol ID while the backpack cell it presents changes. */
public interface SlotBinding {
    int UNBOUND = Integer.MIN_VALUE;
    int INACTIVE = -1;

    int bigInventory$originalIndex();

    int bigInventory$binding();

    void bigInventory$bind(int index);

    void bigInventory$reserve();
}
