package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.ConfigRefresh;
import net.rp.rpessentials.GuiFeedback;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
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
                    String fileId = buf.readUtf();
                    int    count  = buf.readVarInt();
                    Map<String, String> changes = new HashMap<>(count);
                    for (int i = 0; i < count; i++) changes.put(buf.readUtf(), buf.readUtf());
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
            if (!RpEssentialsPermissions.isStaff(player)) {
                RpEssentials.LOGGER.warn("[ConfigGUI] Non-staff {} tried to save config '{}'",
                        player.getName().getString(), packet.fileId());
                return;
            }
            if (packet.changes().isEmpty()) {
                player.sendSystemMessage(Component.literal("§7[Config] No changes to apply."));
                return;
            }

            boolean sensitive = RpEssentialsPermissions.canEditSensitiveConfig(player);
            Set<String> knownPaths = ConfigInspector.getEntries(packet.fileId(), sensitive).stream()
                    .filter(e -> !e.isSection())
                    .map(ConfigInspector.EntryData::fullPath)
                    .collect(java.util.stream.Collectors.toSet());

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
                player.sendSystemMessage(Component.literal("§c[Config] No valid changes to apply."));
                return;
            }

            Map<String, String> oldValues = snapshotCurrentValues(packet.fileId(), validatedChanges.keySet());
            int applied = ConfigInspector.applyAndSave(packet.fileId(), validatedChanges);

            if (applied > 0) {
                ConfigRefresh.refresh(player.getServer());

                List<String> details = new ArrayList<>();
                for (Map.Entry<String, String> c : validatedChanges.entrySet()) {
                    String key = simplifyKey(c.getKey());
                    String oldVal = oldValues.getOrDefault(c.getKey(), "?");
                    String newVal = c.getValue();
                    if (oldVal.contains("\n") || newVal.contains("\n")) {
                        details.add("§e" + key + "§7:");
                        for (String line : GuiFeedback.diff(lines(oldVal), lines(newVal))) details.add("    " + line);
                    } else {
                        details.add("§e" + key + " §7: §c" + shorten(oldVal) + " §7-> §a" + shorten(newVal));
                    }
                }
                if (applied < validatedChanges.size()) {
                    details.add("§8" + (validatedChanges.size() - applied) + " value(s) not applied (invalid or out of range)");
                }
                GuiFeedback.report(player, "Config §e" + packet.fileId() + " §f: " + applied + " change(s) applied", details, true);

                PacketDistributor.sendToPlayer(player, ConfigFileEntriesPacket.from(
                        packet.fileId(), ConfigInspector.getEntries(packet.fileId(), sensitive)));
            } else {
                player.sendSystemMessage(Component.literal(
                        "§c[Config] No changes could be applied (validation failed?)."));
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