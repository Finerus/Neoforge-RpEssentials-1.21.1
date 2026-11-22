package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.rp.rpessentials.network.compat.StreamCodec;
import net.rp.rpessentials.network.compat.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.rp.rpessentials.network.compat.IPayloadContext;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client: extra stats lines of a player, already formatted in the staff member's language. */
public record PlayerStatsPacket(UUID targetUuid, List<String> lines) implements CustomPacketPayload {

    public static final Type<PlayerStatsPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "player_stats"));

    public static final StreamCodec<FriendlyByteBuf, PlayerStatsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerStatsPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    int n = PacketValidation.readCount(buf);
                    List<String> lines = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) lines.add(buf.readUtf(2048));
                    return new PlayerStatsPacket(id, lines);
                }

                @Override
                public void encode(FriendlyByteBuf buf, PlayerStatsPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeVarInt(p.lines().size());
                    for (String l : p.lines()) buf.writeUtf(l.length() > 2048 ? l.substring(0, 2048) : l, 2048);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(PlayerStatsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.receiveStats(packet.targetUuid(), packet.lines());
        });
    }
}
