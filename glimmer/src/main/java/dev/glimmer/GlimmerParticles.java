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

/**
 * Glimmer's own particles: a soft round glow and a four-point sparkle.
 * They fade in, shrink and fade out smoothly, always render fullbright, and take any color.
 * Local to your client only.
 */
public final class GlimmerParticles {
    private GlimmerParticles() {}

    public static final SimpleParticleType GLOW = FabricParticleTypes.simple();
    public static final SimpleParticleType SPARKLE = FabricParticleTypes.simple();

    // Motion is handed to the particle through these just before it is created
    // (the game creates the particle synchronously inside addParticle).
    private static double pDx, pDy, pDz;
    private static float pFriction = 0.96F;

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("glimmer", "soft_glow"), GLOW);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("glimmer", "sparkle"), SPARKLE);
        ParticleProviderRegistry.getInstance().register(GLOW, GlimmerProvider::new);
        ParticleProviderRegistry.getInstance().register(SPARKLE, GlimmerProvider::new);
    }

    /** Spawns one of our particles. rgb = 0xRRGGBB, size = multiplier, life = ticks. */
    public static void spawn(Level level, SimpleParticleType type, double x, double y, double z,
                             int rgb, float size, int life, double dx, double dy, double dz, float friction) {
        pDx = dx;
        pDy = dy;
        pDz = dz;
        pFriction = friction;
        // color, size and lifetime travel in the three "velocity" slots
        level.addParticle(type, x, y, z, rgb, size, life);
    }

    private record GlimmerProvider(FabricSpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            int rgb = (int) vx;
            if (rgb == 0) rgb = 0xFFFFFF;
            float size = vy > 0 ? (float) vy : 1.0F;
            int life = vz > 0 ? (int) vz : 20;
            return new GlimmerParticle(level, x, y, z, sprites.get(random), rgb, size, life, pDx, pDy, pDz, pFriction);
        }
    }

    private static final class GlimmerParticle extends SingleQuadParticle {
        private final float baseSize;

        GlimmerParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite,
                        int rgb, float size, int life, double dx, double dy, double dz, float friction) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.friction = friction;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.lifetime = life;
            this.age = 0;
            this.rCol = ((rgb >> 16) & 0xFF) / 255.0F;
            this.gCol = ((rgb >> 8) & 0xFF) / 255.0F;
            this.bCol = (rgb & 0xFF) / 255.0F;
            this.baseSize = 0.14F * size;
            this.quadSize = this.baseSize;
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
            float fadeOut = 1.0F - Mth.clamp((t - 0.3F) / 0.7F, 0.0F, 1.0F);
            this.alpha = fadeIn * fadeOut * fadeOut;
        }

        // Smooth size change between ticks. (No @Override on purpose: if this method is
        // named differently on a future version it is simply skipped instead of breaking.)
        public float getQuadSize(float partialTick) {
            float t = Mth.clamp((this.age + partialTick) / Math.max(1, this.lifetime), 0.0F, 1.0F);
            float pop = Math.min(1.0F, t / 0.08F);
            return this.baseSize * (0.35F + 0.65F * pop) * (1.0F - 0.8F * t * t);
        }

        // Always fullbright so the particles glow in the dark. Both names are defined
        // because the method was renamed between versions; the unused one is harmless.
        public int getLightColor(float partialTick) {
            return 15728880;
        }

        public int getLightCoords(float partialTick) {
            return 15728880;
        }
    }
}
