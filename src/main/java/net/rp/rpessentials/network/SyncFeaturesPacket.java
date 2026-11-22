package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.rp.rpessentials.network.compat.ByteBufCodecs;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

/** Server systems that can be turned off, for the client (screens, license item, tooltips). */
public record SyncFeaturesPacket(boolean professionsEnabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncFeaturesPacket> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation(RpEssentials.MODID, "sync_features"));

    public static final StreamCodec<ByteBuf, SyncFeaturesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncFeaturesPacket::professionsEnabled,
            SyncFeaturesPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
