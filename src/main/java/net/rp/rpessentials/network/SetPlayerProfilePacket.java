
package net.rp.rpessentials.network;

import net.rp.rpessentials.config.Cfg;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.profession.*;

import java.util.List;
import java.util.UUID;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.TimeHelper;

/**
 * Packet CLIENT → SERVEUR
 * Applique en une seule fois : nickname, rôle et licence principale d'un joueur.
 *
 * Quand revokeMode = true, licenseId est révoqué au lieu d'être donné.
 */
public record SetPlayerProfilePacket(
        UUID   targetUuid,
        String nickname,      // "" = ne pas modifier
        String role,          // "" = ne pas modifier
        String licenseId,     // "" = ne pas modifier
        boolean revokeMode,   // true = révoquer licenseId, false = donner licenseId
        boolean resetNickname, // true = réinitialise le nickname, prioritaire sur nickname
        int tempDays          // 0 = permanente
) implements CustomPacketPayload {

    public static final Type<SetPlayerProfilePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, "set_player_profile"));

    public static final StreamCodec<FriendlyByteBuf, SetPlayerProfilePacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SetPlayerProfilePacket decode(FriendlyByteBuf buf) {
                    return new SetPlayerProfilePacket(
                            buf.readUUID(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readUtf(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt()
                    );
                }
                @Override
                public void encode(FriendlyByteBuf buf, SetPlayerProfilePacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeUtf(p.nickname());
                    buf.writeUtf(p.role());
                    buf.writeUtf(p.licenseId());
                    buf.writeBoolean(p.revokeMode());
                    buf.writeBoolean(p.resetNickname());
                    buf.writeVarInt(p.tempDays());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // =========================================================================
    // HANDLER — côté SERVEUR
    // =========================================================================
    public static void handleOnServer(SetPlayerProfilePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer admin)) return;
            if (!RpEssentialsPermissions.isStaff(admin)) return;

            MinecraftServer server = admin.getServer();
            ServerPlayer target = server.getPlayerList().getPlayer(packet.targetUuid());

            if (target == null) {
                handleOffline(packet, admin);
                return;
            }

            String targetName = target.getGameProfile().getName();

            if (packet.revokeMode()) {
                // ── REVOKE ────────────────────────────────────────────────────
                handleRevoke(packet, admin, server, target, targetName);
            } else {
                // ── GIVE / UPDATE ─────────────────────────────────────────────
                handleGive(packet, admin, server, target, targetName);
            }
        });
    }

    // ── Offline path (joueur préparé mais jamais connecté) ─────────────────────
    private static void handleOffline(SetPlayerProfilePacket packet, ServerPlayer admin) {
        if (!net.rp.rpessentials.profession.PendingProfileManager.hasPending(packet.targetUuid())) {
            admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_PROFILE_OFFLINE)));
            return;
        }

        if (packet.resetNickname()) {
            net.rp.rpessentials.profession.PendingProfileManager.setNickname(packet.targetUuid(), "");
        } else if (!packet.nickname().trim().isEmpty()) {
            String nick = packet.nickname().trim().replace("&", "§");
            String err = NicknameManager.rejection(packet.targetUuid(), nick, true, admin);
            if (err != null) admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(err));
            else net.rp.rpessentials.profession.PendingProfileManager.setNickname(packet.targetUuid(), nick);
        }

        if (!packet.role().trim().isEmpty() && RpEssentialsPermissions.canManageRoles(admin)) {
            PendingProfileManager.setRole(packet.targetUuid(), packet.role().trim());
        }

        String licenseId = packet.licenseId().trim();
        if (!licenseId.isEmpty()) {
            if (packet.revokeMode()) net.rp.rpessentials.profession.PendingProfileManager.removeLicense(packet.targetUuid(), licenseId);
            else if (ProfessionRestrictionManager.getProfessionData(licenseId) == null)
                admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_UNKNOWN_PROFESSION,
                        "profession", licenseId)));
            else net.rp.rpessentials.profession.PendingProfileManager.addLicense(packet.targetUuid(), licenseId);
        }

        String name = net.rp.rpessentials.profession.PendingProfileManager.getMcName(packet.targetUuid());
        admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_PREPARED_UPDATED, "player", name)));
    }

    // ── Revoke path ────────────────────────────────────────────────────────────

    private static void handleRevoke(SetPlayerProfilePacket packet,
                                     ServerPlayer admin, MinecraftServer server,
                                     ServerPlayer target, String targetName) {
        String licenseId = packet.licenseId().trim();
        if (licenseId.isEmpty()) return;

        // Verify the player actually has this license
        List<String> existing = LicenseManager.getLicenses(target.getUUID());
        if (!existing.contains(licenseId)) {
            admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_NO_LICENSE,
                    "player", targetName, "license", licenseId)));
            return;
        }

        LicenseManager.removeLicense(target.getUUID(), licenseId);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        LicenseManager.logAction("REVOKE", admin, target, licenseId, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_VIA_GUI));
        TempLicenseExpirationManager.markRevokedLicenseItems(target);

        // Remove vanilla tag
        if (!ProfessionRestrictionManager.isReservedTag(licenseId)) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "tag " + targetName + " remove " + licenseId);
        }

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(licenseId);
        String profDisplay = profData != null ? profData.getFormattedName() : licenseId;

        admin.sendSystemMessage(Component.literal(
                ServerLang.get(admin, MessagesConfig.LICENSE_REVOKE_STAFF,
                        "profession", profDisplay, "player", targetName)));
        target.sendSystemMessage(Component.literal(
                ServerLang.get(target, MessagesConfig.LICENSE_REVOKE_PLAYER,
                        "profession", profDisplay)));

        RpEssentials.LOGGER.info("[GUI] License '{}' revoked for {} by {}",
                licenseId, targetName, admin.getGameProfile().getName());
    }

    // ── Give / update path ─────────────────────────────────────────────────────

    private static void handleGive(SetPlayerProfilePacket packet,
                                   ServerPlayer admin, MinecraftServer server,
                                   ServerPlayer target, String targetName) {
        StringBuilder report = new StringBuilder(
                ServerLang.get(admin, MessagesConfig.GUI_PROFILE_UPDATED, "player", targetName));

        // ── 1. Nickname ───────────────────────────────────────────────────────
        if (packet.resetNickname()) {
            NicknameManager.removeNickname(target.getUUID());
            report.append(ServerLang.get(admin, MessagesConfig.GUI_REPORT_NICK_RESET));
        } else {
            String nick = packet.nickname().trim().replace("&", "§");
            if (!nick.isEmpty()) {
                // Same checks as /nick (unique nickname...).
                String err = NicknameManager.rejection(target.getUUID(), nick, true, admin);
                if (err != null) {
                    admin.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(err));
                } else {
                    NicknameManager.setNickname(target.getUUID(), nick);
                    report.append(ServerLang.get(admin, MessagesConfig.GUI_REPORT_NICK, "nick", nick));
                }
            }
        }

        // ── 2. Rôle ───────────────────────────────────────────────────────────
        String role = packet.role().trim();
        if (!role.isEmpty()) {
            if (!RpEssentialsPermissions.canManageRoles(admin)) {
                admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_ROLES_NEED_LEVEL)));
            } else if (applyRole(server, target, role)) {
                report.append(ServerLang.get(admin, MessagesConfig.GUI_REPORT_ROLE, "role", role));
            } else {
                admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_UNKNOWN_ROLE, "role", role)));
            }
        }

        // ── 3. Licence ────────────────────────────────────────────────────────
        String licenseId = packet.licenseId().trim();
        if (!licenseId.isEmpty()) {
            List<String> existing = LicenseManager.getLicenses(target.getUUID());

            if (existing.contains(licenseId)) {
                admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_ALREADY_LICENSE,
                        "player", targetName, "license", licenseId)));
            } else {
                ProfessionRestrictionManager.ProfessionData profData =
                        ProfessionRestrictionManager.getProfessionData(licenseId);
                if (profData == null) {
                    admin.sendSystemMessage(Component.literal(ServerLang.get(admin, MessagesConfig.GUI_UNKNOWN_PROFESSION,
                            "profession", licenseId)));
                } else {
                    int days = Math.max(0, Math.min(365, packet.tempDays()));
                    String expires = null;
                    if (days > 0) {
                        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        String issued = TimeHelper.today().format(fmt);
                        expires = TimeHelper.today().plusDays(days).format(fmt);
                        LicenseManager.addLicense(target.getUUID(), licenseId);
                        LicenseManager.addTempLicense(admin, target, licenseId, days, issued, expires);
                        LicenseManager.logAction("TEMPGIVE", admin, target, licenseId,
                                net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_TEMP_GUI, "days", String.valueOf(days), "date", String.valueOf(expires)));
                    } else {
                        LicenseManager.addLicense(target.getUUID(), licenseId);
                        LicenseManager.logAction("GIVE", admin, target, licenseId, net.rp.rpessentials.lang.ServerLang.get(null, net.rp.rpessentials.config.MessagesConfig.AUDIT_VIA_GUI));
                    }
                    LicenseHelper.giveLicenseItem(server, admin, target, licenseId, expires);

                    target.sendSystemMessage(Component.literal(days > 0
                            ? ServerLang.get(target, MessagesConfig.LICENSE_GIVE_RP_PLAYER,
                            "profession", profData.getFormattedName(), "date", expires)
                            : ServerLang.get(target, MessagesConfig.LICENSE_GIVE_PLAYER,
                            "profession", profData.getFormattedName())));
                    report.append(days > 0 ? ServerLang.get(admin, MessagesConfig.GUI_REPORT_LICENSE_DAYS, "license", licenseId, "days", String.valueOf(days)) : ServerLang.get(admin, MessagesConfig.GUI_REPORT_LICENSE, "license", licenseId));
                }
            }
        }

        admin.sendSystemMessage(ColorHelper.parseSection(report.toString()));
        RpEssentials.LOGGER.info("[GUI] Profile applied for {} by {}: nick='{}', role='{}', license='{}'",
                targetName, admin.getGameProfile().getName(),
                packet.nickname(), packet.role(), packet.licenseId());
    }

    // ── Role helper ────────────────────────────────────────────────────────────
    public static boolean applyRole(MinecraftServer server, ServerPlayer target, String roleId) {
        var silentSource = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        String targetName = target.getName().getString();
        if (!Cfg.ready(RpEssentialsConfig.SPEC)) {
            RpEssentials.LOGGER.warn("[GUI] ROLES config unavailable when applying role '{}'", roleId);
            return false;
        }
        try {
            String lpGroup = null;
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 3);
                if (parts[0].trim().equalsIgnoreCase(roleId)) {
                    lpGroup = parts.length >= 2 && !parts[1].isBlank() ? parts[1].trim() : parts[0].trim();
                    break;
                }
            }
            if (lpGroup == null) return false;

            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String oldTag = entry.split(";", 2)[0].trim();
                server.getCommands().performPrefixedCommand(silentSource, "tag " + targetName + " remove " + oldTag);
            }
            server.getCommands().performPrefixedCommand(silentSource, "tag " + targetName + " add " + roleId);
            server.getCommands().performPrefixedCommand(silentSource, "lp user " + targetName + " parent set " + lpGroup);

            RpEssentialsRoleManager.invalidate(target.getUUID());
            RpEssentialsPermissions.invalidateCache(target.getUUID());
            ProfessionSyncHelper.syncToPlayer(target);
            SyncNametagDataPacket.broadcastDelayed(target);
            RpEssentialsScheduleManager.enforceOnlineDelayed(server);
            return true;
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[GUI] Error applying role '{}' to {}: {}", roleId, targetName, e.getMessage());
        }
        return false;
    }
}