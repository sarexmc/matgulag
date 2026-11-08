package com.mathgulag.client;

import com.mathgulag.net.StatePacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public class GulagClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatePacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientState.apply(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientState.clientTick());

        // calavera sobre la cabeza del portador de la bomba
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new SkullLayer(playerRenderer));
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            GulagRenderer.renderOverlay(drawContext, 0f,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
    }
}
