package net.oneria.oneriaserverutilities;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration file for all user-facing messages in the Oneria mod.
 * Allows server admins to customize or translate every message without recompiling.
 *
 * File: config/oneria/oneria-messages.toml
 * Reload: /oneria config reload
 *
 * Supports § and & color codes in all message values.
 */
public class MessagesConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // CATEGORY: SYSTEM
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> SYSTEM_CONFIG_NOT_LOADED;
    public static final ModConfigSpec.ConfigValue<String> SYSTEM_CONFIG_UNAVAILABLE;
    public static final ModConfigSpec.ConfigValue<String> SYSTEM_CONFIG_NOT_BUILT;
    public static final ModConfigSpec.ConfigValue<String> SYSTEM_CONFIG_UPDATED;
    public static final ModConfigSpec.ConfigValue<String> COMMAND_PLAYER_ONLY;

    // =========================================================================
    // CATEGORY: PRIVATE MESSAGING
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> MP_HOVER_REPLY;
    public static final ModConfigSpec.ConfigValue<String> MP_TO_SENDER;
    public static final ModConfigSpec.ConfigValue<String> MP_FROM_TARGET;
    public static final ModConfigSpec.ConfigValue<String> MP_NO_ONE_TO_REPLY;
    public static final ModConfigSpec.ConfigValue<String> MP_TARGET_OFFLINE;
    public static final ModConfigSpec.ConfigValue<String> MP_CONSOLE_TO_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> MP_CONSOLE_FROM_SERVER;

    // =========================================================================
    // CATEGORY: WARN SYSTEM
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> WARN_RECEIVED_PERM;
    public static final ModConfigSpec.ConfigValue<String> WARN_RECEIVED_TEMP;
    public static final ModConfigSpec.ConfigValue<String> WARN_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> WARN_REMOVE_FAILED;
    public static final ModConfigSpec.ConfigValue<String> WARN_REMOVED_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> WARN_CLEARED_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> WARN_SYSTEM_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> WARN_SYSTEM_DISABLED_CONFIG;
    public static final ModConfigSpec.ConfigValue<String> WARN_LIST_HEADER;
    public static final ModConfigSpec.ConfigValue<String> WARN_LIST_NONE;
    public static final ModConfigSpec.ConfigValue<String> WARN_LIST_NONE_SELF;
    public static final ModConfigSpec.ConfigValue<String> WARN_LIST_NONE_STAFF;
    public static final ModConfigSpec.ConfigValue<String> WARN_PURGE_DONE;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_HEADER;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_PLAYER_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_STAFF_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_REASON_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_DATE_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_TYPE_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_INFO_EXPIRY_LABEL;
    public static final ModConfigSpec.ConfigValue<String> WARN_TYPE_PERMANENT;
    public static final ModConfigSpec.ConfigValue<String> WARN_TYPE_EXPIRED;
    public static final ModConfigSpec.ConfigValue<String> WARN_TYPE_TEMPORARY;
    public static final ModConfigSpec.ConfigValue<String> WARN_STATUS_PERM_TAG;
    public static final ModConfigSpec.ConfigValue<String> WARN_STATUS_TEMP_TAG;
    public static final ModConfigSpec.ConfigValue<String> WARN_STATUS_EXPIRED_TAG;
    public static final ModConfigSpec.ConfigValue<String> WARN_MAX_DURATION_EXCEEDED;
    public static final ModConfigSpec.ConfigValue<String> WARN_DURATION_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> WARN_DURATION_HOURS;
    public static final ModConfigSpec.ConfigValue<String> WARN_DURATION_DAYS;

    // =========================================================================
    // CATEGORY: LAST CONNECTION
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_PLAYER_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_NO_DATA;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_NO_DATA_LIST;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_ONLINE;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_OFFLINE;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_UNKNOWN;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_BOX_HEADER;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_BOX_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_BOX_STATUS;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_BOX_LOGIN;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_BOX_LOGOUT;
    public static final ModConfigSpec.ConfigValue<String> LASTCONN_LIST_HEADER;

    // =========================================================================
    // CATEGORY: DEATHRP
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_CONFIG_UNAVAILABLE;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_GLOBAL_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_GLOBAL_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_PLAYER_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_PLAYER_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_OVERRIDE_RESET;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_GLOBAL;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_WHITELIST;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_NO_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_ACTIVE;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_INACTIVE;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_YES;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_NO;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_STATUS_UNKNOWN;

    // =========================================================================
    // CATEGORY: WHOIS
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> WHOIS_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> WHOIS_RESULTS_HEADER;

    // =========================================================================
    // CATEGORY: PLAYER LIST
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> PLAYERLIST_HEADER;

    // =========================================================================
    // CATEGORY: HELP
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> HELP_TITLE;
    public static final ModConfigSpec.ConfigValue<String> HELP_CMD_LIST;
    public static final ModConfigSpec.ConfigValue<String> HELP_CMD_SCHEDULE;
    public static final ModConfigSpec.ConfigValue<String> HELP_CMD_MSG;
    public static final ModConfigSpec.ConfigValue<String> HELP_CMD_REPLY;
    public static final ModConfigSpec.ConfigValue<String> HELP_STAFF_SECTION;
    public static final ModConfigSpec.ConfigValue<String> HELP_DEATHRP_ENABLE;
    public static final ModConfigSpec.ConfigValue<String> HELP_DEATHRP_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> HELP_DEATHRP_RESET;
    public static final ModConfigSpec.ConfigValue<String> HELP_DEATHRP_STATUS;

    // =========================================================================
    // CATEGORY: PROFESSION RESTRICTIONS (fallbacks)
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_CRAFT_BLOCKED_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_BREAK_BLOCKED_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_USE_BLOCKED_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_EQUIP_BLOCKED_FALLBACK;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_NONE_AVAILABLE;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_SYSTEM_NOT_INIT;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_HAS_LICENSE;
    public static final ModConfigSpec.ConfigValue<String> PROFESSION_NO_LICENSE;

    // =========================================================================
    // CATEGORY: LICENSES
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> LICENSE_ITEM_NAME;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LORE_ISSUED_TO;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LORE_DATE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LORE_ISSUED_DATE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LORE_VALID_UNTIL;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_TOOLTIP_OFFICIAL;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_TOOLTIP_NONTRANSFERABLE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REVOKED_TITLE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REVOKED_BODY;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_UNKNOWN_PROFESSION;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_GIVE_STAFF;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_GIVE_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_GIVE_RP_STAFF;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_GIVE_RP_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REVOKE_STAFF;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REVOKE_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_NONE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_HEADER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_RP_EXPIRY;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_ALL_NONE;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_ALL_HEADER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_LIST_ALL_NONE_FOR_PLAYER;

    static {
        // =========================================================================
        BUILDER.push("System");

        SYSTEM_CONFIG_NOT_LOADED = BUILDER
                .comment("Message shown when the config is not yet loaded.")
                .define("configNotLoaded", "§c[Oneria] Config not loaded.");

        SYSTEM_CONFIG_UNAVAILABLE = BUILDER
                .comment("Message shown when a specific config value is unavailable.")
                .define("configUnavailable", "§c[Oneria] Config unavailable.");

        SYSTEM_CONFIG_NOT_BUILT = BUILDER
                .comment("Message shown when the config spec is not yet built.")
                .define("configNotBuilt", "§c[Oneria] Config not yet initialized.");

        SYSTEM_CONFIG_UPDATED = BUILDER
                .comment("Message shown when a config value is successfully updated.",
                        "Placeholders: {label} = option name, {value} = new value.")
                .define("configUpdated", "§a[Oneria] §e{label} §aupdated: §f{value}");

        COMMAND_PLAYER_ONLY = BUILDER
                .comment("Message shown when a player-only command is run from console.")
                .define("commandPlayerOnly", "§c[Oneria] This command can only be used by a player.");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Private Messaging");

        MP_HOVER_REPLY = BUILDER
                .comment("Tooltip shown when hovering a player name in a private message.")
                .define("hoverReply", "Click to reply");

        MP_TO_SENDER = BUILDER
                .comment("Prefix shown to the sender of a private message.",
                        "Placeholders: {target} = recipient display name.")
                .define("toSender", "[PM] Writing to {target}: ");

        MP_FROM_TARGET = BUILDER
                .comment("Prefix shown to the recipient of a private message.",
                        "Placeholders: {sender} = sender display name.")
                .define("fromTarget", "[PM] {sender} writes: ");

        MP_NO_ONE_TO_REPLY = BUILDER
                .comment("Message shown when /r is used but there is no one to reply to.")
                .define("noOneToReply", "§c[PM] You have no one to reply to.");

        MP_TARGET_OFFLINE = BUILDER
                .comment("Message shown when /r is used but the last interlocutor has disconnected.")
                .define("targetOffline", "§c[PM] This player is no longer online.");

        MP_CONSOLE_TO_PLAYER = BUILDER
                .comment("Message shown to the target when the console sends a private message.",
                        "Placeholders: {msg} = message content.")
                .define("consoleTo", "§7[PM] §f§lServer§r§7 writes to you: {msg}");

        MP_CONSOLE_FROM_SERVER = BUILDER
                .comment("Feedback shown to the console after sending a private message.",
                        "Placeholders: {target} = target player name, {msg} = message content.")
                .define("consoleFrom", "§7[PM] Writing to §f§l{target}§r§7: {msg}");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Warn System");

        WARN_RECEIVED_PERM = BUILDER
                .comment("Message sent to a player when they receive a permanent warn.",
                        "Placeholders: {id} = warn ID, {reason} = reason.")
                .define("receivedPermanent",
                        "§c⚠ §lYou have received a warning §r§c(warn #{id})!\n" +
                                "§7Reason  : §f{reason}\n" +
                                "§7Duration: §fPermanent\n" +
                                "§7Type §l/mywarn §r§7to view all your warnings.");

        WARN_RECEIVED_TEMP = BUILDER
                .comment("Message sent to a player when they receive a temporary warn.",
                        "Placeholders: {id} = warn ID, {reason} = reason, {duration} = formatted duration.")
                .define("receivedTemporary",
                        "§c⚠ §lYou have received a temporary warning §r§c(warn #{id})!\n" +
                                "§7Reason  : §f{reason}\n" +
                                "§7Duration: §f{duration}\n" +
                                "§7Type §l/mywarn §r§7to view all your warnings.");

        WARN_NOT_FOUND = BUILDER
                .comment("Message shown when a warn ID cannot be found. Placeholder: {id}.")
                .define("notFound", "§c[Oneria] Warn not found: §e#{id}");

        WARN_REMOVE_FAILED = BUILDER
                .comment("Message shown when a warn cannot be removed. Placeholder: {id}.")
                .define("removeFailed", "§c[Oneria] Failed to remove warn §e#{id}");

        WARN_REMOVED_PLAYER = BUILDER
                .comment("Message sent to the warned player when their warn is removed. Placeholder: {id}.")
                .define("removedPlayer", "§a✔ Your warning §l#{id} §r§ahas been removed by staff.");

        WARN_CLEARED_PLAYER = BUILDER
                .comment("Message sent to the player when all their warns are cleared.")
                .define("clearedPlayer", "§a✔ All your warnings have been cleared by staff.");

        WARN_SYSTEM_DISABLED = BUILDER
                .comment("Message shown to the player when the warn system is disabled (via /mywarn).")
                .define("systemDisabled", "§c[Oneria] The warn system is disabled.");

        WARN_SYSTEM_DISABLED_CONFIG = BUILDER
                .comment("Message shown to staff when the warn system is disabled in config.")
                .define("systemDisabledConfig", "§c[Oneria] The warn system is disabled in config.");

        WARN_LIST_HEADER = BUILDER
                .comment("Header line of the warn list.",
                        "Placeholders: {player} = player name, {count} = active warn count.")
                .define("listHeader", "§6╔═ Warnings for §e{player} §6({count} active) ═╗");

        WARN_LIST_NONE = BUILDER
                .comment("Message shown to staff when a player has no warns. Placeholder: {player}.")
                .define("listNone", "§7[Oneria] §e{player} §7has no warnings.");

        WARN_LIST_NONE_SELF = BUILDER
                .comment("Message shown to a player when they have no active warns.")
                .define("listNoneSelf", "§a✔ You have no active warnings.");

        WARN_LIST_NONE_STAFF = BUILDER
                .comment("Message shown to staff when clearing a player who had no warns. Placeholder: {player}.")
                .define("listNoneStaff", "§7[Oneria] §e{player} §7had no warnings.");

        WARN_PURGE_DONE = BUILDER
                .comment("Message shown after a purge. Placeholder: {count} = purged count.")
                .define("purgeDone", "§a[Oneria] Purge complete: §e{count} §aexpired warn(s) removed.");

        WARN_INFO_HEADER = BUILDER
                .comment("Header of the warn info box. Placeholder: {id}.")
                .define("infoHeader", "§6╔═ Warn #{id} ════════════════════════╗");

        WARN_INFO_PLAYER_LABEL = BUILDER.comment("'Player' label in warn info.").define("infoPlayer",  "§6║ §7Player    : ");
        WARN_INFO_STAFF_LABEL  = BUILDER.comment("'Staff' label in warn info.").define("infoStaff",   "§6║ §7Staff     : ");
        WARN_INFO_REASON_LABEL = BUILDER.comment("'Reason' label in warn info.").define("infoReason",  "§6║ §7Reason    : ");
        WARN_INFO_DATE_LABEL   = BUILDER.comment("'Date' label in warn info.").define("infoDate",    "§6║ §7Date      : ");
        WARN_INFO_TYPE_LABEL   = BUILDER.comment("'Type' label in warn info.").define("infoType",    "§6║ §7Type      : ");
        WARN_INFO_EXPIRY_LABEL = BUILDER.comment("'Expiry' label in warn info.").define("infoExpiry",  "§6║ §7Expiry    : ");

        WARN_TYPE_PERMANENT = BUILDER.comment("Text for a permanent warn.").define("typePermanent", "§cPermanent");
        WARN_TYPE_EXPIRED   = BUILDER.comment("Text for an expired warn.").define("typeExpired",   "§8Expired");
        WARN_TYPE_TEMPORARY = BUILDER.comment("Text for a temporary active warn.").define("typeTemporary", "§eTemporary");

        WARN_STATUS_PERM_TAG    = BUILDER.comment("Tag for permanent warns in the list.").define("tagPerm",    "§c[PERM]");
        WARN_STATUS_TEMP_TAG    = BUILDER.comment("Tag for temporary active warns in the list.").define("tagTemp",    "§e[TEMP]");
        WARN_STATUS_EXPIRED_TAG = BUILDER.comment("Tag for expired warns in the list.").define("tagExpired", "§8[EXP]");

        WARN_MAX_DURATION_EXCEEDED = BUILDER
                .comment("Message shown when temp warn duration exceeds the maximum.",
                        "Placeholders: {maxDays}, {maxMinutes}.")
                .define("maxDurationExceeded",
                        "§c[Oneria] Maximum allowed duration: {maxDays} days ({maxMinutes} minutes).");

        WARN_DURATION_MINUTES = BUILDER
                .comment("Duration format for < 1 hour. Placeholder: {min}.")
                .define("durationMinutes", "{min} minute(s)");

        WARN_DURATION_HOURS = BUILDER
                .comment("Duration format for 1h–24h. Placeholders: {h}, {min}.")
                .define("durationHours", "{h}h {min}min");

        WARN_DURATION_DAYS = BUILDER
                .comment("Duration format for >= 1 day. Placeholders: {d}, {h}.")
                .define("durationDays", "{d}d {h}h");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Last Connection");

        LASTCONN_DISABLED = BUILDER
                .comment("Message shown when the last connection system is disabled in config.")
                .define("disabled", "§c[Oneria] Last connection tracking is disabled in config.");

        LASTCONN_PLAYER_NOT_FOUND = BUILDER
                .comment("Message shown when a player cannot be found.",
                        "Placeholders: {player} = player name.")
                .define("playerNotFound",
                        "§c[Oneria] Player not found: §e{player}\n§7(The player must have connected at least once.)");

        LASTCONN_NO_DATA = BUILDER
                .comment("Message shown when no connection data exists for a player. Placeholder: {player}.")
                .define("noData", "§c[Oneria] No connection data for §e{player}");

        LASTCONN_NO_DATA_LIST = BUILDER
                .comment("Message shown when no connection data exists at all.")
                .define("noDataList", "§7[Oneria] No connection data recorded yet.");

        LASTCONN_ONLINE  = BUILDER.comment("Status label for an online player.").define("online",  "§a● Online");
        LASTCONN_OFFLINE = BUILDER.comment("Status label for an offline player.").define("offline", "§7○ Offline");
        LASTCONN_UNKNOWN = BUILDER.comment("Label when a value is unknown/missing.").define("unknown", "§8Unknown");

        LASTCONN_BOX_HEADER = BUILDER
                .comment("Header of the last connection info box. Placeholder: none.")
                .define("boxHeader",  "§6╔═ Last connection ═══════════════════╗");
        LASTCONN_BOX_PLAYER = BUILDER.comment("'Player' label.").define("boxPlayer", "§6║ §7Player : ");
        LASTCONN_BOX_STATUS = BUILDER.comment("'Status' label.").define("boxStatus", "§6║ §7Status : ");
        LASTCONN_BOX_LOGIN  = BUILDER.comment("'Login' label.").define("boxLogin",  "§6║ §7Login  : ");
        LASTCONN_BOX_LOGOUT = BUILDER.comment("'Logout' label.").define("boxLogout", "§6║ §7Logout : ");

        LASTCONN_LIST_HEADER = BUILDER
                .comment("Header of the last connection list.",
                        "Placeholders: {shown} = displayed count, {total} = total count.")
                .define("listHeader", "§6╔═ Recent connections ({shown}/{total}) ══╗");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Death RP");

        DEATHRP_CONFIG_UNAVAILABLE = BUILDER
                .comment("Message shown when the DeathRP config is not yet available.")
                .define("configUnavailable", "§c[Oneria] Config not yet available.");

        DEATHRP_GLOBAL_ENABLED = BUILDER
                .comment("Staff feedback when the global DeathRP system is enabled.")
                .define("globalEnabled", "§a[Oneria] Global Death RP enabled.");

        DEATHRP_GLOBAL_DISABLED = BUILDER
                .comment("Staff feedback when the global DeathRP system is disabled.")
                .define("globalDisabled", "§c[Oneria] Global Death RP disabled.");

        DEATHRP_PLAYER_ENABLED = BUILDER
                .comment("Staff feedback when Death RP is enabled for a specific player.",
                        "Placeholder: {player} = player name.")
                .define("playerEnabled", "§a[Oneria] Death RP enabled for §e{player}§a.");

        DEATHRP_PLAYER_DISABLED = BUILDER
                .comment("Staff feedback when Death RP is disabled for a specific player.",
                        "Placeholder: {player} = player name.")
                .define("playerDisabled", "§c[Oneria] Death RP disabled for §e{player}§c.");

        DEATHRP_OVERRIDE_RESET = BUILDER
                .comment("Staff feedback when the individual override is removed.",
                        "Placeholder: {player} = player name.")
                .define("overrideReset",
                        "§a[Oneria] Death RP override removed for §e{player}§a. They now follow the global setting.");

        DEATHRP_STATUS_HEADER    = BUILDER.comment("Header of the deathrp status box.").define("statusHeader",    "§6═══════════════════════════════\n§6║ §eDeath RP System");
        DEATHRP_STATUS_GLOBAL    = BUILDER.comment("'Global state' label. Placeholder: {value}.").define("statusGlobal",    "§6║ §7Global state  : {value}");
        DEATHRP_STATUS_WHITELIST = BUILDER.comment("'Whitelist removal' label. Placeholder: {value}.").define("statusWhitelist", "§6║ §7WL removal    : {value}");
        DEATHRP_STATUS_OVERRIDES = BUILDER.comment("'Individual overrides' section header.").define("statusOverrides", "§6║ §eIndividual overrides:");
        DEATHRP_STATUS_NO_OVERRIDES = BUILDER.comment("Shown when there are no individual overrides.").define("statusNone",      "§6║  §7(no overrides)");
        DEATHRP_STATUS_ACTIVE    = BUILDER.comment("'Active' value in the status display.").define("statusActive",    "§aActive");
        DEATHRP_STATUS_INACTIVE  = BUILDER.comment("'Inactive' value in the status display.").define("statusInactive",  "§cInactive");
        DEATHRP_STATUS_YES       = BUILDER.comment("'Yes' value in the status display.").define("statusYes",       "§aYes");
        DEATHRP_STATUS_NO        = BUILDER.comment("'No' value in the status display.").define("statusNo",        "§7No");
        DEATHRP_STATUS_UNKNOWN   = BUILDER.comment("Fallback when a player name cannot be resolved.").define("statusUnknown",   "Unknown");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Whois");

        WHOIS_NOT_FOUND = BUILDER
                .comment("Message shown when no player is found for the given nickname.",
                        "Placeholder: {nick} = searched nickname.")
                .define("notFound", "§c[Whois] No player found with nickname: §f{nick}");

        WHOIS_RESULTS_HEADER = BUILDER
                .comment("Header shown before whois results.",
                        "Placeholder: {nick} = searched nickname.")
                .define("resultsHeader", "§6[Whois] §7Results for \"§f{nick}§7\":");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Player List");

        PLAYERLIST_HEADER = BUILDER
                .comment("Header of the /list command output.",
                        "Placeholder: {count} = number of online players.")
                .define("header", "§ePlayers online ({count}): ");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Help");

        HELP_TITLE       = BUILDER.comment("Title of the /oneria help box.").define("title",      "§6║ §e§lONERIA MOD §7— Commands");
        HELP_CMD_LIST     = BUILDER.comment("Help entry for /list.").define("cmdList",     "§6║ §e/list §7— Online players");
        HELP_CMD_SCHEDULE = BUILDER.comment("Help entry for /schedule.").define("cmdSchedule", "§6║ §e/schedule §7— Server schedule");
        HELP_CMD_MSG      = BUILDER.comment("Help entry for /msg.").define("cmdMsg",      "§6║ §e/msg §8<player> <message> §7— PM");
        HELP_CMD_REPLY    = BUILDER.comment("Help entry for /r.").define("cmdReply",    "§6║ §e/r §8<message> §7— Reply");
        HELP_STAFF_SECTION = BUILDER.comment("Staff section header in help.").define("staffSection", "§6║ §c§lSTAFF");

        HELP_DEATHRP_ENABLE  = BUILDER.comment("Help entry for /oneria deathrp enable.").define("deathRpEnable",  "§6║ §e/oneria deathrp enable §8<true|false> §7— Toggle global Death RP");
        HELP_DEATHRP_PLAYER  = BUILDER.comment("Help entry for /oneria deathrp player ... enable.").define("deathRpPlayer",  "§6║ §e/oneria deathrp player §8<player> enable <true|false> §7— Individual override");
        HELP_DEATHRP_RESET   = BUILDER.comment("Help entry for /oneria deathrp player ... reset.").define("deathRpReset",   "§6║ §e/oneria deathrp player §8<player> reset §7— Remove override");
        HELP_DEATHRP_STATUS  = BUILDER.comment("Help entry for /oneria deathrp status.").define("deathRpStatus",  "§6║ §e/oneria deathrp status §7— View system state and overrides");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Profession Restrictions");

        PROFESSION_CRAFT_BLOCKED_FALLBACK = BUILDER
                .comment("Fallback message when the profession config is unavailable during a craft block.")
                .define("craftBlockedFallback", "§cCrafting blocked.");

        PROFESSION_BREAK_BLOCKED_FALLBACK = BUILDER
                .comment("Fallback message when the profession config is unavailable during a block break.")
                .define("breakBlockedFallback", "§cBlock breaking blocked.");

        PROFESSION_USE_BLOCKED_FALLBACK = BUILDER
                .comment("Fallback message when the profession config is unavailable during item use.")
                .define("useBlockedFallback", "§cItem use blocked.");

        PROFESSION_EQUIP_BLOCKED_FALLBACK = BUILDER
                .comment("Fallback message when the profession config is unavailable during equipment.")
                .define("equipBlockedFallback", "§cEquipment blocked.");

        PROFESSION_NONE_AVAILABLE = BUILDER
                .comment("Shown instead of a profession list when no professions are required (open to all).")
                .define("noneAvailable", "§cNone");

        PROFESSION_SYSTEM_NOT_INIT = BUILDER
                .comment("Shown when the profession restriction system is not yet initialized.")
                .define("systemNotInit", "§cSystem not initialized");

        PROFESSION_HAS_LICENSE = BUILDER
                .comment("Part of the license check message when a player HAS the license.",
                        "Placeholders: {player} = player name, {profession} = profession name.")
                .define("hasLicense", "§e{player} §adoes have §ea §f{profession} §alicense.");

        PROFESSION_NO_LICENSE = BUILDER
                .comment("Part of the license check message when a player does NOT have the license.",
                        "Placeholders: {player} = player name, {profession} = profession name.")
                .define("noLicense", "§e{player} §cdoes NOT have §ea §f{profession} §clicense.");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Licenses");

        // --- Item ---
        LICENSE_ITEM_NAME = BUILDER
                .comment("Prefix of the physical license item name.",
                        "The profession name is appended automatically: e.g. '§aHunter §lLicense'.",
                        "Placeholder: {profession} = formatted profession name (color + display name).")
                .define("itemName", "§lLicense of ");

        LICENSE_LORE_ISSUED_TO = BUILDER
                .comment("Lore line showing who the license was issued to.",
                        "Placeholder: {player} = recipient display name.")
                .define("loreIssuedTo", "§7Issued to: §f{player}");

        LICENSE_LORE_DATE = BUILDER
                .comment("Lore line showing the issuance date on a permanent license.",
                        "Placeholder: {date} = date string.")
                .define("loreDate", "§7Date: §f{date}");

        LICENSE_LORE_ISSUED_DATE = BUILDER
                .comment("Lore line showing the issuance date on an RP license.",
                        "Placeholder: {date} = date string.")
                .define("loreIssuedDate", "§7Issued on: §f{date}");

        LICENSE_LORE_VALID_UNTIL = BUILDER
                .comment("Lore line showing the expiration date on an RP license.",
                        "Placeholder: {date} = expiration date string.")
                .define("loreValidUntil", "§7Valid until: §f{date}");

        // --- Tooltip (LicenseItem + TempLicenseExpirationManager) ---
        LICENSE_TOOLTIP_OFFICIAL = BUILDER
                .comment("Tooltip line shown on all license items.")
                .define("tooltipOfficial", "§7Official document");

        LICENSE_TOOLTIP_NONTRANSFERABLE = BUILDER
                .comment("Tooltip line shown on all license items.")
                .define("tooltipNonTransferable", "§8Non-transferable");

        LICENSE_REVOKED_TITLE = BUILDER
                .comment("Bold title added to a revoked license item's lore.")
                .define("revokedTitle", "§c§l✖ LICENSE REVOKED");

        LICENSE_REVOKED_BODY = BUILDER
                .comment("Description line added to a revoked license item's lore.")
                .define("revokedBody", "§cThis license is no longer valid.");

        // --- Commands ---
        LICENSE_UNKNOWN_PROFESSION = BUILDER
                .comment("Error shown when an unknown profession ID is provided.",
                        "Placeholder: {profession} = the unknown ID.")
                .define("unknownProfession", "§c[Oneria] Unknown profession: {profession}");

        LICENSE_GIVE_STAFF = BUILDER
                .comment("Staff feedback after issuing a permanent license.",
                        "Placeholders: {profession} = formatted profession name, {player} = recipient name.")
                .define("giveStaff", "§a[Oneria] License for {profession} §aissued to §f{player}");

        LICENSE_GIVE_PLAYER = BUILDER
                .comment("Notification sent to the player when they receive a permanent license.",
                        "Placeholder: {profession} = formatted profession name.")
                .define("givePlayer", "§aYou received a {profession}§6§l License§a!");

        LICENSE_GIVE_RP_STAFF = BUILDER
                .comment("Staff feedback after issuing an RP (temporary) license.",
                        "Placeholders: {profession}, {player}, {days}, {date} = expiration date.")
                .define("giveRpStaff",
                        "§a[Oneria] Temporary license for {profession} §aissued to §f{player} §7({days} days, expires {date})");

        LICENSE_GIVE_RP_PLAYER = BUILDER
                .comment("Notification sent to the player when they receive an RP license.",
                        "Placeholders: {profession}, {date} = expiration date.")
                .define("giveRpPlayer", "§aYou received a {profession}§6§l License §7valid until §f{date}");

        LICENSE_REVOKE_STAFF = BUILDER
                .comment("Staff feedback after revoking a license.",
                        "Placeholders: {profession} = profession display name, {player} = player name.")
                .define("revokeStaff", "§a[Oneria] License for §f{profession} §arevoked for §f{player}");

        LICENSE_REVOKE_PLAYER = BUILDER
                .comment("Notification sent to the player when their license is revoked.",
                        "Placeholder: {profession} = profession display name.")
                .define("revokePlayer", "§cYour §f{profession} §clicense has been revoked.");

        LICENSE_LIST_NONE = BUILDER
                .comment("Message shown when a player has no licenses.",
                        "Placeholder: {player} = player name.")
                .define("listNone", "§e[Oneria] §f{player} §ehas no licenses.");

        LICENSE_LIST_HEADER = BUILDER
                .comment("Header title of the license list box.",
                        "Placeholder: {player} = player name.")
                .define("listHeader", "§6║ §e§lLICENSES — §f{player}");

        LICENSE_LIST_RP_EXPIRY = BUILDER
                .comment("Suffix appended to a license entry when it is an RP (temporary) license.",
                        "Placeholder: {date} = expiration date.")
                .define("listRpExpiry", " §7(RP - expires {date})");

        LICENSE_LIST_ALL_NONE = BUILDER
                .comment("Message shown when no licenses are registered at all.")
                .define("listAllNone", "§e[Oneria] No licenses registered.");

        LICENSE_LIST_ALL_HEADER = BUILDER
                .comment("Header title line of the all-players license list.")
                .define("listAllHeader", "§6║ §e§lLICENSES — ALL PLAYERS §6║");

        LICENSE_LIST_ALL_NONE_FOR_PLAYER = BUILDER
                .comment("Shown in the all-players list when a player has no active license.")
                .define("listAllNoneForPlayer", "§8None");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Retrieves a message and replaces all {key} placeholders.
     * Usage: get(WARN_RECEIVED_PERM, "id", warnId, "reason", reason)
     *
     * @param configValue the config entry
     * @param replacements alternating key/value pairs
     */
    public static String get(ModConfigSpec.ConfigValue<String> configValue, String... replacements) {
        String msg;
        try {
            msg = configValue.get();
        } catch (IllegalStateException e) {
            msg = configValue.getDefault().toString();
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    /**
     * Retrieves a message with no placeholder substitution.
     */
    public static String get(ModConfigSpec.ConfigValue<String> configValue) {
        try {
            return configValue.get();
        } catch (IllegalStateException e) {
            return configValue.getDefault().toString();
        }
    }

    /**
     * Formats a warn duration (in minutes) into a human-readable string.
     */
    public static String formatDuration(int minutes) {
        if (minutes < 60) {
            return get(WARN_DURATION_MINUTES, "min", String.valueOf(minutes));
        } else if (minutes < 1440) {
            return get(WARN_DURATION_HOURS, "h", String.valueOf(minutes / 60), "min", String.valueOf(minutes % 60));
        } else {
            return get(WARN_DURATION_DAYS, "d", String.valueOf(minutes / 1440), "h", String.valueOf((minutes % 1440) / 60));
        }
    }
}