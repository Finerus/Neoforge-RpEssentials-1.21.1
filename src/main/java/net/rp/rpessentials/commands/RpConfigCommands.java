package net.rp.rpessentials.commands;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.lang.Msg;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.*;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.PendingProfileManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.List;

import static net.rp.rpessentials.commands.RpChatUi.*;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.core.BackupManager;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.gameplay.RpEssentialsScheduleManager;
import net.rp.rpessentials.gameplay.TabBlur;
import net.rp.rpessentials.gameplay.TabListCache;
import net.rp.rpessentials.gameplay.WorldBorderManager;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ImmersivePresetHelper;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;

public class RpConfigCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var configNode = Commands.literal("config")
                .requires(source -> source.hasPermission(2));

        configNode.then(Commands.literal("reload").executes(RpConfigCommands::reloadConfig));

        configNode.then(Commands.literal("status").executes(RpConfigCommands::showStatus));

        configNode.then(Commands.literal("validate").executes(RpConfigCommands::validateConfig));

        configNode.then(Commands.literal("backup").executes(ctx -> {
            BackupManager.run(RpEssentialsConfig.BACKUP_KEEP.get());
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_BACKUP), true);
            return 1;
        }));

        configNode.then(Commands.literal("exportlang").executes(ctx -> {
            try {
                java.nio.file.Path p = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rpessentials/en_us.json");
                java.nio.file.Files.createDirectories(p.getParent());
                java.nio.file.Files.writeString(p, ConfigInspector.exportDefaults(MessagesConfig.class, ProfessionConfig.class));
                ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_EXPORT_DONE, "path", p.toString()), false);
                return 1;
            } catch (Exception e) {
                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_EXPORT_FAIL, "error", String.valueOf(e.getMessage())));
                return 0;
            }
        }));

        var setNode = Commands.literal("set");

        registerSetters(setNode);
        configNode.then(setNode);

        return configNode;
    }

    private static void registerSetters(LiteralArgumentBuilder<CommandSourceStack> set) {
        set.then(Commands.literal("proximity")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.PROXIMITY_DISTANCE))));
        set.then(Commands.literal("blur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_BLUR))));
        set.then(Commands.literal("obfuscatedNameLength")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OBFUSCATED_NAME_LENGTH))));
        set.then(Commands.literal("obfuscatePrefix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OBFUSCATE_PREFIX))));
        set.then(Commands.literal("opsSeeAll")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OPS_SEE_ALL))));
        set.then(Commands.literal("debugSelfBlur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEBUG_SELF_BLUR))));
        set.then(Commands.literal("enableSchedule")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            int r = updateConfigBool(ctx, ScheduleConfig.ENABLE_SCHEDULE);
                            RpEssentialsScheduleManager.reload();
                            return r;
                        })));
        set.then(Commands.literal("kickNonStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            int r = updateConfigBool(ctx, ScheduleConfig.KICK_NON_STAFF);
                            RpEssentialsScheduleManager.reload();
                            return r;
                        })));
        set.then(Commands.literal("enableWelcome")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_WELCOME))));
        set.then(Commands.literal("enablePlatforms")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_PLATFORMS))));
        set.then(Commands.literal("enableSilentCommands")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_SILENT_COMMANDS))));
        set.then(Commands.literal("logToStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_STAFF))));
        set.then(Commands.literal("logToConsole")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_CONSOLE))));
        set.then(Commands.literal("notifyTarget")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.NOTIFY_TARGET))));
        set.then(Commands.literal("opLevelBypass")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 4))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OP_LEVEL_BYPASS))));
        set.then(Commands.literal("useLuckPermsGroups")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.USE_LUCKPERMS_GROUPS))));
        set.then(Commands.literal("enableChatFormat")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CHAT_FORMAT))));
        set.then(Commands.literal("enableTimestamp")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_TIMESTAMP))));
        set.then(Commands.literal("markdownEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.MARKDOWN_ENABLED))));
        set.then(Commands.literal("chatMessageColor")
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("AQUA").suggest("RED").suggest("LIGHT_PURPLE")
                                    .suggest("YELLOW").suggest("WHITE").suggest("BLACK")
                                    .suggest("GOLD").suggest("GRAY").suggest("BLUE")
                                    .suggest("GREEN").suggest("DARK_GRAY").suggest("DARK_AQUA")
                                    .suggest("DARK_RED").suggest("DARK_PURPLE")
                                    .suggest("DARK_GREEN").suggest("DARK_BLUE");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String color = StringArgumentType.getString(ctx, "color");
                            ChatConfig.CHAT_MESSAGE_COLOR.set(color);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(ChatConfig.CHAT_MESSAGE_COLOR), "value", String.valueOf(color)), true);
                            return 1;
                        })));
        set.then(Commands.literal("timestampFormat")
                .then(Commands.argument("format", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String format = StringArgumentType.getString(ctx, "format");
                            ChatConfig.TIMESTAMP_FORMAT.set(format);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(ChatConfig.TIMESTAMP_FORMAT), "value", String.valueOf(format)), true);
                            return 1;
                        })));
        set.then(Commands.literal("enableColorsCommand")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_COLORS_COMMAND))));
        set.then(Commands.literal("enableSneakStealth")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_SNEAK_STEALTH))));
        set.then(Commands.literal("sneakProximityDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE))));
        set.then(Commands.literal("hideNametags")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.HIDE_NAMETAGS))));
        set.then(Commands.literal("showNametagPrefixSuffix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX))));
        set.then(Commands.literal("enableCustomJoinLeave")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE))));
        set.then(Commands.literal("joinMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.JOIN_MESSAGE.set(msg);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(ChatConfig.JOIN_MESSAGE), "value", String.valueOf(msg)), true);
                            return 1;
                        })));
        set.then(Commands.literal("leaveMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.LEAVE_MESSAGE.set(msg);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(ChatConfig.LEAVE_MESSAGE), "value", String.valueOf(msg)), true);
                            return 1;
                        })));
        set.then(Commands.literal("enableWorldBorderWarning")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING))));
        set.then(Commands.literal("worldBorderDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(100, 100000))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_DISTANCE))));
        set.then(Commands.literal("worldBorderMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            RpEssentialsConfig.WORLD_BORDER_MESSAGE.set(msg);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(RpEssentialsConfig.WORLD_BORDER_MESSAGE), "value", String.valueOf(msg)), true);
                            return 1;
                        })));
        set.then(Commands.literal("worldBorderCheckInterval")
                .then(Commands.argument("value", IntegerArgumentType.integer(20, 200))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_CHECK_INTERVAL))));
        set.then(Commands.literal("zoneMessageMode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("IMMERSIVE").suggest("CHAT").suggest("ACTION_BAR");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode").toUpperCase();
                            if (!mode.equals("IMMERSIVE") && !mode.equals("CHAT") && !mode.equals("ACTION_BAR")) {
                                ctx.getSource().sendFailure(Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_ZONE_MODES));
                                return 0;
                            }
                            RpEssentialsConfig.ZONE_MESSAGE_MODE.set(mode);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", optName(RpEssentialsConfig.ZONE_MESSAGE_MODE), "value", String.valueOf(mode)), true);
                            return 1;
                        })));
        // Death RP setters
        set.then(Commands.literal("deathRpGlobalEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED))));
        set.then(Commands.literal("deathRpWhitelistRemove")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE))));
        set.then(Commands.literal("deathRpDeathMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE))));
        set.then(Commands.literal("deathRpDeathSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND))));
        set.then(Commands.literal("deathRpDeathSoundVolume")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 10.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME))));
        set.then(Commands.literal("deathRpDeathSoundPitch")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 2.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH))));
        set.then(Commands.literal("deathRpPlayerEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MSG))));
        set.then(Commands.literal("deathRpPlayerEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MODE))));
        set.then(Commands.literal("deathRpPlayerDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MSG))));
        set.then(Commands.literal("deathRpPlayerDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MODE))));
        set.then(Commands.literal("deathRpPlayerToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND))));
        set.then(Commands.literal("deathRpGlobalEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG))));
        set.then(Commands.literal("deathRpGlobalEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE))));
        set.then(Commands.literal("deathRpGlobalDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG))));
        set.then(Commands.literal("deathRpGlobalDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE))));
        set.then(Commands.literal("deathRpGlobalToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND))));
        // Schedule day setters (delegated to RpScheduleCommands)
        RpScheduleCommands.registerSetNodes(set);
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================
    static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        // Schedules et caches dérivés
        RpEssentialsRoleManager.clearAll();
        RpEssentialsRoleManager.reload();
        RpEssentialsScheduleManager.reload();
        WarnManager.reload();
        ProfessionRestrictionManager.reloadCache();
        RpEssentialsPatternUtils.clearCache();
        ImmersivePresetHelper.clearCache();
        RpEssentialsPermissions.clearCache();
        TabListCache.reload();
        TabBlur.reload();
        RpEssentials.clearMetaCache();
        WorldBorderManager.reloadConfig();
        RpEssentialsScheduleManager.enforceOnline(ctx.getSource().getServer());

        // Données persistantes (rechargement depuis fichier)
        NicknameManager.reload();
        LicenseManager.reload();
        MuteManager.reload();
        LastConnectionManager.reload();
        AbsenceManager.reload();
        WarnManager.reload();
        PendingActionManager.reload();
        VanishManager.refreshAll(ctx.getSource().getServer());
        PendingProfileManager.reload();

        // Sync client
        if (Cfg.ready(RpEssentialsConfig.SPEC)) {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags)));
        }

        // Re-sync des restrictions de profession pour tous les joueurs connectés
        try {
            for (net.minecraft.server.level.ServerPlayer player :
                    ctx.getSource().getServer().getPlayerList().getPlayers()) {
                net.rp.rpessentials.profession.ProfessionSyncHelper.syncToPlayer(player);
            }
        } catch (Exception ignored) {}

        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_RELOADED), true);
        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RpEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                LicenseManager.getAllLicenses().size(),
                WarnManager.getAll().size(),
                MuteManager.getAllMutes().size(),
                dataFolder);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        java.util.function.Function<ModConfigSpec.ConfigValue<String>, String> L = cv -> Msg.get(ctx.getSource(), cv);
        String schedule;
        try {
            schedule = !ScheduleConfig.ENABLE_SCHEDULE.get() ? L.apply(MessagesConfig.STAFF_STATS_DISABLED)
                    : RpEssentialsScheduleManager.isServerOpen() ? L.apply(MessagesConfig.STAFF_STATS_OPEN)
                    : L.apply(MessagesConfig.STAFF_STATS_CLOSED);
        } catch (Exception e) { schedule = L.apply(MessagesConfig.STAFF_STATS_NA); }

        String luckPerms;
        try { net.luckperms.api.LuckPermsProvider.get(); luckPerms = L.apply(MessagesConfig.STAFF_CFG_DETECTED); }
        catch (NoClassDefFoundError | IllegalStateException e) { luckPerms = L.apply(MessagesConfig.STAFF_CFG_ABSENT); }

        StringBuilder sb = new StringBuilder();
        sb.append(L.apply(MessagesConfig.STAFF_CFG_STATUS_TITLE)).append("\n").append(LINE).append("\n");

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_OBFUSCATION)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLUR), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_BLUR.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_RANGE), value(ctx.getSource(), () -> RpEssentialsConfig.PROXIMITY_DISTANCE.get()) + L.apply(MessagesConfig.STAFF_CFG_BLOCKS)),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SNEAK), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_OPS_SEE_ALL), flag(ctx.getSource(), () -> RpEssentialsConfig.OPS_SEE_ALL.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_HIDE_NAMETAGS), flag(ctx.getSource(), () -> RpEssentialsConfig.HIDE_NAMETAGS.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WHITELIST), value(ctx.getSource(), () -> RpEssentialsConfig.WHITELIST.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLACKLIST), value(ctx.getSource(), () -> RpEssentialsConfig.BLACKLIST.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_ALWAYS_VISIBLE), value(ctx.getSource(), () -> RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_PERMISSIONS)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_LUCKPERMS), luckPerms),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_USE_GROUPS), flag(ctx.getSource(), () -> RpEssentialsConfig.USE_LUCKPERMS_GROUPS.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_OP_LEVEL), value(ctx.getSource(), () -> RpEssentialsConfig.OP_LEVEL_BYPASS.get()))))
                .append(row(kv(L.apply(MessagesConfig.STAFF_CFG_L_ROLES), value(ctx.getSource(), () -> RpEssentialsConfig.ROLES.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_SCHEDULE)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SCHEDULE), schedule),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DEATH_RP), flag(ctx.getSource(), () -> RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DEATH_HOURS), flag(ctx.getSource(), () -> ScheduleConfig.DEATH_HOURS_ENABLED.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WHITELIST_REMOVAL), flag(ctx.getSource(), () -> RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_HRP_HOURS), flag(ctx.getSource(), () -> ScheduleConfig.ENABLE_HRP_HOURS.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_CHAT)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_FORMAT), flag(ctx.getSource(), () -> ChatConfig.ENABLE_CHAT_FORMAT.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PROXIMITY), flag(ctx.getSource(), () -> ChatConfig.ENABLE_PROXIMITY_CHAT.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_MARKDOWN), flag(ctx.getSource(), () -> ChatConfig.MARKDOWN_ENABLED.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_TIMESTAMP), flag(ctx.getSource(), () -> ChatConfig.ENABLE_TIMESTAMP.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_JOIN_LEAVE), flag(ctx.getSource(), () -> ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_COLOR), value(ctx.getSource(), () -> ChatConfig.CHAT_MESSAGE_COLOR.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_BORDER)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_ENABLED), flag(ctx.getSource(), () -> RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DISTANCE), value(ctx.getSource(), () -> RpEssentialsConfig.WORLD_BORDER_DISTANCE.get()) + L.apply(MessagesConfig.STAFF_CFG_BLOCKS)),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_TELEPORT), flag(ctx.getSource(), () -> RpEssentialsConfig.WORLD_BORDER_TELEPORT_ENABLED.get()))))
                .append(row(kv(L.apply(MessagesConfig.STAFF_CFG_L_NAMED_ZONES), value(ctx.getSource(), () -> RpEssentialsConfig.NAMED_ZONES.get().size()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_MODERATION)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WARNS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_WARN_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_MUTES), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_MUTE_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_LAST_CONNECTION), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_LAST_CONNECTION.get()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_AUTO_UNWHITELIST), flag(ctx.getSource(), () -> ModerationConfig.AUTO_UNWHITELIST_ENABLED.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SILENT_COMMANDS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_SILENT_COMMANDS.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PLATFORMS), flag(ctx.getSource(), () -> ModerationConfig.ENABLE_PLATFORMS.get()))));

        sb.append(L.apply(MessagesConfig.STAFF_CFG_S_PROFESSIONS)).append("\n")
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_PROFESSIONS), value(ctx.getSource(), () -> ProfessionConfig.PROFESSIONS.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLOCKED_CRAFTS), value(ctx.getSource(), () -> ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get().size())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_BLOCKED_ITEMS), value(ctx.getSource(), () -> ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get().size()))))
                .append(row(
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_DICE), flag(ctx.getSource(), () -> RpConfig.ENABLE_DICE_SYSTEM.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_SELF_NICK), flag(ctx.getSource(), () -> RpConfig.ENABLE_SELF_NICK.get())),
                        kv(L.apply(MessagesConfig.STAFF_CFG_L_WELCOME), flag(ctx.getSource(), () -> ScheduleConfig.ENABLE_WELCOME.get()))));
        sb.append(LINE);

        String msg = sb.toString();
        RpChatUi.sendFormatted(ctx, msg);
        return 1;
    }

    static int validateConfig(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = ConfigValidator.validate(ctx.getSource().getPlayer());
        if (problems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALID), false);
            return 1;
        }
        StringBuilder sb = new StringBuilder(Msg.get(ctx.getSource(), MessagesConfig.STAFF_CFG_PROBLEMS, "count", String.valueOf(problems.size())) + "\n" + LINE + "\n");
        for (String p : problems) sb.append("§c- §f").append(p).append("\n");
        sb.append(LINE);
        RpChatUi.sendFormatted(ctx, sb.toString());
        return 0;
    }

    // =========================================================================
    // HELPERS (package-private, reused by other command classes)
    // =========================================================================

    /** Name of an option as written in the config file (its last path element). */
    static String optName(ModConfigSpec.ConfigValue<?> cv) {
        java.util.List<String> p = cv.getPath();
        return p.get(p.size() - 1);
    }

    static int updateConfigInt(CommandContext<CommandSourceStack> ctx, ModConfigSpec.IntValue config) {
        String name = optName(config);
        int val = IntegerArgumentType.getInteger(ctx, "value");
        config.set(val);
        config.save();
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_VALUE_SET, "name", name, "value", String.valueOf(val)), true);
        return 1;
    }

    static int updateConfigBool(CommandContext<CommandSourceStack> ctx, ModConfigSpec.BooleanValue config) {
        String name = optName(config);
        boolean val = BoolArgumentType.getBool(ctx, "value");
        config.set(val);
        config.save();
        if (config == RpEssentialsConfig.HIDE_NAMETAGS) {
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(val)));
        }
        ctx.getSource().sendSuccess(() -> Msg.c(ctx.getSource(), MessagesConfig.STAFF_CFG_TOGGLE_SET, "name", name,
                "state", Msg.get(ctx.getSource(), val ? MessagesConfig.STAFF_CFG_ENABLED : MessagesConfig.STAFF_CFG_DISABLED)), true);
        return 1;
    }

    static int updateConfigString(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.ConfigValue<String> configValue) {
        String label = configValue == null ? "" : optName(configValue);
        if (Cfg.ready(MessagesConfig.SPEC)) {
            String value = StringArgumentType.getString(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", value)), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }

    static int updateConfigDouble(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.DoubleValue configValue) {
        String label = configValue == null ? "" : optName(configValue);
        if (Cfg.ready(MessagesConfig.SPEC)) {
            double value = DoubleArgumentType.getDouble(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", String.valueOf(value))), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal(Msg.get(ctx.getSource(), MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }
}
