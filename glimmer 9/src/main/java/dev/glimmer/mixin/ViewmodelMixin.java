package dev.glimmer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.glimmer.Viewmodel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the viewmodel offset, scale and rotation around the first-person hand render. */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer", remap = false)
public abstract class ViewmodelMixin {
    private static final String M = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V";

    @Inject(method = M, at = @At("HEAD"), require = 0, remap = false)
    private void glimmer$begin(float partial, PoseStack ps, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        Viewmodel.begin(ps);
    }

    @Inject(method = M, at = @At("RETURN"), require = 0, remap = false)
    private void glimmer$end(float partial, PoseStack ps, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        Viewmodel.end(ps);
    }
}
