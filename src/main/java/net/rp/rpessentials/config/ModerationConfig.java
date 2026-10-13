package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public class ModerationConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // MODERATION (Silent Commands)
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_SILENT_COMMANDS;
    public static final ModConfigSpec.BooleanValue LOG_TO_STAFF;
    public static final ModConfigSpec.BooleanValue LOG_TO_CONSOLE;
    public static final ModConfigSpec.BooleanValue NOTIFY_TARGET;

    // =========================================================================
    // PLATFORMS
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_PLATFORMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PLATFORMS;

    // =========================================================================
    // LAST CONNECTION
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_LAST_CONNECTION;
    public static final ModConfigSpec.BooleanValue LAST_CONNECTION_TRACK_LOGOUT;
    public static final ModConfigSpec.ConfigValue<String> LAST_CONNECTION_DATE_FORMAT;

    // =========================================================================
    // WARN SYSTEM
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_WARN_SYSTEM;
    public static final ModConfigSpec.BooleanValue WARN_NOTIFY_ON_JOIN;
    public static final ModConfigSpec.ConfigValue<String> WARN_JOIN_MESSAGE;
    public static final ModConfigSpec.IntValue WARN_MAX_TEMP_DAYS;
    public static final ModConfigSpec.BooleanValue WARN_AUTO_PURGE_EXPIRED;
    public static final ModConfigSpec.ConfigValue<String> WARN_ADDED_BROADCAST_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> WARN_REMOVED_BROADCAST_FORMAT;

    // =========================================================================
    // NOTE LIMIT
    // =========================================================================
    public static ModConfigSpec.IntValue NOTE_MAX_PER_PLAYER;
    public static ModConfigSpec.IntValue DEATH_HISTORY_MAX_DAYS;

    // =========================================================================
    // AUTO UNWHITELIST SYSTEM
    // =========================================================================
    public static ModConfigSpec.BooleanValue AUTO_UNWHITELIST_ENABLED;
    public static ModConfigSpec.IntValue     AUTO_UNWHITELIST_DAYS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> AUTO_UNWHITELIST_EXTRA_COMMANDS;

    // =========================================================================
    // MUTE SYSTEM
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_MUTE_SYSTEM;
    public static final ModConfigSpec.BooleanValue MUTE_AUTO_FROM_WARNS;
    public static final ModConfigSpec.IntValue MUTE_AUTO_WARN_COUNT;
    public static final ModConfigSpec.IntValue MUTE_AUTO_DURATION_MINUTES;

    // =========================================================================
    // VANISH SYSTEM
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_VANISH;
    public static final ModConfigSpec.IntValue VANISH_DEFAULT_LEVEL;
    public static final ModConfigSpec.BooleanValue VANISH_PERSIST;
    public static final ModConfigSpec.BooleanValue VANISH_NOTIFY_STAFF;
    public static final ModConfigSpec.BooleanValue VANISH_LOGIN_REMINDER;
    public static final ModConfigSpec.BooleanValue VANISH_INVULNERABLE;
    public static final ModConfigSpec.ConfigValue<String> VANISH_TAB_MODE;
    public static final ModConfigSpec.BooleanValue VANISH_TAB_MARKER_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> VANISH_TAB_MARKER;
    public static final ModConfigSpec.BooleanValue VH_WORLD, VH_LIST, VH_SELECTORS, VH_JOIN_VANILLA,
            VH_JOIN_CUSTOM, VH_DEATH, VH_ADVANCEMENTS, VH_NOISE, VH_GAME_EVENTS, VH_CONTAINERS,
            VH_NAMETAG, VH_GUI, VH_PING, VH_MOBS, VH_PICKUP, VH_TRIGGERS;

    private static ModConfigSpec.BooleanValue vh(String key, String comment) {
        return BUILDER.comment(comment).define(key, true);
    }

    static {
        // ===============================================================================
        // CATEGORY: SILENT COMMANDS
        // ===============================================================================
        BUILDER.push("Silent Commands");

        ENABLE_SILENT_COMMANDS = BUILDER
                .comment("Enable /RpEssentials staff gm/tp/effect commands.")
                .define("enableSilentCommands", true);

        LOG_TO_STAFF = BUILDER
                .comment("Notify other staff members when a silent command is used.")
                .define("logToStaff", false);

        LOG_TO_CONSOLE = BUILDER
                .comment("Log silent commands to the server console.")
                .define("logToConsole", true);

        NOTIFY_TARGET = BUILDER
                .comment("If 'true', the target receives a message (useful for debug, otherwise leave false).")
                .define("notifyTarget", false);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: TELEPORTATION PLATFORMS
        // ===============================================================================
        BUILDER.push("Teleportation Platforms");

        ENABLE_PLATFORMS = BUILDER
                .comment("Enable the /RpEssentials staff platform command.")
                .define("enablePlatforms", true);

        PLATFORMS = BUILDER
                .comment("List of TP platforms.",
                        "Format: id;DisplayName;dimension;x;y;z",
                        "Example: spawn;The Spawn;minecraft:overworld;0;100;0")
                .defineList("platforms", Arrays.asList("platform1;Platform 1;minecraft:overworld;0;300;0"), obj -> obj instanceof String);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: LAST CONNECTION
        // ===============================================================================
        BUILDER.push("Last Connection");

        ENABLE_LAST_CONNECTION = BUILDER
                .comment(
                        "Enable tracking of each player's last connection/disconnection.",
                        "Data is stored in: world/data/RpEssentialsmod/lastconnection.json",
                        "Commands: /RpEssentials lastconnection <player>  |  /RpEssentials lastconnection list [count]"
                )
                .define("enableLastConnection", true);

        LAST_CONNECTION_TRACK_LOGOUT = BUILDER
                .comment("If true, also records the last disconnection time of each player.")
                .define("trackLogout", true);

        LAST_CONNECTION_DATE_FORMAT = BUILDER
                .comment(
                        "Date format used when storing and displaying connection times.",
                        "Uses Java SimpleDateFormat syntax.",
                        "Examples: 'dd/MM/yyyy HH:mm:ss'  |  'yyyy-MM-dd HH:mm'  |  'MM/dd/yyyy hh:mm a'"
                )
                .define("dateFormat", "dd/MM/yyyy HH:mm:ss");

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: WARN SYSTEM
        // ===============================================================================
        BUILDER.push("Warn System");

        ENABLE_WARN_SYSTEM = BUILDER
                .comment(
                        "Enable the warn system.",
                        "Warns are stored in: world/data/RpEssentialsmod/warns.json",
                        "Staff commands: /RpEssentials warn add|temp|remove|list|info|clear|purge",
                        "Player command:  /mywarn"
                )
                .define("enableWarnSystem", true);

        WARN_NOTIFY_ON_JOIN = BUILDER
                .comment(
                        "If true, players are notified of their active warn count on every login.",
                        "They are also invited to type /mywarn to see the details."
                )
                .define("notifyOnJoin", false);

        WARN_JOIN_MESSAGE = BUILDER
                .comment(
                        "Message sent to the player at login when they have active warns.",
                        "Placeholders: {count} = number of active warns."
                )
                .define("joinMessage",
                        "§c⚠ You have §l{count} active warning(s)§r§c. Type §l/mywarn §r§cto view them.");

        WARN_MAX_TEMP_DAYS = BUILDER
                .comment(
                        "Maximum duration (in days) allowed for a /RpEssentials warn temp.",
                        "Set to 0 to disable the limit (allow any duration)."
                )
                .defineInRange("maxTempDays", 30, 0, 3650);

        WARN_AUTO_PURGE_EXPIRED = BUILDER
                .comment(
                        "If true, expired temp-warns are automatically removed from the file",
                        "on each server startup and on every player login."
                )
                .define("autoPurgeExpired", true);

        WARN_ADDED_BROADCAST_FORMAT = BUILDER
                .comment(
                        "Message broadcast to all staff members when a warn is added.",
                        "Placeholders: {id} {staff} {player} {reason} {expiry}",
                        "Set to '' (empty string) to disable staff broadcast."
                )
                .define("addedBroadcastFormat",
                        "§6[WARN] §e{staff} §7sent a warn to §e{player} §7(warn #{id})| §f{reason} §7| {expiry}");

        WARN_REMOVED_BROADCAST_FORMAT = BUILDER
                .comment(
                        "Message broadcast to all staff members when a warn is removed.",
                        "Placeholders: {id} {staff}",
                        "Set to '' (empty string) to disable staff broadcast."
                )
                .define("removedBroadcastFormat",
                        "§6[WARN] §e{staff} §7has deleted the warn §e#{id}§7.");

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: NOTE/DEATHRPHISTORY LIMIT
        // ===============================================================================
        BUILDER.push("Staff Notes");
        NOTE_MAX_PER_PLAYER = BUILDER
                .comment("Maximum number of notes per player. 0 = unlimited.")
                .defineInRange("noteMaxPerPlayer", 20, 0, 200);
        BUILDER.pop();

        BUILDER.push("Death RP History");
        DEATH_HISTORY_MAX_DAYS = BUILDER
                .comment("Maximum age of Death RP history entries in days.",
                        "Entries older than this value are purged automatically at 3 AM.",
                        "Set to 0 to disable purging.")
                .defineInRange("deathHistoryMaxDays", 90, 0, 3650);
        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: MUTE SYSTEM
        // ===============================================================================
        BUILDER.push("Mute System");

        ENABLE_MUTE_SYSTEM = BUILDER
                .comment("Enable the mute system.")
                .define("enableMuteSystem", true);

        MUTE_AUTO_FROM_WARNS = BUILDER
                .comment("If true, players are automatically muted when they reach muteAutoWarnCount active warns.")
                .define("muteAutoFromWarns", false);

        MUTE_AUTO_WARN_COUNT = BUILDER
                .comment("Number of active warns that triggers an automatic mute.")
                .defineInRange("muteAutoWarnCount", 3, 1, 100);

        MUTE_AUTO_DURATION_MINUTES = BUILDER
                .comment("Duration of the automatic mute in minutes (0 = permanent).")
                .defineInRange("muteAutoDurationMinutes", 60, 0, 43200);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: AUTO UNWHITELIST SYSTEM
        // ===============================================================================
        BUILDER.push("Auto Unwhitelist");

        AUTO_UNWHITELIST_ENABLED = BUILDER
                .comment("Auto-remove from whitelist players inactive for more than autoUnwhitelistDays days.",
                        "Requires enableLastConnection = true. Runs once per day at midnight.")
                .define("autoUnwhitelistEnabled", false);

        AUTO_UNWHITELIST_DAYS = BUILDER
                .comment("Days of inactivity before auto-unwhitelist.")
                .defineInRange("autoUnwhitelistDays", 30, 1, 3650);

        AUTO_UNWHITELIST_EXTRA_COMMANDS = BUILDER
                .comment("Extra commands run for each removed player. Placeholders: {player} {uuid}")
                .defineList("autoUnwhitelistExtraCommands",
                        java.util.Collections.emptyList(), obj -> obj instanceof String);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: VANISH SYSTEM
        // ===============================================================================
        BUILDER.push("Vanish");

        ENABLE_VANISH = BUILDER
                .comment("Master switch of the vanish system. When false, /vanish is refused and nobody is hidden.")
                .define("enableVanish", true);

        VANISH_DEFAULT_LEVEL = BUILDER
                .comment("OP level used when /vanish is run with a player but without a level.",
                        "A vanished player is hidden from everyone whose OP level is lower than or equal to this level.")
                .defineInRange("defaultLevel", 4, 0, 4);

        VANISH_PERSIST = BUILDER
                .comment("If true, vanished players stay vanished after a restart (vanish.json).")
                .define("persist", true);

        VANISH_NOTIFY_STAFF = BUILDER
                .comment("If true, staff who are allowed to see a vanished player are told when they join or leave silently.")
                .define("notifyStaffOnSilentJoinLeave", true);

        VANISH_LOGIN_REMINDER = BUILDER
                .comment("If true, a vanished player is reminded of their state when they log in.")
                .define("loginReminder", true);

        VANISH_INVULNERABLE = BUILDER
                .comment("If true, vanished players cannot take damage.")
                .define("invulnerable", false);

        BUILDER.push("Tab List");

        VANISH_TAB_MODE = BUILDER
                .comment("What viewers who cannot see a vanished player get in the tab list.",
                        "REMOVE    : the entry is removed.",
                        "OBFUSCATE : the entry stays with an obfuscated name and no head.",
                        "KEEP      : the entry is untouched (the player stays listed).")
                .defineInList("mode", "REMOVE",
                        Arrays.asList("REMOVE", "OBFUSCATE", "KEEP"));

        VANISH_TAB_MARKER_ENABLED = BUILDER
                .comment("If true, viewers who can see a vanished player (and the player themselves) get a marker before the name.")
                .define("markerEnabled", true);

        VANISH_TAB_MARKER = BUILDER
                .comment("Marker placed before the name of a vanished player in the tab list. Supports & and § color codes.")
                .define("marker", "&7[V] ");

        BUILDER.pop();

        BUILDER.push("Hidden Aspects");

        VH_WORLD = vh("hideFromWorld", "Hide the player entity in the world (no model, no particles, no equipment).");
        VH_LIST = vh("hideFromListCommand", "Hide the player from /list and from online counters.");
        VH_SELECTORS = vh("hideFromSelectors",
                "Hide the player from every command: selectors (@a, @p, @r, @e, names, UUIDs) and name suggestions. " +
                        "This covers /tp, /msg, /gamemode, /effect, /execute and every other command taking a player.");
        VH_JOIN_VANILLA = vh("hideVanillaJoinLeave", "Suppress the vanilla join and leave messages, even if enableCustomJoinLeave is false.");
        VH_JOIN_CUSTOM = vh("hideCustomJoinLeave", "Suppress the custom join and leave messages of this mod.");
        VH_DEATH = vh("hideDeathMessages", "Suppress the vanilla death message.");
        VH_ADVANCEMENTS = vh("hideAdvancements", "Suppress the advancement announcements in chat.");
        VH_NOISE = vh("hideSounds", "Hide the sounds and block break particles caused by the player (steps, blocks, hits).");
        VH_GAME_EVENTS = vh("hideVibrations", "Prevent the player from triggering sculk sensors, wardens and other vibrations.");
        VH_CONTAINERS = vh("silentContainers", "Opening chests, barrels and ender chests makes no sound and plays no lid animation.");
        VH_NAMETAG = vh("hideNametags", "Stop sending the nametag data of the player to viewers who cannot see them.");
        VH_GUI = vh("hideFromProfileGui", "Hide the player from the Player Profile GUI.");
        VH_PING = vh("hideFromServerList", "Remove the player from the player count and the sample of the server list ping.");
        VH_MOBS = vh("mobsIgnore", "Hostile mobs never target the player.");
        VH_PICKUP = vh("blockItemPickup", "The player cannot pick up items (avoids visible item pickups).");
        VH_TRIGGERS = vh("ignorePhysicalTriggers", "The player does not trigger pressure plates and tripwires.");

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
