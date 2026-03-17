package net.rp.rpessentials;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.config.ScheduleConfig;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.identity.RpEssentialsMessagingManager;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.network.HideNametagsPacket;
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

        // ── Welcome message ───────────────────────────────────────────────────────
        try {
            if (ScheduleConfig.ENABLE_WELCOME != null && ScheduleConfig.ENABLE_WELCOME.get()) {
                String playerName = player.getName().getString();
                String nickname   = NicknameManager.getDisplayName(player);
                for (String line : ScheduleConfig.WELCOME_LINES.get()) {
                    String formatted = line
                            .replace("{player}",   playerName)
                            .replace("{nickname}", nickname);
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ColorHelper.translateAlternateColorCodes(formatted)));
                }
            }
        } catch (IllegalStateException e) {
            // config not yet loaded
        }

        // ── Welcome sound ─────────────────────────────────────────────────────────
        try {
            String soundId = ScheduleConfig.WELCOME_SOUND.get();
            if (soundId != null && !soundId.isBlank()) {
                ResourceLocation rl = ResourceLocation.tryParse(soundId);
                if (rl != null) {
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(rl);
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                            net.minecraft.core.Holder.direct(sound),
                            SoundSource.MASTER,
                            player.getX(), player.getY(), player.getZ(),
                            1.0f, 1.0f,
                            player.getRandom().nextLong()
                    ));
                }
            }
        } catch (IllegalStateException e) {
            // config not yet loaded
        }

        // ── Legacy nametag hide packet (backwards compat) ─────────────────────────
        try {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags));
        } catch (IllegalStateException e) {
            // config not yet loaded
        }

        // ── Advanced nametag sync (delayed 500ms to let the client finish loading) ─
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            server.execute(() -> {
                // Send config + all other players' data to the newly joined player
                NametagSyncHelper.sendTo(player, server);
                // Update all other players so they know about the new player
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (!other.getUUID().equals(player.getUUID())) {
                        NametagSyncHelper.sendTo(other, server);
                    }
                }
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogout(player);
        RpEssentialsPermissions.invalidateCache(player.getUUID());
        RpEssentialsMessagingManager.clearCache(player.getUUID());
    }
}