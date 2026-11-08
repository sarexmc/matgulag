package com.mathgulag.net;

import com.mathgulag.GulagMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** phase: 0 nada, 1 pregunta (portador), 2 pasar bomba (portador), 3 espectador */
public record StatePacket(int phase, String question, int ticksLeft, int ticksTotal, String holder)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GulagMod.MODID, "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.phase);
                buf.writeUtf(msg.question);
                buf.writeVarInt(msg.ticksLeft);
                buf.writeVarInt(msg.ticksTotal);
                buf.writeUtf(msg.holder);
            },
            buf -> new StatePacket(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

    @Override
    public CustomPacketPayload.Type<StatePacket> type() {
        return TYPE;
    }
}
