package dev.auram.biginventory.client;

import dev.auram.biginventory.Backpack;
import dev.auram.biginventory.StorageNetwork.Entry;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/** Keeps row positions stable when entries disappear while the mouse is held. */
final class StorageSweep {
    private final List<CompoundTag> positions;
    private final BitSet visited = new BitSet();

    StorageSweep(List<Entry> entries) {
        positions = entries.stream().map(entry -> Backpack.variantKey(entry.icon())).toList();
    }

    List<Entry> layout(List<Entry> entries) {
        var current = new HashMap<CompoundTag, Entry>();
        for (Entry entry : entries) current.put(Backpack.variantKey(entry.icon()), entry);
        return positions.stream().map(current::get).toList();
    }

    @Nullable
    CompoundTag visit(int index) {
        if (index < 0 || index >= positions.size() || visited.get(index)) return null;
        visited.set(index);
        return positions.get(index);
    }
}
