package net.rp.rpessentials;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration du planning serveur.
 * Fichier : config/rpessentials/rpessentials-schedule.toml
 *
 * Chaque jour de la semaine a ses propres horaires d'ouverture/fermeture.
 * Un jour avec enabled = false est considéré fermé toute la journée.
 */
public class ScheduleConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // GÉNÉRAL
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_SCHEDULE;
    public static final ModConfigSpec.BooleanValue KICK_NON_STAFF;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> WARNING_TIMES;

    // =========================================================================
    // HORAIRES PAR JOUR
    // =========================================================================
    public static final ModConfigSpec.BooleanValue          MONDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   MONDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   MONDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          TUESDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   TUESDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   TUESDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          WEDNESDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   WEDNESDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   WEDNESDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          THURSDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   THURSDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   THURSDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          FRIDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   FRIDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   FRIDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          SATURDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   SATURDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   SATURDAY_CLOSE;

    public static final ModConfigSpec.BooleanValue          SUNDAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>   SUNDAY_OPEN;
    public static final ModConfigSpec.ConfigValue<String>   SUNDAY_CLOSE;

    // =========================================================================
    // MESSAGES
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> MSG_SERVER_CLOSED;
    public static final ModConfigSpec.ConfigValue<String> MSG_SERVER_OPENED;
    public static final ModConfigSpec.ConfigValue<String> MSG_WARNING;
    public static final ModConfigSpec.ConfigValue<String> MSG_CLOSING_IMMINENT;

    // =========================================================================
    // WELCOME
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_WELCOME;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELCOME_LINES;
    public static final ModConfigSpec.ConfigValue<String> WELCOME_SOUND;
    public static final ModConfigSpec.DoubleValue WELCOME_SOUND_VOLUME;
    public static final ModConfigSpec.DoubleValue WELCOME_SOUND_PITCH;

    // =========================================================================
    // DEATH RP SCHEDULE
    // =========================================================================
    public static ModConfigSpec.BooleanValue DEATH_HOURS_ENABLED;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DEATH_HOURS_SLOTS;

    // =========================================================================
    // HRP SCHEDULE
    // =========================================================================
    public static ModConfigSpec.BooleanValue ENABLE_HRP_HOURS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> HRP_TOLERATED_SLOTS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> HRP_ALLOWED_SLOTS;
    public static ModConfigSpec.ConfigValue<String> HRP_TOLERATED_MESSAGE;
    public static ModConfigSpec.ConfigValue<String> HRP_ALLOWED_MESSAGE;
    public static ModConfigSpec.ConfigValue<String> HRP_MESSAGE_MODE;

    // =========================================================================
    // STATIC INITIALIZER
    // =========================================================================
    static {

        // ─── Général ──────────────────────────────────────────────────────────
        BUILDER.push("Schedule System");

        ENABLE_SCHEDULE = BUILDER
                .comment("Enable the schedule system.",
                        "When false, the server is always considered open.")
                .define("enableSchedule", false);

        KICK_NON_STAFF = BUILDER
                .comment("If true, non-staff players are kicked when the server closes.")
                .define("kickNonStaff", true);

        WARNING_TIMES = BUILDER
                .comment("Minutes before closing at which warning messages are sent.",
                        "Example: [30, 15, 5, 1]")
                .defineList("warningTimes", Arrays.asList(30, 15, 5, 1),
                        obj -> obj instanceof Integer);

        BUILDER.pop();

        // ─── Horaires par jour ────────────────────────────────────────────────
        BUILDER.push("Days");

        BUILDER.push("MONDAY");
        MONDAY_ENABLED = BUILDER.comment("Is Monday an open day?").define("enabled", true);
        MONDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "19:00");
        MONDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM). Supports cross-midnight, e.g. 02:00.").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("TUESDAY");
        TUESDAY_ENABLED = BUILDER.comment("Is Tuesday an open day?").define("enabled", true);
        TUESDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "19:00");
        TUESDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("WEDNESDAY");
        WEDNESDAY_ENABLED = BUILDER.comment("Is Wednesday an open day?").define("enabled", true);
        WEDNESDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "19:00");
        WEDNESDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("THURSDAY");
        THURSDAY_ENABLED = BUILDER.comment("Is Thursday an open day?").define("enabled", true);
        THURSDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "19:00");
        THURSDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("FRIDAY");
        FRIDAY_ENABLED = BUILDER.comment("Is Friday an open day?").define("enabled", true);
        FRIDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "19:00");
        FRIDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("SATURDAY");
        SATURDAY_ENABLED = BUILDER.comment("Is Saturday an open day?").define("enabled", false);
        SATURDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "15:00");
        SATURDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.push("SUNDAY");
        SUNDAY_ENABLED = BUILDER.comment("Is Sunday an open day?").define("enabled", false);
        SUNDAY_OPEN    = BUILDER.comment("Opening time (HH:MM).").define("open",  "15:00");
        SUNDAY_CLOSE   = BUILDER.comment("Closing time (HH:MM).").define("close", "23:59");
        BUILDER.pop();

        BUILDER.pop(); // Days

        // ─── Messages ─────────────────────────────────────────────────────────
        BUILDER.push("Schedule Messages");

        MSG_SERVER_CLOSED = BUILDER
                .comment("Message sent to players when the server closes or they try to join outside hours.",
                        "Placeholders: {open} {close} {day}")
                .define("msgServerClosed",
                        "§c§lThe server is now closed. See you on {day} at {open}!");

        MSG_SERVER_OPENED = BUILDER
                .comment("Message broadcast when the server opens.",
                        "Placeholders: {open} {close} {day}")
                .define("msgServerOpened",
                        "§a§lThe server is now open! Closing at {close}. Welcome!");

        MSG_WARNING = BUILDER
                .comment("Warning message sent before closing.",
                        "Placeholders: {minutes} {close}")
                .define("msgWarning",
                        "§e§lWarning: §r§eThe server closes in §c{minutes} minutes §e(at {close}).");

        MSG_CLOSING_IMMINENT = BUILDER
                .comment("Last warning message (1 minute before closing).",
                        "Placeholders: {minutes} {close}")
                .define("msgClosingImminent",
                        "§c§lThe server closes in {minutes} minute(s)! Finish what you're doing!");

        BUILDER.pop();

        // ─── Welcome ──────────────────────────────────────────────────────────
        BUILDER.push("Welcome Message");

        ENABLE_WELCOME = BUILDER
                .comment("Enable the welcome message on player join.")
                .define("enableWelcome", true);

        WELCOME_LINES = BUILDER
                .comment("Lines of the welcome message. Supports & and § color codes.",
                        "Placeholder: {player}")
                .defineList("welcomeLines",
                        java.util.Arrays.asList(
                                "§6§m------------------------------------",
                                "§e§lWelcome to the server, {player}!",
                                "§7Enjoy your stay.",
                                "§6§m------------------------------------"),
                        obj -> obj instanceof String);

        WELCOME_SOUND = BUILDER
                .comment("Sound played on join. Use a Minecraft sound ID.",
                        "Set to '' to disable.")
                .define("welcomeSound", "minecraft:ui.toast.challenge_complete");

        WELCOME_SOUND_VOLUME = BUILDER
                .comment("Welcome sound volume.")
                .defineInRange("welcomeSoundVolume", 1.0, 0.0, 10.0);

        WELCOME_SOUND_PITCH = BUILDER
                .comment("Welcome sound pitch.")
                .defineInRange("welcomeSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.pop();

        // ─── Death RP Schedule ──────────────────────────────────────────────────────────
        BUILDER.push("Death Hours");
        DEATH_HOURS_ENABLED = BUILDER
                .comment("If true, RP death is only active during the configured time slots.",
                        "When false, death RP follows only the manual toggle (existing behaviour).")
                .define("deathHoursEnabled", false);
        DEATH_HOURS_SLOTS = BUILDER
                .comment("Time slots during which RP death is active. Format: HH:MM-HH:MM.",
                        "Supports cross-midnight. Example: [\"22:00-23:59\", \"00:00-06:00\"]")
                .defineList("deathHoursSlots",
                        java.util.Arrays.asList("22:00-23:59"), obj -> obj instanceof String);
        BUILDER.pop();

        // ─── HRP Schedule ──────────────────────────────────────────────────────────
        BUILDER.push("HRP Hours");
        ENABLE_HRP_HOURS = BUILDER
                .comment("Enable HRP (out-of-roleplay) hour management.",
                        "When false, this section is completely ignored.")
                .define("enableHrpHours", false);
        HRP_TOLERATED_SLOTS = BUILDER
                .comment("Slots where HRP is tolerated (noted but not punished). Format: HH:MM-HH:MM.")
                .defineList("hrpToleratedSlots",
                        java.util.Arrays.asList("21:00-22:00"), obj -> obj instanceof String);
        HRP_ALLOWED_SLOTS = BUILDER
                .comment("Slots where HRP is fully allowed. Format: HH:MM-HH:MM.")
                .defineList("hrpAllowedSlots",
                        java.util.Arrays.asList("22:00-23:59"), obj -> obj instanceof String);
        HRP_TOLERATED_MESSAGE = BUILDER
                .comment("Message broadcast when HRP-tolerated hours begin. Placeholders: {start} {end}")
                .define("hrpToleratedMessage", "§6[HRP] §fHRP is tolerated until {end}.");
        HRP_ALLOWED_MESSAGE = BUILDER
                .comment("Message broadcast when HRP-allowed hours begin. Placeholders: {start} {end}")
                .define("hrpAllowedMessage", "§a[HRP] §fHRP is freely allowed until {end}.");
        HRP_MESSAGE_MODE = BUILDER
                .comment("Display mode for HRP messages: CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("hrpMessageMode", "CHAT");
        BUILDER.pop();


        SPEC = BUILDER.build();
    }
}