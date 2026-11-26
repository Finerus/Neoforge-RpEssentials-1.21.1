package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
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

    /** Recent request times per player (sliding window): the search fetches every file at once. */
    private static final Map<UUID, java.util.ArrayDeque<Long>> recentRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long REQUEST_WINDOW_MS = 10_000L;
    private static final int REQUEST_WINDOW_MAX = 60;

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — server side
    // =========================================================================

    public static void handleOnServer(RequestConfigFilePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            // Security: only staff can access config GUI
            if (!RpPerms.has(player, RpPerm.CONFIG_GUI)) {
                if (PacketGuard.every(player, "config_denied", 60_000L)) RpEssentials.LOGGER.warn("[ConfigGUI] Non-staff player {} tried to request config file '{}'",
                        player.getName().getString(), packet.fileId());
                return;
            }

            // Rate-limit : at most REQUEST_WINDOW_MAX requests per REQUEST_WINDOW_MS per player
            long now = System.currentTimeMillis();
            java.util.ArrayDeque<Long> times = recentRequests.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
            while (!times.isEmpty() && now - times.peekFirst() > REQUEST_WINDOW_MS) times.pollFirst();
            if (times.size() >= REQUEST_WINDOW_MAX) {
                RpEssentials.LOGGER.debug("[ConfigGUI] Rate-limited config request from {}",
                        player.getName().getString());
                return;
            }
            times.addLast(now);

            // Load entries for this file
            List<ConfigInspector.EntryData> entries = ConfigInspector.getEntries(
                    packet.fileId(), p -> RpPerms.has(player, p));

            if (entries.isEmpty()) {
                RpEssentials.LOGGER.warn("[ConfigGUI] No entries found for file id '{}'", packet.fileId());
                // Answer anyway so the client stops waiting.
            }

            // Send entries back to the requesting player
            PacketDistributor.sendToPlayer(player,
                    ConfigFileEntriesPacket.from(packet.fileId(), entries));

            RpEssentials.LOGGER.debug("[ConfigGUI] Sent {} entries for file '{}' to {}",
                    entries.size(), packet.fileId(), player.getName().getString());
        });
    }
}
