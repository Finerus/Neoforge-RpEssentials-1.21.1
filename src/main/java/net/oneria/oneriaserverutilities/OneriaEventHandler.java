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

@EventBusSubscriber(modid = OneriaServerUtilities.MODID)
public class OneriaEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        OneriaServerUtilities.LOGGER.info("Player {} logged in", player.getName().getString());

        PacketDistributor.sendToPlayer(player, new HideNametagsPacket(OneriaConfig.HIDE_NAMETAGS.get()));

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

        ProfessionSyncHelper.syncToPlayer(player);

        // Fix 1 : import net.minecraft.server.MinecraftServer ajouté
        // Fix 2 : import net.neoforged.neoforge.server.ServerLifecycleHooks ajouté
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        // Fix 3 : signature correcte checkOnLogin(ServerPlayer, MinecraftServer)
        TempLicenseExpirationManager.checkOnLogin(player, server);

        // Fix 4 : new Thread() supprimé -- CompletableFuture seul suffit
        // server.execute() est une méthode de MinecraftServer, pas de Thread
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
                });
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        OneriaServerUtilities.LOGGER.info("Player {} logged out", player.getName().getString());

        OneriaPermissions.invalidateCache(player.getUUID());
        WorldBorderManager.clearCache(player.getUUID());
        OneriaMessagingManager.clearCache(player.getUUID());
    }

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
}