package net.rp.rpessentials.profession;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.items.LicenseItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TempLicenseExpirationManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static boolean hasDoneSweepToday = false;
    private static int lastSweepDay = -1;

    public static void checkOnLogin(ServerPlayer player, MinecraftServer server) {
        if (server == null) return;
        UUID uuid = player.getUUID();
        LocalDate today = LocalDate.now();
        List<LicenseManager.TempLicenseEntry> toExpire = new ArrayList<>();
        for (LicenseManager.TempLicenseEntry e : LicenseManager.getAllTempLicenses()) {
            if (!e.targetUUID.equals(uuid.toString())) continue;
            LocalDate exp = parse(e.expiresAt);
            if (exp != null && !today.isBefore(exp)) toExpire.add(e);
        }
        for (LicenseManager.TempLicenseEntry e : toExpire)
            revoke(server, uuid, e, player);
    }

    public static void markRevokedLicenseItems(ServerPlayer player) {
        List<String> activeLicenses = LicenseManager.getLicenses(player.getUUID());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof LicenseItem)) continue;
            if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) continue;

            net.minecraft.nbt.CompoundTag tag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
            String profId = tag.getString("professionId");
            if (profId.isEmpty()) continue;

            boolean isActive = activeLicenses.stream().anyMatch(l -> l.equalsIgnoreCase(profId));
            boolean alreadyRevoked = tag.getBoolean("revoked");

            if (!isActive && !alreadyRevoked) {
                tag.putBoolean("revoked", true);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(tag));

                List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
                var existing = stack.get(net.minecraft.core.component.DataComponents.LORE);
                if (existing != null) lore.addAll(existing.lines());
                lore.add(net.minecraft.network.chat.Component.literal(
                        MessagesConfig.get(
                                MessagesConfig.LICENSE_REVOKED_TITLE)));
                lore.add(net.minecraft.network.chat.Component.literal(
                        MessagesConfig.get(
                                MessagesConfig.LICENSE_REVOKED_BODY)));
                stack.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(lore));
            }
        }
    }

    public static void tickMidnightSweep(MinecraftServer server, int hour, int minute) {
        int today = LocalDate.now().getDayOfYear();
        if (today != lastSweepDay) { hasDoneSweepToday = false; lastSweepDay = today; }
        if (hasDoneSweepToday || hour != 0 || minute != 0) return;
        hasDoneSweepToday = true;
        RpEssentials.LOGGER.info("[TempLicenseExpiration] Running midnight sweep...");
        LocalDate now = LocalDate.now();
        for (LicenseManager.TempLicenseEntry e : new ArrayList<>(LicenseManager.getAllTempLicenses())) {
            LocalDate exp = parse(e.expiresAt);
            if (exp == null || now.isBefore(exp)) continue;
            try {
                UUID uuid = UUID.fromString(e.targetUUID);
                revoke(server, uuid, e, server.getPlayerList().getPlayer(uuid));
            } catch (IllegalArgumentException ex) {
                RpEssentials.LOGGER.warn("[TempLicenseExpiration] Invalid UUID: {}", e.targetUUID);
            }
        }
        RpEssentials.LOGGER.info("[TempLicenseExpiration] Midnight sweep done.");
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            markRevokedLicenseItems(online);
        }
    }

    private static void revoke(MinecraftServer server, UUID uuid,
                               LicenseManager.TempLicenseEntry e, ServerPlayer online) {
        LicenseManager.removeTempLicense(e);
        LicenseManager.removeLicense(uuid, e.profession);
        ProfessionRestrictionManager.invalidatePlayerCache(uuid);
        if (online != null) {
            ProfessionSyncHelper.syncToPlayer(online);
            ProfessionRestrictionManager.ProfessionData d =
                    ProfessionRestrictionManager.getProfessionData(e.profession);
            String name = d != null ? d.getFormattedName() : e.profession;
            online.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cVotre permis de " + name + "§c a expiré. L'item reste dans votre inventaire."));
        }
        LicenseManager.logActionSystem("EXPIRE_RP", e.targetName, e.targetUUID, e.profession, "Expire le " + e.expiresAt);
        RpEssentials.LOGGER.info("[TempLicenseExpiration] Expired '{}' for {}", e.profession, e.targetName);
    }

    private static LocalDate parse(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, FMT); }
        catch (DateTimeParseException e) {
            RpEssentials.LOGGER.warn("[TempLicenseExpiration] Bad date '{}'", s);
            return null;
        }
    }
}