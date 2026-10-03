package dev.glimmer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** In-game settings menu. Opened with /glimmer. Saves automatically. */
public class GlimmerScreen extends Screen {
    private static int currentTab = 0;
    private static final String[] TABS = {"General", "Particles", "Color", "Aura", "Swing", "Hit"};
    private static final List<String> PARTICLES = new ArrayList<>();
    static {
        PARTICLES.add("soft_glow");
        PARTICLES.add("sparkle");
        PARTICLES.add("dust");
        PARTICLES.addAll(GlimmerEffects.SIMPLE.keySet());
    }

    private Button preview;
    private Button rainbowButton;

    public GlimmerScreen() {
        super(Component.literal("Glimmer"));
    }

    @Override
    protected void init() {
        build();
    }

    // keep the game running so you can see your changes live behind the menu
    public boolean isPauseScreen() {
        return false;
    }

    // saved whenever the menu closes
    public void removed() {
        GlimmerConfig.save();
    }

    private void build() {
        clearWidgets();
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        int cx = this.width / 2;

        Button title = Button.builder(Component.literal("Glimmer - Visual Effects"), b -> {})
                .bounds(cx - 100, 6, 200, 20).build();
        title.active = false;
        addRenderableWidget(title);

        int tabW = Math.min(76, (this.width - 20) / TABS.length - 2);
        int total = TABS.length * (tabW + 2);
        int startX = cx - total / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            String label = (i == currentTab) ? "[" + TABS[i] + "]" : TABS[i];
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                GlimmerConfig.save();
                currentTab = idx;
                build();
            }).bounds(startX + i * (tabW + 2), 30, tabW, 20).build());
        }

        switch (currentTab) {
            case 0 -> generalTab(c);
            case 1 -> particlesTab(c);
            case 2 -> colorTab(c);
            case 3 -> auraTab(c);
            case 4 -> swingTab(c);
            case 5 -> hitTab(c);
            default -> {}
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            GlimmerConfig.save();
            this.onClose();
        }).bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    // ---------------------------------------------------------------- tabs

    private void generalTab(GlimmerConfig c) {
        toggle(0, "Effects", () -> c.enabled, v -> c.enabled = v);
        toggle(1, "Show in 1st person", () -> c.showInFirstPerson, v -> c.showInFirstPerson = v);
        toggle(2, "Glow (fullbright)", () -> c.glow, v -> c.glow = v);
        toggle(3, "Aura", () -> c.aura, v -> c.aura = v);
        toggle(4, "Orbit", () -> c.orbit, v -> c.orbit = v);
        toggle(5, "Move trail", () -> c.trail, v -> c.trail = v);
        toggle(6, "Swing trail", () -> c.swing, v -> c.swing = v);
        toggle(7, "Hit burst", () -> c.hit, v -> c.hit = v);

        int cx = this.width / 2;
        int fullW = colW() * 2 + 8;
        int y = rowY(4) + 4;
        int pw = fullW / 4 - 2;
        int x0 = cx - fullW / 2;
        preset(x0, y, pw, "Ice");
        preset(x0 + (pw + 2), y, pw, "Fire");
        preset(x0 + (pw + 2) * 2, y, pw, "Void");
        preset(x0 + (pw + 2) * 3, y, pw, "Rainbow");
    }

    private void particlesTab(GlimmerConfig c) {
        cycle(0, "Aura", () -> c.auraParticle, v -> c.auraParticle = v);
        cycle(1, "Orbit", () -> c.orbitParticle, v -> c.orbitParticle = v);
        cycle(2, "Move trail", () -> c.trailParticle, v -> c.trailParticle = v);
        cycle(3, "Swing trail", () -> c.swingParticle, v -> c.swingParticle = v);
        cycle(4, "Hit burst", () -> c.hitParticle, v -> c.hitParticle = v);
        toggle(5, "Glow (fullbright)", () -> c.glow, v -> c.glow = v);
        Button note = Button.builder(Component.literal("soft_glow, sparkle and dust use your color"), b -> {})
                .bounds(this.width / 2 - colW() - 4, rowY(4) + 4, colW() * 2 + 8, 20).build();
        note.active = false;
        addRenderableWidget(note);
    }

    private void colorTab(GlimmerConfig c) {
        rainbowButton = toggle(0, "Rainbow", () -> c.rainbow, v -> { c.rainbow = v; updatePreview(); });
        slider(1, "Rainbow speed", 0.1, 5, false, () -> c.rainbowSpeed, v -> c.rainbowSpeed = v);
        slider(2, "Red", 0, 255, true, () -> (c.color >> 16) & 0xFF,
                v -> { c.color = (c.color & 0x00FFFF) | ((int) v << 16); colorEdited(c); });
        slider(3, "Green", 0, 255, true, () -> (c.color >> 8) & 0xFF,
                v -> { c.color = (c.color & 0xFF00FF) | ((int) v << 8); colorEdited(c); });
        slider(4, "Blue", 0, 255, true, () -> c.color & 0xFF,
                v -> { c.color = (c.color & 0xFFFF00) | (int) v; colorEdited(c); });
        slider(5, "Particle size", 0.3, 4, false, () -> c.dustSize, v -> c.dustSize = v);
        slider(6, "Swing color spread", 0, 2, false, () -> c.swingSpread, v -> c.swingSpread = v);
        slider(7, "Lifetime", 0.3, 3, false, () -> c.particleLife, v -> c.particleLife = v);

        preview = Button.builder(Component.empty(), b -> {})
                .bounds(this.width / 2 - colW() - 4, rowY(4) + 4, colW() * 2 + 8, 20).build();
        preview.active = false;
        addRenderableWidget(preview);
        updatePreview();
    }

    private void auraTab(GlimmerConfig c) {
        slider(0, "Aura amount", 0, 10, false, () -> c.auraDensity, v -> c.auraDensity = v);
        slider(1, "Aura radius", 0.2, 3, false, () -> c.auraRadius, v -> c.auraRadius = v);
        slider(2, "Orbit count", 1, 12, true, () -> c.orbitCount, v -> c.orbitCount = (int) v);
        slider(3, "Orbit radius", 0.3, 3, false, () -> c.orbitRadius, v -> c.orbitRadius = v);
        slider(4, "Orbit speed", 0, 40, false, () -> c.orbitSpeed, v -> c.orbitSpeed = v);
        slider(5, "Orbit height", 0, 2.5, false, () -> c.orbitHeight, v -> c.orbitHeight = v);
        slider(6, "Move trail amount", 0, 10, false, () -> c.trailDensity, v -> c.trailDensity = v);
    }

    private void swingTab(GlimmerConfig c) {
        toggle(0, "Swing trail", () -> c.swing, v -> c.swing = v);
        toggle(1, "In 1st person", () -> c.swingFirstPerson, v -> c.swingFirstPerson = v);
        slider(2, "Length", 0.8, 4, false, () -> c.swingLength, v -> c.swingLength = v);
        slider(3, "Arc", 40, 260, false, () -> c.swingArc, v -> c.swingArc = v);
        slider(4, "Tilt", -90, 90, false, () -> c.swingTilt, v -> c.swingTilt = v);
        slider(5, "Smoothness", 1, 20, true, () -> c.swingSamples, v -> c.swingSamples = (int) v);
        slider(6, "Thickness", 0.3, 4, false, () -> c.swingSize, v -> c.swingSize = v);
        slider(7, "Color spread", 0, 2, false, () -> c.swingSpread, v -> c.swingSpread = v);
        slider(8, "Trail fade (ticks)", 4, 40, true, () -> c.swingLife, v -> c.swingLife = (int) v);
    }

    private void hitTab(GlimmerConfig c) {
        toggle(0, "Hit burst", () -> c.hit, v -> c.hit = v);
        slider(1, "Amount", 0, 60, true, () -> c.hitCount, v -> c.hitCount = (int) v);
        slider(2, "Spread", 0.02, 0.6, false, () -> c.hitSpread, v -> c.hitSpread = v);
    }

    // ------------------------------------------------------------- helpers

    private int colW() {
        return Math.min(150, this.width / 2 - 10);
    }

    private int rowY(int row) {
        return 58 + row * 24;
    }

    private int colX(int index) {
        return (index % 2 == 0) ? this.width / 2 - colW() - 4 : this.width / 2 + 4;
    }

    private Button toggle(int index, String label, BooleanSupplier get, Consumer<Boolean> set) {
        final Button[] ref = new Button[1];
        ref[0] = Button.builder(toggleText(label, get.getAsBoolean()), b -> {
            boolean now = !get.getAsBoolean();
            set.accept(now);
            ref[0].setMessage(toggleText(label, now));
            GlimmerConfig.save();
        }).bounds(colX(index), rowY(index / 2), colW(), 20).build();
        return addRenderableWidget(ref[0]);
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private Button cycle(int index, String label, Supplier<String> get, Consumer<String> set) {
        final Button[] ref = new Button[1];
        ref[0] = Button.builder(Component.literal(label + ": " + get.get()), b -> {
            int i = PARTICLES.indexOf(get.get());
            String next = PARTICLES.get((i + 1) % PARTICLES.size());
            set.accept(next);
            ref[0].setMessage(Component.literal(label + ": " + next));
            GlimmerConfig.save();
        }).bounds(colX(index), rowY(index / 2), colW(), 20).build();
        return addRenderableWidget(ref[0]);
    }

    private Slider slider(int index, String label, double min, double max, boolean integer,
                          DoubleSupplier get, DoubleConsumer set) {
        return addRenderableWidget(new Slider(colX(index), rowY(index / 2), colW(), 20,
                label, min, max, integer, get.getAsDouble(), set));
    }

    private void preset(int x, int y, int w, String name) {
        addRenderableWidget(Button.builder(Component.literal(name), b -> {
            applyPreset(name);
            GlimmerConfig.save();
            build();
        }).bounds(x, y, w, 20).build());
    }

    private void applyPreset(String name) {
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        c.glow = true;
        switch (name) {
            case "Ice" -> {
                c.rainbow = false; c.color = 0x55D7FF;
                c.auraParticle = "soft_glow"; c.orbitParticle = "sparkle"; c.trailParticle = "snowflake";
                c.swingParticle = "soft_glow"; c.hitParticle = "sparkle";
            }
            case "Fire" -> {
                c.rainbow = false; c.color = 0xFF6A00;
                c.auraParticle = "soft_glow"; c.orbitParticle = "sparkle"; c.trailParticle = "flame";
                c.swingParticle = "soft_glow"; c.hitParticle = "sparkle";
            }
            case "Void" -> {
                c.rainbow = false; c.color = 0xB44CFF;
                c.auraParticle = "soft_glow"; c.orbitParticle = "sparkle"; c.trailParticle = "portal";
                c.swingParticle = "soft_glow"; c.hitParticle = "electric_spark";
            }
            default -> {
                c.rainbow = true;
                c.auraParticle = "soft_glow"; c.orbitParticle = "sparkle"; c.trailParticle = "soft_glow";
                c.swingParticle = "soft_glow"; c.hitParticle = "sparkle";
            }
        }
    }

    private void colorEdited(GlimmerConfig c) {
        c.rainbow = false;
        if (rainbowButton != null) rainbowButton.setMessage(toggleText("Rainbow", false));
        updatePreview();
    }

    private void updatePreview() {
        if (preview == null) return;
        GlimmerConfig c = GlimmerConfig.INSTANCE;
        int rgb = c.color & 0xFFFFFF;
        String text = c.rainbow ? "Rainbow mode is on (turn off to use this color)" : "Color preview  ########";
        preview.setMessage(Component.literal(text).withStyle(s -> s.withColor(rgb)));
    }

    /** A slider that reads/writes one config value. */
    private static class Slider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final boolean integer;
        private final DoubleConsumer set;

        Slider(int x, int y, int w, int h, String label, double min, double max, boolean integer,
               double initial, DoubleConsumer set) {
            super(x, y, w, h, Component.empty(), (clamp(initial, min, max) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.set = set;
            updateMessage();
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private double current() {
            double v = min + this.value * (max - min);
            return integer ? Math.round(v) : v;
        }

        @Override
        protected void updateMessage() {
            double v = current();
            String s = integer ? String.valueOf((long) v) : String.format("%.2f", v);
            setMessage(Component.literal(label + ": " + s));
        }

        @Override
        protected void applyValue() {
            set.accept(current());
        }
    }
}
