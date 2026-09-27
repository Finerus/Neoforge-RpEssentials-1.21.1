package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet CLIENT → SERVEUR
 * Envoyé quand l'admin clique "Sauvegarder" dans le GUI des professions.
 * Met à jour la config ET appelle .save() pour persister sur le disque.
 */
public record SaveProfessionPacket(
        String id,
        String displayName,
        String color,
        List<String> allowedCrafts,
        List<String> allowedBlocks,
        List<String> allowedItems,
        List<String> allowedEquipment,
        boolean isNew
) implements CustomPacketPayload {

    public static final Type<SaveProfessionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "save_profession"));

    public static final StreamCodec<FriendlyByteBuf, SaveProfessionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveProfessionPacket decode(FriendlyByteBuf buf) {
                    return new SaveProfessionPacket(
                            buf.readUtf(), buf.readUtf(), buf.readUtf(),
                            readList(buf), readList(buf), readList(buf), readList(buf),
                            buf.readBoolean()
                    );
                }
                @Override
                public void encode(FriendlyByteBuf buf, SaveProfessionPacket p) {
                    buf.writeUtf(p.id()); buf.writeUtf(p.displayName()); buf.writeUtf(p.color());
                    writeList(buf, p.allowedCrafts()); writeList(buf, p.allowedBlocks());
                    writeList(buf, p.allowedItems()); writeList(buf, p.allowedEquipment());
                    buf.writeBoolean(p.isNew());
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

    // =========================================================================
    // HANDLER — côté SERVEUR
    // =========================================================================

    public static void handleOnServer(SaveProfessionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!RpEssentialsPermissions.isStaff(player)) return;

            String cleanId = packet.id().toLowerCase().trim().replaceAll("[^a-z0-9_]", "_");
            if (cleanId.isEmpty() || packet.displayName().trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[RpEssentials] Invalid ID or name."));
                return;
            }

            try {
                // ── 1. Mise à jour de la liste des professions ────────────────────
                List<? extends String> current = ProfessionConfig.PROFESSIONS.get();
                List<String> updated = new ArrayList<>();
                boolean found = false;

                for (String line : current) {
                    String[] parts = line.split(";", 2);
                    if (parts.length >= 1 && parts[0].trim().equalsIgnoreCase(cleanId)) {
                        updated.add(cleanId + ";" + packet.displayName().trim() + ";" + packet.color().trim());
                        found = true;
                    } else {
                        updated.add(line);
                    }
                }
                if (!found) {
                    updated.add(cleanId + ";" + packet.displayName().trim() + ";" + packet.color().trim());
                }
                ProfessionConfig.PROFESSIONS.set(updated);
                ProfessionConfig.PROFESSIONS.save();

                // ── 2. Mise à jour des listes d'overrides ─────────────────────────
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_CRAFTS,    cleanId, packet.allowedCrafts());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_BLOCKS,    cleanId, packet.allowedBlocks());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_ITEMS,     cleanId, packet.allowedItems());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT, cleanId, packet.allowedEquipment());

                // ── 3. Recharge le cache en RAM ───────────────────────────────────
                ProfessionRestrictionManager.reloadCache();

                // ── 4. Message de confirmation avec résumé ────────────────────────
                String actionVerb = found ? "updated" : "created";
                player.sendSystemMessage(buildConfirmation(cleanId, packet, actionVerb));

                RpEssentials.LOGGER.info("[GUI] Profession '{}' {} by {}",
                        cleanId, actionVerb, player.getGameProfile().getName());

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RpEssentials] Config not loaded, please try again."));
                RpEssentials.LOGGER.error("[GUI] Config not loaded when saving profession", e);
            }
        });
    }

    /**
     * Builds the confirmation message sent to the admin after saving.
     * Shows how many entries each restriction category has.
     */
    private static Component buildConfirmation(String id, SaveProfessionPacket p, String verb) {
        StringBuilder sb = new StringBuilder();

        sb.append("§a[RpEssentials] Profession §e").append(id).append(" §a").append(verb).append(".\n");
        sb.append("§7  §6Name: §f").append(p.displayName())
                .append("  §6Color: §").append(p.color().replace("&","").replace("§",""))
                .append("■§r\n");
        sb.append(formatRestrictionLine("Crafts",    p.allowedCrafts()));
        sb.append(formatRestrictionLine("Blocks",    p.allowedBlocks()));
        sb.append(formatRestrictionLine("Items",     p.allowedItems()));
        sb.append(formatRestrictionLine("Equipment", p.allowedEquipment()));

        return Component.literal(sb.toString().stripTrailing());
    }

    /**
     * Formats one restriction category line.
     * Examples:
     *   §7  Crafts:    §8none
     *   §7  Equipment: §fminecraft:diamond_sword, minecraft:iron_sword §8(2)
     */
    private static String formatRestrictionLine(String label, List<String> items) {
        if (items.isEmpty()) {
            return "§7  " + label + ": §8none\n";
        }
        int max = 3;
        StringBuilder line = new StringBuilder("§7  ").append(label).append(": §f");

        List<String> shown = items.subList(0, Math.min(items.size(), max));
        line.append(String.join("§7, §f", shown));

        if (items.size() > max) {
            line.append(" §8(+").append(items.size() - max).append(" more)");
        } else {
            line.append(" §8(").append(items.size()).append(")");
        }
        line.append("\n");
        return line.toString();
    }

    private static void setAndSave(
            ModConfigSpec.ConfigValue<List<? extends String>> configValue,
            String profId,
            List<String> items) {

        List<String> current = new ArrayList<>(configValue.get());
        current.removeIf(line -> {
            String[] parts = line.split(";", 2);
            return parts.length >= 1 && parts[0].trim().equalsIgnoreCase(profId);
        });
        if (!items.isEmpty()) {
            current.add(profId + ";" + String.join(",", items));
        }
        configValue.set(current);
        configValue.save();
    }
}