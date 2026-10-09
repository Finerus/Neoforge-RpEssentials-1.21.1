package net.rp.rpessentials.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.ConfigInspector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Packet CLIENT → SERVER
 *
 * Sent when the player selects a config file in the Config Manager GUI.
 * The server validates staff permission, loads the entries, and responds
 * with a ConfigFileEntriesPacket.
 */
public record RequestConfigFilePacket(String fileId) implements CustomPacketPayload {

    public static final Type<RequestConfigFilePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "request_config_file"));

    public static final StreamCodec<ByteBuf, RequestConfigFilePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, RequestConfigFilePacket::fileId,
                    RequestConfigFilePacket::new
            );

    private static final Map<UUID, Long> lastRequestTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long REQUEST_COOLDOWN_MS = 1000L;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — server side
    // =========================================================================

    public static void handleOnServer(RequestConfigFilePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Security: only staff can access config GUI
            if (!RpEssentialsPermissions.isStaff(player)) {
                RpEssentials.LOGGER.warn("[ConfigGUI] Non-staff player {} tried to request config file '{}'",
                        player.getName().getString(), packet.fileId());
                return;
            }

            // Rate-limit : 1 requête par seconde par joueur
            long now = System.currentTimeMillis();
            Long last = lastRequestTime.get(player.getUUID());
            if (last != null && now - last < REQUEST_COOLDOWN_MS) {
                RpEssentials.LOGGER.debug("[ConfigGUI] Rate-limited config request from {}",
                        player.getName().getString());
                return;
            }
            lastRequestTime.put(player.getUUID(), now);

            // Load entries for this file
            List<ConfigInspector.EntryData> entries = ConfigInspector.getEntries(
                    packet.fileId(), RpEssentialsPermissions.canEditSensitiveConfig(player));

            if (entries.isEmpty()) {
                RpEssentials.LOGGER.warn("[ConfigGUI] No entries found for file id '{}'", packet.fileId());
                return;
            }

            // Send entries back to the requesting player
            PacketDistributor.sendToPlayer(player,
                    ConfigFileEntriesPacket.from(packet.fileId(), entries));

            RpEssentials.LOGGER.debug("[ConfigGUI] Sent {} entries for file '{}' to {}",
                    entries.size(), packet.fileId(), player.getName().getString());
        });
    }
}
