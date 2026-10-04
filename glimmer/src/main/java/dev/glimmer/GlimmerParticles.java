package dev.glimmer;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glimmer's own particles (soft glow, sparkle, ring, flare, star, dot).
 * They fade in, shrink, spin, change color, twinkle, fall with gravity, bounce off blocks,
 * slide on the ground, get kicked when you walk through them, and always render fullbright.
 * Local to your client only.
 */
public final class GlimmerParticles {
    private GlimmerParticles() {}

    public static final String[] NAMES = {"soft_glow", "sparkle", "ring", "ring_thin", "ring_thick", "flare", "star", "dot", "bloom"};
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    /** How one particle should look and move. Filled in just before it is spawned. */
    public static final class Spec {
        public int rgb = 0xFFFFFF;
        public int rgb2 = 0xFFFFFF;
        public float size = 1.0F;
        public float alphaMul = 1.0F;
        public float spin = 0.0F;      // degrees per tick
        public float friction = 0.96F; // air drag
        public float fade = 1.5F;
        public int life = 20;
        public double dx, dy, dz;
        // physics
        public float gravity = 0.0F;
        public float bounce = 0.5F;
        public boolean collide = false;
        public float slide = 0.85F;
        public float push = 0.0F;
        public float twinkle = 0.0F;
        public boolean flat = false;   // lie flat on the ground
        public double grow = 0.0;      // > 0: ring that expands to this radius (blocks)
        public float fps = 120.0F;     // animation frames per second
        public float tailLife = 0.0F;  // > 0: drags a line behind it that lasts this many ticks
        public float tailSize = 0.5F;
        public int tailSteps = 2;

        public Spec copy() {
            Spec s = new Spec();
            s.rgb = rgb; s.rgb2 = rgb2; s.size = size; s.alphaMul = alphaMul; s.spin = spin;
            s.friction = friction; s.fade = fade; s.life = life; s.dx = dx; s.dy = dy; s.dz = dz;
            s.gravity = gravity; s.bounce = bounce; s.collide = collide; s.slide = slide;
            s.push = push; s.twinkle = twinkle; s.flat = flat; s.grow = grow; s.fps = fps;
            s.tailLife = tailLife; s.tailSize = tailSize; s.tailSteps = tailSteps;
            return s;
        }
    }

    // The game creates the particle synchronously inside addParticle, so a plain static works.
    private static Spec pending = new Spec();

    public static boolean isCustom(String name) {
        return TYPES.containsKey(name);
    }

    public static void register() {
        for (String n : NAMES) {
            SimpleParticleType t = FabricParticleTypes.simple();
            TYPES.put(n, t);
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("glimmer", n), t);
            ParticleProviderRegistry.getInstance().register(t, GlimmerProvider::new);
        }
    }

    public static void spawn(Level level, String name, double x, double y, double z, Spec spec) {
        SimpleParticleType t = TYPES.get(name);
        if (t == null) return;
        pending = spec;
        level.addParticle(t, x, y, z, 0.0, 0.0, 0.0);
    }

    private record GlimmerProvider(FabricSpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new GlimmerParticle(level, x, y, z, sprites.get(random), pending);
        }
    }

    private static final class GlimmerParticle extends SingleQuadParticle {
        private final float baseSize;
        private final float alphaMul;
        private final float fadeExp;
        private final float spinRad;
        private final float r1, g1, b1, r2, g2, b2;
        private final float bounce, slide, push, twinkle, phase;
        private final boolean bright;
        private final boolean flat;
        private final float growHalf;   // final half-size of an expanding ring (0 = normal particle)
        private final Quaternionf flatRot = new Quaternionf();
        private final float fps;
        private final float tailLife, tailSize;
        private final int tailSteps;
        private final float baseRoll;

        GlimmerParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, Spec s) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
            this.xd = s.dx;
            this.yd = s.dy;
            this.zd = s.dz;
            this.friction = s.friction;
            this.gravity = s.gravity;
            this.hasPhysics = s.collide;
            this.lifetime = Math.max(2, s.life);
            this.age = 0;
            this.r1 = ((s.rgb >> 16) & 0xFF) / 255.0F;
            this.g1 = ((s.rgb >> 8) & 0xFF) / 255.0F;
            this.b1 = (s.rgb & 0xFF) / 255.0F;
            this.r2 = ((s.rgb2 >> 16) & 0xFF) / 255.0F;
            this.g2 = ((s.rgb2 >> 8) & 0xFF) / 255.0F;
            this.b2 = (s.rgb2 & 0xFF) / 255.0F;
            this.rCol = r1;
            this.gCol = g1;
            this.bCol = b1;
            this.baseSize = 0.14F * s.size;
            this.quadSize = this.baseSize;
            this.alphaMul = s.alphaMul;
            this.fadeExp = Math.max(0.3F, s.fade);
            this.spinRad = (float) Math.toRadians(s.spin);
            this.bounce = s.bounce;
            this.slide = s.slide;
            this.push = s.push;
            this.twinkle = s.twinkle;
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.bright = GlimmerConfig.INSTANCE.glow;
            this.flat = s.flat;
            this.fps = s.fps;
            this.tailLife = s.tailLife;
            this.tailSize = s.tailSize;
            this.tailSteps = Math.max(1, s.tailSteps);
            this.growHalf = s.grow > 0 ? (float) (s.grow / 0.68) : 0.0F;
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.baseRoll = this.roll;
            this.alpha = 0.0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        /** Kick the particle away when the player walks or runs through it. */
        private void kickFromPlayer() {
            Player pl = Minecraft.getInstance().player;
            if (pl == null) return;
            double dx = this.x - pl.getX();
            double dz = this.z - pl.getZ();
            double dy = this.y - (pl.getY() + 0.5);
            double d2 = dx * dx + dz * dz + dy * dy;
            if (d2 > 1.0 || d2 < 1.0e-4) return;
            double d = Math.sqrt(d2);
            Vec3 v = pl.getDeltaMovement();
            double speed = Math.sqrt(v.x * v.x + v.z * v.z);
            double f = (1.0 - d) * (0.05 + speed * 1.4) * this.push;
            this.xd += dx / d * f;
            this.zd += dz / d * f;
            this.yd += (1.0 - d) * 0.02 * this.push * (1.0 + speed * 6.0);
        }

        @Override
        public void tick() {
            if (this.push > 0.0F) kickFromPlayer();
            super.tick();

            // ground contact: bounce while there is speed left, then rest and slide to a stop
            if (this.hasPhysics && this.onGround) {
                if (this.yd < -0.06 && this.bounce > 0.0F) {
                    this.yd = -this.yd * this.bounce;
                    this.onGround = false;
                } else {
                    this.yd = 0.0;
                }
                this.xd *= this.slide;
                this.zd *= this.slide;
            }

            applyVisual(this.age);
            if (this.tailLife > 0.0F) dragTail();
        }

        /** Leaves a short fading line of small glows along the path the particle just moved. */
        private void dragTail() {
            double sp = this.xd * this.xd + this.yd * this.yd + this.zd * this.zd;
            if (sp < 0.0004 || this.alpha < 0.05F) return;
            Spec t = new Spec();
            t.rgb = ((int) (this.rCol * 255.0F) << 16) | ((int) (this.gCol * 255.0F) << 8) | (int) (this.bCol * 255.0F);
            t.rgb2 = t.rgb;
            t.size = this.baseSize / 0.14F * this.tailSize;
            t.life = Math.max(2, (int) this.tailLife);
            t.alphaMul = this.alpha * 0.75F;
            t.fade = 1.2F;
            t.friction = 1.0F;
            t.fps = this.fps;
            for (int i = 1; i <= this.tailSteps; i++) {
                double f = i / (double) this.tailSteps;
                spawn(this.level, "soft_glow",
                        this.xo + (this.x - this.xo) * f, this.yo + (this.y - this.yo) * f, this.zo + (this.z - this.zo) * f, t);
            }
        }

        /** Sets size, fade, color and spin for a given point in the particle's life (in ticks, can be fractional). */
        private void applyVisual(float time) {
            float t = this.lifetime <= 0 ? 1.0F : Mth.clamp(time / (float) this.lifetime, 0.0F, 1.0F);
            float fadeIn = Math.min(1.0F, t / 0.12F);
            float fadeOut = 1.0F - Mth.clamp((t - 0.25F) / 0.75F, 0.0F, 1.0F);
            float flick = 1.0F - this.twinkle * 0.5F * (1.0F + (float) Math.sin(time * 0.85F + this.phase));
            this.alpha = this.alphaMul * fadeIn * (float) Math.pow(fadeOut, this.fadeExp) * flick;
            this.rCol = Mth.lerp(t, r1, r2);
            this.gCol = Mth.lerp(t, g1, g2);
            this.bCol = Mth.lerp(t, b1, b2);
            if (this.growHalf > 0.0F) {
                this.quadSize = ringSize(t);
            } else {
                float pop = Math.min(1.0F, t / 0.08F);
                this.quadSize = this.baseSize * (0.35F + 0.65F * pop) * (1.0F - 0.6F * t * t);
            }
            this.roll = this.baseRoll + this.spinRad * time;
            this.oRoll = this.roll;
        }

        /** Ease-out growth of an expanding ring: it spreads fast, then slows down. */
        private float ringSize(float t) {
            float g = Mth.clamp(t / 0.65F, 0.0F, 1.0F);
            float e = 1.0F - (1.0F - g) * (1.0F - g) * (1.0F - g);
            return this.growHalf * e + 0.02F;
        }

        private static Method flatMethod;
        private static boolean flatTried;

        /** Draws the particle lying flat on the ground (rotated to face up) when "flat" is on. */
        public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
            float time = this.age + partialTick;
            if (this.fps > 0.0F && this.fps < 200.0F) { // lower FPS = steppier, stylized animation
                float steps = this.fps / 20.0F;
                time = (float) Math.floor(time * steps) / steps;
            }
            applyVisual(time);
            if (this.flat) {
                try {
                    if (!flatTried) {
                        flatTried = true;
                        for (Method m : SingleQuadParticle.class.getDeclaredMethods()) {
                            if (m.getName().equals("extractRotatedQuad") && m.getParameterCount() == 4
                                    && m.getParameterTypes()[0] == QuadParticleRenderState.class) {
                                m.setAccessible(true);
                                flatMethod = m;
                                break;
                            }
                        }
                    }
                    if (flatMethod != null) {
                        float rl = Mth.lerp(partialTick, this.oRoll, this.roll);
                        this.flatRot.identity().rotateY(rl).rotateX(-1.5707964F);
                        flatMethod.invoke(this, state, camera, this.flatRot, partialTick);
                        return;
                    }
                } catch (Throwable ignored) {
                    flatMethod = null;
                }
            }
            super.extract(state, camera, partialTick);
        }

        // Emissive: full brightness regardless of world light. Both names exist because the
        // method was renamed between versions.
        public int getLightColor(float partialTick) {
            return this.bright ? 15728880 : 11534512;
        }

        public int getLightCoords(float partialTick) {
            return this.bright ? 15728880 : 11534512;
        }
    }
}
