package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ChatConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // === CHAT SYSTEM ===
    public static final ModConfigSpec.BooleanValue ENABLE_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> PLAYER_NAME_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> GLOBAL_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> CHAT_MESSAGE_COLOR;
    public static final ModConfigSpec.BooleanValue ENABLE_TIMESTAMP;
    public static final ModConfigSpec.ConfigValue<String> TIMESTAMP_FORMAT;
    public static final ModConfigSpec.BooleanValue MARKDOWN_ENABLED;
    public static final ModConfigSpec.BooleanValue ENABLE_COLORS_COMMAND;
    public static final ModConfigSpec.BooleanValue LOG_PRIVATE_MESSAGES;
    public static final ModConfigSpec.BooleanValue SHOW_REAL_NAME_IN_CHAT;
    public static final ModConfigSpec.BooleanValue HIDE_REAL_NAMES;

    // === PROXIMITY CHAT ===
    public static final ModConfigSpec.BooleanValue ENABLE_PROXIMITY_CHAT;
    public static final ModConfigSpec.BooleanValue ENABLE_PROXIMITY_SPY;
    public static final ModConfigSpec.IntValue PROXIMITY_CHAT_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_BYPASS_PREFIX;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> CHAT_MESSAGE_FORMAT;

    // === CHAT SYSTEM 2 ===
    public static final ModConfigSpec.ConfigValue<String> PLAYER_CHAT_CODES;
    public static final ModConfigSpec.BooleanValue ENABLE_PM_SPY;

    // === JOIN/LEAVE MESSAGES ===
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_JOIN_LEAVE;
    public static final ModConfigSpec.ConfigValue<String> JOIN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> LEAVE_MESSAGE;

    static {
        BUILDER.push("Chat System");

        ENABLE_CHAT_FORMAT = BUILDER
                .comment("Enable custom chat formatting system.")
                .define("enableChatFormat", true);

        PLAYER_NAME_FORMAT = BUILDER
                .comment(
                        "Format of the $name variable used in the chat formats below (not a chat format itself).",
                        "",
                        "BACKWARD-COMPATIBLE variables (unchanged behaviour):",
                        "  $name    — nickname if set, otherwise MC username  (same as $nick)",
                        "  $prefix  — LuckPerms prefix",
                        "  $suffix  — LuckPerms suffix",
                        "",
                        "NEW variables (opt-in, do not affect existing configs):",
                        "  $nick      — nickname if set, otherwise MC username (alias of $name)",
                        "  $real      — always the Minecraft username, regardless of nickname",
                        "  $nick_real — 'Nickname (RealName)' when they differ, otherwise just the name",
                        "",
                        "Examples:",
                        "  '$prefix $name $suffix'           <- existing config, works unchanged",
                        "  '$prefix $nick_real $suffix'      <- shows 'Aragorn (Steve)' when nick differs",
                        "  '$prefix $nick §8($real)$suffix'  <- manual placement with custom colour"
                )
                .define("playerNameFormat", "$prefix $name $suffix");

        CHAT_MESSAGE_FORMAT = BUILDER
                .comment("Fallback chat format, used when globalChatFormat or proximityChatFormat is empty.",
                        "Variables: $time, $name (or $nick / $nick_real / $real), $msg")
                .define("chatMessageFormat", "§7[$time] §r$name: $msg");

        CHAT_MESSAGE_COLOR = BUILDER
                .comment("Global color for chat messages.",
                        "Choose: AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, BLACK, GOLD,",
                        "GRAY, BLUE, GREEN, DARK_GRAY, DARK_AQUA, DARK_RED,",
                        "DARK_PURPLE, DARK_GREEN, DARK_BLUE")
                .define("chatMessageColor", "WHITE");

        ENABLE_TIMESTAMP = BUILDER
                .comment("Show timestamp in chat messages.")
                .define("enableTimestamp", true);

        TIMESTAMP_FORMAT = BUILDER
                .comment("Timestamp format (Java SimpleDateFormat).",
                        "Examples: HH:mm, HH:mm:ss, hh:mm a")
                .define("timestampFormat", "HH:mm");

        MARKDOWN_ENABLED = BUILDER
                .comment("Enable markdown styling in chat.",
                        "**bold**, *italic*, __underline__, ~~strikethrough~~")
                .define("markdownEnabled", true);

        SHOW_REAL_NAME_IN_CHAT = BUILDER
                .comment(
                        "When true, the real MC username is automatically appended in parentheses",
                        "after the nickname wherever a player has an active nickname.",
                        "",
                        "Applies to: chat, /msg, /rp action, /rp commerce, /rp incognito,",
                        "            /rp annonce, join/leave messages, proximity spy log.",
                        "",
                        "Default: false — existing behaviour preserved.",
                        "",
                        "Note: you can also control placement manually with $nick_real / {nick_real}",
                        "in the format strings instead of using this toggle."
                )
                .define("showRealNameInChat", false);

        ENABLE_PROXIMITY_CHAT = BUILDER
                .comment("If true, chat messages are only visible within proximityChatDistance blocks.",
                        "Use the bypass prefix (default: !) to send a global message.")
                .define("enableProximityChat", false);

        ENABLE_PROXIMITY_SPY = BUILDER
                .comment("If true, staff outside proximity range receive a spy log of nearby chat messages.")
                .define("enableProximitySpy", true);

        PROXIMITY_CHAT_DISTANCE = BUILDER
                .comment("Radius in blocks for proximity chat.")
                .defineInRange("proximityChatDistance", 32, 1, 256);

        PROXIMITY_CHAT_BYPASS_PREFIX = BUILDER
                .comment("Prefix to bypass proximity chat (stripped from the message).")
                .define("proximityChatBypassPrefix", "!");

        PROXIMITY_CHAT_FORMAT = BUILDER
                .comment("Format of messages heard only by nearby players (proximity chat enabled, no bypass prefix).",
                        "Empty = use chatMessageFormat. Variables: $time, $name (or $nick / $nick_real / $real), $msg")
                .define("proximityChatFormat", "[$time] $name: $msg");

        GLOBAL_CHAT_FORMAT = BUILDER
                .comment("Format of every message when proximity chat is disabled, and of messages sent with the",
                        "bypass prefix when it is enabled. Empty = use chatMessageFormat.",
                        "Variables: $time, $name (or $nick / $nick_real / $real), $msg")
                .define("globalChatFormat", "§7[$time] §f$name§r: $msg");

        ENABLE_COLORS_COMMAND = BUILDER
                .comment("Enable /colors command.")
                .define("enableColorsCommand", true);

        LOG_PRIVATE_MESSAGES = BUILDER
                .comment("Log private messages (/msg) to console.")
                .define("logPrivateMessages", false);

        PLAYER_CHAT_CODES = BUILDER
                .comment("Which formatting codes non-staff players may type in chat.",
                        "ALL           : everything (default, previous behaviour)",
                        "NO_OBFUSCATED : everything except the obfuscated code (&k)",
                        "NONE          : no color or format codes at all",
                        "Staff members always keep every code.")
                .define("playerChatCodes", "ALL");
        ConfigRules.oneOf(PLAYER_CHAT_CODES, java.util.Arrays.asList("ALL", "NO_OBFUSCATED", "NONE"));

        ENABLE_PM_SPY = BUILDER
                .comment("If true, players with the spyMessages role permission can read private messages (/msg).",
                        "Players should be told: a notice is sent to everyone at login (see pmSpyNotice in the messages file).",
                        "Default: false.")
                .define("enablePrivateMessageSpy", false);

        HIDE_REAL_NAMES = BUILDER
                .comment("If true, non-staff players do not see the real name of nicknamed players in /list",
                        "and in command name suggestions. Default: false.")
                .define("hideRealNames", false);

        BUILDER.pop();

        BUILDER.push("Join and Leave Messages");

        ENABLE_CUSTOM_JOIN_LEAVE = BUILDER
                .comment("Enable custom join/leave messages.")
                .define("enableCustomJoinLeave", true);

        JOIN_MESSAGE = BUILDER
                .comment(
                        "Join message.",
                        "BACKWARD-COMPATIBLE: {player} = MC username, {nickname} = nick or MC username.",
                        "NEW: {nick} = same as {nickname}, {real} = always MC username,",
                        "     {nick_real} = 'Nick (Real)' when they differ.",
                        "Set to 'none' to disable."
                )
                .define("joinMessage", "§e{player} §7woke up.");

        LEAVE_MESSAGE = BUILDER
                .comment(
                        "Leave message.",
                        "BACKWARD-COMPATIBLE: {player} = MC username, {nickname} = nick or MC username.",
                        "NEW: {nick}, {real}, {nick_real}.",
                        "Set to 'none' to disable."
                )
                .define("leaveMessage", "§e{player} §7went to sleep.");

        BUILDER.pop();

        SPEC = BUILDER.build();

        ConfigAccess.node(net.rp.rpessentials.permission.RpPerm.STAFF_PROXIMITYSPY, ENABLE_PROXIMITY_SPY);
    }
}