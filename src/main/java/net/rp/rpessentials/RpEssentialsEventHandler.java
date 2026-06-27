package net.rp.rpessentials;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
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
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.ProfessionSyncHelper;
import net.rp.rpessentials.profession.TempLicenseExpirationManager;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogin(player);
        PlaytimeManager.onLogin(player.getUUID());

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ProfessionSyncHelper.syncToPlayer(player);

        Component canJoin = RpEssentialsScheduleManager.canPlayerJoin(player);
        if (canJoin != null) {
            player.connection.disconnect(canJoin);
            return;
        }

        // Message join — cast via IRpPlayerList (interface injectée par MixinPlayerList)
        sendJoinLeaveMessage(server, player, true);

        if (RpEssentialsPermissions.isStaff(player)) {
            try {
                if ("FORCE_CLOSED".equals(ScheduleConfig.FORCE_STATE.get())) {
                    player.sendSystemMessage(Component.literal(
                            "§c§l[Schedule] ⚠ The server is currently FORCIBLY CLOSED. "
                                    + "Use §f/opennow §c§lto reopen it."));
                }
            } catch (IllegalStateException ignored) {}
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

        PlaytimeManager.onLogout(player.getUUID());
        LastConnectionManager.recordLogout(player);
        ProximityChatSpyManager.onLogout(player.getUUID());

        MinecraftServer server = player.getServer();
        if (server != null) sendJoinLeaveMessage(server, player, false);

        RpEssentialsPermissions.invalidateCache(player.getUUID());
        net.rp.rpessentials.identity.RpEssentialsMessagingManager.clearCache(player.getUUID());
        RpCooldownManager.clearAll(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        // Initialisation anticipée pour éviter le lag au premier login
        NicknameManager.reload();
        net.rp.rpessentials.profession.LicenseManager.reload();
        net.rp.rpessentials.moderation.WarnManager.reload();
        net.rp.rpessentials.moderation.MuteManager.reload();
        net.rp.rpessentials.moderation.LastConnectionManager.reload();

        // Validation dimension AFK
        try {
            String dimId = net.rp.rpessentials.config.RpConfig.AFK_DIMENSION.get();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimKey =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.parse(dimId));
            if (event.getServer().getLevel(dimKey) == null)
                RpEssentials.LOGGER.warn("[RPEssentials] AFK dimension '{}' does not exist.", dimId);
        } catch (IllegalStateException ignored) {}

        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RPEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                net.rp.rpessentials.profession.LicenseManager.getAllLicenses().size(),
                net.rp.rpessentials.moderation.WarnManager.getAll().size(),
                net.rp.rpessentials.moderation.MuteManager.getAllMutes().size(),
                dataFolder);
        AfkPlatformInitializer.tryPlace(event.getServer());
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