package dev.auram.biginventory.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

record StorageSearchQuery(List<String> itemTerms, List<String> modTerms) {
    private static final Pattern TERM = Pattern.compile("(@?)\"([^\"]*)(?:\"|$)|\\S+");

    static StorageSearchQuery parse(String text) {
        List<String> items = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        var matcher = TERM.matcher(lower(text));
        while (matcher.find()) {
            String quoted = matcher.group(2);
            String term = quoted == null ? matcher.group() : quoted;
            boolean mod = quoted == null ? term.startsWith("@") : !matcher.group(1).isEmpty();
            if (mod && quoted == null) term = term.substring(1);
            if (!term.isBlank()) (mod ? mods : items).add(term);
        }
        return new StorageSearchQuery(List.copyOf(items), List.copyOf(mods));
    }

    boolean isEmpty() {
        return itemTerms.isEmpty() && modTerms.isEmpty();
    }

    boolean matches(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (!modTerms.isEmpty()) {
            String namespace = id == null ? "" : id.getNamespace();
            String creator = stack.getItem().getCreatorModId(stack);
            if (creator == null) creator = namespace;
            String registeredMod = modName(namespace);
            String creatorMod = creator.equals(namespace) ? registeredMod : modName(creator);
            if (!matchesMods(namespace, registeredMod, creator, creatorMod)) return false;
        }
        return itemTerms.isEmpty() || matchesItem(stack.getHoverName().getString(), id == null ? "" : id.toString());
    }

    boolean matchesItem(String itemName, String registryId) {
        String name = lower(itemName);
        return itemTerms.stream().allMatch(term -> name.contains(term) || registryId.contains(term));
    }

    boolean matchesMods(String namespace, String modName, String creator, String creatorName) {
        String registeredName = lower(modName);
        String originName = lower(creatorName);
        return modTerms.stream().allMatch(term -> namespace.contains(term) || registeredName.contains(term)
                || creator.contains(term) || originName.contains(term));
    }

    private static String modName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
