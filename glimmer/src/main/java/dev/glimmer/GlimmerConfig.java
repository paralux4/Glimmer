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

    // layers
    public boolean aura = true;
    public boolean orbit = true;
    public boolean trail = true;
    public boolean hit = true;

    // particle per layer (see /glimmer particles)
    public String auraParticle = "glow";
    public String orbitParticle = "dust";
    public String trailParticle = "end_rod";
    public String hitParticle = "enchanted_hit";

    // color (only affects the "dust" particle)
    public boolean rainbow = true;
    public int color = 0x55FFFF;
    public double rainbowSpeed = 1.0;
    public double dustSize = 1.0;

    // aura
    public double auraDensity = 2;   // particles per tick
    public double auraRadius = 0.8;

    // orbit
    public int orbitCount = 3;
    public double orbitRadius = 0.9;
    public double orbitSpeed = 8;    // degrees per tick
    public double orbitHeight = 1.0; // height above feet

    // trail
    public double trailDensity = 2;

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
