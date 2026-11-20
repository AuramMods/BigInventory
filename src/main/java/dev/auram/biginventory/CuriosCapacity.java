package dev.auram.biginventory;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loaded only when Curios is installed. */
final class CuriosCapacity {
    private static List<? extends String> previousRules = List.of();
    private static Map<ResourceLocation, Map<String, Integer>> adjustments = Map.of();

    static void register() {
        CuriosApi.registerCurio(StorageItems.ENDANT.get(), new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext slot, ItemStack stack) {
                if (slot.entity() == null || slot.cosmetic()) return true;
                return CuriosApi.getCuriosInventory(slot.entity()).map(handler -> handler.findCurios(StorageItems.ENDANT.get())
                        .stream().noneMatch(equipped -> !equipped.slotContext().identifier().equals(slot.identifier())
                                || equipped.slotContext().index() != slot.index())).orElse(true);
            }
        });
    }

    static StorageEquipment equipped(Player player, List<? extends String> config) {
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
            long capacity = 0;
            int bags = 0;
            int rings = 0;
            boolean endant = false;
            for (var slot : handler.getCurios().entrySet()) {
                var equipped = slot.getValue().getStacks();
                for (int i = 0; i < equipped.getSlots(); i++) {
                    ItemStack stack = equipped.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof StorageItems.Pouch) {
                        capacity += StorageItems.POUCH_CAPACITY;
                    } else if (stack.is(StorageItems.BAG.get())) {
                        capacity += StorageItems.BAG_CAPACITY;
                        bags++;
                    } else if (stack.is(StorageItems.ECHO_RING.get())) {
                        rings++;
                    } else if (stack.is(StorageItems.ENDANT.get())) {
                        endant = true;
                    }
                    if (!adjustments.isEmpty()) {
                        Map<String, Integer> rule = adjustments.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                        if (rule != null) capacity += rule.getOrDefault(slot.getKey(), rule.getOrDefault("", 0));
                    }
                }
            }
            return new StorageEquipment(capacity, bags, rings, endant);
        }).orElse(StorageEquipment.NONE);
    }

    private CuriosCapacity() {}
}
