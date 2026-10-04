package dev.glimmer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.glimmer.CelShade;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which kind of item (hand, GUI, ground...) is being submitted, so only held items are cel-shaded. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState", remap = false)
public abstract class ItemStateMixin {
    @Shadow
    public ItemDisplayContext displayContext;

    private static final String SUBMIT = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), require = 0, remap = false)
    private void glimmer$begin(PoseStack ps, SubmitNodeCollector collector, int a, int b, int c, CallbackInfo ci) {
        CelShade.current = this.displayContext;
    }

    @Inject(method = SUBMIT, at = @At("RETURN"), require = 0, remap = false)
    private void glimmer$end(PoseStack ps, SubmitNodeCollector collector, int a, int b, int c, CallbackInfo ci) {
        CelShade.current = null;
    }
}
