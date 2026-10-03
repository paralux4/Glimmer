package dev.glimmer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** All user-tweakable settings. Saved to config/glimmer-v3.json. */
public class GlimmerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("glimmer-v3.json");
    public static GlimmerConfig INSTANCE = new GlimmerConfig();

    /** Look settings shared by every effect layer. */
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
        public double spin = 0.0;   // degrees per tick
        public double rise = 0.0;   // vertical drift per tick
        public double halo = 0.35;  // strength of the soft glow behind each particle
        public double haloSize = 2.4;
        public double fade = 1.5;   // fade-out curve

        public Layer() {}

        public Layer(String particle, double size, double spin, double halo) {
            this.particle = particle;
            this.size = size;
            this.spin = spin;
            this.halo = halo;
        }
    }

    // master
    public boolean enabled = true;
    public boolean glow = true;
    public boolean showInFirstPerson = false;
    public boolean followScaleMe = true;

    // global color
    public boolean rainbow = true;
    public int color = 0x55FFFF;
    public boolean gradient = true;
    public int color2 = 0xB44CFF;
    public double rainbowSpeed = 1.0;

    // menu
    public double uiSpeed = 1.0;
    public double uiOpacity = 0.88;

    // layers
    public Layer aura = new Layer("soft_glow", 1.0, 0, 0.30);
    public Layer orbit = new Layer("sparkle", 1.1, 4, 0.45);
    public Layer trail = new Layer("soft_glow", 1.0, 0, 0.30);
    public Layer swing = new Layer("soft_glow", 1.4, 0, 0.45);
    public Layer hit = new Layer("sparkle", 1.2, 8, 0.45);
    public Layer foot = new Layer("soft_glow", 0.8, 0, 0.35);
    public Layer weapon = new Layer("soft_glow", 0.8, 0, 0.35);

    // aura
    public double auraAmount = 2;
    public double auraRadius = 0.8;

    // orbit
    public int orbitCount = 3;
    public double orbitRadius = 0.9;
    public double orbitSpeed = 8;
    public double orbitHeight = 1.0;

    // movement trail
    public double trailAmount = 2;

    // swing trail
    public double swingLength = 2.2;
    public double swingArc = 140;
    public double swingTilt = 35;
    public int swingSamples = 8;
    public boolean swingFirstPerson = true;
    public boolean swingEmptyHand = true;

    // hit burst
    public int hitCount = 14;
    public double hitSpread = 0.15;

    // footsteps
    public double footInterval = 1.1;
    public double footRadius = 0.5;
    public int footPoints = 10;
    public double footSide = 0.15;

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
