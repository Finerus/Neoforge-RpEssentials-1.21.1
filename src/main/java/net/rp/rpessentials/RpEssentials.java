package net.rp.rpessentials;

import net.rp.rpessentials.config.Cfg;
import com.mojang.logging.LogUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.client.RpKeyBindings;
import net.rp.rpessentials.compat.LuckPermsSync;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.LastConnectionManager;
import net.rp.rpessentials.moderation.PlaytimeManager;
import net.rp.rpessentials.profession.ProfessionRestrictionEventHandler;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;
import net.rp.rpessentials.profession.TempLicenseExpirationManager;
import org.slf4j.Logger;
import net.rp.rpessentials.compat.CuriosCompat;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import net.rp.rpessentials.core.BackupManager;
import net.rp.rpessentials.core.TickScheduler;
import net.rp.rpessentials.core.ConfigRefresh;
import net.rp.rpessentials.core.RpEssentialsEventHandler;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.gameplay.WorldBorderManager;
import net.rp.rpessentials.items.RpEssentialsItems;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ImmersivePresetHelper;
import net.rp.rpessentials.util.NameCache;

@Mod("rpessentials")
public class RpEssentials {

    public static final String MODID = "rpessentials";
    public static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Periodic server tasks. Offsets spread the tasks that share a period over different ticks.
     * 20 ticks = 1 second.
     */
    private final TickScheduler ticks = new TickScheduler()
            .every(40, 0, "tab blur", TabBlur::tick)
            .every(72000, 36000, "history purge", s -> net.rp.rpessentials.moderation.HistoryManager.tick())
            .every(20, 0, "zone views", WorldBorderManager::tickZoneViews)
            .every(() -> Cfg.get(RpEssentialsConfig.WORLD_BORDER_CHECK_INTERVAL, 100), 13, "world border", WorldBorderManager::tick)
            .every(400, 37, "expired caches", s -> {
                RpEssentialsRoleManager.clearExpired();
                ProfessionRestrictionEventHandler.cleanupCaches();
                RpEssentialsPermissions.clearExpiredCache();
            })
            .every(400, 37, "schedule", RpEssentialsScheduleManager::tick)
            .every(1200, 97, "midnight sweeps", s -> {
                RpEssentialsScheduleManager.tickMidnightSweep(s);
                TempLicenseExpirationManager.tickMidnightSweep(s);
            })
            .every(1200, 97, "auto unwhitelist", LastConnectionManager::tickAutoUnwhitelist)
            .every(1200, 97, "backups", s -> BackupManager.tick())
            .every(1200, 97, "death rp history purge", s -> net.rp.rpessentials.moderation.DeathRPManager.tickDailyPurge())
            .every(1200, 97, "playtime flush", s -> PlaytimeManager.flush());
    private final java.util.Set<Object> pendingSpecs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile Boolean lpPresent = null;
    private static final long LP_META_TTL_MS = 30000L;
    private record LpMeta(String prefix, String suffix, long expiry) {}
    private static final Map<UUID, LpMeta> lpMetaCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean refreshPending = new java.util.concurrent.atomic.AtomicBoolean(false);

    public RpEssentials(IEventBus modEventBus, ModContainer modContainer) {

        modContainer.registerConfig(ModConfig.Type.SERVER, RpEssentialsConfig.SPEC, "rpessentials/rpessentials-core.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ChatConfig.SPEC,         "rpessentials/rpessentials-chat.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ScheduleConfig.SPEC,     "rpessentials/rpessentials-schedule.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ModerationConfig.SPEC,   "rpessentials/rpessentials-moderation.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ProfessionConfig.SPEC,   "rpessentials/rpessentials-professions.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, MessagesConfig.SPEC,     "rpessentials/rpessentials-messages.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, RpConfig.SPEC,           "rpessentials/rpessentials-rp.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, net.rp.rpessentials.config.ClientConfig.SPEC, "rpessentials/rpessentials-client.toml");

        modEventBus.addListener(RpKeyBindings::onRegisterKeyMappings);
        RpEssentialsItems.ITEMS.register(modEventBus);
        try {
            CuriosCompat.register(modEventBus);
            LOGGER.info("[RpEssentials] Curios compatibility enabled.");
        } catch (NoClassDefFoundError e) {
            LOGGER.info("[RpEssentials] Curios not found, skipping compatibility.");
        }
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getType() == ModConfig.Type.SERVER && event.getConfig().getSpec() == ProfessionConfig.SPEC) {
                RpEssentialsScheduleManager.reload();
                ProfessionRestrictionManager.reloadCache();
                ImmersivePresetHelper.clearCache();
                LOGGER.info("[RpEssentials] Config loaded: schedule, professions & immersive presets initialized.");
            }
        });
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getType() != ModConfig.Type.SERVER) return;
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv == null) return;
            pendingSpecs.add(event.getConfig().getSpec());
            if (!refreshPending.compareAndSet(false, true)) return;
            java.util.concurrent.CompletableFuture.runAsync(() -> srv.execute(() -> {
                refreshPending.set(false);
                java.util.List<Object> specs = new java.util.ArrayList<>(pendingSpecs);
                pendingSpecs.clear();
                for (Object s : specs) ConfigRefresh.refresh(srv, s);
                // A typo written in a file is kept there: tell the staff online.
                net.rp.rpessentials.config.ConfigValidator.warnOnlineStaff(srv);
            }), java.util.concurrent.CompletableFuture.delayedExecutor(1500, java.util.concurrent.TimeUnit.MILLISECONDS));
        });
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading e) ->
                ProfessionRestrictionEventHandler.clearTooltipCache());
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) ->
                ProfessionRestrictionEventHandler.clearTooltipCache());
    }

    // =========================================================================
    // LUCKPERMS
    // =========================================================================
    private static boolean luckPermsPresent() {
        Boolean p = lpPresent;
        if (p == null) {
            try {
                Class.forName("net.luckperms.api.LuckPermsProvider", false, RpEssentials.class.getClassLoader());
                p = true;
            } catch (ClassNotFoundException | LinkageError e) {
                p = false;
            }
            lpPresent = p;
        }
        return p;
    }

    private static LpMeta lpMeta(ServerPlayer player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        LpMeta cached = lpMetaCache.get(id);
        if (cached != null && cached.expiry() > now) return cached;

        String prefix = "";
        String suffix = "";
        if (luckPermsPresent()) {
            try {
                User user = LuckPermsProvider.get().getUserManager().getUser(id);
                if (user == null) return new LpMeta("", "", 0L);
                String p = user.getCachedData().getMetaData().getPrefix();
                String s = user.getCachedData().getMetaData().getSuffix();
                prefix = p != null ? p : "";
                suffix = s != null ? s : "";
            } catch (IllegalStateException | NoClassDefFoundError ignored) {}
        }
        LpMeta meta = new LpMeta(prefix, suffix, now + LP_META_TTL_MS);
        lpMetaCache.put(id, meta);
        return meta;
    }

    public static String getPlayerPrefix(ServerPlayer player) { return lpMeta(player).prefix(); }

    public static String getPlayerSuffix(ServerPlayer player) { return lpMeta(player).suffix(); }

    public static void invalidateMeta(UUID id) { lpMetaCache.remove(id); }

    public static void clearMetaCache() { lpMetaCache.clear(); }

    // =========================================================================
    // SERVER TICK
    // =========================================================================
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ticks.tick(event.getServer());
    }

    // =========================================================================
    // SERVER STOPPING
    // =========================================================================
    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        RpEssentialsEventHandler.flushOnlinePlayers(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        RpEssentialsRoleManager.clearAll();
        WorldBorderManager.clearAllCache();
        RpEssentialsPermissions.clearCache();
        RpEssentialsScheduleManager.reload();
        ImmersivePresetHelper.clearCache();
        PlaytimeManager.clearAll();
        TabBlur.clearAll();
        clearMetaCache();
        if (Cfg.ready(ModerationConfig.SPEC)) {
            if (net.rp.rpessentials.config.ModerationConfig.WARN_AUTO_PURGE_EXPIRED.get())
                net.rp.rpessentials.moderation.WarnManager.purgeExpiredWarns();
        }
        NameCache.clear();
        RpEssentialsIO.shutdown();
        net.rp.rpessentials.compat.DiscordWebhook.shutdown();
        LuckPermsSync.stop();
        LOGGER.info("[RpEssentials] Static caches cleared on server stop. Bye! - Finerus");
    }
}