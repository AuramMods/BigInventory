package dev.auram.biginventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** This class is reached only on the server when Curios is installed. */
final class CuriosCapacity {
    private static List<? extends String> previousRules = List.of();
    private static Map<ResourceLocation, Map<String, Integer>> adjustments = Map.of();

    static long adjustment(Player player, List<? extends String> config) {
        if (!previousRules.equals(config)) {
            Map<ResourceLocation, Map<String, Integer>> next = new HashMap<>();
            for (String value : config) {
                StorageConfig.CurioRule rule = StorageConfig.parse(value);
                if (rule != null) next.computeIfAbsent(rule.item(), key -> new HashMap<>()).put(rule.slot(), rule.amount());
            }
            adjustments = next;
            previousRules = List.copyOf(config);
        }
        return CuriosApi.getCuriosInventory(player).map(handler -> {
            long delta = 0;
            for (var slot : handler.getCurios().entrySet()) {
                var equipped = slot.getValue().getStacks();
                for (int i = 0; i < equipped.getSlots(); i++) {
                    ItemStack stack = equipped.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    Map<String, Integer> rule = adjustments.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                    if (rule != null) delta += rule.getOrDefault(slot.getKey(), rule.getOrDefault("", 0));
                }
            }
            return delta;
        }).orElse(0L);
    }

    private CuriosCapacity() {}
}
