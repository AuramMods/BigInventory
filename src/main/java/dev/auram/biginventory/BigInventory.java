package dev.auram.biginventory;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BigInventory.MOD_ID)
public final class BigInventory {
    public static final String MOD_ID = "biginventory";

    public BigInventory(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, StorageConfig.SPEC);
        var modBus = context.getModEventBus();
        StorageItems.ITEMS.register(modBus);
        AdditionalLootModifier.SERIALIZERS.register(modBus);
        modBus.addListener(StorageItems::addCreativeItems);
        modBus.addListener(this::commonSetup);
        StorageNetwork.register();
        MinecraftForge.EVENT_BUS.register(new StorageEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("curios")) event.enqueueWork(() -> CuriosCapacity.register());
    }
}
