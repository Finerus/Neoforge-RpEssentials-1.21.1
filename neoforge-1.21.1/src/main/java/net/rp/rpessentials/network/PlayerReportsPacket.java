package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.moderation.ReportManager;
import net.rp.rpessentials.moderation.ReportService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client: reports against a player, for the Reports tab of the profile GUI. */
public record PlayerReportsPacket(UUID targetUuid, boolean enabled, List<View> reports) implements CustomPacketPayload {

    /** Lines of text are already formatted on the server (dates); the status is the enum name. */
    public record View(int id, String status, String reporter, String date, String place,
                       String reason, String handler, String note) {
        public boolean isOpen() { return !"CLOSED".equals(status); }
    }

    public static final int MAX_REPORTS = 100;

    public static final Type<PlayerReportsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "player_reports"));

    public static final StreamCodec<FriendlyByteBuf, PlayerReportsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerReportsPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    boolean enabled = buf.readBoolean();
                    int n = PacketValidation.readCount(buf);
                    List<View> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new View(buf.readVarInt(), buf.readUtf(16), buf.readUtf(64), buf.readUtf(32),
                                buf.readUtf(256), buf.readUtf(2048), buf.readUtf(64), buf.readUtf(2048)));
                    }
                    return new PlayerReportsPacket(id, enabled, list);
                }

                @Override
                public void encode(FriendlyByteBuf buf, PlayerReportsPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeBoolean(p.enabled());
                    buf.writeVarInt(p.reports().size());
                    for (View v : p.reports()) {
                        buf.writeVarInt(v.id());
                        buf.writeUtf(v.status(), 16);
                        buf.writeUtf(cut(v.reporter(), 64), 64);
                        buf.writeUtf(cut(v.date(), 32), 32);
                        buf.writeUtf(cut(v.place(), 256), 256);
                        buf.writeUtf(cut(v.reason(), 2048), 2048);
                        buf.writeUtf(cut(v.handler(), 64), 64);
                        buf.writeUtf(cut(v.note(), 2048), 2048);
                    }
                }
            };

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Builds the packet for {@code target} from the stored reports. */
    public static PlayerReportsPacket of(UUID target) {
        List<View> views = new ArrayList<>();
        for (ReportManager.Report r : ReportManager.forTarget(target)) {
            if (views.size() >= MAX_REPORTS) break;
            views.add(new View(r.id, r.status.name(), r.reporterName, ReportService.date(r.createdAt),
                    ReportService.place(r), r.reason, r.handlerName, r.closeNote));
        }
        return new PlayerReportsPacket(target, ReportManager.enabled(), views);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(PlayerReportsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.receiveReports(packet.targetUuid(), packet.enabled(), packet.reports());
        });
    }
}
