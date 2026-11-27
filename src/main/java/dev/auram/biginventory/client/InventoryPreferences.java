package dev.auram.biginventory.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class InventoryPreferences {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue AUTO_SORT;

    static {
        var builder = new ForgeConfigSpec.Builder();
        AUTO_SORT = builder.comment("Combine and sort backpack stacks when opening or closing storage.")
                .define("autoSort", false);
        SPEC = builder.build();
    }

    static boolean autoSort() {
        return AUTO_SORT.get();
    }

    static boolean toggleAutoSort() {
        boolean enabled = !autoSort();
        AUTO_SORT.set(enabled);
        AUTO_SORT.save();
        return enabled;
    }

    private InventoryPreferences() {}
}
