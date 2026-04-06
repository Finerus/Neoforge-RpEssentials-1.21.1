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
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.RpEssentialsScheduleManager;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.network.HideNametagsPacket;

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
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_SCHEDULE, "Schedule System"))));
        set.then(Commands.literal("kickNonStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.KICK_NON_STAFF, "Kick Non-Staff"))));
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
        RpEssentialsScheduleManager.reload();
        RpEssentialsPermissions.clearCache();
        NicknameManager.reload();
        try {
            boolean hideNametags = RpEssentialsConfig.HIDE_NAMETAGS.get();
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player ->
                    PacketDistributor.sendToPlayer(player, new HideNametagsPacket(hideNametags)));
        } catch (IllegalStateException ignored) {}
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Configuration reloaded!"), true);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            final java.util.function.Function<java.util.function.Supplier<?>, String> safe = (supplier) -> {
                try {
                    Object val = supplier.get();
                    return val != null ? val.toString() : "N/A";
                } catch (Exception e) { return "N/A"; }
            };
            String scheduleStatus;
            try {
                scheduleStatus = RpEssentialsScheduleManager.isServerOpen() ? "§aOPEN" : "§cCLOSED";
            } catch (Exception e) { scheduleStatus = "§7N/A"; }

            String statusMessage =
                    "§6╔═══════════════════════════════════╗\n" +
                    "§6║  §e§lRPESSENTIALS - STATUS§r         §6║\n" +
                    "§6╠═══════════════════════════════════╣\n" +
                    "§6║ §7Obfuscation\n" +
                    "§6║  §eBlur: §f" + safe.apply(() -> RpEssentialsConfig.ENABLE_BLUR.get()) + "\n" +
                    "§6║  §eProximity: §f" + safe.apply(() -> RpEssentialsConfig.PROXIMITY_DISTANCE.get()) + " blocks\n" +
                    "§6║  §eObfuscate Prefix: §f" + safe.apply(() -> RpEssentialsConfig.OBFUSCATE_PREFIX.get()) + "\n" +
                    "§6║  §eOPs See All: §f" + safe.apply(() -> RpEssentialsConfig.OPS_SEE_ALL.get()) + "\n" +
                    "§6║  §eHide Nametags: §f" + safe.apply(() -> RpEssentialsConfig.HIDE_NAMETAGS.get()) + "\n" +
                    "§6║  §eSneak Stealth: §f" + safe.apply(() -> RpEssentialsConfig.ENABLE_SNEAK_STEALTH.get()) + "\n" +
                    "§6║  §eSneak Distance: §f" + safe.apply(() -> RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE.get()) + " blocks\n" +
                    "§6║  §eAlways Visible: §f" + safe.apply(() -> RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().size()) + " players\n" +
                    "§6║\n" +
                    "§6║ §7Schedule: " + scheduleStatus + "\n" +
                    "§6║\n" +
                    "§6║ §7Death RP\n" +
                    "§6║  §eGlobal enabled    : §f" + safe.apply(() -> RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get()) + "\n" +
                    "§6║  §eWhitelist removal : §f" + safe.apply(() -> RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get()) + "\n" +
                    "§6║  §eDeath sound       : §f" + safe.apply(() -> RpEssentialsConfig.DEATH_RP_DEATH_SOUND.get()) + "\n" +
                    "§6║\n" +
                    "§6║ §7Chat System\n" +
                    "§6║  §eChat Format: §f" + safe.apply(() -> ChatConfig.ENABLE_CHAT_FORMAT.get()) + "\n" +
                    "§6║  §eTimestamp: §f" + safe.apply(() -> ChatConfig.ENABLE_TIMESTAMP.get()) + "\n" +
                    "§6║  §eMarkdown: §f" + safe.apply(() -> ChatConfig.MARKDOWN_ENABLED.get()) + "\n" +
                    "§6║  §eMessage Color: §f" + safe.apply(() -> ChatConfig.CHAT_MESSAGE_COLOR.get()) + "\n" +
                    "§6║\n" +
                    "§6║ §7Join/Leave: §f" + safe.apply(() -> ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get()) + "\n" +
                    "§6║\n" +
                    "§6║ §7World Border\n" +
                    "§6║  §eEnabled: §f" + safe.apply(() -> RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get()) + "\n" +
                    "§6║  §eDistance: §f" + safe.apply(() -> RpEssentialsConfig.WORLD_BORDER_DISTANCE.get()) + " blocks\n" +
                    "§6║\n" +
                    "§6║ §7Moderation\n" +
                    "§6║  §eSilent Commands: §f" + safe.apply(() -> ModerationConfig.ENABLE_SILENT_COMMANDS.get()) + "\n" +
                    "§6║  §ePlatforms: §f" + safe.apply(() -> ModerationConfig.ENABLE_PLATFORMS.get()) + "\n" +
                    "§6║  §eWelcome Message: §f" + safe.apply(() -> ScheduleConfig.ENABLE_WELCOME.get()) + "\n" +
                    "§6╚═══════════════════════════════════╝";

            ctx.getSource().sendSuccess(() -> Component.literal(statusMessage), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] Error: " + e.getMessage()));
            return 0;
        }
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
