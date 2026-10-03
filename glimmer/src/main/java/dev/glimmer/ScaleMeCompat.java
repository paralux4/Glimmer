package dev.glimmer;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/**
 * Reads ScaleMe's live settings by reflection (no hard dependency), so Glimmer's
 * swing trail follows ScaleMe's swing speed, swing arc, "disable swing" and scales.
 * If ScaleMe isn't installed, everything here returns neutral values.
 */
public final class ScaleMeCompat {
    private ScaleMeCompat() {}

    private static Class<?> cls;
    private static boolean failed = false;

    public static boolean loaded() {
        return FabricLoader.getInstance().isModLoaded("scaleme");
    }

    private static Object field(String name) {
        if (failed) return null;
        try {
            if (cls == null) cls = Class.forName("com.github.kd_gaming1.scaleme.config.ScaleMeConfig");
            Field f = cls.getField(name);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean bool(String name, boolean def) {
        Object o = field(name);
        return o instanceof Boolean b ? b : def;
    }

    private static double num(String name, double def) {
        Object o = field(name);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** True when ScaleMe has the first-person swing animation switched off. */
    public static boolean swingDisabled() {
        return bool("disableSwingAnimation", false);
    }

    /** ScaleMe's swing speed multiplier (1 = vanilla). */
    public static double swingSpeed() {
        double s = num("swingAnimationSpeed", 1.0);
        return s > 0.05 ? clamp(s, 0.1, 10) : 1.0;
    }

    /** How big the swing arc is compared to vanilla (0 = no arc at all). */
    public static double arcMultiplier() {
        if (!bool("enableSwingOverride", false)) return 1.0;
        double x = Math.abs(num("swingArcXAmount", -80));
        double y = Math.abs(num("swingArcYAmount", -20));
        double z = Math.abs(num("swingArcZAmount", -20));
        if (x < 0.5 && y < 0.5 && z < 0.5) return 0.0;
        return clamp(Math.max(x / 80.0, (y + z) / 160.0), 0.15, 2.5);
    }

    /** Held-item scale from ScaleMe's item transform override. */
    public static double itemScale() {
        if (!bool("enableItemTransformOverride", false)) return 1.0;
        return clamp(num("itemScale", 1.0), 0.4, 3.0);
    }

    /** ScaleMe's own-player scale. */
    public static double playerScale() {
        return clamp(num("playerScale", 1.0), 0.3, 4.0);
    }
}
