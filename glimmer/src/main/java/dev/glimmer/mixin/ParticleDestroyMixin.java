package dev.glimmer.mixin;

import dev.glimmer.GlimmerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optionally hides the normal block-break particles so only Glimmer's are shown. */
@Mixin(targets = "net.minecraft.client.particle.ParticleEngine", remap = false)
public abstract class ParticleDestroyMixin {
    @Inject(method = "destroy(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void glimmer$destroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (c.enabled && c.breakFx.on && c.breakHideVanilla) ci.cancel();
    }
}
