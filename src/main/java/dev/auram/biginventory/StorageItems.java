package dev.auram.biginventory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

final class StorageItems {
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BigInventory.MOD_ID);
    static final int POUCH_CAPACITY = 98;
    static final int BAG_CAPACITY = 352;
    static final int BAG_EXHAUSTION_PERCENT = 15;
    static final int RING_CAPACITY_PERCENT = 50;

    static {
        for (DyeColor color : DyeColor.values()) {
            ITEMS.register(color.getName() + "_pouch", Pouch::new);
        }
    }

    static final RegistryObject<Item> BAG = ITEMS.register("bag", () -> new Accessory(
            bonus("capacity", BAG_CAPACITY), bonus("movement_exhaustion", BAG_EXHAUSTION_PERCENT)));
    static final RegistryObject<Item> ECHO_RING = ITEMS.register("echo_ring", () -> new Accessory(
            bonus("capacity_percent", RING_CAPACITY_PERCENT)));
    static final RegistryObject<Item> ENDANT = ITEMS.register("endant", () -> new Accessory(
            bonus("endant")));

    static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }

    private static Component bonus(String key, Object... args) {
        return Component.translatable("biginventory.tooltip." + key, args).withStyle(ChatFormatting.BLUE);
    }

    private static class Accessory extends Item {
        private final List<Component> effects;

        Accessory(Component... effects) {
            super(new Properties().stacksTo(1));
            this.effects = List.of(effects);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.addAll(effects);
        }
    }

    static final class Pouch extends Accessory {
        Pouch() { super(bonus("capacity", POUCH_CAPACITY)); }
    }

    private StorageItems() {}
}
