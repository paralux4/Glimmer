package dev.glimmer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;

/**
 * Real screen-space bloom. Glimmer ships a small post-processing chain (bright-pass, blur, add back)
 * in several strengths; this class switches the game's post effect to the one matching your settings.
 * If anything about it fails on this game version it just turns itself off.
 */
public final class ScreenBloom {
    private ScreenBloom() {}

    private static Method setter;
    private static boolean searched;
    private static int failures = 0;
    private static String lastTried = "";

    /** True once the chain has failed to load a few times (shown in the menu). */
    public static boolean unavailable() {
        return failures >= 3;
    }

    private static Identifier idFor(GlimmerConfig c) {
        int s = Math.max(1, Math.min(6, c.bloomLevel));
        int r = Math.max(1, Math.min(3, c.bloomRadius));
        int t = Math.max(1, Math.min(3, c.bloomThreshold));
        int g = Math.max(0, Math.min(3, c.colorGrade));
        if (!c.screenBloom) return Identifier.fromNamespaceAndPath("glimmer", "grade_g" + g);
        return Identifier.fromNamespaceAndPath("glimmer", "bloom_s" + s + "_r" + r + "_t" + t + "_g" + g);
    }

    private static boolean ours(Identifier id) {
        return id != null && id.getNamespace().equals("glimmer");
    }

    private static void findSetter() {
        searched = true;
        for (Method m : GameRenderer.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Identifier.class
                    && m.getReturnType() == void.class && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                String n = m.getName().toLowerCase();
                if (n.contains("posteffect") || n.contains("loadeffect") || n.contains("seteffect")) {
                    m.setAccessible(true);
                    setter = m;
                    return;
                }
            }
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.gameRenderer == null) return;
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        GameRenderer gr = mc.gameRenderer;
        Identifier cur = gr.currentPostEffect();
        boolean want = c.enabled && (c.screenBloom || c.colorGrade > 0) && failures < 3;

        if (!want) {
            if (ours(cur)) gr.clearPostEffect();
            return;
        }
        if (cur != null && !ours(cur)) return; // some other post effect is active: leave it alone

        Identifier id = idFor(c);
        if (id.equals(cur)) return;
        if (!searched) findSetter();
        if (setter == null) {
            failures = 3;
            System.err.println("[Glimmer] Screen bloom: could not find the post effect method on this game version.");
            return;
        }
        try {
            setter.invoke(gr, id);
        } catch (Throwable t) {
            failures++;
            System.err.println("[Glimmer] Screen bloom failed: " + t);
            return;
        }
        if (!id.equals(gr.currentPostEffect())) {
            String key = id.toString();
            if (!key.equals(lastTried)) lastTried = key;
            failures++;
            System.err.println("[Glimmer] Screen bloom did not load (" + key + "); check the log above for shader errors.");
        } else {
            failures = 0;
        }
    }
}
