package dev.auram.biginventory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

final class AdditionalLootModifier extends LootModifier {
    static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BigInventory.MOD_ID);

    private static final Codec<AdditionalLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ResourceLocation.CODEC.fieldOf("loot_table").forGetter(modifier -> modifier.lootTable))
            .and(Codec.STRING.optionalFieldOf("path_segment", "").forGetter(modifier -> modifier.pathSegment))
            .apply(instance, AdditionalLootModifier::new));

    static {
        SERIALIZERS.register("additional_loot", () -> CODEC);
    }

    private final ResourceLocation lootTable;
    private final String pathSegment;

    private AdditionalLootModifier(LootItemCondition[] conditions, ResourceLocation lootTable, String pathSegment) {
        super(conditions);
        this.lootTable = lootTable;
        this.pathSegment = pathSegment;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (pathSegment.isEmpty() || ("/" + context.getQueriedLootTableId().getPath()).contains("/" + pathSegment + "/")) {
            // The supplement uses the original context and does not re-enter global loot modifiers.
            context.getResolver().getLootTable(lootTable).getRandomItemsRaw(context, generatedLoot::add);
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
