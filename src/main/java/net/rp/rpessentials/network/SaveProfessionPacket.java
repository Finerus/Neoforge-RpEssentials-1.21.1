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
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            readList(buf),
                            readList(buf),
                            readList(buf),
                            readList(buf),
                            buf.readBoolean()
                    );
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveProfessionPacket p) {
                    buf.writeUtf(p.id());
                    buf.writeUtf(p.displayName());
                    buf.writeUtf(p.color());
                    writeList(buf, p.allowedCrafts());
                    writeList(buf, p.allowedBlocks());
                    writeList(buf, p.allowedItems());
                    writeList(buf, p.allowedEquipment());
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

            // Double vérification droits — ne jamais faire confiance au client
            if (!RpEssentialsPermissions.isStaff(player)) return;

            // Nettoyage de l'ID
            String cleanId = packet.id().toLowerCase().trim().replaceAll("[^a-z0-9_]", "_");
            if (cleanId.isEmpty() || packet.displayName().trim().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[RPEssentials] ID ou nom invalide."));
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

                // set() + save() — set() met en RAM, save() écrit sur le disque
                ProfessionConfig.PROFESSIONS.set(updated);
                ProfessionConfig.PROFESSIONS.save();

                // ── 2. Mise à jour des listes d'overrides ─────────────────────────
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_CRAFTS,    cleanId, packet.allowedCrafts());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_BLOCKS,    cleanId, packet.allowedBlocks());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_ITEMS,     cleanId, packet.allowedItems());
                setAndSave(ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT, cleanId, packet.allowedEquipment());

                // ── 3. Recharge le cache en RAM ───────────────────────────────────
                ProfessionRestrictionManager.reloadCache();

                String action = found ? "mise à jour" : "créée";
                player.sendSystemMessage(Component.literal(
                        "§a[RPEssentials] Profession §e" + cleanId + " §a" + action + "."));
                RpEssentials.LOGGER.info("[GUI] Profession '{}' {} by {}",
                        cleanId, action, player.getGameProfile().getName());

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(
                        "§c[RPEssentials] Config non chargée, réessayez."));
                RpEssentials.LOGGER.error("[GUI] Config not loaded when saving profession", e);
            }
        });
    }

    /**
     * Met à jour l'entrée "professionId;item1,item2" dans une ConfigValue de liste
     * puis la persiste immédiatement sur le disque avec .save().
     * Si items est vide, l'entrée existante est supprimée.
     */
    private static void setAndSave(
            ModConfigSpec.ConfigValue<List<? extends String>> configValue,
            String profId,
            List<String> items) {

        List<String> current = new ArrayList<>(configValue.get());

        // Supprime l'ancienne entrée pour cette profession
        current.removeIf(line -> {
            String[] parts = line.split(";", 2);
            return parts.length >= 1 && parts[0].trim().equalsIgnoreCase(profId);
        });

        // Ajoute la nouvelle seulement si la liste n'est pas vide
        if (!items.isEmpty()) {
            current.add(profId + ";" + String.join(",", items));
        }

        configValue.set(current);
        configValue.save(); // ← persiste sur le disque
    }
}