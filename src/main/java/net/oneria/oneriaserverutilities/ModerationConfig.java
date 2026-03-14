package net.oneria.oneriaserverutilities;

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

    static {
        // ===============================================================================
        // CATEGORY: SILENT COMMANDS
        // ===============================================================================
        BUILDER.push("Silent Commands");

        ENABLE_SILENT_COMMANDS = BUILDER
                .comment("Enable /oneria staff gm/tp/effect commands.")
                .define("enableSilentCommands", true);

        LOG_TO_STAFF = BUILDER
                .comment("Notify other staff members when a silent command is used.")
                .define("logToStaff", true);

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
                .comment("Enable the /oneria staff platform command.")
                .define("enablePlatforms", true);

        PLATFORMS = BUILDER
                .comment("List of TP platforms.",
                        "Format: id;DisplayName;dimension;x;y;z",
                        "Example: spawn;The Spawn;minecraft:overworld;0;100;0")
                .defineList("platforms", Arrays.asList("platform1;Platform 1;oneria:quartier;7217;18;-1321"), obj -> obj instanceof String);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: LAST CONNECTION
        // ===============================================================================
        BUILDER.push("Last Connection");

        ENABLE_LAST_CONNECTION = BUILDER
                .comment(
                        "Enable tracking of each player's last connection/disconnection.",
                        "Data is stored in: world/data/oneriamod/lastconnection.json",
                        "Commands: /oneria lastconnection <player>  |  /oneria lastconnection list [count]"
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
                        "Warns are stored in: world/data/oneriamod/warns.json",
                        "Staff commands: /oneria warn add|temp|remove|list|info|clear|purge",
                        "Player command:  /mywarn"
                )
                .define("enableWarnSystem", true);

        WARN_NOTIFY_ON_JOIN = BUILDER
                .comment(
                        "If true, players are notified of their active warn count on every login.",
                        "They are also invited to type /mywarn to see the details."
                )
                .define("notifyOnJoin", true);

        WARN_JOIN_MESSAGE = BUILDER
                .comment(
                        "Message sent to the player at login when they have active warns.",
                        "Placeholders: {count} = number of active warns."
                )
                .define("joinMessage",
                        "§c⚠ You have §l{count} active warning(s)§r§c. Type §l/mywarn §r§cto view them.");

        WARN_MAX_TEMP_DAYS = BUILDER
                .comment(
                        "Maximum duration (in days) allowed for a /oneria warn temp.",
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
                        "§6[STAFF][WARN] §e{staff} §7a averti §e{player} §7(warn #{id}) — §f{reason} §7— {expiry}");

        WARN_REMOVED_BROADCAST_FORMAT = BUILDER
                .comment(
                        "Message broadcast to all staff members when a warn is removed.",
                        "Placeholders: {id} {staff}",
                        "Set to '' (empty string) to disable staff broadcast."
                )
                .define("removedBroadcastFormat",
                        "§6[STAFF][WARN] §e{staff} §7a supprimé le warn §e#{id}§7.");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
