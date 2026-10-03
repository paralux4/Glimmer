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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawns particles on the local client only. Nothing here touches
 * the server, packets, hitboxes, reach, or any gameplay value.
 */
public final class GlimmerEffects {
    private GlimmerEffects() {}

    /** True only while Glimmer is spawning a vanilla particle; the mixin reads this to make it fullbright. */
    public static volatile boolean spawning = false;

    public static final Map<String, ParticleOptions> VANILLA = new LinkedHashMap<>();
    static {
        VANILLA.put("end_rod", ParticleTypes.END_ROD);
        VANILLA.put("glow", ParticleTypes.GLOW);
        VANILLA.put("electric_spark", ParticleTypes.ELECTRIC_SPARK);
        VANILLA.put("enchant", ParticleTypes.ENCHANT);
        VANILLA.put("enchanted_hit", ParticleTypes.ENCHANTED_HIT);
        VANILLA.put("crit", ParticleTypes.CRIT);
        VANILLA.put("flame", ParticleTypes.FLAME);
        VANILLA.put("soul_fire_flame", ParticleTypes.SOUL_FIRE_FLAME);
        VANILLA.put("happy_villager", ParticleTypes.HAPPY_VILLAGER);
        VANILLA.put("heart", ParticleTypes.HEART);
        VANILLA.put("note", ParticleTypes.NOTE);
        VANILLA.put("witch", ParticleTypes.WITCH);
        VANILLA.put("portal", ParticleTypes.PORTAL);
        VANILLA.put("snowflake", ParticleTypes.SNOWFLAKE);
        VANILLA.put("cloud", ParticleTypes.CLOUD);
    }

    /** All particle names the menu can choose from. */
    public static List<String> particleNames() {
        List<String> list = new ArrayList<>();
        for (String n : GlimmerParticles.NAMES) list.add(n);
        list.add("dust");
        list.addAll(VANILLA.keySet());
        return list;
    }

    private static int tick = 0;
    private static double lastSwing = 0;
    private static double stepDist = 0;
    private static boolean stepSide = false;

    // ------------------------------------------------------------------ colors

    private static int hsv(float hue) {
        float h = hue % 1.0F;
        if (h < 0) h += 1.0F;
        return Mth.hsvToRgb(h, 0.85F, 1.0F) & 0xFFFFFF;
    }

    /** Color of the menu accent and the global color, animated if rainbow. */
    public static int accentColor() {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (c.rainbow) return hsv((float) (System.nanoTime() / 1.0e9 * 0.1 * c.rainbowSpeed));
        return c.color & 0xFFFFFF;
    }

    /** Start (end=false) or end (end=true) color of a particle for this layer. */
    public static int layerColor(GlimmerConfig.Layer l, float offset, boolean end) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        boolean rb = l.customColor ? l.rainbow : c.rainbow;
        boolean grad = l.customColor ? l.gradient : c.gradient;
        int col = l.customColor ? l.color : c.color;
        int col2 = l.customColor ? l.color2 : c.color2;
        if (rb) {
            float hue = (float) (tick * 0.01 * c.rainbowSpeed) + offset;
            return hsv(end && grad ? hue + 0.18F : hue);
        }
        return (end && grad ? col2 : col) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ spawning

    private static ParticleOptions vanilla(String name, int rgb, float size) {
        if (name.equals("dust")) return new DustParticleOptions(rgb, size);
        ParticleOptions p = VANILLA.get(name);
        return p != null ? p : ParticleTypes.END_ROD;
    }

    private static void addVanilla(Level level, ParticleOptions o, double x, double y, double z,
                                   double dx, double dy, double dz) {
        spawning = GlimmerConfig.INSTANCE.glow;
        try {
            level.addParticle(o, x, y, z, dx, dy, dz);
        } finally {
            spawning = false;
        }
    }

    /**
     * Spawns one particle (plus its soft halo) using a layer's look settings.
     * baseLife is in ticks, friction is how fast its motion dies off.
     */
    private static void emit(Level level, GlimmerConfig.Layer l, double x, double y, double z,
                             double dx, double dy, double dz, float colorOffset, float sizeMul,
                             int baseLife, float friction) {
        int rgb = layerColor(l, colorOffset, false);
        int rgb2 = layerColor(l, colorOffset, true);
        float size = (float) (l.size * sizeMul);

        if (GlimmerParticles.isCustom(l.particle)) {
            GlimmerParticles.Spec s = new GlimmerParticles.Spec();
            s.rgb = rgb;
            s.rgb2 = rgb2;
            s.size = size;
            s.life = Math.max(3, Math.round(baseLife * (float) l.life));
            s.dx = dx;
            s.dy = dy + l.rise;
            s.dz = dz;
            s.friction = friction;
            s.fade = (float) l.fade;
            s.spin = (float) l.spin;
            GlimmerParticles.spawn(level, l.particle, x, y, z, s);

            if (l.halo > 0.01 && !l.particle.equals("ring")) {
                GlimmerParticles.Spec h = s.copy();
                h.alphaMul = (float) l.halo;
                h.size = size * (float) l.haloSize;
                h.spin = 0.0F;
                GlimmerParticles.spawn(level, "soft_glow", x, y, z, h);
            }
        } else {
            addVanilla(level, vanilla(l.particle, rgb, size), x, y, z, dx, dy + l.rise, dz);
        }
    }

    // ------------------------------------------------------------------ main tick

    public static void tick(Minecraft mc) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || mc.player == null || mc.level == null || mc.isPaused()) return;
        tick++;
        Player p = mc.player;
        if (p.isSpectator()) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean scaleMe = c.followScaleMe && ScaleMeCompat.loaded();
        double k = scaleMe ? ScaleMeCompat.playerScale() : 1.0;

        if (c.showInFirstPerson || !firstPerson) {
            if (c.aura.on) aura(p, c, k);
            if (c.orbit.on) orbit(p, c, k);
            if (c.trail.on) trail(p, c);
        }
        if (c.foot.on) footsteps(p, c, k);
        if (c.weapon.on) weapon(p, c, k);

        if (c.swing.on && (!firstPerson || c.swingFirstPerson)) swing(p, c, k, firstPerson && scaleMe);
        else lastSwing = 0;
    }

    private static void aura(Player p, GlimmerConfig c, double k) {
        var rnd = p.getRandom();
        int n = whole(c.auraAmount, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = c.auraRadius * k * (0.6 + rnd.nextDouble() * 0.4);
            double y = p.getY() + rnd.nextDouble() * p.getBbHeight() * k;
            emit(p.level(), c.aura, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r,
                    0, 0.012 + rnd.nextDouble() * 0.012, 0, rnd.nextFloat() * 0.3F, 1.0F, 36, 0.995F);
        }
    }

    private static void orbit(Player p, GlimmerConfig c, double k) {
        int count = Math.max(1, c.orbitCount);
        for (int i = 0; i < count; i++) {
            double a = Math.toRadians(tick * c.orbitSpeed) + (Math.PI * 2 * i / count);
            double x = p.getX() + Math.cos(a) * c.orbitRadius * k;
            double z = p.getZ() + Math.sin(a) * c.orbitRadius * k;
            double y = p.getY() + c.orbitHeight * k + Math.sin(tick * 0.1 + i) * 0.15 * k;
            emit(p.level(), c.orbit, x, y, z, 0, 0, 0, i / (float) count * 0.5F, 1.0F, 10, 1.0F);
        }
    }

    private static void trail(Player p, GlimmerConfig c) {
        Vec3 v = p.getDeltaMovement();
        if (v.horizontalDistanceSqr() < 0.0025) return;
        var rnd = p.getRandom();
        int n = whole(c.trailAmount, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            double t = rnd.nextDouble();
            emit(p.level(), c.trail,
                    p.getX() - v.x * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    p.getY() + 0.1 + rnd.nextDouble() * 0.2,
                    p.getZ() - v.z * t * 2 + (rnd.nextDouble() - 0.5) * 0.3,
                    0, 0.01, 0, 0F, 1.0F, 22, 0.97F);
        }
    }

    /** A small expanding ring of glow on the ground each time you take a step. */
    private static void footsteps(Player p, GlimmerConfig c, double k) {
        Vec3 v = p.getDeltaMovement();
        double hs = Math.sqrt(v.horizontalDistanceSqr());
        if (!p.onGround() || hs < 0.02) return;
        stepDist += hs;
        if (stepDist < c.footInterval * k) return;
        stepDist = 0;
        stepSide = !stepSide;

        double dirX = v.x / hs, dirZ = v.z / hs;
        double sideX = -dirZ, sideZ = dirX;
        double side = (stepSide ? 1 : -1) * c.footSide * k;
        double cx = p.getX() + sideX * side;
        double cz = p.getZ() + sideZ * side;
        double cy = p.getY() + 0.03;

        int n = Math.max(4, c.footPoints);
        float fr = 0.84F;
        double v0 = c.footRadius * k * (1.0 - fr);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            emit(p.level(), c.foot, cx + Math.cos(a) * 0.04, cy, cz + Math.sin(a) * 0.04,
                    Math.cos(a) * v0, 0, Math.sin(a) * v0, i / (float) n * 0.4F, 1.0F, 18, fr);
        }
    }

    /** Soft glow hugging the held item (a tunable stand-in for a weapon outline). */
    private static void weapon(Player p, GlimmerConfig c, double k) {
        if (p.getMainHandItem().isEmpty()) return;
        if (c.swing.on && p.getAttackAnim(1.0F) > 0) return; // the swing trail takes over
        var rnd = p.getRandom();
        int n = whole(c.weaponAmount, rnd.nextDouble());
        if (n <= 0) return;

        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double hx = p.getX() + (fx * c.weaponForward + rx * c.weaponSide) * k;
        double hz = p.getZ() + (fz * c.weaponForward + rz * c.weaponSide) * k;
        double hy = p.getY() + (p.getEyeHeight() + c.weaponHeight) * k;

        for (int i = 0; i < n; i++) {
            double t = rnd.nextDouble() * c.weaponLength * k;
            emit(p.level(), c.weapon,
                    hx + fx * t * 0.6 + (rnd.nextDouble() - 0.5) * 0.06,
                    hy + t * 0.8 + (rnd.nextDouble() - 0.5) * 0.06,
                    hz + fz * t * 0.6 + (rnd.nextDouble() - 0.5) * 0.06,
                    0, 0.004, 0, rnd.nextFloat() * 0.2F, 1.0F, 14, 0.98F);
        }
    }

    /**
     * Glowing ribbon that follows the swing arc, for every swing (weapons, empty hand, blocks).
     * With ScaleMe installed it follows ScaleMe's swing speed, arc size, item scale, and
     * switches off when ScaleMe has the swing animation disabled.
     */
    private static void swing(Player p, GlimmerConfig c, double k, boolean useScaleMe) {
        double cur = p.getAttackAnim(1.0F);
        if (cur <= 0) {
            lastSwing = 0;
            return;
        }
        if (p.getMainHandItem().isEmpty() && !c.swingEmptyHand) {
            lastSwing = 0;
            return;
        }

        double arcMul = 1.0, lenMul = 1.0;
        if (useScaleMe) {
            if (ScaleMeCompat.swingDisabled()) {
                lastSwing = 0;
                return;
            }
            arcMul = ScaleMeCompat.arcMultiplier();
            if (arcMul <= 0.0) {
                lastSwing = 0;
                return;
            }
            lenMul = ScaleMeCompat.itemScale();
            cur = Math.min(1.0, cur * ScaleMeCompat.swingSpeed());
        }

        double from = cur < lastSwing - 1.0e-6 ? 0.0 : lastSwing; // progress dropped = new swing
        if (cur <= from + 1.0e-6) return;

        int n = Math.max(1, c.swingSamples);
        var rnd = p.getRandom();
        for (int i = 1; i <= n; i++) {
            double t = from + (cur - from) * i / n;
            Vec3 pos = swingPoint(p, t, c.swingArc * arcMul, c.swingLength * lenMul * k, c.swingTilt);
            emit(p.level(), c.swing,
                    pos.x + (rnd.nextDouble() - 0.5) * 0.04,
                    pos.y + (rnd.nextDouble() - 0.5) * 0.04,
                    pos.z + (rnd.nextDouble() - 0.5) * 0.04,
                    0, 0, 0, (float) (t * 0.5), 1.0F, 14, 1.0F);
        }
        lastSwing = cur;
    }

    private static Vec3 swingPoint(Player p, double t, double arcDeg, double length, double tiltDeg) {
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // right

        double ang = Math.toRadians(arcDeg * (0.5 - t));   // upper-right -> lower-left
        double tilt = Math.toRadians(tiltDeg);
        double fwd = Math.cos(ang) * length;
        double side = Math.sin(ang) * length * Math.cos(tilt);
        double up = Math.sin(ang) * length * Math.sin(tilt);

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
        if (!c.enabled || !c.hit.on) return;
        var rnd = target.level().getRandom();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        double kk = GlimmerParticles.isCustom(c.hit.particle) ? 0.35 : 1.0;
        for (int i = 0; i < c.hitCount; i++) {
            double dx = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk;
            double dy = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk;
            double dz = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk;
            emit(target.level(), c.hit, target.getX(), cy, target.getZ(), dx, dy, dz,
                    rnd.nextFloat() * 0.4F, 1.0F, 16, 0.86F);
        }
    }

    private static int whole(double amount, double roll) {
        int base = (int) Math.floor(amount);
        return base + (roll < amount - base ? 1 : 0);
    }
}
