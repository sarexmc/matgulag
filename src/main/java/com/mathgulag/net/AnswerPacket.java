package com.mathgulag.net;

import com.mathgulag.GulagMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnswerPacket(int digit) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnswerPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GulagMod.MODID, "answer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeVarInt(msg.digit),
            buf -> new AnswerPacket(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<AnswerPacket> type() {
        return TYPE;
    }
}
