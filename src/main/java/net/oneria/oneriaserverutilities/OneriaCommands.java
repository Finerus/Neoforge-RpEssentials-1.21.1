package net.oneria.oneriaserverutilities;

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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = OneriaServerUtilities.MODID)
public class OneriaCommands {

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

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // =========================================================================
        // MAIN COMMAND: /oneria
        // =========================================================================
        var oneriaRoot = Commands.literal("oneria");

        // -------------------------------------------------------------------------
        // 1. MODULE: CONFIGURATION (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var configNode = Commands.literal("config")
                .requires(source -> source.hasPermission(2));

        // Reload
        configNode.then(Commands.literal("reload")
                .executes(OneriaCommands::reloadConfig));

        // Status
        configNode.then(Commands.literal("status")
                .executes(OneriaCommands::showStatus));

        // Setters (On-the-fly modifications)
        var setNode = Commands.literal("set");

        // Obfuscation settings
        setNode.then(Commands.literal("proximity")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 128))
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.PROXIMITY_DISTANCE, "Proximity distance"))));

        setNode.then(Commands.literal("blur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.ENABLE_BLUR, "Blur"))));

        setNode.then(Commands.literal("obfuscatedNameLength")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.OBFUSCATED_NAME_LENGTH, "Hidden name length"))));

        setNode.then(Commands.literal("obfuscatePrefix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.OBFUSCATE_PREFIX, "Obfuscate prefix"))));

        setNode.then(Commands.literal("opsSeeAll")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.OPS_SEE_ALL, "Admin View"))));

        setNode.then(Commands.literal("debugSelfBlur")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.DEBUG_SELF_BLUR, "Debug Self Blur"))));

        // Schedule settings
        setNode.then(Commands.literal("enableSchedule")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ScheduleConfig.ENABLE_SCHEDULE, "Schedule System"))));

        setNode.then(Commands.literal("openingTime")
                .then(Commands.argument("time", StringArgumentType.greedyString())
                        .executes(OneriaCommands::setOpeningTime)));

        setNode.then(Commands.literal("closingTime")
                .then(Commands.argument("time", StringArgumentType.greedyString())
                        .executes(OneriaCommands::setClosingTime)));

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
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.OP_LEVEL_BYPASS, "OP Level Bypass"))));

        setNode.then(Commands.literal("useLuckPermsGroups")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.USE_LUCKPERMS_GROUPS, "Use LuckPerms Groups"))));

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
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[Oneria] Chat Message Color set to: " + color),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("timestampFormat")
                .then(Commands.argument("format", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String format = StringArgumentType.getString(ctx, "format");
                            ChatConfig.TIMESTAMP_FORMAT.set(format);
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[Oneria] Timestamp Format set to: " + format),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("enableColorsCommand")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_COLORS_COMMAND, "Colors Command"))));

        setNode.then(Commands.literal("enableSneakStealth")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.ENABLE_SNEAK_STEALTH, "Sneak Stealth Mode"))));

        setNode.then(Commands.literal("sneakProximityDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(1, 32))
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.SNEAK_PROXIMITY_DISTANCE, "Sneak Detection Distance"))));

        setNode.then(Commands.literal("hideNametags")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.HIDE_NAMETAGS, "Hide Nametags"))));

        setNode.then(Commands.literal("showNametagPrefixSuffix")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.SHOW_NAMETAG_PREFIX_SUFFIX, "Show Nametag Prefix/Suffix"))));

        // Join/Leave messages settings
        setNode.then(Commands.literal("enableCustomJoinLeave")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, ChatConfig.ENABLE_CUSTOM_JOIN_LEAVE, "Custom Join/Leave Messages"))));

        setNode.then(Commands.literal("joinMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.JOIN_MESSAGE.set(msg);
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[Oneria] Join Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("leaveMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            ChatConfig.LEAVE_MESSAGE.set(msg);
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[Oneria] Leave Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        // World Border settings
        setNode.then(Commands.literal("enableWorldBorderWarning")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> updateConfigBool(ctx, OneriaConfig.ENABLE_WORLD_BORDER_WARNING, "World Border Warning"))));

        setNode.then(Commands.literal("worldBorderDistance")
                .then(Commands.argument("value", IntegerArgumentType.integer(100, 100000))
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.WORLD_BORDER_DISTANCE, "World Border Distance"))));

        setNode.then(Commands.literal("worldBorderMessage")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            OneriaConfig.WORLD_BORDER_MESSAGE.set(msg);
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("§a[Oneria] World Border Message set to: " + msg),
                                    true
                            );
                            return 1;
                        })));

        setNode.then(Commands.literal("worldBorderCheckInterval")
                .then(Commands.argument("value", IntegerArgumentType.integer(20, 200))
                        .executes(ctx -> updateConfigInt(ctx, OneriaConfig.WORLD_BORDER_CHECK_INTERVAL, "World Border Check Interval"))));

        setNode.then(Commands.literal("zoneMessageMode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("IMMERSIVE").suggest("CHAT").suggest("ACTION_BAR");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode").toUpperCase();
                            if (!mode.equals("IMMERSIVE") && !mode.equals("CHAT") && !mode.equals("ACTION_BAR")) {
                                ctx.getSource().sendFailure(Component.literal("§cModes valides : IMMERSIVE, CHAT, ACTION_BAR"));
                                return 0;
                            }
                            OneriaConfig.ZONE_MESSAGE_MODE.set(mode);
                            OneriaConfig.SPEC.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] Zone Message Mode set to: " + mode), true);
                            return 1;
                        })));

        configNode.then(setNode);
        oneriaRoot.then(configNode);

        // -------------------------------------------------------------------------
        // 2. MODULE: STAFF & MODERATION (Requires 'isStaff' permission)
        // -------------------------------------------------------------------------
        var staffNode = Commands.literal("staff")
                .requires(src -> OneriaPermissions.isStaff(src.getPlayer()));

        // Silent Gamemode
        staffNode.then(Commands.literal("gamemode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("survival").suggest("creative").suggest("adventure").suggest("spectator");
                            return builder.buildFuture();
                        })
                        .executes(OneriaCommands::silentGamemodeSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(OneriaCommands::silentGamemodeTarget)
                        )
                )
        );

        // Silent Teleport
        staffNode.then(Commands.literal("tp")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(OneriaCommands::silentTeleport)
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
                                                .executes(OneriaCommands::silentEffect)
                                        )
                                )
                        )
                )
        );

        // Platforms
        staffNode.then(Commands.literal("platform")
                .executes(OneriaCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(OneriaCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(OneriaCommands::platformTargetSpecific)
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
                                                        .executes(OneriaCommands::setPlatform)
                                                )
                                        )
                                )
                        )
                ));

        oneriaRoot.then(staffNode);

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
                        .executes(OneriaCommands::addToWhitelist)));

        // Remove - avec autocomplétion des joueurs dans la whitelist
        whitelistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            OneriaConfig.WHITELIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(OneriaCommands::removeFromWhitelist)));

        whitelistNode.then(Commands.literal("list")
                .executes(OneriaCommands::listWhitelist));

        oneriaRoot.then(whitelistNode);

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
                        .executes(OneriaCommands::addToBlacklist)));

        // Remove - avec autocomplétion des joueurs dans la blacklist
        blacklistNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            OneriaConfig.BLACKLIST.get().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(OneriaCommands::removeFromBlacklist)));

        blacklistNode.then(Commands.literal("list")
                .executes(OneriaCommands::listBlacklist));

        oneriaRoot.then(blacklistNode);

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
                        .executes(OneriaCommands::addToAlwaysVisible)));

        // Remove - avec autocomplétion des joueurs dans la always visible list
        alwaysVisibleNode.then(Commands.literal("remove")
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            try {
                                if (OneriaConfig.ALWAYS_VISIBLE_LIST != null) {
                                    OneriaConfig.ALWAYS_VISIBLE_LIST.get().forEach(builder::suggest);
                                }
                            } catch (Exception e) {
                                // Config pas chargée
                            }
                            return builder.buildFuture();
                        })
                        .executes(OneriaCommands::removeFromAlwaysVisible)));

        alwaysVisibleNode.then(Commands.literal("list")
                .executes(OneriaCommands::listAlwaysVisible));

        oneriaRoot.then(alwaysVisibleNode);

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
                                .executes(OneriaCommands::giveLicense)
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
                                .executes(OneriaCommands::revokeLicense)
                        )
                )
        );

        // List - avec argument optionnel pour joueur spécifique ou tous
        licenseNode.then(Commands.literal("list")
                .executes(OneriaCommands::listAllLicenses) // Sans argument = tous les joueurs
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(OneriaCommands::listLicenses) // Avec argument = joueur spécifique
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
                                .executes(OneriaCommands::checkLicense)
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
                                        .executes(OneriaCommands::giveRPLicense)
                                )
                        )
                )
        );

        oneriaRoot.then(licenseNode);

        // -------------------------------------------------------------------------
        // 7. MODULE: NICKNAME (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var nickNode = Commands.literal("nick")
                .requires(source -> source.hasPermission(2));

        nickNode.then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(OneriaCommands::setNickname)
                )
                .executes(OneriaCommands::resetNickname)
        );

        nickNode.then(Commands.literal("list")
                .executes(OneriaCommands::listNicknames)
        );

        oneriaRoot.then(nickNode);

        // -------------------------------------------------------------------------
        // 8. MODULE: WHOIS (Requires OP Level 2)
        // -------------------------------------------------------------------------
        var whoisNode = Commands.literal("whois")
                .requires(source -> source.hasPermission(2));
        whoisNode.then(Commands.argument("nickname", StringArgumentType.greedyString())
                .executes(OneriaCommands::whoisCommand));
        oneriaRoot.then(whoisNode);

        // Also register as standalone /whois
        dispatcher.register(Commands.literal("whois")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(OneriaCommands::whoisCommand)));

        // -------------------------------------------------------------------------
        // 9. MODULE: SCHEDULE (Public)
        // -------------------------------------------------------------------------
        oneriaRoot.then(Commands.literal("schedule")
                .executes(OneriaCommands::showSchedule));

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
                                            OneriaMessagingManager.sendMessage(sender, target, msg);
                                        } else {
                                            MutableComponent toTarget = Component.literal("§7[MP] §f§lServeur§r§7 vous écrit : " + msg);
                                            target.sendSystemMessage(toTarget);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§7[MP] Vous écrivez à §f§l" + target.getName().getString() + "§r§7 : " + msg), false);
                                        }
                                        return 1;
                                    }))));
        }

        dispatcher.register(Commands.literal("r")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            String msg = StringArgumentType.getString(ctx, "message");
                            return OneriaMessagingManager.reply(sender, msg, ctx.getSource().getServer());
                        })));

        // Remplace /list vanilla
        dispatcher.getRoot().getChildren().removeIf(node -> node.getName().equals("list"));
        dispatcher.register(Commands.literal("list")
                .executes(OneriaCommands::playerList));

        // /oneria help
        oneriaRoot.then(Commands.literal("help")
                .executes(OneriaCommands::showHelp));

        // -------------------------------------------------------------------------
        // MODULE: LAST CONNECTION (Requires isStaff)
        // -------------------------------------------------------------------------
        var lastConnNode = Commands.literal("lastconnection")
                .requires(src -> OneriaPermissions.isStaff(src.getPlayer()));

        // /oneria lastconnection <player>
        lastConnNode.then(Commands.argument("player", StringArgumentType.word())
                .executes(OneriaCommands::lastConnectionPlayer));

        // /oneria lastconnection list [count]
        lastConnNode.then(Commands.literal("list")
                .executes(ctx -> lastConnectionList(ctx, 20))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> lastConnectionList(ctx, IntegerArgumentType.getInteger(ctx, "count"))))
        );

        oneriaRoot.then(lastConnNode);

        // -------------------------------------------------------------------------
        // MODULE: WARN (Staff commands + player /mywarn)
        // -------------------------------------------------------------------------
        var warnNode = Commands.literal("warn")
                .requires(src -> OneriaPermissions.isStaff(src.getPlayer()));

        // /oneria warn add <player> <reason>
        warnNode.then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(OneriaCommands::warnAdd))));

        // /oneria warn temp <player> <minutes> <reason>
        warnNode.then(Commands.literal("temp")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(OneriaCommands::warnTemp)))));

        // /oneria warn remove <warnId>
        warnNode.then(Commands.literal("remove")
                .then(Commands.argument("warnId", StringArgumentType.word())
                        .executes(OneriaCommands::warnRemove)));

        // /oneria warn list [player]  — staff peut voir n'importe qui
        warnNode.then(Commands.literal("list")
                .executes(OneriaCommands::warnListAll)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(OneriaCommands::warnListPlayer)));

        // /oneria warn info <warnId>
        warnNode.then(Commands.literal("info")
                .then(Commands.argument("warnId", StringArgumentType.word())
                        .executes(OneriaCommands::warnInfo)));

        // /oneria warn clear <player>  — supprimer tous les warns d'un joueur
        warnNode.then(Commands.literal("clear")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(OneriaCommands::warnClear)));

        // /oneria warn purge  — purger les warns expirés
        warnNode.then(Commands.literal("purge")
                .executes(OneriaCommands::warnPurge));

        oneriaRoot.then(warnNode);

        // /mywarn — alias standalone accessible à tous les joueurs
        dispatcher.register(Commands.literal("mywarn")
                .requires(src -> src.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                .executes(OneriaCommands::myWarn));


        // =========================================================================
        // Register root
        // =========================================================================
        dispatcher.register(oneriaRoot);

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
                    return OneriaCommands.showColors(ctx);
                }));

        // =========================================================================
        // HANDY ALIASES
        // =========================================================================
        dispatcher.register(Commands.literal("schedule")
                .executes(OneriaCommands::showSchedule));

        dispatcher.register(Commands.literal("horaires")
                .executes(OneriaCommands::showSchedule));

        dispatcher.register(Commands.literal("platform")
                .requires(src -> OneriaPermissions.isStaff(src.getPlayer()))
                .executes(OneriaCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(OneriaCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(OneriaCommands::platformTargetSpecific)
                        )
                ));
    }

    // =============================================================================
    // IMPLEMENTATION LOGIC (HANDLERS)
    // =============================================================================

    // --- CONFIG HANDLERS ---

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        OneriaScheduleManager.reload();
        OneriaPermissions.clearCache();
        NicknameManager.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] Configuration, nicknames and nametags reloaded!"), true);
        return 1;
    }

    private static int updateConfigInt(CommandContext<CommandSourceStack> ctx, ModConfigSpec.IntValue config, String name) {
        int val = IntegerArgumentType.getInteger(ctx, "value");
        config.set(val);
        OneriaConfig.SPEC.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] " + name + " set to: " + val), true);
        return 1;
    }

    private static int updateConfigBool(CommandContext<CommandSourceStack> ctx, ModConfigSpec.BooleanValue config, String name) {
        boolean val = BoolArgumentType.getBool(ctx, "value");
        config.set(val);
        OneriaConfig.SPEC.save();

        if (config == OneriaConfig.HIDE_NAMETAGS) {
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player -> {
                PacketDistributor.sendToPlayer(player, new HideNametagsPacket(val));
            });
            OneriaServerUtilities.LOGGER.info("Broadcast nametag config update: hide={}", val);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] " + name + " : " + (val ? "§aENABLED" : "§cDISABLED")), true);
        return 1;
    }

    private static int setOpeningTime(CommandContext<CommandSourceStack> ctx) {
        String time = StringArgumentType.getString(ctx, "time");
        if (!time.matches("\\d{2}:\\d{2}")) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid format! Use HH:MM (e.g., 19:00)"));
            return 0;
        }
        ScheduleConfig.OPENING_TIME.set(time);
        OneriaConfig.SPEC.save();
        OneriaScheduleManager.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] Opening time set to: " + time), true);
        return 1;
    }

    private static int setClosingTime(CommandContext<CommandSourceStack> ctx) {
        String time = StringArgumentType.getString(ctx, "time");
        if (!time.matches("\\d{2}:\\d{2}")) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid format! Use HH:MM (e.g., 23:59)"));
            return 0;
        }
        ScheduleConfig.CLOSING_TIME.set(time);
        OneriaConfig.SPEC.save();
        OneriaScheduleManager.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] Closing time set to: " + time), true);
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
                scheduleStatus = OneriaScheduleManager.isServerOpen() ? "§aOPEN" : "§cCLOSED";
            } catch (Exception e) {
                scheduleStatus = "§7N/A";
            }

            String statusMessage =
                    "§6╔═══════════════════════════════════╗\n" +
                            "§6║  §e§lONERIA MOD - STATUS§r          §6║\n" +
                            "§6╠═══════════════════════════════════╣\n" +
                            "§6║ §7Obfuscation\n" +
                            "§6║  §eBlur: §f" + safe.apply(() -> OneriaConfig.ENABLE_BLUR.get()) + "\n" +
                            "§6║  §eProximity: §f" + safe.apply(() -> OneriaConfig.PROXIMITY_DISTANCE.get()) + " blocks\n" +
                            "§6║  §eObfuscate Prefix: §f" + safe.apply(() -> OneriaConfig.OBFUSCATE_PREFIX.get()) + "\n" +
                            "§6║  §eOPs See All: §f" + safe.apply(() -> OneriaConfig.OPS_SEE_ALL.get()) + "\n" +
                            "§6║  §eHide Nametags: §f" + safe.apply(() -> OneriaConfig.HIDE_NAMETAGS.get()) + "\n" +
                            "§6║  §eSneak Stealth: §f" + safe.apply(() -> OneriaConfig.ENABLE_SNEAK_STEALTH.get()) + "\n" +
                            "§6║  §eSneak Distance: §f" + safe.apply(() -> OneriaConfig.SNEAK_PROXIMITY_DISTANCE.get()) + " blocks\n" +
                            "§6║  §eAlways Visible: §f" + safe.apply(() -> OneriaConfig.ALWAYS_VISIBLE_LIST.get().size()) + " players\n" +
                            "§6║\n" +
                            "§6║ §7Schedule\n" +
                            "§6║  §eEnabled: §f" + safe.apply(() -> ScheduleConfig.ENABLE_SCHEDULE.get()) + "\n" +
                            "§6║  §eStatus: " + scheduleStatus + "\n" +
                            "§6║  §eOpening: §f" + safe.apply(() -> ScheduleConfig.OPENING_TIME.get()) + "\n" +
                            "§6║  §eClosing: §f" + safe.apply(() -> ScheduleConfig.CLOSING_TIME.get()) + "\n" +
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
                            "§6║  §eEnabled: §f" + safe.apply(() -> OneriaConfig.ENABLE_WORLD_BORDER_WARNING.get()) + "\n" +
                            "§6║  §eDistance: §f" + safe.apply(() -> OneriaConfig.WORLD_BORDER_DISTANCE.get()) + " blocks\n" +
                            "§6║\n" +
                            "§6║ §7Moderation\n" +
                            "§6║  §eSilent Commands: §f" + safe.apply(() -> ModerationConfig.ENABLE_SILENT_COMMANDS.get()) + "\n" +
                            "§6║  §ePlatforms: §f" + safe.apply(() -> ModerationConfig.ENABLE_PLATFORMS.get()) + "\n" +
                            "§6║  §eWelcome Message: §f" + safe.apply(() -> ScheduleConfig.ENABLE_WELCOME.get()) + "\n" +
                            "§6╚═══════════════════════════════════╝";

            context.getSource().sendSuccess(() -> Component.literal(statusMessage), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c[Oneria] Error displaying status: " + e.getMessage()));
            OneriaServerUtilities.LOGGER.error("Error in showStatus", e);
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
                ctx.getSource().sendFailure(Component.literal("§c[Oneria] " + player + " is already in " + listName + "."));
                return 0;
            }
            list.add(player);
            ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] " + player + " added to " + listName + "."), true);
        } else {
            if (!list.remove(player)) {
                ctx.getSource().sendFailure(Component.literal("§c[Oneria] " + player + " is not in " + listName + "."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§a[Oneria] " + player + " removed from " + listName + "."), true);
        }

        config.set(list);
        OneriaConfig.SPEC.save();
        return 1;
    }

    private static int addToWhitelist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.WHITELIST, "whitelist", true);
    }

    private static int removeFromWhitelist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.WHITELIST, "whitelist", false);
    }

    private static int listWhitelist(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> whitelist = OneriaConfig.WHITELIST.get();
        if (whitelist.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Whitelist is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Whitelist: §f" + String.join(", ", whitelist)), false);
        }
        return 1;
    }

    private static int addToBlacklist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.BLACKLIST, "blacklist", true);
    }

    private static int removeFromBlacklist(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.BLACKLIST, "blacklist", false);
    }

    private static int listBlacklist(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> blacklist = OneriaConfig.BLACKLIST.get();
        if (blacklist.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Blacklist is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Blacklist (always hidden): §f" + String.join(", ", blacklist)), false);
        }
        return 1;
    }

    private static int addToAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", true);
    }

    private static int removeFromAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        return modifyList(ctx, OneriaConfig.ALWAYS_VISIBLE_LIST, "Always Visible list", false);
    }

    private static int listAlwaysVisible(CommandContext<CommandSourceStack> ctx) {
        List<? extends String> alwaysVisible = OneriaConfig.ALWAYS_VISIBLE_LIST.get();
        if (alwaysVisible.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Always Visible list is empty."), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Always Visible (always shown in TabList): §f" + String.join(", ", alwaysVisible)), false);
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
        OneriaConfig.SPEC.save();

        final boolean wasUpdated = updated;
        ctx.getSource().sendSuccess(() ->
                        Component.literal("§a[Oneria] Platform '" + platformName + "' " +
                                (wasUpdated ? "updated" : "created") + " at " + dimension + " " + x + " " + y + " " + z),
                true
        );

        return 1;
    }

    // --- SCHEDULE HANDLER ---

    private static int showSchedule(CommandContext<CommandSourceStack> ctx) {
        boolean isOpen = OneriaScheduleManager.isServerOpen();
        String timeInfo = OneriaScheduleManager.getTimeUntilNextEvent();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§8§m----------------------------------\n" +
                        " §6§lSERVER SCHEDULE\n" +
                        " §7Current Status: " + (isOpen ? "§a§lOPEN" : "§c§lCLOSED") + "\n" +
                        " §7Opening: §e" + ScheduleConfig.OPENING_TIME.get() + "\n" +
                        " §7Closing: §e" + ScheduleConfig.CLOSING_TIME.get() + "\n\n" +
                        " §f" + timeInfo + "\n" +
                        "§8§m----------------------------------"
        ), false);
        return 1;
    }

    // --- UTILS ---

    private static void logToStaff(CommandSourceStack source, String msg) {
        if (!ModerationConfig.LOG_TO_STAFF.get()) return;
        Component txt = Component.literal("§7§o[StaffLog] " + msg);
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (OneriaPermissions.isStaff(p)) p.sendSystemMessage(txt);
        });
        if (ModerationConfig.LOG_TO_CONSOLE.get()) OneriaServerUtilities.LOGGER.info("[StaffLog] " + msg);
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

        ProfessionRestrictionManager.ProfessionData professionData =
                ProfessionRestrictionManager.getProfessionData(professionId);

        if (professionData == null) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Metier inconnu: " + professionId));
            return 0;
        }

        String displayName = NicknameManager.getDisplayName(target);

        ItemStack license = new ItemStack(OneriaItems.LICENSE.get());

        license.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(professionData.colorCode + "§lPermis de " + professionData.displayName));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("§7Delivre a: §f" + displayName));
        lore.add(Component.literal("§7Date: §f" + java.time.LocalDate.now().toString()));
        license.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));

        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("professionId", professionId);
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));

        if (!target.getInventory().add(license)) {
            target.drop(license, false);
        }

        LicenseManager.addLicense(target.getUUID(), professionId);

        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("GIVE", staff, target, professionId, null);

        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        ctx.getSource().sendSuccess(() ->
                Component.literal("§a[Oneria] Permis de " + professionData.getFormattedName() +
                        "§a donne a §f" + displayName), true);

        target.sendSystemMessage(Component.literal("§aVous avez recu un " + professionData.getFormattedName() +
                "§6§l Permis§a !"));

        return 1;
    }

    private static int revokeLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");

        // Retirer uniquement de licenses.json — l'item reste dans l'inventaire
        LicenseManager.removeLicense(target.getUUID(), profession);

        // Invalider le cache + sync client immediat
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.logAction("REVOKE", staff, target, profession, null);

        TempLicenseExpirationManager.markRevokedLicenseItems(target);

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(profession);
        String profDisplayName = profData != null ? profData.displayName : profession;

        ctx.getSource().sendSuccess(() ->
                Component.literal("§a[Oneria] Permis de §f" + profDisplayName +
                        "§a revoque pour §f" + target.getName().getString()), true);

        target.sendSystemMessage(Component.literal(
                "§cVotre permis de §f" + profDisplayName + "§c a ete revoque."));

        return 1;
    }

    private static int giveRPLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String professionId = StringArgumentType.getString(ctx, "profession");
        int days = IntegerArgumentType.getInteger(ctx, "days_duration");

        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Métier inconnu: " + professionId));
            return 0;
        }

        String displayName = NicknameManager.getDisplayName(target);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String issued  = java.time.LocalDate.now().format(fmt);
        String expires = java.time.LocalDate.now().plusDays(days).format(fmt);

        // Item
        ItemStack license = new ItemStack(OneriaItems.LICENSE.get());
        license.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(profData.colorCode + "§lPermis de " + profData.displayName));
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.literal("§7Délivré à: §f" + displayName));
        lore.add(Component.literal("§7Date de délivrance: §f" + issued));
        lore.add(Component.literal("§7Valide jusqu'au: §f" + expires));
        license.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(lore));
        if (!target.getInventory().add(license)) target.drop(license, false);

        // Donne les vraies permissions — retiré automatiquement à expiration
        LicenseManager.addLicense(target.getUUID(), professionId);

        // Enregistre dans licenses-temp.json + audit
        ServerPlayer staff = ctx.getSource().getPlayer();
        LicenseManager.addTempLicense(staff, target, professionId, days, issued, expires);
        LicenseManager.logAction("GIVE_RP", staff, target, professionId,
                days + " jours, expire le " + expires);

        // Invalider cache + sync client immédiat
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Oneria] Permis temporaire de " + profData.getFormattedName() +
                        "§a donné à §f" + displayName +
                        " §7(" + days + " jours, expire le " + expires + ")"), true);
        target.sendSystemMessage(Component.literal(
                "§aVous avez reçu un " + profData.getFormattedName() +
                        "§6§l Permis §7valable jusqu'au §f" + expires));
        return 1;
    }


    private static int listLicenses(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<String> licenses = LicenseManager.getLicenses(target.getUUID());

        if (licenses.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§e[Oneria] §f" + target.getName().getString() +
                            "§e n'a aucun permis."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append("§6║ §e§lPERMIS — §f").append(target.getName().getString()).append("\n");
        sb.append("§6╠═══════════════════════════════════╣\n");

        for (String profession : licenses) {
            String expiry = LicenseManager.getTempExpirationDate(target.getUUID(), profession);
            if (expiry != null) {
                sb.append("§6║ §f").append(profession)
                        .append(" §7(RP - expire le ").append(expiry).append(")\n");
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
            ctx.getSource().sendSuccess(() -> Component.literal("§e[Oneria] Aucune licence enregistrée."), false);
            return 1;
        }

        StringBuilder result = new StringBuilder("§6╔═══════════════════════════════════╗\n");
        result.append("§6║ §e§lLICENSES - TOUS LES JOUEURS §6║\n");
        result.append("§6╠═══════════════════════════════════╣\n");

        var server = ctx.getSource().getServer();

        for (var entry : allLicenses.entrySet()) {
            java.util.UUID uuid = entry.getKey();
            List<String> licenses = entry.getValue();

            // Récupération sécurisée du nom
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(uuid);
            String playerName = (onlinePlayer != null)
                    ? onlinePlayer.getName().getString()
                    : server.getProfileCache().get(uuid)
                    .map(com.mojang.authlib.GameProfile::getName)
                    .orElse(uuid.toString());

            StringBuilder licLine = new StringBuilder();
            for (String lic : licenses) {
                if (licLine.length() > 0) licLine.append("§7, ");
                String expiry = LicenseManager.getTempExpirationDate(uuid, lic);
                licLine.append("§f").append(lic);
                if (expiry != null) {
                    licLine.append(" §7(RP - ").append(expiry).append(")");
                }
            }

            result.append("§6║ §f").append(playerName).append("§7: ")
                    .append(licLine.length() > 0 ? licLine : "§8Aucune").append("\n");
        }

        result.append("§6╚═══════════════════════════════════╝");
        ctx.getSource().sendSuccess(() -> Component.literal(result.toString()), false);

        return 1;
    }

    private static int checkLicense(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String profession = StringArgumentType.getString(ctx, "profession");

        boolean has = LicenseManager.hasLicense(target.getUUID(), profession);

        ctx.getSource().sendSuccess(() ->
                Component.literal("§e[Oneria] §f" + target.getName().getString() +
                        (has ? " §apossède" : " §cne possède pas") + "§e un permis de §f" + profession), false);

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
                    Component.literal("§a[Oneria] Nickname for §f" + target.getName().getString() +
                            "§a set to: " + formattedNickname), true);

            target.sendSystemMessage(Component.literal("§aYour nickname has been changed to: " + formattedNickname));

            OneriaServerUtilities.LOGGER.info("Nickname set for {}: {}", target.getName().getString(), formattedNickname);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError while setting nickname."));
            OneriaServerUtilities.LOGGER.error("Error setting nickname", e);
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
                    Component.literal("§a[Oneria] Nickname for §f" + target.getName().getString() + "§a reset."), true);

            target.sendSystemMessage(Component.literal("§aYour nickname has been reset."));

            OneriaServerUtilities.LOGGER.info("Nickname reset for {}", target.getName().getString());

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError while resetting nickname."));
            OneriaServerUtilities.LOGGER.error("Error resetting nickname", e);
            return 0;
        }
    }

    private static int listNicknames(CommandContext<CommandSourceStack> context) {
        int count = NicknameManager.count();

        if (count == 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("§e[Oneria] No active nicknames."), false);
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
            String mcName = "Hors-ligne";

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
                                    Component.literal("§7Cliquer pour ouvrir NameMC")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                                    "https://namemc.com/profile/" + finalUuid)));

            MutableComponent line = Component.literal("§7MC: §f" + finalMcName + " §8| §7UUID: ")
                    .append(uuidComponent)
                    .append(Component.literal(" §8| §7Nick: §r" + finalNick));

            results.add(line);
        }

        if (results.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c[Whois] Aucun joueur avec le nickname : §f" + searchNick));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6[Whois] §7Résultats pour \"§f" + searchNick + "§7\" :"), false);
        for (MutableComponent r : results) {
            ctx.getSource().sendSuccess(() -> r, false);
        }
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        boolean isStaff = OneriaPermissions.isStaff(ctx.getSource().getPlayer());
        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═══════════════════════════════════╗\n");
        sb.append("§6║ §e§lONERIA MOD §7— Commandes\n");
        sb.append("§6╠═══════════════════════════════════╣\n");
        sb.append("§6║ §e/list §7— Joueurs en ligne\n");
        sb.append("§6║ §e/schedule §7— Horaires du serveur\n");
        sb.append("§6║ §e/msg §8<joueur> <message> §7— MP\n");
        sb.append("§6║ §e/r §8<message> §7— Répondre\n");
        if (isStaff) {
            sb.append("§6╠═══════════════════════════════════╣\n");
            sb.append("§6║ §c§lSTAFF\n");
            sb.append("§6║ §e/oneria nick §8<joueur> <nick>\n");
            sb.append("§6║ §e/oneria license give/revoke/list\n");
            sb.append("§6║ §e/oneria staff tp/gamemode/effect\n");
            sb.append("§6║ §e/whois §8<nick>\n");
            sb.append("§6║ §e/oneria config status/reload\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int playerList(CommandContext<CommandSourceStack> ctx) {
        var players = ctx.getSource().getServer().getPlayerList().getPlayers();
        StringBuilder sb = new StringBuilder();
        sb.append("§eJoueurs en ligne (").append(players.size()).append(") : ");
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
        ctx.getSource().sendSuccess(() -> OneriaChatFormatter.getColorsHelp(), false);
        return 1;
    }

    // =========================================================================
    // LAST CONNECTION — implémentation
    // =========================================================================

    private static int lastConnectionPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal("§c[Oneria] Le suivi de connexion est désactivé dans la config."));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Config non chargée."));
            return 0;
        }

        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = ctx.getSource().getServer();

        // Chercher d'abord parmi les joueurs en ligne
        ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUUID = null;
        if (online != null) {
            targetUUID = online.getUUID();
        } else {
            // Chercher dans le cache de LastConnectionManager
            targetUUID = LastConnectionManager.findUUIDByName(targetName);
        }

        if (targetUUID == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c[Oneria] Joueur introuvable : §e" + targetName +
                            "\n§7(Le joueur doit s'être déjà connecté au moins une fois pour apparaître ici.)"));
            return 0;
        }

        // Capture finale nécessaire : targetUUID est réassigné dans le if/else ci-dessus
        final UUID finalTargetUUID = targetUUID;

        LastConnectionManager.ConnectionEntry entry = LastConnectionManager.getEntry(finalTargetUUID);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Aucune donnée de connexion pour §e" + targetName));
            return 0;
        }

        String status = online != null ? "§a● En ligne" : "§7○ Hors ligne";
        String loginStr  = entry.lastLogin  != null ? "§f" + entry.lastLogin  : "§7Inconnu";
        String logoutStr = entry.lastLogout != null ? "§f" + entry.lastLogout : "§7Inconnu";

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6╔═ Dernière connexion ═══════════════╗\n" +
                        "§6║ §7Joueur : §e" + (entry.mcName != null ? entry.mcName : targetName) + " §8(" + finalTargetUUID + ")\n" +
                        "§6║ §7Statut  : " + status + "\n" +
                        "§6║ §7Login   : " + loginStr + "\n" +
                        "§6║ §7Logout  : " + logoutStr + "\n" +
                        "§6╚════════════════════════════════════╝"
        ), false);
        return 1;
    }

    private static int lastConnectionList(CommandContext<CommandSourceStack> ctx, int count) {
        try {
            if (!ModerationConfig.ENABLE_LAST_CONNECTION.get()) {
                ctx.getSource().sendFailure(Component.literal("§c[Oneria] Le suivi de connexion est désactivé dans la config."));
                return 0;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Config non chargée."));
            return 0;
        }

        MinecraftServer server = ctx.getSource().getServer();
        var allEntries = LastConnectionManager.getAllSortedByLogin();
        int total = allEntries.size();

        if (total == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7[Oneria] Aucune donnée de connexion enregistrée."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═ Dernières connexions (").append(Math.min(count, total)).append("/").append(total).append(") ══╗\n");

        int shown = 0;
        for (var e : allEntries) {
            if (shown >= count) break;
            UUID uuid = e.getKey();
            LastConnectionManager.ConnectionEntry entry = e.getValue();
            String name = entry.mcName != null ? entry.mcName : uuid.toString().substring(0, 8) + "...";
            boolean isOnline = server.getPlayerList().getPlayer(uuid) != null;
            String bullet = isOnline ? "§a●" : "§7○";
            String loginStr = entry.lastLogin != null ? entry.lastLogin : "§8Inconnu";
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

    private static boolean warnSystemCheck(CommandContext<CommandSourceStack> ctx) {
        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                ctx.getSource().sendFailure(Component.literal("§c[Oneria] Le système de warns est désactivé dans la config."));
                return false;
            }
        } catch (IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Config non chargée."));
            return false;
        }
        return true;
    }

    /** Diffuse un message à tous les staffers en ligne. */
    private static void broadcastToStaff(MinecraftServer server, String message) {
        if (message == null || message.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (OneriaPermissions.isStaff(p)) {
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
                reason, null /* permanent */);

        // Notifier la cible
        target.sendSystemMessage(Component.literal(
                "§c⚠ §lVous avez reçu un avertissement §r§c(warn #" + warnId + ") !\n" +
                        "§7Raison : §f" + reason + "\n" +
                        "§7Durée  : §fPermanent\n" +
                        "§7Tapez §l/mywarn §r§7pour voir tous vos avertissements."));

        // Notifier le staff
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

        // Feedback à l'émetteur (s'il n'est pas staff — pour éviter doublon)
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Oneria] Warn §e#" + warnId + "§a ajouté pour §e" + target.getName().getString() +
                        "§a. Raison : §f" + reason), false);
        return 1;
    }

    private static int warnTemp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!warnSystemCheck(ctx)) return 0;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        String reason = StringArgumentType.getString(ctx, "reason");
        MinecraftServer server = ctx.getSource().getServer();

        // Vérifier la limite de durée max
        try {
            int maxDays = ModerationConfig.WARN_MAX_TEMP_DAYS.get();
            if (maxDays > 0 && minutes > maxDays * 1440) {
                ctx.getSource().sendFailure(Component.literal(
                        "§c[Oneria] Durée maximale autorisée : " + maxDays + " jours (" + (maxDays * 1440) + " minutes)."));
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
                issuerUUID, issuerName,
                reason, expiresAt);

        // Formater la durée pour l'affichage
        String durationStr;
        if (minutes < 60) durationStr = minutes + " minute(s)";
        else if (minutes < 1440) durationStr = (minutes / 60) + "h " + (minutes % 60) + "min";
        else durationStr = (minutes / 1440) + "j " + ((minutes % 1440) / 60) + "h";

        // Notifier la cible
        target.sendSystemMessage(Component.literal(
                "§c⚠ §lVous avez reçu un avertissement temporaire §r§c(warn #" + warnId + ") !\n" +
                        "§7Raison : §f" + reason + "\n" +
                        "§7Durée  : §f" + durationStr + "\n" +
                        "§7Tapez §l/mywarn §r§7pour voir tous vos avertissements."));

        // Notifier le staff
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
                "§a[Oneria] Warn temporaire §e#" + warnId + "§a ajouté pour §e" + target.getName().getString() +
                        "§a (" + finalDurationStr + "). Raison : §f" + reason), false);
        return 1;
    }

    private static int warnRemove(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;

        String warnId = StringArgumentType.getString(ctx, "warnId");
        MinecraftServer server = ctx.getSource().getServer();

        // Récupérer l'entrée avant suppression (pour le broadcast)
        Optional<WarnManager.WarnEntry> optEntry = WarnManager.getWarnById(warnId);
        if (optEntry.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Warn introuvable : §e#" + warnId));
            return 0;
        }

        WarnManager.WarnEntry entry = optEntry.get();
        boolean removed = WarnManager.removeWarn(warnId);

        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Échec de la suppression du warn §e#" + warnId));
            return 0;
        }

        String staffName = "Console";
        if (ctx.getSource().getEntity() instanceof ServerPlayer issuer) {
            staffName = issuer.getName().getString();
        }

        // Notifier le joueur visé s'il est en ligne
        ServerPlayer target = server.getPlayerList().getPlayer(UUID.fromString(entry.targetUUID));
        if (target != null) {
            target.sendSystemMessage(Component.literal(
                    "§a✔ Votre avertissement §l#" + warnId + " §r§a a été retiré par le staff."));
        }

        // Broadcast staff
        try {
            String fmt = ModerationConfig.WARN_REMOVED_BROADCAST_FORMAT.get();
            if (!fmt.isEmpty()) {
                broadcastToStaff(server, fmt.replace("{id}", warnId).replace("{staff}", staffName));
            }
        } catch (IllegalStateException ignored) {}

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Oneria] Warn §e#" + warnId + "§a supprimé (était pour §e" + entry.targetName + "§a)."), false);
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
            ctx.getSource().sendSuccess(() -> Component.literal("§7[Oneria] Aucun warn enregistré."), false);
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
        Optional<WarnManager.WarnEntry> opt = WarnManager.getWarnById(warnId);

        if (opt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Warn introuvable : §e#" + warnId));
            return 0;
        }

        WarnManager.WarnEntry w = opt.get();
        String typeStr = w.isPermanent() ? "§cPermanent" : (w.isExpired() ? "§8Expiré" : "§eTemporaire");

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6╔═ Warn #" + w.id + " ═══════════════════════╗\n" +
                        "§6║ §7Joueur    : §e" + w.targetName + " §8(" + w.targetUUID + ")\n" +
                        "§6║ §7Staff     : §e" + w.issuerName + "\n" +
                        "§6║ §7Raison    : §f" + w.reason + "\n" +
                        "§6║ §7Date      : §f" + w.getFormattedDate() + "\n" +
                        "§6║ §7Type      : " + typeStr + "\n" +
                        "§6║ §7Expiration: §f" + w.getFormattedExpiry() + "\n" +
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
                    "§7[Oneria] §e" + target.getName().getString() + " §7n'avait aucun warn."), false);
            return 1;
        }

        target.sendSystemMessage(Component.literal(
                "§a✔ Tous vos avertissements ont été effacés par le staff."));

        int finalRemoved = removed;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Oneria] §e" + finalRemoved + " §awarn(s) supprimé(s) pour §e" + target.getName().getString() + "§a."), false);
        return 1;
    }

    private static int warnPurge(CommandContext<CommandSourceStack> ctx) {
        if (!warnSystemCheck(ctx)) return 0;

        int purged = WarnManager.purgeExpiredWarns();
        int finalPurged = purged;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Oneria] Purge terminée : §e" + finalPurged + " §awarn(s) expiré(s) supprimé(s)."), false);
        return 1;
    }

    /** /mywarn — joueur voit ses propres warns actifs */
    private static int myWarn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§c[Oneria] Cette commande ne peut être utilisée que par un joueur."));
            return 0;
        }

        try {
            if (!ModerationConfig.ENABLE_WARN_SYSTEM.get()) {
                player.sendSystemMessage(Component.literal("§c[Oneria] Le système de warns est désactivé."));
                return 0;
            }
        } catch (IllegalStateException e) {
            return 0;
        }

        return displayWarnList(ctx, player.getUUID(), player.getName().getString(), false);
    }

    /**
     * Affiche la liste des warns d'un joueur.
     * @param showAll si true, montre aussi les expirés ; si false, uniquement les actifs.
     */
    private static int displayWarnList(CommandContext<CommandSourceStack> ctx,
                                       UUID uuid, String name, boolean showAll) {
        List<WarnManager.WarnEntry> list = showAll
                ? WarnManager.getWarns(uuid)
                : WarnManager.getActiveWarns(uuid);

        if (list.isEmpty()) {
            String msg = showAll
                    ? "§7[Oneria] §e" + name + " §7n'a aucun warn."
                    : "§a✔ Vous n'avez aucun avertissement actif.";
            ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        }

        long activeCount = list.stream().filter(w -> !w.isExpired()).count();
        StringBuilder sb = new StringBuilder();
        sb.append("§6╔═ Avertissements de §e").append(name).append(" §6(").append(activeCount).append(" actif(s)) ═╗\n");

        for (WarnManager.WarnEntry w : list) {
            String status;
            if (w.isExpired())      status = "§8[EXP]";
            else if (w.isPermanent()) status = "§c[PERM]";
            else                     status = "§e[TEMP]";

            sb.append("§6║ ").append(status)
                    .append(" §7#").append(w.id)
                    .append(" §8(").append(w.getFormattedDate()).append(")")
                    .append(" §7par §f").append(w.issuerName).append("\n")
                    .append("§6║   §7→ §f").append(w.reason)
                    .append(" §8| ").append(w.getFormattedExpiry()).append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");

        String finalMsg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
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