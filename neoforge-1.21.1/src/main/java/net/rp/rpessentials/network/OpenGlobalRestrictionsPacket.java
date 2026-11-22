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

public record OpenGlobalRestrictionsPacket(
        List<String> blockedCrafts,
        List<String> unbreakableBlocks,
        List<String> blockedItems,
        List<String> blockedEquipment,
        List<String> containerRestrictions
) implements CustomPacketPayload {

    public static final Type<OpenGlobalRestrictionsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "open_global_restrictions"));

    public static final StreamCodec<FriendlyByteBuf, OpenGlobalRestrictionsPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenGlobalRestrictionsPacket decode(FriendlyByteBuf buf) {
                    return new OpenGlobalRestrictionsPacket(
                            readList(buf), readList(buf), readList(buf),
                            readList(buf), readList(buf));
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenGlobalRestrictionsPacket p) {
                    writeList(buf, p.blockedCrafts());
                    writeList(buf, p.unbreakableBlocks());
                    writeList(buf, p.blockedItems());
                    writeList(buf, p.blockedEquipment());
                    writeList(buf, p.containerRestrictions());
                }

                private List<String> readList(FriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    List<String> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(buf.readUtf());
                    return out;
                }

                private void writeList(FriendlyByteBuf buf, List<String> list) {
                    buf.writeVarInt(list.size());
                    for (String s : list) buf.writeUtf(s);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(OpenGlobalRestrictionsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.openGlobalRestrictionsGui(packet);
        });
    }
}