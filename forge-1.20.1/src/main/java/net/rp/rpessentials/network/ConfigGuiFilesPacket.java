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

/**
 * Packet SERVER → CLIENT
 * -
 * Sent when the player requests the Config Manager GUI.
 * Contains only the lightweight list of available config files (id + display name).
 * The actual entries are loaded on demand via RequestConfigFilePacket / ConfigFileEntriesPacket.
 */
public record ConfigGuiFilesPacket(List<FileEntry> files) implements CustomPacketPayload {

    // =========================================================================
    // DATA
    // =========================================================================

    /** Lightweight descriptor for one config file shown in the file panel. */
    public record FileEntry(String id, String displayName, int color, String mod, int modColor,
                            int accent, int section, int title) {
        public FileEntry(String id, String displayName, int color) { this(id, displayName, color, "", 0, 0, 0, 0); }
        public FileEntry(String id, String displayName) { this(id, displayName, 0); }
    }

    // =========================================================================
    // PACKET INFRA
    // =========================================================================
    public static final Type<ConfigGuiFilesPacket> TYPE =
            new Type<>(new ResourceLocation(RpEssentials.MODID, "config_gui_files"));

    public static final StreamCodec<FriendlyByteBuf, ConfigGuiFilesPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ConfigGuiFilesPacket decode(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<FileEntry> files = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        files.add(new FileEntry(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readUtf(),
                                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
                    }
                    return new ConfigGuiFilesPacket(files);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ConfigGuiFilesPacket packet) {
                    buf.writeVarInt(packet.files().size());
                    for (FileEntry f : packet.files()) {
                        buf.writeUtf(f.id());
                        buf.writeUtf(f.displayName());
                        buf.writeInt(f.color());
                        buf.writeUtf(f.mod());
                        buf.writeInt(f.modColor());
                        buf.writeInt(f.accent());
                        buf.writeInt(f.section());
                        buf.writeInt(f.title());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — client side
    // =========================================================================

    public static void handleOnClient(ConfigGuiFilesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.openConfigManagerGui(packet.files());
        });
    }
}
