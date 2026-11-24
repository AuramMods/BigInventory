package dev.auram.biginventory.client;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class SoundsCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    static void playItemClick(ItemStack stack) {
        if (!ModList.get().isLoaded("sounds") || Api.INSTANCE == null) return;
        try {
            Api api = Api.INSTANCE;
            Object config = api.getConfig().invoke(null, api.uiConfig());
            api.play().invoke(api.itemClick().get(config), stack, api.placeContext());
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.debug("Could not play the Sounds inventory click", failure);
        }
    }

    private record Api(Class<?> uiConfig, Method getConfig, Field itemClick, Method play, Object placeContext) {
        @Nullable private static final Api INSTANCE = load();

        @Nullable
        private static Api load() {
            try {
                String prefix = "dev.imb11.sounds.";
                Class<?> uiConfig = Class.forName(prefix + "config.UISoundsConfig");
                Class<?> soundsConfig = Class.forName(prefix + "config.SoundsConfig");
                Class<?> contextType = Class.forName(prefix + "sound.context.ItemStackSoundContext");
                Class<?> blockSoundType = Class.forName(prefix + "dynamic.DynamicSoundHelper$BlockSoundType");
                Field itemClick = uiConfig.getField("itemClickSoundEffect");
                Method play = itemClick.getType().getMethod("playDynamicSound", ItemStack.class, contextType);
                Object place = blockSoundType.getField("PLACE").get(null);
                Object placeContext = contextType.getMethod("of", blockSoundType).invoke(null, place);
                return new Api(uiConfig, soundsConfig.getMethod("get", Class.class), itemClick, play, placeContext);
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.debug("This version of Sounds does not expose its inventory click effect", failure);
                return null;
            }
        }
    }

    private SoundsCompat() {}
}
