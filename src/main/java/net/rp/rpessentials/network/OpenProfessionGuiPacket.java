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

/**
 * Packet SERVEUR → CLIENT
 * Envoie la liste complète des professions existantes à l'admin
 * pour qu'il puisse ouvrir le GUI d'édition.
 */
public record OpenProfessionGuiPacket(List<ProfessionEntry> professions) implements CustomPacketPayload {

    // =========================================================================
    // DATA — une profession complète sérialisable
    // =========================================================================

    public record ProfessionEntry(
            String id,
            String displayName,
            String color,
            List<String> allowedCrafts,
            List<String> allowedBlocks,
            List<String> allowedItems,
            List<String> allowedEquipment
    ) {}

    // =========================================================================
    // PACKET INFRA
    // =========================================================================

    public static final Type<OpenProfessionGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "open_profession_gui"));

    public static final StreamCodec<FriendlyByteBuf, OpenProfessionGuiPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenProfessionGuiPacket decode(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<ProfessionEntry> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        list.add(new ProfessionEntry(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                readStringList(buf),
                                readStringList(buf),
                                readStringList(buf),
                                readStringList(buf)
                        ));
                    }
                    return new OpenProfessionGuiPacket(list);
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenProfessionGuiPacket packet) {
                    buf.writeVarInt(packet.professions().size());
                    for (ProfessionEntry e : packet.professions()) {
                        buf.writeUtf(e.id());
                        buf.writeUtf(e.displayName());
                        buf.writeUtf(e.color());
                        writeStringList(buf, e.allowedCrafts());
                        writeStringList(buf, e.allowedBlocks());
                        writeStringList(buf, e.allowedItems());
                        writeStringList(buf, e.allowedEquipment());
                    }
                }

                private List<String> readStringList(FriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    List<String> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(buf.readUtf());
                    return out;
                }

                private void writeStringList(FriendlyByteBuf buf, List<String> list) {
                    buf.writeVarInt(list.size());
                    for (String s : list) buf.writeUtf(s);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — côté CLIENT
    // =========================================================================

    public static void handleOnClient(OpenProfessionGuiPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.openProfessionGui(packet.professions());
        });
    }
}
