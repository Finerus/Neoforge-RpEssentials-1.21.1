package net.rp.rpessentials;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Map;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = RpEssentials.MODID)
public class RpEssentialsCommands {

    private static final SuggestionProvider<CommandSourceStack> PLATFORM_SUGGESTIONS = (ctx, builder) -> {
        try {
            if (ModerationConfig.PLATFORMS != null && ModerationConfig.PLATFORMS.get() != null) {
                for (String platform : ModerationConfig.PLATFORMS.get()) {
                    String[] parts = platform.split(";");
                    if (parts.length > 0) {
                        builder.suggest(parts[0]);
                    }
                }
            }
        } catch (IllegalStateException e) {
        }
        return builder.buildFuture();
    };

    private static UUID findUUIDByName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        UUID fromLastConn = LastConnectionManager.findUUIDByName(name);
        if (fromLastConn != null) return fromLastConn;
        if (server.getProfileCache() != null)
            return server.getProfileCache().get(name).map(p -> p.getId()).orElse(null);
        return null;
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WARNED_PLAYERS =
            (ctx, builder) -> {
                WarnManager.getAll().stream().map(w -> w.targetName).distinct().sorted()
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_WARN_IDS =
            (ctx, builder) -> {
                String name;
                try { name = StringArgumentType.getString(ctx, "player"); }
                catch (Exception e) { return builder.buildFuture(); }
                // Online d'abord, sinon LastConnectionManager
                MinecraftServer server = ctx.getSource().getServer();
                ServerPlayer online = server.getPlayerList().getPlayerByName(name);
                UUID uuid = online != null ? online.getUUID() : LastConnectionManager.findUUIDByName(name);
                if (uuid == null) return builder.buildFuture();
                WarnManager.getWarns(uuid).forEach(w ->
                        builder.suggest(w.id, Component.literal("#" + w.id + " — " + w.reason)));
                return builder.buildFuture();
            };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // =========================================================================
        // MAIN COMMAND: /rpessentials
        // =========================================================================
        var rpessentialsRoot = Commands.literal("rpessentials");

        // -------------------------------------------------------------------------
        // 1. MODULE: CONFIGURATION (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var configNode = Commands.literal("config")
                .requires(source -> source.hasPermission(2));

        // Reload
        configNode.then(Commands.literal("reload")
                .executes(RpEssentialsCommands::reloadConfig));

        // Status
        configNode.then(Commands.literal("status")
                .executes(RpEssentialsCommands::showStatus));

        // Setters (On-the-fly modifications)
        var setNode = Commands.literal("set");

        // Obfuscation settings
        setNode.then(Commands.literal("proximity")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.PROXIMITY_DISTANCE, "Proximity distance"))));

        setNode.then(Commands.literal("blur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_BLUR, "Blur"))));

        setNode.then(Commands.literal("obfuscatedNameLength")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OBFUSCATED_NAME_LENGTH, "Hidden name length"))));

        setNode.then(Commands.literal("obfuscatePrefix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OBFUSCATE_PREFIX, "Obfuscate prefix"))));

        setNode.then(Commands.literal("opsSeeAll")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.OPS_SEE_ALL, "Admin View"))));

        setNode.then(Commands.literal("debugSelfBlur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEBUG_SELF_BLUR, "Debug Self Blur"))));

        // Schedule settings
        setNode.then(Commands.literal("enableSchedule")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_SCHEDULE, "Schedule System"))));

        setNode.then(Commands.literal("kickNonStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.KICK_NON_STAFF, "Kick Non-Staff"))));

        // Welcome settings
        setNode.then(Commands.literal("enableWelcome")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_WELCOME, "Welcome Message"))));

        // Platform settings
        setNode.then(Commands.literal("enablePlatforms")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_PLATFORMS, "Platforms System"))));

        // Silent commands settings
        setNode.then(Commands.literal("enableSilentCommands")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.ENABLE_SILENT_COMMANDS, "Silent Commands"))));

        setNode.then(Commands.literal("logToStaff")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_STAFF, "Log to Staff"))));

        setNode.then(Commands.literal("logToConsole")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.LOG_TO_CONSOLE, "Log to Console"))));

        setNode.then(Commands.literal("notifyTarget")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ModerationConfig.NOTIFY_TARGET, "Notify Target"))));

        // Permission settings
        setNode.then(Commands.literal("opLevelBypass")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 4))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.OP_LEVEL_BYPASS, "OP Level Bypass"))));

        setNode.then(Commands.literal("useLuckPermsGroups")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.USE_LUCKPERMS_GROUPS, "Use LuckPerms Groups"))));

        // Chat settings
        setNode.then(Commands.literal("enableChatFormat")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CHAT_FORMAT, "Chat Format"))));

        setNode.then(Commands.literal("enableTimestamp")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_TIMESTAMP, "Timestamp"))));

        setNode.then(Commands.literal("markdownEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.MARKDOWN_ENABLED, "Markdown"))));

        setNode.then(Commands.literal("chatMessageColor")
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
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[RpEssentials] Chat Message Color set to: " + color),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("timestampFormat")
                .then(Commands.argument("format", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String format = StringArgumentType.getString(ctx, "format");
                            ChatConfig.TIMESTAMP_FORMAT.set(format);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[RpEssentials] Timestamp Format set to: " + format),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("enableColorsCommand")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_COLORS_COMMAND, "Colors Command"))));

        setNode.then(Commands.literal("enableSneakStealth")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_SNEAK_STEALTH, "Sneak Stealth Mode"))));

        setNode.then(Commands.literal("sneakProximityDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.SNEAK_PROXIMITY_DISTANCE, "Sneak Detection Distance"))));

        setNode.then(Commands.literal("nametagAdvancedEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.NAMETAG_ADVANCED_ENABLED, "Nametag Advanced"))));

        setNode.then(Commands.literal("nametagObfuscationEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.NAMETAG_OBFUSCATION_ENABLED, "Nametag Obfuscation"))));

        setNode.then(Commands.literal("nametagHideBehindBlocks")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.NAMETAG_HIDE_BEHIND_BLOCKS, "Nametag Hide Behind Blocks"))));

        setNode.then(Commands.literal("nametagShowWhileSneaking")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.NAMETAG_SHOW_WHILE_SNEAKING, "Nametag Show While Sneaking"))));

        setNode.then(Commands.literal("nametagStaffAlwaysSeeReal")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.NAMETAG_STAFF_ALWAYS_SEE_REAL, "Nametag Staff Bypass"))));

        setNode.then(Commands.literal("nametagObfuscationDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.NAMETAG_OBFUSCATION_DISTANCE, "Nametag Obfuscation Distance"))));

        setNode.then(Commands.literal("nametagRenderDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(0, 256))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.NAMETAG_RENDER_DISTANCE, "Nametag Render Distance"))));

        setNode.then(Commands.literal("nametagFormat")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.NAMETAG_FORMAT, "Nametag Format"))));

        setNode.then(Commands.literal("hideNametags")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.HIDE_NAMETAGS, "Hide Nametags"))));

        setNode.then(Commands.literal("showNametagPrefixSuffix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.SHOW_NAMETAG_PREFIX_SUFFIX, "Show Nametag Prefix/Suffix"))));

        // Join/Leave messages settings
        setNode.then(Commands.literal("enableCustomJoinLeave")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE, "Custom Join/Leave Messages"))));

        setNode.then(Commands.literal("joinMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.JOIN_MESSAGE.set(msg);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[RpEssentials] Join Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("leaveMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.LEAVE_MESSAGE.set(msg);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[RpEssentials] Leave Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        // World Border settings
        setNode.then(Commands.literal("enableWorldBorderWarning")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING, "World Border Warning"))));

        setNode.then(Commands.literal("worldBorderDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(100, 100000))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_DISTANCE, "World Border Distance"))));

        setNode.then(Commands.literal("worldBorderMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            RpEssentialsConfig.WORLD_BORDER_MESSAGE.set(msg);
                            RpEssentialsConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[RpEssentials] World Border Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("worldBorderCheckInterval")
                .then(Commands.argument("value", IntegerArgumentType.integer(20, 200))
                        .executes(ctx -> updateConfigInt(ctx, RpEssentialsConfig.WORLD_BORDER_CHECK_INTERVAL, "World Border Check Interval"))));

        setNode.then(Commands.literal("zoneMessageMode")
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

        setNode.then(Commands.literal("deathRpGlobalEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED, "Global Death RP enabled"))));

        setNode.then(Commands.literal("deathRpWhitelistRemove")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE, "Death RP whitelist removal"))));

        setNode.then(Commands.literal("deathRpDeathMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_MESSAGE, "Death RP death message"))));

        setNode.then(Commands.literal("deathRpDeathSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND, "Death RP death sound"))));

        setNode.then(Commands.literal("deathRpDeathSoundVolume")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 10.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_VOLUME, "Death RP death sound volume"))));

        setNode.then(Commands.literal("deathRpDeathSoundPitch")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 2.0))
                        .executes(ctx -> updateConfigDouble(ctx, RpEssentialsConfig.DEATH_RP_DEATH_SOUND_PITCH, "Death RP death sound pitch"))));

        setNode.then(Commands.literal("deathRpPlayerEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MSG, "Death RP player enable message"))));

        setNode.then(Commands.literal("deathRpPlayerEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_ENABLE_MODE, "Death RP player enable mode"))));

        setNode.then(Commands.literal("deathRpPlayerDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MSG, "Death RP player disable message"))));

        setNode.then(Commands.literal("deathRpPlayerDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_DISABLE_MODE, "Death RP player disable mode"))));

        setNode.then(Commands.literal("deathRpPlayerToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND, "Death RP player toggle sound"))));

        setNode.then(Commands.literal("deathRpGlobalEnableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MSG, "Death RP global enable message"))));

        setNode.then(Commands.literal("deathRpGlobalEnableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLE_MODE, "Death RP global enable mode"))));

        setNode.then(Commands.literal("deathRpGlobalDisableMsg")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MSG, "Death RP global disable message"))));

        setNode.then(Commands.literal("deathRpGlobalDisableMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_DISABLE_MODE, "Death RP global disable mode"))));

        setNode.then(Commands.literal("deathRpGlobalToggleSound")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> updateConfigString(ctx, RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND, "Death RP global toggle sound"))));

        configNode.then(setNode);
        rpessentialsRoot.then(configNode);

        // -------------------------------------------------------------------------
        // 2. MODULE: STAFF & MODERATION (Requires 'isStaff' permission)
        // -------------------------------------------------------------------------
        var staffNode = Commands.literal("staff")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        // Silent Gamemode
        staffNode.then(Commands.literal("gamemode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("survival").suggest("creative").suggest("adventure").suggest("spectator");
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::silentGamemodeSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(RpEssentialsCommands::silentGamemodeTarget)
                        )
                )
        );

        // Silent Teleport
        staffNode.then(Commands.literal("tp")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpEssentialsCommands::silentTeleport)
                )
        );

        // Silent Effects
        staffNode.then(Commands.literal("effect")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> {
                                    BuiltInRegistries.MOB_EFFECT.keySet().forEach(loc -> builder.suggest(loc.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                .executes(RpEssentialsCommands::silentEffect)
                                        )
                                )
                        )
                )
        );

        // Platforms
        staffNode.then(Commands.literal("platform")
                .executes(RpEssentialsCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpEssentialsCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(RpEssentialsCommands::platformTargetSpecific)
                        )
                )
        );

        // Alias /setplatform pour les admins
        dispatcher.register(Commands.literal("setplatform")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("platform_name", StringArgumentType.word())
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(RpEssentialsCommands::setPlatform)
                                                )
                                        )
                                )
                        )
                ));

        rpessentialsRoot.then(staffNode);

        // -------------------------------------------------------------------------
        // 3. MODULE: WHITELIST (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var whitelistNode = Commands.literal("whitelist")
                .requires(source -> source.hasPermission(2));

        // Add - avec autocomplétion des joueurs connectés
        whitelistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p ->
                                    builder.suggest(p.getName().getString())
                            );
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::addToWhitelist)));

        // Remove - avec autocomplétion des joueurs dans la whitelist
        whitelistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.WHITELIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::removeFromWhitelist)));

        whitelistNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::listWhitelist));

        rpessentialsRoot.then(whitelistNode);

        // -------------------------------------------------------------------------
        // 4. MODULE: BLACKLIST (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var blacklistNode = Commands.literal("blacklist")
                .requires(source -> source.hasPermission(2));

        // Add - avec autocomplétion des joueurs connectés
        blacklistNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p ->
                                    builder.suggest(p.getName().getString())
                            );
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::addToBlacklist)));

        // Remove - avec autocomplétion des joueurs dans la blacklist
        blacklistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            RpEssentialsConfig.BLACKLIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::removeFromBlacklist)));

        blacklistNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::listBlacklist));

        rpessentialsRoot.then(blacklistNode);

        // -------------------------------------------------------------------------
        // 5. MODULE: ALWAYS VISIBLE LIST (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var alwaysVisibleNode = Commands.literal("alwaysvisible")
                .requires(source -> source.hasPermission(2));

        // Add - avec autocomplétion des joueurs connectés
        alwaysVisibleNode.then(Commands.literal("add")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p ->
                                    builder.suggest(p.getName().getString())
                            );
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::addToAlwaysVisible)));

        // Remove - avec autocomplétion des joueurs dans la always visible list
        alwaysVisibleNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                if (RpEssentialsConfig.ALWAYS_VISIBLE_LIST != null) {
                                    RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get().forEach(builder::suggest);
                                }
                            } catch (Exception e) {
                                // Config pas chargée
                            }
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::removeFromAlwaysVisible)));

        alwaysVisibleNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::listAlwaysVisible));

        rpessentialsRoot.then(alwaysVisibleNode);

        // -------------------------------------------------------------------------
        // 6. MODULE: LICENSE (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var licenseNode = Commands.literal("license")
                .requires(source -> source.hasPermission(2));

        licenseNode.then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        // Récupérer le joueur target
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                        // Récupérer ses licences actuelles
                                        List<String> currentLicenses = LicenseManager.getLicenses(target.getUUID());

                                        // Suggérer seulement les métiers qu'il N'A PAS
                                        for (ProfessionRestrictionManager.ProfessionData profession :
                                                ProfessionRestrictionManager.getAllProfessions()) {
                                            if (!currentLicenses.contains(profession.id)) {
                                                builder.suggest(profession.id);
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Fallback: suggérer tous les métiers si erreur
                                        for (ProfessionRestrictionManager.ProfessionData profession :
                                                ProfessionRestrictionManager.getAllProfessions()) {
                                            builder.suggest(profession.id);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpEssentialsCommands::giveLicense)
                        )
                )
        );

        // Revoke - avec autocomplétion des licences du joueur
        licenseNode.then(Commands.literal("revoke")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        List<String> licenses = LicenseManager.getLicenses(target.getUUID());
                                        licenses.forEach(builder::suggest);
                                    } catch (Exception e) {
                                        // Fallback to common professions
                                        builder.suggest("chasseur").suggest("pecheur")
                                                .suggest("mineur").suggest("bucheron")
                                                .suggest("forgeron").suggest("alchimiste")
                                                .suggest("marchand").suggest("garde");
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpEssentialsCommands::revokeLicense)
                        )
                )
        );

        // List - avec argument optionnel pour joueur spécifique ou tous
        licenseNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::listAllLicenses) // Sans argument = tous les joueurs
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(RpEssentialsCommands::listLicenses) // Avec argument = joueur spécifique
                )
        );

        // Check - avec autocomplétion des licences du joueur
        licenseNode.then(Commands.literal("check")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    try {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        List<String> licenses = LicenseManager.getLicenses(target.getUUID());
                                        licenses.forEach(builder::suggest);
                                    } catch (Exception e) {
                                        // Fallback to common professions
                                        builder.suggest("chasseur").suggest("pecheur")
                                                .suggest("mineur").suggest("bucheron")
                                                .suggest("forgeron").suggest("alchimiste")
                                                .suggest("marchand").suggest("garde");
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RpEssentialsCommands::checkLicense)
                        )
                )
        );

        licenseNode.then(Commands.literal("giverp")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ProfessionRestrictionManager.ProfessionData profession :
                                            ProfessionRestrictionManager.getAllProfessions()) {
                                        builder.suggest(profession.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("days_duration", IntegerArgumentType.integer(1, 365))
                                        .executes(RpEssentialsCommands::giveRPLicense)
                                )
                        )
                )
        );

        rpessentialsRoot.then(licenseNode);

        // -------------------------------------------------------------------------
        // 7. MODULE: NICKNAME (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var nickNode = Commands.literal("nick")
                .requires(source -> source.hasPermission(2));

        nickNode.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(RpEssentialsCommands::setNickname)
                )
                .executes(RpEssentialsCommands::resetNickname)
        );

        nickNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::listNicknames)
        );

        rpessentialsRoot.then(nickNode);

        // -------------------------------------------------------------------------
        // 8. MODULE: WHOIS (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var whoisNode = Commands.literal("whois")
                .requires(source -> source.hasPermission(2));
        whoisNode.then(Commands.argument("nickname", StringArgumentType.greedyString())
                .executes(RpEssentialsCommands::whoisCommand));
        rpessentialsRoot.then(whoisNode);

        // Also register as standalone /whois
        dispatcher.register(Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(RpEssentialsCommands::whoisCommand)));

        // -------------------------------------------------------------------------
        // 9. MODULE: SCHEDULE (Public)
        // -------------------------------------------------------------------------
        rpessentialsRoot.then(Commands.literal("schedule")
                .executes(RpEssentialsCommands::showSchedule));

        // Remplacer setOpeningTime et setClosingTime par :
        setNode.then(Commands.literal("scheduleDay")
                .then(Commands.argument("day", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            java.util.Arrays.asList("MONDAY","TUESDAY","WEDNESDAY",
                                            "THURSDAY","FRIDAY","SATURDAY","SUNDAY")
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.literal("open")
                                .then(Commands.argument("time", StringArgumentType.word())
                                        .executes(ctx -> setDayTime(ctx, "open"))))
                        .then(Commands.literal("close")
                                .then(Commands.argument("time", StringArgumentType.word())
                                        .executes(ctx -> setDayTime(ctx, "close"))))
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setDayEnabled(ctx))))));

        // ── Death Hours ──────────────────────────────────────────────────────────
        setNode.then(Commands.literal("deathHoursEnabled")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx,
                                ScheduleConfig.DEATH_HOURS_ENABLED, "Death Hours"))));

        // /rpessentials config set deathHoursSlots "22:00-23:59,00:00-06:00"
        // (slots séparés par virgule, l'admin les tape en une fois)
        setNode.then(Commands.literal("deathHoursSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "slots");
                            java.util.List<String> slots = java.util.Arrays.asList(raw.split(","));
                            slots = slots.stream().map(String::trim).collect(java.util.stream.Collectors.toList());
                            try {
                                ScheduleConfig.DEATH_HOURS_SLOTS.set(slots);
                                ScheduleConfig.SPEC.save();
                                RpEssentialsScheduleManager.reload();
                                java.util.List<String> finalSlots = slots;
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED,
                                                "label", "Death Hours Slots",
                                                "value", String.join(", ", finalSlots))), true);
                            } catch (IllegalStateException e) {
                                ctx.getSource().sendFailure(Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
                            }
                            return 1;
                        })));

        // ── HRP Hours ─────────────────────────────────────────────────────────────
        setNode.then(Commands.literal("enableHrpHours")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx,
                                ScheduleConfig.ENABLE_HRP_HOURS, "HRP Hours"))));

        setNode.then(Commands.literal("hrpToleratedSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "slots");
                            java.util.List<String> slots = java.util.Arrays.stream(raw.split(","))
                                    .map(String::trim).collect(java.util.stream.Collectors.toList());
                            try {
                                ScheduleConfig.HRP_TOLERATED_SLOTS.set(slots);
                                ScheduleConfig.SPEC.save();
                                RpEssentialsScheduleManager.reload();
                                java.util.List<String> finalSlots = slots;
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED,
                                                "label", "HRP Tolerated Slots",
                                                "value", String.join(", ", finalSlots))), true);
                            } catch (IllegalStateException e) {
                                ctx.getSource().sendFailure(Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
                            }
                            return 1;
                        })));

        setNode.then(Commands.literal("hrpAllowedSlots")
                .then(Commands.argument("slots", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "slots");
                            java.util.List<String> slots = java.util.Arrays.stream(raw.split(","))
                                    .map(String::trim).collect(java.util.stream.Collectors.toList());
                            try {
                                ScheduleConfig.HRP_ALLOWED_SLOTS.set(slots);
                                ScheduleConfig.SPEC.save();
                                RpEssentialsScheduleManager.reload();
                                java.util.List<String> finalSlots = slots;
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED,
                                                "label", "HRP Allowed Slots",
                                                "value", String.join(", ", finalSlots))), true);
                            } catch (IllegalStateException e) {
                                ctx.getSource().sendFailure(Component.literal(
                                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
                            }
                            return 1;
                        })));

        setNode.then(Commands.literal("hrpToleratedMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx,
                                ScheduleConfig.HRP_TOLERATED_MESSAGE, "HRP Tolerated Message"))));

        setNode.then(Commands.literal("hrpAllowedMessage")
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> updateConfigString(ctx,
                                ScheduleConfig.HRP_ALLOWED_MESSAGE, "HRP Allowed Message"))));

        setNode.then(Commands.literal("hrpMessageMode")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("CHAT").suggest("ACTION_BAR")
                                    .suggest("TITLE").suggest("IMMERSIVE");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> updateConfigString(ctx,
                                ScheduleConfig.HRP_MESSAGE_MODE, "HRP Message Mode"))));

        // -------------------------------------------------------------------------
        // MODULE: MESSAGERIE PRIVÉE — remplace /msg /tell /w /whisper + /r
        // -------------------------------------------------------------------------
        for (String alias : new String[]{"msg", "tell", "w", "whisper"}) {
            dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals(alias));
            dispatcher.register(Commands.literal(alias)
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        String msg = StringArgumentType.getString(ctx, "message");

                                        if (ctx.getSource().getEntity() instanceof ServerPlayer sender) {
                                            RpEssentialsMessagingManager.sendMessage(sender, target, msg);
                                        } else {
                                            MutableComponent toTarget = Component.literal(MessagesConfig.get(MessagesConfig.MP_CONSOLE_TO_PLAYER, "msg", msg));
                                            target.sendSystemMessage(toTarget);
                                            ctx.getSource().sendSuccess(() -> Component.literal(MessagesConfig.get(MessagesConfig.MP_CONSOLE_FROM_SERVER, "target", target.getName().getString(), "msg", msg)), false);
                                        }
                                        return 1;
                                    }))));
        }

        dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            String msg = StringArgumentType.getString(ctx, "message");
                            return RpEssentialsMessagingManager.reply(sender, msg, ctx.getSource().getServer());
                        })));

        // Remplace /list vanilla
        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("list"));
        dispatcher.register(Commands.literal("list")
                .executes(RpEssentialsCommands::playerList));

        // /rpessentials help
        rpessentialsRoot.then(Commands.literal("help")
                .executes(RpEssentialsCommands::showHelp));

        // -------------------------------------------------------------------------
        // MODULE: LAST CONNECTION (Requires isStaff)
        // -------------------------------------------------------------------------
        var lastConnNode = Commands.literal("lastconnection")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        // /rpessentials lastconnection <player>
        lastConnNode.then(Commands.argument("player", StringArgumentType.word())
                .executes(RpEssentialsCommands::lastConnectionPlayer));

        // /rpessentials lastconnection list [count]
        lastConnNode.then(Commands.literal("list")
                .executes(ctx -> lastConnectionList(ctx, 20))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> lastConnectionList(ctx, IntegerArgumentType.getInteger(ctx, "count"))))
        );

        rpessentialsRoot.then(lastConnNode);

        // -------------------------------------------------------------------------
        // MODULE: WARN (Staff commands + player /mywarn)
        // -------------------------------------------------------------------------
        var warnNode = Commands.literal("warn")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        // /rpessentials warn add <player> <reason>
        warnNode.then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(RpEssentialsCommands::warnAdd))));

        // /rpessentials warn temp <player> <minutes> <reason>
        warnNode.then(Commands.literal("temp")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(RpEssentialsCommands::warnTemp)))));

        // /rpessentials warn remove <warnId>
        warnNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_WARNED_PLAYERS)
                        .then(Commands.argument("warnId", StringArgumentType.word())
                                .suggests(SUGGEST_WARN_IDS)
                                .executes(RpEssentialsCommands::warnRemove))));

        // /rpessentials warn list [player]  — staff peut voir n'importe qui
        warnNode.then(Commands.literal("list")
                .executes(RpEssentialsCommands::warnListAll)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpEssentialsCommands::warnListPlayer)));

        // /rpessentials warn info <warnId>
        warnNode.then(Commands.literal("info")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(SUGGEST_WARNED_PLAYERS)
                        .executes(ctx -> {
                            // Sans ID → affiche la liste des warns du joueur
                            if (!warnSystemCheck(ctx)) return 0;
                            String name = StringArgumentType.getString(ctx, "player");
                            MinecraftServer srv = ctx.getSource().getServer();
                            ServerPlayer op = srv.getPlayerList().getPlayerByName(name);
                            UUID uuid = op != null ? op.getUUID() : LastConnectionManager.findUUIDByName(name);
                            if (uuid == null) {
                                ctx.getSource().sendFailure(Component.literal("§c[RPE] Joueur introuvable : " + name));
                                return 0;
                            }
                            return displayWarnList(ctx, uuid, name, true);
                        })
                        .then(Commands.argument("warnId", StringArgumentType.word())
                                .suggests(SUGGEST_WARN_IDS)
                                .executes(RpEssentialsCommands::warnInfo))));

        // /rpessentials warn clear <player>  — supprimer tous les warns d'un joueur
        warnNode.then(Commands.literal("clear")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpEssentialsCommands::warnClear)));

        // /rpessentials warn purge  — purger les warns expirés
        warnNode.then(Commands.literal("purge")
                .executes(RpEssentialsCommands::warnPurge));

        rpessentialsRoot.then(warnNode);

        // /mywarn — alias standalone accessible à tous les joueurs
        dispatcher.register(Commands.literal("mywarn")
                .requires(src -> src.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                .executes(RpEssentialsCommands::myWarn));

        // ── Construction du sous-arbre /rpessentials deathrp ─────────────────────────────

        var deathRpNode = Commands.literal("deathrp")
                .requires(source -> source.hasPermission(2));

        // /rpessentials deathrp enable <true|false>
        deathRpNode.then(Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(RpEssentialsCommands::deathRpSetGlobal)));

        // /rpessentials deathrp player <joueur> enable <true|false>
        // /rpessentials deathrp player <joueur> reset
        var deathRpPlayerNode = Commands.literal("player")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(RpEssentialsCommands::deathRpSetPlayer)))
                        .then(Commands.literal("reset")
                                .executes(RpEssentialsCommands::deathRpResetPlayer)));

        deathRpNode.then(deathRpPlayerNode);

        // /rpessentials deathrp status
        deathRpNode.then(Commands.literal("status")
                .executes(RpEssentialsCommands::deathRpStatus));

        rpessentialsRoot.then(deathRpNode);

        // /rpessentials setrole <player> <role>
        var setRoleNode = Commands.literal("setrole")
                .requires(source -> source.hasPermission(3));

        setRoleNode.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("role", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            try {
                                RpEssentialsConfig.ROLES.get().stream()
                                        .map(s -> s.split(";", 2)[0].trim())
                                        .forEach(builder::suggest);
                            } catch (IllegalStateException ignored) {}
                            return builder.buildFuture();
                        })
                        .executes(RpEssentialsCommands::setRole)));

        rpessentialsRoot.then(setRoleNode);

        // =========================================================================
        // Register root
        // =========================================================================
        dispatcher.register(rpessentialsRoot);

        // =========================================================================
        // COLORS COMMAND
        // =========================================================================
        dispatcher.register(Commands.literal("colors")
                .executes(ctx -> {
                    if (ChatConfig.ENABLE_COLORS_COMMAND != null &&
                            !ChatConfig.ENABLE_COLORS_COMMAND.get()) {
                        ctx.getSource().sendFailure(Component.literal("§cColors command is disabled."));
                        return 0;
                    }
                    return RpEssentialsCommands.showColors(ctx);
                }));

        // =========================================================================
        // HANDY ALIASES
        // =========================================================================
        dispatcher.register(Commands.literal("schedule")
                .executes(RpEssentialsCommands::showSchedule));

        dispatcher.register(Commands.literal("horaires")
                .executes(RpEssentialsCommands::showSchedule));

        dispatcher.register(Commands.literal("platform")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .executes(RpEssentialsCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpEssentialsCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(RpEssentialsCommands::platformTargetSpecific)
                        )
                ));
    }

    // =============================================================================
    // IMPLEMENTATION LOGIC (HANDLERS)
    // =============================================================================

    // --- CONFIG HANDLERS ---

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        RpEssentialsScheduleManager.reload();
        RpEssentialsPermissions.clearCache();
        NicknameManager.reload();
        NametagSyncHelper.broadcastToAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Configuration, nicknames and nametags reloaded!"), true);
        return 1;
    }

    private static int updateConfigInt(CommandContext<CommandSourceStack> ctx, ModConfigSpec.IntValue config, String name) {
        int val = IntegerArgumentType.getInteger(ctx, "value");
        config.set(val);
        RpEssentialsConfig.SPEC.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + name + " set to: " + val), true);
        return 1;
    }

    private static int updateConfigBool(CommandContext<CommandSourceStack> ctx, ModConfigSpec.BooleanValue config, String name) {
        boolean val = BoolArgumentType.getBool(ctx, "value");
        config.set(val);
        RpEssentialsConfig.SPEC.save();

        if (config == RpEssentialsConfig.HIDE_NAMETAGS) {
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player -> {
                PacketDistributor.sendToPlayer(player, new HideNametagsPacket(val));
            });
            RpEssentials.LOGGER.info("Broadcast nametag config update: hide={}", val);
        }

        if (config == RpEssentialsConfig.NAMETAG_ADVANCED_ENABLED
                || config == RpEssentialsConfig.NAMETAG_OBFUSCATION_ENABLED
                || config == RpEssentialsConfig.NAMETAG_HIDE_BEHIND_BLOCKS
                || config == RpEssentialsConfig.NAMETAG_SHOW_WHILE_SNEAKING
                || config == RpEssentialsConfig.NAMETAG_STAFF_ALWAYS_SEE_REAL) {
            NametagSyncHelper.broadcastToAll(ctx.getSource().getServer());
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + name + " : " + (val ? "§aENABLED" : "§cDISABLED")), true);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        try {
            final java.util.function.Function<java.util.function.Supplier<?>, String> safe = (supplier) -> {
                try {
                    Object val = supplier.get();
                    return val != null ? val.toString() : "N/A";
                } catch (Exception e) {
                    return "N/A";
                }
            };

            // Helper pour le status du schedule
            String scheduleStatus;
            try {
                scheduleStatus = RpEssentialsScheduleManager.isServerOpen() ? "§aOPEN" : "§cCLOSED";
            } catch (Exception e) {
                scheduleStatus = "§7N/A";
            }

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
                            "§6║ §7Schedule\n" +
                            "§6║  §eEnabled: §f" + scheduleStatus + "\n" +
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
                            "§6║ §7Join/Leave Messages\n" +
                            "§6║  §eEnabled: §f" + safe.apply(() -> ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE.get()) + "\n" +
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

            context.getSource().sendSuccess(() -> Component.literal(statusMessage), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c[RpEssentials] Error displaying status: " + e.getMessage()));
            RpEssentials.LOGGER.error("Error in showStatus", e);
            return 0;
        }
    }

    // --- WHITELIST / BLACKLIST / ALWAYS VISIBLE HANDLERS ---
    // Factorisés via modifyList() pour éviter la duplication

    private static int modifyList(
            CommandContext<CommandSourceStack> ctx,
            ModConfigSpec.ConfigValue<List<? extends String>> config,
            String listName,
            boolean add
    ) {
        String player = StringArgumentType.getString(ctx, "player");
        List<String> list = new ArrayList<>(config.get());

        if (add) {
            if (list.contains(player)) {
                ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] " + player + " is already in " + listName + "."));
                return 0;
            }
            list.add(player);
            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + player + " added to " + listName + "."), true);
        } else {
            if (!list.remove(player)) {
                ctx.getSource().sendFailure(Component.literal("§c[RpEssentials] " + player + " is not in " + listName + "."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] " + player + " removed from " + listName + "."), true);
        }

        config.set(list);
        RpEssentialsConfig.SPEC.save();
        return 1;
    }

    private static int addToWhitelist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.WHITELIST, "whitelist", true);
    }

    private static int removeFromWhitelist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.WHITELIST, "whitelist", false);
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> whitelist = RpEssentialsConfig.WHITELIST.get();
        if (whitelist.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Whitelist is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Whitelist: §f" + String.join(", ", whitelist)), false);
        }
        return 1;
    }

    private static int addToBlacklist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.BLACKLIST, "blacklist", true);
    }

    private static int removeFromBlacklist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.BLACKLIST, "blacklist", false);
    }

    private static int listBlacklist(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> blacklist = RpEssentialsConfig.BLACKLIST.get();
        if (blacklist.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Blacklist is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Blacklist (always hidden): §f" + String.join(", ", blacklist)), false);
        }
        return 1;
    }

    private static int addToAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", true);
    }

    private static int removeFromAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, RpEssentialsConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", false);
    }

    private static int listAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> alwaysVisible = RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get();
        if (alwaysVisible.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Always Visible list is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[RpEssentials] Always Visible (always shown in TabList): §f" + String.join(", ", alwaysVisible)), false);
        }
        return 1;
    }

    // --- STAFF HANDLERS (SILENT) ---

    private static int silentGamemodeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"));
    }

    private static int silentGamemodeTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "mode"));
    }

    private static int setGamemode(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String modeName) {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }

        GameType type = parseGameMode(modeName);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid gamemode."));
            return 0;
        }

        target.setGameMode(type);

        String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : "Console";
        logToStaff(ctx.getSource(), sourceName + " set " + target.getName().getString() + " to " + modeName);

        if (ModerationConfig.NOTIFY_TARGET.get() && target != ctx.getSource().getEntity()) {
            target.sendSystemMessage(Component.literal("§7[Staff] Your gamemode has been changed to " + modeName));
        }
        return 1;
    }

    private static int silentTeleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }

        ServerPlayer executor = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

        executor.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        logToStaff(ctx.getSource(), executor.getName().getString() + " TP'd to " + target.getName().getString());
        return 1;
    }

    private static int silentEffect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ResourceLocation effectId = ResourceLocationArgument.getId(ctx, "effect");
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");

        Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(BuiltInRegistries.MOB_EFFECT.key(), effectId));

        if (effect.isPresent()) {
            target.addEffect(new MobEffectInstance(effect.get(), duration * 20, amplifier, false, false));
            String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : "Console";
            logToStaff(ctx.getSource(), sourceName + " gave effect " + effectId + " to " + target.getName().getString());

            if (ModerationConfig.NOTIFY_TARGET.get()) {
                target.sendSystemMessage(Component.literal("§7[Staff] An effect has been applied to you."));
            }
        } else {
            ctx.getSource().sendFailure(Component.literal("§cInvalid effect."));
            return 0;
        }
        return 1;
    }

    // --- PLATFORMS HANDLERS ---

    private static int platformSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), null, "platform1");
    }

    private static int platformTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), "platform1");
    }

    private static int platformTargetSpecific(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "platform_id"));
    }

    private static int teleportToPlatform(CommandSourceStack source, ServerPlayer executor, ServerPlayer target, String platformId) {
        if (!ModerationConfig.ENABLE_PLATFORMS.get()) {
            source.sendFailure(Component.literal("§cPlatforms system is disabled."));
            return 0;
        }

        for (String pData : ModerationConfig.PLATFORMS.get()) {
            String[] parts = pData.split(";");
            if (parts.length >= 6 && parts[0].equals(platformId)) {
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(parts[2]));
                ServerLevel level = source.getServer().getLevel(dim);
                if (level == null) {
                    source.sendFailure(Component.literal("§cDimension not found: " + parts[2]));
                    continue;
                }

                Vec3 pos = new Vec3(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), Double.parseDouble(parts[5]));

                if (target != null) {
                    target.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    target.sendSystemMessage(Component.literal("§e[Staff] You have been teleported to: " + parts[1]));
                    logToStaff(source, executor.getName().getString() + " TP'd " + target.getName().getString() + " to " + parts[1]);
                } else {
                    executor.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    executor.sendSystemMessage(Component.literal("§aTeleported to: " + parts[1]));
                }
                return 1;
            }
        }
        source.sendFailure(Component.literal("§cPlatform not found: " + platformId));
        return 0;
    }

    private static int setPlatform(CommandContext<CommandSourceStack> ctx) {
        final String platformName = StringArgumentType.getString(ctx, "platform_name");
        final ResourceLocation dimension = ResourceLocationArgument.getId(ctx, "dimension");
        final int x = IntegerArgumentType.getInteger(ctx, "x");
        final int y = IntegerArgumentType.getInteger(ctx, "y");
        final int z = IntegerArgumentType.getInteger(ctx, "z");

        String platformEntry = platformName + ";" + platformName + ";" + dimension + ";" + x + ";" + y + ";" + z;

        List<String> platforms = new ArrayList<>(ModerationConfig.PLATFORMS.get());

        boolean updated = false;
        for (int i = 0; i < platforms.size(); i++) {
            if (platforms.get(i).startsWith(platformName + ";")) {
                platforms.set(i, platformEntry);
                updated = true;
                break;
            }
        }

        if (!updated) {
            platforms.add(platformEntry);
        }

        ModerationConfig.PLATFORMS.set(platforms);
        RpEssentialsConfig.SPEC.save();

        final boolean wasUpdated = updated;
        ctx.getSource().sendSuccess(() ->
                        Component.literal("§a[RpEssentials] Platform '" + platformName + "' " +
                                (wasUpdated ? "updated" : "created") + " at " + dimension + " " + x + " " + y + " " + z),
                true
        );

        return 1;
    }

    /**
     * Helper pour mettre à jour une ConfigValue<String> via commande.
     * À ajouter à côté de updateConfigBool() et updateConfigInt() dans RpEssentialsCommands.
     */
    private static int updateConfigString(CommandContext<CommandSourceStack> ctx,
                                          ModConfigSpec.ConfigValue<String> configValue,
                                          String label) {
        try {
            String value = StringArgumentType.getString(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(
                    () -> Component.literal(
                            MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED, "label", label, "value", value)),
                    true);
            NametagSyncHelper.broadcastToAll(ctx.getSource().getServer());
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }

    private static int updateConfigDouble(CommandContext<CommandSourceStack> ctx,
                                          ModConfigSpec.DoubleValue configValue,
                                          String label) {
        try {
            double value = DoubleArgumentType.getDouble(ctx, "value");
            if (configValue == null) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UNAVAILABLE)));
                return 0;
            }
            configValue.set(value);
            configValue.save();
            ctx.getSource().sendSuccess(
                    () -> Component.literal(
                            MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_UPDATED,
                                    "label", label, "value", String.valueOf(value))),
                    true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_BUILT)));
            return 0;
        }
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String roleId = StringArgumentType.getString(ctx, "role").toLowerCase();
        MinecraftServer server = ctx.getSource().getServer();

        List<? extends String> rolesConfig;
        try {
            rolesConfig = RpEssentialsConfig.ROLES.get();
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String lpGroup = null;
        for (String entry : rolesConfig) {
            String[] parts = entry.split(";", 2);
            if (parts[0].trim().equalsIgnoreCase(roleId)) {
                lpGroup = parts.length > 1 ? parts[1].trim() : roleId;
                break;
            }
        }
        if (lpGroup == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SETROLE_UNKNOWN, "role", roleId.toUpperCase())));
            return 0;
        }

        final String finalLpGroup = lpGroup;
        final String name         = target.getName().getString();
        final String roleDisplay  = roleId.toUpperCase();

        CommandSourceStack silentSource = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);

        for (String entry : rolesConfig) {
            String oldTag = entry.split(";", 2)[0].trim();
            server.getCommands().performPrefixedCommand(silentSource, "tag " + name + " remove " + oldTag);
        }
        server.getCommands().performPrefixedCommand(silentSource, "tag " + name + " add " + roleId);
        server.getCommands().performPrefixedCommand(silentSource, "lp user " + name + " parent set " + finalLpGroup);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.SETROLE_SUCCESS_STAFF,
                        "role", roleDisplay, "player", name)), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.SETROLE_SUCCESS_PLAYER, "role", roleDisplay)));
        return 1;
    }

    // --- SCHEDULE HANDLER ---

    private static int setDayTime(CommandContext<CommandSourceStack> ctx, String type) {
        String day  = StringArgumentType.getString(ctx, "day").toUpperCase();
        String time = StringArgumentType.getString(ctx, "time");
        if (!time.matches("\\d{2}:\\d{2}")) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SCHEDULE_TIME_INVALID)));
            return 0;
        }
        try {
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> cfg = switch (day + "_" + type.toUpperCase()) {
                case "MONDAY_OPEN"     -> ScheduleConfig.MONDAY_OPEN;
                case "MONDAY_CLOSE"    -> ScheduleConfig.MONDAY_CLOSE;
                case "TUESDAY_OPEN"    -> ScheduleConfig.TUESDAY_OPEN;
                case "TUESDAY_CLOSE"   -> ScheduleConfig.TUESDAY_CLOSE;
                case "WEDNESDAY_OPEN"  -> ScheduleConfig.WEDNESDAY_OPEN;
                case "WEDNESDAY_CLOSE" -> ScheduleConfig.WEDNESDAY_CLOSE;
                case "THURSDAY_OPEN"   -> ScheduleConfig.THURSDAY_OPEN;
                case "THURSDAY_CLOSE"  -> ScheduleConfig.THURSDAY_CLOSE;
                case "FRIDAY_OPEN"     -> ScheduleConfig.FRIDAY_OPEN;
                case "FRIDAY_CLOSE"    -> ScheduleConfig.FRIDAY_CLOSE;
                case "SATURDAY_OPEN"   -> ScheduleConfig.SATURDAY_OPEN;
                case "SATURDAY_CLOSE"  -> ScheduleConfig.SATURDAY_CLOSE;
                case "SUNDAY_OPEN"     -> ScheduleConfig.SUNDAY_OPEN;
                case "SUNDAY_CLOSE"    -> ScheduleConfig.SUNDAY_CLOSE;
                default -> null;
            };
            if (cfg == null) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_INVALID, "day", day)));
                return 0;
            }
            cfg.set(time);
            ScheduleConfig.SPEC.save();
            RpEssentialsScheduleManager.reload();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_UPDATED,
                            "day", day, "type", type, "value", time)), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }
    }

    private static int setDayEnabled(CommandContext<CommandSourceStack> ctx) {
        String day    = StringArgumentType.getString(ctx, "day").toUpperCase();
        boolean value = BoolArgumentType.getBool(ctx, "value");
        try {
            net.neoforged.neoforge.common.ModConfigSpec.BooleanValue cfg = switch (day) {
                case "MONDAY"    -> ScheduleConfig.MONDAY_ENABLED;
                case "TUESDAY"   -> ScheduleConfig.TUESDAY_ENABLED;
                case "WEDNESDAY" -> ScheduleConfig.WEDNESDAY_ENABLED;
                case "THURSDAY"  -> ScheduleConfig.THURSDAY_ENABLED;
                case "FRIDAY"    -> ScheduleConfig.FRIDAY_ENABLED;
                case "SATURDAY"  -> ScheduleConfig.SATURDAY_ENABLED;
                case "SUNDAY"    -> ScheduleConfig.SUNDAY_ENABLED;
                default -> null;
            };
            if (cfg == null) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_INVALID, "day", day)));
                return 0;
            }
            cfg.set(value);
            ScheduleConfig.SPEC.save();
            RpEssentialsScheduleManager.reload();
            String state = value ? "enabled" : "disabled";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_ENABLED,
                            "day", day, "state", state)), true);
            return 1;
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }
    }

    private static int showSchedule(CommandContext<CommandSourceStack> ctx) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();

        boolean scheduleEnabled;
        try {
            scheduleEnabled = ScheduleConfig.ENABLE_SCHEDULE.get();
        } catch (IllegalStateException e) {
            scheduleEnabled = false;
        }

        boolean isOpen  = RpEssentialsScheduleManager.isServerOpen();
        String timeInfo = scheduleEnabled
                ? RpEssentialsScheduleManager.getTimeUntilNextEvent()
                : MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_OPEN) + " (schedule disabled)";

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HEADER)).append("\n");
        sb.append(" §6§lSERVER SCHEDULE\n");
        sb.append(" §7Status: ")
                .append(isOpen
                        ? MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_OPEN)
                        : MessagesConfig.get(MessagesConfig.SCHEDULE_STATUS_CLOSED))
                .append("\n\n");

        if (scheduleEnabled) {
            for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
                RpEssentialsScheduleManager.DaySchedule s =
                        RpEssentialsScheduleManager.getSchedules().get(day);
                boolean isToday = day == today;
                String prefix = isToday
                        ? MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_TODAY_PREFIX)
                        : MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_OTHER_PREFIX);
                String dayName = day.getDisplayName(
                        java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

                if (s == null) {
                    sb.append(prefix)
                            .append(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_CLOSED_FORMAT,
                                    "day", dayName))
                            .append("\n");
                } else {
                    sb.append(prefix)
                            .append(MessagesConfig.get(MessagesConfig.SCHEDULE_DAY_OPEN_FORMAT,
                                    "day",   dayName,
                                    "open",  s.open().format(fmt),
                                    "close", s.close().format(fmt)))
                            .append("\n");
                }
            }
        }

        // ── Death Hours ───────────────────────────────────────────────────
        try {
            if (ScheduleConfig.DEATH_HOURS_ENABLED.get()) {
                sb.append("\n ").append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_LABEL))
                        .append(" — ");
                if (RpEssentialsScheduleManager.isDeathHour()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_ACTIVE));
                } else {
                    String slots = String.join(", ", ScheduleConfig.DEATH_HOURS_SLOTS.get());
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_DEATH_HOURS_INACTIVE,
                            "slots", slots));
                }
                sb.append("\n");
            }
        } catch (IllegalStateException ignored) {}

        // ── HRP Hours ─────────────────────────────────────────────────────
        try {
            if (ScheduleConfig.ENABLE_HRP_HOURS.get()) {
                sb.append(" ").append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_LABEL))
                        .append(" — ");
                if (RpEssentialsScheduleManager.isHrpAllowed()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_ALLOWED));
                } else if (RpEssentialsScheduleManager.isHrpTolerated()) {
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_TOLERATED));
                } else {
                    String tolerated = String.join(", ", ScheduleConfig.HRP_TOLERATED_SLOTS.get());
                    String allowed   = String.join(", ", ScheduleConfig.HRP_ALLOWED_SLOTS.get());
                    sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_HRP_INACTIVE,
                            "slots", "Tolerated: " + tolerated + " / Allowed: " + allowed));
                }
                sb.append("\n");
            }
        } catch (IllegalStateException ignored) {}

        if (scheduleEnabled) {
            sb.append("\n §f").append(timeInfo).append("\n");
        }
        sb.append(MessagesConfig.get(MessagesConfig.SCHEDULE_FOOTER));

        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // --- UTILS ---

    private static void logToStaff(CommandSourceStack source, String msg) {
        if (!ModerationConfig.LOG_TO_STAFF.get()) return;
        Component txt = Component.literal("§7§o[StaffLog] " + msg);
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(txt);
        });
        if (ModerationConfig.LOG_TO_CONSOLE.get()) RpEssentials.LOGGER.info("[StaffLog] " + msg);
    }

    private static GameType parseGameMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };
    }

    // --- LICENSE HANDLERS ---

    private static int giveLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        ProfessionRestrictionManager.ProfessionData professionData =
                ProfessionRestrictionManager.getProfessionData(professionId);
        if (professionData == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        String displayName = NicknameManager.getDisplayName(target);
        ItemStack license = new ItemStack(RpEssentialsItems.LICENSE.get());
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(professionData.colorCode +
                        MessagesConfig.get(MessagesConfig.LICENSE_ITEM_NAME) +
                        professionData.displayName));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_TO, "player", displayName)));
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_DATE,
                "date", java.time.LocalDate.now().toString())));
        license.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("professionId", professionId);
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));

        if (!target.getInventory().add(license)) target.drop(license, false);

        LicenseManager.addLicense(target.getUUID(), professionId);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("GIVE", staff, target, professionId, null);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        // Tag vanilla automatique
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "tag " + target.getName().getString() + " add " + professionId);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_STAFF,
                        "profession", professionData.getFormattedName(),
                        "player", displayName)), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_PLAYER,
                        "profession", professionData.getFormattedName())));
        return 1;
    }

    private static int revokeLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");
        MinecraftServer server = ctx.getSource().getServer();

        LicenseManager.removeLicense(target.getUUID(), profession);
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("REVOKE", staff, target, profession, null);
        TempLicenseExpirationManager.markRevokedLicenseItems(target);

        // Tag vanilla automatique
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "tag " + target.getName().getString() + " remove " + profession);

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(profession);
        String profDisplayName = profData != null ? profData.getFormattedName() : profession;

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_STAFF,
                        "profession", profDisplayName,
                        "player", target.getName().getString())), true);
        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_REVOKE_PLAYER,
                        "profession", profDisplayName)));
        return 1;
    }

    private static int giveRPLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        int days = IntegerArgumentType.getInteger(ctx, "days_duration");

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_UNKNOWN_PROFESSION, "profession", professionId)));
            return 0;
        }

        String displayName = NicknameManager.getDisplayName(target);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String issued  = java.time.LocalDate.now().format(fmt);
        String expires = java.time.LocalDate.now().plusDays(days).format(fmt);

        ItemStack license = new ItemStack(RpEssentialsItems.LICENSE.get());
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(profData.colorCode +
                        MessagesConfig.get(MessagesConfig.LICENSE_ITEM_NAME) +
                        profData.displayName));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_TO, "player", displayName)));
        lore.add(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_DATE, "date", issued)));
        lore.add(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_LORE_VALID_UNTIL, "date", expires)));
        license.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));
        if (!target.getInventory().add(license)) target.drop(license, false);

        LicenseManager.addLicense(target.getUUID(), professionId);

        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.addTempLicense(staff, target, professionId, days, issued, expires);
        LicenseManager.logAction("GIVE_RP", staff, target, professionId,
                days + " days, expires " + expires);

        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_RP_STAFF,
                        "profession", profData.getFormattedName(),
                        "player", displayName,
                        "days", String.valueOf(days),
                        "date", expires)), true);

        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.LICENSE_GIVE_RP_PLAYER,
                        "profession", profData.getFormattedName(),
                        "date", expires)));

        return 1;
    }


    private static int listLicenses(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<String> licenses = LicenseManager.getLicenses(target.getUUID());

        if (licenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_LIST_NONE,
                            "player", target.getName().getString())), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_HEADER,
                "player", target.getName().getString())).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");

        for (String profession : licenses) {
            String expiry = LicenseManager.getTempExpirationDate(target.getUUID(), profession);
            if (expiry != null) {
                sb.append("§6║ §f").append(profession)
                        .append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry))
                        .append("\n");
            } else {
                sb.append("§6║ §f").append(profession).append("\n");
            }
        }

        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int listAllLicenses(CommandContext<CommandSourceStack> ctx) {
        var allLicenses = LicenseManager.getAllLicenses();

        if (allLicenses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_NONE)), false);
            return 1;
        }

        StringBuilder result = new StringBuilder("§6╔═══════════════════════════════════╗\n");
        result.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_HEADER)).append("\n");
        result.append("§6╠═══════════════════════════════════╣\n");

        var server = ctx.getSource().getServer();

        for (var entry : allLicenses.entrySet()) {
            java.util.UUID uuid = entry.getKey();
            List<String> licenses = entry.getValue();

            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String playerName = (onlinePlayer != null)
                    ? onlinePlayer.getName().getString()
                    : server.getProfileCache().get(uuid)
                    .map(com.mojang.authlib.GameProfile::getName)
                    .orElse(uuid.toString());

            String noneStr = MessagesConfig.get(MessagesConfig.LICENSE_LIST_ALL_NONE_FOR_PLAYER);
            StringBuilder licLine = new StringBuilder();
            for (String lic : licenses) {
                if (licLine.length() > 0) licLine.append("§7, ");
                String expiry = LicenseManager.getTempExpirationDate(uuid, lic);
                licLine.append("§f").append(lic);
                if (expiry != null) {
                    licLine.append(MessagesConfig.get(MessagesConfig.LICENSE_LIST_RP_EXPIRY, "date", expiry));
                }
            }

            result.append("§6║ §f").append(playerName).append("§7: ")
                    .append(licLine.length() > 0 ? licLine : noneStr).append("\n");
        }

        result.append("§6╚═══════════════════════════════════╝");
        ctx.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

    private static int checkLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");

        boolean has = LicenseManager.hasLicense(target.getUUID(), profession);

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(
                        has ? MessagesConfig.PROFESSION_HAS_LICENSE : MessagesConfig.PROFESSION_NO_LICENSE,
                        "player", target.getName().getString(), "profession", profession)
        ), false);

        return 1;
    }

    // --- NICKNAME HANDLERS ---

    private static int setNickname(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            String nickname = StringArgumentType.getString(context, "nickname");

            // Support color codes & and §
            String formattedNickname = nickname.replace("&", "§");

            // Store in NicknameManager
            NicknameManager.setNickname(target.getUUID(), formattedNickname);

            // Force TabList update for everyone
            target.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(target)
                    )
            );

            context.getSource().sendSuccess(() ->
                    Component.literal("§a[RpEssentials] Nickname for §f" + target.getName().getString() +
                            "§a set to: " + formattedNickname), true);

            target.sendSystemMessage(Component.literal("§aYour nickname has been changed to: " + formattedNickname));

            RpEssentials.LOGGER.info("Nickname set for {}: {}", target.getName().getString(), formattedNickname);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError while setting nickname."));
            RpEssentials.LOGGER.error("Error setting nickname", e);
            return 0;
        }
    }

    private static int resetNickname(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");

            // Remove from NicknameManager
            NicknameManager.removeNickname(target.getUUID());

            // Force TabList update
            target.getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(
                            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                            List.of(target)
                    )
            );

            context.getSource().sendSuccess(() ->
                    Component.literal("§a[RpEssentials] Nickname for §f" + target.getName().getString() + "§a reset."), true);

            target.sendSystemMessage(Component.literal("§aYour nickname has been reset."));

            RpEssentials.LOGGER.info("Nickname reset for {}", target.getName().getString());

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError while resetting nickname."));
            RpEssentials.LOGGER.error("Error resetting nickname", e);
            return 0;
        }
    }

    private static int listNicknames(CommandContext<CommandSourceStack> context) {
        int count = NicknameManager.count();

        if (count == 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("§e[RpEssentials] No active nicknames."), false);
            return 1;
        }

        StringBuilder list = new StringBuilder("§6╔═══════════════════════════════════╗\n");
        list.append("§6║ §e§lACTIVE NICKNAMES §6(§e").append(count).append("§6)\n");
        list.append("§6╠═══════════════════════════════════╣\n");

        context.getSource().getServer().getPlayerList().getPlayers().forEach(player -> {
            if (NicknameManager.hasNickname(player.getUUID())) {
                String nickname = NicknameManager.getNickname(player.getUUID());
                list.append("§6║ §f")
                        .append(player.getName().getString())
                        .append(" §7→ ")
                        .append(nickname)
                        .append("\n");
            }
        });

        list.append("§6╚═══════════════════════════════════╝");

        context.getSource().sendSuccess(() -> Component.literal(list.toString()), false);
        return 1;
    }

    private static int whoisCommand(CommandContext<CommandSourceStack> ctx) {
        String searchNick = StringArgumentType.getString(ctx, "nickname");
        MinecraftServer server = ctx.getSource().getServer();

        Map<UUID, String> allNicknames = NicknameManager.getAllNicknames();
        List<MutableComponent> results = new ArrayList<>();

        for (Map.Entry<UUID, String> entry : allNicknames.entrySet()) {
            if (entry.getValue() == null) continue;

            String cleanNick = entry.getValue()
                    .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                    .replaceAll("&[0-9a-fk-orA-FK-OR]", "");

            if (!cleanNick.equalsIgnoreCase(searchNick)) continue;

            UUID uuid = entry.getKey();
            String mcName = "Offline";

            try {
                ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                if (online != null) {
                    mcName = online.getName().getString();
                } else {
                    try {
                        var cache = server.getProfileCache();
                        if (cache != null) {
                            var profile = cache.get(uuid);
                            if (profile != null && profile.isPresent()) {
                                mcName = profile.get().getName();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            final String finalMcName = mcName;
            final String finalNick = entry.getValue();
            final UUID finalUuid = uuid;

            MutableComponent uuidComponent = Component.literal("§8" + finalUuid)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("§7Click to open NameMC")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                                    "https://namemc.com/profile/" + finalUuid)));

            MutableComponent line = Component.literal("§7MC: §f" + finalMcName + " §8| §7UUID: ")
                    .append(uuidComponent)
                    .append(Component.literal(" §8| §7Nick: §r" + finalNick));

            results.add(line);
        }

        if (results.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.WHOIS_NOT_FOUND, "nick", searchNick)));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.WHOIS_RESULTS_HEADER, "nick", searchNick)), false);
        for (MutableComponent r : results) {
            ctx.getSource().sendSuccess(() -> r, false);
        }
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        boolean isStaff = RpEssentialsPermissions.isStaff(ctx.getSource().getPlayer());
        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_TITLE)).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_LIST)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_SCHEDULE)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_MSG)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.HELP_CMD_REPLY)).append("\n");
        if (isStaff) {
            sb.append("§6╠═══════════════════════════════════╣\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_STAFF_SECTION)).append("\n");
            sb.append("§6║ §e/rpessentials nick §8<player> <nick>\n");
            sb.append("§6║ §e/rpessentials license give/revoke/list\n");
            sb.append("§6║ §e/rpessentials staff tp/gamemode/effect\n");
            sb.append("§6║ §e/whois §8<nick>\n");
            sb.append("§6║ §e/rpessentials config status/reload\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_ENABLE)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_PLAYER)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_RESET)).append("\n");
            sb.append(MessagesConfig.get(MessagesConfig.HELP_DEATHRP_STATUS)).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.PLAYERLIST_HEADER, "count", String.valueOf(players.size())));
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            String nick = NicknameManager.getNickname(p.getUUID());
            if (nick != null) {
                sb.append(nick).append(" §8(").append(p.getName().getString()).append(")§r");
            } else {
                sb.append("§f").append(p.getName().getString());
            }
            if (i < players.size() - 1) sb.append("§7, ");
        }
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return players.size();
    }

    private static int showColors(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> RpEssentialsChatFormatter.getColorsHelp(), false);
        return 1;
    }

    /**
     * /rpessentials deathrp enable <true|false>
     * Active ou désactive le système de mort RP pour TOUS les joueurs sans override.
     */
    private static int deathRpSetGlobal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enabled = BoolArgumentType.getBool(ctx, "value");
        String staffName;
        try {
            staffName = ctx.getSource().getPlayerOrException().getName().getString();
        } catch (CommandSyntaxException e) {
            staffName = "Console";
        }

        try {
            if (RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null) {
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.set(enabled);
                RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.save();
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.DEATHRP_CONFIG_UNAVAILABLE)));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) {
            DeathRPManager.broadcastGlobalToggle(staffName, enabled, server);
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                enabled
                        ? MessagesConfig.get(MessagesConfig.DEATHRP_GLOBAL_ENABLED)
                        : MessagesConfig.get(MessagesConfig.DEATHRP_GLOBAL_DISABLED)
        ), true);
        return 1;
    }

    /**
     * /rpessentials deathrp player <joueur> enable <true|false>
     * Définit un override individuel pour le joueur spécifié.
     */
    private static int deathRpSetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        boolean enabled = BoolArgumentType.getBool(ctx, "value");

        DeathRPManager.setOverride(target.getUUID(), enabled);
        DeathRPManager.notifyPlayerToggle(target, enabled);

        ctx.getSource().sendSuccess(() -> Component.literal(
                enabled
                        ? MessagesConfig.get(MessagesConfig.DEATHRP_PLAYER_ENABLED, "player", target.getName().getString())
                        : MessagesConfig.get(MessagesConfig.DEATHRP_PLAYER_DISABLED, "player", target.getName().getString())
        ), true);
        return 1;
    }

    /**
     * /rpessentials deathrp player <joueur> reset
     * Supprime l'override individuel — le joueur suit à nouveau le global.
     */
    private static int deathRpResetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "joueur");
        DeathRPManager.removeOverride(target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.DEATHRP_OVERRIDE_RESET, "player", target.getName().getString())
        ), true);
        return 1;
    }

    /**
     * /rpessentials deathrp status
     * Affiche l'état global + tous les overrides individuels.
     */
    private static int deathRpStatus(CommandContext<CommandSourceStack> ctx) {
        boolean globalEnabled;
        try {
            globalEnabled = RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED != null
                    && RpEssentialsConfig.DEATH_RP_GLOBAL_ENABLED.get();
        } catch (IllegalStateException e) {
            globalEnabled = false;
        }
        boolean whitelistRemove;
        try {
            whitelistRemove = RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE != null
                    && RpEssentialsConfig.DEATH_RP_WHITELIST_REMOVE.get();
        } catch (IllegalStateException e) {
            whitelistRemove = false;
        }

        String active   = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_ACTIVE);
        String inactive = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_INACTIVE);
        String yes      = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_YES);
        String no       = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_NO);
        String unknown  = MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_UNKNOWN);

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_HEADER)).append("\n");
        sb.append("§6╠═══════════════════════════════\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_GLOBAL,
                "value", globalEnabled ? active : inactive)).append("\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_WHITELIST,
                "value", whitelistRemove ? yes : no)).append("\n");
        sb.append("§6╠═══════════════════════════════\n");
        sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_OVERRIDES)).append("\n");

        Map<UUID, Boolean> overrides = DeathRPManager.getAllOverrides();
        MinecraftServer server = ctx.getSource().getServer();

        if (overrides.isEmpty()) {
            sb.append(MessagesConfig.get(MessagesConfig.DEATHRP_STATUS_NO_OVERRIDES)).append("\n");
        } else {
            for (Map.Entry<UUID, Boolean> entry : overrides.entrySet()) {
                String name = unknown;
                if (server != null) {
                    ServerPlayer onlineP = server.getPlayerList().getPlayer(entry.getKey());
                    if (onlineP != null) {
                        name = onlineP.getName().getString();
                    } else if (server.getProfileCache() != null) {
                        name = server.getProfileCache().get(entry.getKey())
                                .map(com.mojang.authlib.GameProfile::getName)
                                .orElse(entry.getKey().toString());
                    }
                }
                sb.append("§6║  §e").append(name)
                        .append(" §8(").append(entry.getKey()).append("§8)")
                        .append(" §7→ ").append(entry.getValue() ? active : inactive).append("\n");
            }
        }
        sb.append("§6═══════════════════════════════");

        final String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // =========================================================================
    // LAST CONNECTION — implémentation
    // =========================================================================

    private static int lastConnectionPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUUID = null;
        if (online != null) {
            targetUUID = online.getUUID();
        } else {
            targetUUID = LastConnectionManager.findUUIDByName(targetName);
        }

        if (targetUUID == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LASTCONN_PLAYER_NOT_FOUND, "player", targetName)));
            return 0;
        }

        final UUID finalTargetUUID = targetUUID;
        LastConnectionManager.ConnectionEntry entry = LastConnectionManager.getEntry(finalTargetUUID);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.LASTCONN_NO_DATA, "player", targetName)));
            return 0;
        }

        String status   = online != null
                ? MessagesConfig.get(MessagesConfig.LASTCONN_ONLINE)
                : MessagesConfig.get(MessagesConfig.LASTCONN_OFFLINE);
        String unknown  = MessagesConfig.get(MessagesConfig.LASTCONN_UNKNOWN);
        String loginStr  = entry.lastLogin  != null ? "§f" + entry.lastLogin  : unknown;
        String logoutStr = entry.lastLogout != null ? "§f" + entry.lastLogout : unknown;
        String displayName = entry.mcName != null ? entry.mcName : targetName;

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.LASTCONN_BOX_HEADER) + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_PLAYER) +
                        "§e" + displayName + " §8(" + finalTargetUUID + ")\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_STATUS) + status + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_LOGIN)  + loginStr + "\n" +
                        MessagesConfig.get(MessagesConfig.LASTCONN_BOX_LOGOUT) + logoutStr + "\n" +
                        "§6╚════════════════════════════════════╝"
        ), false);
        return 1;
    }

    private static int lastConnectionList(CommandContext<CommandSourceStack> ctx, int count) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.LASTCONN_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        var allEntries = LastConnectionManager.getAllSortedByLogin();
        int total = allEntries.size();

        if (total == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.LASTCONN_NO_DATA_LIST)), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.LASTCONN_LIST_HEADER,
                "shown", String.valueOf(Math.min(count, total)),
                "total", String.valueOf(total))).append("\n");

        String unknown = MessagesConfig.get(MessagesConfig.LASTCONN_UNKNOWN);
        int shown = 0;
        for (var e : allEntries) {
            if (shown >= count) break;
            UUID uuid = e.getKey();
            LastConnectionManager.ConnectionEntry entry = e.getValue();
            String name = entry.mcName != null ? entry.mcName : uuid.toString().substring(0, 8) + "...";
            boolean isOnline = server.getPlayerList().getPlayer(uuid) != null;
            String bullet = isOnline
                    ? MessagesConfig.get(MessagesConfig.LASTCONN_ONLINE).substring(0, 4)   // "§a●"
                    : MessagesConfig.get(MessagesConfig.LASTCONN_OFFLINE).substring(0, 4);  // "§7○"
            String loginStr = entry.lastLogin != null ? entry.lastLogin : unknown;
            sb.append("§6║ ").append(bullet).append(" §e").append(name)
                    .append("§7 — ").append(loginStr).append("\n");
            shown++;
        }
        sb.append("§6╚═══════════════════════════════════╝");

        String finalMsg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
        return 1;
    }

    // =========================================================================
    // WARN — implémentation
    // =========================================================================

    // =========================================================================
    // WARN — warnSystemCheck (correction)
    // =========================================================================

    private static boolean warnSystemCheck(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.WARN_SYSTEM_DISABLED_CONFIG)));
                return false;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.SYSTEM_CONFIG_NOT_LOADED)));
            return false;
        }
        return true;
    }

    /** Diffuse un message à tous les staffers en ligne. */
    private static void broadcastToStaff(MinecraftServer server, String message) {
        if (message == null || message.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p)) {
                p.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static int warnAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();

        UUID issuerUUID = null;
        String issuerName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            issuerUUID = issuer.getUUID();
            issuerName = issuer.getName().getString();
        }

        String warnId = WarnManager.addWarn(
                target.getUUID(), target.getName().getString(),
                issuerUUID, issuerName,
                reason, null);

        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_RECEIVED_PERM, "id", warnId, "reason", reason)));

        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) {
                String broadcast = fmt
                        .replace("{id}", warnId)
                        .replace("{staff}", issuerName)
                        .replace("{player}", target.getName().getString())
                        .replace("{reason}", reason)
                        .replace("{expiry}", "Permanent");
                broadcastToStaff(server, broadcast);
            }
        } catch (IllegalStateException ignored) {}

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] Warn §e#" + warnId + "§a added for §e" + target.getName().getString() + "§a."), false);
        return 1;
    }

    private static int warnTemp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();

        try {
            int maxDays = ModerationConfig.WARN_MAX_TEMP_DAYS.get();
            if (maxDays > 0 && minutes > maxDays * 1440) {
                ctx.getSource().sendFailure(Component.literal(
                        MessagesConfig.get(MessagesConfig.WARN_MAX_DURATION_EXCEEDED,
                                "maxDays", String.valueOf(maxDays),
                                "maxMinutes", String.valueOf(maxDays * 1440))));
                return 0;
            }
        } catch (IllegalStateException ignored) {}

        UUID issuerUUID = null;
        String issuerName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            issuerUUID = issuer.getUUID();
            issuerName = issuer.getName().getString();
        }

        long expiresAt = System.currentTimeMillis() + (long) minutes * 60_000;
        String warnId = WarnManager.addWarn(
                target.getUUID(), target.getName().getString(),
                issuerUUID, issuerName, reason, expiresAt);

        String durationStr = MessagesConfig.formatDuration(minutes);

        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_RECEIVED_TEMP,
                        "id", warnId, "reason", reason, "duration", durationStr)));

        try {
            String fmt = ModerationConfig.WARN_ADDED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) {
                String broadcast = fmt
                        .replace("{id}", warnId)
                        .replace("{staff}", issuerName)
                        .replace("{player}", target.getName().getString())
                        .replace("{reason}", reason)
                        .replace("{expiry}", durationStr);
                broadcastToStaff(server, broadcast);
            }
        } catch (IllegalStateException ignored) {}

        String finalDurationStr = durationStr;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] Temp warn §e#" + warnId + "§a added for §e" + target.getName().getString() +
                        "§a (" + finalDurationStr + "). Reason: §f" + reason), false);
        return 1;
    }

    private static int warnRemove(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;

        String warnId = StringArgumentType.getString(ctx, "warnId");
        MinecraftServer server = ctx.getSource().getServer();

        Optional<WarnManager.WarnEntry> optEntry = WarnManager.getWarnById(warnId);
        if (optEntry.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.WARN_NOT_FOUND, "id", warnId)));
            return 0;
        }

        WarnManager.WarnEntry entry = optEntry.get();
        boolean removed = WarnManager.removeWarn(warnId);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.WARN_REMOVE_FAILED, "id", warnId)));
            return 0;
        }

        String staffName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            staffName = issuer.getName().getString();
        }

        ServerPlayer targetOnline = server.getPlayerList().getPlayer(UUID.fromString(entry.targetUUID));
        if (targetOnline != null) {
            targetOnline.sendSystemMessage(Component.literal(
                    MessagesConfig.get(MessagesConfig.WARN_REMOVED_PLAYER, "id", warnId)));
        }

        try {
            String fmt = ModerationConfig.WARN_REMOVED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) {
                broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", staffName));
            }
        } catch (IllegalStateException ignored) {}

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] Warn §e#" + warnId + "§a removed (was for §e" + entry.targetName + "§a)."), false);
        return 1;
    }

    private static int warnListPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        return displayWarnList(ctx, target.getUUID(), target.getName().getString(), true);
    }

    private static int warnListAll(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;

        var all = WarnManager.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[RpEssentials] No registered warn."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═ Tous les warns (").append(all.size()).append(") ══════════╗\n");
        for (WarnManager.WarnEntry w : all) {
            String status = w.isExpired() ? "§8[EXP]" : (w.isPermanent() ? "§c[PERM]" : "§e[TEMP]");
            sb.append("§6║ ").append(status).append(" §e#").append(w.id)
                    .append(" §7→ §f").append(w.targetName)
                    .append(" §7— ").append(w.reason).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");

        String finalMsg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
        return 1;
    }

    private static int warnInfo(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;

        String warnId = StringArgumentType.getString(ctx, "warnId");
        Optional<WarnManager.WarnEntry> optW = WarnManager.getWarnById(warnId);
        if (optW.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.WARN_NOT_FOUND, "id", warnId)));
            return 0;
        }

        WarnManager.WarnEntry w = optW.get();
        String typeStr = w.isPermanent()
                ? MessagesConfig.get(MessagesConfig.WARN_TYPE_PERMANENT)
                : (w.isExpired()
                ? MessagesConfig.get(MessagesConfig.WARN_TYPE_EXPIRED)
                : MessagesConfig.get(MessagesConfig.WARN_TYPE_TEMPORARY));

        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_INFO_HEADER, "id", w.id) + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_PLAYER_LABEL) + "§e" + w.targetName + " §8(" + w.targetUUID + ")\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_STAFF_LABEL)  + "§e" + w.issuerName + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_REASON_LABEL) + "§f" + w.reason + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_DATE_LABEL)   + "§f" + w.getFormattedDate() + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_TYPE_LABEL)   + typeStr + "\n" +
                        MessagesConfig.get(MessagesConfig.WARN_INFO_EXPIRY_LABEL) + "§f" + w.getFormattedExpiry() + "\n" +
                        "§6╚════════════════════════════════════╝"
        ), false);
        return 1;
    }

    private static int warnClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int removed = WarnManager.clearWarns(target.getUUID());

        if (removed == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    MessagesConfig.get(MessagesConfig.WARN_LIST_NONE_STAFF,
                            "player", target.getName().getString())), false);
            return 1;
        }

        target.sendSystemMessage(Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_CLEARED_PLAYER)));

        int finalRemoved = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[RpEssentials] §e" + finalRemoved + "§a warn(s) removed for §e" +
                        target.getName().getString() + "§a."), false);
        return 1;
    }

    private static int warnPurge(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;
        int purged = WarnManager.purgeExpiredWarns();
        int finalPurged = purged;
        ctx.getSource().sendSuccess(() -> Component.literal(
                MessagesConfig.get(MessagesConfig.WARN_PURGE_DONE,
                        "count", String.valueOf(finalPurged))), false);
        return 1;
    }

    /** /mywarn — player sees their own active warns */
    private static int myWarn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal(
                    MessagesConfig.get(MessagesConfig.COMMAND_PLAYER_ONLY)));
            return 0;
        }
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                player.sendSystemMessage(Component.literal(
                        MessagesConfig.get(MessagesConfig.WARN_SYSTEM_DISABLED)));
                return 0;
            }
        } catch (IllegalStateException e) {
            return 0;
        }
        return displayWarnList(ctx, player.getUUID(), player.getName().getString(), false);
    }

    /**
     * Displays the warn list for a player.
     * @param showAll if true, includes expired warns; if false, only active ones.
     */
    private static int displayWarnList(CommandContext<CommandSourceStack> ctx,
                                       UUID uuid, String name, boolean showAll) {
        List<WarnManager.WarnEntry> list = showAll
                ? WarnManager.getWarns(uuid)
                : WarnManager.getActiveWarns(uuid);

        if (list.isEmpty()) {
            String msg = showAll
                    ? MessagesConfig.get(MessagesConfig.WARN_LIST_NONE, "player", name)
                    : MessagesConfig.get(MessagesConfig.WARN_LIST_NONE_SELF);
            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        }

        long activeCount = list.stream().filter(w -> !w.isExpired()).count();
        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.WARN_LIST_HEADER,
                "player", name, "count", String.valueOf(activeCount))).append("\n");

        for (WarnManager.WarnEntry w : list) {
            String tag;
            if (w.isExpired())        tag = MessagesConfig.get(MessagesConfig.WARN_STATUS_EXPIRED_TAG);
            else if (w.isPermanent()) tag = MessagesConfig.get(MessagesConfig.WARN_STATUS_PERM_TAG);
            else                      tag = MessagesConfig.get(MessagesConfig.WARN_STATUS_TEMP_TAG);

            sb.append("§6║ ").append(tag)
                    .append(" §7#").append(w.id)
                    .append(" §8(").append(w.getFormattedDate()).append(")")
                    .append(" §7by §f").append(w.issuerName).append("\n")
                    .append("§6║   §7→ §f").append(w.reason)
                    .append(" §8| ").append(w.getFormattedExpiry()).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");

        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    public static Component getColorsHelp() {
        StringBuilder help = new StringBuilder();
        help.append("§6╔═══════════════════════════════╗\n");
        help.append("§6║  §e§lAVAILABLE COLORS§r          §6║\n");
        help.append("§6╠═══════════════════════════════╣\n");

        String[][] colors = {
                {"§0", "BLACK", "&0 or §0"},
                {"§1", "DARK_BLUE", "&1 or §1"},
                {"§2", "DARK_GREEN", "&2 or §2"},
                {"§3", "DARK_AQUA", "&3 or §3"},
                {"§4", "DARK_RED", "&4 or §4"},
                {"§5", "DARK_PURPLE", "&5 or §5"},
                {"§6", "GOLD", "&6 or §6"},
                {"§7", "GRAY", "&7 or §7"},
                {"§8", "DARK_GRAY", "&8 or §8"},
                {"§9", "BLUE", "&9 or §9"},
                {"§a", "GREEN", "&a or §a"},
                {"§b", "AQUA", "&b or §b"},
                {"§c", "RED", "&c or §c"},
                {"§d", "LIGHT_PURPLE", "&d or §d"},
                {"§e", "YELLOW", "&e or §e"},
                {"§f", "WHITE", "&f or §f"}
        };

        for (String[] color : colors) {
            help.append(String.format("§6║ %s§r %-13s §7%s §6║\n",
                    color[0] + "███",
                    color[1],
                    color[2]
            ));
        }

        help.append("§6║                               §6║\n");
        help.append("§6║ §7Formatting Codes:           §6║\n");
        help.append("§6║ §l§lBold§r §7(&l or §l)          §6║\n");
        help.append("§6║ §o§oItalic§r §7(&o or §o)        §6║\n");
        help.append("§6║ §n§nUnderline§r §7(&n or §n)     §6║\n");
        help.append("§6║ §m§mStrike§r §7(&m or §m)        §6║\n");
        help.append("§6║ §k§kObfuscated§r §7(&k or §k)   §6║\n");
        help.append("§6║ §r§rReset§r §7(&r or §r)         §6║\n");
        help.append("§6║                               §6║\n");
        help.append("§6║ §7Usage: Just type & or §     §6║\n");
        help.append("§6║ §7followed by the code!       §6║\n");
        help.append("§6╚═══════════════════════════════╝");

        return Component.literal(help.toString());
    }
}