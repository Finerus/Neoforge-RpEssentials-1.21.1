package net.rp.rpessentials;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.network.NametagSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire pour construire et envoyer des {@link NametagSyncPacket}.
 *
 * Appelé :
 *   - À la connexion d'un joueur (envoi à lui + broadcast à tous)
 *   - Sur /oneria config reload
 *   - Potentiellement sur /oneria nick (si le pseudo d'un joueur change)
 */
public class NametagSyncHelper {

    /**
     * Construit le packet personnalisé pour {@code recipient} et l'envoie.
     * Le packet contient la liste de TOUS les autres joueurs en ligne.
     *
     * @param recipient Le joueur qui reçoit le packet.
     * @param server    Le serveur (pour itérer sur la liste des joueurs).
     */
    public static void sendTo(ServerPlayer recipient, MinecraftServer server) {
        NametagSyncPacket packet = buildFor(recipient, server);
        PacketDistributor.sendToPlayer(recipient, packet);
    }

    /**
     * Envoie un sync personnalisé à TOUS les joueurs en ligne.
     * Utile quand la config change (reload) ou quand un joueur rejoint/quitte
     * (pour mettre à jour la liste chez tout le monde).
     *
     * @param server Le serveur.
     */
    public static void broadcastToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player, server);
        }
    }

    // ── Construction du packet ────────────────────────────────────────────────────

    private static NametagSyncPacket buildFor(ServerPlayer recipient, MinecraftServer server) {
        boolean advancedEnabled, hideBehindBlocks, showWhileSneaking, staffAlwaysSeeReal, obfEnabled;
        String  format;
        int     obfDist, renderDist;

        // Lecture des configs (protection contre IllegalStateException au démarrage)
        try {
            advancedEnabled    = RpEssentialsConfig.NAMETAG_ADVANCED_ENABLED.get();
            format             = RpEssentialsConfig.NAMETAG_FORMAT.get();
            obfDist            = RpEssentialsConfig.NAMETAG_OBFUSCATION_DISTANCE.get();
            renderDist         = RpEssentialsConfig.NAMETAG_RENDER_DISTANCE.get();
            hideBehindBlocks   = RpEssentialsConfig.NAMETAG_HIDE_BEHIND_BLOCKS.get();
            showWhileSneaking  = RpEssentialsConfig.NAMETAG_SHOW_WHILE_SNEAKING.get();
            staffAlwaysSeeReal = RpEssentialsConfig.NAMETAG_STAFF_ALWAYS_SEE_REAL.get();
            obfEnabled         = RpEssentialsConfig.NAMETAG_OBFUSCATION_ENABLED.get();
        } catch (IllegalStateException | NullPointerException e) {
            // Config pas encore construite → on envoie un packet "désactivé"
            RpEssentials.LOGGER.debug("[NametagSync] Config not ready, sending disabled packet");
            return new NametagSyncPacket(false, "$prefix$name", 8, 64,
                    true, false, true, true, false, List.of());
        }

        boolean viewerIsStaff = RpEssentialsPermissions.isStaff(recipient);

        // Construction de la liste des autres joueurs
        List<NametagSyncPacket.PlayerEntry> entries = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(recipient.getUUID())) continue; // pas soi-même

            String realName    = p.getGameProfile().getName();
            String displayName = resolveDisplayName(p, realName);
            String prefix      = RpEssentials.getPlayerPrefix(p); // méthode existante dans RpEssentials
            boolean isStaff    = RpEssentialsPermissions.isStaff(p);

            entries.add(new NametagSyncPacket.PlayerEntry(
                    p.getUUID(), realName, displayName, prefix, isStaff));
        }

        return new NametagSyncPacket(
                advancedEnabled, format, obfDist, renderDist,
                hideBehindBlocks, showWhileSneaking, staffAlwaysSeeReal, obfEnabled,
                viewerIsStaff, entries);
    }

    /** Retourne le nickname s'il existe, sinon le nom réel. */
    private static String resolveDisplayName(ServerPlayer player, String realName) {
        try {
            if (NicknameManager.hasNickname(player.getUUID())) {
                String nick = NicknameManager.getNickname(player.getUUID());
                if (nick != null && !nick.isBlank()) return nick;
            }
        } catch (Exception ignored) {}
        return realName;
    }
}
