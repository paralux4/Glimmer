package dev.glimmer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

public class GlimmerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GlimmerConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(GlimmerEffects::tick);

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
}
