package dev.auram.biginventory;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class StorageConfig {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.IntValue BASE_CAPACITY;
    static final ForgeConfigSpec.IntValue FOOTPRINT_FACTOR;
    static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURIO_CAPACITY;
    static final ForgeConfigSpec.BooleanValue REFILL_HOTBAR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("storage");
        BASE_CAPACITY = builder
                .comment("Weighted capacity of the player's storage, excluding the hotbar.")
                .defineInRange("baseCapacity", 500, 0, Integer.MAX_VALUE);
        FOOTPRINT_FACTOR = builder
                .comment("Each stored item's footprint is ceil(footprintFactor / max stack size).")
                .defineInRange("footprintFactor", 32, 1, 8192);
        REFILL_HOTBAR = builder
                .comment("Refill an empty hotbar slot from storage after its stack is used up or its tool breaks.")
                .define("refillHotbar", true);
        CURIO_CAPACITY = builder
                .comment("Signed capacity changes for equipped Curios. Format: mod:item=200 or mod:item@ring=-50.",
                        "A slot-specific rule replaces the item rule for that slot. The last matching rule wins.")
                .defineListAllowEmpty(List.of("curioCapacity"), List::of, value -> value instanceof String rule && parse(rule) != null);
        builder.pop();
        SPEC = builder.build();
    }

    static CurioRule parse(String text) {
        String[] pair = text.split("=", -1);
        if (pair.length != 2) return null;
        String[] key = pair[0].trim().split("@", -1);
        ResourceLocation item = ResourceLocation.tryParse(key[0]);
        if (item == null || key[0].isBlank() || key.length > 2 || (key.length == 2 && key[1].isBlank())) return null;
        try {
            return new CurioRule(item, key.length == 2 ? key[1] : "", Integer.parseInt(pair[1].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    record CurioRule(ResourceLocation item, String slot, int amount) {}

    private StorageConfig() {}
}
