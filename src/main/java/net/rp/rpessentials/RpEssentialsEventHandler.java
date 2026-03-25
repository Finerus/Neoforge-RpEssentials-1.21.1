package net.rp.rpessentials;

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
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.ModerationConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.MuteManager;
import net.rp.rpessentials.profession.ProfessionSyncHelper;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsEventHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogin(player);

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ProfessionSyncHelper.syncToPlayer(player);

        Component canJoin = RpEssentialsScheduleManager.canPlayerJoin(player);
        if (canJoin != null) {
            player.connection.disconnect(canJoin);
            return;
        }

        // Feature 7 — notification mute au login
        try {
            if (ModerationConfig.ENABLE_MUTE_SYSTEM.get()
                    && MuteManager.isMuted(player.getUUID())) {
                MuteManager.MuteEntry entry = MuteManager.getEntry(player.getUUID());
                if (entry != null) {
                    player.sendSystemMessage(ColorHelper.parseColors(
                            MessagesConfig.get(MessagesConfig.MUTE_NOTIFY_ON_JOIN,
                                    "reason", entry.reason,
                                    "expiry", entry.isPermanent() ? "Permanent" : entry.getFormattedExpiry())));
                }
            }
        } catch (IllegalStateException ignored) {}

        // Sync nametag
        java.util.concurrent.CompletableFuture.runAsync(
                () -> server.execute(() -> {
                    SyncNametagDataPacket.broadcastForPlayer(player);
                    for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                        if (!online.getUUID().equals(player.getUUID())) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                    player, SyncNametagDataPacket.from(online));
                        }
                    }
                }),
                java.util.concurrent.CompletableFuture.delayedExecutor(
                        500, java.util.concurrent.TimeUnit.MILLISECONDS)
        );

        // Welcome message
        try {
            if (ScheduleConfig.ENABLE_WELCOME != null && ScheduleConfig.ENABLE_WELCOME.get()) {
                String playerName = player.getName().getString();
                String nickname   = net.rp.rpessentials.identity.NicknameManager.getDisplayName(player);
                for (String line : ScheduleConfig.WELCOME_LINES.get()) {
                    String formatted = line
                            .replace("{player}",   playerName)
                            .replace("{nickname}", nickname);
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ColorHelper.translateAlternateColorCodes(formatted)));
                }
            }
        } catch (IllegalStateException ignored) {}

        // Welcome sound
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
                            1.0f, 1.0f, player.getRandom().nextLong()
                    ));
                }
            }
        } catch (IllegalStateException ignored) {}
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogout(player);
        RpEssentialsPermissions.invalidateCache(player.getUUID());
        net.rp.rpessentials.identity.RpEssentialsMessagingManager.clearCache(player.getUUID());
        RpCooldownManager.clearAll(player.getUUID()); // Feature 14
    }
}