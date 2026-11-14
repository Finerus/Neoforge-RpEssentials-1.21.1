package net.rp.rpessentials;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.network.PacketValidation;

import java.util.ArrayList;
import java.util.List;

public record SyncNametagBatchPacket(List<SyncNametagDataPacket> entries) implements CustomPacketPayload {

    public static final Type<SyncNametagBatchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "sync_nametag_batch"));

    public static final StreamCodec<FriendlyByteBuf, SyncNametagBatchPacket> CODEC =
            new StreamCodec<>() {
                @Override
                public SyncNametagBatchPacket decode(FriendlyByteBuf buf) {
                    int n = PacketValidation.readCount(buf);
                    List<SyncNametagDataPacket> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(SyncNametagDataPacket.CODEC.decode(buf));
                    return new SyncNametagBatchPacket(list);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SyncNametagBatchPacket p) {
                    buf.writeVarInt(p.entries().size());
                    for (SyncNametagDataPacket e : p.entries()) SyncNametagDataPacket.CODEC.encode(buf, e);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncNametagBatchPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            for (SyncNametagDataPacket e : packet.entries()) {
                net.rp.rpessentials.client.ClientNametagCache.update(e);
            }
        });
    }
}