package dev.glimmer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** All user-tweakable settings. Saved to config/glimmer.json. */
public class GlimmerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("glimmer.json");
    public static GlimmerConfig INSTANCE = new GlimmerConfig();

    public boolean enabled = true;
    public boolean showInFirstPerson = false;
    public boolean glow = true; // fullbright particles

    // layers
    public boolean aura = true;
    public boolean orbit = true;
    public boolean trail = true;
    public boolean swing = true;
    public boolean hit = true;

    // particle per layer
    public String auraParticle = "glow";
    public String orbitParticle = "dust";
    public String trailParticle = "end_rod";
    public String swingParticle = "dust";
    public String hitParticle = "enchanted_hit";

    // color (affects the "dust" particle)
    public boolean rainbow = true;
    public int color = 0x55FFFF;
    public double rainbowSpeed = 1.0;
    public double dustSize = 1.0;

    // aura
    public double auraDensity = 2;
    public double auraRadius = 0.8;

    // orbit
    public int orbitCount = 3;
    public double orbitRadius = 0.9;
    public double orbitSpeed = 8;
    public double orbitHeight = 1.0;

    // movement trail
    public double trailDensity = 2;

    // swing trail
    public double swingLength = 2.2;
    public double swingArc = 140;
    public double swingTilt = 35;
    public int swingSamples = 8;
    public double swingSize = 1.3;
    public double swingSpread = 0.5;
    public boolean swingFirstPerson = true;

    // hit burst
    public int hitCount = 14;
    public double hitSpread = 0.15;

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
