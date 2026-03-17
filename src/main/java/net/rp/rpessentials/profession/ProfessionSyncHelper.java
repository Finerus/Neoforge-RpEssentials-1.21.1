package net.rp.rpessentials.profession;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPatternUtils;
import net.rp.rpessentials.network.SyncProfessionRestrictionsPacket;
import net.rp.rpessentials.config.ProfessionConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilitaire pour synchroniser les restrictions de métiers avec le client
 */
public class ProfessionSyncHelper {

    /**
     * Envoie les restrictions au joueur lors de sa connexion
     * Appelé dans OneriaEventHandler.onPlayerLogin()
     */
    public static void syncToPlayer(ServerPlayer player) {
        List<String> playerLicenses = LicenseManager.getLicenses(player.getUUID());

        Set<String> blockedCrafts = calculateBlockedCrafts(playerLicenses);
        Set<String> blockedEquipment = calculateBlockedEquipment(playerLicenses);

        SyncProfessionRestrictionsPacket packet = new SyncProfessionRestrictionsPacket(blockedCrafts, blockedEquipment);
        PacketDistributor.sendToPlayer(player, packet);

        RpEssentials.LOGGER.info("[ProfessionSync] Sent restrictions to {} - {} crafts, {} equipment blocked",
                player.getName().getString(), blockedCrafts.size(), blockedEquipment.size());
    }

    private static Set<String> calculateBlockedCrafts(List<String> playerLicenses) {
        Set<String> blocked = new HashSet<>();
        for (String itemPattern : ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get()) {
            if (!hasPermissionForPattern(playerLicenses, itemPattern, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get())) {
                blocked.add(itemPattern);
            }
        }
        return blocked;
    }

    private static Set<String> calculateBlockedEquipment(List<String> playerLicenses) {
        Set<String> blocked = new HashSet<>();
        for (String itemPattern : ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get()) {
            if (!hasPermissionForPattern(playerLicenses, itemPattern, ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get())) {
                blocked.add(itemPattern);
            }
        }
        return blocked;
    }

    /**
     * Vérifie si le joueur a la permission pour un pattern via ses licences.
     * Utilise OneriaPatternUtils pour le matching — plus de duplication.
     */
    private static boolean hasPermissionForPattern(List<String> licenses, String pattern, List<? extends String> allowedList) {
        for (String license : licenses) {
            for (String allowEntry : allowedList) {
                if (!allowEntry.contains(";")) continue;

                String[] parts = allowEntry.split(";", 2);
                String professionId = parts[0].toLowerCase().trim();
                String allowedItems = parts[1];

                if (!professionId.equals(license.toLowerCase())) continue;

                for (String allowedItem : allowedItems.split(",")) {
                    if (RpEssentialsPatternUtils.matchesPattern(pattern, allowedItem.trim())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}