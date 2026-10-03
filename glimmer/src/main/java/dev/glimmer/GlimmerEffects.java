package dev.glimmer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawns vanilla particles on the local client only. Nothing here touches
 * the server, packets, hitboxes, reach, or any gameplay value.
 */
public final class GlimmerEffects {
    private GlimmerEffects() {}

    /** True only while Glimmer is spawning a particle; the mixin reads this to make it fullbright. */
    public static volatile boolean spawning = false;

    public static final Map<String, ParticleOptions> SIMPLE = new LinkedHashMap<>();
    static {
        SIMPLE.put("end_rod", ParticleTypes.END_ROD);
        SIMPLE.put("glow", ParticleTypes.GLOW);
        SIMPLE.put("electric_spark", ParticleTypes.ELECTRIC_SPARK);
        SIMPLE.put("enchant", ParticleTypes.ENCHANT);
        SIMPLE.put("enchanted_hit", ParticleTypes.ENCHANTED_HIT);
        SIMPLE.put("crit", ParticleTypes.CRIT);
        SIMPLE.put("flame", ParticleTypes.FLAME);
        SIMPLE.put("soul_fire_flame", ParticleTypes.SOUL_FIRE_FLAME);
        SIMPLE.put("happy_villager", ParticleTypes.HAPPY_VILLAGER);
        SIMPLE.put("heart", ParticleTypes.HEART);
        SIMPLE.put("note", ParticleTypes.NOTE);
        SIMPLE.put("witch", ParticleTypes.WITCH);
        SIMPLE.put("portal", ParticleTypes.PORTAL);
        SIMPLE.put("snowflake", ParticleTypes.SNOWFLAKE);
        SIMPLE.put("cloud", ParticleTypes.CLOUD);
    }

    private static int tick = 0;
    private static double lastSwing = 0;

    public static ParticleOptions resolve(String name, int color, float size) {
        if (name.equals("dust")) return new DustParticleOptions(color, size);
        ParticleOptions p = SIMPLE.get(name);
        return p != null ? p : ParticleTypes.END_ROD;
    }

    public static int currentColor(float offset) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.rainbow) return c.color & 0xFFFFFF;
        float hue = (float) (((tick * 0.01 * c.rainbowSpeed) + offset) % 1.0);
        if (hue < 0) hue += 1f;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }

    private static void add(Level level, ParticleOptions o, double x, double y, double z,
                            double dx, double dy, double dz) {
        spawning = GlimmerConfig.INSTANCE.glow;
        try {
            level.addParticle(o, x, y, z, dx, dy, dz);
        } finally {
            spawning = false;
        }
    }

    public static void tick(Minecraft mc) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || mc.player == null || mc.level == null || mc.isPaused()) return;
        tick++;
        Player p = mc.player;
        if (p.isSpectator()) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        if (c.showInFirstPerson || !firstPerson) {
            if (c.aura) aura(p, c);
            if (c.orbit) orbit(p, c);
            if (c.trail) trail(p, c);
        }
        if (c.swing && (!firstPerson || c.swingFirstPerson)) swing(p, c);
        else lastSwing = 0;
    }

    private static void aura(Player p, GlimmerConfig c) {
        var rnd = p.getRandom();
        int n = whole(c.auraDensity, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            ParticleOptions opt = resolve(c.auraParticle, currentColor(rnd.nextFloat() * 0.3f), (float) c.dustSize);
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = c.auraRadius * (0.6 + rnd.nextDouble() * 0.4);
            double y = p.getY() + rnd.nextDouble() * p.getBbHeight();
            add(p.level(), opt, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private static void orbit(Player p, GlimmerConfig c) {
        int count = Math.max(1, c.orbitCount);
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(tick * c.orbitSpeed) + (Math.PI * 2 * i / count);
            double x = p.getX() + Math.cos(a) * c.orbitRadius;
            double z = p.getZ() + Math.sin(a) * c.orbitRadius;
            double y = p.getY() + c.orbitHeight + Math.sin(tick * 0.1 + i) * 0.15;
            ParticleOptions o = resolve(c.orbitParticle, currentColor(i / (float) count * 0.5f), (float) c.dustSize);
            add(p.level(), o, x, y, z, 0, 0, 0);
        }
    }

    private static void trail(Player p, GlimmerConfig c) {
        Vec3 v = p.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 0.0025) return;
        var rnd = p.getRandom();
        int n = whole(c.trailDensity, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            ParticleOptions opt = resolve(c.trailParticle, currentColor(0f), (float) c.dustSize);
            double t = rnd.nextDouble();
            add(p.level(), opt,
                    p.getX() - v.x * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    p.getY() + 0.1 + rnd.nextDouble() * 0.2,
                    p.getZ() - v.z * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    0, 0.01, 0);
        }
    }

    /** Glowing ribbon that follows an approximation of the swing arc. Cosmetic only. */
    private static void swing(Player p, GlimmerConfig c) {
        double cur = p.getAttackAnim(1.0f);
        if (cur <= 0 || p.getMainHandItem().isEmpty()) {
            lastSwing = 0;
            return;
        }
        double from = lastSwing < cur ? lastSwing : 0;
        int n = Math.max(1, c.swingSamples);
        var rnd = p.getRandom();
        for (int i = 1; i <= n; i++) {
            double t = from + (cur - from) * i / n;
            Vec3 pos = swingPoint(p, t, c);
            ParticleOptions o = resolve(c.swingParticle,
                    currentColor((float) (t * c.swingSpread)), (float) c.swingSize);
            add(p.level(), o,
                    pos.x + (rnd.nextDouble() - 0.5) * 0.04,
                    pos.y + (rnd.nextDouble() - 0.5) * 0.04,
                    pos.z + (rnd.nextDouble() - 0.5) * 0.04,
                    0, 0, 0);
        }
        lastSwing = cur;
    }

    private static Vec3 swingPoint(Player p, double t, GlimmerConfig c) {
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right

        double ang = Math.toRadians(c.swingArc * (0.5 - t)); // upper-right -> lower-left
        double tilt = Math.toRadians(c.swingTilt);
        double fwd = Math.cos(ang) * c.swingLength;
        double side = Math.sin(ang) * c.swingLength * Math.cos(tilt);
        double up = Math.sin(ang) * c.swingLength * Math.sin(tilt);

        double fwdH = fwd * Math.cos(pitch);
        double fwdV = -fwd * Math.sin(pitch);

        double ox = p.getX() + rx * 0.25;
        double oy = p.getY() + p.getBbHeight() * 0.7;
        double oz = p.getZ() + rz * 0.25;
        return new Vec3(ox + fx * fwdH + rx * side, oy + fwdV + up, oz + fz * fwdH + rz * side);
    }

    /** Burst of particles on whatever the local player swings at. Cosmetic only. */
    public static void hitBurst(Entity target) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || !c.hit) return;
        var rnd = target.level().getRandom();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        for (int i = 0; i < c.hitCount; i++) {
            ParticleOptions opt = resolve(c.hitParticle, currentColor(rnd.nextFloat() * 0.4f), (float) c.dustSize);
            double dx = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            double dy = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            double dz = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            add(target.level(), opt, target.getX(), cy, target.getZ(), dx, dy, dz);
        }
    }

    private static int whole(double amount, double roll) {
        int base = (int) Math.floor(amount);
        return base + (roll < amount - base ? 1 : 0);
    }
}
