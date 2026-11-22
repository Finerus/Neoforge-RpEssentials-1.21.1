package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.client.ClientTooltipRestrictions;

import java.util.ArrayList;
import java.util.List;

public record SyncTooltipPacket(List<List<String>> blocked, List<List<String>> allowed,
                                List<String> names, List<String> licenses,
                                List<String> zones, String idMode) implements CustomPacketPayload {

    public static final Type<SyncTooltipPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "sync_tooltip"));

    public static final StreamCodec<FriendlyByteBuf, SyncTooltipPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SyncTooltipPacket decode(FriendlyByteBuf buf) {
                    return new SyncTooltipPacket(readNested(buf), readNested(buf), readList(buf), readList(buf), readList(buf), buf.readUtf(32));
                }

                @Override
                public void encode(FriendlyByteBuf buf, SyncTooltipPacket p) {
                    writeNested(buf, p.blocked());
                    writeNested(buf, p.allowed());
                    writeList(buf, p.names());
                    writeList(buf, p.licenses());
                    writeList(buf, p.zones());
                    buf.writeUtf(p.idMode(), 32);
                }
            };

    private static List<String> readList(FriendlyByteBuf buf) {
        int n = PacketValidation.readCount(buf);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    private static List<List<String>> readNested(FriendlyByteBuf buf) {
        int n = PacketValidation.readCount(buf);
        List<List<String>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(readList(buf));
        return out;
    }

    private static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    private static void writeNested(FriendlyByteBuf buf, List<List<String>> lists) {
        buf.writeVarInt(lists.size());
        for (List<String> l : lists) writeList(buf, l);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncTooltipPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTooltipRestrictions.update(p.blocked(), p.allowed(), p.names(), p.licenses(), p.zones(), p.idMode()));
    }
}