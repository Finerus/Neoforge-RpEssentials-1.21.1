package net.rp.rpessentials.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.TimeHelper;
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
    private static LocalDate lastSweepDate = null;

    public static void checkOnLogin(ServerPlayer player, MinecraftServer server) {
        if (server == null) return;
        UUID uuid = player.getUUID();
        LocalDate today = TimeHelper.today();
        List<LicenseManager.TempLicenseEntry> toExpire = new ArrayList<>();
        for (LicenseManager.TempLicenseEntry e : LicenseManager.getAllTempLicenses()) {
            if (!e.targetUUID.equals(uuid.toString())) continue;
            LocalDate exp = parse(e.expiresAt);
            if (exp != null && !today.isBefore(exp)) toExpire.add(e);
        }
        for (LicenseManager.TempLicenseEntry e : toExpire)
            revoke(server, uuid, e, player);
    }

    /**
     * Scans the player's inventory for license items whose profession is no longer active
     * and marks them as revoked by setting the "revoked" flag in CUSTOM_DATA.
     *
     * The visual revoke message (title + body) is displayed exclusively by
     * LicenseItem.appendHoverText() which reads the "revoked" flag at render time.
     * We deliberately do NOT add lore lines here to avoid duplicating what
     * appendHoverText already shows.
     */
    public static void markRevokedLicenseItems(ServerPlayer player) {
        List<String> activeLicenses = LicenseManager.getLicenses(player.getUUID());

        // inventaire vanilla
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            markStackIfRevoked(player, player.getInventory().getItem(i), activeLicenses);
        }

        // slots Curios (optionnel)
        try {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.getCurios().forEach((slotId, stacksHandler) -> {
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            markStackIfRevoked(player, stacksHandler.getStacks().getStackInSlot(i), activeLicenses);
                        }
                    }));
        } catch (NoClassDefFoundError ignored) {}
    }

    private static void markStackIfRevoked(ServerPlayer player, net.minecraft.world.item.ItemStack stack,
                                           List<String> activeLicenses) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LicenseItem)) return;
        //#if forge
        //? if (!stack.hasTag()) return;
        //#else
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return;
        //#endif

        net.minecraft.nbt.CompoundTag tag =
                //#if forge
                //? stack.getTag().copy();
                //#else
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
                //#endif
        String profId = tag.getString("professionId");
        if (profId.isEmpty()) return;

        String holderUuid = tag.getString("holderUuid");
        boolean owner;
        if (!holderUuid.isEmpty()) {
            owner = holderUuid.equals(player.getUUID().toString());
        } else {
            String holder = tag.getString("holderName");
            owner = holder.equals(net.rp.rpessentials.identity.NicknameManager.getDisplayName(player))
                    || holder.equals(player.getGameProfile().getName());
        }
        if (!owner) return;

        boolean isActive = activeLicenses.stream().anyMatch(l -> l.equalsIgnoreCase(profId));
        boolean alreadyRevoked = tag.getBoolean("revoked");

        if (!isActive && !alreadyRevoked) {
            tag.putBoolean("revoked", true);
        } else if (isActive && alreadyRevoked) {
            tag.remove("revoked");
        } else {
            return;
        }
        //#if forge
        //? stack.setTag(tag);
        //#else
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        //#endif
    }

    public static void tickMidnightSweep(MinecraftServer server) {
        LocalDate today = TimeHelper.today();
        if (today.equals(lastSweepDate)) return;
        lastSweepDate = today;
        RpEssentials.LOGGER.info("[TempLicenseExpiration] Running daily sweep...");
        LocalDate now = TimeHelper.today();
        for (LicenseManager.TempLicenseEntry e : new ArrayList<>(LicenseManager.getAllTempLicenses())) {
            LocalDate exp = parse(e.expiresAt);
            if (exp == null || now.isBefore(exp)) continue;
            try {
                UUID uuid = UUID.fromString(e.targetUUID);
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                // Offline players are handled by checkOnLogin, where their tag can be removed too.
                if (online == null) continue;
                revoke(server, uuid, e, online);
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
            if (!ProfessionRestrictionManager.isReservedTag(e.profession)) online.removeTag(e.profession);
            ProfessionSyncHelper.syncToPlayer(online);
            ProfessionRestrictionManager.ProfessionData d =
                    ProfessionRestrictionManager.getProfessionData(e.profession);
            String name = d != null ? d.getFormattedName() : e.profession;
            online.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseSection(
                    ServerLang.get(online, MessagesConfig.LICENSE_EXPIRED_RP_PLAYER, "profession", name)));
        }
        LicenseManager.logActionSystem("TEMPEXPIRE", e.targetName, e.targetUUID, e.profession, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_EXPIRED_ON, "date", String.valueOf(e.expiresAt)));
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