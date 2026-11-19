package dev.auram.biginventory;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BigInventory.MOD_ID)
public final class BigInventory {
    public static final String MOD_ID = "biginventory";

    public BigInventory(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, StorageConfig.SPEC);
        StorageNetwork.register();
        MinecraftForge.EVENT_BUS.register(new StorageEvents());
    }
}
