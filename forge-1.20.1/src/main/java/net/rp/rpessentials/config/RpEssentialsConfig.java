package net.rp.rpessentials.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

public class RpEssentialsConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // === OBFUSCATION ===
    public static final ForgeConfigSpec.IntValue PROXIMITY_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLUR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALWAYS_VISIBLE_LIST;
    public static final ForgeConfigSpec.BooleanValue BLUR_SPECTATORS;
    public static final ForgeConfigSpec.BooleanValue WHITELIST_EXEMPT_PROFESSIONS;
    public static final ForgeConfigSpec.IntValue OBFUSCATED_NAME_LENGTH;
    public static final ForgeConfigSpec.BooleanValue OBFUSCATE_PREFIX;
    public static final ForgeConfigSpec.ConfigValue<String> BLUR_STYLE;
    public static final ForgeConfigSpec.ConfigValue<String> BLUR_CODES;
    public static final ForgeConfigSpec.ConfigValue<String> NAMETAG_BLUR_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SNEAK_STEALTH;
    public static final ForgeConfigSpec.IntValue SNEAK_PROXIMITY_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue OPS_SEE_ALL;
    public static final ForgeConfigSpec.BooleanValue DEBUG_SELF_BLUR;
    public static final ForgeConfigSpec.BooleanValue HIDE_NAMETAGS;
    public static final ForgeConfigSpec.BooleanValue SHOW_NAMETAG_PREFIX_SUFFIX;

    // === ROLES ===
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> ROLES;
    public static final ForgeConfigSpec.BooleanValue LP_SYNC_ENABLED;
    public static final ForgeConfigSpec.BooleanValue LP_SYNC_IMPORT_ALL_GROUPS;

    // === PERMISSIONS ===
    public static final ForgeConfigSpec.IntValue OP_LEVEL_BYPASS;
    public static final ForgeConfigSpec.BooleanValue USE_LUCKPERMS_GROUPS;

    // === WORLD BORDER & ZONES ===
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORLD_BORDER_WARNING;
    public static final ForgeConfigSpec.IntValue WORLD_BORDER_DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<String> WORLD_BORDER_SHAPE;
    public static final ForgeConfigSpec.BooleanValue WORLD_BORDER_TELEPORT_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WORLD_BORDER_DIMENSION_OVERRIDES;
    public static final ForgeConfigSpec.ConfigValue<String> WORLD_BORDER_PREWARN_MESSAGE;
    public static final ForgeConfigSpec.IntValue WORLD_BORDER_PREWARN_DISTANCE;
    public static final ForgeConfigSpec.ConfigValue<String> WORLD_BORDER_MESSAGE;
    public static final ForgeConfigSpec.IntValue WORLD_BORDER_CHECK_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAMED_ZONES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ZONE_ACTIONS;
    public static final ForgeConfigSpec.ConfigValue<String> ZONE_MESSAGE_MODE;

    // === DEATH RP ===
    public static ForgeConfigSpec.BooleanValue DEATH_RP_GLOBAL_ENABLED;
    public static ForgeConfigSpec.BooleanValue DEATH_RP_WHITELIST_REMOVE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_DEATH_MESSAGE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_DEATH_SOUND;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_VOLUME;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_PITCH;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MSG;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MODE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MSG;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MODE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_TOGGLE_SOUND;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH;
    public static ForgeConfigSpec.BooleanValue        DEATH_RP_RESET_NICKNAME;
    public static ForgeConfigSpec.BooleanValue        DEATH_RP_RESET_ROLE;
    public static ForgeConfigSpec.BooleanValue        DEATH_RP_RESET_PROFESSIONS;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MSG;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MODE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MSG;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MODE;
    public static ForgeConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_TOGGLE_SOUND;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME;
    public static ForgeConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH;

    // === BACKUP ===
    public static final ForgeConfigSpec.BooleanValue BACKUP_ENABLED;
    public static final ForgeConfigSpec.IntValue BACKUP_INTERVAL_HOURS;
    public static final ForgeConfigSpec.IntValue BACKUP_KEEP;

    // === COMMANDS ===
    public static final ForgeConfigSpec.BooleanValue UNPREFIXED_STAFF_COMMANDS;
    public static final ForgeConfigSpec.BooleanValue UNPREFIXED_RP_COMMANDS;

    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_URL;
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_USERNAME;
    public static final ForgeConfigSpec.BooleanValue DISCORD_REPORTS;
    public static final ForgeConfigSpec.BooleanValue DISCORD_WARNS;
    public static final ForgeConfigSpec.BooleanValue DISCORD_MUTES;
    public static final ForgeConfigSpec.BooleanValue DISCORD_MANAGE;
    public static final ForgeConfigSpec.BooleanValue DISCORD_DEATH_RP;
    public static final ForgeConfigSpec.BooleanValue DISCORD_LICENSES;
    public static final ForgeConfigSpec.BooleanValue DISCORD_NOTES;
    public static final ForgeConfigSpec.BooleanValue DISCORD_SCHEDULE;

    static {
        // =========================================================================
        // OBFUSCATION
        // =========================================================================
        BUILDER.push("Obfuscation Settings");

        PROXIMITY_DISTANCE = BUILDER
                .comment("The distance (in blocks) required to see another player's name clearly.",
                        "Beyond this distance the name is replaced by '???'. Default: 8 blocks.")
                .defineInRange("proximityDistance", 12, 1, 128);

        ENABLE_BLUR = BUILDER
                .comment("Enables or disables the name blurring system.")
                .define("enableBlur", true);

        OBFUSCATED_NAME_LENGTH = BUILDER
                .comment("Number of characters used to replace the name (e.g. 5 → '?????').")
                .defineInRange("obfuscatedNameLength", 5, 1, 16);

        OBFUSCATE_PREFIX = BUILDER
                .comment("If true, prefixes (ranks, titles) are also hidden in the TabList.")
                .define("obfuscatePrefix", true);

        BLUR_STYLE = BUILDER
                .comment("Look of a hidden name: OBFUSCATED (default, animated), DASHES, HASHES or STARS.")
                .define("blurStyle", "OBFUSCATED");
        ConfigRules.oneOf(BLUR_STYLE, Arrays.asList("OBFUSCATED", "DASHES", "HASHES", "STARS"));

        BLUR_CODES = BUILDER
                .comment("Color and format codes applied to a hidden name, for example &7 or &8&o. Empty = none.")
                .define("blurCodes", "§o§8");

        NAMETAG_BLUR_MODE = BUILDER
                .comment("What happens to the nametag above the head of a player whose name is hidden in the tab list.",
                        "OFF  : nametag unchanged (default)",
                        "BLUR : the nametag shows the hidden name",
                        "HIDE : the nametag is not displayed")
                .define("nametagBlurMode", "OFF");
        ConfigRules.oneOf(NAMETAG_BLUR_MODE, Arrays.asList("OFF", "BLUR", "HIDE"));

        ENABLE_SNEAK_STEALTH = BUILDER
                .comment("If true, sneaking players are only visible at a much closer distance.")
                .define("enableSneakStealth", true);

        SNEAK_PROXIMITY_DISTANCE = BUILDER
                .comment("Distance at which sneaking players can be detected. Default: 2 blocks.")
                .defineInRange("sneakProximityDistance", 1, 1, 32);

        OPS_SEE_ALL = BUILDER
                .comment("If true, operators and staff always see all names clearly.")
                .define("opsSeeAll", true);

        DEBUG_SELF_BLUR = BUILDER
                .comment("DEBUG only — applies blur to yourself. Never leave enabled in production.")
                .define("debugSelfBlur", false);

        HIDE_NAMETAGS = BUILDER
                .comment("If true, hides all player nametags above their heads.",
                        "This config is synced to clients via HideNametagsPacket.")
                .define("hideNametags", false);

        SHOW_NAMETAG_PREFIX_SUFFIX = BUILDER
                .comment("If true, displays LuckPerms prefix/suffix above player heads.")
                .define("showNametagPrefixSuffix", true);

        WHITELIST = BUILDER
                .comment("Players who always see everything clearly and are never obfuscated.")
                .defineListAllowEmpty("whitelist", List.of(), obj -> obj instanceof String);

        BLACKLIST = BUILDER
                .comment("Players who are always obfuscated regardless of proximity.")
                .defineListAllowEmpty("blacklist", List.of(), obj -> obj instanceof String);

        ALWAYS_VISIBLE_LIST = BUILDER
                .comment("Players who are always shown clearly in the TabList.")
                .defineListAllowEmpty("alwaysVisibleList", List.of(), obj -> obj instanceof String);

        BLUR_SPECTATORS = BUILDER
                .comment("If true, spectators are also subject to name blurring.")
                .define("blurSpectators", true);

        WHITELIST_EXEMPT_PROFESSIONS = BUILDER
                .comment("If true, tab-whitelisted players are also exempted from profession restrictions.")
                .define("whitelistExemptProfessions", false);

        BUILDER.pop(); // Obfuscation Settings

        // =========================================================================
        // PERMISSIONS
        // =========================================================================
        BUILDER.push("Permissions System");

        OP_LEVEL_BYPASS = BUILDER
                .comment("Minimum OP level for a player to be considered staff (staff commands).",
                        "It does NOT bypass profession restrictions: use the professionWhitelist role permission for that.",
                        "0 = disabled.")
                .defineInRange("opLevelBypass", 3, 0, 4);

        USE_LUCKPERMS_GROUPS = BUILDER
                .comment("If true, the LuckPerms groups of a player give them the role of the same name (and the",
                        "rpessentials.<permission> nodes count, see luckPermsSync). If false, only the vanilla tags",
                        "(/tag <player> add <role>) give roles. Ignored if LuckPerms is absent.")
                .define("useLuckPermsGroups", true);

        ROLES = BUILDER
                .comment(rolesComment())
                .defineListAllowEmpty("roles",
                        java.util.Arrays.asList(
                                "admin;admin;rpessentials.*",
                                "moderator;moderator;rpessentials.staff,rpessentials.bypass.profession,rpessentials.see.nicknames,"
                                        + "rpessentials.bypass.worldborder,rpessentials.bypass.autounwhitelist,rpessentials.bypass.deathrpwhitelist",
                                "builder;builder;rpessentials.bypass.schedule,rpessentials.bypass.profession,rpessentials.bypass.worldborder,"
                                        + "rpessentials.bypass.autounwhitelist,rpessentials.bypass.deathrpwhitelist",
                                "player;player"
                        ),
                        obj -> obj instanceof String && ((String) obj).contains(";"));

        LP_SYNC_ENABLED = BUILDER
                .comment("Two way sync between the roles and the LuckPerms groups.",
                        "The nodes of each role are mirrored on its LuckPerms group (a refused node as a node set to false).",
                        "Roles created here appear in LuckPerms, and groups holding rpessentials.* nodes appear here as roles.",
                        "rpessentials.* nodes set in LuckPerms on a player or a group are also applied, even when LuckPerms",
                        "is not the permission handler of NeoForge. Ignored if LuckPerms is absent.")
                .define("luckPermsSync", true);

        LP_SYNC_IMPORT_ALL_GROUPS = BUILDER
                .comment("If true, every LuckPerms group becomes a role, even without any rpessentials.* node.",
                        "If false, a group becomes a role as soon as it holds at least one rpessentials.* node.",
                        "When true, deleting a role here has no lasting effect unless the LuckPerms group is deleted too.")
                .define("luckPermsImportAllGroups", false);

        BUILDER.pop(); // Permissions System

        // =========================================================================
        // WORLD BORDER & ZONES
        // =========================================================================
        BUILDER.push("World Border Warning");

        ENABLE_WORLD_BORDER_WARNING = BUILDER
                .comment("Enable warning when players reach the world border distance. Since the 4.2.0, if the value is set to false, the world border will now be completely disabled.")
                .define("enableWorldBorderWarning", true);

        WORLD_BORDER_DISTANCE = BUILDER
                .comment("Distance from spawn (blocks) before warning is triggered.")
                .defineInRange("worldBorderDistance", 5000, 100, 100000);

        WORLD_BORDER_SHAPE = BUILDER
                .comment("Shape of the world border around the spawn point.",
                        "SQUARE : the limit is a square, worldBorderDistance is half of its side (default).",
                        "CIRCLE : the limit is a circle, worldBorderDistance is its radius.")
                .define("worldBorderShape", "SQUARE");
        ConfigRules.oneOf(WORLD_BORDER_SHAPE, Arrays.asList("SQUARE", "CIRCLE"));

        WORLD_BORDER_TELEPORT_ENABLED = BUILDER
                .comment("If true, players reaching the world border are teleported to the opposite side.")
                .define("worldBorderTeleportEnabled", false);

        WORLD_BORDER_DIMENSION_OVERRIDES = BUILDER
                .comment(
                        "Per-dimension world border overrides.",
                        "Format: dimension;distance;teleport;prewarnDistance",
                        "  dimension      : resource location (e.g. minecraft:overworld)",
                        "  distance       : border distance in blocks from spawn (0 = use global value)",
                        "  teleport       : true/false (overrides global worldBorderTeleportEnabled)",
                        "  prewarnDistance: pre-warning distance (0 = use global value)",
                        "Example: minecraft:the_nether;1000;false;800"
                )
                .defineListAllowEmpty("worldBorderDimensionOverrides",
                        List.of(),
                        obj -> obj instanceof String && ((String) obj).contains(";"));

        WORLD_BORDER_PREWARN_DISTANCE = BUILDER
                .comment(
                        "Distance from spawn at which the pre-warning is sent, before the hard border.",
                        "Must be less than worldBorderDistance. Set to 0 to disable."
                )
                .defineInRange("worldBorderPrewarnDistance", 4950, 0, 100000);

        WORLD_BORDER_PREWARN_MESSAGE = BUILDER
                .comment(
                        "Message sent when a player reaches the pre-warning distance.",
                        "Variables: {distance}, {player}, {border}"
                )
                .define("worldBorderPrewarnMessage",
                        "§e⚠ You are approaching the world border ({distance} blocks from center). Limit: {border} blocks.");

        WORLD_BORDER_MESSAGE = BUILDER
                .comment("Message displayed when player reaches border. Variables: {distance}, {player}")
                .define("worldBorderMessage",
                        "§c§l⚠ WARNING §r§7You've reached the limit of the world! (§c{distance} blocks§7)");

        WORLD_BORDER_CHECK_INTERVAL = BUILDER
                .comment("Check interval in ticks (20 = 1 second).")
                .defineInRange("worldBorderCheckInterval", 100, 20, 200);

        NAMED_ZONES = BUILDER
                .comment("Named zones with entry/exit messages.",
                        "Format without dimension: name;centerX;centerZ;radius;messageEnter;messageExit",
                        "Format with dimension:    name;centerX;centerZ;radius;dimension;messageEnter;messageExit",
                        "Example: spawn;0;0;50;minecraft:overworld;Welcome to spawn!;You left spawn.",
                        "The radius field accepts: 50 (circle), square:50, rect:50x30, ellipse:50x30 (half sizes, X then Z).")
                .defineListAllowEmpty("namedZones", Arrays.asList(), obj -> obj instanceof String);

        ZONE_ACTIONS = BUILDER
                .comment("Commands run when a player enters or leaves a named zone.",
                        "Format: zoneName;enter|exit;command",
                        "Placeholder: {player}",
                        "Example: spawn;enter;effect give {player} minecraft:regeneration 10 0 true")
                .defineListAllowEmpty("zoneActions", List.of(),
                        obj -> obj instanceof String && ((String) obj).split(";", 3).length == 3);

        ZONE_MESSAGE_MODE = BUILDER
                .comment("Display mode for zone and world border messages:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("zoneMessageMode", "ACTION_BAR");
        ConfigRules.displayMode(ZONE_MESSAGE_MODE);

        BUILDER.pop(); // World Border Warning

        // =========================================================================
        // DEATH RP
        // =========================================================================
        BUILDER.push("DeathRP");

        DEATH_RP_GLOBAL_ENABLED = BUILDER
                .comment("Global state of the Death RP system.",
                        "When true, all players without an individual override are affected.")
                .define("globalEnabled", false);

        DEATH_RP_WHITELIST_REMOVE = BUILDER
                .comment("Automatically remove the player from the whitelist on RP death.")
                .define("whitelistRemove", false);

        BUILDER.push("deathEvent");

        DEATH_RP_DEATH_MESSAGE = BUILDER
                .comment("Message broadcast to all players on RP death.",
                        "Variables: {player} (nickname), {realname} (MC name). Supports & and § color codes.")
                .define("deathMessage",
                        "&c[Death RP] &f{player} &7({realname}) &chas permanently lost their life.");

        DEATH_RP_DEATH_SOUND = BUILDER
                .comment("Sound played to all players on RP death. Format: namespace:sound_id. 'none' to disable.")
                .define("deathSound", "minecraft:entity.wither.death");

        DEATH_RP_DEATH_SOUND_VOLUME = BUILDER
                .comment("Volume of the death sound (0.0 - 10.0).")
                .defineInRange("deathSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_DEATH_SOUND_PITCH = BUILDER
                .comment("Pitch of the death sound (0.5 - 2.0).")
                .defineInRange("deathSoundPitch", 1.0, 0.5, 2.0);

        DEATH_RP_RESET_NICKNAME = BUILDER
                .comment("Clear the nickname of the player on RP death (new character flow).")
                .define("resetNickname", false);

        DEATH_RP_RESET_ROLE = BUILDER
                .comment("Clear the role of the player on RP death.")
                .define("resetRole", false);

        DEATH_RP_RESET_PROFESSIONS = BUILDER
                .comment("Revoke every profession license of the player on RP death.")
                .define("resetProfessions", false);

        BUILDER.pop(); // deathEvent

        BUILDER.push("playerToggle");

        DEATH_RP_PLAYER_ENABLE_MSG = BUILDER
                .comment("Message sent to the player when their RP death is individually enabled.",
                        "Variables: {player}, {realname}.")
                .define("enableMessage",
                        "&6[Death RP] &fYour RP death has been &aenabled&f. Your next death will be permanent.");

        DEATH_RP_PLAYER_ENABLE_MODE = BUILDER
                .comment("Display mode for the individual enable message:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("enableMessageMode", "CHAT");
        ConfigRules.displayMode(DEATH_RP_PLAYER_ENABLE_MODE);

        DEATH_RP_PLAYER_DISABLE_MSG = BUILDER
                .comment("Message sent to the player when their RP death is individually disabled.",
                        "Variables: {player}, {realname}.")
                .define("disableMessage",
                        "&6[Death RP] &fYour RP death has been &cdisabled&f.");

        DEATH_RP_PLAYER_DISABLE_MODE = BUILDER
                .comment("Display mode for the individual disable message:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("disableMessageMode", "CHAT");
        ConfigRules.displayMode(DEATH_RP_PLAYER_DISABLE_MODE);

        DEATH_RP_PLAYER_TOGGLE_SOUND = BUILDER
                .comment("Sound played to the player on individual toggle. 'none' to disable.")
                .define("toggleSound", "minecraft:block.note_block.pling");

        DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME = BUILDER
                .comment("Volume of the individual toggle sound (0.0 - 10.0).")
                .defineInRange("toggleSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH = BUILDER
                .comment("Pitch of the individual toggle sound (0.5 - 2.0).")
                .defineInRange("toggleSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.pop(); // playerToggle

        BUILDER.push("globalToggle");

        DEATH_RP_GLOBAL_ENABLE_MSG = BUILDER
                .comment("Message broadcast when the Death RP system is globally enabled.",
                        "Variables: {staff}.")
                .define("enableMessage",
                        "&6[Death RP] &fThe Death RP system has been &aenabled &fby {staff}.");

        DEATH_RP_GLOBAL_ENABLE_MODE = BUILDER
                .comment("Display mode for the global enable message:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("enableMessageMode", "CHAT");
        ConfigRules.displayMode(DEATH_RP_GLOBAL_ENABLE_MODE);

        DEATH_RP_GLOBAL_DISABLE_MSG = BUILDER
                .comment("Message broadcast when the Death RP system is globally disabled.",
                        "Variables: {staff}.")
                .define("disableMessage",
                        "&6[Death RP] &fThe Death RP system has been &cdisabled &fby {staff}.");

        DEATH_RP_GLOBAL_DISABLE_MODE = BUILDER
                .comment("Display mode for the global disable message:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("disableMessageMode", "CHAT");
        ConfigRules.displayMode(DEATH_RP_GLOBAL_DISABLE_MODE);

        DEATH_RP_GLOBAL_TOGGLE_SOUND = BUILDER
                .comment("Sound played to all on global toggle. 'none' to disable.")
                .define("globalToggleSound", "minecraft:ui.toast.challenge_complete");

        DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME = BUILDER
                .comment("Volume of the global toggle sound (0.0 - 10.0).")
                .defineInRange("globalToggleSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH = BUILDER
                .comment("Pitch of the global toggle sound (0.5 - 2.0).")
                .defineInRange("globalToggleSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.pop(); // globalToggle

        BUILDER.pop(); // DeathRP


        BUILDER.push("Backups");

        BACKUP_ENABLED = BUILDER
                .comment("Zip the data folder (world/data/rpessentials) into world/data/rpessentials-backups.")
                .define("enableBackups", true);

        BACKUP_INTERVAL_HOURS = BUILDER
                .comment("Hours between two automatic backups.")
                .defineInRange("backupIntervalHours", 24, 1, 720);

        BACKUP_KEEP = BUILDER
                .comment("Number of backups kept, the oldest are deleted.")
                .defineInRange("backupKeep", 7, 1, 100);

        BUILDER.pop();

        BUILDER.push("Commands");

        UNPREFIXED_STAFF_COMMANDS = BUILDER
                .comment("If true, every /rpessentials subcommand can also be typed without the prefix",
                        "(/warn, /mute, /nick, /license, /staff, /config...). /rpessentials ... keeps working.",
                        "A name already used by Minecraft or another mod (/help, /list...) is skipped:",
                        "see the server log or /rpessentials config validate.",
                        "Takes effect on config reload, no restart needed.")
                .define("unprefixedStaffCommands", false);

        UNPREFIXED_RP_COMMANDS = BUILDER
                .comment("If true, every /rp subcommand can also be typed without the prefix",
                        "(/afk, /spawn, /commerce, /incognito, /action, /selfnick, /annonce). /rp ... keeps working.",
                        "Same conflict rule as unprefixedStaffCommands.")
                .define("unprefixedRpCommands", false);

        BUILDER.pop();

        BUILDER.push("Discord Webhook");

        DISCORD_WEBHOOK_URL = BUILDER
                .comment("Sends staff events to a Discord channel. Empty = off.",
                        "In Discord: channel settings > Integrations > Webhooks > New Webhook > Copy Webhook URL,",
                        "then paste it here. Keep it secret: anyone with this URL can post in the channel.",
                        "Only staff who can edit sensitive options see it in the Config Manager.",
                        "Test it with /rpessentials discord test.")
                .define("discordWebhookUrl", "");

        DISCORD_USERNAME = BUILDER
                .comment("Name shown as the author of the messages in Discord.")
                .define("discordUsername", "RpEssentials");

        DISCORD_REPORTS = BUILDER
                .comment("Player reports: new report, report closed.")
                .define("discordReports", true);
        DISCORD_WARNS = BUILDER
                .comment("Warns given, removed and cleared.")
                .define("discordWarns", true);
        DISCORD_MUTES = BUILDER
                .comment("Mutes and unmutes.")
                .define("discordMutes", true);
        DISCORD_MANAGE = BUILDER
                .comment("Manage actions (kick, ban, tempban, pardon, whitelist, op, clear inventory, reset...).",
                        "Vanish actions are never sent.")
                .define("discordManage", true);
        DISCORD_DEATH_RP = BUILDER
                .comment("RP deaths.")
                .define("discordDeathRp", true);
        DISCORD_LICENSES = BUILDER
                .comment("Licenses given, revoked and expired.")
                .define("discordLicenses", false);
        DISCORD_NOTES = BUILDER
                .comment("Staff notes added and removed.")
                .define("discordNotes", false);
        DISCORD_SCHEDULE = BUILDER
                .comment("Server opening and closing (schedule).")
                .define("discordSchedule", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /** Comment of the roles option: format, rules, and every node with its default. */
    private static String[] rolesComment() {
        java.util.List<String> lines = new java.util.ArrayList<>(java.util.List.of(
                "Roles and the permission nodes they give. A player has a role when they have the vanilla tag of its id,",
                "or the LuckPerms group of the role (useLuckPermsGroups).",
                "Format: id;luckpermsGroup;node1,node2,...",
                "  rpessentials.moderation.warn    : grants the node",
                "  rpessentials.moderation.*       : grants every node starting with rpessentials.moderation.",
                "  rpessentials.*                  : grants every node",
                "  -rpessentials.manage.ban        : refuses the node (the most precise entry wins, a refusal wins a tie)",
                "Nodes only cover RpEssentials commands: vanilla commands (/kick, /ban, /op...) keep their vanilla OP checks.",
                "With LuckPerms set as permissionHandler in neoforge-server.toml, LuckPerms can also set these nodes.",
                "A node set nowhere uses its default, shown below: staff = given to rpessentials.staff,",
                "OP n = from OP level n, sensitive = the level of opLevelBypass (4 when 0), everyone = every player.",
                ""));
        for (net.rp.rpessentials.permission.RpPerm p : net.rp.rpessentials.permission.RpPerm.all()) {
            String def;
            if (p.opLevel() == net.rp.rpessentials.permission.RpPerm.EVERYONE) def = "everyone";
            else if (p.opLevel() == net.rp.rpessentials.permission.RpPerm.SENSITIVE) def = "sensitive";
            else if (p.opLevel() <= 4) def = "OP " + p.opLevel();
            else def = p == net.rp.rpessentials.permission.RpPerm.STAFF ? "opLevelBypass" : "";
            if (p.staffDefault()) def = def.isEmpty() ? "staff" : "staff, " + def;
            if (def.isEmpty()) def = "nobody";
            lines.add(String.format("  %-38s (default: %s)", p.node(), def));
        }
        return lines.toArray(new String[0]);
    }
}
