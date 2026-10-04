package dev.glimmer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Cel-shaded held items: the item is lit flat (ignores world lighting) and gets a colored,
 * glowing outline drawn from enlarged copies of the item's own back faces ("inverted hull").
 * Purely visual. Hooked in by the two mixins in dev.glimmer.mixin.
 */
public final class CelShade {
    private CelShade() {}

    /** The display context of the item currently being submitted (set by ItemStateMixin). */
    public static volatile ItemDisplayContext current = null;

    private static Method poseMatrix;
    private static boolean poseTried;

    private static boolean wanted(ItemDisplayContext ctx) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        if (ctx == null || !c.enabled || !c.cel.on) return false;
        String n = ctx.name();
        if (n.startsWith("FIRST_PERSON")) return c.cel.firstPerson;
        if (n.startsWith("THIRD_PERSON")) return c.cel.thirdPerson;
        return false;
    }

    /** Flat, even lighting for cel-shaded items. */
    public static int light(int light) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        return (c.cel.flatLight && wanted(current)) ? 15728880 : light;
    }

    private static int outlineRgb() {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        GlimmerConfig.Cel cel = c.cel;
        int rgb;
        if (cel.useGlobalColor) rgb = GlimmerEffects.accentColor();
        else if (cel.rainbow) rgb = Mth.hsvToRgb((float) ((System.nanoTime() / 1.0e9 * 0.1 * c.rainbowSpeed) % 1.0), 0.85F, 1.0F) & 0xFFFFFF;
        else rgb = cel.color & 0xFFFFFF;
        double white = Mth.clamp((float) ((cel.brightness - 1.0) * 0.12), 0.0F, 0.6F);
        double t = white;
        int r = (int) Math.round(((rgb >> 16) & 255) * (1 - t) + 255 * t);
        int g = (int) Math.round(((rgb >> 8) & 255) * (1 - t) + 255 * t);
        int b = (int) Math.round((rgb & 255) * (1 - t) + 255 * t);
        return (r << 16) | (g << 8) | b;
    }

    private static Matrix4fc matrix(Object pose) {
        try {
            if (!poseTried) {
                poseTried = true;
                for (Method m : pose.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && Matrix4fc.class.isAssignableFrom(m.getReturnType())) {
                        poseMatrix = m;
                        break;
                    }
                }
            }
            return poseMatrix == null ? null : (Matrix4fc) poseMatrix.invoke(pose);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Draws the outline (and its glow) behind the item that is about to be submitted. */
    public static void outline(OrderedSubmitNodeCollector collector, PoseStack ps, ItemDisplayContext ctx,
                               List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty() || !wanted(ctx != null ? ctx : current)) return;
        GlimmerConfig.Cel cel = GlimmerConfig.INSTANCE.cel;
        int rgb = outlineRgb();
        boolean flip = cel.flip;

        int passes = cel.glow > 0.02 ? 3 : 0;
        for (int i = passes; i >= 0; i--) {
            final double scale = 1.0 + cel.thickness + i * cel.glowSize;
            double a = i == 0 ? 1.0 : cel.glow * (i == 1 ? 0.5 : i == 2 ? 0.3 : 0.15);
            final int argb = ((int) Math.round(Mth.clamp((float) a, 0.0F, 1.0F) * 255.0) << 24) | rgb;
            collector.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, buf) -> {
                Matrix4fc m = matrix(pose);
                if (m == null) return; // can't tell which side faces away: draw nothing
                Vector3f a0 = new Vector3f(), b0 = new Vector3f(), c0 = new Vector3f();
                for (BakedQuad q : quads) {
                    Vector3fc p0 = q.position(0), p1 = q.position(1), p2 = q.position(2);
                    m.transformPosition(p0.x(), p0.y(), p0.z(), a0);
                    m.transformPosition(p1.x(), p1.y(), p1.z(), b0);
                    m.transformPosition(p2.x(), p2.y(), p2.z(), c0);
                    float ux = b0.x - a0.x, uy = b0.y - a0.y, uz = b0.z - a0.z;
                    float vx = c0.x - a0.x, vy = c0.y - a0.y, vz = c0.z - a0.z;
                    float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    boolean away = (nx * a0.x + ny * a0.y + nz * a0.z) > 0.0F; // faces away from the camera
                    if (away == flip) continue;
                    for (int k = 3; k >= 0; k--) { // reversed winding so it shows from the camera side
                        Vector3fc v = q.position(k);
                        float x = 0.5F + (v.x() - 0.5F) * (float) scale;
                        float y = 0.5F + (v.y() - 0.5F) * (float) scale;
                        float z = 0.5F + (v.z() - 0.5F) * (float) scale;
                        buf.addVertex(pose, x, y, z).setColor(argb);
                    }
                }
            });
        }
    }
}
