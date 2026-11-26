package net.rp.rpessentials.network;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.core.ConfigRefresh;
import net.rp.rpessentials.util.GuiFeedback;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.ConfigInspector;

import java.util.*;

/**
 * Packet CLIENT → SERVEUR : application des modifications de config depuis le GUI.
 *
 * Nouveau en 4.1.6 : broadcast aux staffs online un résumé des changements.
 */
public record SaveConfigEntriesPacket(String fileId, Map<String, String> changes)
        implements CustomPacketPayload {

    public static final Type<SaveConfigEntriesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "save_config_entries"));

    public static final StreamCodec<FriendlyByteBuf, SaveConfigEntriesPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveConfigEntriesPacket decode(FriendlyByteBuf buf) {
                    String fileId = buf.readUtf(128);
                    int    count  = PacketValidation.readCount(buf);
                    Map<String, String> changes = new HashMap<>(count);
                    for (int i = 0; i < count; i++) changes.put(buf.readUtf(1024), buf.readUtf());
                    return new SaveConfigEntriesPacket(fileId, changes);
                }
                @Override
                public void encode(FriendlyByteBuf buf, SaveConfigEntriesPacket packet) {
                    buf.writeUtf(packet.fileId());
                    buf.writeVarInt(packet.changes().size());
                    for (Map.Entry<String, String> e : packet.changes().entrySet()) {
                        buf.writeUtf(e.getKey());
                        buf.writeUtf(e.getValue());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — côté SERVEUR
    // =========================================================================
    public static void handleOnServer(SaveConfigEntriesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpPerms.has(player, RpPerm.CONFIG_GUI)) {
                if (PacketGuard.every(player, "config_denied", 60_000L)) RpEssentials.LOGGER.warn("[ConfigGUI] Non-staff {} tried to save config '{}'",
                        player.getName().getString(), packet.fileId());
                return;
            }
            if (packet.changes().isEmpty()) {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NO_CHANGES)));
                return;
            }

            Set<String> knownPaths = ConfigInspector.visiblePaths(packet.fileId(), p -> RpPerms.has(player, p));

            Map<String, String> validatedChanges = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> change : packet.changes().entrySet()) {
                if (!knownPaths.contains(change.getKey())) {
                    RpEssentials.LOGGER.warn("[ConfigGUI] {} tried to set unknown or restricted path '{}'",
                            player.getName().getString(), change.getKey());
                    continue;
                }
                if (change.getValue().length() > 8000) {
                    RpEssentials.LOGGER.warn("[ConfigGUI] {} sent oversized value for '{}'",
                            player.getName().getString(), change.getKey());
                    continue;
                }
                validatedChanges.put(change.getKey(), change.getValue());
            }

            if (validatedChanges.isEmpty()) {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NO_VALID)));
                return;
            }

            Map<String, String> oldValues = snapshotCurrentValues(packet.fileId(), validatedChanges.keySet());
            int applied = ConfigInspector.applyAndSave(packet.fileId(), validatedChanges);

            if (applied > 0) {
                // Only what depends on this file.
                ConfigRefresh.refresh(player.getServer(), ConfigInspector.specOf(packet.fileId()));

                // Restricted options are only shown to the staff who may see them.
                List<String> details = new ArrayList<>();
                List<String> restrictedDetails = new ArrayList<>();
                List<String> restrictedHidden = new ArrayList<>();
                for (Map.Entry<String, String> c : validatedChanges.entrySet()) {
                    String key = simplifyKey(c.getKey());
                    // A secret (webhook URL, bot token) is never written in the chat of the staff nor in the log.
                    String oldVal = ConfigInspector.mask(key, oldValues.getOrDefault(c.getKey(), "?"));
                    String newVal = ConfigInspector.mask(key, c.getValue());
                    boolean restricted = ConfigInspector.isRestricted(packet.fileId(), c.getKey());
                    List<String> target = restricted ? restrictedDetails : details;
                    if (restricted) restrictedHidden.add("§e" + key + " §7: ********");
                    if (oldVal.contains("\n") || newVal.contains("\n")) {
                        target.add("§e" + key + "§7:");
                        for (String line : GuiFeedback.diff(lines(oldVal), lines(newVal))) target.add("    " + line);
                    } else {
                        target.add("§e" + key + " §7: §c" + shorten(oldVal) + " §7-> §a" + shorten(newVal));
                    }
                }
                final int notApplied = validatedChanges.size() - applied;
                GuiFeedback.report(player, MessagesConfig.GUI_CONFIG_APPLIED,
                        new String[]{"file", packet.fileId(), "count", String.valueOf(applied)}, v -> {
                            List<String> out = new ArrayList<>(details);
                            boolean all = v == null || v == player || RpPerms.has(v, RpPerm.CONFIG_SENSITIVE);
                            out.addAll(all ? restrictedDetails : restrictedHidden);
                            if (notApplied > 0) out.add(ServerLang.get(v, MessagesConfig.GUI_CONFIG_NOT_APPLIED,
                                    "count", String.valueOf(notApplied)));
                            return out;
                        }, true);

                PacketDistributor.sendToPlayer(player, ConfigFileEntriesPacket.from(
                        packet.fileId(), ConfigInspector.getEntries(packet.fileId(), p -> RpPerms.has(player, p))));
            } else {
                player.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(ServerLang.get(player, MessagesConfig.GUI_CONFIG_FAILED)));
            }
        });
    }

    // =========================================================================
    // PRIVATE
    // =========================================================================

    /**
     * Récupère les valeurs actuelles des clés modifiées (avant application).
     */
    private static Map<String, String> snapshotCurrentValues(String fileId, java.util.Set<String> paths) {
        Map<String, String> snapshot = new HashMap<>();
        try {
            List<ConfigInspector.EntryData> entries = ConfigInspector.getEntries(fileId);
            for (ConfigInspector.EntryData e : entries) {
                if (paths.contains(e.fullPath())) {
                    snapshot.put(e.fullPath(), e.currentValue());
                }
            }
        } catch (Exception ignored) {}
        return snapshot;
    }

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) if (!l.isBlank()) out.add(l.trim());
        return out;
    }

    private static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 37) + "..." : s;
    }

    /** Raccourcit le chemin complet "section.subsection.key" en juste "key" pour la lisibilité. */
    private static String simplifyKey(String fullPath) {
        int dot = fullPath.lastIndexOf('.');
        return dot >= 0 ? fullPath.substring(dot + 1) : fullPath;
    }
}