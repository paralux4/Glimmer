package dev.glimmer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.List;

/** /glimmer ... — every setting is changeable live and saved to config/glimmer.json. */
public final class GlimmerCommands {
    private GlimmerCommands() {}
        private static final class ClientCommandManager {
        static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
            return LiteralArgumentBuilder.literal(name);
        }

        static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
            return RequiredArgumentBuilder.argument(name, type);
        }
    }

    private static final List<String> LAYERS = List.of("aura", "orbit", "trail", "hit");
    private static final List<String> NUMBERS = List.of(
            "auraDensity", "auraRadius", "orbitCount", "orbitRadius", "orbitSpeed", "orbitHeight",
            "trailDensity", "hitCount", "hitSpread", "dustSize", "rainbowSpeed");

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        var root = ClientCommandManager.literal("glimmer");

        root.then(ClientCommandManager.literal("toggle").executes(ctx -> {
            var c = GlimmerConfig.INSTANCE;
            c.enabled = !c.enabled;
            GlimmerConfig.save();
            say(ctx.getSource(), "Glimmer " + (c.enabled ? "on" : "off"));
            return 1;
        }));

        root.then(ClientCommandManager.literal("layer")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                        .then(ClientCommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                            String n = StringArgumentType.getString(ctx, "name");
                            boolean on = BoolArgumentType.getBool(ctx, "on");
                            var c = GlimmerConfig.INSTANCE;
                            switch (n) {
                                case "aura" -> c.aura = on;
                                case "orbit" -> c.orbit = on;
                                case "trail" -> c.trail = on;
                                case "hit" -> c.hit = on;
                                default -> { say(ctx.getSource(), "Layers: " + LAYERS); return 0; }
                            }
                            GlimmerConfig.save();
                            say(ctx.getSource(), n + " " + (on ? "on" : "off"));
                            return 1;
                        }))));

        root.then(ClientCommandManager.literal("particle")
                .then(ClientCommandManager.argument("layer", StringArgumentType.word())
                        .then(ClientCommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
                            String layer = StringArgumentType.getString(ctx, "layer");
                            String type = StringArgumentType.getString(ctx, "type");
                            if (!type.equals("dust") && !GlimmerEffects.SIMPLE.containsKey(type)) {
                                say(ctx.getSource(), "Unknown particle. Use /glimmer particles");
                                return 0;
                            }
                            var c = GlimmerConfig.INSTANCE;
                            switch (layer) {
                                case "aura" -> c.auraParticle = type;
                                case "orbit" -> c.orbitParticle = type;
                                case "trail" -> c.trailParticle = type;
                                case "hit" -> c.hitParticle = type;
                                default -> { say(ctx.getSource(), "Layers: " + LAYERS); return 0; }
                            }
                            GlimmerConfig.save();
                            say(ctx.getSource(), layer + " particle = " + type);
                            return 1;
                        }))));

        root.then(ClientCommandManager.literal("particles").executes(ctx -> {
            say(ctx.getSource(), "dust (uses your color), " + String.join(", ", GlimmerEffects.SIMPLE.keySet()));
            return 1;
        }));

        root.then(ClientCommandManager.literal("color")
                .then(ClientCommandManager.argument("hex", StringArgumentType.word()).executes(ctx -> {
                    try {
                        String h = StringArgumentType.getString(ctx, "hex").replace("#", "");
                        var c = GlimmerConfig.INSTANCE;
                        c.color = Integer.parseInt(h, 16) & 0xFFFFFF;
                        c.rainbow = false;
                        GlimmerConfig.save();
                        say(ctx.getSource(), "Color set (rainbow off). Applies to the 'dust' particle.");
                        return 1;
                    } catch (NumberFormatException e) {
                        say(ctx.getSource(), "Use a hex color like ff66cc");
                        return 0;
                    }
                })));

        root.then(ClientCommandManager.literal("rainbow")
                .then(ClientCommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    GlimmerConfig.INSTANCE.rainbow = BoolArgumentType.getBool(ctx, "on");
                    GlimmerConfig.save();
                    say(ctx.getSource(), "Rainbow " + (GlimmerConfig.INSTANCE.rainbow ? "on" : "off"));
                    return 1;
                })));

        root.then(ClientCommandManager.literal("firstperson")
                .then(ClientCommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                    GlimmerConfig.INSTANCE.showInFirstPerson = BoolArgumentType.getBool(ctx, "on");
                    GlimmerConfig.save();
                    say(ctx.getSource(), "Show in first person: " + GlimmerConfig.INSTANCE.showInFirstPerson);
                    return 1;
                })));

        root.then(ClientCommandManager.literal("set")
                .then(ClientCommandManager.argument("option", StringArgumentType.word())
                        .then(ClientCommandManager.argument("value", DoubleArgumentType.doubleArg(0, 100)).executes(ctx -> {
                            String o = StringArgumentType.getString(ctx, "option");
                            double v = DoubleArgumentType.getDouble(ctx, "value");
                            var c = GlimmerConfig.INSTANCE;
                            switch (o) {
                                case "auraDensity" -> c.auraDensity = v;
                                case "auraRadius" -> c.auraRadius = v;
                                case "orbitCount" -> c.orbitCount = (int) v;
                                case "orbitRadius" -> c.orbitRadius = v;
                                case "orbitSpeed" -> c.orbitSpeed = v;
                                case "orbitHeight" -> c.orbitHeight = v;
                                case "trailDensity" -> c.trailDensity = v;
                                case "hitCount" -> c.hitCount = (int) v;
                                case "hitSpread" -> c.hitSpread = v;
                                case "dustSize" -> c.dustSize = v;
                                case "rainbowSpeed" -> c.rainbowSpeed = v;
                                default -> { say(ctx.getSource(), "Options: " + NUMBERS); return 0; }
                            }
                            GlimmerConfig.save();
                            say(ctx.getSource(), o + " = " + v);
                            return 1;
                        }))));

        root.then(ClientCommandManager.literal("reload").executes(ctx -> {
            GlimmerConfig.load();
            say(ctx.getSource(), "Config reloaded");
            return 1;
        }));

        root.executes(ctx -> {
            say(ctx.getSource(), "/glimmer toggle | layer <aura|orbit|trail|hit> <true|false> | particle <layer> <type> | particles | color <hex> | rainbow <bool> | firstperson <bool> | set <option> <number> | reload");
            return 1;
        });

        d.register(root);
    }

    private static void say(FabricClientCommandSource s, String msg) {
        s.sendFeedback(Component.literal("[Glimmer] " + msg));
    }
}
