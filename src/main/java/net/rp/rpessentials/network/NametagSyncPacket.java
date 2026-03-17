package net.rp.rpessentials.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Packet Server → Client envoyé à chaque joueur lors de sa connexion (et en broadcast
 * quand un joueur rejoint/quitte, pour maintenir la liste à jour).
 *
 * Contient :
 *   - Toute la config du système nametag avancé
 *   - La liste des joueurs en ligne avec leurs données (pseudo, prefix, isStaff)
 *   - Un flag indiquant si le destinataire est lui-même staff
 */
public record NametagSyncPacket(
        // ── Config globale ────────────────────────────────────────────────────────
        boolean advancedEnabled,       // master switch
        String  format,                // "$prefix$name", "$prefix $name", etc.
        int     obfuscationDistance,   // en blocs, même logique que le TabList
        int     renderDistance,        // distance max d'affichage (0 = illimité)
        boolean hideBehindBlocks,      // raycast occlusion
        boolean showWhileSneaking,     // garder le nametag si le joueur sneak
        boolean staffAlwaysSeeReal,    // staff bypass l'obfuscation
        boolean obfuscationEnabled,    // activer/désactiver l'obfuscation seule

        // ── Données du destinataire ───────────────────────────────────────────────
        boolean viewerIsStaff,         // le joueur qui reçoit ce packet est-il staff ?

        // ── Liste des joueurs en ligne ────────────────────────────────────────────
        List<PlayerEntry> players      // un entry par joueur en ligne (soi-même exclu)
) implements CustomPacketPayload {

    // ── Entry d'un joueur ─────────────────────────────────────────────────────────
    public record PlayerEntry(
            UUID   uuid,
            String realName,       // nom MC réel
            String displayName,    // nickname ou nom réel si pas de nick
            String prefix,         // préfixe LuckPerms (vide si absent)
            boolean isStaff
    ) {}

    // ── Identifiant du packet ──────────────────────────────────────────────────────
    public static final CustomPacketPayload.Type<NametagSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "nametag_sync"));

    // ── Codec FriendlyByteBuf (compatible RegistryFriendlyByteBuf en playToClient) ──
    public static final StreamCodec<FriendlyByteBuf, NametagSyncPacket> STREAM_CODEC =
            StreamCodec.of(NametagSyncPacket::encode, NametagSyncPacket::decode);

    // ── Encodage ───────────────────────────────────────────────────────────────────
    private static void encode(FriendlyByteBuf buf, NametagSyncPacket p) {
        buf.writeBoolean(p.advancedEnabled());
        buf.writeUtf(p.format());
        buf.writeVarInt(p.obfuscationDistance());
        buf.writeVarInt(p.renderDistance());
        buf.writeBoolean(p.hideBehindBlocks());
        buf.writeBoolean(p.showWhileSneaking());
        buf.writeBoolean(p.staffAlwaysSeeReal());
        buf.writeBoolean(p.obfuscationEnabled());
        buf.writeBoolean(p.viewerIsStaff());

        buf.writeVarInt(p.players().size());
        for (PlayerEntry e : p.players()) {
            buf.writeUUID(e.uuid());
            buf.writeUtf(e.realName());
            buf.writeUtf(e.displayName());
            buf.writeUtf(e.prefix());
            buf.writeBoolean(e.isStaff());
        }
    }

    // ── Décodage ───────────────────────────────────────────────────────────────────
    private static NametagSyncPacket decode(FriendlyByteBuf buf) {
        boolean advancedEnabled     = buf.readBoolean();
        String  format              = buf.readUtf(256);
        int     obfDist             = buf.readVarInt();
        int     renderDist          = buf.readVarInt();
        boolean hideBehindBlocks    = buf.readBoolean();
        boolean showWhileSneaking   = buf.readBoolean();
        boolean staffAlwaysSeeReal  = buf.readBoolean();
        boolean obfEnabled          = buf.readBoolean();
        boolean viewerIsStaff       = buf.readBoolean();

        int count = buf.readVarInt();
        List<PlayerEntry> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID    uuid        = buf.readUUID();
            String  realName    = buf.readUtf(64);
            String  displayName = buf.readUtf(128);
            String  prefix      = buf.readUtf(128);
            boolean isStaff     = buf.readBoolean();
            players.add(new PlayerEntry(uuid, realName, displayName, prefix, isStaff));
        }

        return new NametagSyncPacket(advancedEnabled, format, obfDist, renderDist,
                hideBehindBlocks, showWhileSneaking, staffAlwaysSeeReal, obfEnabled,
                viewerIsStaff, players);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
