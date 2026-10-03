package dev.glimmer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawns vanilla particles on the local client only. Nothing here touches
 * the server, packets, hitboxes, reach, or any gameplay value.
 */
public final class GlimmerEffects {
    private GlimmerEffects() {}

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

    public static ParticleOptions resolve(String name) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (name.equals("dust")) {
            return new DustParticleOptions(currentColor(0f), (float) c.dustSize);
        }
        ParticleOptions p = SIMPLE.get(name);
        return p != null ? p : ParticleTypes.END_ROD;
    }

    public static int currentColor(float offset) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.rainbow) return c.color & 0xFFFFFF;
        float hue = (float) (((tick * 0.01 * c.rainbowSpeed) + offset) % 1.0);
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }

    public static void tick(Minecraft mc) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || mc.player == null || mc.level == null || mc.isPaused()) return;
        tick++;
        Player p = mc.player;
        if (!c.showInFirstPerson && mc.options.getCameraType().isFirstPerson()) return;
        if (p.isSpectator()) return;

        if (c.aura) aura(p, c);
        if (c.orbit) orbit(p, c);
        if (c.trail) trail(p, c);
    }

    private static void aura(Player p, GlimmerConfig c) {
        ParticleOptions opt = resolve(c.auraParticle);
        var rnd = p.getRandom();
        int n = whole(c.auraDensity, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = c.auraRadius * (0.6 + rnd.nextDouble() * 0.4);
            double y = p.getY() + rnd.nextDouble() * p.getBbHeight();
            p.level().addParticle(opt, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private static void orbit(Player p, GlimmerConfig c) {
        ParticleOptions opt = resolve(c.orbitParticle);
        int count = Math.max(1, c.orbitCount);
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(tick * c.orbitSpeed) + (Math.PI * 2 * i / count);
            double x = p.getX() + Math.cos(a) * c.orbitRadius;
            double z = p.getZ() + Math.sin(a) * c.orbitRadius;
            double y = p.getY() + c.orbitHeight + Math.sin(tick * 0.1 + i) * 0.15;
            ParticleOptions o = c.orbitParticle.equals("dust")
                    ? new DustParticleOptions(currentColor(i / (float) count * 0.5f), (float) c.dustSize)
                    : opt;
            p.level().addParticle(o, x, y, z, 0, 0, 0);
        }
    }

    private static void trail(Player p, GlimmerConfig c) {
        Vec3 v = p.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 0.0025) return;
        ParticleOptions opt = resolve(c.trailParticle);
        var rnd = p.getRandom();
        int n = whole(c.trailDensity, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            double t = rnd.nextDouble();
            p.level().addParticle(opt,
                    p.getX() - v.x * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    p.getY() + 0.1 + rnd.nextDouble() * 0.2,
                    p.getZ() - v.z * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    0, 0.01, 0);
        }
    }

    /** Burst of particles on whatever the local player swings at. Cosmetic only. */
    public static void hitBurst(Entity target) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || !c.hit) return;
        ParticleOptions opt = resolve(c.hitParticle);
        var rnd = target.level().getRandom();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        for (int i = 0; i < c.hitCount; i++) {
            double dx = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            double dy = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            double dz = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4;
            target.level().addParticle(opt, target.getX(), cy, target.getZ(), dx, dy, dz);
        }
    }

    private static int whole(double amount, double roll) {
        int base = (int) Math.floor(amount);
        return base + (roll < amount - base ? 1 : 0);
    }
}
