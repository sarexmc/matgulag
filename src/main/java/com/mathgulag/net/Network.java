package com.mathgulag.net;

import com.mathgulag.game.GulagManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class Network {
    private Network() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(StatePacket.TYPE, StatePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AnswerPacket.TYPE, AnswerPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AnswerPacket.TYPE, (payload, context) ->
                context.server().execute(() -> GulagManager.onAnswer(context.player(), payload.digit())));
    }
}
