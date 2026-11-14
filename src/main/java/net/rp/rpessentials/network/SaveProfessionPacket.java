package net.rp.rpessentials.network;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.util.GuiFeedback;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

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
                            PacketValidation.readList(buf), PacketValidation.readList(buf),
                            PacketValidation.readList(buf), PacketValidation.readList(buf),
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
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_PROF_INVALID)));
                return;
            }
            if (packet.isNew() && ProfessionRestrictionManager.isReservedTag(cleanId)) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_PROF_RESERVED)));
                return;
            }
            String cleanName = packet.displayName().trim().replace("&", "§").replace(";", "");
            if (cleanName.length() > 48) cleanName = cleanName.substring(0, 48);
            String cleanColor = packet.color().trim().replace("&", "§");
            if (!cleanColor.matches("(§[0-9a-f])?")) cleanColor = "";
            List<String> crafts = PacketValidation.cleanItems(packet.allowedCrafts());
            List<String> blocks = PacketValidation.cleanItems(packet.allowedBlocks());
            List<String> items = PacketValidation.cleanItems(packet.allowedItems());
            List<String> equipment = PacketValidation.cleanItems(packet.allowedEquipment());

            try {
                // ── 1. Mise à jour de la liste des professions ────────────────────
                List<? extends String> current = ProfessionConfig.PROFESSIONS.get();
                List<String> updated = new ArrayList<>();
                boolean found = false;

                for (String line : current) {
                    String[] parts = line.split(";", 2);
                    if (parts.length >= 1 && parts[0].trim().equalsIgnoreCase(cleanId)) {
                        updated.add(cleanId + ";" + cleanName + ";" + cleanColor);
                        found = true;
                    } else {
                        updated.add(line);
                    }
                }
                if (!found) {
                    updated.add(cleanId + ";" + cleanName + ";" + cleanColor);
                }
                ProfessionConfig.PROFESSIONS.set(updated);

                // ── 2. Mise à jour des listes d'overrides ─────────────────────────
                setOnly(ProfessionConfig.PROFESSION_ALLOWED_CRAFTS,    cleanId, crafts);
                setOnly(ProfessionConfig.PROFESSION_ALLOWED_BLOCKS,    cleanId, blocks);
                setOnly(ProfessionConfig.PROFESSION_ALLOWED_ITEMS,     cleanId, items);
                setOnly(ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT, cleanId, equipment);

                // ── 3. Recharge le cache en RAM ───────────────────────────────────
                ProfessionRestrictionManager.reloadCache();
                ProfessionConfig.SPEC.save();

                // ── 4. Message de confirmation avec résumé ────────────────────────
                String actionVerb = found ? "updated" : "created";

                player.sendSystemMessage(buildConfirmation(player, cleanId, cleanName, cleanColor, crafts,
                        blocks, items, equipment, found));

                RpEssentials.LOGGER.info("[GUI] Profession '{}' {} by {}",
                        cleanId, actionVerb, player.getGameProfile().getName());

            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.GUI_CONFIG_NOT_LOADED)));
                RpEssentials.LOGGER.error("[GUI] Config not loaded when saving profession", e);
            }
        });
    }

    /**
     * Builds the confirmation message sent to the admin after saving.
     * Shows how many entries each restriction category has.
     */
    private static Component buildConfirmation(ServerPlayer v, String id, String name, String color,
                                               List<String> crafts, List<String> blocks,
                                               List<String> items, List<String> equipment, boolean updated) {
        MutableComponent out = Component.literal(
                ServerLang.get(v, updated ? MessagesConfig.GUI_PROF_UPDATED : MessagesConfig.GUI_PROF_CREATED, "id", id) + "\n"
                + ServerLang.get(v, MessagesConfig.GUI_PROF_SUMMARY, "name", name,
                        "color", "§" + color.replace("&", "").replace("§", "")));
        out.append(restrictionLine(v, ServerLang.get(v, MessagesConfig.GUI_LABEL_CRAFTS), crafts));
        out.append(restrictionLine(v, ServerLang.get(v, MessagesConfig.GUI_LABEL_BLOCKS), blocks));
        out.append(restrictionLine(v, ServerLang.get(v, MessagesConfig.GUI_LABEL_ITEMS), items));
        out.append(restrictionLine(v, ServerLang.get(v, MessagesConfig.GUI_LABEL_EQUIPMENT), equipment));
        return out;
    }

    private static MutableComponent restrictionLine(ServerPlayer v, String label, List<String> items) {
        if (items.isEmpty()) return Component.literal("\n" + ServerLang.get(v, MessagesConfig.GUI_PROF_LINE_NONE, "label", label));
        int max = 3;
        String text = "\n" + ServerLang.get(v, MessagesConfig.GUI_PROF_LINE, "label", label,
                "items", String.join("§7, §f", items.subList(0, Math.min(items.size(), max))));
        if (items.size() <= max) return Component.literal(text
                + ServerLang.get(v, MessagesConfig.GUI_PROF_COUNT, "count", String.valueOf(items.size())));
        List<String> tip = items.stream().map(s -> "§f" + s).toList();
        return GuiFeedback.withTooltip(v,
                Component.literal(text + ServerLang.get(v, MessagesConfig.GUI_PROF_MORE,
                        "count", String.valueOf(items.size() - max))), tip);
    }



    private static void setOnly(
            ModConfigSpec.ConfigValue<List<? extends String>> configValue,
            String profId,
            List<String> items) {
        List<String> current = new ArrayList<>(configValue.get());
        current.removeIf(line -> {
            String[] parts = line.split(";", 2);
            return parts.length >= 1 && parts[0].trim().equalsIgnoreCase(profId);
        });
        if (!items.isEmpty()) current.add(profId + ";" + String.join(",", items));
        configValue.set(current);
    }
}