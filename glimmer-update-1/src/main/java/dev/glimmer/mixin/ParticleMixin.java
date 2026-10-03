package dev.glimmer.mixin;

import dev.glimmer.GlimmerEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes particles that Glimmer spawns fullbright (they ignore world darkness, so they
 * look glowing/emissive). Particles from the rest of the game are not touched.
 * If a method name below doesn't exist on this Minecraft version the injection is just skipped.
 */
@Mixin(targets = "net.minecraft.client.particle.Particle", remap = false)
public abstract class ParticleMixin {
    @Unique
    private boolean glimmer$glow;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void glimmer$init(CallbackInfo ci) {
        this.glimmer$glow = GlimmerEffects.spawning;
    }

    @Inject(method = {"getLightColor(F)I", "getLightCoords(F)I"}, at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void glimmer$light(float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (this.glimmer$glow) {
            cir.setReturnValue(15728880); // full block + sky light
        }
    }
}
