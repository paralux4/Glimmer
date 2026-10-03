package dev.glimmer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;

import java.lang.reflect.Method;

public class GlimmerClient implements ClientModInitializer {
    /** Set by /glimmer; the menu opens on the next tick (after the chat screen has closed). */
    public static volatile boolean openMenu = false;

    @Override
    public void onInitializeClient() {
        GlimmerConfig.load();
        GlimmerParticles.register();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openMenu) {
                openMenu = false;
                openScreen(mc, new GlimmerScreen());
            }
            GlimmerEffects.tick(mc);
        });

        // Purely cosmetic: always PASS so the attack itself is untouched.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                GlimmerEffects.hitBurst(entity);
            }
            return InteractionResult.PASS;
        });

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> GlimmerCommands.register(dispatcher));
    }

    /** Opens a screen without hard-coding the method name, which changed in 26.x. */
    private static void openScreen(Minecraft mc, Screen screen) {
        try {
            for (String name : new String[]{"setScreen", "setScreenAndShow"}) {
                for (Method m : Minecraft.class.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(screen.getClass())) {
                        m.invoke(mc, screen);
                        return;
                    }
                }
            }
            for (Method m : Minecraft.class.getMethods()) {
                if (m.getParameterCount() == 1 && m.getReturnType() == void.class
                        && m.getParameterTypes()[0] == Screen.class) {
                    m.invoke(mc, screen);
                    return;
                }
            }
            System.err.println("[Glimmer] Could not find a method to open the menu.");
        } catch (Exception e) {
            System.err.println("[Glimmer] Could not open the menu: " + e);
        }
    }
}
