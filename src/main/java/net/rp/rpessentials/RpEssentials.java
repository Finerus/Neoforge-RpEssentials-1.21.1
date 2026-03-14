package net.rp.rpessentials;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.EnumSet;

@Mod("rpessentials")
public class RpEssentials {

    // ⚠ CHANGED: valeur du MODID
    public static final String MODID = "rpessentials";
    public static final Logger LOGGER = LogUtils.getLogger();
    private int tickCounter = 0;

    public RpEssentials(IEventBus modEventBus, ModContainer modContainer) {
        ConfigMigrator.migrateIfNeeded();

        modContainer.registerConfig(ModConfig.Type.SERVER, RpEssentialsConfig.SPEC,       "rpessentials/rpessentials-core.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ChatConfig.SPEC,         "rpessentials/rpessentials-chat.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ScheduleConfig.SPEC,     "rpessentials/rpessentials-schedule.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ModerationConfig.SPEC,   "rpessentials/rpessentials-moderation.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ProfessionConfig.SPEC,   "rpessentials/rpessentials-professions.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, MessagesConfig.SPEC,     "rpessentials/rpessentials-messages.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, NametagConfig.SPEC,  "rpessentials/rpessentials-nametag.toml");

        RpEssentialsItems.ITEMS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getType() == ModConfig.Type.SERVER) {
                RpEssentialsScheduleManager.reload();
                ProfessionRestrictionManager.reloadCache();
                LOGGER.info("[RPEssentials] Schedule system and profession restrictions initialized after config load");
            }
        });
    }

    public static String getPlayerPrefix(ServerPlayer player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                return prefix != null ? prefix : "";
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            // LuckPerms non disponible
        }
        return "";
    }

    public static String getPlayerSuffix(ServerPlayer player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String suffix = user.getCachedData().getMetaData().getSuffix();
                return suffix != null ? suffix : "";
            }
        } catch (IllegalStateException | NoClassDefFoundError e) {
            // LuckPerms non disponible
        }
        return "";
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;
        MinecraftServer server = event.getServer();

        if (tickCounter % 40 == 0) {
            try {
                if (RpEssentialsConfig.ENABLE_BLUR.get()) {
                    ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            server.getPlayerList().getPlayers());
                    server.getPlayerList().broadcastAll(packet);
                }
            } catch (IllegalStateException ignored) {}
        }

        if (tickCounter % 400 == 0) {
            ProfessionRestrictionEventHandler.cleanupCaches();
            CraftingAndArmorRestrictionEventHandler.cleanupCaches();
        }

        if (tickCounter % 20 == 0) {
            RpEssentialsScheduleManager.tick(server);
            WorldBorderManager.tick(server);
        }

        if (tickCounter % 1200 == 0) {
            java.time.LocalTime now = java.time.LocalTime.now();
            RpEssentialsScheduleManager.tickMidnightSweep(server);
            TempLicenseExpirationManager.tickMidnightSweep(server, now.getHour(), now.getMinute());
            LastConnectionManager.tickAutoUnwhitelist(server, now.getHour(), now.getMinute());
            tickCounter = 0;
        }

        tickCounter++;
    }
}