package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

public record HideNametagsPacket(boolean hideNametags) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HideNametagsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "hide_nametags"));

    public static final StreamCodec<ByteBuf, HideNametagsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HideNametagsPacket::hideNametags,
            HideNametagsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}