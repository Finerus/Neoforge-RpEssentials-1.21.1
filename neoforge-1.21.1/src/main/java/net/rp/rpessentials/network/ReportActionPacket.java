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
import net.rp.rpessentials.moderation.ReportService;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.UUID;

/**
 * Client -> server: a staff member acts on a report from the profile GUI.
 * The server answers with the updated list of reports against {@code targetUuid}.
 */
public record ReportActionPacket(UUID targetUuid, int reportId, Action action, String note) implements CustomPacketPayload {

    public enum Action { CLAIM, CLOSE, REOPEN, DELETE, TP }

    public static final int MAX_NOTE = 256;

    public static final Type<ReportActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "report_action"));

    public static final StreamCodec<FriendlyByteBuf, ReportActionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ReportActionPacket decode(FriendlyByteBuf buf) {
                    return new ReportActionPacket(buf.readUUID(), buf.readVarInt(),
                            buf.readEnum(Action.class), buf.readUtf(MAX_NOTE));
                }

                @Override
                public void encode(FriendlyByteBuf buf, ReportActionPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeVarInt(p.reportId());
                    buf.writeEnum(p.action());
                    String n = p.note() == null ? "" : p.note();
                    buf.writeUtf(n.length() > MAX_NOTE ? n.substring(0, MAX_NOTE) : n, MAX_NOTE);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(ReportActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpPerms.has(staff, RpPerm.REPORTS)) return;
            int id = packet.reportId();
            switch (packet.action()) {
                case CLAIM -> ReportService.claim(staff, id);
                case CLOSE -> ReportService.close(staff.getServer(), staff, id, packet.note());
                case REOPEN -> ReportService.reopen(staff.getServer(), staff, id);
                case DELETE -> ReportService.delete(staff.getServer(), staff, id);
                case TP -> ReportService.teleport(staff, id);
            }
            PacketDistributor.sendToPlayer(staff, PlayerReportsPacket.of(packet.targetUuid()));
        });
    }
}
