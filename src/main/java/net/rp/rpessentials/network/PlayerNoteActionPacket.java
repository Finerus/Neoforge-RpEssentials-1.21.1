package net.rp.rpessentials.network;

import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.lang.ServerLang;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.util.GuiFeedback;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
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
                    staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(ServerLang.get(staff,
                            MessagesConfig.GUI_NOTE_NOT_FOUND, "id", String.valueOf(packet.noteId()), "player", target)));
                    return;
                }
                net.rp.rpessentials.moderation.HistoryManager.record(packet.targetUuid(), target,
                        net.rp.rpessentials.moderation.HistoryManager.NOTE_REMOVE, staff.getName().getString(), "#" + packet.noteId());
                final NoteManager.NoteEntry deleted = old;
                GuiFeedback.report(staff, MessagesConfig.GUI_NOTE_DELETED,
                        new String[]{"id", String.valueOf(packet.noteId()), "player", target},
                        v -> List.of("§c- §7" + deleted.text,
                                ServerLang.get(v, MessagesConfig.GUI_NOTE_AUTHOR, "author", deleted.displayAuthor())), false);
                return;
            }

            String text = packet.text().trim();
            if (text.isEmpty()) return;
            if (text.length() > 256) text = text.substring(0, 256);

            if (packet.noteId() > 0) {
                if (old == null || !NoteManager.editNote(packet.targetUuid(), packet.noteId(), text, staff.getName().getString())) {
                    staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(ServerLang.get(staff,
                            MessagesConfig.GUI_NOTE_NOT_FOUND, "id", String.valueOf(packet.noteId()), "player", target)));
                    return;
                }
                final NoteManager.NoteEntry edited = old;
                final String newText = text;
                GuiFeedback.report(staff, MessagesConfig.GUI_NOTE_EDITED,
                        new String[]{"id", String.valueOf(packet.noteId()), "player", target},
                        v -> List.of("§c- §7" + edited.text, "§a+ §7" + newText,
                                ServerLang.get(v, MessagesConfig.GUI_NOTE_AUTHOR, "author", edited.authorName)), false);
                return;
            }

            int newId = NoteManager.addNote(packet.targetUuid(), target,
                    staff.getName().getString(), staff.getUUID().toString(), text);
            if (newId < 0) {
                staff.sendSystemMessage(net.minecraft.network.chat.Component.literal(ServerLang.get(staff,
                        MessagesConfig.GUI_NOTE_LIMIT, "player", target)));
                return;
            }
            final String addedText = text;
            GuiFeedback.report(staff, MessagesConfig.GUI_NOTE_ADDED,
                    new String[]{"id", String.valueOf(newId), "player", target},
                    v -> List.of("§a+ §7" + addedText), false);
        });
    }
}