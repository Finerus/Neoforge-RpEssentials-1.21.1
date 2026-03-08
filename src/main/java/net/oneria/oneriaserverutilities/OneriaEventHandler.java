package net.oneria.oneriaserverutilities;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;

@EventBusSubscriber(modid = OneriaServerUtilities.MODID)
public class OneriaEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        OneriaServerUtilities.LOGGER.info("Player {} logged in", player.getName().getString());

        // --- Nametag packet ---
        PacketDistributor.sendToPlayer(player, new HideNametagsPacket(OneriaConfig.HIDE_NAMETAGS.get()));

        // --- Nickname custom name ---
        if (NicknameManager.hasNickname(player.getUUID())) {
            String nickname = NicknameManager.getNickname(player.getUUID());
            String nametagDisplay;
            if (OneriaConfig.SHOW_NAMETAG_PREFIX_SUFFIX.get()) {
                String prefix = OneriaServerUtilities.getPlayerPrefix(player);
                String suffix = OneriaServerUtilities.getPlayerSuffix(player);
                nametagDisplay = prefix + nickname + suffix;
            } else {
                nametagDisplay = nickname;
            }
            player.setCustomName(Component.literal(nametagDisplay.replace("&", "§")));
            player.setCustomNameVisible(true);
        }

        // --- Profession sync ---
        ProfessionSyncHelper.syncToPlayer(player);

        // --- Last connection: enregistrer le login IMMÉDIATEMENT ---
        LastConnectionManager.recordLogin(player);

        // --- Temp license expiration ---
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        TempLicenseExpirationManager.checkOnLogin(player, server);
        TempLicenseExpirationManager.markRevokedLicenseItems(player);

        // --- Actions différées (schedule, welcome, warns) sur le server thread ---
        if (server != null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                server.execute(() -> {
                    checkScheduleOnJoin(player);
                    sendWelcomeMessage(player);
                    notifyWarnsOnJoin(player);   // ← NOUVEAU
                    autoPurgeWarnsOnJoin(player); // ← NOUVEAU
                });
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        OneriaServerUtilities.LOGGER.info("Player {} logged out", player.getName().getString());

        // --- Last connection: enregistrer le logout ---
        LastConnectionManager.recordLogout(player);

        // --- Invalidation des caches ---
        OneriaPermissions.invalidateCache(player.getUUID());
        WorldBorderManager.clearCache(player.getUUID());
        OneriaMessagingManager.clearCache(player.getUUID());
    }

    // =========================================================================
    // SCHEDULE
    // =========================================================================

    private static void checkScheduleOnJoin(ServerPlayer player) {
        Component kickMessage = OneriaScheduleManager.canPlayerJoin(player);

        if (kickMessage != null) {
            player.connection.disconnect(kickMessage);
            OneriaServerUtilities.LOGGER.info("Kicked {} (server closed, non-staff)", player.getName().getString());
        } else if (ScheduleConfig.ENABLE_SCHEDULE.get()) {
            if (OneriaPermissions.isStaff(player) && !OneriaScheduleManager.isServerOpen()) {
                player.sendSystemMessage(Component.literal("§6[STAFF] Connection authorized (server closed)."));
            }
            player.sendSystemMessage(Component.literal("§7" + OneriaScheduleManager.getTimeUntilNextEvent()));
            ProfessionRestrictionManager.invalidatePlayerCache(player.getUUID());
        }
    }

    // =========================================================================
    // WELCOME
    // =========================================================================

    private static void sendWelcomeMessage(ServerPlayer player) {
        if (!ScheduleConfig.ENABLE_WELCOME.get()) return;

        String playerName = player.getName().getString();
        String displayName = player.getDisplayName().getString();

        for (String line : ScheduleConfig.WELCOME_LINES.get()) {
            player.sendSystemMessage(Component.literal(
                    line.replace("{player}", playerName).replace("{nickname}", displayName)));
        }

        String soundName = ScheduleConfig.WELCOME_SOUND.get();
        if (!soundName.isEmpty()) {
            try {
                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(soundName));
                player.playNotifySound(soundEvent, SoundSource.MASTER,
                        ScheduleConfig.WELCOME_SOUND_VOLUME.get().floatValue(),
                        ScheduleConfig.WELCOME_SOUND_PITCH.get().floatValue());
            } catch (Exception e) {
                OneriaServerUtilities.LOGGER.warn("Failed to play welcome sound: {}", soundName);
            }
        }
    }

    // =========================================================================
    // WARN NOTIFICATIONS ON JOIN (NOUVEAU)
    // =========================================================================

    /**
     * Notifie le joueur de ses warns actifs au login, si le système est activé.
     * Affiche le nombre de warns actifs et l'invite à taper /mywarn.
     */
    private static void notifyWarnsOnJoin(ServerPlayer player) {
        try {
            if (ModerationConfig.ENABLE_WARN_SYSTEM == null || !ModerationConfig.ENABLE_WARN_SYSTEM.get()) return;
            if (!ModerationConfig.WARN_NOTIFY_ON_JOIN.get()) return;
        } catch (IllegalStateException e) {
            return;
        }

        List<WarnManager.WarnEntry> activeWarns = WarnManager.getActiveWarns(player.getUUID());
        if (activeWarns.isEmpty()) return;

        int count = activeWarns.size();
        String template;
        try {
            template = ModerationConfig.WARN_JOIN_MESSAGE.get();
        } catch (IllegalStateException e) {
            template = "§c⚠ Vous avez §l{count} avertissement(s) actif(s)§r§c. Tapez §l/mywarn §r§cpour les consulter.";
        }
        String msg = template.replace("{count}", String.valueOf(count));
        player.sendSystemMessage(Component.literal(msg));
    }

    /**
     * Purge les warns expirés au login si autoPurgeExpired est activé.
     * Appelé sur le server thread donc thread-safe.
     */
    private static void autoPurgeWarnsOnJoin(ServerPlayer player) {
        try {
            if (ModerationConfig.ENABLE_WARN_SYSTEM == null || !ModerationConfig.ENABLE_WARN_SYSTEM.get()) return;
            if (!ModerationConfig.WARN_AUTO_PURGE_EXPIRED.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        // La purge est légère (in-memory filter + async save si changement)
        WarnManager.purgeExpiredWarns();
    }
}
