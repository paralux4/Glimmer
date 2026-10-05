package dev.glimmer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Trail lines: thin, crisp ribbon lines (like hitbox lines) that follow a moving particle,
 * with adjustable length, width, taper, fade, glow and a white-hot core.
 * They are drawn as camera-facing strips, so they stay a clean line from any angle.
 */
public final class GlimmerStreaks {
    private GlimmerStreaks() {}

    /** Gives the current (interpolated) position of whatever the line is following. */
    public interface Head {
        void get(double[] out, float partial);
    }

    /** The look of one line, copied from a layer's settings. */
    public static final class Style {
        public int maxPoints, steps;
        public float width, taper, fade, glow, glowWidth, core, opacity, minSpeed;
        public int rgb, rgb2;
    }

    public static Style style(GlimmerConfig.Layer l, int rgb, int rgb2) {
        Style s = new Style();
        s.steps = Math.max(1, Math.min(4, l.tailDensity));
        s.maxPoints = (int) ((3 + l.tail * 30) * s.steps);
        s.width = (float) l.tailWidth;
        s.taper = (float) l.tailTaper;
        s.fade = (float) l.tailFade;
        s.glow = (float) l.tailGlow;
        s.glowWidth = (float) l.tailGlowWidth;
        s.core = (float) l.tailCore;
        s.opacity = (float) l.tailOpacity;
        s.minSpeed = (float) l.tailMinSpeed;
        s.rgb = rgb;
        s.rgb2 = rgb2;
        return s;
    }

    public static final class Streak {
        final Style st;
        final List<double[]> pts = new ArrayList<>();
        Head head;
        boolean ended, pushed;
        int touched;
        double lx, ly, lz;
        boolean hasLast;

        Streak(Style st) {
            this.st = st;
        }

        public float minSpeedSq() {
            return st.minSpeed * st.minSpeed;
        }

        /** Adds the particle's newest position (call once per tick while it is moving). */
        public void push(double x, double y, double z) {
            touched = tickCount;
            pushed = true;
            if (hasLast) {
                for (int i = 1; i <= st.steps; i++) {
                    double f = i / (double) st.steps;
                    pts.add(new double[]{lx + (x - lx) * f, ly + (y - ly) * f, lz + (z - lz) * f});
                }
            } else {
                pts.add(new double[]{x, y, z});
            }
            lx = x; ly = y; lz = z;
            hasLast = true;
            while (pts.size() > st.maxPoints) pts.remove(0);
        }

        /** The particle is gone: the line fades away on its own. */
        public void end() {
            ended = true;
            head = null;
        }
    }

    private static final List<Streak> ALL = new ArrayList<>();
    private static int tickCount = 0;
    private static long lastTickNanos = System.nanoTime();

    public static long lastTickNanos() {
        return lastTickNanos;
    }

    public static Streak start(Style st, Head head) {
        Streak s = new Streak(st);
        s.head = head;
        s.touched = tickCount;
        ALL.add(s);
        if (ALL.size() > 500) ALL.remove(0);
        return s;
    }

    /** Once per client tick, after the particles have moved. */
    public static void tick() {
        tickCount++;
        lastTickNanos = System.nanoTime();
        for (int i = ALL.size() - 1; i >= 0; i--) {
            Streak s = ALL.get(i);
            if (!s.ended && tickCount - s.touched > 4) s.end(); // particle vanished without telling us
            if (s.ended) {
                for (int k = 0; k < 2 && !s.pts.isEmpty(); k++) s.pts.remove(0);
            } else if (!s.pushed && !s.pts.isEmpty()) {
                s.pts.remove(0); // not moving: the line retracts
            }
            s.pushed = false;
            if (s.pts.size() < 2 && (s.ended || s.pts.isEmpty())) ALL.remove(i);
        }
    }

    // ------------------------------------------------------------ drawing

    private static int argb(float alpha, int rgb, float whiten) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F + 0.5F);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * whiten);
        g += (int) ((255 - g) * whiten);
        b += (int) ((255 - b) * whiten);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    public static void render(LevelRenderContext ctx) {
        GlimmerConfig cfg = GlimmerConfig.INSTANCE;
        if (ALL.isEmpty() || !cfg.enabled) return;
        Vec3 cam = ctx.levelState().cameraRenderState.pos;
        float partial = Mth.clamp((System.nanoTime() - lastTickNanos) / 50.0e6F, 0.0F, 1.0F);
        double[] h = new double[3];

        for (Streak s : ALL) {
            int extra = s.head != null ? 1 : 0;
            int n = s.pts.size() + extra;
            if (n < 2) continue;
            final float[] px = new float[n], py = new float[n], pz = new float[n];
            int idx = 0;
            if (s.head != null) {
                s.head.get(h, partial);
                px[idx] = (float) (h[0] - cam.x); py[idx] = (float) (h[1] - cam.y); pz[idx] = (float) (h[2] - cam.z);
                idx++;
            }
            for (int i = s.pts.size() - 1; i >= 0; i--) { // newest first
                double[] p = s.pts.get(i);
                px[idx] = (float) (p[0] - cam.x); py[idx] = (float) (p[1] - cam.y); pz[idx] = (float) (p[2] - cam.z);
                idx++;
            }
            final Style st = s.st;
            final int count = n;
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(), (pose, buf) -> {
                if (st.glow > 0.01F) ribbon(pose, buf, px, py, pz, count, st, st.width * st.glowWidth, st.glow * 0.35F, 0.0F);
                ribbon(pose, buf, px, py, pz, count, st, st.width, 1.0F, 0.0F);
                if (st.core > 0.01F) ribbon(pose, buf, px, py, pz, count, st, st.width * 0.45F, st.core, 0.7F);
            });
        }
    }

    static void ribbon(Object pose, com.mojang.blaze3d.vertex.VertexConsumer buf, float[] px, float[] py, float[] pz,
                               int n, Style st, float width, float alphaMul, float whiten) {
        float[] ox = new float[n], oy = new float[n], oz = new float[n], wid = new float[n], al = new float[n];
        int[] col = new int[n];
        float lpx = 0, lpy = 0, lpz = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float dx = px[b] - px[a], dy = py[b] - py[a], dz = pz[b] - pz[a];
            // perpendicular to the line and the view direction (camera is at the origin)
            float wx = dy * -pz[i] - dz * -py[i], wy = dz * -px[i] - dx * -pz[i], wz = dx * -py[i] - dy * -px[i];
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1.0e-8F) {
                wx = lpx; wy = lpy; wz = lpz;
            } else {
                wx /= wl; wy /= wl; wz /= wl;
            }
            lpx = wx; lpy = wy; lpz = wz;
            float w = Math.max(0.0F, width * (1.0F - st.taper * t));
            ox[i] = wx * w; oy[i] = wy * w; oz[i] = wz * w;
            al[i] = st.opacity * alphaMul * (float) Math.pow(1.0F - t, st.fade);
            int r = (int) (((st.rgb >> 16) & 255) * (1 - t) + ((st.rgb2 >> 16) & 255) * t);
            int g = (int) (((st.rgb >> 8) & 255) * (1 - t) + ((st.rgb2 >> 8) & 255) * t);
            int bl = (int) ((st.rgb & 255) * (1 - t) + (st.rgb2 & 255) * t);
            col[i] = (r << 16) | (g << 8) | bl;
        }
        @SuppressWarnings("unchecked")
        com.mojang.blaze3d.vertex.PoseStack.Pose pz0 = (com.mojang.blaze3d.vertex.PoseStack.Pose) pose;
        for (int i = 0; i < n - 1; i++) {
            int j = i + 1;
            int c0 = argb(al[i], col[i], whiten), c1 = argb(al[j], col[j], whiten);
            // drawn in both windings so it shows whether or not the pipeline culls back faces
            buf.addVertex(pz0, px[i] + ox[i], py[i] + oy[i], pz[i] + oz[i]).setColor(c0);
            buf.addVertex(pz0, px[j] + ox[j], py[j] + oy[j], pz[j] + oz[j]).setColor(c1);
            buf.addVertex(pz0, px[j] - ox[j], py[j] - oy[j], pz[j] - oz[j]).setColor(c1);
            buf.addVertex(pz0, px[i] - ox[i], py[i] - oy[i], pz[i] - oz[i]).setColor(c0);

            buf.addVertex(pz0, px[i] - ox[i], py[i] - oy[i], pz[i] - oz[i]).setColor(c0);
            buf.addVertex(pz0, px[j] - ox[j], py[j] - oy[j], pz[j] - oz[j]).setColor(c1);
            buf.addVertex(pz0, px[j] + ox[j], py[j] + oy[j], pz[j] + oz[j]).setColor(c1);
            buf.addVertex(pz0, px[i] + ox[i], py[i] + oy[i], pz[i] + oz[i]).setColor(c0);
        }
    }
}
