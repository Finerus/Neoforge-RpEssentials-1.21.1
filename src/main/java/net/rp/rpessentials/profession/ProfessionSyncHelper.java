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
     */
    public static void syncToPlayer(ServerPlayer player) {
        ProfessionRestrictionManager.invalidatePlayerCache(player.getUUID());
        ProfessionRestrictionManager.enforceEquipment(player);

        if (ProfessionRestrictionManager.isExemptFromProfessionRestrictions(player)) {
            PacketDistributor.sendToPlayer(player,
                    new SyncProfessionRestrictionsPacket(new HashSet<>(), new HashSet<>()));
            return;
        }

        List<String> playerLicenses = LicenseManager.getLicenses(player.getUUID());
        Set<String> blockedCrafts = calculateBlockedCrafts(playerLicenses);
        Set<String> blockedEquipment = calculateBlockedEquipment(playerLicenses);
        PacketDistributor.sendToPlayer(player, new SyncProfessionRestrictionsPacket(blockedCrafts, blockedEquipment));
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
     */
    private static boolean hasPermissionForPattern(List<String> licenses, String pattern,
                                                   List<? extends String> allowedList) {
        for (String license : licenses) {
            for (String allowEntry : allowedList) {
                if (!allowEntry.contains(";")) continue;
                String[] parts = allowEntry.split(";", 2);
                if (!parts[0].trim().equalsIgnoreCase(license)) continue;
                for (String allowedItem : parts[1].split(",")) {
                    String a = allowedItem.trim();
                    if (RpEssentialsPatternUtils.matchesPattern(pattern, a)
                            || RpEssentialsPatternUtils.matchesPattern(a, pattern)) return true;
                }
            }
        }
        return false;
    }
}