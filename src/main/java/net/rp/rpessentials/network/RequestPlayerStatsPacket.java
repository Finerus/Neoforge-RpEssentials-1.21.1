package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.network.FriendlyByteBuf;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.network.compat.PacketDistributor;
import net.rp.rpessentials.network.compat.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.List;
import java.util.UUID;

/** Client -> server: the Player Profile GUI asks for the extra stats of a player. */
public record RequestPlayerStatsPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final Type<RequestPlayerStatsPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "request_player_stats"));

    public static final StreamCodec<FriendlyByteBuf, RequestPlayerStatsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestPlayerStatsPacket decode(FriendlyByteBuf buf) {
                    return new RequestPlayerStatsPacket(buf.readUUID());
                }

                @Override
                public void encode(FriendlyByteBuf buf, RequestPlayerStatsPacket p) {
                    buf.writeUUID(p.targetUuid());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(RequestPlayerStatsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpPerms.has(staff, RpPerm.STATS)) return;
            List<String> lines = net.rp.rpessentials.moderation.PlayerStatsLines.build(staff.getServer(), staff, packet.targetUuid());
            PacketDistributor.sendToPlayer(staff, new PlayerStatsPacket(packet.targetUuid(), lines));
        });
    }
}
