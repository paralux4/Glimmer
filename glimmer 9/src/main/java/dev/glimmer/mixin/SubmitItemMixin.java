package dev.glimmer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.glimmer.CelShade;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Adds the cel-shade outline and flat lighting to items as they are submitted for drawing. */
@Mixin(targets = "net.minecraft.client.renderer.SubmitNodeCollection", remap = false)
public abstract class SubmitItemMixin {
    private static final String SUBMIT = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), require = 0, remap = false)
    private void glimmer$outline(PoseStack ps, ItemDisplayContext ctx, int light, int overlay, int outline,
                                 int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType foil,
                                 CallbackInfo ci) {
        CelShade.outline((OrderedSubmitNodeCollector) (Object) this, ps, ctx, quads);
    }

    @ModifyVariable(method = SUBMIT, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0, remap = false)
    private int glimmer$light(int light) {
        return CelShade.light(light);
    }
}
