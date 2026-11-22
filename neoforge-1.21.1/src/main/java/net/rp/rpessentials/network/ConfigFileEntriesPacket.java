package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.client.gui.ConfigManagerScreen;
import net.rp.rpessentials.config.ConfigInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet SERVER → CLIENT
 * -
 * Response to RequestConfigFilePacket.
 * Contains all config entries for one file, serialized as EntryTransfer objects.
 * -
 * The ValueType is encoded as a byte to keep the packet compact.
 * Comments may be long but are bounded per-entry.
 */
public record ConfigFileEntriesPacket(String fileId, List<EntryTransfer> entries)
        implements CustomPacketPayload {

    // =========================================================================
    // TRANSFER OBJECT
    // =========================================================================

    /**
     * Wire representation of a single config entry.
     * isSection=true → render as a section header, all other fields ignored.
     */
    public record EntryTransfer(
            String fullPath,
            String key,
            String comment,
            byte   typeByte,      // ConfigInspector.ValueType ordinal
            String currentValue,
            String defaultValue,
            boolean hasRange,
            double  rangeMin,
            double  rangeMax,
            boolean isSection
    ) {
        public ConfigInspector.ValueType type() {
            ConfigInspector.ValueType[] vals = ConfigInspector.ValueType.values();
            int ord = typeByte & 0xFF;
            return ord < vals.length ? vals[ord] : ConfigInspector.ValueType.UNKNOWN;
        }
    }

    // =========================================================================
    // FACTORY
    // =========================================================================

    public static ConfigFileEntriesPacket from(String fileId, List<ConfigInspector.EntryData> entries) {
        List<EntryTransfer> transfers = new ArrayList<>(entries.size());
        for (ConfigInspector.EntryData e : entries) {
            transfers.add(new EntryTransfer(
                    e.fullPath(), e.key(),
                    truncateComment(e.comment()),   // cap comment length for packet safety
                    (byte) e.type().ordinal(),
                    e.currentValue(), e.defaultValue(),
                    e.hasRange(), e.rangeMin(), e.rangeMax(),
                    e.isSection()
            ));
        }
        return new ConfigFileEntriesPacket(fileId, transfers);
    }

    /** Limits comment to 512 chars to avoid excessively large packets. */
    private static String truncateComment(String comment) {
        if (comment == null || comment.length() <= 512) return comment != null ? comment : "";
        return comment.substring(0, 509) + "…";
    }

    // =========================================================================
    // PACKET INFRA
    // =========================================================================

    public static final Type<ConfigFileEntriesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "config_file_entries"));

    public static final StreamCodec<FriendlyByteBuf, ConfigFileEntriesPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ConfigFileEntriesPacket decode(FriendlyByteBuf buf) {
                    String fileId = buf.readUtf();
                    int count = buf.readVarInt();
                    List<EntryTransfer> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String  fullPath     = buf.readUtf();
                        String  key          = buf.readUtf();
                        String  comment      = buf.readUtf();
                        byte    typeByte     = buf.readByte();
                        String  current      = buf.readUtf();
                        String  defVal       = buf.readUtf();
                        boolean hasRange     = buf.readBoolean();
                        double  rangeMin     = hasRange ? buf.readDouble() : 0;
                        double  rangeMax     = hasRange ? buf.readDouble() : 0;
                        boolean isSection    = buf.readBoolean();
                        entries.add(new EntryTransfer(fullPath, key, comment, typeByte,
                                current, defVal, hasRange, rangeMin, rangeMax, isSection));
                    }
                    return new ConfigFileEntriesPacket(fileId, entries);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ConfigFileEntriesPacket packet) {
                    buf.writeUtf(packet.fileId());
                    buf.writeVarInt(packet.entries().size());
                    for (EntryTransfer e : packet.entries()) {
                        buf.writeUtf(e.fullPath());
                        buf.writeUtf(e.key());
                        buf.writeUtf(e.comment());
                        buf.writeByte(e.typeByte());
                        buf.writeUtf(e.currentValue());
                        buf.writeUtf(e.defaultValue());
                        buf.writeBoolean(e.hasRange());
                        if (e.hasRange()) {
                            buf.writeDouble(e.rangeMin());
                            buf.writeDouble(e.rangeMax());
                        }
                        buf.writeBoolean(e.isSection());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — client side
    // =========================================================================

    public static void handleOnClient(ConfigFileEntriesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            // Deliver entries to the open ConfigManagerScreen (if still open)
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof ConfigManagerScreen screen) {
                screen.onFileDataReceived(packet);
            }
        });
    }
}
