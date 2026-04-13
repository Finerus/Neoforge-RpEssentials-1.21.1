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
import net.rp.rpessentials.client.RpKeyBindings;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.PlaytimeManager;
import net.rp.rpessentials.profession.ProfessionRestrictionEventHandler;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.TempLicenseExpirationManager;
import org.slf4j.Logger;

import java.time.LocalTime;
import java.util.EnumSet;

@Mod("rpessentials")
public class RpEssentials {

    public static final String MODID = "rpessentials";
    public static final Logger LOGGER = LogUtils.getLogger();
    private int tickCounter = 0;

    public RpEssentials(IEventBus modEventBus, ModContainer modContainer) {

        modContainer.registerConfig(ModConfig.Type.SERVER, RpEssentialsConfig.SPEC, "rpessentials/rpessentials-core.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ChatConfig.SPEC,         "rpessentials/rpessentials-chat.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ScheduleConfig.SPEC,     "rpessentials/rpessentials-schedule.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ModerationConfig.SPEC,   "rpessentials/rpessentials-moderation.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ProfessionConfig.SPEC,   "rpessentials/rpessentials-professions.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, MessagesConfig.SPEC,     "rpessentials/rpessentials-messages.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, RpConfig.SPEC,           "rpessentials/rpessentials-rp.toml");

        modEventBus.addListener(RpKeyBindings::onRegisterKeyMappings);
        RpEssentialsItems.ITEMS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getType() == ModConfig.Type.SERVER) {
                RpEssentialsScheduleManager.reload();
                ProfessionRestrictionManager.reloadCache();
                // Invalider le cache des presets Immersive au reload de config
                ImmersivePresetHelper.clearCache();
                LOGGER.info("[RPEssentials] Config loaded: schedule, professions & immersive presets initialized.");
            }
        });
    }

    // =========================================================================
    // LUCKPERMS
    // =========================================================================

    public static String getPlayerPrefix(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                return prefix != null ? prefix : "";
            }
        } catch (IllegalStateException | NoClassDefFoundError ignored) {}
        return "";
    }

    public static String getPlayerSuffix(ServerPlayer player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String suffix = user.getCachedData().getMetaData().getSuffix();
                return suffix != null ? suffix : "";
            }
        } catch (IllegalStateException | NoClassDefFoundError ignored) {}
        return "";
    }

    // =========================================================================
    // SERVER TICK
    // =========================================================================

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        // Toutes les 40 ticks (2s) : mise à jour TabList blur
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

        // World border check (interval configurable, offset +13 pour éviter le burst tick-0)
        int wbInterval = 40;
        try { wbInterval = RpEssentialsConfig.WORLD_BORDER_CHECK_INTERVAL.get(); }
        catch (IllegalStateException ignored) {}
        if ((tickCounter + 13) % wbInterval == 0) {
            WorldBorderManager.tick(server);
        }

        // Toutes les 400 ticks (20s) : caches, schedule, warnings
        if ((tickCounter + 37) % 400 == 0) {
            ProfessionRestrictionEventHandler.cleanupCaches();
            RpEssentialsPermissions.clearExpiredCache();

            LocalTime now = LocalTime.now();

            try {
                if (ScheduleConfig.ENABLE_SCHEDULE.get()
                        && !RpEssentialsScheduleManager.getSchedules().isEmpty()) {
                    RpEssentialsScheduleManager.DaySchedule active  = RpEssentialsScheduleManager.getActiveSchedule();
                    RpEssentialsScheduleManager.DaySchedule todayS  = RpEssentialsScheduleManager.getTodaySchedule();

                    if (!RpEssentialsScheduleManager.hasOpenedToday()
                            && todayS != null
                            && now.getHour()   == todayS.open().getHour()
                            && now.getMinute() == todayS.open().getMinute()) {
                        RpEssentialsScheduleManager.markOpenedToday();
                        RpEssentialsScheduleManager.sendOpeningMessage(server, todayS);
                    }

                    if (active != null) {
                        RpEssentialsScheduleManager.checkWarnings(server, now, active);
                    }

                    if (!RpEssentialsScheduleManager.hasClosedToday() && active == null) {
                        RpEssentialsScheduleManager.markClosedToday();
                        RpEssentialsScheduleManager.closeServer(server);
                    }
                }
            } catch (IllegalStateException ignored) {}

            RpEssentialsScheduleManager.tickDeathHoursNotifications(server, now);
            RpEssentialsScheduleManager.tickHrpNotifications(server, now);
        }

        // Toutes les 1200 ticks (1min) : midnight sweeps
        if ((tickCounter + 97) % 1200 == 0) {
            LocalTime now = LocalTime.now();
            RpEssentialsScheduleManager.tickMidnightSweep(server);
            TempLicenseExpirationManager.tickMidnightSweep(server, now.getHour(), now.getMinute());
            LastConnectionManager.tickAutoUnwhitelist(server, now.getHour(), now.getMinute());
        }

        tickCounter++;
        if (tickCounter >= 1200) tickCounter = 0;
    }

    // =========================================================================
    // SERVER STOPPING
    // =========================================================================

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        WorldBorderManager.clearAllCache();
        RpEssentialsPermissions.clearCache();
        RpEssentialsScheduleManager.reload();
        ImmersivePresetHelper.clearCache();
        PlaytimeManager.clearAll();
        LOGGER.info("[RPEssentials] Static caches cleared on server stop. Bye! - Finerus");
    }
}