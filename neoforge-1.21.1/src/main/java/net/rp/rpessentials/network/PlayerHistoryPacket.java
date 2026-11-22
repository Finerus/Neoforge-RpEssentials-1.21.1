package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client: history lines of a player, already formatted in the staff member's language. */
public record PlayerHistoryPacket(UUID targetUuid, boolean enabled, List<String> lines) implements CustomPacketPayload {

    public static final Type<PlayerHistoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "player_history"));

    public static final StreamCodec<FriendlyByteBuf, PlayerHistoryPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerHistoryPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    boolean enabled = buf.readBoolean();
                    int n = PacketValidation.readCount(buf);
                    List<String> lines = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) lines.add(buf.readUtf(2048));
                    return new PlayerHistoryPacket(id, enabled, lines);
                }

                @Override
                public void encode(FriendlyByteBuf buf, PlayerHistoryPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeBoolean(p.enabled());
                    buf.writeVarInt(p.lines().size());
                    for (String l : p.lines()) buf.writeUtf(l.length() > 2048 ? l.substring(0, 2048) : l, 2048);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(PlayerHistoryPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.receiveHistory(packet.targetUuid(), packet.enabled(), packet.lines());
        });
    }
}
