package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.rp.rpessentials.network.compat.ByteBufCodecs;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

import java.util.List;

/** Server -> client: the RpEssentials permission nodes granted to the player (keys without "rpessentials."). */
public record SyncPermissionsPacket(List<String> granted) implements CustomPacketPayload {

    public static final Type<SyncPermissionsPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "sync_permissions"));

    public static final StreamCodec<ByteBuf, SyncPermissionsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)), SyncPermissionsPacket::granted,
            SyncPermissionsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
