package dev.glimmer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/** Moves, scales and tilts the first-person hand and held item. Applied around the hand render. */
public final class Viewmodel {
    private Viewmodel() {}

    private static boolean pushed = false;

    public static void begin(PoseStack ps) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        GlimmerConfig.View v = c.view;
        pushed = c.enabled && v.on;
        if (!pushed) return;
        ps.pushPose();
        ps.translate((float) v.x, (float) v.y, (float) -v.z);
        // scale and rotate around roughly where the item sits on screen
        float px = 0.56F, py = -0.52F, pz = -0.72F;
        ps.translate(px, py, pz);
        ps.mulPose(new Quaternionf().rotationXYZ((float) Math.toRadians(v.pitch), (float) Math.toRadians(v.yaw), (float) Math.toRadians(v.roll)));
        float s = (float) v.scale;
        ps.scale(s, s, s);
        ps.translate(-px, -py, -pz);
    }

    public static void end(PoseStack ps) {
        if (!pushed) return;
        pushed = false;
        ps.popPose();
    }
}
