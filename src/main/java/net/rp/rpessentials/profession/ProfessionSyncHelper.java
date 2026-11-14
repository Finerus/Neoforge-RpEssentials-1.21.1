package net.rp.rpessentials.profession;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;
import net.rp.rpessentials.network.SyncProfessionRestrictionsPacket;
import net.rp.rpessentials.config.ProfessionConfig;
import net.rp.rpessentials.network.SyncTooltipPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitaire pour synchroniser les restrictions de métiers avec le client
 */
public class ProfessionSyncHelper {

    /**
     * Envoie les restrictions au joueur lors de sa connexion
     */
    private record Computed(Set<String> crafts, Set<String> equipment) {}
    private static final Map<String, Computed> computed = new ConcurrentHashMap<>();

    public static void clearCache() {
        computed.clear();
    }

    private static String key(List<String> licenses) {
        List<String> l = new ArrayList<>(licenses);
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", l).toLowerCase();
    }

    private static List<String> copy(List<? extends String> l) {
        return new ArrayList<>(l);
    }

    private static void sendTooltip(ServerPlayer player, boolean exempt) {
        List<List<String>> blocked = new ArrayList<>();
        List<List<String>> allowed = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (Cfg.ready(ProfessionConfig.SPEC)) {
            blocked.add(copy(ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get()));
            blocked.add(copy(ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.get()));
            blocked.add(copy(ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get()));
            blocked.add(copy(ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get()));
            blocked.add(copy(ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get()));
            allowed.add(copy(ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get()));
            allowed.add(copy(ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get()));
            allowed.add(copy(ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get()));
            allowed.add(copy(ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get()));
            for (ProfessionRestrictionManager.ProfessionData d : ProfessionRestrictionManager.getAllProfessions()) {
                names.add(d.id + ";" + d.getFormattedName());
            }
        } else {
            blocked.clear();
            allowed.clear();
            names.clear();
        }
        PacketDistributor.sendToPlayer(player, new SyncTooltipPacket(blocked, allowed, names,
                exempt ? List.of("@exempt") : LicenseManager.getLicenses(player.getUUID()),
                net.rp.rpessentials.gameplay.WorldBorderManager.zoneDefs(), licenseIdMode()));
    }

    private static String licenseIdMode() {
        return Cfg.get(ProfessionConfig.LICENSE_ID_DISPLAY, "NAME_AND_ID");
    }

    public static void syncToPlayer(ServerPlayer player) {
        ProfessionRestrictionManager.invalidatePlayerCache(player.getUUID());
        ProfessionRestrictionManager.enforceEquipment(player);

        if (ProfessionRestrictionManager.isExemptFromProfessionRestrictions(player)) {
            PacketDistributor.sendToPlayer(player,
                    new SyncProfessionRestrictionsPacket(new HashSet<>(), new HashSet<>()));
            sendTooltip(player, true);
            return;
        }

        List<String> playerLicenses = LicenseManager.getLicenses(player.getUUID());
        Computed c = computed.computeIfAbsent(key(playerLicenses), k -> new Computed(
                Set.copyOf(calculateBlockedCrafts(playerLicenses)),
                Set.copyOf(calculateBlockedEquipment(playerLicenses))));
        PacketDistributor.sendToPlayer(player, new SyncProfessionRestrictionsPacket(c.crafts(), c.equipment()));
        sendTooltip(player, false);
    }

    private static Set<String> calculateBlockedCrafts(List<String> playerLicenses) {
        Set<String> blocked = new HashSet<>();
        for (String itemPattern : ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get()) {
            if (itemPattern.indexOf('|') >= 0) continue;
            if (!hasPermissionForPattern(playerLicenses, itemPattern, ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get())) {
                blocked.add(itemPattern);
            }
        }
        return blocked;
    }

    private static Set<String> calculateBlockedEquipment(List<String> playerLicenses) {
        Set<String> blocked = new HashSet<>();
        for (String itemPattern : ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get()) {
            if (itemPattern.indexOf('|') >= 0) continue;
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