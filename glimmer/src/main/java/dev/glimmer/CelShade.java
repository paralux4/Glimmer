package dev.glimmer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cel-shaded held items: flat lighting plus a colored, glowing outline that follows the exact
 * shape of the item's sprite (read from the sprite's transparent pixels), drawn just behind the
 * item. Purely visual. Hooked in by the two mixins in dev.glimmer.mixin.
 */
public final class CelShade {
    private CelShade() {}

    /** The display context of the item currently being submitted (set by ItemStateMixin). */
    public static volatile ItemDisplayContext current = null;

    private static Method poseMatrix;
    private static boolean poseTried;
    private static boolean warned;

    // ------------------------------------------------------------ settings helpers

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
        double t = Mth.clamp((float) ((cel.brightness - 1.0) * 0.12), 0.0F, 0.6F);
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

    // ------------------------------------------------------------ sprite silhouette

    /** Which pixels of a sprite are visible. Read once per sprite through reflection. */
    private static final class Sprite {
        int w, h;
        boolean[] opaque;
        boolean ok;
    }

    private static final Map<Object, Sprite> SPRITES = new WeakHashMap<>();

    private static Sprite sprite(TextureAtlasSprite sp) {
        Sprite cached = SPRITES.get(sp);
        if (cached != null) return cached;
        Sprite s = new Sprite();
        try {
            Object contents = sp.contents();
            s.w = (Integer) contents.getClass().getMethod("width").invoke(contents);
            s.h = (Integer) contents.getClass().getMethod("height").invoke(contents);
            s.opaque = new boolean[s.w * s.h];
            Method trans = null;
            try {
                trans = contents.getClass().getMethod("isTransparent", int.class, int.class, int.class);
            } catch (NoSuchMethodException ignored) {
            }
            if (trans != null) {
                for (int y = 0; y < s.h; y++) {
                    for (int x = 0; x < s.w; x++) {
                        s.opaque[y * s.w + x] = !((Boolean) trans.invoke(contents, 0, x, y));
                    }
                }
                s.ok = true;
            } else {
                // fall back to reading the pixels of the sprite's original image
                for (Method m : contents.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().equals("NativeImage")) {
                        Object img = m.invoke(contents);
                        Method px = img.getClass().getMethod("getPixel", int.class, int.class);
                        for (int y = 0; y < s.h; y++) {
                            for (int x = 0; x < s.w; x++) {
                                int argb = (Integer) px.invoke(img, x, y);
                                s.opaque[y * s.w + x] = ((argb >>> 24) & 0xFF) > 8;
                            }
                        }
                        s.ok = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            s.ok = false;
        }
        if (!s.ok && !warned) {
            warned = true;
            System.err.println("[Glimmer] Cel outline: could not read the item sprite's pixels on this version.");
        }
        SPRITES.put(sp, s);
        return s;
    }

    /** Outline rings (center + glow steps) as lists of cell rows {x0, x1, y}, on a padded grid. */
    private static final class Rings {
        int pad, scale, gw, gh;
        List<int[]>[] rects;
    }

    private static final Map<String, Rings> RING_CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static Rings rings(Sprite sp, Object key, int r0, int step, int count) {
        String k = System.identityHashCode(key) + ":" + r0 + ":" + step + ":" + count;
        Rings cached = RING_CACHE.get(k);
        if (cached != null) return cached;
        if (RING_CACHE.size() > 48) RING_CACHE.clear();

        int s = Math.max(1, (int) Math.ceil(48.0 / Math.max(sp.w, sp.h)));
        int rMax = r0 + step * count;
        int pad = rMax + 1;
        int gw = sp.w * s + pad * 2, gh = sp.h * s + pad * 2;
        boolean[] base = new boolean[gw * gh];
        for (int y = 0; y < sp.h * s; y++) {
            for (int x = 0; x < sp.w * s; x++) {
                if (sp.opaque[(y / s) * sp.w + (x / s)]) base[(y + pad) * gw + (x + pad)] = true;
            }
        }

        boolean[][] masks = new boolean[count + 1][];
        for (int i = 0; i <= count; i++) masks[i] = dilate(base, gw, gh, r0 + step * i);

        Rings out = new Rings();
        out.pad = pad;
        out.scale = s;
        out.gw = gw;
        out.gh = gh;
        out.rects = new List[count + 1];
        for (int i = 0; i <= count; i++) {
            List<int[]> list = new ArrayList<>();
            for (int y = 0; y < gh; y++) {
                int start = -1;
                for (int x = 0; x <= gw; x++) {
                    boolean on = x < gw && masks[i][y * gw + x] && (i == 0 || !masks[i - 1][y * gw + x]);
                    if (on && start < 0) start = x;
                    if (!on && start >= 0) {
                        list.add(new int[]{start, x, y});
                        start = -1;
                    }
                }
            }
            out.rects[i] = list;
        }
        RING_CACHE.put(k, out);
        return out;
    }

    private static boolean[] dilate(boolean[] src, int gw, int gh, int r) {
        boolean[] out = new boolean[src.length];
        int r2 = r * r + r;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                if (!src[y * gw + x]) continue;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= gh) continue;
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dy * dy > r2) continue;
                        int xx = x + dx;
                        if (xx < 0 || xx >= gw) continue;
                        out[yy * gw + xx] = true;
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ drawing

    private static float u(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    private static float v(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    /** Draws the outline (and its glow) just behind the item that is about to be submitted. */
    public static void outline(OrderedSubmitNodeCollector collector, PoseStack ps, ItemDisplayContext ctx,
                               List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty() || !wanted(ctx != null ? ctx : current)) return;
        GlimmerConfig.Cel cel = GlimmerConfig.INSTANCE.cel;

        // pick the big flat face of the item that points away from the camera
        BakedQuad face = chooseFace(collector, ps, quads, cel.flip);
        if (face == null) return;
        TextureAtlasSprite tex = face.materialInfo().sprite();
        Sprite sp = sprite(tex);
        if (!sp.ok) return;

        // map sprite pixels onto the face: P(u, v) = P0 + Gu * (u - u0) + Gv * (v - v0)
        Vector3fc p0 = face.position(0), p1 = face.position(1), p2 = face.position(2), p3 = face.position(3);
        long[] uv = {face.packedUV(0), face.packedUV(1), face.packedUV(2), face.packedUV(3)};
        float u0 = u(uv[0]), v0 = v(uv[0]);
        float du1 = u(uv[1]) - u0, dv1 = v(uv[1]) - v0, du3 = u(uv[3]) - u0, dv3 = v(uv[3]) - v0;
        float det = du1 * dv3 - du3 * dv1;
        if (Math.abs(det) < 1.0e-12F) return;
        float e1x = p1.x() - p0.x(), e1y = p1.y() - p0.y(), e1z = p1.z() - p0.z();
        float e3x = p3.x() - p0.x(), e3y = p3.y() - p0.y(), e3z = p3.z() - p0.z();
        float gux = (e1x * dv3 - e3x * dv1) / det, guy = (e1y * dv3 - e3y * dv1) / det, guz = (e1z * dv3 - e3z * dv1) / det;
        float gvx = (e3x * du1 - e1x * du3) / det, gvy = (e3y * du1 - e1y * du3) / det, gvz = (e3z * du1 - e1z * du3) / det;
        float umin = Math.min(Math.min(u(uv[0]), u(uv[1])), Math.min(u(uv[2]), u(uv[3])));
        float umax = Math.max(Math.max(u(uv[0]), u(uv[1])), Math.max(u(uv[2]), u(uv[3])));
        float vmin = Math.min(Math.min(v(uv[0]), v(uv[1])), Math.min(v(uv[2]), v(uv[3])));
        float vmax = Math.max(Math.max(v(uv[0]), v(uv[1])), Math.max(v(uv[2]), v(uv[3])));

        // face normal (points away from the camera) and which way our quads must wind to face the camera
        float ax = p1.x() - p0.x(), ay = p1.y() - p0.y(), az = p1.z() - p0.z();
        float bx = p2.x() - p0.x(), by = p2.y() - p0.y(), bz = p2.z() - p0.z();
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0e-9F) return;
        nx /= nl; ny /= nl; nz /= nl;
        float mx = guy * gvz - guz * gvy, my = guz * gvx - gux * gvz, mz = gux * gvy - guy * gvx;
        boolean reverse = (mx * nx + my * ny + mz * nz) > 0.0F;
        float shift = 0.004F;

        int s0 = (int) Math.max(0, Math.round(cel.thickness * Math.max(1, (int) Math.ceil(48.0 / Math.max(sp.w, sp.h)))));
        int step = (int) Math.max(1, Math.round(cel.glowSize * Math.max(1, (int) Math.ceil(48.0 / Math.max(sp.w, sp.h)))));
        int count = cel.glow > 0.02 ? 3 : 0;
        Rings rg = rings(sp, tex, s0, step, count);

        int rgb = outlineRgb();
        for (int ring = count; ring >= 0; ring--) {
            double a = ring == 0 ? 1.0 : cel.glow * (ring == 1 ? 0.55 : ring == 2 ? 0.32 : 0.16);
            int cr = (int) (((rgb >> 16) & 255) * a), cg = (int) (((rgb >> 8) & 255) * a), cb = (int) ((rgb & 255) * a);
            final int argb = ((int) Math.round(a * 255.0) << 24) | (cr << 16) | (cg << 8) | cb;
            final List<int[]> rects = rg.rects[ring];
            final Rings fr = rg;
            final float fu0 = u0, fv0 = v0;
            final float fgux = gux, fguy = guy, fguz = guz, fgvx = gvx, fgvy = gvy, fgvz = gvz;
            final float fnx = nx, fny = ny, fnz = nz, fshift = shift;
            final boolean frev = reverse;
            final float fumin = umin, fumax = umax, fvmin = vmin, fvmax = vmax;
            collector.submitCustomGeometry(ps, RenderTypes.debugQuads(), (pose, buf) -> {
                float[] cx = new float[4], cy = new float[4], cz = new float[4];
                for (int[] r : rects) {
                    float xa = (r[0] - fr.pad) / (float) (fr.scale * sp.w), xb = (r[1] - fr.pad) / (float) (fr.scale * sp.w);
                    float ya = (r[2] - fr.pad) / (float) (fr.scale * sp.h), yb = (r[2] + 1 - fr.pad) / (float) (fr.scale * sp.h);
                    float[] us = {fumin + xa * (fumax - fumin), fumin + xb * (fumax - fumin), fumin + xb * (fumax - fumin), fumin + xa * (fumax - fumin)};
                    float[] vs = {fvmin + ya * (fvmax - fvmin), fvmin + ya * (fvmax - fvmin), fvmin + yb * (fvmax - fvmin), fvmin + yb * (fvmax - fvmin)};
                    for (int i = 0; i < 4; i++) {
                        float du = us[i] - fu0, dv = vs[i] - fv0;
                        cx[i] = p0.x() + fgux * du + fgvx * dv + fnx * fshift;
                        cy[i] = p0.y() + fguy * du + fgvy * dv + fny * fshift;
                        cz[i] = p0.z() + fguz * du + fgvz * dv + fnz * fshift;
                    }
                    for (int k = 0; k < 4; k++) {
                        int i = frev ? 3 - k : k;
                        buf.addVertex(pose, cx[i], cy[i], cz[i]).setColor(argb);
                    }
                }
            });
        }
    }

    /** The big front/back face of a flat item that faces away from the camera (or toward it, if flipped). */
    private static BakedQuad chooseFace(OrderedSubmitNodeCollector collector, PoseStack ps, List<BakedQuad> quads,
                                        boolean flip) {
        final BakedQuad[] found = new BakedQuad[1];
        // we need the pose matrix to know which way faces point; PoseStack exposes it through last()
        Object last;
        try {
            last = PoseStack.class.getMethod("last").invoke(ps);
        } catch (Throwable t) {
            return null;
        }
        Matrix4fc m = matrix(last);
        if (m == null) return null;

        // a mirrored transform (left hand) reverses winding; detect it from the matrix itself
        Vector3f o = new Vector3f(), ex = new Vector3f(), ey = new Vector3f(), ez = new Vector3f();
        m.transformPosition(0, 0, 0, o);
        m.transformPosition(1, 0, 0, ex);
        m.transformPosition(0, 1, 0, ey);
        m.transformPosition(0, 0, 1, ez);
        float cx = (ex.y - o.y) * (ey.z - o.z) - (ex.z - o.z) * (ey.y - o.y);
        float cy = (ex.z - o.z) * (ey.x - o.x) - (ex.x - o.x) * (ey.z - o.z);
        float cz = (ex.x - o.x) * (ey.y - o.y) - (ex.y - o.y) * (ey.x - o.x);
        boolean mirrored = (cx * (ez.x - o.x) + cy * (ez.y - o.y) + cz * (ez.z - o.z)) < 0.0F;

        Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
        for (BakedQuad q : quads) {
            String dir = q.direction().name();
            if (!dir.equals("NORTH") && !dir.equals("SOUTH")) continue;
            Vector3fc q0 = q.position(0), q1 = q.position(1), q2 = q.position(2);
            m.transformPosition(q0.x(), q0.y(), q0.z(), a);
            m.transformPosition(q1.x(), q1.y(), q1.z(), b);
            m.transformPosition(q2.x(), q2.y(), q2.z(), c);
            float ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
            float vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            boolean away = (nx * a.x + ny * a.y + nz * a.z) > 0.0F;
            if (mirrored) away = !away;
            if (away != flip) {
                found[0] = q;
                break;
            }
        }
        return found[0];
    }
}
