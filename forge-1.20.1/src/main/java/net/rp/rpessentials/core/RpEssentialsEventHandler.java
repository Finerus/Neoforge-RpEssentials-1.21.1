package net.rp.rpessentials.core;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.rp.rpessentials.network.compat.PacketDistributor;
import net.rp.rpessentials.api.IRpPlayerList;
import net.rp.rpessentials.compat.LuckPermsSync;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.network.SetPlayerProfilePacket;
import net.rp.rpessentials.profession.*;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.gameplay.AfkPlatformInitializer;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.gameplay.WorldBorderManager;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.network.SyncNametagDataPacket;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.NameCache;
import net.rp.rpessentials.util.RpCooldownManager;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsEventHandler {

    private static final java.util.Set<java.util.UUID> refusedJoins = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.setCustomName(null);
        NameCache.put(player.getUUID(), player.getGameProfile().getName());

        Component canJoin = RpEssentialsScheduleManager.canPlayerJoin(player);
        if (canJoin != null) {
            refusedJoins.add(player.getUUID());
            if (Cfg.ready(ScheduleConfig.SPEC)) {
                if (ScheduleConfig.NOTIFY_REFUSED_JOIN.get()) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (RpPerms.has(p, RpPerm.NOTIFY)) p.sendSystemMessage(Component.literal(ServerLang.get(p,
                                MessagesConfig.LOGIN_REFUSED_NOTICE, "player", player.getName().getString())));
                    }
                }
            }
            player.connection.disconnect(canJoin);
            return;
        }

        LastConnectionManager.recordLogin(player);
        AbsenceManager.onLogin(player.getUUID());
        PlaytimeManager.onLogin(player.getUUID());
        ProfessionSyncHelper.syncToPlayer(player);
        net.rp.rpessentials.gameplay.ModNotices.onLogin(player);

        if (VanishManager.isVanished(player.getUUID()) && VanishManager.reminderEnabled()) {
            player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.LOGIN_VANISH_REMINDER,
                    "level", String.valueOf(VanishManager.getLevel(player.getUUID())))));
        }

        PendingActionManager.applyOnLogin(server, player);

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
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.PROFILE_PREPARED)));
            }
        }

        sendJoinLeaveMessage(server, player, true);

        if (RpPerms.has(player, RpPerm.NOTIFY)) {
            if (Cfg.ready(ScheduleConfig.SPEC)) {
                if ("FORCE_CLOSED".equals(ScheduleConfig.FORCE_STATE.get())) {
                    player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.LOGIN_FORCE_CLOSED)));
                }
            }
            for (String w : RpEssentialsScheduleManager.getConfigWarnings(player)) {
                player.sendSystemMessage(Component.literal(ServerLang.get(player, MessagesConfig.SCHED_SYS_CONFIG_WARNING, "warning", w)));
            }
        }

        if (Cfg.ready(ModerationConfig.SPEC)) {
            if (ModerationConfig.ENABLE_MUTE_SYSTEM.get() && MuteManager.isMuted(player.getUUID())) {
                MuteManager.MuteEntry entry = MuteManager.getEntry(player.getUUID());
                if (entry != null) {
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ServerLang.get(player, MessagesConfig.MUTE_NOTIFY_ON_JOIN,
                                    "reason", entry.reason,
                                    "expiry", entry.getFormattedExpiry(player))));
                }
            }
        }

        java.util.concurrent.CompletableFuture.runAsync(
                () -> server.execute(() -> {
                    SyncNametagDataPacket.broadcastForPlayer(player);
                    SyncNametagDataPacket.sendAllTo(player);
                }),
                java.util.concurrent.CompletableFuture.delayedExecutor(
                        500, java.util.concurrent.TimeUnit.MILLISECONDS));

        if (Cfg.ready(ScheduleConfig.SPEC)) {
            if (ScheduleConfig.ENABLE_WELCOME != null && ScheduleConfig.ENABLE_WELCOME.get()) {
                String playerName = player.getName().getString();
                String nickname   = NicknameManager.getDisplayName(player);
                for (String line : ScheduleConfig.WELCOME_LINES.get()) {
                    String fmt = line.replace("{player}", playerName).replace("{nickname}", nickname);
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ColorHelper.translateAlternateColorCodes(fmt)));
                }
            }
        }

        if (Cfg.ready(ChatConfig.SPEC)) {
            if (ChatConfig.ENABLE_PM_SPY.get()) {
                String notice = ServerLang.get(player, MessagesConfig.MP_SPY_NOTICE);
                if (!notice.isBlank()) player.sendSystemMessage(ColorHelper.parseColors(notice));
            }
        }

        TempLicenseExpirationManager.checkOnLogin(player, server);
        TempLicenseExpirationManager.markRevokedLicenseItems(player);
        net.rp.rpessentials.moderation.ReportService.onStaffLogin(player);
        net.rp.rpessentials.config.ConfigValidator.warnStaff(player);

        if (Cfg.ready(ModerationConfig.SPEC)) { if (ModerationConfig.WARN_AUTO_PURGE_EXPIRED.get()) WarnManager.purgeExpiredWarns(player.getUUID()); }

        if (Cfg.ready(ModerationConfig.SPEC)) {
            if (ModerationConfig.WARN_NOTIFY_ON_JOIN.get()) {
                int count = WarnManager.getActiveWarns(player.getUUID()).size();
                if (count > 0) player.sendSystemMessage(ColorHelper.parseColors(
                        ModerationConfig.WARN_JOIN_MESSAGE.get().replace("{count}", String.valueOf(count))));
            }
        }

        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags));
        }
        PacketDistributor.sendToPlayer(player, new net.rp.rpessentials.network.SyncPermissionsPacket(
                net.rp.rpessentials.permission.RpPerms.granted(player)));

        if (Cfg.ready(ScheduleConfig.SPEC)) {
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
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        java.util.UUID id = player.getUUID();

        RpEssentialsRoleManager.invalidate(id);
        RpEssentialsPermissions.invalidateCache(id);
        RpEssentialsMessagingManager.clearCache(id);
        RpCooldownManager.clearExpired(id);
        ProximityChatSpyManager.onLogout(id);
        ProfessionRestrictionManager.invalidatePlayerCache(id);
        ProfessionRestrictionEventHandler.onLogout(id);
        WorldBorderManager.clearCache(id);
        TabBlur.onLogout(id);
        RpEssentials.invalidateMeta(id);

        if (refusedJoins.remove(id)) return;

        if (!flushedAtStop.remove(id)) {
            PlaytimeManager.onLogout(id);
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
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        NicknameManager.reload();
        LicenseManager.reload();
        WarnManager.reload();
        MuteManager.reload();
        LastConnectionManager.reload();
        NoteManager.reload();
        net.rp.rpessentials.moderation.ReportManager.reload();
        net.rp.rpessentials.identity.CharacterSheetManager.reload();
        DeathRPManager.reload();
        AutoUnwhitelistHistory.reload();
        PendingProfileManager.reload();
        AbsenceManager.reload();
        PendingActionManager.reload();
        VanishManager.reload();
        net.rp.rpessentials.moderation.HistoryManager.reload();

        if (Cfg.ready(RpConfig.SPEC)) {
            String dimId = net.rp.rpessentials.config.RpConfig.AFK_DIMENSION.get();
            net.minecraft.resources.ResourceLocation dimRl = net.minecraft.resources.ResourceLocation.tryParse(dimId);
            if (dimRl == null || event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, dimRl)) == null)
                RpEssentials.LOGGER.warn("[RpEssentials] AFK dimension '{}' does not exist.", dimId);
        }

        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RpEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                LicenseManager.getAllLicenses().size(),
                WarnManager.getAll().size(),
                MuteManager.getAllMutes().size(),
                dataFolder);
        AfkPlatformInitializer.tryPlace(event.getServer());
        net.rp.rpessentials.gameplay.ModNotices.onServerStarted(event.getServer());
        // Roles first: old permission names are converted to nodes before LuckPerms compares its groups.
        net.rp.rpessentials.permission.RpEssentialsRoleManager.reload();
        LuckPermsSync.start(event.getServer());

        for (String problem : net.rp.rpessentials.config.ConfigValidator.validate()) {
            RpEssentials.LOGGER.warn("[Config] {}", problem);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!DeathRPManager.isDeathRPEnabled(player.getUUID())) return;
        net.rp.rpessentials.api.event.DeathRpEvent ev = new net.rp.rpessentials.api.event.DeathRpEvent(player, event.getSource());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return;
        DeathRPManager.onPlayerDeathRP(player, event.getSource());
    }

    private static void sendJoinLeaveMessage(MinecraftServer server, ServerPlayer player, boolean isJoin) {
        if (VanishManager.isVanished(player.getUUID()) && VanishManager.hides(VanishManager.Aspect.JOIN_CUSTOM)) {
            VanishManager.notifySilent(server, player, isJoin);
            return;
        }
        PlayerList pl = server.getPlayerList();

        if (pl instanceof IRpPlayerList rpl) {
            rpl.sendCustomJoinLeaveMessage(player, isJoin);
        } else {
            RpEssentials.LOGGER.warn("[JoinLeave] IRpPlayerList not available on PlayerList");
        }
    }
}