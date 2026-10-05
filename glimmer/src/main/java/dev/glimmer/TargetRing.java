package dev.glimmer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Spinning target marker around the enemy you just hit. It is a normal depth-tested overlay, so it
 * is hidden behind walls like anything else: it only marks a target you can already see.
 */
public final class TargetRing {
    private TargetRing() {}

    private static int targetId = -1;
    private static int hitTick = -10000;

    public static void set(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == null || e == mc.player) return;
        targetId = e.getId();
        hitTick = GlimmerEffects.now();
    }

    private static float[][] arc(Vec3 cam, double cx, double cy, double cz, double r, double a0, double span, int n) {
        float[][] p = new float[3][n];
        for (int i = 0; i < n; i++) {
            double a = a0 + span * i / (n - 1);
            p[0][i] = (float) (cx + Math.cos(a) * r - cam.x);
            p[1][i] = (float) (cy - cam.y);
            p[2][i] = (float) (cz + Math.sin(a) * r - cam.z);
        }
        return p;
    }

    public static void render(LevelRenderContext ctx) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        GlimmerConfig.Target t = c.target;
        if (!c.enabled || !t.on || targetId < 0) return;
        double holdTicks = t.holdSeconds * 20.0;
        double age = GlimmerEffects.now() - hitTick;
        if (age > holdTicks) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(targetId);
        if (e == null || !e.isAlive()) return;

        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        float partial = Mth.clamp((System.nanoTime() - GlimmerStreaks.lastTickNanos()) / 50.0e6F, 0.0F, 1.0F);
        double ex = e.xo + (e.getX() - e.xo) * partial;
        double ey = e.yo + (e.getY() - e.yo) * partial + t.height;
        double ez = e.zo + (e.getZ() - e.zo) * partial;

        double dist = Math.sqrt((ex - cam.x) * (ex - cam.x) + (ey - cam.y) * (ey - cam.y) + (ez - cam.z) * (ez - cam.z));
        float width = (float) t.width * (t.scaleWithDistance ? (float) Mth.clamp((float) (0.6 + dist * 0.08), 1.0F, 5.0F) : 1.0F);
        double r = t.radius * Math.max(0.5, e.getBbWidth() / 0.6);
        double time = System.nanoTime() / 1.0e9 * t.speed;
        double left = holdTicks - age;
        float fade = (float) Mth.clamp((float) (left / 10.0), 0.0F, 1.0F); // fades out over the last half second

        GlimmerStreaks.Style st = new GlimmerStreaks.Style();
        st.taper = 0.0F;
        st.fade = 0.0F;
        st.opacity = (float) t.opacity * fade;
        st.rgb = GlimmerEffects.accentColor();
        st.rgb2 = c.gradient && !c.rainbow ? (c.color2 & 0xFFFFFF) : st.rgb;
        final int n = Math.max(12, t.segments);
        final String style = t.style;
        final double fex = ex, fey = ey, fez = ez, fr = r;
        final float fw = width;

        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(), (pose, buf) -> {
            if (style.equals("circle") || style.equals("both")) {
                float[][] full = arc(cam, fex, fey, fez, fr, 0, Math.PI * 2, n);
                if (t.glow > 0.01) GlimmerStreaks.ribbon(pose, buf, full[0], full[1], full[2], n, st, fw * 3.0F, (float) t.glow * 0.25F, 0.0F);
                GlimmerStreaks.ribbon(pose, buf, full[0], full[1], full[2], n, st, fw * 0.6F, 0.45F, 0.0F);
                float[][] bright = arc(cam, fex, fey, fez, fr, time * 2.2, Math.PI * 0.55, 24);
                if (t.glow > 0.01) GlimmerStreaks.ribbon(pose, buf, bright[0], bright[1], bright[2], 24, st, fw * 3.0F, (float) t.glow * 0.4F, 0.0F);
                GlimmerStreaks.ribbon(pose, buf, bright[0], bright[1], bright[2], 24, st, fw, 1.0F, 0.2F);
            }
            if (style.equals("quarter") || style.equals("both")) {
                double rr = style.equals("both") ? fr * 0.8 : fr;
                for (int k = 0; k < 2; k++) { // outer pair spins one way
                    float[][] a = arc(cam, fex, fey + 0.02, fez, rr * 1.15, time * 1.6 + k * Math.PI, Math.PI * 0.42, 20);
                    if (t.glow > 0.01) GlimmerStreaks.ribbon(pose, buf, a[0], a[1], a[2], 20, st, fw * 3.0F, (float) t.glow * 0.35F, 0.0F);
                    GlimmerStreaks.ribbon(pose, buf, a[0], a[1], a[2], 20, st, fw, 1.0F, 0.15F);
                }
                for (int k = 0; k < 2; k++) { // inner pair spins the other way
                    float[][] a = arc(cam, fex, fey + 0.02, fez, rr * 0.92, -time * 1.6 + Math.PI * 0.5 + k * Math.PI, Math.PI * 0.42, 20);
                    if (t.glow > 0.01) GlimmerStreaks.ribbon(pose, buf, a[0], a[1], a[2], 20, st, fw * 3.0F, (float) t.glow * 0.35F, 0.0F);
                    GlimmerStreaks.ribbon(pose, buf, a[0], a[1], a[2], 20, st, fw, 1.0F, 0.15F);
                }
            }
        });
    }
}
