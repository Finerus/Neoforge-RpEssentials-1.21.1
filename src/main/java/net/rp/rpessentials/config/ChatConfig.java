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

    // === JOIN/LEAVE MESSAGES ===
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_JOIN_LEAVE;
    public static final ModConfigSpec.ConfigValue<String> JOIN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> LEAVE_MESSAGE;

    static {
        // ===============================================================================
        // CATEGORY: CHAT SYSTEM
        // ===============================================================================
        BUILDER.push("Chat System");

        ENABLE_CHAT_FORMAT = BUILDER
                .comment("Enable custom chat formatting system")
                .define("enableChatFormat", true);

        PLAYER_NAME_FORMAT = BUILDER
                .comment("Player name format in chat",
                        "Variables: $prefix, $name, $suffix",
                        "$prefix = LuckPerms prefix",
                        "$name = player name or nickname",
                        "$suffix = LuckPerms suffix")
                .define("playerNameFormat", "$prefix $name $suffix");

        CHAT_MESSAGE_FORMAT = BUILDER
                .comment("Chat message format",
                        "Variables: $time, $name, $msg",
                        "$time = timestamp (if enabled)",
                        "$name = formatted player name",
                        "$msg = player's message",
                        "You can use color codes with §")
                .define("chatMessageFormat", "[$time] $name: $msg");

        CHAT_MESSAGE_COLOR = BUILDER
                .comment("Global color for chat messages",
                        "Choose: AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE, BLACK, GOLD,",
                        "GRAY, BLUE, GREEN, DARK_GRAY, DARK_AQUA, DARK_RED,",
                        "DARK_PURPLE, DARK_GREEN, DARK_BLUE")
                .define("chatMessageColor", "WHITE");

        ENABLE_TIMESTAMP = BUILDER
                .comment("Show timestamp in chat messages")
                .define("enableTimestamp", true);

        TIMESTAMP_FORMAT = BUILDER
                .comment("Timestamp format (Java SimpleDateFormat)",
                        "Examples: HH:mm, HH:mm:ss, hh:mm a",
                        "Read more: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
                .define("timestampFormat", "HH:mm");

        MARKDOWN_ENABLED = BUILDER
                .comment("Enable markdown styling in chat",
                        "**bold**, *italic*, __underline__, ~~strikethrough~~")
                .define("markdownEnabled", true);

        ENABLE_COLORS_COMMAND = BUILDER
                .comment("Enable /colors command to show available colors")
                .define("enableColorsCommand", true);

        LOG_PRIVATE_MESSAGES = BUILDER
                .comment("If 'true', logs private messages (/msg) to the server console.",
                        "Useful for moderation purposes.")
                .define("logPrivateMessages", false);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: JOIN/LEAVE MESSAGES
        // ===============================================================================
        BUILDER.push("Join and Leave Messages");

        ENABLE_CUSTOM_JOIN_LEAVE = BUILDER
                .comment("Enable custom join/leave messages.")
                .define("enableCustomJoinLeave", true);

        JOIN_MESSAGE = BUILDER
                .comment("Join message. Variables: {player}, {nickname}",
                        "Use 'none' to disable join messages completely.")
                .define("joinMessage", "§e{player} §7joined the game");

        LEAVE_MESSAGE = BUILDER
                .comment("Leave message. Variables: {player}, {nickname}",
                        "Use 'none' to disable leave messages completely.")
                .define("leaveMessage", "§e{player} §7left the game");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
