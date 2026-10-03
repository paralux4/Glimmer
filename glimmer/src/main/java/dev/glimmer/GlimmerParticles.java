package dev.glimmer;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glimmer's own particles (soft glow, sparkle, ring, flare, star, dot).
 * They fade in, shrink, spin, change color over their life and always render fullbright.
 * Local to your client only.
 */
public final class GlimmerParticles {
    private GlimmerParticles() {}

    public static final String[] NAMES = {"soft_glow", "sparkle", "ring", "flare", "star", "dot"};
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    /** How one particle should look. Filled in just before it is spawned. */
    public static final class Spec {
        public int rgb = 0xFFFFFF;
        public int rgb2 = 0xFFFFFF;
        public float size = 1.0F;
        public float alphaMul = 1.0F;
        public float spin = 0.0F;      // degrees per tick
        public float friction = 0.96F;
        public float fade = 1.5F;
        public int life = 20;
        public double dx, dy, dz;

        public Spec copy() {
            Spec s = new Spec();
            s.rgb = rgb; s.rgb2 = rgb2; s.size = size; s.alphaMul = alphaMul; s.spin = spin;
            s.friction = friction; s.fade = fade; s.life = life; s.dx = dx; s.dy = dy; s.dz = dz;
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

        GlimmerParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, Spec s) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
            this.xd = s.dx;
            this.yd = s.dy;
            this.zd = s.dz;
            this.friction = s.friction;
            this.gravity = 0.0F;
            this.hasPhysics = false;
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
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
            this.alpha = 0.0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            float t = this.lifetime <= 0 ? 1.0F : (float) this.age / (float) this.lifetime;
            float fadeIn = Math.min(1.0F, t / 0.12F);
            float fadeOut = 1.0F - Mth.clamp((t - 0.25F) / 0.75F, 0.0F, 1.0F);
            this.alpha = this.alphaMul * fadeIn * (float) Math.pow(fadeOut, this.fadeExp);
            this.rCol = Mth.lerp(t, r1, r2);
            this.gCol = Mth.lerp(t, g1, g2);
            this.bCol = Mth.lerp(t, b1, b2);
            this.oRoll = this.roll;
            this.roll += this.spinRad;
        }

        // No @Override on purpose: if a method is named differently on a future version it is
        // simply skipped instead of breaking the build.
        public float getQuadSize(float partialTick) {
            float t = Mth.clamp((this.age + partialTick) / Math.max(1, this.lifetime), 0.0F, 1.0F);
            float pop = Math.min(1.0F, t / 0.08F);
            return this.baseSize * (0.35F + 0.65F * pop) * (1.0F - 0.75F * t * t);
        }

        // Always fullbright. Both names exist because the method was renamed between versions.
        public int getLightColor(float partialTick) {
            return 15728880;
        }

        public int getLightCoords(float partialTick) {
            return 15728880;
        }
    }
}
