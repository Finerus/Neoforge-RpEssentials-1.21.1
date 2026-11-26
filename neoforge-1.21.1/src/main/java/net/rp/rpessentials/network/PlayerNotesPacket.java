package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.moderation.NoteManager;
import net.rp.rpessentials.network.OpenPlayerProfileGuiPacket.PlayerData.NoteEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: notes of a player after a note was added, edited or deleted from the profile GUI
 * (real numbers, author and date), so the Notes tab is up to date without refreshing the whole screen.
 */
public record PlayerNotesPacket(UUID targetUuid, List<NoteEntry> notes) implements CustomPacketPayload {

    public static final Type<PlayerNotesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "player_notes"));

    public static final StreamCodec<FriendlyByteBuf, PlayerNotesPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerNotesPacket decode(FriendlyByteBuf buf) {
                    UUID id = buf.readUUID();
                    int n = PacketValidation.readCount(buf);
                    List<NoteEntry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new NoteEntry(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
                    }
                    return new PlayerNotesPacket(id, list);
                }

                @Override
                public void encode(FriendlyByteBuf buf, PlayerNotesPacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeVarInt(p.notes().size());
                    for (NoteEntry n : p.notes()) {
                        buf.writeVarInt(n.id());
                        buf.writeUtf(n.text());
                        buf.writeUtf(n.authorName());
                        buf.writeUtf(n.timestamp());
                    }
                }
            };

    /** Builds the packet for {@code target} from the stored notes. */
    public static PlayerNotesPacket of(UUID target) {
        List<NoteEntry> list = new ArrayList<>();
        for (NoteManager.NoteEntry n : NoteManager.getNotes(target)) {
            list.add(new NoteEntry(n.id, n.text, n.displayAuthor(), n.timestamp));
        }
        return new PlayerNotesPacket(target, list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleOnClient(PlayerNotesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            ClientGuiOpener.receiveNotes(packet.targetUuid(), packet.notes());
        });
    }
}
