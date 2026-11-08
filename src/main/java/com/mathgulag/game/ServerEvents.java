package com.mathgulag.game;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class ServerEvents {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GulagCommand.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(GulagManager::tick);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide) return InteractionResult.PASS;
            return GulagManager.onAttack(player, entity) ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer sp) GulagManager.onDeath(sp);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GulagManager.onLogout(handler.player, server));
    }
}
