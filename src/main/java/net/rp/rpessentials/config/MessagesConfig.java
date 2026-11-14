package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.lang.ServerLang;

/**
 * Configuration file for all user-facing messages in the RpEssentials mod.
 * Allows server admins to customize or translate every message without recompiling.
 *
 * File: config/RpEssentials/RpEssentials-messages.toml
 * Reload: /RpEssentials config reload
 *
 * Supports § and & color codes in all message values.
 */
public class MessagesConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // CATEGORY: TRANSLATION
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> MESSAGE_SOURCE;
    public static final ModConfigSpec.ConfigValue<String> SERVER_LANGUAGE;

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
    public static final ModConfigSpec.ConfigValue<String> MP_SPY_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> MP_SPY_NOTICE;

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
    public static final ModConfigSpec.ConfigValue<String> LICENSE_EXPIRED_RP_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> PROFILE_PREPARED;
    public static final ModConfigSpec.ConfigValue<String> CONTAINER_FALLBACK;

    // =========================================================================
    // CATEGORY: SETROLE
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> SETROLE_UNKNOWN;
    public static final ModConfigSpec.ConfigValue<String> SETROLE_SUCCESS_STAFF;
    public static final ModConfigSpec.ConfigValue<String> SETROLE_SUCCESS_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_UPDATED;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_INVALID;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_TIME_INVALID;
    public static final ModConfigSpec.ConfigValue<String> AUTO_UNWHITELIST_STAFF_NOTIFY;

    // =========================================================================
    // CATEGORY: SCHEDULE MESSAGES
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_HEADER;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_STATUS_OPEN;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_STATUS_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_OPEN_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_CLOSED_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_TODAY_PREFIX;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_OTHER_PREFIX;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_FOOTER;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DEATH_HOURS_LABEL;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DEATH_HOURS_ACTIVE;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DEATH_HOURS_INACTIVE;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_HRP_LABEL;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_HRP_TOLERATED;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_HRP_ALLOWED;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_HRP_INACTIVE;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DEATH_HOURS_NOTIFY;
    public static final ModConfigSpec.ConfigValue<String> SCHEDULE_DEATH_HOURS_NOTIFY_MODE;

    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_MONDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_TUESDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_WEDNESDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_THURSDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_FRIDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_SATURDAY;
    public static ModConfigSpec.ConfigValue<String> SCHEDULE_DAY_SUNDAY;

    // =========================================================================
    // CATEGORY: RP COMMANDS
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> RP_PERMISSION_DENIED;
    public static final ModConfigSpec.ConfigValue<String> RP_AFK_TELEPORT;
    public static final ModConfigSpec.ConfigValue<String> AFK_COMBAT;
    public static final ModConfigSpec.ConfigValue<String> RP_COMMERCE_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> RP_INCOGNITO_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> RP_INCOGNITO_LOG;
    public static final ModConfigSpec.ConfigValue<String> RP_ACTION_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> RP_ACTION_SPY;
    public static final ModConfigSpec.ConfigValue<String> RP_ANNONCE_TITLE;
    public static final ModConfigSpec.ConfigValue<String> RP_ANNONCE_SUBTITLE;
    public static final ModConfigSpec.ConfigValue<String> RP_ANNONCE_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> RP_ANNONCE_SENT;

    public static final ModConfigSpec.ConfigValue<String> RP_SELFNICK_SET;
    public static final ModConfigSpec.ConfigValue<String> RP_SELFNICK_RESET;
    public static final ModConfigSpec.ConfigValue<String> RP_SELFNICK_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> RP_SELFNICK_COLORS_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> RP_SELFNICK_TOO_LONG;

    public static final ModConfigSpec.ConfigValue<String> NICK_USED;
    public static final ModConfigSpec.ConfigValue<String> NICK_FORBIDDEN;

    public static final ModConfigSpec.ConfigValue<String> LICENSE_REISSUE_STAFF;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REISSUE_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> LICENSE_REISSUE_NOT_FOUND;

    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_SPY_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_SPY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_SPY_DISABLED;

    public static final ModConfigSpec.ConfigValue<String> MUTE_RECEIVED;
    public static final ModConfigSpec.ConfigValue<String> MUTE_EXPIRED;
    public static final ModConfigSpec.ConfigValue<String> MUTE_NOTIFY_ON_JOIN;
    public static final ModConfigSpec.ConfigValue<String> MUTE_BLOCKED_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> MUTE_STAFF_ADDED;
    public static final ModConfigSpec.ConfigValue<String> MUTE_STAFF_REMOVED;
    public static final ModConfigSpec.ConfigValue<String> MUTE_AUTO_NOTIFY;
    public static final ModConfigSpec.ConfigValue<String> NOTE_ADDED;
    public static final ModConfigSpec.ConfigValue<String> NOTE_REMOVED;
    public static final ModConfigSpec.ConfigValue<String> NOTE_NONE;
    public static final ModConfigSpec.ConfigValue<String> NOTE_LIST_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_HISTORY_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_HISTORY_NONE;
    public static final ModConfigSpec.ConfigValue<String> DEATHRP_HISTORY_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> RP_COOLDOWN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> STATS_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_SPY_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> DICE_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> DICE_NONE;
    public static final ModConfigSpec.ConfigValue<String> DICE_UNKNOWN;

    public static final ModConfigSpec.ConfigValue<String> STAFF_RP_AFK_DIM, STAFF_RP_ANNONCE_TYPE, STAFF_RP_SPAWN_DIM, RP_SPAWN_TELEPORT;
    public static final ModConfigSpec.ConfigValue<String> STAFF_LIC_ALREADY, STAFF_LIC_ALREADY_TEMP, STAFF_LIC_CREATE_FAILED, STAFF_LIC_AUDIT_NONE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_TOOLS_SILENT_OFF, STAFF_TOOLS_BAD_GAMEMODE, STAFF_TOOLS_GAMEMODE_SET,
            STAFF_TOOLS_EFFECT_APPLIED, STAFF_TOOLS_BAD_EFFECT, STAFF_TOOLS_PLATFORMS_OFF, STAFF_TOOLS_DIM_MISSING,
            STAFF_TOOLS_TP_TARGET, STAFF_TOOLS_TP_SELF, STAFF_TOOLS_PLATFORM_MISSING, STAFF_TOOLS_PLATFORM_UPDATED,
            STAFF_TOOLS_PLATFORM_CREATED, STAFF_TOOLS_BROADCAST_FORMAT, STAFF_TOOLS_BROADCAST_SENT;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ZONE_NONE, STAFF_ZONE_LIST, STAFF_ZONE_HIDDEN, STAFF_ZONE_UNKNOWN, STAFF_ZONE_SHOWING;
    public static final ModConfigSpec.ConfigValue<String> STAFF_VANISH_OFF, STAFF_VANISH_ABOVE, STAFF_VANISH_VISIBLE, STAFF_VANISH_VANISHED,
            STAFF_VANISH_LIST_HEADER, STAFF_VANISH_LIST_NONE, STAFF_VANISH_LIST_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_BLUR_ALREADY, STAFF_BLUR_ADDED, STAFF_BLUR_NOT_IN, STAFF_BLUR_REMOVED,
            STAFF_BLUR_EMPTY, STAFF_BLUR_CONTENT;

    public static final ModConfigSpec.ConfigValue<String> MANAGE_NEED_SENSITIVE, MANAGE_NOT_ONLINE, MANAGE_WL_ADDED, MANAGE_WL_REMOVED,
            MANAGE_KICK_SELF, MANAGE_KICKED, MANAGE_BAN_SELF, MANAGE_ALREADY_BANNED, MANAGE_BANNED, MANAGE_NOT_BANNED, MANAGE_PARDONED,
            MANAGE_OP_TOO_HIGH, MANAGE_OP_LOWER_SELF, MANAGE_OP_SET, MANAGE_DEOP_SELF, MANAGE_NOT_OP, MANAGE_DEOP_HIGHER, MANAGE_DEOPPED,
            MANAGE_INV_CLEARED, MANAGE_ENDER_CLEARED, MANAGE_ABSENCE_SET, MANAGE_ABSENCE_REMOVED, MANAGE_NO_ABSENCE,
            MANAGE_NICK_CLEARED, MANAGE_NO_NICK, MANAGE_PREP_ROLE_CLEARED, MANAGE_ROLE_CLEARED, MANAGE_ROLES_UNAVAILABLE,
            MANAGE_PROF_CLEARED, MANAGE_NO_PROF, MANAGE_RESET_SELF, MANAGE_RESET_HIGHER, MANAGE_RESET_DONE,
            MANAGE_TP_ONLY_PLAYER, MANAGE_TP_SELF, MANAGE_TP_DONE, MANAGE_BRING_ONLY_PLAYER, MANAGE_BRING_SELF, MANAGE_BRING_DONE,
            MANAGE_PLATFORM_UNKNOWN, MANAGE_PLATFORM_SENT, MANAGE_AFK_MISSING, MANAGE_AFK_SENT,
            MANAGE_VANISH_OFF, MANAGE_VANISH_ABOVE_OWN, MANAGE_HIDDEN_ABOVE, MANAGE_VANISHED, MANAGE_NOT_VANISHED, MANAGE_VISIBLE,
            MANAGE_QUEUED, MANAGE_ALERT, MANAGE_CANCELLED, MANAGE_UNKNOWN_ACTION, MANAGE_PLAYER_NOT_FOUND, MANAGE_NUMBER_EXPECTED,
            MANAGE_RESET_NEEDS_NAME, MANAGE_RESET_PROMPT, MANAGE_CONFIRM_PROMPT, MANAGE_BTN_CONFIRM_RESET, MANAGE_BTN_CANCEL_GREY,
            MANAGE_BTN_CONFIRM, MANAGE_BTN_CANCEL, MANAGE_NOTHING,
            MANAGE_SUG_HEAD_WL, MANAGE_SUG_HEAD_DEOP, MANAGE_SUG_HEAD_BAN,
            MANAGE_SUG_L_REWL, MANAGE_SUG_H_REWL, MANAGE_SUG_L_BAN, MANAGE_SUG_H_BAN, MANAGE_SUG_L_CLRINV, MANAGE_SUG_H_CLRINV,
            MANAGE_SUG_L_ABS, MANAGE_SUG_H_ABS, MANAGE_SUG_L_REOP, MANAGE_SUG_H_REOP, MANAGE_SUG_L_KICK, MANAGE_SUG_H_KICK,
            MANAGE_SUG_L_PARDON, MANAGE_SUG_H_PARDON, MANAGE_SUG_L_UNWL, MANAGE_SUG_H_UNWL;

    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_PLAYER_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_ADDED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_TEMP_ADDED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_REMOVED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_NONE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_ALL_HEADER;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_TAG_EXPIRED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_TAG_PERM;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_TAG_TEMP;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_ALL_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_LIST_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_WARN_CLEARED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_MUTE_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_MUTE_CONFIG;
    public static final ModConfigSpec.ConfigValue<String> STAFF_NOTE_LIMIT;
    public static final ModConfigSpec.ConfigValue<String> STAFF_NOTE_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_NOTE_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> STAFF_NOTE_CLEARED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_HOURS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_HOURS_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_HOURS_SLOTS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_EFFECTIVE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_OVERRIDE_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_ALL_PLAYERS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_DRP_HISTORY_ALL_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_HEADER;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_STATUS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_ONLINE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_OFFLINE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NICK;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_ROLE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_EMPTY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_UUID;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_UUID_HOVER;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_LICENSES;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_LICENSE_TEMP;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_WARNS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_WARN_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_MUTE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_MUTE_ACTIVE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NONE_GREY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NONE_GREEN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_LAST_SEEN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_UNKNOWN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_PLAYTIME;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_SESSION;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NOTES;
    public static final ModConfigSpec.ConfigValue<String> STAFF_INSPECT_NOTE_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_NOT_FOUND;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_NONE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_HEADER;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_TAG_ENDED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_TAG_ACTIVE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_ENTRY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_ABS_REASON;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_TITLE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_LABEL_STATUS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_LABEL_NEXT;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_SECTION_OPENING;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_SECTION_SPECIAL;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_FORCED_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_FORCED_OPEN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_TOLERATED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_SETROLE_ALERT;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_STATE_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_STATE_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_FORCE_RESET;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_FORCE_OPEN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_FORCE_CLOSE;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_ALREADY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_ADDED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_NOT_IN;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_REMOVED;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_EMPTY;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_WL_LIST;
    public static final ModConfigSpec.ConfigValue<String> STAFF_SCHED_CONFIG;

    public static final ModConfigSpec.ConfigValue<String> STAFF_NICK_COLORS_OFF,
            STAFF_NICK_SET,
            STAFF_NICK_SET_TARGET,
            STAFF_NICK_SET_ERROR,
            STAFF_NICK_RESET,
            STAFF_NICK_RESET_TARGET,
            STAFF_NICK_RESET_ERROR,
            STAFF_NICK_LIST_EMPTY,
            STAFF_NICK_LIST_HEADER,
            STAFF_NICK_WHOIS_HOVER,
            STAFF_NICK_WHOIS_MC,
            STAFF_NICK_WHOIS_NICK;
    public static final ModConfigSpec.ConfigValue<String> STAFF_HELP_TITLE,
            STAFF_HELP_PUBLIC,
            STAFF_HELP_STAFF_TITLE,
            STAFF_HELP_STAFF;
    public static final ModConfigSpec.ConfigValue<String> STAFF_TOOLSX_OP_TOO_HIGH,
            STAFF_TOOLSX_OP_SET,
            STAFF_TOOLSX_OP_NOTHING,
            STAFF_TOOLSX_STAFF_LOG;
    public static final ModConfigSpec.ConfigValue<String> STAFF_LASTCO_COPY_HOVER;
    public static final ModConfigSpec.ConfigValue<String> STAFF_STATS_DISABLED,
            STAFF_STATS_OPEN,
            STAFF_STATS_CLOSED,
            STAFF_STATS_NA,
            STAFF_STATS_ON,
            STAFF_STATS_OFF,
            STAFF_STATS_S_SERVER,
            STAFF_STATS_S_PROFESSIONS,
            STAFF_STATS_S_MODERATION,
            STAFF_STATS_S_PLAYTIME,
            STAFF_STATS_S_RECENT,
            STAFF_STATS_L_ONLINE,
            STAFF_STATS_L_KNOWN,
            STAFF_STATS_L_PLAYTIME,
            STAFF_STATS_L_NICKNAMES,
            STAFF_STATS_L_SCHEDULE,
            STAFF_STATS_L_HOLDERS,
            STAFF_STATS_L_EXPIRING,
            STAFF_STATS_L_WARNS,
            STAFF_STATS_L_MUTES,
            STAFF_STATS_L_DEATHRP,
            STAFF_STATS_L_OVERRIDES,
            STAFF_STATS_L_DEATHS,
            STAFF_STATS_V_HOLDERS,
            STAFF_STATS_V_WARNS;
    public static final ModConfigSpec.ConfigValue<String> STAFF_CFG_BACKUP,
            STAFF_CFG_EXPORT_DONE,
            STAFF_CFG_EXPORT_FAIL,
            STAFF_CFG_VALUE_SET,
            STAFF_CFG_TOGGLE_SET,
            STAFF_CFG_ENABLED,
            STAFF_CFG_DISABLED,
            STAFF_CFG_ZONE_MODES,
            STAFF_CFG_RELOADED,
            STAFF_CFG_VALID,
            STAFF_CFG_PROBLEMS,
            STAFF_CFG_STATUS_TITLE,
            STAFF_CFG_DETECTED,
            STAFF_CFG_ABSENT,
            STAFF_CFG_BLOCKS,
            STAFF_CFG_S_OBFUSCATION,
            STAFF_CFG_S_PERMISSIONS,
            STAFF_CFG_S_SCHEDULE,
            STAFF_CFG_S_CHAT,
            STAFF_CFG_S_BORDER,
            STAFF_CFG_S_MODERATION,
            STAFF_CFG_S_PROFESSIONS,
            STAFF_CFG_L_BLUR,
            STAFF_CFG_L_RANGE,
            STAFF_CFG_L_SNEAK,
            STAFF_CFG_L_OPS_SEE_ALL,
            STAFF_CFG_L_HIDE_NAMETAGS,
            STAFF_CFG_L_WHITELIST,
            STAFF_CFG_L_BLACKLIST,
            STAFF_CFG_L_ALWAYS_VISIBLE,
            STAFF_CFG_L_LUCKPERMS,
            STAFF_CFG_L_USE_GROUPS,
            STAFF_CFG_L_OP_LEVEL,
            STAFF_CFG_L_ROLES,
            STAFF_CFG_L_SCHEDULE,
            STAFF_CFG_L_DEATH_RP,
            STAFF_CFG_L_DEATH_HOURS,
            STAFF_CFG_L_WHITELIST_REMOVAL,
            STAFF_CFG_L_HRP_HOURS,
            STAFF_CFG_L_FORMAT,
            STAFF_CFG_L_PROXIMITY,
            STAFF_CFG_L_MARKDOWN,
            STAFF_CFG_L_TIMESTAMP,
            STAFF_CFG_L_JOIN_LEAVE,
            STAFF_CFG_L_COLOR,
            STAFF_CFG_L_ENABLED,
            STAFF_CFG_L_DISTANCE,
            STAFF_CFG_L_TELEPORT,
            STAFF_CFG_L_NAMED_ZONES,
            STAFF_CFG_L_WARNS,
            STAFF_CFG_L_MUTES,
            STAFF_CFG_L_LAST_CONNECTION,
            STAFF_CFG_L_AUTO_UNWHITELIST,
            STAFF_CFG_L_SILENT_COMMANDS,
            STAFF_CFG_L_PLATFORMS,
            STAFF_CFG_L_PROFESSIONS,
            STAFF_CFG_L_BLOCKED_CRAFTS,
            STAFF_CFG_L_BLOCKED_ITEMS,
            STAFF_CFG_L_DICE,
            STAFF_CFG_L_SELF_NICK,
            STAFF_CFG_L_WELCOME;

    public static final ModConfigSpec.ConfigValue<String> GUI_MORE, GUI_CONFIG_NOT_LOADED, GUI_ROLES_NEED_LEVEL,
            GUI_PENDING_DELETED, GUI_PROF_DELETED, GUI_ROLE_DELETED, GUI_LP_GROUP, GUI_LP_GROUP_CHANGED, GUI_PERMISSIONS,
            GUI_NO_PERMISSIONS, GUI_NO_CHANGE, GUI_ROLE_INVALID, GUI_ROLE_ID_TAKEN, GUI_ROLE_CREATED, GUI_ROLE_UPDATED,
            GUI_NOTE_NOT_FOUND, GUI_NOTE_DELETED, GUI_NOTE_EDITED, GUI_NOTE_ADDED, GUI_NOTE_AUTHOR, GUI_NOTE_LIMIT,
            GUI_ADD_NOT_FOUND, GUI_ADD_KNOWN, GUI_ADD_CONNECTED, GUI_ADD_DONE,
            GUI_CONFIG_NO_CHANGES, GUI_CONFIG_NO_VALID, GUI_CONFIG_APPLIED, GUI_CONFIG_NOT_APPLIED, GUI_CONFIG_FAILED,
            GUI_GLOBAL_NO_CHANGE, GUI_GLOBAL_UPDATED, GUI_CAT_BLOCKED_CRAFTS, GUI_CAT_UNBREAKABLE, GUI_CAT_BLOCKED_ITEMS,
            GUI_CAT_BLOCKED_EQUIPMENT, GUI_CAT_CONTAINERS,
            GUI_PROF_INVALID, GUI_PROF_RESERVED, GUI_PROF_CREATED, GUI_PROF_UPDATED, GUI_PROF_SUMMARY,
            GUI_PROF_LINE, GUI_PROF_LINE_NONE, GUI_PROF_COUNT, GUI_PROF_MORE,
            GUI_LABEL_CRAFTS, GUI_LABEL_BLOCKS, GUI_LABEL_ITEMS, GUI_LABEL_EQUIPMENT,
            GUI_PROFILE_OFFLINE, GUI_PREPARED_UPDATED, GUI_NO_LICENSE, GUI_PROFILE_UPDATED, GUI_UNKNOWN_ROLE,
            GUI_ALREADY_LICENSE, GUI_UNKNOWN_PROFESSION;
    public static final ModConfigSpec.ConfigValue<String> VANISH_MSG_SILENT_JOIN, VANISH_MSG_SILENT_LEAVE;
    public static final ModConfigSpec.ConfigValue<String> SCHED_SYS_CONFIG_WARNING, SCHED_SYS_STAFF_CLOSED;

    // Common Text
    public static final ModConfigSpec.ConfigValue<String> TIME_PERMANENT;
    public static final ModConfigSpec.ConfigValue<String> TIME_EXPIRED;
    public static final ModConfigSpec.ConfigValue<String> TIME_REMAINING_DAYS;
    public static final ModConfigSpec.ConfigValue<String> TIME_REMAINING_HOURS;
    public static final ModConfigSpec.ConfigValue<String> TIME_REMAINING_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> TIME_LESS_MINUTE;
    public static final ModConfigSpec.ConfigValue<String> TIME_UNIT_DAYS;
    public static final ModConfigSpec.ConfigValue<String> TIME_UNIT_HOURS;
    public static final ModConfigSpec.ConfigValue<String> TIME_UNIT_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> UI_ON;
    public static final ModConfigSpec.ConfigValue<String> UI_OFF;
    public static final ModConfigSpec.ConfigValue<String> UI_NA;
    public static final ModConfigSpec.ConfigValue<String> UI_PAGE;
    public static final ModConfigSpec.ConfigValue<String> UI_CONSOLE;
    public static final ModConfigSpec.ConfigValue<String> LOGIN_REFUSED_NOTICE;
    public static final ModConfigSpec.ConfigValue<String> LOGIN_VANISH_REMINDER;
    public static final ModConfigSpec.ConfigValue<String> LOGIN_FORCE_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> SCHED_WARN_INVALID_OPEN;
    public static final ModConfigSpec.ConfigValue<String> SCHED_WARN_INVALID_CLOSE;
    public static final ModConfigSpec.ConfigValue<String> MUTE_AUTO_REASON;
    public static final ModConfigSpec.ConfigValue<String> UNWHITELIST_UNDO;
    public static final ModConfigSpec.ConfigValue<String> UNWHITELIST_UNDO_HOVER;
    public static final ModConfigSpec.ConfigValue<String> GUI_REPORT_NICK;
    public static final ModConfigSpec.ConfigValue<String> GUI_REPORT_NICK_RESET;
    public static final ModConfigSpec.ConfigValue<String> GUI_REPORT_ROLE;
    public static final ModConfigSpec.ConfigValue<String> GUI_REPORT_LICENSE;
    public static final ModConfigSpec.ConfigValue<String> GUI_REPORT_LICENSE_DAYS;
    public static final ModConfigSpec.ConfigValue<String> VANISH_ADVANCEMENT_HIDDEN;
    public static final ModConfigSpec.ConfigValue<String> LP_ROLES_SYNCED;
    public static final ModConfigSpec.ConfigValue<String> LIC_AUDIT_HEADER;
    public static final ModConfigSpec.ConfigValue<String> LIC_AUDIT_LINE;
    public static final ModConfigSpec.ConfigValue<String> COLORS_TITLE;
    public static final ModConfigSpec.ConfigValue<String> COLORS_NAMES;
    public static final ModConfigSpec.ConfigValue<String> COLORS_FORMATTING;
    public static final ModConfigSpec.ConfigValue<String> COLORS_BOLD;
    public static final ModConfigSpec.ConfigValue<String> COLORS_ITALIC;
    public static final ModConfigSpec.ConfigValue<String> COLORS_UNDERLINE;
    public static final ModConfigSpec.ConfigValue<String> COLORS_STRIKE;
    public static final ModConfigSpec.ConfigValue<String> COLORS_OBFUSCATED;
    public static final ModConfigSpec.ConfigValue<String> COLORS_RESET;
    public static final ModConfigSpec.ConfigValue<String> COLORS_CHAT_VARS;
    public static final ModConfigSpec.ConfigValue<String> COLORS_RP_VARS;
    public static final ModConfigSpec.ConfigValue<String> COLORS_HEX;
    public static final ModConfigSpec.ConfigValue<String> COLORS_VAR_NICK;
    public static final ModConfigSpec.ConfigValue<String> COLORS_VAR_REAL;
    public static final ModConfigSpec.ConfigValue<String> COLORS_VAR_NICK_REAL;
    public static final ModConfigSpec.ConfigValue<String> COLORS_HEX_COLOR;
    public static final ModConfigSpec.ConfigValue<String> COLORS_GRADIENT;
    public static final ModConfigSpec.ConfigValue<String> COLORS_GRADIENT_TEXT;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_BAD_LINE;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_BAD_PATTERN;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_BAD_PATTERN_IN;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_UNKNOWN_PROF;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_UNKNOWN_PROF_IN;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_BAD_SLOT;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_EMPTY_ROLE;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_UNKNOWN_PERM;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_NOT_LOADED;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_CMD_CONFLICT;

    // Status Text
    public static final ModConfigSpec.ConfigValue<String> SCHED_NEXT_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> SCHED_NEXT_NOT_INIT;
    public static final ModConfigSpec.ConfigValue<String> SCHED_NEXT_OPEN;
    public static final ModConfigSpec.ConfigValue<String> SCHED_NEXT_NONE;
    public static final ModConfigSpec.ConfigValue<String> SCHED_NEXT_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> SCHED_TODAY;
    public static final ModConfigSpec.ConfigValue<String> BLUR_LIST_WHITELIST;
    public static final ModConfigSpec.ConfigValue<String> BLUR_LIST_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<String> BLUR_LIST_ALWAYS;
    public static final ModConfigSpec.ConfigValue<String> BLUR_TITLE_WHITELIST;
    public static final ModConfigSpec.ConfigValue<String> BLUR_TITLE_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<String> BLUR_TITLE_ALWAYS;
    public static final ModConfigSpec.ConfigValue<String> REASON_NONE;

    // Player History
    public static final ModConfigSpec.ConfigValue<String> HISTORY_SYSTEM;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_HEADER;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_EMPTY;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_UNKNOWN_PLAYER;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_LINE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_PURGED;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_WARN;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_WARN_REMOVE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_WARN_CLEAR;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_MUTE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_UNMUTE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_NOTE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_NOTE_REMOVE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_MANAGE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_LICENSE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_DEATH_RP;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_ALL_NOTES;

    // Audit Text
    public static final ModConfigSpec.ConfigValue<String> AUDIT_VIA_GUI;
    public static final ModConfigSpec.ConfigValue<String> AUDIT_TEMP;
    public static final ModConfigSpec.ConfigValue<String> AUDIT_TEMP_GUI;
    public static final ModConfigSpec.ConfigValue<String> AUDIT_REISSUED;
    public static final ModConfigSpec.ConfigValue<String> AUDIT_VIA_MANAGE;
    public static final ModConfigSpec.ConfigValue<String> AUDIT_EXPIRED_ON;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_QUEUED_REASON;

    // Manage Extra
    public static final ModConfigSpec.ConfigValue<String> MANAGE_TEMPBANNED;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_DURATION_EXPECTED;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_SUG_L_TEMPBAN;
    public static final ModConfigSpec.ConfigValue<String> MANAGE_SUG_H_TEMPBAN;

    // Teleport Warmup
    public static final ModConfigSpec.ConfigValue<String> TP_WARMUP_COUNTDOWN;
    public static final ModConfigSpec.ConfigValue<String> TP_WARMUP_CANCELLED_MOVE;
    public static final ModConfigSpec.ConfigValue<String> TP_WARMUP_CANCELLED_DAMAGE;

    // Schedule Exceptions
    public static final ModConfigSpec.ConfigValue<String> SCHED_WARN_INVALID_EXCEPTION;
    public static final ModConfigSpec.ConfigValue<String> SCHED_SECTION_EXCEPTIONS;
    public static final ModConfigSpec.ConfigValue<String> SCHED_EXCEPTION_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> SCHED_EXCEPTION_OPEN;

    // Dice Extra
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_DETAIL;
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_SECRET_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> DICE_ADVANTAGE;
    public static final ModConfigSpec.ConfigValue<String> DICE_DISADVANTAGE;
    public static final ModConfigSpec.ConfigValue<String> DICE_SECRET_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> DICE_EXPRESSIONS_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> DICE_TOO_MANY;

    // Validate Extra
    public static final ModConfigSpec.ConfigValue<String> CFG_V_UNKNOWN_DIMENSION;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_UNKNOWN_SOUND;
    public static final ModConfigSpec.ConfigValue<String> CFG_V_ORPHAN_LICENSE;

    // Player Stats
    public static final ModConfigSpec.ConfigValue<String> STATS_SECTION_ACTIVITY;
    public static final ModConfigSpec.ConfigValue<String> STATS_SECTION_DISCIPLINE;
    public static final ModConfigSpec.ConfigValue<String> STATS_SECTION_RP;
    public static final ModConfigSpec.ConfigValue<String> STATS_SECTION_ACCESS;
    public static final ModConfigSpec.ConfigValue<String> STATS_FIRST_JOIN;
    public static final ModConfigSpec.ConfigValue<String> STATS_LAST_LOGIN;
    public static final ModConfigSpec.ConfigValue<String> STATS_LAST_LOGOUT;
    public static final ModConfigSpec.ConfigValue<String> STATS_ONLINE_NOW;
    public static final ModConfigSpec.ConfigValue<String> STATS_UNKNOWN;
    public static final ModConfigSpec.ConfigValue<String> STATS_AGO_NOW;
    public static final ModConfigSpec.ConfigValue<String> STATS_AGO_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> STATS_AGO_HOURS;
    public static final ModConfigSpec.ConfigValue<String> STATS_AGO_DAYS;
    public static final ModConfigSpec.ConfigValue<String> STATS_WARNS_TOTAL;
    public static final ModConfigSpec.ConfigValue<String> STATS_MUTES_TOTAL;
    public static final ModConfigSpec.ConfigValue<String> STATS_ABSENCE;
    public static final ModConfigSpec.ConfigValue<String> STATS_ABSENCE_NONE;
    public static final ModConfigSpec.ConfigValue<String> STATS_TEMP_LICENSE;
    public static final ModConfigSpec.ConfigValue<String> STATS_TEMP_NONE;
    public static final ModConfigSpec.ConfigValue<String> STATS_DEATHRP;
    public static final ModConfigSpec.ConfigValue<String> STATS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> STATS_DISABLED;
    public static final ModConfigSpec.ConfigValue<String> STATS_WHITELIST;
    public static final ModConfigSpec.ConfigValue<String> STATS_YES;
    public static final ModConfigSpec.ConfigValue<String> STATS_NO;
    public static final ModConfigSpec.ConfigValue<String> STATS_OP;
    public static final ModConfigSpec.ConfigValue<String> STATS_ROLE;
    public static final ModConfigSpec.ConfigValue<String> STATS_ROLE_OFFLINE;
    public static final ModConfigSpec.ConfigValue<String> STATS_VANISH;
    public static final ModConfigSpec.ConfigValue<String> STATS_VANISH_LEVEL;
    public static final ModConfigSpec.ConfigValue<String> STATS_VISIBLE;

    // Player Stats Extra
    public static final ModConfigSpec.ConfigValue<String> STATS_LOGINS;
    public static final ModConfigSpec.ConfigValue<String> STATS_PLAYTIME_RECENT;
    public static final ModConfigSpec.ConfigValue<String> STATS_NICKS;
    public static final ModConfigSpec.ConfigValue<String> STATS_NICKS_NONE;
    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_NICK;

    public static final ModConfigSpec.ConfigValue<String> HISTORY_T_REPORT, REPORT_DISABLED, REPORT_SELF,
            REPORT_UNKNOWN_PLAYER, REPORT_COOLDOWN, REPORT_TOO_MANY, REPORT_SENT, REPORT_STAFF_NEW, REPORT_BTN_VIEW,
            REPORT_BTN_VIEW_HOVER, REPORT_STAFF_JOIN, REPORT_NOT_FOUND, REPORT_CLAIMED, REPORT_CLAIMED_STAFF,
            REPORT_CLOSED, REPORT_REOPENED, REPORT_DELETED, REPORT_ALREADY_CLOSED, REPORT_NOT_CLOSED,
            REPORT_REPORTER_CLOSED, REPORT_LIST_HEADER, REPORT_LIST_NONE, REPORT_LIST_LINE, REPORT_INFO,
            REPORT_INFO_HANDLER, REPORT_INFO_NOTE, REPORT_STATUS_OPEN, REPORT_STATUS_CLAIMED, REPORT_STATUS_CLOSED,
            REPORT_BTN_CLAIM, REPORT_BTN_CLOSE, REPORT_BTN_TP, REPORT_TP_FAILED;

    static {

        // =========================================================================
        BUILDER.push("Message Source");

        MESSAGE_SOURCE = BUILDER
                .comment("Where player messages come from.",
                        "CONFIG : the values written in this file and in rpessentials-professions.toml (default).",
                        "MOD    : the translation files shipped with the mod, in the language of each player's client.",
                        "         A missing translation falls back to serverLanguage, then en_us, then the value in this file.")
                .defineInList("messageSource", "CONFIG", java.util.Arrays.asList("CONFIG", "MOD"));

        SERVER_LANGUAGE = BUILDER
                .comment("Language used in MOD mode for the console and as fallback (for example en_us, fr_fr).")
                .define("serverLanguage", "en_us");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("System");

        SYSTEM_CONFIG_NOT_LOADED = BUILDER
                .comment("Message shown when the config is not yet loaded.")
                .define("configNotLoaded", "§c[RpEssentials] Config not loaded.");

        SYSTEM_CONFIG_UNAVAILABLE = BUILDER
                .comment("Message shown when a specific config value is unavailable.")
                .define("configUnavailable", "§c[RpEssentials] Config unavailable.");

        SYSTEM_CONFIG_NOT_BUILT = BUILDER
                .comment("Message shown when the config spec is not yet built.")
                .define("configNotBuilt", "§c[RpEssentials] Config not yet initialized.");

        SYSTEM_CONFIG_UPDATED = BUILDER
                .comment("Message shown when a config value is successfully updated.",
                        "Placeholders: {label} = option name, {value} = new value.")
                .define("configUpdated", "§a[RpEssentials] §e{label} §aupdated: §f{value}");

        COMMAND_PLAYER_ONLY = BUILDER
                .comment("Message shown when a player-only command is run from console.")
                .define("commandPlayerOnly", "§c[RpEssentials] This command can only be used by a player.");

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

        MP_SPY_FORMAT = BUILDER
                .comment("Format of the private message spy shown to staff.",
                        "Placeholders: {sender}, {target}, {msg}.")
                .define("pmSpyFormat", "§7[MP-SPY] §e{sender} §7to §e{target}§7: §f{msg}");

        MP_SPY_NOTICE = BUILDER
                .comment("Notice sent to every player at login when private message spy is enabled.",
                        "Leave empty to disable the notice (not recommended).")
                .define("pmSpyNotice", "§7[Info] Staff members may read private messages on this server.");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Warn System");

        WARN_RECEIVED_PERM = BUILDER
                .comment("Message sent to a player when they receive a permanent warn.",
                        "Placeholders: {id} = warn ID, {reason} = reason.")
                .define("receivedPermanent",
                        """
                                §c⚠ §lYou have received a warning §r§c(warn #{id})!
                                §7Reason  : §f{reason}
                                §7Duration: §fPermanent
                                §7Type §l/mywarn §r§7to view all your warnings.""");

        WARN_RECEIVED_TEMP = BUILDER
                .comment("Message sent to a player when they receive a temporary warn.",
                        "Placeholders: {id} = warn ID, {reason} = reason, {duration} = formatted duration.")
                .define("receivedTemporary",
                        """
                                §c⚠ §lYou have received a temporary warning §r§c(warn #{id})!
                                §7Reason  : §f{reason}
                                §7Duration: §f{duration}
                                §7Type §l/mywarn §r§7to view all your warnings.""");

        WARN_NOT_FOUND = BUILDER
                .comment("Message shown when a warn ID cannot be found. Placeholder: {id}.")
                .define("notFound", "§c[RpEssentials] Warn not found: §e#{id}");

        WARN_REMOVE_FAILED = BUILDER
                .comment("Message shown when a warn cannot be removed. Placeholder: {id}.")
                .define("removeFailed", "§c[RpEssentials] Failed to remove warn §e#{id}");

        WARN_REMOVED_PLAYER = BUILDER
                .comment("Message sent to the warned player when their warn is removed. Placeholder: {id}.")
                .define("removedPlayer", "§a✔ Your warning §l#{id} §r§ahas been removed by staff.");

        WARN_CLEARED_PLAYER = BUILDER
                .comment("Message sent to the player when all their warns are cleared.")
                .define("clearedPlayer", "§a✔ All your warnings have been cleared by staff.");

        WARN_SYSTEM_DISABLED = BUILDER
                .comment("Message shown to the player when the warn system is disabled (via /mywarn).")
                .define("systemDisabled", "§c[RpEssentials] The warn system is disabled.");

        WARN_SYSTEM_DISABLED_CONFIG = BUILDER
                .comment("Message shown to staff when the warn system is disabled in config.")
                .define("systemDisabledConfig", "§c[RpEssentials] The warn system is disabled in config.");

        WARN_LIST_HEADER = BUILDER
                .comment("Header line of the warn list.",
                        "Placeholders: {player} = player name, {count} = active warn count.")
                .define("listHeader", "§6Warnings for §e{player} §7({count} active)");

        WARN_LIST_NONE = BUILDER
                .comment("Message shown to staff when a player has no warns. Placeholder: {player}.")
                .define("listNone", "§7[RpEssentials] §e{player} §7has no warnings.");

        WARN_LIST_NONE_SELF = BUILDER
                .comment("Message shown to a player when they have no active warns.")
                .define("listNoneSelf", "§a✔ You have no active warnings.");

        WARN_LIST_NONE_STAFF = BUILDER
                .comment("Message shown to staff when clearing a player who had no warns. Placeholder: {player}.")
                .define("listNoneStaff", "§7[RpEssentials] §e{player} §7had no warnings.");

        WARN_PURGE_DONE = BUILDER
                .comment("Message shown after a purge. Placeholder: {count} = purged count.")
                .define("purgeDone", "§a[RpEssentials] Purge complete: §e{count} §aexpired warn(s) removed.");

        WARN_INFO_HEADER = BUILDER.comment("Header of the warn info box. Placeholder: {id}.").define("infoHeader", "§6Warn #{id}");
        WARN_INFO_PLAYER_LABEL = BUILDER.comment("'Player' label in warn info.").define("infoPlayer",  "§7Player    : ");
        WARN_INFO_STAFF_LABEL  = BUILDER.comment("'Staff' label in warn info.").define("infoStaff",   "§7Staff     : ");
        WARN_INFO_REASON_LABEL = BUILDER.comment("'Reason' label in warn info.").define("infoReason",  "§7Reason    : ");
        WARN_INFO_DATE_LABEL   = BUILDER.comment("'Date' label in warn info.").define("infoDate",    "§7Date      : ");
        WARN_INFO_TYPE_LABEL   = BUILDER.comment("'Type' label in warn info.").define("infoType",    "§7Type      : ");
        WARN_INFO_EXPIRY_LABEL = BUILDER.comment("'Expiry' label in warn info.").define("infoExpiry",  "§7Expiry    : ");

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
                        "§c[RpEssentials] Maximum allowed duration: {maxDays} days ({maxMinutes} minutes).");

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
                .define("disabled", "§c[RpEssentials] Last connection tracking is disabled in config.");

        LASTCONN_PLAYER_NOT_FOUND = BUILDER
                .comment("Message shown when a player cannot be found.",
                        "Placeholders: {player} = player name.")
                .define("playerNotFound",
                        "§c[RpEssentials] Player not found: §e{player}\n§7(The player must have connected at least once.)");

        LASTCONN_NO_DATA = BUILDER
                .comment("Message shown when no connection data exists for a player. Placeholder: {player}.")
                .define("noData", "§c[RpEssentials] No connection data for §e{player}");

        LASTCONN_NO_DATA_LIST = BUILDER
                .comment("Message shown when no connection data exists at all.")
                .define("noDataList", "§7[RpEssentials] No connection data recorded yet.");

        LASTCONN_ONLINE  = BUILDER.comment("Status label for an online player.").define("online",  "§a● Online");
        LASTCONN_OFFLINE = BUILDER.comment("Status label for an offline player.").define("offline", "§7○ Offline");
        LASTCONN_UNKNOWN = BUILDER.comment("Label when a value is unknown/missing.").define("unknown", "§8Unknown");

        LASTCONN_BOX_HEADER = BUILDER
                .comment("Header of the last connection info box. Placeholder: none.")
                .define("boxHeader",  "§6§lLast connection");
        LASTCONN_BOX_PLAYER = BUILDER.comment("'Player' label.").define("boxPlayer", "§7Player : ");
        LASTCONN_BOX_STATUS = BUILDER.comment("'Status' label.").define("boxStatus", "§7Status : ");
        LASTCONN_BOX_LOGIN  = BUILDER.comment("'Login' label.").define("boxLogin",  "§7Login  : ");
        LASTCONN_BOX_LOGOUT = BUILDER.comment("'Logout' label.").define("boxLogout", "§7Logout : ");

        LASTCONN_LIST_HEADER = BUILDER
                .comment("Header of the last connection list.",
                        "Placeholders: {shown} = displayed count, {total} = total count.")
                .define("listHeader", "§6§lConnections §8- §7page {page}/{pages} §8({total} players)");

        BUILDER.pop();

        // =========================================================================
        BUILDER.push("Death RP");

        DEATHRP_CONFIG_UNAVAILABLE = BUILDER
                .comment("Message shown when the DeathRP config is not yet available.")
                .define("configUnavailable", "§c[RpEssentials] Config not yet available.");

        DEATHRP_GLOBAL_ENABLED = BUILDER
                .comment("Staff feedback when the global DeathRP system is enabled.")
                .define("globalEnabled", "§a[RpEssentials] Global Death RP enabled.");

        DEATHRP_GLOBAL_DISABLED = BUILDER
                .comment("Staff feedback when the global DeathRP system is disabled.")
                .define("globalDisabled", "§c[RpEssentials] Global Death RP disabled.");

        DEATHRP_PLAYER_ENABLED = BUILDER
                .comment("Staff feedback when Death RP is enabled for a specific player.",
                        "Placeholder: {player} = player name.")
                .define("playerEnabled", "§a[RpEssentials] Death RP enabled for §e{player}§a.");

        DEATHRP_PLAYER_DISABLED = BUILDER
                .comment("Staff feedback when Death RP is disabled for a specific player.",
                        "Placeholder: {player} = player name.")
                .define("playerDisabled", "§c[RpEssentials] Death RP disabled for §e{player}§c.");

        DEATHRP_OVERRIDE_RESET = BUILDER
                .comment("Staff feedback when the individual override is removed.",
                        "Placeholder: {player} = player name.")
                .define("overrideReset",
                        "§a[RpEssentials] Death RP override removed for §e{player}§a. They now follow the global setting.");

        DEATHRP_STATUS_HEADER    = BUILDER.comment("Header of the deathrp status box.").define("statusHeader",    "§6Death RP System");
        DEATHRP_STATUS_GLOBAL    = BUILDER.comment("'Global state' label. Placeholder: {value}.").define("statusGlobal",    "§7Global state  : {value}");
        DEATHRP_STATUS_WHITELIST = BUILDER.comment("'Whitelist removal' label. Placeholder: {value}.").define("statusWhitelist", "§7WL removal    : {value}");
        DEATHRP_STATUS_OVERRIDES = BUILDER.comment("'Individual overrides' section header.").define("statusOverrides", "§eIndividual overrides:");
        DEATHRP_STATUS_NO_OVERRIDES = BUILDER.comment("Shown when there are no individual overrides.").define("statusNone",      "§7(no overrides)");
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

        BUILDER.push("Schedule Display");

        SCHEDULE_HEADER = BUILDER
                .comment("Header line of /schedule.")
                .define("scheduleHeader", "§8§m----------------------------------");
        SCHEDULE_STATUS_OPEN = BUILDER
                .comment("Status shown when server is open.")
                .define("scheduleStatusOpen", "§a§lOPEN");
        SCHEDULE_STATUS_CLOSED = BUILDER
                .comment("Status shown when server is closed.")
                .define("scheduleStatusClosed", "§c§lCLOSED");
        SCHEDULE_DAY_OPEN_FORMAT = BUILDER
                .comment("Format for an open day. Placeholders: {day} {open} {close}")
                .define("scheduleDayOpenFormat", "{day} : §a{open}§7 → §c{close}");
        SCHEDULE_DAY_CLOSED_FORMAT = BUILDER
                .comment("Format for a closed day. Placeholder: {day}")
                .define("scheduleDayClosedFormat", "{day} : §cClosed");
        SCHEDULE_DAY_TODAY_PREFIX = BUILDER
                .comment("Prefix for the current day.")
                .define("scheduleDayTodayPrefix", "§e▶ ");
        SCHEDULE_DAY_OTHER_PREFIX = BUILDER
                .comment("Prefix for other days.")
                .define("scheduleDayOtherPrefix", "  §7");
        SCHEDULE_FOOTER = BUILDER
                .comment("Footer line of /schedule.")
                .define("scheduleFooter", "§8§m----------------------------------");

        SCHEDULE_DAY_MONDAY    = BUILDER.comment("Day name for Monday.")   .define("dayMonday",    "Monday");
        SCHEDULE_DAY_TUESDAY   = BUILDER.comment("Day name for Tuesday.")  .define("dayTuesday",   "Tuesday");
        SCHEDULE_DAY_WEDNESDAY = BUILDER.comment("Day name for Wednesday.").define("dayWednesday", "Wednesday");
        SCHEDULE_DAY_THURSDAY  = BUILDER.comment("Day name for Thursday.") .define("dayThursday",  "Thursday");
        SCHEDULE_DAY_FRIDAY    = BUILDER.comment("Day name for Friday.")   .define("dayFriday",    "Friday");
        SCHEDULE_DAY_SATURDAY  = BUILDER.comment("Day name for Saturday.") .define("daySaturday",  "Saturday");
        SCHEDULE_DAY_SUNDAY    = BUILDER.comment("Day name for Sunday.")   .define("daySunday",    "Sunday");

        SCHEDULE_DEATH_HOURS_LABEL = BUILDER
                .comment("Label for the Death Hours section.")
                .define("scheduleDeathHoursLabel", "§c☠ Death Hours");
        SCHEDULE_DEATH_HOURS_ACTIVE = BUILDER
                .comment("Shown when death hours are active now.")
                .define("scheduleDeathHoursActive", "§cACTIVE");
        SCHEDULE_DEATH_HOURS_INACTIVE = BUILDER
                .comment("Shown when death hours are not active now. Placeholder: {slots}")
                .define("scheduleDeathHoursInactive", "§7{slots}");
        SCHEDULE_HRP_LABEL = BUILDER
                .comment("Label for the HRP Hours section.")
                .define("scheduleHrpLabel", "§6✦ HRP Hours");
        SCHEDULE_HRP_TOLERATED = BUILDER
                .comment("Shown when HRP is currently tolerated.")
                .define("scheduleHrpTolerated", "§6TOLERATED");
        SCHEDULE_HRP_ALLOWED = BUILDER
                .comment("Shown when HRP is currently allowed.")
                .define("scheduleHrpAllowed", "§aALLOWED");
        SCHEDULE_HRP_INACTIVE = BUILDER
                .comment("Shown when HRP hours are not active now. Placeholder: {slots}")
                .define("scheduleHrpInactive", "§7{slots}");
        SCHEDULE_DEATH_HOURS_NOTIFY = BUILDER
                .comment("Message broadcast to all players when death hours begin.",
                        "Placeholder: {slots}")
                .define("scheduleDeathHoursNotify",
                        "§c☠ [Death Hours] RP death is now active until: {slots}. Be careful.");
        SCHEDULE_DEATH_HOURS_NOTIFY_MODE = BUILDER
                .comment("Display mode for the death hours notification:",
                        "  CHAT       : chat message",
                        "  ACTION_BAR : above the hotbar",
                        "  TITLE      : big title in the middle of the screen",
                        "  IMMERSIVE  : animated text with the 'default' preset",
                        "  IMMERSIVE:<preset> : animated text with a preset of immersivePresets (rpessentials-rp.toml), e.g. IMMERSIVE:death")
                .define("scheduleDeathHoursNotifyMode", "CHAT");

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
                .define("unknownProfession", "§c[RpEssentials] Unknown profession: {profession}");

        LICENSE_GIVE_STAFF = BUILDER
                .comment("Staff feedback after issuing a permanent license.",
                        "Placeholders: {profession} = formatted profession name, {player} = recipient name.")
                .define("giveStaff", "§a[RpEssentials] License for {profession} §aissued to §f{player}");

        LICENSE_GIVE_PLAYER = BUILDER
                .comment("Notification sent to the player when they receive a permanent license.",
                        "Placeholder: {profession} = formatted profession name.")
                .define("givePlayer", "§aYou received a {profession}§6§l License§a!");

        LICENSE_GIVE_RP_STAFF = BUILDER
                .comment("Staff feedback after issuing an RP (temporary) license.",
                        "Placeholders: {profession}, {player}, {days}, {date} = expiration date.")
                .define("giveRpStaff",
                        "§a[RpEssentials] Temporary license for {profession} §aissued to §f{player} §7({days} days, expires {date})");

        LICENSE_GIVE_RP_PLAYER = BUILDER
                .comment("Notification sent to the player when they receive an RP license.",
                        "Placeholders: {profession}, {date} = expiration date.")
                .define("giveRpPlayer", "§aYou received a {profession}§6§l License §7valid until §f{date}");

        LICENSE_REVOKE_STAFF = BUILDER
                .comment("Staff feedback after revoking a license.",
                        "Placeholders: {profession} = profession display name, {player} = player name.")
                .define("revokeStaff", "§a[RpEssentials] License for §f{profession} §arevoked for §f{player}");

        LICENSE_REVOKE_PLAYER = BUILDER
                .comment("Notification sent to the player when their license is revoked.",
                        "Placeholder: {profession} = profession display name.")
                .define("revokePlayer", "§cYour §f{profession} §clicense has been revoked.");

        LICENSE_LIST_NONE = BUILDER
                .comment("Message shown when a player has no licenses.",
                        "Placeholder: {player} = player name.")
                .define("listNone", "§e[RpEssentials] §f{player} §ehas no licenses.");

        LICENSE_LIST_HEADER = BUILDER
                .comment("Header title of the license list box.",
                        "Placeholder: {player} = player name.")
                .define("listHeader", "§6§lLicenses §8- §f{player}");

        LICENSE_LIST_RP_EXPIRY = BUILDER
                .comment("Suffix appended to a license entry when it is an RP (temporary) license.",
                        "Placeholder: {date} = expiration date.")
                .define("listRpExpiry", " §7(RP - expires {date})");

        LICENSE_LIST_ALL_NONE = BUILDER
                .comment("Message shown when no licenses are registered at all.")
                .define("listAllNone", "§e[RpEssentials] No licenses registered.");

        LICENSE_LIST_ALL_HEADER = BUILDER
                .comment("Header title line of the all-players license list.")
                .define("listAllHeader", "§6§lLicenses §8- §fall players");

        LICENSE_LIST_ALL_NONE_FOR_PLAYER = BUILDER
                .comment("Shown in the all-players list when a player has no active license.")
                .define("listAllNoneForPlayer", "§8None");

        LICENSE_REISSUE_STAFF = BUILDER
                .comment("Staff feedback after reissuing a license item.",
                        "Placeholders: {profession} = profession display name, {player} = player name.")
                .define("reissueStaff",
                        "§a[RpEssentials] Replacement license item for {profession} §agiven to §f{player}§a. Their license itself is unchanged.");

        LICENSE_REISSUE_PLAYER = BUILDER
                .comment("Notification sent to the player when a replacement license item is given.",
                        "Placeholder: {profession} = profession display name.")
                .define("reissuePlayer",
                        "§aYou received a replacement {profession}§6§l License §aitem.");

        LICENSE_REISSUE_NOT_FOUND = BUILDER
                .comment("Error shown when the player does not have the license to reissue.",
                        "Placeholders: {player} = player name, {profession} = profession id.")
                .define("reissueNotFound",
                        "§c[RpEssentials] §f{player} §cdoes not have the §f{profession} §clicense: nothing to replace. Use §f/rpessentials license give §cto grant it.");

        LICENSE_EXPIRED_RP_PLAYER = BUILDER
                .comment("Message sent to the player when their RP license expires.",
                        "Placeholder: {profession} = profession display name.")
                .define("expiredRpPlayer",
                        "§cYour §f{profession} §clicense has expired. The item remains in your inventory.");

        PROFILE_PREPARED = BUILDER.comment("Shown at login when staff prepared the profile of a new player.")
                .define("profilePrepared", "§a[RPEssentials] Your profile was prepared in advance by staff.");

        CONTAINER_FALLBACK = BUILDER.comment("Fallback when a container cannot be opened and the profession message is unavailable.")
                .define("containerBlockedFallback", "§c✘ You cannot open this block. §7Required profession.");


        RP_SELFNICK_SET = BUILDER
                .comment("Message shown when a player sets their own nickname. Placeholder: {nick}")
                .define("selfNickSet", "§aYour nickname has been set to: {nick}");
        RP_SELFNICK_RESET = BUILDER
                .comment("Message shown when a player resets their own nickname.")
                .define("selfNickReset", "§aYour nickname has been reset.");
        RP_SELFNICK_DISABLED = BUILDER
                .comment("Message shown when self-nick is disabled.")
                .define("selfNickDisabled", "§c[RP] Self-nick is disabled on this server.");
        RP_SELFNICK_COLORS_DISABLED = BUILDER
                .comment("Message shown when color codes are not allowed in self-nicks.")
                .define("selfNickColorsDisabled", "§c[RP] Color codes are not allowed in self-assigned nicknames.");
        RP_SELFNICK_TOO_LONG = BUILDER
                .comment("Message shown when the nickname exceeds the max length. Placeholder: {max}")
                .define("selfNickTooLong", "§c[RP] Nickname too long (max {max} characters).");
        NICK_USED = BUILDER.comment("Shown when a nickname is already taken.")
                .define("nicknameUsed", "§c[RP] Nickname already used.");
        NICK_FORBIDDEN = BUILDER.comment("Shown when a nickname contains a forbidden word.")
                .define("nicknameForbidden", "§c[RP] Nickname not allowed.");

        BUILDER.pop();

        BUILDER.push("RP Commands");
        RP_PERMISSION_DENIED = BUILDER
                .comment("Message shown when a player lacks permission for an RP staff command.")
                .define("permissionDenied", "§c[ERROR] Permission denied.");

        RP_AFK_TELEPORT = BUILDER
                .comment("Message shown to the player when they use /rp afk.")
                .define("afkTeleport", "§b[INFO] Transferring to AFK zone.");

        AFK_COMBAT = BUILDER.comment("Shown when /rp afk is refused right after a fight.")
                .define("afkCombat", "§c[RP] You cannot do that right after a fight.");
        RP_SPAWN_TELEPORT = BUILDER.comment("Message shown to the player when they use /rp spawn.").define("spawnTeleport", "§b[INFO] Teleported to spawn.");

        RP_COMMERCE_FORMAT = BUILDER
                .comment("Format of the /rp commerce broadcast.",
                        "Placeholders: {player} = sender nickname, {message} = message content.")
                .define("commerceFormat", "§a[COMMERCE] §e{player} §f: {message}");

        RP_INCOGNITO_FORMAT = BUILDER
                .comment("Format of the /rp incognito broadcast seen by all players.",
                        "Placeholder: {message} = message content.")
                .define("incognitoFormat", "§8§l[INCOGNITO] §7{message}");

        RP_INCOGNITO_LOG = BUILDER
                .comment("Log line sent to staff members when /rp incognito is used.",
                        "Placeholders: {player} = real sender nickname, {message} = message content.")
                .define("incognitoLog", "§7[INCOGNITO-LOG] §e{player} §7: §f{message}");

        RP_ACTION_FORMAT = BUILDER
                .comment("Format of the /rp action and /me messages shown to nearby players.",
                        "Placeholders: {player} = sender nickname, {action} = action text.")
                .define("actionFormat", "§5* §e{player} §f{action} §5*");

        RP_ACTION_SPY = BUILDER
                .comment("Format of the spy log shown to staff outside action range.",
                        "Placeholders: {player} = sender nickname, {action} = action text.")
                .define("actionSpy", "§7[SPY] §e{player} §7(far) : §5* §f{action} §5*");

        RP_ANNONCE_TITLE = BUILDER
                .comment("Title text shown in the center of the screen for /rp annonce title.")
                .define("annonceTitleText", "§6§l[ANNONCE]");

        RP_ANNONCE_SUBTITLE = BUILDER
                .comment("Subtitle format for /rp annonce title.",
                        "Placeholder: {message} = announcement content.")
                .define("annonceSubtitleFormat", "§e{message}");

        RP_ANNONCE_CHAT_FORMAT = BUILDER
                .comment("Format of /rp annonce chat broadcast.",
                        "Placeholder: {message} = announcement content.")
                .define("annonceChatFormat", "§6§l[ANNONCE] §f{message}");

        RP_ANNONCE_SENT = BUILDER
                .comment("Feedback sent to the staff member after sending an announcement.")
                .define("annonceSent", "§a[INFO] Announcement sent.");

        BUILDER.pop();

        BUILDER.push("Roles");
        SETROLE_UNKNOWN = BUILDER
                .comment("Shown when the role ID is not found. Placeholder: {role}")
                .define("setroleUnknown", "§c[RpEssentials] Unknown role: {role}.");
        SETROLE_SUCCESS_STAFF = BUILDER
                .comment("Staff feedback on /setrole. Placeholders: {role} {player}")
                .define("setroleSuccessStaff", "§a✔ §aRole §e§l{role} §aassigned to §e§l{player}§a.");
        SETROLE_SUCCESS_PLAYER = BUILDER
                .comment("Message sent to the target. Placeholder: {role}")
                .define("setroleSuccessPlayer", "§6[RpEssentials] §fYour role has been updated: §e§l{role}§f.");
        BUILDER.pop();

        BUILDER.push("Schedule Commands");
        SCHEDULE_DAY_UPDATED = BUILDER
                .comment("Shown when a day schedule value is updated. Placeholders: {day} {type} {value}")
                .define("scheduleDayUpdated", "§a[RpEssentials] §e{day} {type} §aupdated: §f{value}");
        SCHEDULE_DAY_INVALID = BUILDER
                .comment("Shown when an invalid day is provided. Placeholder: {day}")
                .define("scheduleDayInvalid", "§c[RpEssentials] Invalid day: {day}");
        SCHEDULE_DAY_ENABLED = BUILDER
                .comment("Shown when a day is enabled or disabled. Placeholders: {day} {state}")
                .define("scheduleDayEnabled", "§a[RpEssentials] §e{day} §a{state}.");
        SCHEDULE_TIME_INVALID = BUILDER
                .comment("Shown when the time format is invalid.")
                .define("scheduleTimeInvalid", "§c[RpEssentials] Invalid format. Use HH:MM (e.g. 19:00).");
        AUTO_UNWHITELIST_STAFF_NOTIFY = BUILDER
                .comment("Staff notification when a player is auto-unwhitelisted. Placeholders: {player} {days}")
                .define("autoUnwhitelistStaffNotify", "§6[Auto-Unwhitelist] §e{player} §7removed ({days} days inactive). ");
        BUILDER.pop();

        BUILDER.push("Proximity Chat");
        PROXIMITY_CHAT_SPY_FORMAT = BUILDER
                .comment("Format shown to staff outside proximity range.",
                        "Placeholders: {player}, {msg}, {distance}")
                .define("proximityChatSpyFormat",
                        "§7[PROX-SPY] §e{player} §7(§f{distance}m§7)§7: {msg}");
        PROXIMITY_CHAT_SPY_ENABLED = BUILDER
                .comment("Message sent to staff when they enable proximity spy.")
                .define("proximityChatSpyEnabled", "§a[SPY] Proximity chat spy enabled.");
        PROXIMITY_CHAT_SPY_DISABLED = BUILDER
                .comment("Message sent to staff when they disable proximity spy.")
                .define("proximityChatSpyDisabled", "§c[SPY] Proximity chat spy disabled.");
        BUILDER.pop();

        BUILDER.push("Mute System");
        MUTE_RECEIVED = BUILDER
                .comment("Message sent to the muted player. Placeholders: {reason} {duration}")
                .define("muteReceived",
                        "§c[MUTE] You have been muted. Reason: §f{reason}§c. Duration: §f{duration}§c.");
        MUTE_EXPIRED = BUILDER
                .comment("Message sent when mute expires.")
                .define("muteExpired", "§a[MUTE] Your mute has expired. You can chat again.");
        MUTE_NOTIFY_ON_JOIN = BUILDER
                .comment("Shown to muted player on login. Placeholders: {reason} {expiry}")
                .define("muteNotifyOnJoin",
                        "§c[MUTE] You are currently muted. Reason: §f{reason}§c. Expires: §f{expiry}§c.");
        MUTE_BLOCKED_MESSAGE = BUILDER
                .comment("Shown when muted player tries to chat. Placeholder: {expiry}")
                .define("muteBlockedMessage", "§c[MUTE] You cannot chat. Mute expires: §f{expiry}§c.");
        MUTE_STAFF_ADDED = BUILDER
                .comment("Staff feedback when muting. Placeholders: {player} {duration} {reason}")
                .define("muteStaffAdded",
                        "§a[MUTE] §e{player} §amuted for §f{duration}§a. Reason: §f{reason}");
        MUTE_STAFF_REMOVED = BUILDER
                .comment("Staff feedback when unmuting. Placeholder: {player}")
                .define("muteStaffRemoved", "§a[MUTE] §e{player} §ahas been unmuted.");
        MUTE_AUTO_NOTIFY = BUILDER
                .comment("Staff broadcast when auto-mute triggers. Placeholders: {player} {count} {duration}")
                .define("muteAutoNotify",
                        "§6[AUTO-MUTE] §e{player} §7auto-muted ({count} active warns, {duration}).");
        BUILDER.pop();

        BUILDER.push("Staff Notes");
        NOTE_ADDED = BUILDER
                .comment("Feedback when a note is added. Placeholders: {player} {id}")
                .define("noteAdded", "§a[NOTE] Note §e#{id} §aadded for §e{player}§a.");
        NOTE_REMOVED = BUILDER
                .comment("Feedback when a note is removed. Placeholders: {player} {id}")
                .define("noteRemoved", "§a[NOTE] Note §e#{id} §aremoved for §e{player}§a.");
        NOTE_NONE = BUILDER
                .comment("Shown when player has no notes. Placeholder: {player}")
                .define("noteNone", "§7[NOTE] No notes for §e{player}§7.");
        NOTE_LIST_HEADER = BUILDER
                .comment("Header of the note list. Placeholder: {player}")
                .define("noteListHeader", "§6Notes for §e{player}");
        BUILDER.pop();

        BUILDER.push("Death RP History");
        DEATHRP_HISTORY_HEADER = BUILDER
                .comment("Header of the death history. Placeholder: {player}")
                .define("deathHistoryHeader", "§6Death RP History, §e{player}");
        DEATHRP_HISTORY_NONE = BUILDER
                .comment("Shown when player has no death history. Placeholder: {player}")
                .define("deathHistoryNone", "§7No death RP recorded for §e{player}§7.");
        DEATHRP_HISTORY_ENTRY = BUILDER
                .comment("Format of each history entry. Placeholders: {index} {date} {cause}")
                .define("deathHistoryEntry", "§e#{index} §7: §f{date} §7: {cause}");
        BUILDER.pop();

        BUILDER.push("RP Cooldowns");
        RP_COOLDOWN_MESSAGE = BUILDER
                .comment("Shown when a player is on cooldown. Placeholders: {command} {seconds}")
                .define("rpCooldownMessage",
                        "§c[RP] Please wait §e{seconds}s §cbefore using §f/{command} §cagain.");
        BUILDER.pop();

        BUILDER.push("Stats");
        STATS_HEADER = BUILDER
                .comment("Header of the /rpessentials stats command.")
                .define("statsHeader", "§e§lRP Essentials Stats");
        BUILDER.pop();

        BUILDER.push("Dice");
        DICE_ROLL_FORMAT = BUILDER
                .comment("Format of the dice roll broadcast. Placeholders: {player} {dice} {result}")
                .define("diceRollFormat",
                        "§8[🎲] §e{player} §7rolled §6{dice} §7and got §a§l{result}§7!");
        DICE_ROLL_SPY_FORMAT = BUILDER
                .comment("Format shown to staff outside dice range. Placeholders: {player} {dice} {result}")
                .define("diceRollSpyFormat",
                        "§7[DICE-SPY] §e{player} §7rolled §6{dice}§7: §f{result}");
        DICE_DISABLED = BUILDER.comment("Shown when the dice system is disabled.")
                .define("diceDisabled", "§c[DICE] Dice system is disabled.");
        DICE_NONE = BUILDER.comment("Shown when no dice is configured.")
                .define("diceNone", "§c[DICE] No dice configured.");
        DICE_UNKNOWN = BUILDER.comment("Shown for an unknown dice. Placeholder: {dice}")
                .define("diceUnknown", "§c[DICE] Unknown dice type: §f{dice}");
        BUILDER.pop();

        BUILDER.push("Staff RP Commands");
        STAFF_RP_SPAWN_DIM = BUILDER.comment("/rp spawn: dimension missing. Placeholder: {dim}").define("spawnDimensionNotFound", "§c[ERROR] Spawn dimension not found: {dim}");
        STAFF_RP_AFK_DIM = BUILDER.comment("/rp afk: dimension missing. Placeholder: {dim}").define("afkDimensionNotFound", "§c[ERROR] AFK dimension not found: {dim}");
        STAFF_RP_ANNONCE_TYPE = BUILDER.comment("/rp annonce: unknown type. Placeholder: {type}").define("announceUnknownType", "§c[ERROR] Unknown type: {type}. Use 'title' or 'chat'.");
        BUILDER.pop();

        BUILDER.push("Staff Licenses");
        STAFF_LIC_ALREADY = BUILDER.comment("license give: already owned. Placeholders: {player} {profession}").define("alreadyHasLicense", "§c[RpEssentials] §f{player} §calready has the §f{profession} §clicense. Use §f/rpessentials license reissue §cfor a replacement item.");
        STAFF_LIC_ALREADY_TEMP = BUILDER.comment("license tempgive: already owned. Placeholders: {player} {profession}").define("alreadyHasLicenseTemp", "§c[RpEssentials] §f{player} §calready has the §f{profession} §clicense.");
        STAFF_LIC_CREATE_FAILED = BUILDER.comment("license reissue: item creation failed. Placeholder: {profession}").define("createItemFailed", "§c[RpEssentials] Could not create item for: {profession}");
        STAFF_LIC_AUDIT_NONE = BUILDER.comment("license audit: no entry.").define("auditNoEntries", "§7[Audit] No entries found.");
        BUILDER.pop();

        BUILDER.push("Staff Tools");
        STAFF_TOOLS_SILENT_OFF = BUILDER.comment("Silent commands disabled.").define("silentCommandsDisabled", "§cSilent commands are disabled.");
        STAFF_TOOLS_BAD_GAMEMODE = BUILDER.comment("Invalid gamemode.").define("invalidGamemode", "§cInvalid gamemode.");
        STAFF_TOOLS_GAMEMODE_SET = BUILDER.comment("Sent to the target when notifyTarget is on. Placeholder: {mode}").define("gamemodeChanged", "§7[Staff] Your gamemode has been changed to {mode}");
        STAFF_TOOLS_EFFECT_APPLIED = BUILDER.comment("Sent to the target when notifyTarget is on.").define("effectApplied", "§7[Staff] An effect has been applied to you.");
        STAFF_TOOLS_BAD_EFFECT = BUILDER.comment("Invalid effect.").define("invalidEffect", "§cInvalid effect.");
        STAFF_TOOLS_PLATFORMS_OFF = BUILDER.comment("Platforms disabled.").define("platformsDisabled", "§cPlatforms system is disabled.");
        STAFF_TOOLS_DIM_MISSING = BUILDER.comment("Platform dimension missing. Placeholder: {dim}").define("dimensionNotFound", "§cDimension not found: {dim}");
        STAFF_TOOLS_TP_TARGET = BUILDER.comment("Sent to a player sent to a platform. Placeholder: {name}").define("teleportedTarget", "§e[Staff] You have been teleported to: {name}");
        STAFF_TOOLS_TP_SELF = BUILDER.comment("Sent to the staff member who teleports. Placeholder: {name}").define("teleportedSelf", "§aTeleported to: {name}");
        STAFF_TOOLS_PLATFORM_MISSING = BUILDER.comment("Unknown platform. Placeholder: {platform}").define("platformNotFound", "§cPlatform not found: {platform}");
        STAFF_TOOLS_PLATFORM_UPDATED = BUILDER.comment("Placeholders: {name} {dim} {x} {y} {z}").define("platformUpdated", "§a[RpEssentials] Platform '{name}' updated at {dim} {x} {y} {z}");
        STAFF_TOOLS_PLATFORM_CREATED = BUILDER.comment("Placeholders: {name} {dim} {x} {y} {z}").define("platformCreated", "§a[RpEssentials] Platform '{name}' created at {dim} {x} {y} {z}");
        STAFF_TOOLS_BROADCAST_FORMAT = BUILDER.comment("Staff broadcast format. Placeholders: {staff} {message}").define("broadcastFormat", "§8[STAFF] §e{staff}§7: §f{message}");
        STAFF_TOOLS_BROADCAST_SENT = BUILDER.comment("Feedback after a staff broadcast. Placeholder: {count}").define("broadcastSent", "§a[STAFF] Broadcast sent to §e{count} §astaff member(s).");
        BUILDER.pop();

        BUILDER.push("Staff Zones");
        STAFF_ZONE_NONE = BUILDER.comment("No named zone configured.").define("noZone", "§7[Zone] No named zone configured.");
        STAFF_ZONE_LIST = BUILDER.comment("Zone list. Placeholder: {zones}").define("zoneList", "§6[Zone] §f{zones}");
        STAFF_ZONE_HIDDEN = BUILDER.comment("Zone display stopped.").define("displayStopped", "§7[Zone] Display stopped.");
        STAFF_ZONE_UNKNOWN = BUILDER.comment("Unknown zone. Placeholder: {name}").define("unknownZone", "§c[Zone] Unknown zone: {name}");
        STAFF_ZONE_SHOWING = BUILDER.comment("Zone display started. Placeholder: {name}").define("showing", "§a[Zone] Showing §e{name}§a for 2 minutes. Use /rpessentials zone hide to stop.");
        BUILDER.pop();

        BUILDER.push("Staff Vanish");
        STAFF_VANISH_OFF = BUILDER.comment("Vanish disabled.").define("disabled", "§c[Vanish] Vanish is disabled in the config.");
        STAFF_VANISH_ABOVE = BUILDER.comment("Hidden above your level. Placeholder: {player}").define("aboveLevel", "§c[Vanish] {player} is hidden above your OP level.");
        STAFF_VANISH_VISIBLE = BUILDER.comment("Placeholder: {player}").define("visible", "§a[Vanish] §f{player} §7is now visible.");
        STAFF_VANISH_VANISHED = BUILDER.comment("Placeholders: {player} {level}").define("vanished", "§a[Vanish] §f{player} §7is now vanished, hidden from OP level §e{level} §7and below.");
        STAFF_VANISH_LIST_HEADER = BUILDER.comment("Vanish list title.").define("listHeader", "§6Vanished players");
        STAFF_VANISH_LIST_NONE = BUILDER.comment("Vanish list empty.").define("listNone", "§8None");
        STAFF_VANISH_LIST_ENTRY = BUILDER.comment("Vanish list line. Placeholders: {player} {level}").define("listEntry", "§f{player} §8| §7level §e{level}");
        BUILDER.pop();

        BUILDER.push("Staff BlurTab");
        STAFF_BLUR_ALREADY = BUILDER.comment("Placeholders: {player} {list}").define("alreadyInList", "§c[RpEssentials] {player} is already in {list}.");
        STAFF_BLUR_ADDED = BUILDER.comment("Placeholders: {player} {list}").define("added", "§a[RpEssentials] {player} added to {list}.");
        STAFF_BLUR_NOT_IN = BUILDER.comment("Placeholders: {player} {list}").define("notInList", "§c[RpEssentials] {player} is not in {list}.");
        STAFF_BLUR_REMOVED = BUILDER.comment("Placeholders: {player} {list}").define("removed", "§a[RpEssentials] {player} removed from {list}.");
        STAFF_BLUR_EMPTY = BUILDER.comment("Placeholder: {label}").define("listEmpty", "§e[RpEssentials] {label} is empty.");
        STAFF_BLUR_CONTENT = BUILDER.comment("Placeholders: {label} {list}").define("listContent", "§e[RpEssentials] {label}: §f{list}");
        BUILDER.pop();

        BUILDER.push("Staff Manage");
        MANAGE_NEED_SENSITIVE = BUILDER.comment("Action reserved to the opLevelBypass level.").define("needSensitiveLevel", "§c[Manage] This action requires the OP level set in opLevelBypass.");
        MANAGE_NOT_ONLINE = BUILDER.comment("Placeholder: {player}").define("notOnline", "§c[Manage] {player} is not online.");
        MANAGE_WL_ADDED = BUILDER.comment("Placeholder: {player}").define("whitelistAdded", "§a[Manage] {player} added to the whitelist.");
        MANAGE_WL_REMOVED = BUILDER.comment("Placeholder: {player}").define("whitelistRemoved", "§a[Manage] {player} removed from the whitelist.");
        MANAGE_KICK_SELF = BUILDER.comment("Self kick refused.").define("cannotKickSelf", "§c[Manage] You cannot kick yourself.");
        MANAGE_KICKED = BUILDER.comment("Placeholder: {player}").define("kicked", "§a[Manage] {player} kicked.");
        MANAGE_BAN_SELF = BUILDER.comment("Self ban refused.").define("cannotBanSelf", "§c[Manage] You cannot ban yourself.");
        MANAGE_ALREADY_BANNED = BUILDER.comment("Placeholder: {player}").define("alreadyBanned", "§c[Manage] {player} is already banned.");
        MANAGE_BANNED = BUILDER.comment("Placeholder: {player}").define("banned", "§a[Manage] {player} banned.");
        MANAGE_NOT_BANNED = BUILDER.comment("Placeholder: {player}").define("notBanned", "§c[Manage] {player} is not banned.");
        MANAGE_PARDONED = BUILDER.comment("Placeholder: {player}").define("pardoned", "§a[Manage] {player} pardoned.");
        MANAGE_OP_TOO_HIGH = BUILDER.comment("OP level above the executor.").define("opLevelTooHigh", "§c[Manage] You cannot grant a level higher than your own.");
        MANAGE_OP_LOWER_SELF = BUILDER.comment("Lowering own OP level refused.").define("opLowerSelf", "§c[Manage] You cannot lower your own OP level.");
        MANAGE_OP_SET = BUILDER.comment("Placeholders: {player} {level}").define("opSet", "§a[Manage] {player} is now OP level {level}.");
        MANAGE_DEOP_SELF = BUILDER.comment("Self deop refused.").define("cannotDeopSelf", "§c[Manage] You cannot deop yourself.");
        MANAGE_NOT_OP = BUILDER.comment("Placeholder: {player}").define("notOperator", "§c[Manage] {player} is not an operator.");
        MANAGE_DEOP_HIGHER = BUILDER.comment("Deop of a higher operator refused.").define("deopHigherOperator", "§c[Manage] You cannot deop a higher level operator.");
        MANAGE_DEOPPED = BUILDER.comment("Placeholder: {player}").define("deopped", "§a[Manage] {player} is no longer an operator.");
        MANAGE_INV_CLEARED = BUILDER.comment("Placeholder: {player}").define("inventoryCleared", "§a[Manage] Inventory of {player} cleared.");
        MANAGE_ENDER_CLEARED = BUILDER.comment("Placeholder: {player}").define("enderChestCleared", "§a[Manage] Ender chest of {player} cleared.");
        MANAGE_ABSENCE_SET = BUILDER.comment("Placeholders: {player} {days}").define("absenceSet", "§a[Manage] Absence set for {player} ({days} days).");
        MANAGE_ABSENCE_REMOVED = BUILDER.comment("Placeholder: {player}").define("absenceRemoved", "§a[Manage] Absence of {player} removed.");
        MANAGE_NO_ABSENCE = BUILDER.comment("Placeholder: {player}").define("noAbsence", "§c[Manage] {player} has no absence.");
        MANAGE_NICK_CLEARED = BUILDER.comment("Placeholder: {player}").define("nicknameCleared", "§a[Manage] Nickname of {player} cleared.");
        MANAGE_NO_NICK = BUILDER.comment("Placeholder: {player}").define("noNickname", "§c[Manage] {player} has no nickname.");
        MANAGE_PREP_ROLE_CLEARED = BUILDER.comment("Placeholder: {player}").define("preparedRoleCleared", "§a[Manage] Prepared role of {player} cleared.");
        MANAGE_ROLE_CLEARED = BUILDER.comment("Placeholder: {player}").define("roleCleared", "§a[Manage] Role of {player} cleared.");
        MANAGE_ROLES_UNAVAILABLE = BUILDER.comment("Roles config not loaded.").define("rolesUnavailable", "§c[Manage] Roles config unavailable.");
        MANAGE_PROF_CLEARED = BUILDER.comment("Placeholders: {count} {player}").define("professionsCleared", "§a[Manage] {count} profession(s) cleared for {player}.");
        MANAGE_NO_PROF = BUILDER.comment("Placeholder: {player}").define("noProfession", "§c[Manage] {player} has no profession.");
        MANAGE_RESET_SELF = BUILDER.comment("Self reset refused.").define("cannotResetSelf", "§c[Manage] You cannot reset yourself.");
        MANAGE_RESET_HIGHER = BUILDER.comment("Reset of a higher operator refused.").define("resetHigherOperator", "§c[Manage] You cannot reset a higher level operator.");
        MANAGE_RESET_DONE = BUILDER.comment("Placeholders: {player} {count}").define("resetDone", "§a[Manage] Full reset of {player}: {count} step(s) applied or queued.");
        MANAGE_TP_ONLY_PLAYER = BUILDER.comment("Console cannot teleport.").define("onlyPlayerTeleport", "§c[Manage] Only a player can teleport.");
        MANAGE_TP_SELF = BUILDER.comment("Teleport to self refused.").define("cannotTeleportSelf", "§c[Manage] You cannot teleport to yourself.");
        MANAGE_TP_DONE = BUILDER.comment("Placeholder: {player}").define("teleportedToPlayer", "§a[Manage] Teleported to {player}.");
        MANAGE_BRING_ONLY_PLAYER = BUILDER.comment("Console cannot bring a player.").define("onlyPlayerBring", "§c[Manage] Only a player can use this action.");
        MANAGE_BRING_SELF = BUILDER.comment("Bring self refused.").define("cannotBringSelf", "§c[Manage] You cannot teleport yourself to yourself.");
        MANAGE_BRING_DONE = BUILDER.comment("Placeholder: {player}").define("broughtToYou", "§a[Manage] {player} teleported to you.");
        MANAGE_PLATFORM_UNKNOWN = BUILDER.comment("Placeholder: {platform}").define("unknownPlatform", "§c[Manage] Unknown platform: {platform}");
        MANAGE_PLATFORM_SENT = BUILDER.comment("Placeholders: {player} {platform}").define("sentToPlatform", "§a[Manage] {player} sent to {platform}.");
        MANAGE_AFK_MISSING = BUILDER.comment("AFK dimension missing.").define("afkDimensionNotFound", "§c[Manage] AFK dimension not found.");
        MANAGE_AFK_SENT = BUILDER.comment("Placeholder: {player}").define("sentToAfk", "§a[Manage] {player} sent to the AFK zone.");
        MANAGE_VANISH_OFF = BUILDER.comment("Vanish disabled.").define("vanishDisabled", "§c[Manage] Vanish is disabled in the config.");
        MANAGE_VANISH_ABOVE_OWN = BUILDER.comment("Vanish level above own level.").define("vanishAboveOwnLevel", "§c[Manage] You cannot vanish above your own OP level.");
        MANAGE_HIDDEN_ABOVE = BUILDER.comment("Placeholder: {player}").define("hiddenAboveYourLevel", "§c[Manage] {player} is hidden above your OP level.");
        MANAGE_VANISHED = BUILDER.comment("Placeholders: {player} {level}").define("nowVanished", "§a[Manage] {player} is now vanished (level {level}).");
        MANAGE_NOT_VANISHED = BUILDER.comment("Placeholder: {player}").define("notVanished", "§c[Manage] {player} is not vanished.");
        MANAGE_VISIBLE = BUILDER.comment("Placeholder: {player}").define("nowVisible", "§a[Manage] {player} is now visible.");
        MANAGE_QUEUED = BUILDER.comment("Offline player. Placeholders: {player} {action}").define("queued", "§a[Manage] {player} is offline: {action} queued until the next login.");
        MANAGE_ALERT = BUILDER.comment("Alert to other staff and console. Placeholders: {admin} {action} {player} {reason}").define("staffAlert", "§6[Manage] §e{admin} §7: {action} on {player}{reason}");
        MANAGE_CANCELLED = BUILDER.comment("Pending action cancelled.").define("cancelled", "§7[Manage] Cancelled.");
        MANAGE_UNKNOWN_ACTION = BUILDER.comment("Unknown action.").define("unknownAction", "§c[Manage] Unknown action.");
        MANAGE_PLAYER_NOT_FOUND = BUILDER.comment("Placeholder: {player}").define("playerNotFound", "§c[Manage] Player not found: {player}");
        MANAGE_NUMBER_EXPECTED = BUILDER.comment("A number was expected.").define("numberExpected", "§c[Manage] A number is expected.");
        MANAGE_RESET_NEEDS_NAME = BUILDER.comment("Placeholder: {player}").define("resetNeedsName", "§c[Manage] Full reset requires the player name as confirmation: /rpessentials manage {player} reset_all {player}");
        MANAGE_RESET_PROMPT = BUILDER.comment("Full reset confirmation. Placeholder: {player}").define("resetPrompt", "§c§l[Manage] FULL RESET of §e§l{player}§c§l: inventory, ender chest, role, professions, nickname, absence, OP and whitelist. ");
        MANAGE_CONFIRM_PROMPT = BUILDER.comment("Confirmation prompt. Placeholders: {action} {player} {detail}").define("confirmPrompt", "§6[Manage] §7Confirm §f{action} §7on §e{player}{detail}§7? ");
        MANAGE_BTN_CONFIRM_RESET = BUILDER.comment("Clickable button.").define("buttonConfirmReset", "§c§l[CONFIRM FULL RESET]");
        MANAGE_BTN_CANCEL_GREY = BUILDER.comment("Clickable button.").define("buttonCancelGrey", "§7[Cancel]");
        MANAGE_BTN_CONFIRM = BUILDER.comment("Clickable button.").define("buttonConfirm", "§a[Confirm]");
        MANAGE_BTN_CANCEL = BUILDER.comment("Clickable button.").define("buttonCancel", "§c[Cancel]");
        MANAGE_NOTHING = BUILDER.comment("Nothing to confirm.").define("nothingToConfirm", "§c[Manage] Nothing to confirm.");
        MANAGE_SUG_HEAD_WL = BUILDER.comment("Placeholder: {player}").define("suggestHeaderUnwhitelisted", "§6[Manage] §e{player} §7has been removed from the whitelist. Quick actions:");
        MANAGE_SUG_HEAD_DEOP = BUILDER.comment("Placeholder: {player}").define("suggestHeaderDeopped", "§6[Manage] §e{player} §7is no longer an operator. Quick actions:");
        MANAGE_SUG_HEAD_BAN = BUILDER.comment("Placeholder: {player}").define("suggestHeaderBanned", "§6[Manage] §e{player} §7has been banned. Quick actions:");
        MANAGE_SUG_L_REWL = BUILDER.comment("Clickable label.").define("suggestLabelReWhitelist", "§e[Re-whitelist]");
        MANAGE_SUG_H_REWL = BUILDER.comment("Hover text.").define("suggestHoverReWhitelist", "§7Add back to the whitelist");
        MANAGE_SUG_L_BAN = BUILDER.comment("Clickable label.").define("suggestLabelBan", "§e[Ban]");
        MANAGE_SUG_H_BAN = BUILDER.comment("Hover text.").define("suggestHoverBan", "§7Ban (type a reason)");
        MANAGE_SUG_L_CLRINV = BUILDER.comment("Clickable label.").define("suggestLabelClearInventory", "§e[Clear inventory]");
        MANAGE_SUG_H_CLRINV = BUILDER.comment("Hover text.").define("suggestHoverClearInventory", "§7Clear the inventory (online only)");
        MANAGE_SUG_L_ABS = BUILDER.comment("Clickable label.").define("suggestLabelAbsence", "§e[Absence]");
        MANAGE_SUG_H_ABS = BUILDER.comment("Hover text.").define("suggestHoverAbsence", "§7Declare an absence: days then reason");
        MANAGE_SUG_L_REOP = BUILDER.comment("Clickable label.").define("suggestLabelReOp", "§e[Re-op]");
        MANAGE_SUG_H_REOP = BUILDER.comment("Hover text.").define("suggestHoverReOp", "§7Grant OP (type the level 1 to 4)");
        MANAGE_SUG_L_KICK = BUILDER.comment("Clickable label.").define("suggestLabelKick", "§e[Kick]");
        MANAGE_SUG_H_KICK = BUILDER.comment("Hover text.").define("suggestHoverKick", "§7Kick (type a reason)");
        MANAGE_SUG_L_PARDON = BUILDER.comment("Clickable label.").define("suggestLabelPardon", "§e[Pardon]");
        MANAGE_SUG_H_PARDON = BUILDER.comment("Hover text.").define("suggestHoverPardon", "§7Lift the ban");
        MANAGE_SUG_L_UNWL = BUILDER.comment("Clickable label.").define("suggestLabelUnwhitelist", "§e[Unwhitelist]");
        MANAGE_SUG_H_UNWL = BUILDER.comment("Hover text.").define("suggestHoverUnwhitelist", "§7Remove from the whitelist");
        BUILDER.pop();

        BUILDER.push("Staff Warns");
        STAFF_WARN_PLAYER_NOT_FOUND = BUILDER.comment("Placeholder: {player}").define("playerNotFound", "§c[RpEssentials] Player not found: {player}");
        STAFF_WARN_ADDED = BUILDER.comment("Placeholders: {id} {player}").define("added", "§a[RpEssentials] Warn §e#{id}§a added for §e{player}§a.");
        STAFF_WARN_TEMP_ADDED = BUILDER.comment("Placeholders: {id} {player} {duration}").define("tempAdded", "§a[RpEssentials] Temp warn §e#{id}§a for §e{player}§a ({duration}).");
        STAFF_WARN_REMOVED = BUILDER.comment("Placeholder: {id}").define("removed", "§a[RpEssentials] Warn §e#{id}§a removed.");
        STAFF_WARN_NONE = BUILDER.comment("No warn at all.").define("noWarns", "§7[RpEssentials] No warns.");
        STAFF_WARN_ALL_HEADER = BUILDER.comment("Title of the full warn list. Placeholder: {count}").define("allHeader", "§6All warns §7({count})");
        STAFF_WARN_TAG_EXPIRED = BUILDER.comment("Tag of an expired warn in the full list.").define("tagExpired", "§8[EXP]");
        STAFF_WARN_TAG_PERM = BUILDER.comment("Tag of a permanent warn in the full list.").define("tagPermanent", "§c[PERM]");
        STAFF_WARN_TAG_TEMP = BUILDER.comment("Tag of a temporary warn in the full list.").define("tagTemporary", "§e[TEMP]");
        STAFF_WARN_ALL_ENTRY = BUILDER.comment("Line of the full warn list. Placeholders: {tag} {id} {player} {reason}").define("allEntry", "{tag} §e#{id} §7-> §f{player} §7- {reason}");
        STAFF_WARN_LIST_ENTRY = BUILDER.comment("Line of a player's warn list (two lines). Placeholders: {tag} {id} {date} {staff} {reason} {expiry}").define("listEntry", "{tag} §7#{id} §8({date}) §7by §f{staff}\n §7-> §f{reason} §8| {expiry}");
        STAFF_WARN_CLEARED = BUILDER.comment("Placeholders: {count} {player}").define("cleared", "§a[RpEssentials] §e{count}§a warn(s) removed for §e{player}§a.");
        BUILDER.pop();

        BUILDER.push("Staff Mutes");
        STAFF_MUTE_DISABLED = BUILDER.comment("Mute system disabled.").define("disabled", "§c[MUTE] Mute system disabled.");
        STAFF_MUTE_CONFIG = BUILDER.comment("Config not loaded.").define("configNotLoaded", "§c[MUTE] Config not loaded.");
        BUILDER.pop();

        BUILDER.push("Staff Notes Commands");
        STAFF_NOTE_LIMIT = BUILDER.comment("Placeholder: {player}").define("limitReached", "§c[NOTE] Note limit reached for §e{player}§c.");
        STAFF_NOTE_ENTRY = BUILDER.comment("Line of the note list (two lines). Placeholders: {id} {date} {author} {text}").define("listEntry", "§e#{id} §8[{date}§8] §7by §f{author}\n §7-> §f{text}");
        STAFF_NOTE_NOT_FOUND = BUILDER.comment("Placeholder: {id}").define("notFound", "§c[NOTE] Note #{id} not found.");
        STAFF_NOTE_CLEARED = BUILDER.comment("Placeholder: {player}").define("cleared", "§a[NOTE] All notes cleared for §e{player}");
        BUILDER.pop();

        BUILDER.push("Staff DeathRP");
        STAFF_DRP_NOT_FOUND = BUILDER.comment("Placeholder: {player}").define("playerNotFound", "§c[DeathRP] Player not found: {player}");
        STAFF_DRP_HOURS = BUILDER.comment("Status line. Placeholder: {value}").define("deathHours", "§7Death hours   : {value}");
        STAFF_DRP_HOURS_DISABLED = BUILDER.comment("Value when death hours are disabled.").define("hoursDisabled", "§8Disabled");
        STAFF_DRP_HOURS_SLOTS = BUILDER.comment("Slots after the death hours state. Placeholder: {slots}").define("hoursSlots", " §8({slots})");
        STAFF_DRP_EFFECTIVE = BUILDER.comment("Status line. Placeholder: {value}").define("effectiveNow", "§7Effective now : {value}");
        STAFF_DRP_OVERRIDE_ENTRY = BUILDER.comment("Line of the override list. Placeholders: {player} {value}").define("overrideEntry", " §e{player} §7-> {value}");
        STAFF_DRP_ALL_PLAYERS = BUILDER.comment("Name used in the history title when no player is given.").define("allPlayers", "All players");
        STAFF_DRP_HISTORY_ALL_ENTRY = BUILDER.comment("Line of the global history. Placeholders: {index} {player} {date} {cause}").define("historyAllEntry", "§e#{index} §f{player} §8: {date} §7: {cause}");
        BUILDER.pop();

        BUILDER.push("Staff Inspect");
        STAFF_INSPECT_NOT_FOUND = BUILDER.comment("Placeholder: {player}").define("playerNotFound", "§c[Inspect] Player not found: {player}");
        STAFF_INSPECT_HEADER = BUILDER.comment("Placeholder: {player}").define("header", "§6Inspect: §f{player}");
        STAFF_INSPECT_STATUS = BUILDER.comment("Placeholder: {status}").define("status", "§7Status   : {status}");
        STAFF_INSPECT_ONLINE = BUILDER.comment("Status value.").define("online", "§a● Online");
        STAFF_INSPECT_OFFLINE = BUILDER.comment("Status value.").define("offline", "§7○ Offline");
        STAFF_INSPECT_NICK = BUILDER.comment("Placeholder: {nickname}").define("nickname", "§7Nickname : {nickname}");
        STAFF_INSPECT_ROLE = BUILDER.comment("Placeholder: {role}").define("role", "§7Role     : {role}");
        STAFF_INSPECT_EMPTY = BUILDER.comment("Value when there is no nickname or role.").define("empty", "§8-");
        STAFF_INSPECT_UUID = BUILDER.comment("Placeholder: {uuid}").define("uuid", "§7UUID     : §8{uuid}");
        STAFF_INSPECT_UUID_HOVER = BUILDER.comment("Hover text of the UUID line.").define("uuidHover", "§7Click to open NameMC");
        STAFF_INSPECT_LICENSES = BUILDER.comment("Placeholders: {count} {list}").define("licenses", "§eLicenses §7({count})§7: {list}");
        STAFF_INSPECT_LICENSE_TEMP = BUILDER.comment("After a temporary license. Placeholder: {date}").define("licenseTemp", "§8(RP:{date})");
        STAFF_INSPECT_WARNS = BUILDER.comment("Placeholders: {count} {list}").define("warns", "§eWarns §7({count} active)§7: {list}");
        STAFF_INSPECT_WARN_ENTRY = BUILDER.comment("One warn. Placeholders: {id} {reason}").define("warnEntry", "§c#{id} §7{reason}");
        STAFF_INSPECT_MUTE = BUILDER.comment("Placeholder: {value}").define("mute", "§eMute     : {value}");
        STAFF_INSPECT_MUTE_ACTIVE = BUILDER.comment("Placeholders: {expiry} {reason}").define("muteActive", "§c{expiry} §7: §f{reason}");
        STAFF_INSPECT_NONE_GREY = BUILDER.comment("Empty license list.").define("noneGrey", "§8None");
        STAFF_INSPECT_NONE_GREEN = BUILDER.comment("No warn or no mute.").define("noneGreen", "§aNone");
        STAFF_INSPECT_LAST_SEEN = BUILDER.comment("Placeholder: {date}").define("lastSeen", "§eLast seen: §f{date}");
        STAFF_INSPECT_UNKNOWN = BUILDER.comment("Unknown last login.").define("unknown", "§8Unknown");
        STAFF_INSPECT_PLAYTIME = BUILDER.comment("Placeholders: {total} {session}").define("playtime", "§ePlaytime : §f{total}{session}");
        STAFF_INSPECT_SESSION = BUILDER.comment("Current session after the playtime. Placeholder: {session}").define("session", " §8(session: §7{session}§8)");
        STAFF_INSPECT_NOTES = BUILDER.comment("Placeholder: {count}").define("notes", "§eNotes §7({count}):");
        STAFF_INSPECT_NOTE_ENTRY = BUILDER.comment("Placeholders: {id} {author} {text}").define("noteEntry", " §8#{id} §7by §f{author}§7: §f{text}");
        BUILDER.pop();

        BUILDER.push("Staff Absences");
        STAFF_ABS_NOT_FOUND = BUILDER.comment("Placeholder: {player}").define("playerNotFound", "§c[Absence] Player not found: {player}");
        STAFF_ABS_NONE = BUILDER.comment("No absence.").define("none", "§7[Absence] No absences.");
        STAFF_ABS_HEADER = BUILDER.comment("Placeholder: {count}").define("header", "§6Absences §8({count})");
        STAFF_ABS_TAG_ENDED = BUILDER.comment("Tag of an ended absence.").define("tagEnded", "§8[END] ");
        STAFF_ABS_TAG_ACTIVE = BUILDER.comment("Tag of an active absence.").define("tagActive", "§a[ON] ");
        STAFF_ABS_ENTRY = BUILDER.comment("Placeholders: {tag} {player} {end} {reason}").define("entry", "{tag}§f{player} §7until §f{end}{reason}");
        STAFF_ABS_REASON = BUILDER.comment("Reason after an absence. Placeholder: {reason}").define("reason", " §8| §7{reason}");
        BUILDER.pop();

        BUILDER.push("Staff Schedule");
        STAFF_SCHED_TITLE = BUILDER.comment("Title of /schedule.").define("title", "Server Schedule");
        STAFF_SCHED_LABEL_STATUS = BUILDER.comment("Label.").define("labelStatus", "Status");
        STAFF_SCHED_LABEL_NEXT = BUILDER.comment("Label.").define("labelNext", "Next");
        STAFF_SCHED_SECTION_OPENING = BUILDER.comment("Section title.").define("sectionOpening", "Opening hours");
        STAFF_SCHED_SECTION_SPECIAL = BUILDER.comment("Section title.").define("sectionSpecial", "Special hours");
        STAFF_SCHED_FORCED_CLOSED = BUILDER.comment("Status when forcibly closed.").define("forcedClosed", "§c§lFORCIBLY CLOSED");
        STAFF_SCHED_FORCED_OPEN = BUILDER.comment("Status when forcibly open.").define("forcedOpen", "§a§lFORCIBLY OPEN");
        STAFF_SCHED_DISABLED = BUILDER.comment("Schedule disabled.").define("disabled", "§7Schedule disabled, the server is always open.");
        STAFF_SCHED_TOLERATED = BUILDER.comment("HRP slots when inactive. Placeholder: {slots}").define("toleratedSlots", "Tolerated: {slots}");
        STAFF_SCHED_SETROLE_ALERT = BUILDER.comment("Alert to other staff. Placeholders: {staff} {player} {role} {group}").define("setroleAlert", "§6[SETROLE] §e{staff} §7→ §e{player} §7: §f{role} §8(LP: {group})");
        STAFF_SCHED_STATE_ENABLED = BUILDER.comment("Day state word.").define("stateEnabled", "enabled");
        STAFF_SCHED_STATE_DISABLED = BUILDER.comment("Day state word.").define("stateDisabled", "disabled");
        STAFF_SCHED_FORCE_RESET = BUILDER.comment("Alert to staff. Placeholder: {staff}").define("forceReset", "§e[Schedule] Forced state cleared by §f{staff}§e. Schedule resumes normally.");
        STAFF_SCHED_FORCE_OPEN = BUILDER.comment("Alert to staff. Placeholder: {staff}").define("forceOpen", "§a[Schedule] Server forcibly opened by §e{staff}§a.");
        STAFF_SCHED_FORCE_CLOSE = BUILDER.comment("Alert to staff. Placeholders: {staff} {count}").define("forceClose", "§c[Schedule] Server forcibly closed by §e{staff}§c. §8({count} player(s) kicked)");
        STAFF_SCHED_WL_ALREADY = BUILDER.comment("Placeholder: {player}").define("whitelistAlready", "§c[Schedule] §f{player} §cis already in the schedule whitelist.");
        STAFF_SCHED_WL_ADDED = BUILDER.comment("Placeholder: {player}").define("whitelistAdded", "§a[Schedule] §f{player} §aadded to the schedule whitelist.");
        STAFF_SCHED_WL_NOT_IN = BUILDER.comment("Placeholder: {player}").define("whitelistNotIn", "§c[Schedule] §f{player} §cis not in the schedule whitelist.");
        STAFF_SCHED_WL_REMOVED = BUILDER.comment("Placeholder: {player}").define("whitelistRemoved", "§a[Schedule] §f{player} §aremoved from the schedule whitelist.");
        STAFF_SCHED_WL_EMPTY = BUILDER.comment("Empty schedule whitelist.").define("whitelistEmpty", "§e[Schedule] Schedule whitelist is empty.");
        STAFF_SCHED_WL_LIST = BUILDER.comment("Placeholders: {count} {list}").define("whitelistList", "§e[Schedule] Whitelist §8({count})§e: §f{list}");
        STAFF_SCHED_CONFIG = BUILDER.comment("Config not loaded.").define("configNotLoaded", "§c[Schedule] Config not loaded.");
        BUILDER.pop();

        BUILDER.push("Staff Nicknames");
        STAFF_NICK_COLORS_OFF = BUILDER.comment("/colors disabled.").define("colorsDisabled", "§cColors command is disabled.");
        STAFF_NICK_SET = BUILDER.comment("Placeholders: {player} {nick}").define("setDone", "§a[RpEssentials] Nickname for §f{player}§a set to: {nick}");
        STAFF_NICK_SET_TARGET = BUILDER.comment("Sent to the player. Placeholder: {nick}").define("setTarget", "§aYour nickname has been changed to: {nick}");
        STAFF_NICK_SET_ERROR = BUILDER.comment("Unexpected error.").define("setError", "§cError while setting nickname.");
        STAFF_NICK_RESET = BUILDER.comment("Placeholder: {player}").define("resetDone", "§a[RpEssentials] Nickname for §f{player}§a reset.");
        STAFF_NICK_RESET_TARGET = BUILDER.comment("Sent to the player.").define("resetTarget", "§aYour nickname has been reset.");
        STAFF_NICK_RESET_ERROR = BUILDER.comment("Unexpected error.").define("resetError", "§cError while resetting nickname.");
        STAFF_NICK_LIST_EMPTY = BUILDER.comment("nick list: no nickname.").define("listEmpty", "§e[RpEssentials] No active nicknames.");
        STAFF_NICK_LIST_HEADER = BUILDER.comment("Placeholders: {page} {pages} {total}").define("listHeader", "§6§lNicknames §8- §7page {page}/{pages} §8({total})");
        STAFF_NICK_WHOIS_HOVER = BUILDER.comment("Hover on the UUID.").define("whoisHover", "§7Click to open NameMC");
        STAFF_NICK_WHOIS_MC = BUILDER.comment("Start of a whois line. Placeholder: {player}").define("whoisMc", "§7MC: §f{player} §8| §7UUID: ");
        STAFF_NICK_WHOIS_NICK = BUILDER.comment("End of a whois line. Placeholder: {nick}").define("whoisNick", " §8| §7Nick: §r{nick}");
        BUILDER.pop();

        BUILDER.push("Staff Help");
        STAFF_HELP_TITLE = BUILDER.comment("Help title.").define("title", "§6§lRpEssentials §8— §7Commands");
        STAFF_HELP_PUBLIC = BUILDER.comment("Lines shown to every player, separated by \\n.").define("publicCommands", "§e/list: §7Online players with nicknames\n§e/schedule: §7Server schedule & status\n§e/msg §8<player> <msg>: §7Private message\n§e/r §8<msg>: §7Reply to last message\n§e/roll §8[dice]: §7Roll a dice\n§e/rp action §8<action>: §7RP action (/me)\n§e/rp commerce §8<msg>: §7Commerce broadcast\n§e/rp selfnick §8[nick]: §7Set your own nickname");
        STAFF_HELP_STAFF_TITLE = BUILDER.comment("Title of the staff part.").define("staffTitle", "§c§lStaff");
        STAFF_HELP_STAFF = BUILDER.comment("Lines shown to staff, separated by \\n.").define("staffCommands", "§e/rpessentials nick §8<player> [nick]: §7Set nickname\n§e/rpessentials license §8give/revoke/list: §7Licenses (jobs)\n§e/rpessentials warn §8add/temp/remove: §7Warns\n§e/rpessentials deathrp §8enable/player: §7Death RP\n§e/rpessentials config §8reload/status/set: §7Config\n§e/whois §8<nick>: §7Find player behind a nickname\n§e/setrole §8<player> <role>: §7Assign a role\n§e/rpessentials inspect §8<player>: §7Full player info\n§c For further information, check the mod page or the config!\n§e/vanish §8[player] [level]: §7Hide from the server");
        BUILDER.pop();

        BUILDER.push("Staff Tools Extra");
        STAFF_TOOLSX_OP_TOO_HIGH = BUILDER.comment("/rpessentials op: level above the executor.").define("opLevelTooHigh", "§c[RpEssentials] You cannot grant a level higher than your own.");
        STAFF_TOOLSX_OP_SET = BUILDER.comment("Placeholders: {player} {level}").define("opSet", "§a[RpEssentials] §f{player} §ais now OP level §e{level}");
        STAFF_TOOLSX_OP_NOTHING = BUILDER.comment("/rpessentials op: nothing changed.").define("opNothingChanged", "§c[RpEssentials] Nothing changed (already at this level or higher than you).");
        STAFF_TOOLSX_STAFF_LOG = BUILDER.comment("Staff log line. Placeholder: {message}").define("staffLog", "§7§o[StaffLog] {message}");
        BUILDER.pop();

        BUILDER.push("Staff Last Connection");
        STAFF_LASTCO_COPY_HOVER = BUILDER.comment("Hover on the player name. Placeholder: {uuid}").define("copyHover", "§7{uuid}\n§8Click to copy");
        BUILDER.pop();

        BUILDER.push("Staff Stats");
        STAFF_STATS_DISABLED = BUILDER.comment("Status word.").define("disabled", "§7Disabled");
        STAFF_STATS_OPEN = BUILDER.comment("Status word.").define("open", "§aOpen");
        STAFF_STATS_CLOSED = BUILDER.comment("Status word.").define("closed", "§cClosed");
        STAFF_STATS_NA = BUILDER.comment("Status word.").define("notAvailable", "§8N/A");
        STAFF_STATS_ON = BUILDER.comment("Status word.").define("on", "§aON");
        STAFF_STATS_OFF = BUILDER.comment("Status word.").define("off", "§cOFF");
        STAFF_STATS_S_SERVER = BUILDER.comment("Section title.").define("sectionServer", "§eServer");
        STAFF_STATS_S_PROFESSIONS = BUILDER.comment("Section title.").define("sectionProfessions", "§eProfessions");
        STAFF_STATS_S_MODERATION = BUILDER.comment("Section title.").define("sectionModeration", "§eModeration");
        STAFF_STATS_S_PLAYTIME = BUILDER.comment("Section title.").define("sectionMostPlaytime", "§eMost playtime");
        STAFF_STATS_S_RECENT = BUILDER.comment("Section title.").define("sectionRecentConnections", "§eRecent connections");
        STAFF_STATS_L_ONLINE = BUILDER.comment("Label.").define("labelOnline", "Online");
        STAFF_STATS_L_KNOWN = BUILDER.comment("Label.").define("labelKnown", "Known");
        STAFF_STATS_L_PLAYTIME = BUILDER.comment("Label.").define("labelPlaytime", "Playtime");
        STAFF_STATS_L_NICKNAMES = BUILDER.comment("Label.").define("labelNicknames", "Nicknames");
        STAFF_STATS_L_SCHEDULE = BUILDER.comment("Label.").define("labelSchedule", "Schedule");
        STAFF_STATS_L_HOLDERS = BUILDER.comment("Label.").define("labelHolders", "Holders");
        STAFF_STATS_L_EXPIRING = BUILDER.comment("Label.").define("labelExpiring", "Expiring in 7d");
        STAFF_STATS_L_WARNS = BUILDER.comment("Label.").define("labelActiveWarns", "Active warns");
        STAFF_STATS_L_MUTES = BUILDER.comment("Label.").define("labelActiveMutes", "Active mutes");
        STAFF_STATS_L_DEATHRP = BUILDER.comment("Label.").define("labelDeathRp", "Death RP");
        STAFF_STATS_L_OVERRIDES = BUILDER.comment("Label.").define("labelOverrides", "Overrides");
        STAFF_STATS_L_DEATHS = BUILDER.comment("Label.").define("labelDeaths", "Deaths recorded");
        STAFF_STATS_V_HOLDERS = BUILDER.comment("Placeholders: {count} {licenses}").define("valueHolders", "§f{count} §8({licenses} licenses)");
        STAFF_STATS_V_WARNS = BUILDER.comment("Placeholders: {count} {players}").define("valueWarns", "§f{count} §8({players} players)");
        BUILDER.pop();

        BUILDER.push("Staff Config");
        STAFF_CFG_BACKUP = BUILDER.comment("config backup.").define("backupStarted", "§a[RpEssentials] Backup started.");
        STAFF_CFG_EXPORT_DONE = BUILDER.comment("config exportlang. Placeholder: {path}").define("exportWritten", "§a[RpEssentials] Written: {path}");
        STAFF_CFG_EXPORT_FAIL = BUILDER.comment("Placeholder: {error}").define("exportFailed", "§c[RpEssentials] Export failed: {error}");
        STAFF_CFG_VALUE_SET = BUILDER.comment("config set. Placeholders: {name} {value}").define("valueSet", "§a[RpEssentials] {name} set to: {value}");
        STAFF_CFG_TOGGLE_SET = BUILDER.comment("config set (boolean). Placeholders: {name} {state}").define("toggleSet", "§a[RpEssentials] {name} : {state}");
        STAFF_CFG_ENABLED = BUILDER.comment("Value of {state}.").define("enabled", "§aENABLED");
        STAFF_CFG_DISABLED = BUILDER.comment("Value of {state}.").define("disabled", "§cDISABLED");
        STAFF_CFG_ZONE_MODES = BUILDER.comment("Invalid zone message mode.").define("zoneModesValid", "§c[RpEssentials] Valid modes: IMMERSIVE, CHAT, ACTION_BAR");
        STAFF_CFG_RELOADED = BUILDER.comment("config reload.").define("reloaded", "§a[RpEssentials] Configuration reloaded.");
        STAFF_CFG_VALID = BUILDER.comment("config validate.").define("valid", "§a[RpEssentials] Config valid: no problem found.");
        STAFF_CFG_PROBLEMS = BUILDER.comment("Placeholder: {count}").define("problemsHeader", "§6Config problems §8({count})");
        STAFF_CFG_STATUS_TITLE = BUILDER.comment("config status title.").define("statusTitle", "§6§lRpEssentials §8- §7Status");
        STAFF_CFG_DETECTED = BUILDER.comment("LuckPerms present.").define("detected", "§aDetected");
        STAFF_CFG_ABSENT = BUILDER.comment("LuckPerms missing.").define("absent", "§7Absent");
        STAFF_CFG_BLOCKS = BUILDER.comment("Unit after a distance.").define("blocksSuffix", "§7 blocks");
        STAFF_CFG_S_OBFUSCATION = BUILDER.comment("Section title.").define("sectionObfuscation", "§eObfuscation");
        STAFF_CFG_S_PERMISSIONS = BUILDER.comment("Section title.").define("sectionPermissions", "§ePermissions");
        STAFF_CFG_S_SCHEDULE = BUILDER.comment("Section title.").define("sectionScheduleDeathRp", "§eSchedule and Death RP");
        STAFF_CFG_S_CHAT = BUILDER.comment("Section title.").define("sectionChat", "§eChat");
        STAFF_CFG_S_BORDER = BUILDER.comment("Section title.").define("sectionWorldBorder", "§eWorld border");
        STAFF_CFG_S_MODERATION = BUILDER.comment("Section title.").define("sectionModeration", "§eModeration");
        STAFF_CFG_S_PROFESSIONS = BUILDER.comment("Section title.").define("sectionProfessionsRp", "§eProfessions and RP");
        STAFF_CFG_L_BLUR = BUILDER.comment("Label.").define("labelBlur", "Blur");
        STAFF_CFG_L_RANGE = BUILDER.comment("Label.").define("labelRange", "Range");
        STAFF_CFG_L_SNEAK = BUILDER.comment("Label.").define("labelSneak", "Sneak");
        STAFF_CFG_L_OPS_SEE_ALL = BUILDER.comment("Label.").define("labelOpsSeeAll", "Ops see all");
        STAFF_CFG_L_HIDE_NAMETAGS = BUILDER.comment("Label.").define("labelHideNametags", "Hide nametags");
        STAFF_CFG_L_WHITELIST = BUILDER.comment("Label.").define("labelWhitelist", "Whitelist");
        STAFF_CFG_L_BLACKLIST = BUILDER.comment("Label.").define("labelBlacklist", "Blacklist");
        STAFF_CFG_L_ALWAYS_VISIBLE = BUILDER.comment("Label.").define("labelAlwaysVisible", "Always visible");
        STAFF_CFG_L_LUCKPERMS = BUILDER.comment("Label.").define("labelLuckperms", "LuckPerms");
        STAFF_CFG_L_USE_GROUPS = BUILDER.comment("Label.").define("labelUseGroups", "Use groups");
        STAFF_CFG_L_OP_LEVEL = BUILDER.comment("Label.").define("labelOpLevel", "OP level");
        STAFF_CFG_L_ROLES = BUILDER.comment("Label.").define("labelRoles", "Roles");
        STAFF_CFG_L_SCHEDULE = BUILDER.comment("Label.").define("labelSchedule", "Schedule");
        STAFF_CFG_L_DEATH_RP = BUILDER.comment("Label.").define("labelDeathRp", "Death RP");
        STAFF_CFG_L_DEATH_HOURS = BUILDER.comment("Label.").define("labelDeathHours", "Death hours");
        STAFF_CFG_L_WHITELIST_REMOVAL = BUILDER.comment("Label.").define("labelWhitelistRemoval", "Whitelist removal");
        STAFF_CFG_L_HRP_HOURS = BUILDER.comment("Label.").define("labelHrpHours", "HRP hours");
        STAFF_CFG_L_FORMAT = BUILDER.comment("Label.").define("labelFormat", "Format");
        STAFF_CFG_L_PROXIMITY = BUILDER.comment("Label.").define("labelProximity", "Proximity");
        STAFF_CFG_L_MARKDOWN = BUILDER.comment("Label.").define("labelMarkdown", "Markdown");
        STAFF_CFG_L_TIMESTAMP = BUILDER.comment("Label.").define("labelTimestamp", "Timestamp");
        STAFF_CFG_L_JOIN_LEAVE = BUILDER.comment("Label.").define("labelJoinLeave", "Join/Leave");
        STAFF_CFG_L_COLOR = BUILDER.comment("Label.").define("labelColor", "Color");
        STAFF_CFG_L_ENABLED = BUILDER.comment("Label.").define("labelEnabled", "Enabled");
        STAFF_CFG_L_DISTANCE = BUILDER.comment("Label.").define("labelDistance", "Distance");
        STAFF_CFG_L_TELEPORT = BUILDER.comment("Label.").define("labelTeleport", "Teleport");
        STAFF_CFG_L_NAMED_ZONES = BUILDER.comment("Label.").define("labelNamedZones", "Named zones");
        STAFF_CFG_L_WARNS = BUILDER.comment("Label.").define("labelWarns", "Warns");
        STAFF_CFG_L_MUTES = BUILDER.comment("Label.").define("labelMutes", "Mutes");
        STAFF_CFG_L_LAST_CONNECTION = BUILDER.comment("Label.").define("labelLastConnection", "Last connection");
        STAFF_CFG_L_AUTO_UNWHITELIST = BUILDER.comment("Label.").define("labelAutoUnwhitelist", "Auto unwhitelist");
        STAFF_CFG_L_SILENT_COMMANDS = BUILDER.comment("Label.").define("labelSilentCommands", "Silent commands");
        STAFF_CFG_L_PLATFORMS = BUILDER.comment("Label.").define("labelPlatforms", "Platforms");
        STAFF_CFG_L_PROFESSIONS = BUILDER.comment("Label.").define("labelProfessions", "Professions");
        STAFF_CFG_L_BLOCKED_CRAFTS = BUILDER.comment("Label.").define("labelBlockedCrafts", "Blocked crafts");
        STAFF_CFG_L_BLOCKED_ITEMS = BUILDER.comment("Label.").define("labelBlockedItems", "Blocked items");
        STAFF_CFG_L_DICE = BUILDER.comment("Label.").define("labelDice", "Dice");
        STAFF_CFG_L_SELF_NICK = BUILDER.comment("Label.").define("labelSelfNick", "Self nick");
        STAFF_CFG_L_WELCOME = BUILDER.comment("Label.").define("labelWelcome", "Welcome");
        BUILDER.pop();

        BUILDER.push("GUI Feedback");
        GUI_MORE = BUILDER.comment("Shown when a GUI report is truncated. Placeholder: {count}").define("more", "§8... and {count} more");
        GUI_CONFIG_NOT_LOADED = BUILDER.comment("GUI save while the config is not loaded.").define("configNotLoaded", "§c[RpEssentials] Config not loaded, please try again.");
        GUI_ROLES_NEED_LEVEL = BUILDER.comment("Role editing reserved to the opLevelBypass level.").define("rolesNeedLevel", "§c[RpEssentials] Managing roles requires the OP level set in opLevelBypass.");
        GUI_PENDING_DELETED = BUILDER.comment("Placeholder: {player}").define("pendingDeleted", "§a[RpEssentials] Prepared profile for {player} deleted.");
        GUI_PROF_DELETED = BUILDER.comment("Placeholder: {id}").define("professionDeleted", "§a[RpEssentials] Profession §e{id} §adeleted.");
        GUI_ROLE_DELETED = BUILDER.comment("Report title. Placeholder: {id}").define("roleDeleted", "§a[RpEssentials] §fRole §e{id} §fdeleted");
        GUI_LP_GROUP = BUILDER.comment("Report line. Placeholder: {group}").define("lpGroup", "§7LuckPerms group: §f{group}");
        GUI_LP_GROUP_CHANGED = BUILDER.comment("Report line. Placeholders: {old} {new}").define("lpGroupChanged", "§7LuckPerms group: §c{old} §7-> §a{new}");
        GUI_PERMISSIONS = BUILDER.comment("Report line. Placeholder: {permissions}").define("permissions", "§7Permissions: §f{permissions}");
        GUI_NO_PERMISSIONS = BUILDER.comment("Report line.").define("noPermissions", "§8No permissions");
        GUI_NO_CHANGE = BUILDER.comment("Report line.").define("noChange", "§8No change");
        GUI_ROLE_INVALID = BUILDER.comment("Invalid role ID.").define("roleInvalid", "§c[RpEssentials] Invalid role ID.");
        GUI_ROLE_ID_TAKEN = BUILDER.comment("Role ID used by a profession.").define("roleIdTaken", "§c[RpEssentials] This ID is already used by a profession.");
        GUI_ROLE_CREATED = BUILDER.comment("Report title. Placeholder: {id}").define("roleCreated", "§a[RpEssentials] §fRole §e{id} §fcreated");
        GUI_ROLE_UPDATED = BUILDER.comment("Report title. Placeholder: {id}").define("roleUpdated", "§a[RpEssentials] §fRole §e{id} §fupdated");
        GUI_NOTE_NOT_FOUND = BUILDER.comment("Placeholders: {id} {player}").define("noteNotFound", "§c[NOTE] Note #{id} not found for {player}.");
        GUI_NOTE_DELETED = BUILDER.comment("Report title. Placeholders: {id} {player}").define("noteDeleted", "§a[RpEssentials] §fNote #{id} deleted for §e{player}");
        GUI_NOTE_EDITED = BUILDER.comment("Report title. Placeholders: {id} {player}").define("noteEdited", "§a[RpEssentials] §fNote #{id} edited for §e{player}");
        GUI_NOTE_ADDED = BUILDER.comment("Report title. Placeholders: {id} {player}").define("noteAdded", "§a[RpEssentials] §fNote #{id} added for §e{player}");
        GUI_NOTE_AUTHOR = BUILDER.comment("Report line. Placeholder: {author}").define("noteAuthor", "§8Original author: {author}");
        GUI_NOTE_LIMIT = BUILDER.comment("Placeholder: {player}").define("noteLimit", "§c[NOTE] Note limit reached for {player}.");
        GUI_ADD_NOT_FOUND = BUILDER.comment("Prepared profile: unknown name. Placeholder: {player}").define("addNotFound", "§c[RpEssentials] Player not found: §f{player}");
        GUI_ADD_KNOWN = BUILDER.comment("Placeholder: {player}").define("addKnown", "§e[RpEssentials] {player} is already known.");
        GUI_ADD_CONNECTED = BUILDER.comment("Placeholder: {player}").define("addConnected", "§e[RpEssentials] {player} has already connected to this server before. No need to prepare a profile, edit them directly from the offline list.");
        GUI_ADD_DONE = BUILDER.comment("Placeholder: {player}").define("addDone", "§a[RpEssentials] {player} added to prepared profiles.");
        GUI_CONFIG_NO_CHANGES = BUILDER.comment("Config GUI: nothing sent.").define("configNoChanges", "§7[Config] No changes to apply.");
        GUI_CONFIG_NO_VALID = BUILDER.comment("Config GUI: every change refused.").define("configNoValid", "§c[Config] No valid changes to apply.");
        GUI_CONFIG_APPLIED = BUILDER.comment("Report title. Placeholders: {file} {count}").define("configApplied", "§a[RpEssentials] §fConfig §e{file} §f: {count} change(s) applied");
        GUI_CONFIG_NOT_APPLIED = BUILDER.comment("Report line. Placeholder: {count}").define("configNotApplied", "§8{count} value(s) not applied (invalid or out of range)");
        GUI_CONFIG_FAILED = BUILDER.comment("Config GUI: nothing applied.").define("configFailed", "§c[Config] No changes could be applied (validation failed?).");
        GUI_GLOBAL_NO_CHANGE = BUILDER.comment("Global restrictions GUI: nothing changed.").define("globalNoChange", "§7[RpEssentials] Global restrictions: no change.");
        GUI_GLOBAL_UPDATED = BUILDER.comment("Report title.").define("globalUpdated", "§a[RpEssentials] §fGlobal restrictions updated");
        GUI_CAT_BLOCKED_CRAFTS = BUILDER.comment("Category label.").define("categoryBlockedCrafts", "Blocked crafts");
        GUI_CAT_UNBREAKABLE = BUILDER.comment("Category label.").define("categoryUnbreakableBlocks", "Unbreakable blocks");
        GUI_CAT_BLOCKED_ITEMS = BUILDER.comment("Category label.").define("categoryBlockedItems", "Blocked items");
        GUI_CAT_BLOCKED_EQUIPMENT = BUILDER.comment("Category label.").define("categoryBlockedEquipment", "Blocked equipment");
        GUI_CAT_CONTAINERS = BUILDER.comment("Category label.").define("categoryContainers", "Container restrictions");
        GUI_PROF_INVALID = BUILDER.comment("Profession editor: invalid ID or name.").define("professionInvalid", "§c[RpEssentials] Invalid ID or name.");
        GUI_PROF_RESERVED = BUILDER.comment("Profession editor: reserved ID.").define("professionReserved", "§c[RpEssentials] This ID is reserved (role or staff tag).");
        GUI_PROF_CREATED = BUILDER.comment("Placeholder: {id}").define("professionCreated", "§a[RpEssentials] Profession §e{id} §acreated.");
        GUI_PROF_UPDATED = BUILDER.comment("Placeholder: {id}").define("professionUpdated", "§a[RpEssentials] Profession §e{id} §aupdated.");
        GUI_PROF_SUMMARY = BUILDER.comment("Placeholders: {name} {color} (color code)").define("professionSummary", "§7  §6Name: §f{name}  §6Color: {color}■§r");
        GUI_PROF_LINE = BUILDER.comment("Placeholders: {label} {items}").define("professionLine", "§7  {label}: §f{items}");
        GUI_PROF_LINE_NONE = BUILDER.comment("Placeholder: {label}").define("professionLineNone", "§7  {label}: §8none");
        GUI_PROF_COUNT = BUILDER.comment("Placeholder: {count}").define("professionCount", " §8({count})");
        GUI_PROF_MORE = BUILDER.comment("Placeholder: {count}").define("professionMore", " §8(+{count} more)");
        GUI_LABEL_CRAFTS = BUILDER.comment("Label.").define("labelCrafts", "Crafts");
        GUI_LABEL_BLOCKS = BUILDER.comment("Label.").define("labelBlocks", "Blocks");
        GUI_LABEL_ITEMS = BUILDER.comment("Label.").define("labelItems", "Items");
        GUI_LABEL_EQUIPMENT = BUILDER.comment("Label.").define("labelEquipment", "Equipment");
        GUI_PROFILE_OFFLINE = BUILDER.comment("Profile GUI: target gone.").define("profileOffline", "§c[RpEssentials] Player is no longer connected.");
        GUI_PREPARED_UPDATED = BUILDER.comment("Placeholder: {player}").define("preparedUpdated", "§a[RpEssentials] Prepared profile updated for §e{player}");
        GUI_NO_LICENSE = BUILDER.comment("Placeholders: {player} {license}").define("noLicense", "§e[RpEssentials] §f{player} §edoes not have the §f{license} §elicense.");
        GUI_PROFILE_UPDATED = BUILDER.comment("Start of the profile report. Placeholder: {player}").define("profileUpdated", "§a[RpEssentials] Profile of §e{player} §aupdated:");
        GUI_UNKNOWN_ROLE = BUILDER.comment("Placeholder: {role}").define("unknownRole", "§c[RpEssentials] Unknown role: §f{role}");
        GUI_ALREADY_LICENSE = BUILDER.comment("Placeholders: {player} {license}").define("alreadyLicense", "§e[RpEssentials] §f{player} §ealready has the §f{license} §elicense. Use §f/rpessentials license reissue §eto give a replacement item.");
        GUI_UNKNOWN_PROFESSION = BUILDER.comment("Placeholder: {profession}").define("unknownProfession", "§c[RpEssentials] Unknown profession: §f{profession}");
        BUILDER.pop();

        BUILDER.push("Vanish System Messages");
        VANISH_MSG_SILENT_JOIN = BUILDER.comment("Sent to staff when a vanished player joins. Placeholder: {player}").define("silentJoin", "§7[Vanish] §f{player} §7joined silently.");
        VANISH_MSG_SILENT_LEAVE = BUILDER.comment("Sent to staff when a vanished player leaves. Placeholder: {player}").define("silentLeave", "§7[Vanish] §f{player} §7left silently.");
        BUILDER.pop();

        BUILDER.push("Schedule System");
        SCHED_SYS_CONFIG_WARNING = BUILDER.comment("Schedule config error sent to staff. Placeholder: {warning}").define("configWarning", "§c[Schedule] {warning}");
        SCHED_SYS_STAFF_CLOSED = BUILDER.comment("Sent to staff who stay connected when the server closes.").define("staffClosed", "§6[STAFF] Server closed: you may remain connected.");
        BUILDER.pop();

        BUILDER.push("Common Text");
        TIME_PERMANENT = BUILDER.comment("Duration: permanent.").define("timePermanent", "Permanent");
        TIME_EXPIRED = BUILDER.comment("Duration: expired.").define("timeExpired", "§cExpired");
        TIME_REMAINING_DAYS = BUILDER.comment("Placeholders: {d} {h}").define("timeRemainingDays", "{d}d {h}h remaining");
        TIME_REMAINING_HOURS = BUILDER.comment("Placeholders: {h} {min}").define("timeRemainingHours", "{h}h {min}min remaining");
        TIME_REMAINING_MINUTES = BUILDER.comment("Placeholder: {min}").define("timeRemainingMinutes", "{min}min remaining");
        TIME_LESS_MINUTE = BUILDER.comment("Playtime under one minute.").define("timeLessThanMinute", "< 1min");
        TIME_UNIT_DAYS = BUILDER.comment("Placeholder: {n}").define("timeUnitDays", "{n}d");
        TIME_UNIT_HOURS = BUILDER.comment("Placeholder: {n}").define("timeUnitHours", "{n}h");
        TIME_UNIT_MINUTES = BUILDER.comment("Placeholder: {n}").define("timeUnitMinutes", "{n}min");
        UI_ON = BUILDER.comment("Enabled flag in status screens.").define("on", "§aON");
        UI_OFF = BUILDER.comment("Disabled flag in status screens.").define("off", "§cOFF");
        UI_NA = BUILDER.comment("Unavailable value in status screens.").define("notAvailable", "§8N/A");
        UI_PAGE = BUILDER.comment("Pager. Placeholders: {page} {pages}").define("page", " §7Page §f{page}§7/§f{pages} ");
        UI_CONSOLE = BUILDER.comment("Name used when the console runs a command.").define("console", "Console");
        LOGIN_REFUSED_NOTICE = BUILDER.comment("Staff notice when a player tries to join while closed. Placeholder: {player}").define("refusedJoinNotice", "§6[Schedule] §e{player} §7tried to join while the server is closed.");
        LOGIN_VANISH_REMINDER = BUILDER.comment("Login reminder for vanished staff. Placeholder: {level}").define("vanishReminder", "§7[Vanish] You are still vanished (level {level}).");
        LOGIN_FORCE_CLOSED = BUILDER.comment("Staff login warning while the server is forcibly closed.").define("forceClosedWarning", "§c§l[Schedule] ⚠ The server is currently FORCIBLY CLOSED. Use §f/schedule forceopen §c§lto reopen it, or §f/schedule resetforcestate §c§lto go back to the normal schedule.");
        SCHED_WARN_INVALID_OPEN = BUILDER.comment("Schedule config warning. Placeholders: {day} {value}").define("scheduleInvalidOpen", "{day}: invalid open time '{value}' (expected HH:MM), day disabled.");
        SCHED_WARN_INVALID_CLOSE = BUILDER.comment("Schedule config warning. Placeholders: {day} {value}").define("scheduleInvalidClose", "{day}: invalid close time '{value}' (expected HH:MM), day disabled.");
        MUTE_AUTO_REASON = BUILDER.comment("Reason stored for an automatic mute (server language). Placeholder: {count}").define("autoMuteReason", "Auto-mute ({count} warns)");
        UNWHITELIST_UNDO = BUILDER.comment("Undo button after an automatic unwhitelist.").define("unwhitelistUndo", "§a[Undo]");
        UNWHITELIST_UNDO_HOVER = BUILDER.comment("Placeholder: {player}").define("unwhitelistUndoHover", "Re-whitelist {player}");
        GUI_REPORT_NICK = BUILDER.comment("Profile report. Placeholder: {nick}").define("reportNick", " §fnickname: {nick}");
        GUI_REPORT_NICK_RESET = BUILDER.comment("Profile report.").define("reportNickReset", " §fnickname: §8(reset)");
        GUI_REPORT_ROLE = BUILDER.comment("Profile report. Placeholder: {role}").define("reportRole", " §frole: {role}");
        GUI_REPORT_LICENSE = BUILDER.comment("Profile report. Placeholder: {license}").define("reportLicense", " §flicense: {license}");
        GUI_REPORT_LICENSE_DAYS = BUILDER.comment("Profile report. Placeholders: {license} {days}").define("reportLicenseDays", " §flicense: {license} ({days}d)");
        VANISH_ADVANCEMENT_HIDDEN = BUILDER.comment("Suffix of an advancement hidden by vanish (seen by staff).").define("advancementHidden", " §8[hidden]");
        LP_ROLES_SYNCED = BUILDER.comment("Placeholder: {roles}").define("luckPermsRolesSynced", "§6[LuckPerms] §7Roles synced from LuckPerms: §f{roles}");
        LIC_AUDIT_HEADER = BUILDER.comment("Placeholders: {shown} {total}").define("licenseAuditHeader", "§6License Audit §8({shown}/{total})");
        LIC_AUDIT_LINE = BUILDER.comment("Placeholders: {color} {action} {player} {staff} {profession} {date}").define("licenseAuditLine", "{color}{action} §7| §f{player} §7| by §e{staff} §7| §b{profession} §8({date})");
        COLORS_TITLE = BUILDER.comment("/colors title.").define("colorsTitle", "Available Colors");
        COLORS_NAMES = BUILDER.comment("The 16 color names, comma separated, from &0 to &f.").define("colorsNames", "Black,Dark Blue,Dark Green,Dark Aqua,Dark Red,Dark Purple,Gold,Gray,Dark Gray,Blue,Green,Aqua,Red,Light Purple,Yellow,White");
        COLORS_FORMATTING = BUILDER.comment("/colors section.").define("colorsFormatting", "Formatting");
        COLORS_BOLD = BUILDER.define("colorsBold", "Bold");
        COLORS_ITALIC = BUILDER.define("colorsItalic", "Italic");
        COLORS_UNDERLINE = BUILDER.define("colorsUnderline", "Underline");
        COLORS_STRIKE = BUILDER.define("colorsStrikethrough", "Strikethrough");
        COLORS_OBFUSCATED = BUILDER.define("colorsObfuscated", "Obfuscated");
        COLORS_RESET = BUILDER.define("colorsReset", "Reset");
        COLORS_CHAT_VARS = BUILDER.comment("/colors section.").define("colorsChatVariables", "Chat Variables");
        COLORS_RP_VARS = BUILDER.comment("/colors section.").define("colorsRpVariables", "RP Command Variables");
        COLORS_HEX = BUILDER.comment("/colors section.").define("colorsHex", "Hex and gradients");
        COLORS_VAR_NICK = BUILDER.define("colorsVarNick", "nick or username");
        COLORS_VAR_REAL = BUILDER.define("colorsVarReal", "always the Minecraft username");
        COLORS_VAR_NICK_REAL = BUILDER.define("colorsVarNickReal", "Nick (RealName)");
        COLORS_HEX_COLOR = BUILDER.define("colorsHexColor", "hex color");
        COLORS_GRADIENT = BUILDER.define("colorsGradient", "gradient");
        COLORS_GRADIENT_TEXT = BUILDER.define("colorsGradientText", "text");
        CFG_V_BAD_LINE = BUILDER.comment("Placeholders: {option} {value}").define("validateBadLine", "{option}: bad line '{value}'");
        CFG_V_BAD_PATTERN = BUILDER.comment("Placeholders: {option} {value}").define("validateBadPattern", "{option}: bad pattern '{value}'");
        CFG_V_BAD_PATTERN_IN = BUILDER.comment("Placeholders: {option} {value} {line}").define("validateBadPatternIn", "{option}: bad pattern '{value}' in '{line}'");
        CFG_V_UNKNOWN_PROF = BUILDER.comment("Placeholders: {option} {value}").define("validateUnknownProfession", "{option}: unknown profession '{value}'");
        CFG_V_UNKNOWN_PROF_IN = BUILDER.comment("Placeholders: {option} {value} {line}").define("validateUnknownProfessionIn", "{option}: unknown profession '{value}' in '{line}'");
        CFG_V_BAD_SLOT = BUILDER.comment("Placeholders: {option} {value}").define("validateBadSlot", "{option}: bad slot '{value}' (expected HH:MM-HH:MM)");
        CFG_V_EMPTY_ROLE = BUILDER.comment("Placeholders: {option} {line}").define("validateEmptyRole", "{option}: empty id in '{line}'");
        CFG_V_UNKNOWN_PERM = BUILDER.comment("Placeholders: {option} {value} {line}").define("validateUnknownPermission", "{option}: unknown permission '{value}' in '{line}'");
        CFG_V_NOT_LOADED = BUILDER.define("validateNotLoaded", "config not loaded yet");
        CFG_V_CMD_CONFLICT = BUILDER.comment("Placeholder: {command}").define("validateCommandConflict", "unprefixed command skipped, name already taken: {command}");
        BUILDER.pop();

        BUILDER.push("Status Text");
        SCHED_NEXT_DISABLED = BUILDER.comment("/schedule next event: schedule disabled.").define("nextEventDisabled", "Schedule disabled: server always open.");
        SCHED_NEXT_NOT_INIT = BUILDER.comment("/schedule next event: not ready.").define("nextEventNotInitialized", "Schedule not initialized");
        SCHED_NEXT_OPEN = BUILDER.comment("Placeholders: {h} {min}").define("nextEventOpen", "Open: closing in {h}h{min}");
        SCHED_NEXT_NONE = BUILDER.define("nextEventNoOpenDay", "Closed: no open day configured");
        SCHED_NEXT_CLOSED = BUILDER.comment("Placeholders: {day} {time}").define("nextEventClosed", "Closed: next opening {day} at {time}");
        SCHED_TODAY = BUILDER.comment("Used as {day} when the next opening is today.").define("today", "today");
        BLUR_LIST_WHITELIST = BUILDER.comment("Name of the blur whitelist in messages.").define("blurWhitelist", "whitelist");
        BLUR_LIST_BLACKLIST = BUILDER.comment("Name of the blur blacklist in messages.").define("blurBlacklist", "blacklist");
        BLUR_LIST_ALWAYS = BUILDER.comment("Name of the always-visible list in messages.").define("blurAlwaysVisible", "Always Visible list");
        BLUR_TITLE_WHITELIST = BUILDER.comment("Title of the blur whitelist.").define("blurWhitelistTitle", "Whitelist");
        BLUR_TITLE_BLACKLIST = BUILDER.comment("Title of the blur blacklist.").define("blurBlacklistTitle", "Blacklist (always hidden)");
        BLUR_TITLE_ALWAYS = BUILDER.comment("Title of the always-visible list.").define("blurAlwaysVisibleTitle", "Always Visible (always shown in TabList)");
        REASON_NONE = BUILDER.comment("Reason used when none is given (stored, server language).").define("noReason", "No reason specified");
        BUILDER.pop();

        BUILDER.push("Player History");
        HISTORY_SYSTEM = BUILDER.comment("Actor name of automatic actions (stored, server language).").define("system", "System");
        HISTORY_HEADER = BUILDER.comment("Placeholders: {player} {count}").define("header", "§6History of §e{player} §8({count})");
        HISTORY_EMPTY = BUILDER.comment("Placeholder: {player}").define("empty", "§7No history for §e{player}§7.");
        HISTORY_DISABLED = BUILDER.define("disabled", "§c[RpEssentials] The player history is disabled (historyEnabled).");
        HISTORY_UNKNOWN_PLAYER = BUILDER.comment("Placeholder: {player}").define("unknownPlayer", "§c[RpEssentials] Unknown player: §f{player}");
        HISTORY_LINE = BUILDER.comment("Placeholders: {date} {type} {actor} {detail}").define("line", "§8{date} §e{type} §7{actor}§8: §f{detail}");
        HISTORY_PURGED = BUILDER.comment("Placeholder: {count}").define("purged", "§a[RpEssentials] {count} old history line(s) removed.");
        HISTORY_T_WARN = BUILDER.comment("History type label.").define("typeWarn", "Warn");
        HISTORY_T_WARN_REMOVE = BUILDER.comment("History type label.").define("typeWarnRemove", "Warn removed");
        HISTORY_T_WARN_CLEAR = BUILDER.comment("History type label.").define("typeWarnClear", "Warns cleared");
        HISTORY_T_MUTE = BUILDER.comment("History type label.").define("typeMute", "Mute");
        HISTORY_T_UNMUTE = BUILDER.comment("History type label.").define("typeUnmute", "Unmute");
        HISTORY_T_NOTE = BUILDER.comment("History type label.").define("typeNote", "Note");
        HISTORY_T_NOTE_REMOVE = BUILDER.comment("History type label.").define("typeNoteRemove", "Note removed");
        HISTORY_T_MANAGE = BUILDER.comment("History type label.").define("typeManage", "Manage");
        HISTORY_T_LICENSE = BUILDER.comment("History type label.").define("typeLicense", "License");
        HISTORY_T_DEATH_RP = BUILDER.comment("History type label.").define("typeDeathRp", "RP death");
        HISTORY_ALL_NOTES = BUILDER.comment("Detail when every note of a player is removed.").define("allNotes", "all notes");
        BUILDER.pop();

        BUILDER.push("Audit Text");
        AUDIT_VIA_GUI = BUILDER.comment("License audit / history detail (server language).").define("viaGui", "via GUI");
        AUDIT_TEMP = BUILDER.comment("Placeholders: {days} {date}").define("tempLicense", "{days} days, expires {date}");
        AUDIT_TEMP_GUI = BUILDER.comment("Placeholders: {days} {date}").define("tempLicenseGui", "{days} days, expires {date} (via GUI)");
        AUDIT_REISSUED = BUILDER.comment("Placeholder: {staff}").define("reissued", "Item reissued by {staff}");
        AUDIT_VIA_MANAGE = BUILDER.comment("Placeholder: {staff}").define("viaManage", "via manage by {staff}");
        AUDIT_EXPIRED_ON = BUILDER.comment("Placeholder: {date}").define("expiredOn", "expired on {date}");
        MANAGE_QUEUED_REASON = BUILDER.comment("Reason of a queued manage action applied at login. Placeholder: {issuer}").define("queuedReason", "queued by {issuer}");
        BUILDER.pop();

        BUILDER.push("Manage Extra");
        MANAGE_TEMPBANNED = BUILDER.comment("Placeholders: {player} {date}").define("tempBanned", "§a[Manage] {player} banned until {date}.");
        MANAGE_DURATION_EXPECTED = BUILDER.comment("Invalid tempban duration.").define("durationExpected", "§c[Manage] Duration expected first, for example 3d, 12h, 30m or 1d12h.");
        MANAGE_SUG_L_TEMPBAN = BUILDER.comment("Clickable label.").define("suggestLabelTempban", "§e[Tempban 7d]");
        MANAGE_SUG_H_TEMPBAN = BUILDER.comment("Hover text.").define("suggestHoverTempban", "§7Ban for 7 days (change the duration, type a reason)");
        BUILDER.pop();

        BUILDER.push("Teleport Warmup");
        TP_WARMUP_COUNTDOWN = BUILDER.comment("Action bar during the delay. Placeholder: {seconds}").define("countdown", "§eTeleporting in §f{seconds}s§e, don't move...");
        TP_WARMUP_CANCELLED_MOVE = BUILDER.define("cancelledMove", "§cTeleport cancelled: you moved.");
        TP_WARMUP_CANCELLED_DAMAGE = BUILDER.define("cancelledDamage", "§cTeleport cancelled: you took damage.");
        BUILDER.pop();

        BUILDER.push("Schedule Exceptions");
        SCHED_WARN_INVALID_EXCEPTION = BUILDER.comment("Schedule config warning. Placeholder: {value}").define("invalidException", "scheduleExceptions: invalid line '{value}' (expected yyyy-MM-dd=CLOSED, yyyy-MM-dd=OPEN or yyyy-MM-dd=HH:MM-HH:MM), ignored.");
        SCHED_SECTION_EXCEPTIONS = BUILDER.comment("/schedule section title.").define("sectionExceptions", "Special dates");
        SCHED_EXCEPTION_CLOSED = BUILDER.comment("Placeholders: {day} {date}").define("exceptionClosed", "§7{day} §8{date} §7: §cclosed");
        SCHED_EXCEPTION_OPEN = BUILDER.comment("Placeholders: {day} {date} {open} {close}").define("exceptionOpen", "§7{day} §8{date} §7: §a{open} - {close}");
        BUILDER.pop();

        BUILDER.push("Dice Extra");
        DICE_ROLL_DETAIL = BUILDER.comment("Details appended to the result. Placeholder: {detail}").define("rollDetail", " §8({detail})");
        DICE_ROLL_SECRET_FORMAT = BUILDER.comment("Secret roll, seen by the roller and the staff. Placeholders: {player} {dice} {result}").define("secretRollFormat", "§8[Secret] §7{player} rolled §f{dice}§7: §e{result}");
        DICE_ADVANTAGE = BUILDER.define("advantage", "advantage");
        DICE_DISADVANTAGE = BUILDER.define("disadvantage", "disadvantage");
        DICE_SECRET_DISABLED = BUILDER.define("secretDisabled", "§c[Dice] Secret rolls are disabled on this server.");
        DICE_EXPRESSIONS_DISABLED = BUILDER.define("expressionsDisabled", "§c[Dice] Only the configured dice can be rolled here.");
        DICE_TOO_MANY = BUILDER.comment("Placeholders: {count} {sides}").define("tooMany", "§c[Dice] Too big: at most {count} dice of {sides} sides.");
        BUILDER.pop();

        BUILDER.push("Validate Extra");
        CFG_V_UNKNOWN_DIMENSION = BUILDER.comment("Placeholders: {option} {value}").define("validateUnknownDimension", "{option}: dimension '{value}' does not exist on this server");
        CFG_V_UNKNOWN_SOUND = BUILDER.comment("Placeholders: {option} {value}").define("validateUnknownSound", "{option}: unknown sound '{value}'");
        CFG_V_ORPHAN_LICENSE = BUILDER.comment("Placeholders: {value} {count}").define("validateOrphanLicense", "licenses: {count} player(s) hold a license for '{value}', which is not in professions");
        BUILDER.pop();

        BUILDER.push("Player Stats");
        STATS_SECTION_ACTIVITY = BUILDER.comment("Stats tab section title.").define("sectionActivity", "§6§lActivity");
        STATS_SECTION_DISCIPLINE = BUILDER.comment("Stats tab section title.").define("sectionDiscipline", "§6§lDiscipline");
        STATS_SECTION_RP = BUILDER.comment("Stats tab section title.").define("sectionRoleplay", "§6§lRoleplay");
        STATS_SECTION_ACCESS = BUILDER.comment("Stats tab section title.").define("sectionAccess", "§6§lAccess");
        STATS_FIRST_JOIN = BUILDER.comment("Placeholders: {date} {ago}").define("firstJoin", "§7First join: {date} §8({ago})");
        STATS_LAST_LOGIN = BUILDER.comment("Placeholders: {date} {ago}").define("lastLogin", "§7Last login: {date} §8({ago})");
        STATS_LAST_LOGOUT = BUILDER.comment("Placeholder: {date}").define("lastLogout", "§7Last logout: {date}");
        STATS_ONLINE_NOW = BUILDER.define("onlineNow", "§aonline now");
        STATS_UNKNOWN = BUILDER.define("unknown", "§8unknown");
        STATS_AGO_NOW = BUILDER.define("agoNow", "just now");
        STATS_AGO_MINUTES = BUILDER.comment("Placeholder: {n}").define("agoMinutes", "{n} min ago");
        STATS_AGO_HOURS = BUILDER.comment("Placeholder: {n}").define("agoHours", "{n} h ago");
        STATS_AGO_DAYS = BUILDER.comment("Placeholder: {n}").define("agoDays", "{n} day(s) ago");
        STATS_WARNS_TOTAL = BUILDER.comment("Placeholders: {total} {active}").define("warnsTotal", "§7Warns received: §f{total} §8({active} active)");
        STATS_MUTES_TOTAL = BUILDER.comment("Placeholder: {count}").define("mutesTotal", "§7Mutes received: §f{count} §8(since the history started)");
        STATS_ABSENCE = BUILDER.comment("Placeholders: {reason} {date}").define("absence", "§7Absence: §e{reason} §8until §f{date}");
        STATS_ABSENCE_NONE = BUILDER.define("absenceNone", "§7Absence: §8none");
        STATS_TEMP_LICENSE = BUILDER.comment("Placeholders: {profession} {date}").define("tempLicense", "§7Temporary license: §f{profession} §8until §f{date}");
        STATS_TEMP_NONE = BUILDER.define("tempLicenseNone", "§7Temporary licenses: §8none");
        STATS_DEATHRP = BUILDER.comment("Placeholders: {state} {count}").define("deathRp", "§7RP death: {state} §8({count} death(s))");
        STATS_ENABLED = BUILDER.define("enabled", "§aenabled");
        STATS_DISABLED = BUILDER.define("disabled", "§cdisabled");
        STATS_WHITELIST = BUILDER.comment("Placeholder: {value}").define("whitelist", "§7Whitelisted: {value}");
        STATS_YES = BUILDER.define("yes", "§ayes");
        STATS_NO = BUILDER.define("no", "§cno");
        STATS_OP = BUILDER.comment("Placeholder: {level}").define("opLevel", "§7OP level: §f{level}");
        STATS_ROLE = BUILDER.comment("Placeholders: {role} {group}").define("role", "§7Role: §f{role} §8(LuckPerms: {group})");
        STATS_ROLE_OFFLINE = BUILDER.define("roleOffline", "§7Role: §8visible when the player is online");
        STATS_VANISH = BUILDER.comment("Placeholder: {value}").define("vanish", "§7Vanish: {value}");
        STATS_VANISH_LEVEL = BUILDER.comment("Placeholder: {level}").define("vanishLevel", "§elevel {level}");
        STATS_VISIBLE = BUILDER.define("visible", "§avisible");
        BUILDER.pop();

        BUILDER.push("Player Stats Extra");
        STATS_LOGINS = BUILDER.comment("Placeholders: {total} {d7} {d30}").define("logins", "§7Logins: §f{total} §8(7 days: §f{d7}§8, 30 days: §f{d30}§8)");
        STATS_PLAYTIME_RECENT = BUILDER.comment("Placeholders: {d7} {d30}").define("playtimeRecent", "§7Playtime: §f{d7} §8in 7 days, §f{d30} §8in 30 days");
        STATS_NICKS = BUILDER.comment("Latest nicknames, newest first. Placeholder: {list}").define("nicknames", "§7Nicknames: §r{list}");
        STATS_NICKS_NONE = BUILDER.define("nicknamesNone", "§7Nicknames: §8no change recorded");
        HISTORY_T_NICK = BUILDER.comment("History type label.").define("typeNick", "Nickname");
        BUILDER.pop();

        BUILDER.push("Reports");
        HISTORY_T_REPORT = BUILDER.comment("History type label.").define("historyType", "Report");
        REPORT_DISABLED = BUILDER.define("disabled", "§c[Report] Reports are disabled on this server.");
        REPORT_SELF = BUILDER.define("self", "§c[Report] You cannot report yourself.");
        REPORT_UNKNOWN_PLAYER = BUILDER.comment("Placeholder: {player}").define("unknownPlayer", "§c[Report] Unknown player: §f{player}");
        REPORT_COOLDOWN = BUILDER.comment("Placeholder: {seconds}").define("cooldown", "§c[Report] Please wait §e{seconds}s §cbefore sending another report.");
        REPORT_TOO_MANY = BUILDER.comment("Placeholder: {count}").define("tooMany", "§c[Report] You already have §e{count} §creports waiting for the staff.");
        REPORT_SENT = BUILDER.comment("Placeholders: {id} {player}").define("sent", "§a[Report] Your report §e#{id} §aagainst §e{player} §awas sent to the staff.");
        REPORT_STAFF_NEW = BUILDER.comment("Sent to online staff. Placeholders: {id} {reporter} {player} {reason}")
                .define("staffNew", "§c[Report #{id}] §e{reporter} §7reported §e{player}§7: §f{reason}");
        REPORT_BTN_VIEW = BUILDER.define("buttonView", "§b[View]");
        REPORT_BTN_VIEW_HOVER = BUILDER.define("buttonViewHover", "Show this report");
        REPORT_STAFF_JOIN = BUILDER.comment("Placeholder: {count}").define("staffJoin", "§6[Report] §e{count} §6report(s) waiting. §7/rpessentials reports list");
        REPORT_NOT_FOUND = BUILDER.comment("Placeholder: {id}").define("notFound", "§c[Report] Report §e#{id} §cnot found.");
        REPORT_CLAIMED = BUILDER.comment("Placeholder: {id}").define("claimed", "§a[Report] You are now handling report §e#{id}§a.");
        REPORT_CLAIMED_STAFF = BUILDER.comment("Sent to the other staff. Placeholders: {id} {staff}").define("claimedStaff", "§7[Report] §e{staff} §7is handling report §e#{id}§7.");
        REPORT_CLOSED = BUILDER.comment("Placeholder: {id}").define("closed", "§a[Report] Report §e#{id} §aclosed.");
        REPORT_REOPENED = BUILDER.comment("Placeholder: {id}").define("reopened", "§a[Report] Report §e#{id} §areopened.");
        REPORT_DELETED = BUILDER.comment("Placeholder: {id}").define("deleted", "§a[Report] Report §e#{id} §adeleted.");
        REPORT_ALREADY_CLOSED = BUILDER.comment("Placeholder: {id}").define("alreadyClosed", "§c[Report] Report §e#{id} §cis already closed.");
        REPORT_NOT_CLOSED = BUILDER.comment("Placeholder: {id}").define("notClosed", "§c[Report] Report §e#{id} §cis not closed.");
        REPORT_REPORTER_CLOSED = BUILDER.comment("Sent to the reporter. Placeholders: {id} {player}")
                .define("reporterClosed", "§a[Report] Your report §e#{id} §aagainst §e{player} §awas handled by the staff.");
        REPORT_LIST_HEADER = BUILDER.comment("Placeholder: {count}").define("listHeader", "§6--- Reports waiting ({count}) ---");
        REPORT_LIST_NONE = BUILDER.define("listNone", "§7No report waiting.");
        REPORT_LIST_LINE = BUILDER.comment("Placeholders: {id} {status} {reporter} {player} {date}")
                .define("listLine", "§e#{id} §8[{status}§8] §f{reporter} §7→ §f{player} §8({date})");
        REPORT_INFO = BUILDER.comment("Placeholders: {id} {status} {reporter} {player} {date} {place} {reason}")
                .define("info", "§6--- Report #{id} §8[{status}§8] §6---\n§7By: §f{reporter}\n§7Against: §f{player}\n§7Date: §f{date}\n§7Place: §f{place}\n§7Reason: §f{reason}");
        REPORT_INFO_HANDLER = BUILDER.comment("Placeholder: {staff}").define("infoHandler", "§7Handled by: §f{staff}");
        REPORT_INFO_NOTE = BUILDER.comment("Placeholder: {note}").define("infoNote", "§7Closing note: §f{note}");
        REPORT_STATUS_OPEN = BUILDER.define("statusOpen", "§cOpen");
        REPORT_STATUS_CLAIMED = BUILDER.define("statusClaimed", "§6In progress");
        REPORT_STATUS_CLOSED = BUILDER.define("statusClosed", "§aClosed");
        REPORT_BTN_CLAIM = BUILDER.define("buttonClaim", "§6[Handle]");
        REPORT_BTN_CLOSE = BUILDER.define("buttonClose", "§a[Close]");
        REPORT_BTN_TP = BUILDER.define("buttonTp", "§b[TP]");
        REPORT_TP_FAILED = BUILDER.define("tpFailed", "§c[Report] This place cannot be reached.");
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
        return ServerLang.get(null, configValue, replacements);
    }

    /**
     * Retrieves a message with no placeholder substitution.
     */
    public static String get(ModConfigSpec.ConfigValue<String> configValue) {
        return ServerLang.get(null, configValue);
    }

    /**
     * Formats a warn duration (in minutes) into a human-readable string.
     */
    public static String formatDuration(int minutes) {
        return formatDuration(null, minutes);
    }

    /** {@link #formatDuration(int)} in the viewer's language. */
    public static String formatDuration(net.minecraft.server.level.ServerPlayer v, int minutes) {
        if (minutes < 60) {
            return ServerLang.get(v, WARN_DURATION_MINUTES, "min", String.valueOf(minutes));
        } else if (minutes < 1440) {
            return ServerLang.get(v, WARN_DURATION_HOURS, "h", String.valueOf(minutes / 60), "min", String.valueOf(minutes % 60));
        } else {
            return ServerLang.get(v, WARN_DURATION_DAYS, "d", String.valueOf(minutes / 1440), "h", String.valueOf((minutes % 1440) / 60));
        }
    }

    /** "Permanent", "Expired" or the remaining time of an expiry timestamp, in the viewer's language. */
    public static String formatExpiry(net.minecraft.server.level.ServerPlayer v, Long expiresAt) {
        if (expiresAt == null) return ServerLang.get(v, TIME_PERMANENT);
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return ServerLang.get(v, TIME_EXPIRED);
        long totalMinutes = remaining / 60_000;
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long minutes = totalMinutes % 60;
        if (days > 0) return ServerLang.get(v, TIME_REMAINING_DAYS, "d", String.valueOf(days), "h", String.valueOf(hours));
        if (hours > 0) return ServerLang.get(v, TIME_REMAINING_HOURS, "h", String.valueOf(hours), "min", String.valueOf(minutes));
        return ServerLang.get(v, TIME_REMAINING_MINUTES, "min", String.valueOf(minutes));
    }
}