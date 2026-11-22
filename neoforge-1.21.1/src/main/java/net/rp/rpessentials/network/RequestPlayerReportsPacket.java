package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.UUID;

/** Client -> server: the profile GUI asks for the reports against a player. */
public record RequestPlayerReportsPacket(UUID targetUuid) implements CustomPacketPayload {

    public static final Type<RequestPlayerReportsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_player_reports"));

    public static final StreamCodec<FriendlyByteBuf, RequestPlayerReportsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestPlayerReportsPacket decode(FriendlyByteBuf buf) {
                    return new RequestPlayerReportsPacket(buf.readUUID());
                }

                @Override
                public void encode(FriendlyByteBuf buf, RequestPlayerReportsPacket p) {
                    buf.writeUUID(p.targetUuid());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(RequestPlayerReportsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpPerms.has(staff, RpPerm.REPORTS)) return;
            PacketDistributor.sendToPlayer(staff, PlayerReportsPacket.of(packet.targetUuid()));
        });
    }
}
