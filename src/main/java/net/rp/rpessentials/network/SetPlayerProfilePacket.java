package net.rp.rpessentials.network;

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
import net.rp.rpessentials.profession.LicenseHelper;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;
import net.rp.rpessentials.profession.TempLicenseExpirationManager;

import java.util.List;
import java.util.UUID;

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
        boolean resetNickname // true = réinitialise le nickname, prioritaire sur nickname
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
                            buf.readBoolean()
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
            admin.sendSystemMessage(Component.literal(
                    "§c[RpEssentials] Player is no longer connected."));
            return;
        }

        if (packet.resetNickname()) {
            net.rp.rpessentials.profession.PendingProfileManager.setNickname(packet.targetUuid(), "");
        } else if (!packet.nickname().trim().isEmpty()) {
            net.rp.rpessentials.profession.PendingProfileManager.setNickname(packet.targetUuid(), packet.nickname().trim());
        }

        if (!packet.role().trim().isEmpty()) {
            net.rp.rpessentials.profession.PendingProfileManager.setRole(packet.targetUuid(), packet.role().trim());
        }

        String licenseId = packet.licenseId().trim();
        if (!licenseId.isEmpty()) {
            if (packet.revokeMode()) net.rp.rpessentials.profession.PendingProfileManager.removeLicense(packet.targetUuid(), licenseId);
            else net.rp.rpessentials.profession.PendingProfileManager.addLicense(packet.targetUuid(), licenseId);
        }

        String name = net.rp.rpessentials.profession.PendingProfileManager.getMcName(packet.targetUuid());
        admin.sendSystemMessage(Component.literal(
                "§a[RpEssentials] Prepared profile updated for §e" + name));
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
            admin.sendSystemMessage(Component.literal(
                    "§e[RpEssentials] §f" + targetName + " §edoes not have the §f" + licenseId + " §elicense."));
            return;
        }

        LicenseManager.removeLicense(target.getUUID(), licenseId);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        LicenseManager.logAction("REVOKE", admin, target, licenseId, "via GUI");
        TempLicenseExpirationManager.markRevokedLicenseItems(target);

        // Remove vanilla tag
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                "tag " + targetName + " remove " + licenseId);

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(licenseId);
        String profDisplay = profData != null ? profData.getFormattedName() : licenseId;

        admin.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_STAFF,
                        "profession", profDisplay, "player", targetName)));
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_PLAYER,
                        "profession", profDisplay)));

        RpEssentials.LOGGER.info("[GUI] License '{}' revoked for {} by {}",
                licenseId, targetName, admin.getGameProfile().getName());
    }

    // ── Give / update path ─────────────────────────────────────────────────────

    private static void handleGive(SetPlayerProfilePacket packet,
                                   ServerPlayer admin, MinecraftServer server,
                                   ServerPlayer target, String targetName) {
        StringBuilder report = new StringBuilder(
                "§a[RpEssentials] Profile of §e" + targetName + " §aupdated:");

        // ── 1. Nickname ───────────────────────────────────────────────────────
        if (packet.resetNickname()) {
            NicknameManager.removeNickname(target.getUUID());
            report.append(" §fnick=§8(reset)");
        } else {
            String nick = packet.nickname().trim();
            if (!nick.isEmpty()) {
                NicknameManager.setNickname(target.getUUID(), nick);
                report.append(" §fnick=").append(nick);
            }
        }

        // ── 2. Rôle ───────────────────────────────────────────────────────────
        String role = packet.role().trim();
        if (!role.isEmpty()) {
            applyRole(server, target, role);
            report.append(" §frole=").append(role);
        }

        // ── 3. Licence ────────────────────────────────────────────────────────
        String licenseId = packet.licenseId().trim();
        if (!licenseId.isEmpty()) {
            List<String> existing = LicenseManager.getLicenses(target.getUUID());

            if (existing.contains(licenseId)) {
                admin.sendSystemMessage(Component.literal(
                        "§e[RpEssentials] §f" + targetName + " §ealready has the §f" + licenseId
                                + " §elicense. Use §f/rpessentials license reissue §eto give a replacement item."));
            } else {
                LicenseManager.addLicense(target.getUUID(), licenseId);
                LicenseManager.logAction("GIVE", admin, target, licenseId, "via GUI");

                boolean itemGiven = LicenseHelper.giveLicenseItem(server, admin, target, licenseId);

                if (itemGiven) {
                    ProfessionRestrictionManager.ProfessionData profData =
                            ProfessionRestrictionManager.getProfessionData(licenseId);
                    String profDisplay = profData != null ? profData.getFormattedName() : licenseId;
                    target.sendSystemMessage(Component.literal(
                            MessagesConfig.get(MessagesConfig.LICENSE_GIVE_PLAYER,
                                    "profession", profDisplay)));
                    report.append(" §flicense=").append(licenseId);
                } else {
                    admin.sendSystemMessage(Component.literal(
                            "§c[RpEssentials] Unknown profession: §f" + licenseId));
                }
            }
        }

        admin.sendSystemMessage(Component.literal(report.toString()));
        RpEssentials.LOGGER.info("[GUI] Profile applied for {} by {}: nick='{}', role='{}', license='{}'",
                targetName, admin.getGameProfile().getName(),
                packet.nickname(), packet.role(), packet.licenseId());
    }

    // ── Role helper ────────────────────────────────────────────────────────────
    public static void applyRole(MinecraftServer server, ServerPlayer target, String roleId) {
        var silentSource = server.createCommandSourceStack()
                .withSuppressedOutput().withPermission(4);
        String targetName = target.getName().getString();

        try {
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String oldTag = entry.split(";", 2)[0].trim();
                server.getCommands().performPrefixedCommand(
                        silentSource, "tag " + targetName + " remove " + oldTag);
            }
            server.getCommands().performPrefixedCommand(
                    silentSource, "tag " + targetName + " add " + roleId);

            String lpGroup = roleId;
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 3);
                if (parts.length >= 2 && parts[0].trim().equalsIgnoreCase(roleId) && !parts[1].isBlank()) {
                    lpGroup = parts[1].trim();
                    break;
                }
            }
            server.getCommands().performPrefixedCommand(
                    silentSource, "lp user " + targetName + " parent set " + lpGroup);

            RpEssentialsRoleManager.invalidate(target.getUUID());
            RpEssentialsPermissions.invalidateCache(target.getUUID());
            ProfessionSyncHelper.syncToPlayer(target);

        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] ROLES config unavailable when applying role '{}': {}", roleId, e.getMessage());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[GUI] Error applying role '{}' to {}: {}", roleId, targetName, e.getMessage());
        }
    }
}