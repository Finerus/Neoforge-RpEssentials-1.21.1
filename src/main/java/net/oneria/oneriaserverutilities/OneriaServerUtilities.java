package net.oneria.oneriaserverutilities;

import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.EnumSet;

@Mod("oneriaserverutilities")
public class OneriaServerUtilities {
    public static final String MODID = "oneriaserverutilities";
    public static final Logger LOGGER = LogUtils.getLogger();
    private int tickCounter = 0;

    public OneriaServerUtilities(IEventBus modEventBus, ModContainer modContainer) {
        // Migrer l'ancienne config si nécessaire (doit être fait AVANT registerConfig)
        ConfigMigrator.migrateIfNeeded();

        // Enregistrer les configs dans le dossier oneria/
        modContainer.registerConfig(ModConfig.Type.SERVER, OneriaConfig.SPEC,       "oneria/oneria-core.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ChatConfig.SPEC,         "oneria/oneria-chat.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ScheduleConfig.SPEC,     "oneria/oneria-schedule.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ModerationConfig.SPEC,   "oneria/oneria-moderation.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ProfessionConfig.SPEC,   "oneria/oneria-professions.toml");

        OneriaItems.ITEMS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        // Initialize systems when config loads
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getType() == ModConfig.Type.SERVER) {
                OneriaScheduleManager.reload();
                ProfessionRestrictionManager.reloadCache();
                LOGGER.info("Schedule system and profession restrictions initialized after config load");
            }
        });
    }

    // Securely retrieve LuckPerms prefix
    public static String getPlayerPrefix(ServerPlayer player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                return prefix != null ? prefix : "";
            }
        } catch (IllegalStateException e) {
            // LuckPerms not loaded - this is normal
            return "";
        } catch (NoClassDefFoundError e) {
            // LuckPerms not present - this is normal
            LOGGER.debug("LuckPerms not available (NoClassDefFoundError)");
            return "";
        } catch (Exception e) {
            LOGGER.debug("LuckPerms not available: {}", e.getMessage());
        }
        return "";
    }

    // Securely retrieve LuckPerms suffix
    public static String getPlayerSuffix(ServerPlayer player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUUID());
            if (user != null) {
                String suffix = user.getCachedData().getMetaData().getSuffix();
                return suffix != null ? suffix : "";
            }
        } catch (IllegalStateException e) {
            // LuckPerms not loaded - this is normal
            return "";
        } catch (NoClassDefFoundError e) {
            // LuckPerms not present - this is normal
            LOGGER.debug("LuckPerms not available (NoClassDefFoundError)");
            return "";
        } catch (Exception e) {
            LOGGER.debug("LuckPerms is not available: {}", e.getMessage());
        }
        return "";
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) return;

        // Déclaré ICI — visible par tous les blocs
        var server = event.getServer();

        if (tickCounter++ % 40 == 0) {
            if (OneriaConfig.ENABLE_BLUR.get()) {
                ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                        server.getPlayerList().getPlayers()
                );
                server.getPlayerList().broadcastAll(packet);
            }
        }

        if (tickCounter % 400 == 0) {
            ProfessionRestrictionEventHandler.cleanupCaches();
        }

        if (tickCounter % 1200 == 0) {
            java.time.LocalTime now = java.time.LocalTime.now();
            TempLicenseExpirationManager.tickMidnightSweep(server, now.getHour(), now.getMinute());
        }

        OneriaScheduleManager.tick(server);
        WorldBorderManager.tick(server);
    }
}