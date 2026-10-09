package net.rp.rpessentials;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.api.IRpPlayerList;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.ProfessionSyncHelper;
import net.rp.rpessentials.profession.TempLicenseExpirationManager;

import net.rp.rpessentials.profession.LicenseHelper;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.PendingProfileManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.network.SetPlayerProfilePacket;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsEventHandler {

    private static final java.util.Set<java.util.UUID> refusedJoins = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.setCustomName(null);

        Component canJoin = RpEssentialsScheduleManager.canPlayerJoin(player);
        if (canJoin != null) {
            refusedJoins.add(player.getUUID());
            try {
                if (ScheduleConfig.NOTIFY_REFUSED_JOIN.get()) {
                    Component notice = Component.literal("§6[Schedule] §e" + player.getName().getString()
                            + " §7tried to join while the server is closed.");
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(notice);
                    }
                }
            } catch (IllegalStateException ignored) {}
            player.connection.disconnect(canJoin);
            return;
        }

        LastConnectionManager.recordLogin(player);
        PlaytimeManager.onLogin(player.getUUID());
        ProfessionSyncHelper.syncToPlayer(player);

        // Application du profil préparé à l'avance par le staff, si existant
        PendingProfileManager.PendingEntry pending = PendingProfileManager.consumePending(player.getUUID());
        if (pending != null) {
            StringBuilder logSummary = new StringBuilder();

            try {
                if (pending.nickname != null && !pending.nickname.isEmpty()) {
                    NicknameManager.setNickname(player.getUUID(), pending.nickname);
                    logSummary.append("nickname ").append(player.getName().getString())
                            .append(" -> ").append(pending.nickname);
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[RPEssentials] Failed to apply prepared nickname for {}",
                        player.getName().getString(), e);
            }

            try {
                if (pending.role != null && !pending.role.isEmpty()) {
                    SetPlayerProfilePacket.applyRole(server, player, pending.role);
                    if (logSummary.length() > 0) logSummary.append(", ");
                    logSummary.append("role \"").append(pending.role).append("\"");
                }
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[RPEssentials] Failed to apply prepared role for {}",
                        player.getName().getString(), e);
            }

            if (!pending.licenses.isEmpty()) {
                int given = 0;
                for (String profId : pending.licenses) {
                    try {
                        LicenseManager.addLicense(player.getUUID(), profId);
                        boolean itemGiven = LicenseHelper.giveLicenseItem(server, null, player, profId);
                        if (!itemGiven) {
                            RpEssentials.LOGGER.warn("[RPEssentials] Prepared license '{}' for {} has no matching profession, item not given.",
                                    profId, player.getName().getString());
                        } else {
                            given++;
                        }
                    } catch (Exception e) {
                        RpEssentials.LOGGER.error("[RPEssentials] Failed to give prepared license '{}' to {}",
                                profId, player.getName().getString(), e);
                    }
                }
                ProfessionRestrictionManager.invalidatePlayerCache(player.getUUID());
                ProfessionSyncHelper.syncToPlayer(player);
                if (given > 0) {
                    if (logSummary.length() > 0) logSummary.append(", ");
                    logSummary.append("gave ").append(given)
                            .append(" profession(s) (").append(String.join(", ", pending.licenses)).append(")");
                }
            }

            if (logSummary.length() > 0) {
                RpEssentials.LOGGER.info("[RPEssentials] Applied prepared profile for {}: {}",
                        player.getName().getString(), logSummary);
                player.sendSystemMessage(Component.literal(
                        "§a[RPEssentials] Your profile was prepared in advance by staff."));
            }
        }

        // Message join — cast via IRpPlayerList (interface injectée par MixinPlayerList)
        sendJoinLeaveMessage(server, player, true);

        if (RpEssentialsPermissions.isStaff(player)) {
            try {
                if ("FORCE_CLOSED".equals(ScheduleConfig.FORCE_STATE.get())) {
                    player.sendSystemMessage(Component.literal(
                            "§c§l[Schedule] ⚠ The server is currently FORCIBLY CLOSED. "
                                    + "Use §f/calendar opennow §c§lto reopen it. Or §f/calendar resetforcestate §c§lto reset the state of the schedule."));
                }
            } catch (IllegalStateException ignored) {}
            for (String w : RpEssentialsScheduleManager.getConfigWarnings()) {
                player.sendSystemMessage(Component.literal("§c[Schedule] " + w));
            }
        }

        try {
            if (ModerationConfig.ENABLE_MUTE_SYSTEM.get() && MuteManager.isMuted(player.getUUID())) {
                MuteManager.MuteEntry entry = MuteManager.getEntry(player.getUUID());
                if (entry != null) {
                    player.sendSystemMessage(ColorHelper.parseColors(
                            MessagesConfig.get(MessagesConfig.MUTE_NOTIFY_ON_JOIN,
                                    "reason", entry.reason,
                                    "expiry", entry.isPermanent() ? "Permanent" : entry.getFormattedExpiry())));
                }
            }
        } catch (IllegalStateException ignored) {}

        // Sync nametag différé 500ms
        java.util.concurrent.CompletableFuture.runAsync(
                () -> server.execute(() -> {
                    SyncNametagDataPacket.broadcastForPlayer(player);
                    for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                        if (!online.getUUID().equals(player.getUUID())) {
                            PacketDistributor.sendToPlayer(player, SyncNametagDataPacket.from(online));
                        }
                    }
                }),
                java.util.concurrent.CompletableFuture.delayedExecutor(
                        500, java.util.concurrent.TimeUnit.MILLISECONDS));

        // Message de bienvenue
        try {
            if (ScheduleConfig.ENABLE_WELCOME != null && ScheduleConfig.ENABLE_WELCOME.get()) {
                String playerName = player.getName().getString();
                String nickname   = NicknameManager.getDisplayName(player);
                for (String line : ScheduleConfig.WELCOME_LINES.get()) {
                    String fmt = line.replace("{player}", playerName).replace("{nickname}", nickname);
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ColorHelper.translateAlternateColorCodes(fmt)));
                }
            }
        } catch (IllegalStateException ignored) {}

        TempLicenseExpirationManager.checkOnLogin(player, server);
        TempLicenseExpirationManager.markRevokedLicenseItems(player);

        try { if (ModerationConfig.WARN_AUTO_PURGE_EXPIRED.get()) WarnManager.purgeExpiredWarns(); }
        catch (IllegalStateException ignored) {}

        try {
            if (ModerationConfig.WARN_NOTIFY_ON_JOIN.get()) {
                int count = WarnManager.getActiveWarns(player.getUUID()).size();
                if (count > 0) player.sendSystemMessage(ColorHelper.parseColors(
                        ModerationConfig.WARN_JOIN_MESSAGE.get().replace("{count}", String.valueOf(count))));
            }
        } catch (IllegalStateException ignored) {}

        // Sync hideNametags state to the joining player
        try {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags));
        } catch (IllegalStateException ignored) {}

        try {
            String soundId = ScheduleConfig.WELCOME_SOUND.get();
            if (soundId != null && !soundId.isBlank()) {
                net.minecraft.resources.ResourceLocation rl =
                        net.minecraft.resources.ResourceLocation.tryParse(soundId);
                if (rl != null) {
                    net.minecraft.sounds.SoundEvent sound =
                            net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            net.minecraft.core.Holder.direct(sound),
                            net.minecraft.sounds.SoundSource.MASTER,
                            player.getX(), player.getY(), player.getZ(),
                            1.0f, 1.0f, player.getRandom().nextLong()));
                }
            }
        } catch (IllegalStateException ignored) {}
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RpEssentialsRoleManager.invalidate(player.getUUID());
        RpEssentialsPermissions.invalidateCache(player.getUUID());
        RpEssentialsMessagingManager.clearCache(player.getUUID());
        RpCooldownManager.clearAll(player.getUUID());
        ProximityChatSpyManager.onLogout(player.getUUID());

        if (refusedJoins.remove(player.getUUID())) return;

        if (!flushedAtStop.remove(player.getUUID())) {
            PlaytimeManager.onLogout(player.getUUID());
            LastConnectionManager.recordLogout(player);
        }
        MinecraftServer server = player.getServer();
        if (server != null) sendJoinLeaveMessage(server, player, false);
    }

    private static final java.util.Set<java.util.UUID> flushedAtStop = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void flushOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!flushedAtStop.add(p.getUUID())) continue;
            PlaytimeManager.onLogout(p.getUUID());
            LastConnectionManager.recordLogout(p);
        }
        LastConnectionManager.flushToDisk();
    }

    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        // Initialisation anticipée pour éviter le lag au premier login
        NicknameManager.reload();
        LicenseManager.reload();
        WarnManager.reload();
        MuteManager.reload();
        LastConnectionManager.reload();
        NoteManager.reload();
        DeathRPManager.reload();
        AutoUnwhitelistHistory.reload();
        PendingProfileManager.reload();

        // Validation dimension AFK
        try {
            String dimId = net.rp.rpessentials.config.RpConfig.AFK_DIMENSION.get();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.parse(dimId));
            if (event.getServer().getLevel(dimKey) == null)
                RpEssentials.LOGGER.warn("[RpEssentials] AFK dimension '{}' does not exist.", dimId);
        } catch (IllegalStateException ignored) {}

        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RpEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                LicenseManager.getAllLicenses().size(),
                WarnManager.getAll().size(),
                MuteManager.getAllMutes().size(),
                dataFolder);
        AfkPlatformInitializer.tryPlace(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!DeathRPManager.isDeathRPEnabled(player.getUUID())) return;
        DeathRPManager.onPlayerDeathRP(player, event.getSource());
    }

    // =========================================================================
    // PRIVATE
    // =========================================================================

    /**
     * Envoie le message join/leave via l'interface IRpPlayerList.
     * MixinPlayerList implémente IRpPlayerList, ce qui permet le cast
     * sans référencer directement la classe de mixin.
     */
    private static void sendJoinLeaveMessage(MinecraftServer server, ServerPlayer player, boolean isJoin) {
        PlayerList pl = server.getPlayerList();

        if (pl instanceof IRpPlayerList rpl) {
            rpl.sendCustomJoinLeaveMessage(player, isJoin);
        } else {
            RpEssentials.LOGGER.warn("[JoinLeave] IRpPlayerList not available on PlayerList");
        }
    }
}