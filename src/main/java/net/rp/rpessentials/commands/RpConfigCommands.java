package net.rp.rpessentials.commands;

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
import net.rp.rpessentials.moderation.WarnManager;
import net.rp.rpessentials.network.HideNametagsPacket;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import static net.rp.rpessentials.commands.RpChatUi.*;

public class RpConfigCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var configNode = Commands.literal("config")
                .requires(source -> source.hasPermission(2));

        configNode.then(Commands.literal("reload").executes(RpConfigCommands::reloadConfig));
        configNode.then(Commands.literal("status").executes(RpConfigCommands::showStatus));

        var setNode = Commands.literal("set");
        registerSetters(setNode);
        configNode.then(setNode);

        return configNode;
    }

    private static void registerSetters(LiteralArgumentBuilder<CommandSourceStack> set) {
        set.then(Commands.literal("proximity")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.PROXIMITY_DISTANCE, "Proximity distance"))));
        set.then(Commands.literal("blur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_BLUR, "Blur"))));
        set.then(Commands.literal("obfuscatedNameLength")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OBFUSCATED_NAME_LENGTH, "Hidden name length"))));
        set.then(Commands.literal("obfuscatePrefix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OBFUSCATE_PREFIX, "Obfuscate prefix"))));
        set.then(Commands.literal("opsSeeAll")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OPS_SEE_ALL, "Admin View"))));
        set.then(Commands.literal("debugSelfBlur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEBUG_SELF_BLUR, "Debug Self Blur"))));
        set.then(Commands.literal("enableSchedule")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            int r = updateConfigBool(ctx, ScheduleConfig.ENABLE_SCHEDULE, "Schedule System");
                            RpEssentialsScheduleManager.reload();
                            return r;
                        })));
        set.then(Commands.literal("kickNonStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            int r = updateConfigBool(ctx, ScheduleConfig.KICK_NON_STAFF, "Kick Non-Staff");
                            RpEssentialsScheduleManager.reload();
                            return r;
                        })));
        set.then(Commands.literal("enableWelcome")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_WELCOME, "Welcome Message"))));
        set.then(Commands.literal("enablePlatforms")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_PLATFORMS, "Platforms System"))));
        set.then(Commands.literal("enableSilentCommands")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_SILENT_COMMANDS, "Silent Commands"))));
        set.then(Commands.literal("logToStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_STAFF, "Log to Staff"))));
        set.then(Commands.literal("logToConsole")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_CONSOLE, "Log to Console"))));
        set.then(Commands.literal("notifyTarget")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.NOTIFY_TARGET, "Notify Target"))));
        set.then(Commands.literal("opLevelBypass")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 4))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OP_LEVEL_BYPASS, "OP Level Bypass"))));
        set.then(Commands.literal("useLuckPermsGroups")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.USE_LUCKPERMS_GROUPS, "Use LuckPerms Groups"))));
        set.then(Commands.literal("enableChatFormat")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CHAT_FORMAT, "Chat Format"))));
        set.then(Commands.literal("enableTimestamp")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_TIMESTAMP, "Timestamp"))));
        set.then(Commands.literal("markdownEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.MARKDOWN_ENABLED, "Markdown"))));
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
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Chat Message Color set to: " + color), true);
                            return 1;
                        })));
        set.then(Commands.literal("timestampFormat")
                .then(Commands.argument("format", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String format = StringArgumentType.getString(ctx, "format");
                            ChatConfig.TIMESTAMP_FORMAT.set(format);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Timestamp Format set to: " + format), true);
                            return 1;
                        })));
        set.then(Commands.literal("enableColorsCommand")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_COLORS_COMMAND, "Colors Command"))));
        set.then(Commands.literal("enableSneakStealth")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_SNEAK_STEALTH, "Sneak Stealth Mode"))));
        set.then(Commands.literal("sneakProximityDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE, "Sneak Detection Distance"))));
        set.then(Commands.literal("hideNametags")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.HIDE_NAMETAGS, "Hide Nametags"))));
        set.then(Commands.literal("showNametagPrefixSuffix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX, "Show Nametag Prefix/Suffix"))));
        set.then(Commands.literal("enableCustomJoinLeave")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE, "Custom Join/Leave Messages"))));
        set.then(Commands.literal("joinMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.JOIN_MESSAGE.set(msg);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Join Message set to: " + msg), true);
                            return 1;
                        })));
        set.then(Commands.literal("leaveMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.LEAVE_MESSAGE.set(msg);
                            ChatConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Leave Message set to: " + msg), true);
                            return 1;
                        })));
        set.then(Commands.literal("enableWorldBorderWarning")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING, "World Border Warning"))));
        set.then(Commands.literal("worldBorderDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(100, 100000))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_DISTANCE, "World Border Distance"))));
        set.then(Commands.literal("worldBorderMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            RpEssentialsConfig.WORLD_BORDER_MESSAGE.set(msg);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] World Border Message set to: " + msg), true);
                            return 1;
                        })));
        set.then(Commands.literal("worldBorderCheckInterval")
                .then(Commands.argument("value", IntegerArgumentType.integer(20, 200))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_CHECK_INTERVAL, "World Border Check Interval"))));
        set.then(Commands.literal("zoneMessageMode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("IMMERSIVE").suggest("CHAT").suggest("ACTION_BAR");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode").toUpperCase();
                            if (!mode.equals("IMMERSIVE") && !mode.equals("CHAT") && !mode.equals("ACTION_BAR")) {
                                ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] Valid modes: IMMERSIVE, CHAT, ACTION_BAR"));
                                return 0;
                            }
                            RpEssentialsConfig.ZONE_MESSAGE_MODE.set(mode);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Zone Message Mode set to: " + mode), true);
                            return 1;
                        })));
        // Death RP setters
        set.then(Commands.literal("deathRpGlobalEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED, "Global Death RP enabled"))));
        set.then(Commands.literal("deathRpWhitelistRemove")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE, "Death RP whitelist removal"))));
        set.then(Commands.literal("deathRpDeathMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE, "Death RP death message"))));
        set.then(Commands.literal("deathRpDeathSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND, "Death RP death sound"))));
        set.then(Commands.literal("deathRpDeathSoundVolume")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 10.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME, "Death RP death sound volume"))));
        set.then(Commands.literal("deathRpDeathSoundPitch")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 2.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH, "Death RP death sound pitch"))));
        set.then(Commands.literal("deathRpPlayerEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MSG, "Death RP player enable message"))));
        set.then(Commands.literal("deathRpPlayerEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MODE, "Death RP player enable mode"))));
        set.then(Commands.literal("deathRpPlayerDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MSG, "Death RP player disable message"))));
        set.then(Commands.literal("deathRpPlayerDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MODE, "Death RP player disable mode"))));
        set.then(Commands.literal("deathRpPlayerToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND, "Death RP player toggle sound"))));
        set.then(Commands.literal("deathRpGlobalEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG, "Death RP global enable message"))));
        set.then(Commands.literal("deathRpGlobalEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE, "Death RP global enable mode"))));
        set.then(Commands.literal("deathRpGlobalDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG, "Death RP global disable message"))));
        set.then(Commands.literal("deathRpGlobalDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE, "Death RP global disable mode"))));
        set.then(Commands.literal("deathRpGlobalToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND, "Death RP global toggle sound"))));
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
        RpEssentialsPermissions.clearCache();
        net.rp.rpessentials.TabListCache.reload();

        // Données persistantes (rechargement depuis fichier)
        NicknameManager.reload();
        net.rp.rpessentials.profession.LicenseManager.reload();
        net.rp.rpessentials.moderation.MuteManager.reload();
        net.rp.rpessentials.moderation.LastConnectionManager.reload();
        net.rp.rpessentials.moderation.WarnManager.reload();

        // Sync client
        try {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags)));
        } catch (IllegalStateException ignored) {}

        // Re-sync des restrictions de profession pour tous les joueurs connectés
        try {
            for (net.minecraft.server.level.ServerPlayer player :
                    ctx.getSource().getServer().getPlayerList().getPlayers()) {
                net.rp.rpessentials.profession.ProfessionSyncHelper.syncToPlayer(player);
            }
        } catch (Exception ignored) {}

        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Configuration reloaded."), true);
        String dataFolder = RpEssentialsDataPaths.getDataFolder().getAbsolutePath();
        RpEssentials.LOGGER.info("[RpEssentials] Data layer ready — {} nickname(s), {} license(s), {} warn(s), {} mute(s). Data folder: {}",
                NicknameManager.count(),
                net.rp.rpessentials.profession.LicenseManager.getAllLicenses().size(),
                net.rp.rpessentials.moderation.WarnManager.getAll().size(),
                net.rp.rpessentials.moderation.MuteManager.getAllMutes().size(),
                dataFolder);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        String schedule;
        try {
            schedule = !ScheduleConfig.ENABLE_SCHEDULE.get() ? "§7Disabled"
                    : RpEssentialsScheduleManager.isServerOpen() ? "§aOpen" : "§cClosed";
        } catch (Exception e) { schedule = "§8N/A"; }

        String luckPerms;
        try { net.luckperms.api.LuckPermsProvider.get(); luckPerms = "§aDetected"; }
        catch (NoClassDefFoundError | IllegalStateException e) { luckPerms = "§7Absent"; }

        StringBuilder sb = new StringBuilder();
        sb.append("§6§lRpEssentials §8- §7Status\n").append(LINE).append("\n");

        sb.append("§eObfuscation\n")
                .append(row(
                        kv("Blur", flag(() -> RpEssentialsConfig.ENABLE_BLUR.get())),
                        kv("Range", value(() -> RpEssentialsConfig.PROXIMITY_DISTANCE.get()) + "§7 blocks"),
                        kv("Sneak", flag(() -> RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get()))))
                .append(row(
                        kv("Ops see all", flag(() -> RpEssentialsConfig.OPS_SEE_ALL.get())),
                        kv("Hide nametags", flag(() -> RpEssentialsConfig.HIDE_NAMETAGS.get()))))
                .append(row(
                        kv("Whitelist", value(() -> RpEssentialsConfig.WHITELIST.get().size())),
                        kv("Blacklist", value(() -> RpEssentialsConfig.BLACKLIST.get().size())),
                        kv("Always visible", value(() -> RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().size()))));

        sb.append("§ePermissions\n")
                .append(row(
                        kv("LuckPerms", luckPerms),
                        kv("Use groups", flag(() -> RpEssentialsConfig.USE_LUCKPERMS_GROUPS.get())),
                        kv("OP level", value(() -> RpEssentialsConfig.OP_LEVEL_BYPASS.get()))))
                .append(row(kv("Roles", value(() -> RpEssentialsConfig.ROLES.get().size()))));

        sb.append("§eSchedule and Death RP\n")
                .append(row(
                        kv("Schedule", schedule),
                        kv("Death RP", flag(() -> RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get())),
                        kv("Death hours", flag(() -> ScheduleConfig.DEATH_HOURS_ENABLED.get()))))
                .append(row(
                        kv("Whitelist removal", flag(() -> RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get())),
                        kv("HRP hours", flag(() -> ScheduleConfig.ENABLE_HRP_HOURS.get()))));

        sb.append("§eChat\n")
                .append(row(
                        kv("Format", flag(() -> ChatConfig.ENABLE_CHAT_FORMAT.get())),
                        kv("Proximity", flag(() -> ChatConfig.ENABLE_PROXIMITY_CHAT.get())),
                        kv("Markdown", flag(() -> ChatConfig.MARKDOWN_ENABLED.get()))))
                .append(row(
                        kv("Timestamp", flag(() -> ChatConfig.ENABLE_TIMESTAMP.get())),
                        kv("Join/Leave", flag(() -> ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get())),
                        kv("Color", value(() -> ChatConfig.CHAT_MESSAGE_COLOR.get()))));

        sb.append("§eWorld border\n")
                .append(row(
                        kv("Enabled", flag(() -> RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get())),
                        kv("Distance", value(() -> RpEssentialsConfig.WORLD_BORDER_DISTANCE.get()) + "§7 blocks"),
                        kv("Teleport", flag(() -> RpEssentialsConfig.WORLD_BORDER_TELEPORT_ENABLED.get()))))
                .append(row(kv("Named zones", value(() -> RpEssentialsConfig.NAMED_ZONES.get().size()))));

        sb.append("§eModeration\n")
                .append(row(
                        kv("Warns", flag(() -> ModerationConfig.ENABLE_WARN_SYSTEM.get())),
                        kv("Mutes", flag(() -> ModerationConfig.ENABLE_MUTE_SYSTEM.get())),
                        kv("Last connection", flag(() -> ModerationConfig.ENABLE_LAST_CONNECTION.get()))))
                .append(row(
                        kv("Auto unwhitelist", flag(() -> ModerationConfig.AUTO_UNWHITELIST_ENABLED.get())),
                        kv("Silent commands", flag(() -> ModerationConfig.ENABLE_SILENT_COMMANDS.get())),
                        kv("Platforms", flag(() -> ModerationConfig.ENABLE_PLATFORMS.get()))));

        sb.append("§eProfessions and RP\n")
                .append(row(
                        kv("Professions", value(() -> ProfessionConfig.PROFESSIONS.get().size())),
                        kv("Blocked crafts", value(() -> ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get().size())),
                        kv("Blocked items", value(() -> ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get().size()))))
                .append(row(
                        kv("Dice", flag(() -> RpConfig.ENABLE_DICE_SYSTEM.get())),
                        kv("Self nick", flag(() -> RpConfig.ENABLE_SELF_NICK.get())),
                        kv("Welcome", flag(() -> ScheduleConfig.ENABLE_WELCOME.get()))));
        sb.append(LINE);

        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // =========================================================================
    // HELPERS (package-private, reused by other command classes)
    // =========================================================================

    static int updateConfigInt(CommandContext<CommandSourceStack> ctx, ModConfigSpec.IntValue config, String name) {
        int val = IntegerArgumentType.getInteger(ctx, "value");
        config.set(val);
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + name + " set to: " + val), true);
        return 1;
    }

    static int updateConfigBool(CommandContext<CommandSourceStack> ctx, ModConfigSpec.BooleanValue config, String name) {
        boolean val = BoolArgumentType.getBool(ctx, "value");
        config.set(val);
        config.save();
        if (config == RpEssentialsConfig.HIDE_NAMETAGS) {
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(val)));
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + name + " : " + (val ? "§aENABLED" : "§cDISABLED")), true);
        return 1;
    }

    static int updateConfigString(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.ConfigValue<String> configValue, String label) {
        try {
            String value = StringArgumentType.getString(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", value)), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }

    static int updateConfigDouble(CommandContext<CommandSourceStack> ctx,
                                   ModConfigSpec.DoubleValue configValue, String label) {
        try {
            double value = DoubleArgumentType.getDouble(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", String.valueOf(value))), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }
}
