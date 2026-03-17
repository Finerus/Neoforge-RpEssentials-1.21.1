package net.rp.rpessentials.network;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

import java.util.UUID;

/**
 * Packet CLIENT → SERVEUR
 * Applique en une seule fois : nickname, rôle et licence principale d'un joueur.
 *
 * Persistance :
 *  - Nickname → NicknameManager.setNickname() → nicknames.json (async)
 *  - Rôle     → commandes serveur silencieuses (tag + lp) → géré par LuckPerms / scoreboard
 *  - Licence  → LicenseManager.addLicense() → licenses.json (async)
 */
public record SetPlayerProfilePacket(
        UUID targetUuid,
        String nickname,   // "" = ne pas modifier
        String role,       // "" = ne pas modifier
        String licenseId   // "" = ne pas modifier
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
                            buf.readUtf()
                    );
                }
                @Override
                public void encode(FriendlyByteBuf buf, SetPlayerProfilePacket p) {
                    buf.writeUUID(p.targetUuid());
                    buf.writeUtf(p.nickname());
                    buf.writeUtf(p.role());
                    buf.writeUtf(p.licenseId());
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

            // Double vérification droits
            if (!RpEssentialsPermissions.isStaff(admin)) return;

            MinecraftServer server = admin.getServer();
            ServerPlayer target = server.getPlayerList().getPlayer(packet.targetUuid());

            if (target == null) {
                admin.sendSystemMessage(Component.literal(
                        "§c[RPEssentials] Ce joueur n'est plus connecté."));
                return;
            }

            String targetName = target.getGameProfile().getName();
            StringBuilder report = new StringBuilder(
                    "§a[RPEssentials] Profil de §e" + targetName + " §amis à jour :");

            // ── 1. Nickname ───────────────────────────────────────────────────
            // NicknameManager.setNickname() sauvegarde dans nicknames.json (async CompletableFuture)
            String nick = packet.nickname().trim();
            if (!nick.isEmpty()) {
                NicknameManager.setNickname(target.getUUID(), nick);
                report.append(" §fnick=").append(nick);
            }

            // ── 2. Rôle via commandes serveur silencieuses ────────────────────
            // Même logique que /rpessentials setrole — persiste via tags scoreboard + LuckPerms
            String role = packet.role().trim();
            if (!role.isEmpty()) {
                applyRole(server, target, role, admin);
                report.append(" §frôle=").append(role);
            }

            // ── 3. Licence principale ─────────────────────────────────────────
            // LicenseManager.addLicense() sauvegarde dans licenses.json (async CompletableFuture)
            String licenseId = packet.licenseId().trim();
            if (!licenseId.isEmpty()) {
                LicenseManager.addLicense(target.getUUID(), licenseId);
                // Invalide le cache de restrictions et resync le client
                ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
                ProfessionSyncHelper.syncToPlayer(target);
                report.append(" §flicence=").append(licenseId);
            }

            admin.sendSystemMessage(Component.literal(report.toString()));
            RpEssentials.LOGGER.info("[GUI] Profile applied for {} by {}: nick='{}', role='{}', license='{}'",
                    targetName, admin.getGameProfile().getName(),
                    packet.nickname(), packet.role(), packet.licenseId());
        });
    }

    /**
     * Applique un rôle via les commandes serveur silencieuses.
     * Copie exacte de la logique de /rpessentials setrole pour garantir la cohérence.
     * - Supprime tous les anciens tags de rôle configurés
     * - Ajoute le nouveau tag vanilla
     * - Définit le groupe LuckPerms correspondant
     */
    private static void applyRole(MinecraftServer server, ServerPlayer target, String roleId, ServerPlayer admin) {
        // Source silencieuse avec permissions maximales (comme dans setrole)
        var silentSource = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);

        String targetName = target.getGameProfile().getName();

        try {
            // Supprime tous les anciens tags de rôle
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String oldTag = entry.split(";", 2)[0].trim();
                server.getCommands().performPrefixedCommand(
                        silentSource, "tag " + targetName + " remove " + oldTag);
            }

            // Ajoute le nouveau tag vanilla
            server.getCommands().performPrefixedCommand(
                    silentSource, "tag " + targetName + " add " + roleId);

            // Trouve le groupe LuckPerms correspondant (fallback = roleId)
            String lpGroup = roleId;
            for (String entry : RpEssentialsConfig.ROLES.get()) {
                String[] parts = entry.split(";", 2);
                if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(roleId)) {
                    lpGroup = parts[1].trim();
                    break;
                }
            }

            // Applique le groupe LuckPerms
            server.getCommands().performPrefixedCommand(
                    silentSource, "lp user " + targetName + " parent set " + lpGroup);

        } catch (IllegalStateException e) {
            RpEssentials.LOGGER.warn("[GUI] Could not read ROLES config when applying role '{}': {}", roleId, e.getMessage());
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[GUI] Error applying role '{}' to {}: {}", roleId, targetName, e.getMessage());
        }
    }
}
