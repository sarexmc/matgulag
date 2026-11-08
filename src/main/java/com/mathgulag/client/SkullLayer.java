package com.mathgulag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Dibuja la calavera sobre la cabeza del jugador que tiene la bomba (sustituye a la TNT). */
public class SkullLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Tamano de la calavera (1 = tamano original del modelo, ocupa mas de 2 bloques de largo). */
    public static final float SCALE = 0.42f;
    /** Altura extra sobre la cabeza, en bloques. */
    public static final float LIFT = 0.03f;
    /** Giro extra en grados si el hocico no mira hacia delante (0, 90, 180 o 270). */
    public static final float YAW_OFFSET = 0f;

    public SkullLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!ClientState.hasSkull(player)) return;
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        // la parte de arriba de la cabeza esta 0.5 bloques por encima del pivote (eje y hacia abajo)
        float bob = 0.012f * (float) Math.sin(ageInTicks * 0.25f);
        poseStack.translate(0f, -0.5f - LIFT - bob, 0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f + YAW_OFFSET));
        poseStack.scale(SCALE, -SCALE, -SCALE);
        SkullModel.render(poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
