package dev.auram.biginventory.client;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;

final class MouseTweaksCompat {
    static boolean shiftDragEnabled() {
        if (!ModList.get().isLoaded("mousetweaks") || Settings.INSTANCE == null) return false;
        try {
            var settings = Settings.INSTANCE;
            Object config = settings.config().get(null);
            return config != null && settings.shiftDrag().getBoolean(config);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private record Settings(Field config, Field shiftDrag) {
        private static final Settings INSTANCE = load();

        private static Settings load() {
            try {
                Field config = Class.forName("yalter.mousetweaks.Main").getField("config");
                return new Settings(config, config.getType().getField("lmbTweakWithoutItem"));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }

    private MouseTweaksCompat() {}
}
