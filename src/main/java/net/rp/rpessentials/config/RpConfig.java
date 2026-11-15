package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration for RP gameplay commands.
 * File: config/rpessentials/rpessentials-rp.toml
 */
public class RpConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHEET_ENABLED;
    public static final ModConfigSpec.BooleanValue SHEET_NAME;
    public static final ModConfigSpec.BooleanValue SHEET_AGE;
    public static final ModConfigSpec.BooleanValue SHEET_RACE;
    public static final ModConfigSpec.BooleanValue SHEET_LANGUAGES;
    public static final ModConfigSpec.BooleanValue SHEET_APPEARANCE;
    public static final ModConfigSpec.BooleanValue SHEET_DESCRIPTION;
    public static final ModConfigSpec.BooleanValue SHEET_LORE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SHEET_CUSTOM_FIELDS;
    public static final ModConfigSpec.IntValue SHEET_MAX_TEXT_LENGTH;

    // =========================================================================
    // AFK
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> AFK_DIMENSION;
    public static final ModConfigSpec.DoubleValue         AFK_X;
    public static final ModConfigSpec.DoubleValue         AFK_Y;
    public static final ModConfigSpec.DoubleValue         AFK_Z;
    public static final ModConfigSpec.DoubleValue         AFK_YAW;
    public static final ModConfigSpec.DoubleValue         AFK_PITCH;
    public static final ModConfigSpec.IntValue AFK_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue AFK_COMBAT_BLOCK_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AFK_COMMANDS;

    // =========================================================================
    // SPAWN
    // =========================================================================
    public static final ModConfigSpec.BooleanValue        ENABLE_SPAWN_COMMAND;
    public static final ModConfigSpec.IntValue            SPAWN_WARMUP_SECONDS;
    public static final ModConfigSpec.IntValue            AFK_WARMUP_SECONDS;
    public static final ModConfigSpec.BooleanValue        TELEPORT_CANCEL_ON_MOVE;
    public static final ModConfigSpec.BooleanValue        TELEPORT_CANCEL_ON_DAMAGE;
    public static final ModConfigSpec.BooleanValue        SPAWN_USE_WORLD_SPAWN;
    public static final ModConfigSpec.ConfigValue<String> SPAWN_DIMENSION;
    public static final ModConfigSpec.DoubleValue         SPAWN_X;
    public static final ModConfigSpec.DoubleValue         SPAWN_Y;
    public static final ModConfigSpec.DoubleValue         SPAWN_Z;
    public static final ModConfigSpec.DoubleValue         SPAWN_YAW;
    public static final ModConfigSpec.DoubleValue         SPAWN_PITCH;
    public static final ModConfigSpec.IntValue            SPAWN_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue            SPAWN_COMBAT_BLOCK_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPAWN_COMMANDS;

    // =========================================================================
    // ACTION / ME
    // =========================================================================
    public static final ModConfigSpec.IntValue ACTION_DISTANCE;

    // =========================================================================
    // ANNONCE
    // =========================================================================
    public static final ModConfigSpec.BooleanValue        ANNONCE_PLAY_SOUND;
    public static final ModConfigSpec.ConfigValue<String> ANNONCE_SOUND;

    // =========================================================================
    // DICE SYSTEM
    // =========================================================================
    public static final ModConfigSpec.BooleanValue                          ENABLE_DICE_SYSTEM;
    public static final ModConfigSpec.ConfigValue<List<? extends String>>   DICE_TYPES;
    public static final ModConfigSpec.IntValue                              DICE_ROLL_DISTANCE;

    // =========================================================================
    // COOLDOWN
    // =========================================================================
    public static final ModConfigSpec.IntValue ACTION_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue COMMERCE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue INCOGNITO_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue DICE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue DICE_ALLOW_EXPRESSIONS;
    public static final ModConfigSpec.BooleanValue DICE_ALLOW_SECRET;
    public static final ModConfigSpec.IntValue DICE_MAX_COUNT;
    public static final ModConfigSpec.IntValue DICE_MAX_SIDES;

    // =========================================================================
    // SELF NICK
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_SELF_NICK;
    public static final ModConfigSpec.IntValue     SELF_NICK_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue SELF_NICK_ALLOW_COLORS;
    public static final ModConfigSpec.IntValue     SELF_NICK_MAX_LENGTH;
    public static final ModConfigSpec.BooleanValue NICK_UNIQUE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NICK_BLACKLIST;

    // =========================================================================
    // IMMERSIVE PRESETS
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<List<? extends String>> IMMERSIVE_PRESETS;

    static {

        // ── AFK ───────────────────────────────────────────────────────────────
        BUILDER.push("AFK");
        AFK_DIMENSION = BUILDER.comment("Dimension to teleport to when using /rp afk.").define("dimension", "minecraft:overworld");
        AFK_X     = BUILDER.comment("X coordinate.").defineInRange("x",     0, -30000000.0, 30000000.0);
        AFK_Y     = BUILDER.comment("Y coordinate.").defineInRange("y",   255, -64.0, 320.0);
        AFK_Z     = BUILDER.comment("Z coordinate.").defineInRange("z",     0, -30000000.0, 30000000.0);
        AFK_YAW   = BUILDER.comment("Yaw.").defineInRange("yaw",   90, -180.0, 180.0);
        AFK_PITCH = BUILDER.comment("Pitch.").defineInRange("pitch",  0, -90.0, 90.0);
        AFK_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown for /rp afk in seconds (0 = disabled).")
                .defineInRange("afkCooldownSeconds", 0, 0, 3600);
        AFK_COMBAT_BLOCK_SECONDS = BUILDER
                .comment("/rp afk is refused for this many seconds after the player was hurt by a mob or player (0 = disabled).")
                .defineInRange("afkCombatBlockSeconds", 0, 0, 600);
        AFK_COMMANDS = BUILDER
                .comment("Commands run after /rp afk, as the player: @s is the player, ~ ~ ~ is the destination,",
                        "{player} is the Minecraft name. They run with OP level 4 and their output is hidden.",
                        "Example: [\"effect give @s minecraft:resistance 30 4 true\", \"playsound minecraft:block.bell.use master @s\"]")
                .defineList("afkCommands", List.of(), obj -> obj instanceof String);
        BUILDER.pop();

        // ── SPAWN ─────────────────────────────────────────────────────────────
        BUILDER.push("Spawn");
        ENABLE_SPAWN_COMMAND = BUILDER
                .comment("Enable /rp spawn for every player.")
                .define("enableSpawnCommand", false);
        SPAWN_USE_WORLD_SPAWN = BUILDER
                .comment("If true, /rp spawn teleports to the world spawn of the overworld.",
                        "If false, it uses the dimension and coordinates below.")
                .define("useWorldSpawn", true);
        SPAWN_DIMENSION = BUILDER.comment("Dimension used when useWorldSpawn is false.").define("dimension", "minecraft:overworld");
        SPAWN_X     = BUILDER.comment("X coordinate.").defineInRange("x",   0, -30000000.0, 30000000.0);
        SPAWN_Y     = BUILDER.comment("Y coordinate.").defineInRange("y",  64, -64.0, 320.0);
        SPAWN_Z     = BUILDER.comment("Z coordinate.").defineInRange("z",   0, -30000000.0, 30000000.0);
        SPAWN_YAW   = BUILDER.comment("Yaw.").defineInRange("yaw",   0, -180.0, 180.0);
        SPAWN_PITCH = BUILDER.comment("Pitch.").defineInRange("pitch", 0, -90.0, 90.0);
        SPAWN_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown for /rp spawn in seconds (0 = disabled).")
                .defineInRange("spawnCooldownSeconds", 0, 0, 3600);
        SPAWN_COMBAT_BLOCK_SECONDS = BUILDER
                .comment("/rp spawn is refused for this many seconds after the player was hurt by a mob or player (0 = disabled).")
                .defineInRange("spawnCombatBlockSeconds", 0, 0, 600);
        SPAWN_COMMANDS = BUILDER
                .comment("Commands run after /rp spawn, as the player: @s is the player, ~ ~ ~ is the destination,",
                        "{player} is the Minecraft name. They run with OP level 4 and their output is hidden.")
                .defineList("spawnCommands", List.of(), obj -> obj instanceof String);
        BUILDER.pop();

        // ── ACTION / ME ───────────────────────────────────────────────────────
        BUILDER.push("Action");
        ACTION_DISTANCE = BUILDER
                .comment("Radius in blocks for /rp action and /me messages.")
                .defineInRange("actionDistance", 24, 1, 256);
        BUILDER.pop();

        // ── DICE ──────────────────────────────────────────────────────────────
        BUILDER.push("Dice System");
        ENABLE_DICE_SYSTEM = BUILDER.comment("Enable the dice roll system.").define("enableDiceSystem", true);
        DICE_TYPES = BUILDER
                .comment("Available dice types.",
                        "Format: name;maxValue  (e.g. d6;6)",
                        "Or custom faces: name;face1,face2,face3 (e.g. coin;Heads,Tails)")
                .defineList("diceTypes",
                        Arrays.asList("d4;4","d6;6","d8;8","d10;10","d12;12","d20;20","d100;100"),
                        obj -> obj instanceof String && ((String)obj).contains(";"));
        DICE_ROLL_DISTANCE   = BUILDER.comment("Radius (-1 = global).").defineInRange("diceRollDistance", 12, -1, 256);
        BUILDER.pop();

        // ── COOLDOWN ──────────────────────────────────────────────────────────
        BUILDER.push("RP Cooldowns");
        ACTION_COOLDOWN_SECONDS    = BUILDER.comment("Cooldown for /rp action and /me (0=disabled).").defineInRange("actionCooldownSeconds",    0, 0, 300);
        COMMERCE_COOLDOWN_SECONDS  = BUILDER.comment("Cooldown for /rp commerce (0=disabled).").defineInRange("commerceCooldownSeconds",  300, 0, 300);
        INCOGNITO_COOLDOWN_SECONDS = BUILDER.comment("Cooldown for /rp incognito (0=disabled).").defineInRange("incognitoCooldownSeconds", 30, 0, 300);
        DICE_COOLDOWN_SECONDS = BUILDER.comment("Cooldown for dice rolls (0=disabled).").defineInRange("diceCooldownSeconds", 5, 0, 300);
        BUILDER.pop();

        // ── SELF NICK ─────────────────────────────────────────────────────
        BUILDER.push("Self Nick");
        ENABLE_SELF_NICK = BUILDER
                .comment("Allow players to set their own nickname via /rp selfnick.")
                .define("enableSelfNick", false);
        SELF_NICK_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown for /rp selfnick in seconds (0 = disabled).")
                .defineInRange("selfNickCooldownSeconds", 3600, 0, 86400);
        SELF_NICK_ALLOW_COLORS = BUILDER
                .comment("Allow color codes (& and §) in self-assigned nicknames.")
                .define("selfNickAllowColors", false);
        SELF_NICK_MAX_LENGTH = BUILDER
                .comment("Maximum nickname length (excluding color codes).")
                .defineInRange("selfNickMaxLength", 15, 1, 64);
        NICK_UNIQUE = BUILDER
                .comment("If true, two players cannot have the same nickname (case and colors ignored).")
                .define("nicknamesUnique", false);
        NICK_BLACKLIST = BUILDER
                .comment("Words forbidden in nicknames (case and colors ignored). Staff are not affected.")
                .defineList("nicknameBlacklist", List.of(), obj -> obj instanceof String);
        BUILDER.pop();

        // ── ANNONCE ───────────────────────────────────────────────────────────
        BUILDER.push("Annonce");
        ANNONCE_PLAY_SOUND = BUILDER.comment("Play a sound to all players on announcement.").define("playSound", true);
        ANNONCE_SOUND      = BUILDER.comment("Sound resource location.").define("sound", "minecraft:entity.arrow.hit_player");
        BUILDER.pop();

        // ── IMMERSIVE PRESETS ─────────────────────────────────────────────────
        BUILDER.push("Immersive Presets");
        IMMERSIVE_PRESETS = BUILDER
                .comment(
                        "Presets for ImmersiveMessages display mode.",
                        "Format: name;duration;fadeIn;fadeOut  (floats, seconds)",
                        "",
                        "Usage in display mode configs:",
                        "  'IMMERSIVE'        → uses preset named 'default'",
                        "  'IMMERSIVE:death'  → uses preset named 'death'",
                        "  'IMMERSIVE:zone'   → uses preset named 'zone'",
                        "",
                        "Fallback to ACTION_BAR if ImmersiveMessages is absent client-side.",
                        "",
                        "Predefined presets (can be overridden):",
                        "  default — 3s, fade 0.5s in/out  → general purpose",
                        "  death   — 5s, fade 0.5s in/out  → death RP events",
                        "  zone    — 3s, fade 0.3s in/out  → zone entry/exit",
                        "  alert   — 4s, fade 0.2s in, 1s out → warnings",
                        "  hrp     — 4s, fade 0.5s in/out  → HRP hours notification",
                        "  title   — 5s, fade 1s in/out    → announcements"
                )
                .defineList("presets",
                        Arrays.asList(
                                "default;3.0;0.5;0.5",
                                "death;5.0;0.5;0.5",
                                "zone;3.0;0.3;0.3",
                                "alert;4.0;0.2;1.0",
                                "hrp;4.0;0.5;0.5",
                                "title;5.0;1.0;1.0"
                        ),
                        obj -> obj instanceof String && ((String)obj).contains(";"));
        BUILDER.pop();

        BUILDER.push("Dice Rolls");
        DICE_ALLOW_EXPRESSIONS = BUILDER
                .comment("Allow /roll with expressions: d20, 2d6+3, d100-10, <dice name>+2, adv d20 (advantage), dis d20 (disadvantage).",
                        "Configured dice names (diceTypes) always work.")
                .define("allowExpressions", true);
        DICE_ALLOW_SECRET = BUILDER
                .comment("Allow /roll secret <dice>: the result is only shown to the roller and the staff.")
                .define("allowSecretRolls", true);
        DICE_MAX_COUNT = BUILDER.comment("Maximum number of dice in one expression (2d6 = 2).").defineInRange("maxDiceCount", 20, 1, 100);
        DICE_MAX_SIDES = BUILDER.comment("Maximum number of sides of a dice in an expression.").defineInRange("maxDiceSides", 1000, 2, 100000);
        BUILDER.pop();

        BUILDER.push("Teleport Warmup");
        SPAWN_WARMUP_SECONDS = BUILDER
                .comment("Seconds the player must wait without moving before /rp spawn teleports them. 0 = instant.",
                        "A countdown is shown in the action bar. Other mods can add animations through RpTeleportEvent.")
                .defineInRange("spawnWarmupSeconds", 0, 0, 120);
        AFK_WARMUP_SECONDS = BUILDER
                .comment("Seconds the player must wait without moving before /rp afk teleports them. 0 = instant.")
                .defineInRange("afkWarmupSeconds", 0, 0, 120);
        TELEPORT_CANCEL_ON_MOVE = BUILDER
                .comment("Cancel the teleport when the player moves during the delay.")
                .define("cancelOnMove", true);
        TELEPORT_CANCEL_ON_DAMAGE = BUILDER
                .comment("Cancel the teleport when the player takes damage during the delay (prevents escaping a fight).")
                .define("cancelOnDamage", true);
        BUILDER.pop();

        BUILDER.push("Character Sheet");
        SHEET_ENABLED = BUILDER
                .comment("Character sheets, filled by the staff in the Profile tab of the player profile GUI",
                        "(button \"Character sheet\"), prepared profiles included. Players cannot see them.")
                .define("enableCharacterSheets", true);
        SHEET_NAME = BUILDER.comment("Show the RP name field.").define("fieldName", true);
        SHEET_AGE = BUILDER.comment("Show the age field.").define("fieldAge", true);
        SHEET_RACE = BUILDER.comment("Show the race field.").define("fieldRace", true);
        SHEET_LANGUAGES = BUILDER.comment("Show the languages field (free text, one or more languages).").define("fieldLanguages", true);
        SHEET_APPEARANCE = BUILDER.comment("Show the appearance field (height, build, distinctive marks...). Several lines.").define("fieldAppearance", true);
        SHEET_DESCRIPTION = BUILDER.comment("Show the description field. Several lines.").define("fieldDescription", true);
        SHEET_LORE = BUILDER.comment("Show the lore / background field. Several lines.").define("fieldLore", true);
        SHEET_CUSTOM_FIELDS = BUILDER
                .comment("Extra fields, one per line: id;Label;LINE or id;Label;TEXT",
                        "  LINE = one line (for example a faction or a rank), TEXT = several lines with the text editor.",
                        "  The id is a lowercase word (letters, digits, _); changing it loses what was written in that field.",
                        "Example: [\"faction;Faction;LINE\", \"religion;Religion;LINE\", \"secrets;Secrets;TEXT\"]")
                .defineList("customFields", java.util.List.of(), o -> o instanceof String s && s.split(";").length >= 2);
        SHEET_MAX_TEXT_LENGTH = BUILDER
                .comment("Maximum length of a several-lines field. One-line fields are limited to 128 characters.")
                .defineInRange("maxTextLength", 4000, 100, 20000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}