package dev.glimmer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Glimmer's settings menu, drawn from scratch with smooth animations:
 * slide/fade open and close, a sliding tab indicator, animated switches and sliders,
 * smooth scrolling and a live color picker. Opened with /glimmer. Saves automatically.
 */
public class GlimmerScreen extends Screen {
    private static final String[] TABS = {"General", "Color", "Aura", "Orbit", "Trail", "Swing", "Hit", "Steps", "Weapon", "Outline", "Break"};
    private static int tab = 0;

    private final List<Row> rows = new ArrayList<>();
    private Row dragging;
    private boolean closing = false;
    private float openT = 0f;
    private float tabT = 1f;
    private float scroll = 0f;
    private float scrollTarget = 0f;
    private float indicatorY = -1f;
    private float totalH = 0f;
    private long lastNano = System.nanoTime();
    private float dt = 0f;
    private int mx, my;
    private int accent = 0x55FFFF;
    private final float[] tabHover = new float[TABS.length];

    // layout (recomputed every frame)
    private int px, py, pw, ph, sw, vx, vy, vw, vh, ty0;
    private int tabH = 17;

    public GlimmerScreen() {
        super(Component.literal("Glimmer"));
    }

    @Override
    protected void init() {
        build();
    }

    // keep the game running so the effects stay visible behind the menu
    public boolean isPauseScreen() {
        return false;
    }

    public void removed() {
        GlimmerConfig.save();
    }

    @Override
    public void onClose() {
        GlimmerConfig.save();
        closing = true;
    }

    public void tick() {
        if (closing && openT <= 0.001f) {
            super.onClose();
        }
    }

    // ------------------------------------------------------------ helpers

    private static float ease(float t) {
        float u = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    private float approach(float cur, float target, float speed) {
        return cur + (target - cur) * (1f - (float) Math.exp(-dt * speed));
    }

    private static int argb(int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f + 0.5f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int rgb, float alpha) {
        if (alpha < 0.06f) return; // very low alpha would be drawn opaque by the game
        g.text(this.font, s, x, y, argb(rgb, alpha));
    }

    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private String fit(String s, int max) {
        if (font.width(s) <= max) return s;
        while (s.length() > 1 && font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0f;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        float s = max <= 0f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    private static int hsvToRgb(float h, float s, float v) {
        return Mth.hsvToRgb(Mth.clamp(h, 0f, 0.9999f), Mth.clamp(s, 0f, 1f), Mth.clamp(v, 0f, 1f)) & 0xFFFFFF;
    }

    // ------------------------------------------------------------ tabs

    private void select(int i) {
        tab = i;
        tabT = 0f;
        scroll = 0f;
        scrollTarget = 0f;
        build();
    }

    private void rebuild() {
        build();
    }

    private void build() {
        rows.clear();
        dragging = null;
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        switch (tab) {
            case 0 -> general(c);
            case 1 -> colorTab(c);
            case 2 -> {
                header("Shape");
                slider("Amount", 0, 10, false, () -> c.auraAmount, v -> c.auraAmount = v);
                slider("Radius", 0.2, 3, false, () -> c.auraRadius, v -> c.auraRadius = v);
                look(c.aura);
            }
            case 3 -> {
                header("Shape");
                slider("Count", 1, 12, true, () -> c.orbitCount, v -> c.orbitCount = (int) v);
                slider("Radius", 0.3, 3, false, () -> c.orbitRadius, v -> c.orbitRadius = v);
                slider("Speed", 0, 40, false, () -> c.orbitSpeed, v -> c.orbitSpeed = v);
                slider("Height", 0, 2.5, false, () -> c.orbitHeight, v -> c.orbitHeight = v);
                look(c.orbit);
            }
            case 4 -> {
                header("Shape");
                slider("Amount", 0, 10, false, () -> c.trailAmount, v -> c.trailAmount = v);
                look(c.trail);
            }
            case 5 -> {
                header("Swing");
                toggle("In 1st person", () -> c.swingFirstPerson, v -> c.swingFirstPerson = v);
                toggle("Empty hand + block swings", () -> c.swingEmptyHand, v -> c.swingEmptyHand = v);
                slider("Length", 0.8, 4, false, () -> c.swingLength, v -> c.swingLength = v);
                slider("Arc", 40, 260, false, () -> c.swingArc, v -> c.swingArc = v);
                slider("Tilt", -90, 90, false, () -> c.swingTilt, v -> c.swingTilt = v);
                slider("Smoothness", 1, 20, true, () -> c.swingSamples, v -> c.swingSamples = (int) v);
                look(c.swing);
            }
            case 6 -> {
                header("Shape");
                slider("Amount", 0, 60, true, () -> c.hitCount, v -> c.hitCount = (int) v);
                slider("Spread", 0.02, 0.6, false, () -> c.hitSpread, v -> c.hitSpread = v);
                slider("Upward pop", 0, 0.8, false, () -> c.hitLift, v -> c.hitLift = v);
                slider("Hit effect range", 0, 5, false, () -> c.hitRange, v -> c.hitRange = v);
                look(c.hit);
            }
            case 7 -> {
                header("Footstep ring");
                slider("Distance between steps", 0.2, 3, false, () -> c.footInterval, v -> c.footInterval = v);
                slider("Min ticks between rings", 0, 20, true, () -> c.footMinGap, v -> c.footMinGap = (int) v);
                slider("Ring radius", 0.15, 3, false, () -> c.footRadius, v -> c.footRadius = v);
                slider("Ring smoothness", 8, 96, true, () -> c.footPoints, v -> c.footPoints = (int) v);
                slider("Left/right offset", 0, 0.5, false, () -> c.footSide, v -> c.footSide = v);
                header("Landing and teleport");
                toggle("Ring when landing", () -> c.landRing, v -> c.landRing = v);
                slider("Min fall distance", 0.3, 4, false, () -> c.landMinFall, v -> c.landMinFall = v);
                slider("Landing ring size", 0.5, 4, false, () -> c.landScale, v -> c.landScale = v);
                toggle("Ring when jumping", () -> c.jumpRing, v -> c.jumpRing = v);
                toggle("Ring on teleport", () -> c.teleportRing, v -> c.teleportRing = v);
                toggle("Ring where you left", () -> c.teleportDeparture, v -> c.teleportDeparture = v);
                slider("Teleport distance", 2, 12, false, () -> c.teleportMin, v -> c.teleportMin = v);
                look(c.foot);
            }
            case 9 -> celTab(c);
            case 10 -> {
                header("Block breaking");
                slider("Amount", 0, 60, true, () -> c.breakCount, v -> c.breakCount = (int) v);
                slider("Spread", 0.02, 0.6, false, () -> c.breakSpread, v -> c.breakSpread = v);
                slider("Upward pop", 0, 0.6, false, () -> c.breakLift, v -> c.breakLift = v);
                toggle("Hide vanilla break particles", () -> c.breakHideVanilla, v -> c.breakHideVanilla = v);
                look(c.breakFx);
            }
            default -> {
                header("Weapon glow");
                note("Soft glow around the held item.");
                slider("Amount", 0, 8, false, () -> c.weaponAmount, v -> c.weaponAmount = v);
                slider("Forward", 0, 1.5, false, () -> c.weaponForward, v -> c.weaponForward = v);
                slider("Side", -1, 1, false, () -> c.weaponSide, v -> c.weaponSide = v);
                slider("Height", -1.2, 0.5, false, () -> c.weaponHeight, v -> c.weaponHeight = v);
                slider("Length", 0, 2, false, () -> c.weaponLength, v -> c.weaponLength = v);
                look(c.weapon);
            }
        }
    }

    private void general(GlimmerConfig c) {
        header("Master");
        toggle("Effects enabled", () -> c.enabled, v -> c.enabled = v);
        toggle("Glow (fullbright)", () -> c.glow, v -> c.glow = v);
        toggle("Aura/orbit in 1st person", () -> c.showInFirstPerson, v -> c.showInFirstPerson = v);
        header("Glow");
        slider("Particle brightness", 0.3, 6, false, () -> c.brightness, v -> c.brightness = v);
        slider("Particle bloom", 0, 2, false, () -> c.bloom, v -> c.bloom = v);
        slider("Particle bloom size", 1.2, 8, false, () -> c.bloomSize, v -> c.bloomSize = v);
        slider("Animation FPS", 5, 240, true, () -> c.animFps, v -> c.animFps = v);
        header("Screen bloom");
        note(ScreenBloom.unavailable() ? "Screen bloom could not load on this version." : "Real glow: bright things bleed light.");
        toggle("Screen bloom", () -> c.screenBloom, v -> c.screenBloom = v);
        slider("Strength", 1, 6, true, () -> c.bloomLevel, v -> c.bloomLevel = (int) v);
        slider("Spread", 1, 3, true, () -> c.bloomRadius, v -> c.bloomRadius = (int) v);
        slider("Brightness needed", 1, 3, true, () -> c.bloomThreshold, v -> c.bloomThreshold = (int) v);
        header("ScaleMe");
        note(ScaleMeCompat.loaded() ? "ScaleMe detected." : "ScaleMe is not installed.");
        toggle("Follow ScaleMe swing + scale", () -> c.followScaleMe, v -> c.followScaleMe = v);
        header("Presets");
        rows.add(new ButtonsRow(new String[]{"Cheat", "Ice", "Fire", "Void", "Rainbow"}, name -> {
            applyPreset(name);
            rebuild();
        }));
        header("Menu");
        slider("Animation speed", 0.3, 3, false, () -> c.uiSpeed, v -> c.uiSpeed = v);
        slider("Panel opacity", 0.4, 1, false, () -> c.uiOpacity, v -> c.uiOpacity = v);
        rows.add(new ButtonsRow(new String[]{"Reset all settings"}, name -> {
            GlimmerConfig.INSTANCE = new GlimmerConfig();
            GlimmerConfig.save();
            rebuild();
        }));
    }

    private void colorTab(GlimmerConfig c) {
        header("Global color");
        toggleR("Rainbow", () -> c.rainbow, v -> c.rainbow = v, this::rebuild);
        if (c.rainbow) slider("Rainbow speed", 0.1, 5, false, () -> c.rainbowSpeed, v -> c.rainbowSpeed = v);
        toggleR("Gradient (fade to 2nd color)", () -> c.gradient, v -> c.gradient = v, this::rebuild);
        rows.add(new StripRow(() -> c.color, () -> c.color2, () -> c.rainbow, () -> c.gradient));
        if (!c.rainbow) {
            colorBlock(() -> c.color, v -> c.color = v);
            if (c.gradient) {
                header("Second color");
                colorBlock(() -> c.color2, v -> c.color2 = v);
            }
        }
        note("Layers use this unless they have a custom color.");
    }

    private void look(GlimmerConfig.Layer l) {
        header("Look");
        toggle("Layer enabled", () -> l.on, v -> l.on = v);
        rows.add(new CycleRow("Particle", GlimmerEffects.particleNames(), () -> l.particle, v -> l.particle = v));
        slider("Size", 0.2, 4, false, () -> l.size, v -> l.size = v);
        slider("Opacity", 0.05, 1, false, () -> l.opacity, v -> l.opacity = v);
        slider("Lifetime", 0.3, 3, false, () -> l.life, v -> l.life = v);
        slider("Spin", -30, 30, false, () -> l.spin, v -> l.spin = v);
        slider("Float up/down", -0.05, 0.05, false, () -> l.rise, v -> l.rise = v);
        slider("Fade curve", 0.5, 3, false, () -> l.fade, v -> l.fade = v);
        slider("Animation FPS (0 = global)", 0, 240, true, () -> l.fps, v -> l.fps = v);
        toggle("Lie flat on ground", () -> l.flat, v -> l.flat = v);
        header("Trail lines");
        slider("Line length", 0, 1, false, () -> l.tail, v -> l.tail = v);
        slider("Line width", 0.002, 0.08, false, () -> l.tailWidth, v -> l.tailWidth = v);
        slider("Taper to a point", 0, 1, false, () -> l.tailTaper, v -> l.tailTaper = v);
        slider("Fade along line", 0.2, 4, false, () -> l.tailFade, v -> l.tailFade = v);
        slider("Line opacity", 0.05, 1, false, () -> l.tailOpacity, v -> l.tailOpacity = v);
        slider("Line glow", 0, 1, false, () -> l.tailGlow, v -> l.tailGlow = v);
        slider("Line glow width", 1, 8, false, () -> l.tailGlowWidth, v -> l.tailGlowWidth = v);
        slider("White-hot core", 0, 1, false, () -> l.tailCore, v -> l.tailCore = v);
        slider("Line smoothness", 1, 4, true, () -> l.tailDensity, v -> l.tailDensity = (int) v);
        slider("Min speed for line", 0, 0.2, false, () -> l.tailMinSpeed, v -> l.tailMinSpeed = v);
        header("Glow");
        slider("Bright core", 0, 1, false, () -> l.core, v -> l.core = v);
        slider("Twinkle", 0, 1, false, () -> l.twinkle, v -> l.twinkle = v);
        slider("Brightness x", 0, 3, false, () -> l.brightness, v -> l.brightness = v);
        slider("Bloom x", 0, 3, false, () -> l.bloom, v -> l.bloom = v);
        header("Physics");
        slider("Gravity", 0, 2, false, () -> l.gravity, v -> l.gravity = v);
        slider("Bounce", 0, 0.95, false, () -> l.bounce, v -> l.bounce = v);
        toggle("Collide with blocks", () -> l.collide, v -> l.collide = v);
        slider("Ground slide", 0.3, 1, false, () -> l.slide, v -> l.slide = v);
        slider("Air drag", 0.8, 1, false, () -> l.drag, v -> l.drag = v);
        slider("Pushed by you", 0, 2, false, () -> l.push, v -> l.push = v);
        header("Color");
        toggleR("Custom color", () -> l.customColor, v -> l.customColor = v, this::rebuild);
        if (!l.customColor) {
            note("Using the global color.");
            return;
        }
        toggleR("Rainbow", () -> l.rainbow, v -> l.rainbow = v, this::rebuild);
        toggleR("Gradient", () -> l.gradient, v -> l.gradient = v, this::rebuild);
        rows.add(new StripRow(() -> l.color, () -> l.color2, () -> l.rainbow, () -> l.gradient));
        if (!l.rainbow) {
            colorBlock(() -> l.color, v -> l.color = v);
            if (l.gradient) {
                header("Second color");
                colorBlock(() -> l.color2, v -> l.color2 = v);
            }
        }
    }

    private void celTab(GlimmerConfig c) {
        GlimmerConfig.Cel cel = c.cel;
        header("Cel-shaded item");
        note("Flat lighting and a glowing outline on held items.");
        toggle("Enabled", () -> cel.on, v -> cel.on = v);
        toggle("First person", () -> cel.firstPerson, v -> cel.firstPerson = v);
        toggle("Third person / others", () -> cel.thirdPerson, v -> cel.thirdPerson = v);
        toggle("Flat lighting", () -> cel.flatLight, v -> cel.flatLight = v);
        slider("Outline thickness (px)", 0, 4, false, () -> cel.thickness, v -> cel.thickness = v);
        slider("Glow", 0, 1, false, () -> cel.glow, v -> cel.glow = v);
        slider("Glow reach (px)", 0.3, 4, false, () -> cel.glowSize, v -> cel.glowSize = v);
        slider("Brightness", 0.5, 6, false, () -> cel.brightness, v -> cel.brightness = v);
        toggle("Flip outline side", () -> cel.flip, v -> cel.flip = v);
        note("Outline looks wrong? Try Flip.");
        header("Outline color");
        toggleR("Use global color", () -> cel.useGlobalColor, v -> cel.useGlobalColor = v, this::rebuild);
        if (cel.useGlobalColor) return;
        toggleR("Rainbow", () -> cel.rainbow, v -> cel.rainbow = v, this::rebuild);
        if (!cel.rainbow) colorBlock(() -> cel.color, v -> cel.color = v);
    }

    private void applyPreset(String name) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        c.glow = true;
        c.gradient = true;
        switch (name) {
            case "Cheat" -> {
                c.rainbow = false; c.color = 0x45E3FF; c.color2 = 0xB44CFF;
                c.brightness = 2.2; c.bloom = 0.35; c.bloomSize = 2.6;
                c.screenBloom = true; c.bloomLevel = 4; c.bloomRadius = 1; c.bloomThreshold = 3;
                c.cel.on = true; c.cel.glow = 0.8; c.cel.thickness = 1.0; c.cel.glowSize = 1.0; c.cel.brightness = 2.5;
                c.cel.useGlobalColor = true;
                c.hit.core = 0.8; c.hit.tail = 0.7; c.orbit.core = 0.6; c.swing.core = 0.5;
            }
            case "Ice" -> { c.rainbow = false; c.color = 0x55D7FF; c.color2 = 0xC9F4FF; }
            case "Fire" -> { c.rainbow = false; c.color = 0xFF5A00; c.color2 = 0xFFD23C; }
            case "Void" -> { c.rainbow = false; c.color = 0xB44CFF; c.color2 = 0x4C6BFF; }
            default -> { c.rainbow = true; }
        }
        for (GlimmerConfig.Layer l : new GlimmerConfig.Layer[]{c.aura, c.orbit, c.trail, c.swing, c.hit, c.foot, c.weapon, c.breakFx}) {
            l.customColor = false;
        }
        GlimmerConfig.save();
    }

    // ------------------------------------------------------------ row builders

    private void header(String t) {
        rows.add(new HeaderRow(t));
    }

    private void note(String t) {
        rows.add(new NoteRow(t));
    }

    private void toggle(String l, BooleanSupplier g, Consumer<Boolean> s) {
        rows.add(new ToggleRow(l, g, s, null));
    }

    private void toggleR(String l, BooleanSupplier g, Consumer<Boolean> s, Runnable after) {
        rows.add(new ToggleRow(l, g, s, after));
    }

    private void slider(String l, double min, double max, boolean integer, DoubleSupplier g, DoubleConsumer s) {
        rows.add(new SliderRow(l, min, max, integer, g, s));
    }

    private void colorBlock(IntSupplier get, IntConsumer set) {
        float[] hsv = rgbToHsv(get.getAsInt());
        rows.add(new HsvRow("Hue", 0, hsv, set));
        rows.add(new HsvRow("Saturation", 1, hsv, set));
        rows.add(new HsvRow("Brightness", 2, hsv, set));
    }

    // ------------------------------------------------------------ rendering

    private void layout() {
        pw = Math.min(this.width - 16, 330);
        ph = Math.min(this.height - 16, 250);
        sw = 66;
        float e = ease(openT);
        px = 8 - Math.round((1f - e) * 26f);
        py = (this.height - ph) / 2;
        vx = px + sw + 8;
        vw = pw - sw - 16;
        vy = py + 26;
        vh = ph - 32;
        ty0 = py + 34;
        tabH = Math.max(11, Math.min(17, (ph - 34 - 24) / TABS.length));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        GlimmerConfig cfg = GlimmerConfig.INSTANCE;
        this.mx = mouseX;
        this.my = mouseY;
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNano) / 1.0e9f) * (float) cfg.uiSpeed;
        lastNano = now;

        openT = Mth.clamp(openT + (closing ? -dt : dt) / 0.24f, 0f, 1f);
        tabT = Math.min(1f, tabT + dt / 0.26f);
        float e = ease(openT);
        accent = GlimmerEffects.accentColor();
        layout();

        // panel
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, argb(accent, 0.30f * e));
        g.fill(px, py, px + pw, py + ph, argb(0x0D0F16, (float) cfg.uiOpacity * e));
        g.fill(px, py, px + sw, py + ph, argb(0x05060A, 0.55f * e));
        g.fill(px, py, px + pw, py + 2, argb(accent, e));
        text(g, "GLIMMER", px + 9, py + 9, accent, e);
        text(g, "visual effects", px + 9, py + 20, 0x6F768A, e);
        text(g, "ESC to close", px + 9, py + ph - 14, 0x4F566A, e);

        // tabs
        float targetY = ty0 + tab * tabH;
        if (indicatorY < 0f) indicatorY = targetY;
        indicatorY = approach(indicatorY, targetY, 16f);
        int iy = Math.round(indicatorY);
        g.fill(px + 2, iy, px + sw, iy + tabH - 2, argb(accent, 0.16f * e));
        g.fill(px, iy, px + 2, iy + tabH - 2, argb(accent, e));
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * tabH;
            boolean hov = mx >= px && mx < px + sw && my >= ty && my < ty + tabH - 2;
            tabHover[i] = approach(tabHover[i], hov ? 1f : 0f, 14f);
            if (i != tab) g.fill(px + 2, ty, px + sw, ty + tabH - 2, argb(0xFFFFFF, 0.06f * tabHover[i] * e));
            float lit = i == tab ? 1f : tabHover[i];
            int tx = px + 10 + Math.round(lit * 2f);
            text(g, TABS[i], tx, ty + Math.max(1, (tabH - 11) / 2 + 2), mix(0x8A91A3, 0xFFFFFF, lit), e);
        }

        // content header
        text(g, TABS[tab].toUpperCase(), vx, py + 9, 0xFFFFFF, e);
        g.fill(vx, py + 21, vx + vw, py + 22, argb(0xFFFFFF, 0.08f * e));

        // content
        float ce = ease(tabT);
        float ca = e * ce;
        int xoff = Math.round((1f - ce) * 18f);
        float maxScroll = Math.max(0f, totalH - vh);
        scrollTarget = Mth.clamp(scrollTarget, 0f, maxScroll);
        scroll = approach(scroll, scrollTarget, 16f);
        if (dragging != null) dragging.drag(mx);

        g.enableScissor(vx - 2, vy, vx + vw + 2, vy + vh);
        float yy = vy - scroll + 2f;
        for (Row r : rows) {
            r.x = vx + xoff;
            r.y = Math.round(yy);
            r.w = vw - 7;
            if (r.y + r.h > vy && r.y < vy + vh) r.draw(g, ca);
            yy += r.h + 3f;
        }
        g.disableScissor();
        totalH = yy + scroll - vy;

        // scrollbar
        if (totalH > vh + 1f) {
            float frac = vh / totalH;
            int barH = Math.max(14, Math.round(vh * frac));
            int barY = vy + Math.round((vh - barH) * (scroll / Math.max(1f, totalH - vh)));
            g.fill(px + pw - 5, vy, px + pw - 3, vy + vh, argb(0xFFFFFF, 0.05f * e));
            g.fill(px + pw - 5, barY, px + pw - 3, barY + barH, argb(accent, 0.7f * e));
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * tabH;
            if (mx >= px && mx < px + sw && my >= ty && my < ty + tabH - 2) {
                if (i != tab) select(i);
                return true;
            }
        }
        if (mx >= vx - 2 && mx < vx + vw + 2 && my >= vy && my < vy + vh) {
            for (Row r : rows) {
                if (r.hot()) {
                    r.click();
                    if (r.draggable()) dragging = r;
                    return true;
                }
            }
        }
        return mx >= px && mx < px + pw && my >= py && my < py + ph;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = dragging != null;
        dragging = null;
        if (was) GlimmerConfig.save();
        return was;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return dragging != null;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollTarget -= (float) scrollY * 26f;
        return true;
    }

    // ------------------------------------------------------------ rows

    private abstract class Row {
        int x, y, w, h;
        float hover;

        Row(int h) {
            this.h = h;
        }

        boolean hot() {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= vy && my < vy + vh;
        }

        void updateHover() {
            boolean on = dragging == this || (dragging == null && hot());
            hover = approach(hover, on ? 1f : 0f, 16f);
        }

        void background(GuiGraphicsExtractor g, float a) {
            g.fill(x, y, x + w, y + h, argb(0xFFFFFF, 0.05f * hover * a));
        }

        abstract void draw(GuiGraphicsExtractor g, float a);

        boolean draggable() {
            return false;
        }

        void click() {}

        void drag(int mouseX) {}
    }

    private final class HeaderRow extends Row {
        private final String label;

        HeaderRow(String label) {
            super(18);
            this.label = label;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            text(g, label.toUpperCase(), x + 2, y + 6, accent, a * 0.9f);
            g.fill(x, y + 16, x + w, y + 17, argb(0xFFFFFF, 0.06f * a));
        }
    }

    private final class NoteRow extends Row {
        private final String label;

        NoteRow(String label) {
            super(14);
            this.label = label;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            text(g, label, x + 6, y + 3, 0x6F768A, a);
        }
    }

    private final class ToggleRow extends Row {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private final Runnable after;
        private float t = -1f;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set, Runnable after) {
            super(20);
            this.label = label;
            this.get = get;
            this.set = set;
            this.after = after;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            updateHover();
            background(g, a);
            boolean on = get.getAsBoolean();
            if (t < 0f) t = on ? 1f : 0f;
            t = approach(t, on ? 1f : 0f, 18f);
            text(g, fit(label, w - 52), x + 6, y + (h - 9) / 2, 0xE6E9F2, a);
            int trackW = 26, trackH = 12;
            int sx = x + w - trackW - 6, sy = y + (h - trackH) / 2;
            roundRect(g, sx, sy, trackW, trackH, argb(mix(0x3A3F4B, accent, t), a));
            int kx = sx + 1 + Math.round(t * (trackW - trackH));
            roundRect(g, kx, sy + 1, trackH - 2, trackH - 2, argb(0xFFFFFF, a));
        }

        @Override
        void click() {
            set.accept(!get.getAsBoolean());
            GlimmerConfig.save();
            if (after != null) after.run();
        }
    }

    private final class SliderRow extends Row {
        private final String label;
        private final double min, max;
        private final boolean integer;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private float disp = -1f;

        SliderRow(String label, double min, double max, boolean integer, DoubleSupplier get, DoubleConsumer set) {
            super(26);
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.get = get;
            this.set = set;
        }

        private String fmt(double v) {
            if (integer) return String.valueOf(Math.round(v));
            return String.format(Math.abs(max - min) <= 0.25 ? "%.3f" : "%.2f", v);
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            updateHover();
            background(g, a);
            double v = get.getAsDouble();
            float n = (float) Mth.clamp((v - min) / (max - min), 0.0, 1.0);
            if (disp < 0f) disp = n;
            disp = approach(disp, n, 22f);
            String s = fmt(v);
            text(g, fit(label, w - 24 - font.width(s)), x + 6, y + 3, 0xE6E9F2, a);
            text(g, s, x + w - 6 - font.width(s), y + 3, mix(0x8A91A3, accent, hover), a);
            int tx = x + 6, tw = w - 12, ty = y + 17;
            g.fill(tx, ty, tx + tw, ty + 4, argb(0x2A2E3A, a));
            int fillW = Math.round(disp * tw);
            g.fill(tx, ty, tx + fillW, ty + 4, argb(accent, a));
            int kh = 8 + Math.round(hover * 2f);
            int kx = tx + fillW;
            g.fill(kx - 2, ty + 2 - kh / 2, kx + 2, ty + 2 + kh / 2, argb(0xFFFFFF, a));
        }

        @Override
        boolean draggable() {
            return true;
        }

        @Override
        void click() {
            drag(mx);
        }

        @Override
        void drag(int mouseX) {
            double n = Mth.clamp((mouseX - (x + 6)) / (double) (w - 12), 0.0, 1.0);
            double v = min + n * (max - min);
            if (integer) v = Math.round(v);
            set.accept(v);
        }
    }

    private final class CycleRow extends Row {
        private final String label;
        private final List<String> options;
        private final Supplier<String> get;
        private final Consumer<String> set;
        private int ctrlX, ctrlW;

        CycleRow(String label, List<String> options, Supplier<String> get, Consumer<String> set) {
            super(20);
            this.label = label;
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            updateHover();
            background(g, a);
            ctrlW = 112;
            text(g, fit(label, w - ctrlW - 18), x + 6, y + (h - 9) / 2, 0xE6E9F2, a);
            ctrlX = x + w - ctrlW - 6;
            roundRect(g, ctrlX, y + 2, ctrlW, h - 4, argb(0x1A1D27, a));
            if (hot() && mx >= ctrlX && mx < ctrlX + ctrlW) {
                boolean left = mx < ctrlX + ctrlW / 2;
                int hx = left ? ctrlX : ctrlX + ctrlW / 2;
                g.fill(hx, y + 3, hx + ctrlW / 2, y + h - 3, argb(0xFFFFFF, 0.08f * a));
            }
            String v = get.get().replace('_', ' ');
            text(g, v, ctrlX + (ctrlW - font.width(v)) / 2, y + (h - 9) / 2, accent, a);
            text(g, "<", ctrlX + 5, y + (h - 9) / 2, 0x8A91A3, a);
            text(g, ">", ctrlX + ctrlW - 5 - font.width(">"), y + (h - 9) / 2, 0x8A91A3, a);
        }

        @Override
        void click() {
            int idx = options.indexOf(get.get());
            if (idx < 0) idx = 0;
            idx += (mx < ctrlX + ctrlW / 2) ? -1 : 1;
            idx = (idx + options.size()) % options.size();
            set.accept(options.get(idx));
            GlimmerConfig.save();
        }
    }

    private final class ButtonsRow extends Row {
        private final String[] labels;
        private final Consumer<String> action;
        private final float[] hov;

        ButtonsRow(String[] labels, Consumer<String> action) {
            super(20);
            this.labels = labels;
            this.action = action;
            this.hov = new float[labels.length];
        }

        private int cell(int mouseX) {
            int cw = (w - 12) / labels.length;
            return Mth.clamp((mouseX - (x + 6)) / Math.max(1, cw), 0, labels.length - 1);
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            int cw = (w - 12) / labels.length;
            for (int i = 0; i < labels.length; i++) {
                int cx = x + 6 + i * cw;
                boolean on = hot() && cell(mx) == i;
                hov[i] = approach(hov[i], on ? 1f : 0f, 16f);
                roundRect(g, cx + 1, y + 1, cw - 2, h - 2, argb(mix(0x1A1D27, accent, hov[i] * 0.45f), a));
                text(g, labels[i], cx + (cw - font.width(labels[i])) / 2, y + (h - 9) / 2,
                        mix(0xC9CEDB, 0xFFFFFF, hov[i]), a);
            }
        }

        @Override
        void click() {
            action.accept(labels[cell(mx)]);
        }
    }

    private final class StripRow extends Row {
        private final IntSupplier c1, c2;
        private final BooleanSupplier rainbow, gradient;

        StripRow(IntSupplier c1, IntSupplier c2, BooleanSupplier rainbow, BooleanSupplier gradient) {
            super(12);
            this.c1 = c1;
            this.c2 = c2;
            this.rainbow = rainbow;
            this.gradient = gradient;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            int tw = w - 12;
            for (int i = 0; i < tw; i++) {
                float f = i / (float) Math.max(1, tw - 1);
                int col;
                if (rainbow.getAsBoolean()) col = hsvToRgb(f, 0.85f, 1f);
                else if (gradient.getAsBoolean()) col = mix(c1.getAsInt(), c2.getAsInt(), f);
                else col = c1.getAsInt();
                g.fill(x + 6 + i, y + 1, x + 7 + i, y + h - 1, argb(col, a));
            }
        }
    }

    private final class HsvRow extends Row {
        private final String label;
        private final int mode;
        private final float[] hsv;
        private final IntConsumer set;
        private float disp = -1f;

        HsvRow(String label, int mode, float[] hsv, IntConsumer set) {
            super(24);
            this.label = label;
            this.mode = mode;
            this.hsv = hsv;
            this.set = set;
        }

        @Override
        void draw(GuiGraphicsExtractor g, float a) {
            updateHover();
            background(g, a);
            if (disp < 0f) disp = hsv[mode];
            disp = approach(disp, hsv[mode], 22f);
            text(g, label, x + 6, y + 2, 0xE6E9F2, a);
            String s = mode == 0 ? String.valueOf(Math.round(hsv[0] * 360f)) : Math.round(hsv[mode] * 100f) + "%";
            text(g, s, x + w - 6 - font.width(s), y + 2, mix(0x8A91A3, accent, hover), a);
            int tx = x + 6, tw = w - 12, ty = y + 13, th = 6;
            for (int i = 0; i < tw; i++) {
                float f = i / (float) Math.max(1, tw - 1);
                int col = mode == 0 ? hsvToRgb(f, 1f, 1f)
                        : mode == 1 ? hsvToRgb(hsv[0], f, hsv[2])
                        : hsvToRgb(hsv[0], hsv[1], f);
                g.fill(tx + i, ty, tx + i + 1, ty + th, argb(col, a));
            }
            int kx = tx + Math.round(disp * (tw - 1));
            g.fill(kx - 2, ty - 2, kx + 3, ty + th + 2, argb(0xFFFFFF, a));
            g.fill(kx - 1, ty - 1, kx + 2, ty + th + 1, argb(hsvToRgb(hsv[0], hsv[1], hsv[2]), a));
        }

        @Override
        boolean draggable() {
            return true;
        }

        @Override
        void click() {
            drag(mx);
        }

        @Override
        void drag(int mouseX) {
            float n = (float) Mth.clamp((mouseX - (x + 6)) / (double) Math.max(1, w - 13), 0.0, 1.0);
            hsv[mode] = n;
            set.accept(hsvToRgb(hsv[0], hsv[1], hsv[2]));
        }
    }
}
