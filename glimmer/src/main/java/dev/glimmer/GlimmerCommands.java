package dev.glimmer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** /glimmer opens the settings menu. Extra: toggle, reset, reload, dump. */
public final class GlimmerCommands {
    private GlimmerCommands() {}

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        var root = literal("glimmer");

        root.then(literal("toggle").executes(ctx -> {
            var c = GlimmerConfig.INSTANCE;
            c.enabled = !c.enabled;
            GlimmerConfig.save();
            say(ctx.getSource(), "Glimmer " + (c.enabled ? "on" : "off"));
            return 1;
        }));

        root.then(literal("reset").executes(ctx -> {
            GlimmerConfig.INSTANCE = new GlimmerConfig();
            GlimmerConfig.save();
            say(ctx.getSource(), "Settings reset to defaults");
            return 1;
        }));

        root.then(literal("reload").executes(ctx -> {
            GlimmerConfig.load();
            say(ctx.getSource(), "Config reloaded");
            return 1;
        }));

        root.then(literal("dump").executes(ctx -> {
            String where = GlimmerDump.run();
            say(ctx.getSource(), "Wrote " + where);
            return 1;
        }));

        root.executes(ctx -> {
            GlimmerClient.openMenu = true;
            return 1;
        });

        d.register(root);
    }

    private static void say(FabricClientCommandSource s, String msg) {
        s.sendFeedback(Component.literal("[Glimmer] " + msg));
    }
}
