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
import net.rp.rpessentials.moderation.HistoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client -> server: the Player Profile GUI asks for the history of a player. */
public record RequestPlayerHistoryPacket(UUID targetUuid) implements CustomPacketPayload {

    /** Lines sent to the GUI (newest first). */
    public static final int MAX_LINES = 150;

    public static final Type<RequestPlayerHistoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_player_history"));

    public static final StreamCodec<FriendlyByteBuf, RequestPlayerHistoryPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RequestPlayerHistoryPacket decode(FriendlyByteBuf buf) {
                    return new RequestPlayerHistoryPacket(buf.readUUID());
                }

                @Override
                public void encode(FriendlyByteBuf buf, RequestPlayerHistoryPacket p) {
                    buf.writeUUID(p.targetUuid());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(RequestPlayerHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpPerms.has(staff, RpPerm.HISTORY)) return;
            List<String> lines = new ArrayList<>();
            if (HistoryManager.enabled()) {
                for (HistoryManager.Entry e : HistoryManager.get(packet.targetUuid(), staff)) {
                    if (lines.size() >= MAX_LINES) break;
                    lines.add(HistoryManager.format(staff, e));
                }
            }
            PacketDistributor.sendToPlayer(staff, new PlayerHistoryPacket(packet.targetUuid(), HistoryManager.enabled(), lines));
        });
    }
}
