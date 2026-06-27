package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ChatConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // === CHAT SYSTEM ===
    public static final ModConfigSpec.BooleanValue ENABLE_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> PLAYER_NAME_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> CHAT_MESSAGE_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> CHAT_MESSAGE_COLOR;
    public static final ModConfigSpec.BooleanValue ENABLE_TIMESTAMP;
    public static final ModConfigSpec.ConfigValue<String> TIMESTAMP_FORMAT;
    public static final ModConfigSpec.BooleanValue MARKDOWN_ENABLED;
    public static final ModConfigSpec.BooleanValue ENABLE_COLORS_COMMAND;
    public static final ModConfigSpec.BooleanValue LOG_PRIVATE_MESSAGES;
    public static final ModConfigSpec.BooleanValue SHOW_REAL_NAME_IN_CHAT;

    // === JOIN/LEAVE MESSAGES ===
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_JOIN_LEAVE;
    public static final ModConfigSpec.ConfigValue<String> JOIN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> LEAVE_MESSAGE;

    // === PROXIMITY CHAT ===
    public static final ModConfigSpec.BooleanValue ENABLE_PROXIMITY_CHAT;
    public static final ModConfigSpec.BooleanValue ENABLE_PROXIMITY_SPY;
    public static final ModConfigSpec.IntValue PROXIMITY_CHAT_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_BYPASS_PREFIX;
    public static final ModConfigSpec.ConfigValue<String> PROXIMITY_CHAT_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> GLOBAL_CHAT_FORMAT;

    static {
        BUILDER.push("Chat System");

        ENABLE_CHAT_FORMAT = BUILDER
                .comment("Enable custom chat formatting system.")
                .define("enableChatFormat", true);

        PLAYER_NAME_FORMAT = BUILDER
                .comment(
                        "Player name format in chat.",
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
                .comment(
                        "Chat message format.",
                        "Variables: $time, $name (or $nick / $nick_real / $real), $msg",
                        "See playerNameFormat comment for the full variable list."
                )
                .define("chatMessageFormat", "[$time] $name: $msg");

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
                .comment("Format for proximity chat.",
                        "Variables: $time, $name (or $nick / $nick_real / $real), $msg")
                .define("proximityChatFormat", "[$time] $name: $msg");

        GLOBAL_CHAT_FORMAT = BUILDER
                .comment("Format for global chat (bypass prefix or proximity disabled).",
                        "Variables: $time, $name (or $nick / $nick_real / $real), $msg")
                .define("globalChatFormat", "[$time] §f$name§r: $msg");

        ENABLE_COLORS_COMMAND = BUILDER
                .comment("Enable /colors command.")
                .define("enableColorsCommand", true);

        LOG_PRIVATE_MESSAGES = BUILDER
                .comment("Log private messages (/msg) to console.")
                .define("logPrivateMessages", false);

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
                .define("joinMessage", "§e{player} §7woke up");

        LEAVE_MESSAGE = BUILDER
                .comment(
                        "Leave message.",
                        "BACKWARD-COMPATIBLE: {player} = MC username, {nickname} = nick or MC username.",
                        "NEW: {nick}, {real}, {nick_real}.",
                        "Set to 'none' to disable."
                )
                .define("leaveMessage", "§e{player} §7went to sleep");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}