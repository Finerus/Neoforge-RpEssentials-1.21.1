package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.GuiFeedback;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.NoteManager;

import java.util.List;
import java.util.UUID;

/**
 * Packet CLIENT -> SERVEUR
 * Ajouter ou supprimer une note sur un joueur depuis le GUI.
 */
public record PlayerNoteActionPacket(
        UUID   targetUuid,
        boolean isDelete,   // true = supprimer, false = ajouter
        int    noteId,      // utilisé si isDelete = true
        String text         // utilisé si isDelete = false
) implements CustomPacketPayload {

    public static final Type<PlayerNoteActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "player_note_action"));

    public static final StreamCodec<FriendlyByteBuf, PlayerNoteActionPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerNoteActionPacket decode(FriendlyByteBuf buf) {
                    return new PlayerNoteActionPacket(
                            buf.readUUID(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readUtf(256)
                    );
                }
                @Override
                public void encode(FriendlyByteBuf buf, PlayerNoteActionPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeBoolean(p.isDelete());
                    buf.writeVarInt(p.noteId());
                    buf.writeUtf(p.text());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnServer(PlayerNoteActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer staff)) return;
            if (!RpEssentialsPermissions.isStaff(staff)) return;

            MinecraftServer server = staff.getServer();
            String target = LastConnectionManager.resolveName(server, packet.targetUuid());
            NoteManager.NoteEntry old = null;
            for (NoteManager.NoteEntry n : NoteManager.getNotes(packet.targetUuid())) {
                if (n.id == packet.noteId()) old = n;
            }

            if (packet.isDelete()) {
                if (old == null || !NoteManager.removeNote(packet.targetUuid(), packet.noteId())) {
                    staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[NOTE] Note #" + packet.noteId() + " not found for " + target + "."));
                    return;
                }
                GuiFeedback.report(staff, "Note #" + packet.noteId() + " deleted for §e" + target,
                        List.of("§c- §7" + old.text, "§8Original author: " + old.displayAuthor()), false);
                return;
            }

            String text = packet.text().trim();
            if (text.isEmpty()) return;
            if (text.length() > 256) text = text.substring(0, 256);

            if (packet.noteId() > 0) {
                if (old == null || !NoteManager.editNote(packet.targetUuid(), packet.noteId(), text, staff.getName().getString())) {
                    staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[NOTE] Note #" + packet.noteId() + " not found for " + target + "."));
                    return;
                }
                GuiFeedback.report(staff, "Note #" + packet.noteId() + " edited for §e" + target,
                        List.of("§c- §7" + old.text, "§a+ §7" + text, "§8Original author: " + old.authorName), false);
                return;
            }

            int newId = NoteManager.addNote(packet.targetUuid(), target,
                    staff.getName().getString(), staff.getUUID().toString(), text);
            if (newId < 0) {
                staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[NOTE] Note limit reached for " + target + "."));
                return;
            }
            GuiFeedback.report(staff, "Note #" + newId + " added for §e" + target,
                    List.of("§a+ §7" + text), false);
        });
    }
}