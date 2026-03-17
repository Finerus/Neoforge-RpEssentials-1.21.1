package net.rp.rpessentials.client;

import net.rp.rpessentials.network.NametagSyncPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage client-side de toute la configuration du système nametag.
 * Mis à jour via {@link NametagSyncPacket} reçu du serveur.
 * Remis à zéro à la déconnexion via {@link net.rp.rpessentials.ClientEventHandler}.
 */
public class ClientNametagConfig {

    // ── État de réception ─────────────────────────────────────────────────────────
    private static boolean hasReceivedConfig = false;

    // ── Config legacy (compatibilité HideNametagsPacket) ─────────────────────────
    private static boolean hideNametags = false;

    // ── Config système avancé ─────────────────────────────────────────────────────
    private static boolean advancedEnabled      = false;
    private static String  format               = "$prefix$name";
    private static int     obfuscationDistance  = 8;
    private static int     renderDistance       = 64;
    private static boolean hideBehindBlocks     = true;
    private static boolean showWhileSneaking    = false;
    private static boolean staffAlwaysSeeReal   = true;
    private static boolean obfuscationEnabled   = true;
    private static boolean viewerIsStaff        = false;

    // ── Cache des données par joueur ──────────────────────────────────────────────
    private static final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    /** Données nametag d'un joueur reçues du serveur. */
    public record PlayerData(
            String  realName,
            String  displayName,   // nickname ou realName
            String  prefix,        // LuckPerms prefix, vide si absent
            boolean isStaff
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════════
    // APPLICATION DU PACKET
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Applique un {@link NametagSyncPacket} reçu du serveur. */
    public static void applySync(NametagSyncPacket packet) {
        hasReceivedConfig       = true;
        advancedEnabled         = packet.advancedEnabled();
        format                  = packet.format();
        obfuscationDistance     = packet.obfuscationDistance();
        renderDistance          = packet.renderDistance();
        hideBehindBlocks        = packet.hideBehindBlocks();
        showWhileSneaking       = packet.showWhileSneaking();
        staffAlwaysSeeReal      = packet.staffAlwaysSeeReal();
        obfuscationEnabled      = packet.obfuscationEnabled();
        viewerIsStaff           = packet.viewerIsStaff();

        playerDataMap.clear();
        for (NametagSyncPacket.PlayerEntry e : packet.players()) {
            playerDataMap.put(e.uuid(),
                    new PlayerData(e.realName(), e.displayName(), e.prefix(), e.isStaff()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ACCESSEURS — CONFIG LEGACY
    // ═══════════════════════════════════════════════════════════════════════════════

    public static void setHideNametags(boolean hide) {
        hideNametags      = hide;
        hasReceivedConfig = true;
    }

    public static boolean shouldHideNametags()    { return hideNametags; }
    public static boolean hasReceivedServerConfig() { return hasReceivedConfig; }

    // Pour compatibilité avec l'ancien nom dans ClientNametagRenderer
    public static boolean hasReceivedConfig()     { return hasReceivedConfig; }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ACCESSEURS — SYSTÈME AVANCÉ
    // ═══════════════════════════════════════════════════════════════════════════════

    public static boolean isAdvancedEnabled()     { return advancedEnabled; }
    public static String  getFormat()             { return format; }
    public static int     getObfuscationDistance(){ return obfuscationDistance; }
    public static int     getRenderDistance()     { return renderDistance; }
    public static boolean isHideBehindBlocks()    { return hideBehindBlocks; }
    public static boolean isShowWhileSneaking()   { return showWhileSneaking; }
    public static boolean isStaffAlwaysSeeReal()  { return staffAlwaysSeeReal; }
    public static boolean isObfuscationEnabled()  { return obfuscationEnabled; }
    public static boolean isViewerStaff()         { return viewerIsStaff; }

    /** Retourne les données nametag d'un joueur, ou {@code null} si non reçues. */
    public static PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RESET (déconnexion)
    // ═══════════════════════════════════════════════════════════════════════════════

    public static void reset() {
        hasReceivedConfig    = false;
        hideNametags         = false;
        advancedEnabled      = false;
        format               = "$prefix$name";
        obfuscationDistance  = 8;
        renderDistance       = 64;
        hideBehindBlocks     = true;
        showWhileSneaking    = false;
        staffAlwaysSeeReal   = true;
        obfuscationEnabled   = true;
        viewerIsStaff        = false;
        playerDataMap.clear();
    }
}
