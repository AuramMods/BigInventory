package dev.auram.biginventory.client;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

final class SmoothSwappingCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    static Timing timing() {
        if (!ModList.get().isLoaded("smoothswapping") || Api.INSTANCE == null) return null;
        try {
            Api api = Api.INSTANCE;
            Object config = api.getConfig().invoke(null);
            if (!(boolean) api.enabled().invoke(config)) return null;
            float speed = (float) api.speed().invoke(config);
            if (!(speed > 0)) return null;
            Object splines = api.splines().invoke(config);
            if (!(splines instanceof List<?> list) || list.isEmpty()) return null;
            // Smooth Swapping advances one tenth of the distance per tick at 100% speed.
            return new Timing(500.0 / speed, progress -> api.evaluate(progress, splines));
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.debug("Could not read Smooth Swapping animation settings", failure);
            return null;
        }
    }

    record Timing(double durationMillis, DoubleUnaryOperator curve) {}

    // Resolve the optional mod only when installed, so a Forge-only build needs no copy of its jar.
    private record Api(Method getConfig, Method enabled, Method speed, Method splines, Method progress) {
        @Nullable private static final Api INSTANCE = load();

        @Nullable
        private static Api load() {
            try {
                String packageName = "dev.shwg.smoothswapping.config.";
                Class<?> manager = Class.forName(packageName + "ConfigManager");
                Method getConfig = manager.getMethod("getConfig");
                Class<?> config = getConfig.getReturnType();
                Class<?> widget = Class.forName(packageName + "CatmullRomWidget");
                return new Api(getConfig, config.getMethod("getToggleMod"),
                        config.getMethod("getAnimationSpeedFormatted"), config.getMethod("getSplines"),
                        widget.getMethod("getProgress", double.class, List.class));
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.debug("This version of Smooth Swapping does not expose its animation settings", failure);
                return null;
            }
        }

        private double evaluate(double value, Object splines) {
            try {
                double eased = (double) progress.invoke(null, value, splines);
                return Double.isFinite(eased) ? eased : value;
            } catch (ReflectiveOperationException failure) {
                return value;
            }
        }
    }

    private SmoothSwappingCompat() {}
}
