package dev.glimmer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
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
        for (String n : GlimmerParticles.NAMES) if (!n.equals("bloom")) list.add(n);
        list.add("dust");
        list.addAll(VANILLA.keySet());
        return list;
    }

    private static int tick = 0;
    private static double lastSwing = 0;
    private static double stepDist = 0;
    private static boolean stepSide = false;
    private static int lastSwingTick = -100;
    private static int ringIdx = 0;
    private static int lastRingTick = -100;
    private static final Map<Integer, Integer> recentHit = new HashMap<>();

    // movement tracking (landing + teleport rings)
    private static Level lastLevel = null;
    private static double lx, ly, lz;
    private static boolean wasGround = true;
    private static double airMaxY = 0;

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

    private static int toWhite(int rgb, double amount) {
        double t = Mth.clamp((float) amount, 0.0F, 1.0F);
        int r = (int) Math.round(((rgb >> 16) & 255) * (1 - t) + 255 * t);
        int g = (int) Math.round(((rgb >> 8) & 255) * (1 - t) + 255 * t);
        int b = (int) Math.round((rgb & 255) * (1 - t) + 255 * t);
        return (r << 16) | (g << 8) | b;
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
     * Spawns one particle using a layer's look, glow and physics settings.
     * baseLife is in ticks. friction <= 0 means "use the layer's air drag".
     */
    private static void emit(Level level, GlimmerConfig.Layer l, double x, double y, double z,
                             double dx, double dy, double dz, float colorOffset, float sizeMul,
                             int baseLife, float friction) {
        emit(level, l, x, y, z, dx, dy, dz, colorOffset, sizeMul, baseLife, friction, 0.0);
    }

    private static void emit(Level level, GlimmerConfig.Layer l, double x, double y, double z,
                             double dx, double dy, double dz, float colorOffset, float sizeMul,
                             int baseLife, float friction, double grow) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        int rgb = layerColor(l, colorOffset, false);
        int rgb2 = layerColor(l, colorOffset, true);
        float size = (float) (l.size * sizeMul);
        double b = Math.max(0.05, c.brightness * l.brightness);
        double white = Mth.clamp((float) ((b - 1.0) * 0.08), 0.0F, 0.35F);

        if (!GlimmerParticles.isCustom(l.particle)) {
            addVanilla(level, vanilla(l.particle, toWhite(rgb, white), size), x, y, z, dx, dy + l.rise, dz);
            return;
        }

        GlimmerParticles.Spec s = new GlimmerParticles.Spec();
        s.rgb = toWhite(rgb, white);
        s.rgb2 = toWhite(rgb2, white);
        s.size = size;
        s.life = Math.max(3, Math.round(baseLife * (float) l.life));
        s.dx = dx;
        s.dy = dy + l.rise;
        s.dz = dz;
        s.friction = friction > 0 ? friction : (float) l.drag;
        s.fade = (float) l.fade;
        s.spin = (float) l.spin;
        s.gravity = (float) l.gravity;
        s.bounce = (float) l.bounce;
        s.collide = l.collide;
        s.slide = (float) l.slide;
        s.push = (float) l.push;
        s.twinkle = (float) l.twinkle;
        s.flat = l.flat;
        s.grow = grow * l.size;
        s.fps = (float) (l.fps > 0 ? l.fps : c.animFps);

        // brightness: below 1 dims, above 1 stacks extra copies (each one adds light)
        int copies = 1;
        float extra = 0.0F;
        if (b <= 1.0) {
            s.alphaMul = (float) b;
        } else {
            copies = (int) Math.min(6, Math.floor(b));
            extra = (float) (b - Math.floor(b));
            if (copies >= 6) extra = 0.0F;
        }
        for (int i = 0; i < copies; i++) GlimmerParticles.spawn(level, l.particle, x, y, z, s);
        if (extra > 0.05F) {
            GlimmerParticles.Spec e2 = s.copy();
            e2.alphaMul = extra;
            GlimmerParticles.spawn(level, l.particle, x, y, z, e2);
        }

        // white-hot core: a smaller, brighter copy that moves with the particle
        if (l.core > 0.01 && grow <= 0) {
            GlimmerParticles.Spec core = s.copy();
            core.size = size * 0.55F;
            core.rgb = toWhite(rgb, 0.4 + 0.55 * l.core);
            core.rgb2 = toWhite(rgb2, 0.4 + 0.55 * l.core);
            core.alphaMul = 1.0F;
            core.twinkle = 0.0F;
            double[] cp = away(x, y, z, -0.015);
            GlimmerParticles.spawn(level, l.particle, cp[0], cp[1], cp[2], core);
        }

        // bloom: a very soft, wide glow behind the particle
        double bloom = c.bloom * l.bloom;
        if (bloom > 0.02 && grow <= 0) {
            GlimmerParticles.Spec bs = s.copy();
            bs.size = size * (float) c.bloomSize;
            bs.alphaMul = (float) Math.min(0.9, 0.22 * bloom * Math.sqrt(Math.max(1.0, b)));
            bs.rgb = toWhite(rgb, 0.15 + white);
            bs.rgb2 = toWhite(rgb2, 0.15 + white);
            bs.spin = 0.0F;
            bs.twinkle = 0.0F;
            bs.flat = false;
            double[] bp = away(x, y, z, 0.03);
            GlimmerParticles.spawn(level, "bloom", bp[0], bp[1], bp[2], bs);
        }
    }

    /** Moves a point along the line from the camera so layers of one particle always sort the same way. */
    private static double[] away(double x, double y, double z, double amount) {
        Player pl = Minecraft.getInstance().player;
        if (pl == null) return new double[]{x, y, z};
        double dx = x - pl.getX(), dy = y - (pl.getY() + pl.getEyeHeight()), dz = z - pl.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5) return new double[]{x, y, z};
        double k = amount / len;
        return new double[]{x + dx * k, y + dy * k, z + dz * k};
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

        boolean teleported = movement(mc, p, c, k);

        if (c.showInFirstPerson || !firstPerson) {
            if (c.aura.on) aura(p, c, k);
            if (c.orbit.on) orbit(p, c, k);
            if (c.trail.on) trail(p, c);
        }
        if (c.foot.on && !teleported) footsteps(p, c, k);
        if (c.weapon.on) weapon(p, c, k);

        if (c.swing.on && (!firstPerson || c.swingFirstPerson)) swing(p, c, k, firstPerson && scaleMe);
        else lastSwing = 0;

        hitScan(mc, p, c);
    }

    private static void aura(Player p, GlimmerConfig c, double k) {
        var rnd = p.getRandom();
        int n = whole(c.auraAmount, rnd.nextDouble());
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = c.auraRadius * k * (0.6 + rnd.nextDouble() * 0.4);
            double y = p.getY() + rnd.nextDouble() * p.getBbHeight() * k;
            emit(p.level(), c.aura, p.getX() + Math.cos(a) * r, y, p.getZ() + Math.sin(a) * r,
                    0, 0.012 + rnd.nextDouble() * 0.012, 0, rnd.nextFloat() * 0.3F, 1.0F, 36, -1F);
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
                    0, 0.01, 0, 0F, 1.0F, 22, -1F);
        }
    }

    // ------------------------------------------------------------------ rings

    /** One big ring of glow on the ground that expands outward from (cx, cy, cz). */
    private static void ring(Level level, GlimmerConfig c, double cx, double cy, double cz,
                             double radiusMul, double k) {
        if (c.foot.particle.startsWith("ring")) { // one real ring that expands outward
            double lift = 0.03 + (ringIdx++ % 6) * 0.006;
            emit(level, c.foot, cx, cy + lift, cz, 0, 0, 0, 0F, 1.0F, 22, 1.0F, c.footRadius * radiusMul * k);
            return;
        }
        int n = Math.max(8, c.footPoints);
        float fr = 0.84F;
        double v0 = c.footRadius * radiusMul * k * (1.0 - fr);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cs = Math.cos(a), sn = Math.sin(a);
            emit(level, c.foot, cx + cs * 0.1, cy + 0.03, cz + sn * 0.1,
                    cs * v0, 0, sn * v0, i / (float) n * 0.4F, 1.0F, 18, fr);
        }
    }

    /** Detects jump landings and teleports (like Aspect of the Void). Returns true on a teleport. */
    private static boolean movement(Minecraft mc, Player p, GlimmerConfig c, double k) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        boolean ground = p.onGround();

        if (lastLevel != mc.level) { // joined a world or changed dimension: just reset
            lastLevel = mc.level;
            lx = x; ly = y; lz = z;
            wasGround = ground;
            airMaxY = y;
            return false;
        }

        double dx = x - lx, dy = y - ly, dz = z - lz;
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean teleported = false;

        if (c.foot.on && c.teleportRing && moved > c.teleportMin) {
            teleported = true;
            if (c.teleportDeparture) ring(p.level(), c, lx, ly, lz, 1.0, k);
            ring(p.level(), c, x, y, z, c.landScale, k);
            airMaxY = y;
        } else if (c.foot.on) {
            if (!ground) {
                airMaxY = Math.max(airMaxY, y);
            } else if (!wasGround) {
                double fall = airMaxY - y;
                if (c.landRing && fall >= c.landMinFall) {
                    double scale = c.landScale * (1.0 + Math.min(fall, 8.0) * 0.08);
                    ring(p.level(), c, x, y, z, scale, k);
                }
                airMaxY = y;
            } else {
                airMaxY = y;
            }
            if (c.jumpRing && wasGround && !ground && p.getDeltaMovement().y > 0.2) {
                ring(p.level(), c, x, y, z, 0.8, k);
            }
        } else {
            airMaxY = y;
        }

        lx = x; ly = y; lz = z;
        wasGround = ground;
        if (teleported) stepDist = 0;
        return teleported;
    }

    /** A big ring on the ground every time you take a step. */
    private static void footsteps(Player p, GlimmerConfig c, double k) {
        Vec3 v = p.getDeltaMovement();
        double hs = Math.sqrt(v.horizontalDistanceSqr());
        if (!p.onGround() || hs < 0.02) return;
        stepDist += hs;
        if (stepDist < c.footInterval * k) return;
        if (tick - lastRingTick < c.footMinGap) return; // don't pile rings on top of each other
        lastRingTick = tick;
        stepDist = 0;
        stepSide = !stepSide;

        double dirX = v.x / hs, dirZ = v.z / hs;
        double side = (stepSide ? 1 : -1) * c.footSide * k;
        ring(p.level(), c, p.getX() - dirZ * side, p.getY(), p.getZ() + dirX * side, 1.0, k);
    }

    // ------------------------------------------------------------------ weapon + swing

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

    // ------------------------------------------------------------------ hits

    /**
     * Shows the hit effect on targets that really got hurt while you were swinging, up to
     * "hitRange" blocks away. This is what lets long-range weapons (like the Dark Claymore)
     * still show their hit effects even though the normal attack is not registered by the game.
     */
    private static void hitScan(Minecraft mc, Player p, GlimmerConfig c) {
        if (p.getAttackAnim(1.0F) > 0) lastSwingTick = tick;
        if (!c.hit.on || c.hitRange <= 0 || tick - lastSwingTick > 8) return;

        double range = Math.min(5.0, c.hitRange);
        AABB box = p.getBoundingBox().inflate(range);
        for (Entity e : mc.level.getEntities(p, box, x -> true)) {
            if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (le.hurtTime < 8) continue; // only entities that were hurt this very moment
            if (p.distanceTo(e) > range + 1.0F) continue;
            hitBurst(e);
        }
    }

    /** Burst of falling, bouncing stars on a target you hit. Cosmetic only. */
    public static void hitBurst(Entity target) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (!c.enabled || !c.hit.on) return;

        int id = target.getId();
        if (tick - recentHit.getOrDefault(id, -1000) < 8) return; // already played for this hit
        recentHit.put(id, tick);
        if (recentHit.size() > 64) recentHit.entrySet().removeIf(en -> tick - en.getValue() > 40);

        var rnd = target.level().getRandom();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        double kk = GlimmerParticles.isCustom(c.hit.particle) ? 0.35 : 1.0;
        for (int i = 0; i < c.hitCount; i++) {
            double dx = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk;
            double dy = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk + c.hitLift * (0.5 + rnd.nextDouble());
            double dz = (rnd.nextDouble() - 0.5) * 2 * c.hitSpread * 4 * kk;
            emit(target.level(), c.hit, target.getX(), cy, target.getZ(), dx, dy, dz,
                    rnd.nextFloat() * 0.4F, 0.6F + rnd.nextFloat() * 0.8F, 44, -1F);
        }
    }

    private static int whole(double amount, double roll) {
        int base = (int) Math.floor(amount);
        return base + (roll < amount - base ? 1 : 0);
    }
}
