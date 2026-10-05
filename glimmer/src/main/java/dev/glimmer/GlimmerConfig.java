package dev.glimmer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** All user-tweakable settings. Saved to config/glimmer-v8.json. */
public class GlimmerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("glimmer-v8.json");
    public static GlimmerConfig INSTANCE = new GlimmerConfig();

    /** Look + physics settings shared by every effect layer. */
    public static class Layer {
        public boolean on = true;
        public String particle = "soft_glow";
        public boolean customColor = false; // false = use the global color
        public boolean rainbow = false;
        public int color = 0x55FFFF;
        public boolean gradient = false;
        public int color2 = 0xFF55FF;
        public double size = 1.0;
        public double life = 1.0;
        public double spin = 0.0;    // degrees per tick
        public double rise = 0.0;    // starting upward drift
        public double fade = 1.5;    // fade-out curve
        // glow
        public double core = 0.0;    // bright white-hot core (0 = off)
        public double twinkle = 0.0; // brightness flicker
        // physics
        public double gravity = 0.0; // 0 = floats, 1 = falls like a normal particle
        public double bounce = 0.5;  // how much speed is kept when it hits the ground
        public boolean collide = false; // collide with blocks
        public double slide = 0.85;  // sliding friction on the ground (lower = stops quicker)
        public double drag = 0.96;   // air drag (1 = none)
        public double push = 0.0;    // how strongly you kick it when you walk through it
        // brightness / bloom (multiplied with the global values)
        public double brightness = 1.0;
        public double bloom = 1.0;
        public double opacity = 1.0;   // overall transparency of this layer's particles
        // trail lines: thin lines that follow moving particles (like stars falling to the ground)
        public double tail = 0.0;          // length, 0 = off
        public double tailWidth = 0.012;   // line thickness in blocks
        public double tailTaper = 0.8;     // how much it thins toward the end (0 = same width, 1 = to a point)
        public double tailFade = 1.2;      // how quickly it fades toward the end
        public double tailGlow = 0.5;      // soft glow around the line
        public double tailGlowWidth = 3.0; // how wide that glow is, in line widths
        public double tailCore = 0.5;      // thin white-hot line in the middle
        public double tailOpacity = 1.0;
        public int tailDensity = 2;        // smoothness (points per tick)
        public double tailMinSpeed = 0.02; // the particle must move this fast to draw a line
        public double fps = 0.0;     // animation FPS for this layer (0 = use the global value)
        public boolean flat = false; // lie flat on the ground instead of facing the camera

        public Layer() {}

        Layer(boolean on, String particle, double size, double life, double spin, double rise) {
            this.on = on;
            this.particle = particle;
            this.size = size;
            this.life = life;
            this.spin = spin;
            this.rise = rise;
        }
    }

    // master
    public boolean enabled = true;
    public boolean glow = true;
    public boolean showInFirstPerson = false;
    public boolean followScaleMe = true;

    // global color
    public boolean rainbow = false;
    public int color = 0x45E3FF;
    public boolean gradient = true;
    public int color2 = 0xB44CFF;
    public double rainbowSpeed = 1.0;

    // glow (global)
    public double brightness = 2.2;  // how bright particles are (stacks and whitens them)
    public double bloom = 0.35;      // strength of the soft particle bloom
    public double animFps = 120.0;   // how many animation frames per second particles use (20 = vanilla steps)
    // real screen bloom (post-processing): everything bright bleeds light
    public boolean screenBloom = true;
    public int bloomLevel = 4;       // 1-6 strength
    public int bloomRadius = 1;      // 1-3 how far the glow spreads
    public int bloomThreshold = 3;   // 1-3 how bright something must be to glow (1 = most things)
    public double bloomSize = 2.6;   // size of the bloom

    /** Cel-shaded held item: flat lighting plus a colored, glowing outline. */
    public static class Cel {
        public boolean on = true;
        public boolean firstPerson = true;
        public boolean thirdPerson = false;
        public boolean flatLight = true;   // ignore world lighting on the item (flat, even look)
        public double thickness = 0.6;     // outline thickness in item pixels
        public double glow = 0.8;          // soft glow outside the outline
        public double glowSize = 1.0;      // how far each glow step reaches, in item pixels
        public boolean useGlobalColor = true;
        public boolean rainbow = false;
        public int color = 0x55D7FF;
        public double brightness = 2.5;    // whitens the outline color
        public boolean flip = false;       // flip which side of the item the outline is drawn on
    }
    public Cel cel = new Cel();

    // menu
    public double uiSpeed = 1.0;
    public double uiOpacity = 0.88;

    // layers
    public Layer aura = new Layer(false, "soft_glow", 1.0, 1.0, 0.0, 0.0);
    public Layer orbit = new Layer(true, "soft_glow", 1.427947598253275, 1.0, 4.0, 0.0);
    public Layer trail = new Layer(false, "sparkle", 1.0, 1.0, 0.0, 0.0);
    public Layer swing = new Layer(true, "soft_glow", 1.4, 1.0, 0.0, 0.0);
    public Layer hit = new Layer(true, "sparkle", 1.2, 1.0, 8.0, 0.0);
    public Layer foot = new Layer(true, "ring_thin", 1.0, 0.9, 0.0, 0.0);
    public Layer weapon = new Layer(false, "soft_glow", 0.8, 1.0, 0.0, 0.0);
    public Layer breakFx = new Layer(true, "dot", 0.9, 1.0, 6.0, 0.0);

    {
        // falling stars: hit sparkles fall with gravity, bounce, slide and get kicked by you
        hit.gravity = 0.9;
        hit.bounce = 0.5;
        hit.collide = true;
        hit.slide = 0.8;
        hit.drag = 0.96;
        hit.push = 0.6;
        hit.core = 0.6;
        hit.twinkle = 0.25;
        hit.fade = 1.2;
        hit.tail = 0.7;
        orbit.core = 0.6;
        swing.core = 0.5;
        // footstep ring: thin, faint and short-lived so it is not obvious
        foot.flat = true;
        foot.bloom = 0.0;
        foot.fade = 2.0;
        foot.opacity = 0.3;
        foot.brightness = 0.8;
        // block breaking: glowing debris that falls, bounces and trails
        breakFx.gravity = 1.0;
        breakFx.bounce = 0.4;
        breakFx.collide = true;
        breakFx.slide = 0.7;
        breakFx.core = 0.5;
        breakFx.tail = 0.3;
        breakFx.fade = 1.2;
    }

    // aura
    public double auraAmount = 2;
    public double auraRadius = 0.8;

    // orbit
    public int orbitCount = 4;
    public double orbitRadius = 1.1371179039301311;
    public double orbitSpeed = 9.956331877729257;
    public double orbitHeight = 0.3056768558951965;

    // movement trail
    public double trailAmount = 2;

    // swing trail
    public double swingLength = 2.2;
    public double swingArc = 140;
    public double swingTilt = 35;
    public int swingSamples = 8;
    public boolean swingFirstPerson = false;
    public boolean swingEmptyHand = true;

    // hit burst
    public int hitCount = 14;
    public double hitSpread = 0.15;
    public double hitLift = 0.25;   // upward pop so stars arc and fall
    public double hitRange = 4.0;   // see hit effects on targets up to this far away (max 5)

    // block breaking
    public int breakCount = 14;
    public double breakSpread = 0.1;
    public double breakLift = 0.12;
    public boolean breakHideVanilla = false; // hide the normal block-break particles

    // footstep ring (one thin ring per step)
    public int footMinGap = 6;      // minimum ticks between rings so they don't pile up
    public double footInterval = 1.0;
    public double footRadius = 0.6;
    public int footPoints = 48; // only used if the step particle is not a ring
    public double footSide = 0.20087336244541484;
    public boolean landRing = true;
    public double landMinFall = 0.8;
    public double landScale = 1.5;
    public boolean jumpRing = false;
    public boolean teleportRing = true;
    public boolean teleportDeparture = true;
    public double teleportMin = 4.5;

    // weapon glow
    public double weaponAmount = 1.5;
    public double weaponForward = 0.55;
    public double weaponSide = 0.4;
    public double weaponHeight = -0.45;
    public double weaponLength = 0.6;

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                GlimmerConfig c = GSON.fromJson(Files.readString(FILE), GlimmerConfig.class);
                if (c != null) INSTANCE = c;
            }
            save();
        } catch (Exception e) {
            System.err.println("[Glimmer] Could not read config, using defaults: " + e);
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            System.err.println("[Glimmer] Could not save config: " + e);
        }
    }
}
