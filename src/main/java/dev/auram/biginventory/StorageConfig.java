package dev.auram.biginventory;

import net.minecraftforge.common.ForgeConfigSpec;

final class StorageConfig {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue BASE_CAPACITY;
    static final ForgeConfigSpec.IntValue FOOTPRINT_FACTOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("storage");
        BASE_CAPACITY = builder
                .comment("Weighted capacity of the player's storage, excluding the hotbar.")
                .defineInRange("baseCapacity", 500, 0, Integer.MAX_VALUE);
        FOOTPRINT_FACTOR = builder
                .comment("Each stored item's footprint is ceil(footprintFactor / max stack size).")
                .defineInRange("footprintFactor", 32, 1, 8192);
        builder.pop();
        SPEC = builder.build();
    }

    private StorageConfig() {}
}
