package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public class RpEssentialsConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // === OBFUSCATION ===
    public static final ModConfigSpec.IntValue PROXIMITY_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_BLUR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_VISIBLE_LIST;
    public static final ModConfigSpec.BooleanValue BLUR_SPECTATORS;
    public static final ModConfigSpec.BooleanValue WHITELIST_EXEMPT_PROFESSIONS;
    public static final ModConfigSpec.IntValue OBFUSCATED_NAME_LENGTH;
    public static final ModConfigSpec.BooleanValue OBFUSCATE_PREFIX;
    public static final ModConfigSpec.BooleanValue ENABLE_SNEAK_STEALTH;
    public static final ModConfigSpec.IntValue SNEAK_PROXIMITY_DISTANCE;
    public static final ModConfigSpec.BooleanValue OPS_SEE_ALL;
    public static final ModConfigSpec.BooleanValue DEBUG_SELF_BLUR;
    public static final ModConfigSpec.BooleanValue HIDE_NAMETAGS;
    public static final ModConfigSpec.BooleanValue SHOW_NAMETAG_PREFIX_SUFFIX;

    // === PERMISSIONS ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAFF_TAGS;
    public static final ModConfigSpec.IntValue OP_LEVEL_BYPASS;
    public static final ModConfigSpec.BooleanValue USE_LUCKPERMS_GROUPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LUCKPERMS_STAFF_GROUPS;

    // === WORLD BORDER & ZONES ===
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_BORDER_WARNING;
    public static final ModConfigSpec.IntValue WORLD_BORDER_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> WORLD_BORDER_MESSAGE;
    public static final ModConfigSpec.IntValue WORLD_BORDER_CHECK_INTERVAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMED_ZONES;
    public static final ModConfigSpec.ConfigValue<String> ZONE_MESSAGE_MODE;

    // === DEATH RP ===
    public static ModConfigSpec.BooleanValue DEATH_RP_GLOBAL_ENABLED;
    public static ModConfigSpec.BooleanValue DEATH_RP_WHITELIST_REMOVE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_DEATH_MESSAGE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_DEATH_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_PITCH;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MODE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MODE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_TOGGLE_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MODE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MODE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_TOGGLE_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH;

    // === NAMETAG ADVANCED SYSTEM ===
    public static ModConfigSpec.BooleanValue NAMETAG_ADVANCED_ENABLED;
    public static ModConfigSpec.ConfigValue<String> NAMETAG_FORMAT;
    public static ModConfigSpec.IntValue NAMETAG_OBFUSCATION_DISTANCE;
    public static ModConfigSpec.IntValue NAMETAG_RENDER_DISTANCE;
    public static ModConfigSpec.BooleanValue NAMETAG_HIDE_BEHIND_BLOCKS;
    public static ModConfigSpec.BooleanValue NAMETAG_SHOW_WHILE_SNEAKING;
    public static ModConfigSpec.BooleanValue NAMETAG_STAFF_ALWAYS_SEE_REAL;
    public static ModConfigSpec.BooleanValue NAMETAG_OBFUSCATION_ENABLED;

    // === ROLES ===
    public static ModConfigSpec.ConfigValue<List<? extends String>> ROLES;

    static {
        // =========================================================================
        // OBFUSCATION
        // =========================================================================
        BUILDER.push("Obfuscation Settings");

        PROXIMITY_DISTANCE = BUILDER
                .comment("The distance (in blocks) required to see another player's name clearly.",
                        "Beyond this distance the name is replaced by '???'. Default: 8 blocks.")
                .defineInRange("proximityDistance", 8, 1, 128);

        ENABLE_BLUR = BUILDER
                .comment("Enables or disables the name blurring system.")
                .define("enableBlur", true);

        OBFUSCATED_NAME_LENGTH = BUILDER
                .comment("Number of characters used to replace the name (e.g. 5 → '?????').")
                .defineInRange("obfuscatedNameLength", 5, 1, 16);

        OBFUSCATE_PREFIX = BUILDER
                .comment("If true, prefixes (ranks, titles) are also hidden in the TabList.")
                .define("obfuscatePrefix", true);

        ENABLE_SNEAK_STEALTH = BUILDER
                .comment("If true, sneaking players are only visible at a much closer distance.")
                .define("enableSneakStealth", true);

        SNEAK_PROXIMITY_DISTANCE = BUILDER
                .comment("Distance at which sneaking players can be detected. Default: 2 blocks.")
                .defineInRange("sneakProximityDistance", 2, 1, 32);

        OPS_SEE_ALL = BUILDER
                .comment("If true, operators and staff always see all names clearly.")
                .define("opsSeeAll", true);

        DEBUG_SELF_BLUR = BUILDER
                .comment("DEBUG only — applies blur to yourself. Never leave enabled in production.")
                .define("debugSelfBlur", false);

        HIDE_NAMETAGS = BUILDER
                .comment("If true, hides all player nametags above their heads.")
                .define("hideNametags", false);

        SHOW_NAMETAG_PREFIX_SUFFIX = BUILDER
                .comment("If true, displays LuckPerms prefix/suffix above player heads.")
                .define("showNametagPrefixSuffix", true);

        WHITELIST = BUILDER
                .comment("Players who always see everything clearly and are never obfuscated.")
                .defineList("whitelist", List.of(), obj -> obj instanceof String);

        BLACKLIST = BUILDER
                .comment("Players who are always obfuscated regardless of proximity.")
                .defineList("blacklist", List.of(), obj -> obj instanceof String);

        ALWAYS_VISIBLE_LIST = BUILDER
                .comment("Players who are always shown clearly in the TabList.")
                .defineList("alwaysVisibleList", List.of(), obj -> obj instanceof String);

        BLUR_SPECTATORS = BUILDER
                .comment("If true, spectators are also subject to name blurring.")
                .define("blurSpectators", false);

        WHITELIST_EXEMPT_PROFESSIONS = BUILDER
                .comment("If true, whitelisted players are also exempt from profession restrictions.")
                .define("whitelistExemptProfessions", false);

        BUILDER.pop(); // Obfuscation Settings

        // =========================================================================
        // PERMISSIONS
        // =========================================================================
        BUILDER.push("Permissions System");

        STAFF_TAGS = BUILDER
                .comment("LuckPerms tags/groups considered as staff.")
                .defineList("staffTags",
                        Arrays.asList("admin", "moderateur", "modo", "staff", "builder"),
                        obj -> obj instanceof String);

        OP_LEVEL_BYPASS = BUILDER
                .comment("Minimum OP level to bypass all restrictions. 0 = disabled.")
                .defineInRange("opLevelBypass", 2, 0, 4);

        USE_LUCKPERMS_GROUPS = BUILDER
                .comment("If true, uses LuckPerms groups to determine staff status.")
                .define("useLuckPermsGroups", true);

        LUCKPERMS_STAFF_GROUPS = BUILDER
                .comment("LuckPerms groups considered as staff. Only used if useLuckPermsGroups = true.")
                .defineList("luckPermsStaffGroups",
                        Arrays.asList("admin", "moderateur", "staff"),
                        obj -> obj instanceof String);

        BUILDER.pop(); // Permissions System

        // =========================================================================
        // WORLD BORDER & ZONES
        // =========================================================================
        BUILDER.push("World Border Warning");

        ENABLE_WORLD_BORDER_WARNING = BUILDER
                .comment("Enable warning when players reach the world border distance.")
                .define("enableWorldBorderWarning", true);

        WORLD_BORDER_DISTANCE = BUILDER
                .comment("Distance from spawn (blocks) before warning is triggered.")
                .defineInRange("worldBorderDistance", 2000, 100, 100000);

        WORLD_BORDER_MESSAGE = BUILDER
                .comment("Message displayed when player reaches border. Variables: {distance}, {player}")
                .define("worldBorderMessage",
                        "§c§l⚠ WARNING §r§7You've reached the limit of the world! (§c{distance} blocks§7)");

        WORLD_BORDER_CHECK_INTERVAL = BUILDER
                .comment("Check interval in ticks (20 = 1 second).")
                .defineInRange("worldBorderCheckInterval", 40, 20, 200);

        NAMED_ZONES = BUILDER
                .comment("Named zones with entry/exit messages.",
                        "Format: name;centerX;centerZ;radius;messageEnter;messageExit")
                .defineList("namedZones", Arrays.asList(), obj -> obj instanceof String);

        ZONE_MESSAGE_MODE = BUILDER
                .comment("Display mode for zone/border messages: IMMERSIVE, CHAT, ACTION_BAR.")
                .define("zoneMessageMode", "ACTION_BAR");

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

        BUILDER.pop(); // deathEvent

        BUILDER.push("playerToggle");

        DEATH_RP_PLAYER_ENABLE_MSG = BUILDER
                .comment("Message sent to the player when their RP death is individually enabled.",
                        "Variables: {player}, {realname}.")
                .define("enableMessage",
                        "&6[Death RP] &fYour RP death has been &aenabled&f. Your next death will be permanent.");

        DEATH_RP_PLAYER_ENABLE_MODE = BUILDER
                .comment("Display mode for individual enable message: CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("enableMessageMode", "CHAT");

        DEATH_RP_PLAYER_DISABLE_MSG = BUILDER
                .comment("Message sent to the player when their RP death is individually disabled.",
                        "Variables: {player}, {realname}.")
                .define("disableMessage",
                        "&6[Death RP] &fYour RP death has been &cdisabled&f.");

        DEATH_RP_PLAYER_DISABLE_MODE = BUILDER
                .comment("Display mode for individual disable message: CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("disableMessageMode", "CHAT");

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
                .comment("Display mode for global enable message: CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("enableMessageMode", "CHAT");

        DEATH_RP_GLOBAL_DISABLE_MSG = BUILDER
                .comment("Message broadcast when the Death RP system is globally disabled.",
                        "Variables: {staff}.")
                .define("disableMessage",
                        "&6[Death RP] &fThe Death RP system has been &cdisabled &fby {staff}.");

        DEATH_RP_GLOBAL_DISABLE_MODE = BUILDER
                .comment("Display mode for global disable message: CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("disableMessageMode", "CHAT");

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

        // =========================================================================
        // NAMETAG ADVANCED SYSTEM
        // =========================================================================
        BUILDER.push("Nametag Settings");

        NAMETAG_ADVANCED_ENABLED = BUILDER
                .comment(
                        "CONFIGURATION: Advanced Nametag System",
                        "If 'true', enables the full nametag system with proximity obfuscation,",
                        "block occlusion, render distance and configurable format.",
                        "If 'false', falls back to the legacy hideNametags behaviour.")
                .define("nametagAdvancedEnabled", false);

        NAMETAG_FORMAT = BUILDER
                .comment(
                        "CONFIGURATION: Nametag Format",
                        "Variables: $prefix (LuckPerms prefix), $name (nickname or real name),",
                        "           $realname (always the real MC name).",
                        "Color codes & and § are supported.",
                        "Examples: '$prefix$name'  |  '$prefix $name'  |  '&7[$prefix&7] $name'")
                .define("nametagFormat", "$prefix$name");

        NAMETAG_OBFUSCATION_DISTANCE = BUILDER
                .comment(
                        "CONFIGURATION: Nametag Obfuscation Distance",
                        "Distance (in blocks) below which the nametag is shown clearly.",
                        "Above this distance the name is replaced by obfuscated characters (§k...).",
                        "Same logic as the TabList proximityDistance.")
                .defineInRange("nametagObfuscationDistance", 8, 1, 128);

        NAMETAG_RENDER_DISTANCE = BUILDER
                .comment(
                        "CONFIGURATION: Nametag Render Distance",
                        "Maximum distance (in blocks) at which a nametag is displayed at all.",
                        "Set to 0 for unlimited. Beyond this, the nametag is simply hidden.")
                .defineInRange("nametagRenderDistance", 64, 0, 256);

        NAMETAG_HIDE_BEHIND_BLOCKS = BUILDER
                .comment(
                        "CONFIGURATION: Hide Nametag Behind Blocks",
                        "If 'true', a raycast is performed from the camera to the player's eye.",
                        "The nametag is hidden if any block is in the way (no through-wall names).")
                .define("nametagHideBehindBlocks", true);

        NAMETAG_SHOW_WHILE_SNEAKING = BUILDER
                .comment(
                        "CONFIGURATION: Show Nametag While Sneaking",
                        "If 'false', sneaking players do not show their nametag at all.",
                        "If 'true', sneaking players still show their (possibly obfuscated) nametag.")
                .define("nametagShowWhileSneaking", false);

        NAMETAG_STAFF_ALWAYS_SEE_REAL = BUILDER
                .comment(
                        "CONFIGURATION: Staff Always See Real Nametags",
                        "If 'true', staff members bypass all obfuscation and always see",
                        "the formatted nametag with the real name, regardless of distance.")
                .define("nametagStaffAlwaysSeeReal", true);

        NAMETAG_OBFUSCATION_ENABLED = BUILDER
                .comment(
                        "CONFIGURATION: Enable Nametag Obfuscation",
                        "If 'false', nametags are always shown clearly (no §k obfuscation).",
                        "Other filters (distance, block occlusion, sneak) still apply.")
                .define("nametagObfuscationEnabled", true);

        BUILDER.pop(); // Nametag Settings

        // =========================================================================
        // ROLES
        // =========================================================================
        BUILDER.push("Roles");

        ROLES = BUILDER
                .comment("Configurable roles for /rpessentials setrole.",
                        "Format: roleId;lpGroup",
                        "Example: [\"admin;admin\", \"modo;modo\", \"builder;builder\", \"joueur;joueur\"]",
                        "The vanilla tag added = roleId. The LuckPerms parent set = lpGroup.",
                        "All other role tags are removed automatically when a new role is set.")
                .defineList("roles",
                        java.util.Arrays.asList("admin;admin", "modo;modo", "builder;builder", "joueur;joueur"),
                        obj -> obj instanceof String && ((String) obj).contains(";"));

        BUILDER.pop(); // Roles

        SPEC = BUILDER.build();
    }
}