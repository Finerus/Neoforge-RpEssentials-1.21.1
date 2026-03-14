package net.rp.rpessentials;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsEventHandler {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Path worldData = event.getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("data");
        ConfigMigrator.migrateDataIfNeeded(worldData);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogin(player);

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ProfessionSyncHelper.syncToPlayer(player);

        // Sync nametag data vers tous les joueurs connectés
        SyncNametagDataPacket.broadcastForPlayer(player);
        // Et envoyer les données des joueurs déjà connectés au nouveau joueur
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            if (!online.getUUID().equals(player.getUUID())) {
                net.neoforged.neoforge.network.PacketDistributor
                        .sendToPlayer(player, SyncNametagDataPacket.from(online));
            }
        }

        try {
            if (ScheduleConfig.ENABLE_WELCOME != null && ScheduleConfig.ENABLE_WELCOME.get()) {
                for (String line : ScheduleConfig.WELCOME_LINES.get()) {
                    String formatted = line.replace("%player%", player.getName().getString());
                    player.sendSystemMessage(ColorHelper.parseColors(
                            ColorHelper.translateAlternateColorCodes(formatted)));
                }
            }
        } catch (IllegalStateException e) {
            // config pas encore chargée
        }

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
            // config pas encore chargée
        }

        try {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags));
        } catch (IllegalStateException e) {
            // config pas encore chargée
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LastConnectionManager.recordLogout(player);
        RpEssentialsMessagingManager.clearCache(player.getUUID());
    }
}