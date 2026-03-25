package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration for RP gameplay commands.
 * File: config/rpessentials/rpessentials-rp.toml
 * Reload: /rpessentials config reload
 */
public class RpConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // AFK
    // =========================================================================
    public static final ModConfigSpec.ConfigValue<String> AFK_DIMENSION;
    public static final ModConfigSpec.DoubleValue         AFK_X;
    public static final ModConfigSpec.DoubleValue         AFK_Y;
    public static final ModConfigSpec.DoubleValue         AFK_Z;
    public static final ModConfigSpec.DoubleValue         AFK_YAW;
    public static final ModConfigSpec.DoubleValue         AFK_PITCH;

    // =========================================================================
    // ACTION / ME
    // =========================================================================
    public static final ModConfigSpec.IntValue ACTION_DISTANCE;

    // =========================================================================
    // ANNONCE
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ANNONCE_PLAY_SOUND;
    public static final ModConfigSpec.ConfigValue<String> ANNONCE_SOUND;

    // =========================================================================
    // DICE SYSTEM
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_DICE_SYSTEM;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DICE_TYPES;
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> DICE_ROLL_SPY_FORMAT;
    public static final ModConfigSpec.IntValue DICE_ROLL_DISTANCE;

    // =========================================================================
    // COOLDOWN
    // =========================================================================
    public static final ModConfigSpec.IntValue ACTION_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue COMMERCE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue INCOGNITO_COOLDOWN_SECONDS;

    static {

        // ── AFK ───────────────────────────────────────────────────────────────
        BUILDER.push("AFK");

        AFK_DIMENSION = BUILDER
                .comment("Dimension to teleport to when using /rp afk.",
                         "Use the resource location of the dimension (e.g. RpEssentials:afk, minecraft:overworld).")
                .define("dimension", "RpEssentials:afk");

        AFK_X = BUILDER
                .comment("X coordinate of the AFK destination.")
                .defineInRange("x", 0, -30000000.0, 30000000.0);

        AFK_Y = BUILDER
                .comment("Y coordinate of the AFK destination.")
                .defineInRange("y", 255, -64.0, 320.0);

        AFK_Z = BUILDER
                .comment("Z coordinate of the AFK destination.")
                .defineInRange("z", 0, -30000000.0, 30000000.0);

        AFK_YAW = BUILDER
                .comment("Yaw (horizontal rotation) of the AFK destination.")
                .defineInRange("yaw", 90, -180.0, 180.0);

        AFK_PITCH = BUILDER
                .comment("Pitch (vertical rotation) of the AFK destination.")
                .defineInRange("pitch", 0, -90.0, 90.0);

        BUILDER.pop();

        // ── ACTION / ME ───────────────────────────────────────────────────────
        BUILDER.push("Action");

        ACTION_DISTANCE = BUILDER
                .comment("Radius in blocks within which players can see /rp action and /me messages.",
                         "Staff outside this range will still see actions via the spy log.")
                .defineInRange("actionDistance", 16, 1, 256);

        BUILDER.pop();

        // ── DICE ───────────────────────────────────────────────────────
        BUILDER.push("Dice System");

        ENABLE_DICE_SYSTEM = BUILDER
                .comment("Enable the dice roll system.")
                .define("enableDiceSystem", true);

        DICE_TYPES = BUILDER
                .comment("Available dice types.",
                        "Format: name;maxValue  (e.g. d6;6)",
                        "Or custom faces: name;face1,face2,face3 (e.g. coin;Heads,Tails)")
                .defineList("diceTypes",
                        java.util.Arrays.asList("d4;4", "d6;6", "d8;8", "d10;10", "d12;12", "d20;20", "d100;100"),
                        obj -> obj instanceof String && ((String) obj).contains(";"));

        DICE_ROLL_FORMAT = BUILDER
                .comment("Format of the dice roll broadcast.",
                        "Placeholders: {player}, {dice}, {result}")
                .define("diceRollFormat", "§8[🎲] §e{player} §7rolled §6{dice} §7and got §a§l{result}§7!");

        DICE_ROLL_SPY_FORMAT = BUILDER
                .comment("Format shown to staff outside dice roll range.",
                        "Placeholders: {player}, {dice}, {result}")
                .define("diceRollSpyFormat", "§7[DICE-SPY] §e{player} §7rolled §6{dice}§7: §f{result}");

        DICE_ROLL_DISTANCE = BUILDER
                .comment("Radius in blocks within which dice roll results are visible (-1 = global).")
                .defineInRange("diceRollDistance", 32, -1, 256);

        BUILDER.pop();

        // ── COOLDOWN ───────────────────────────────────────────────────────────
        BUILDER.push("RP Cooldowns");

        ACTION_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown in seconds between /rp action and /me uses (0 = disabled).")
                .defineInRange("actionCooldownSeconds", 0, 0, 300);

        COMMERCE_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown in seconds between /rp commerce uses (0 = disabled).")
                .defineInRange("commerceCooldownSeconds", 0, 0, 300);

        INCOGNITO_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown in seconds between /rp incognito uses (0 = disabled).")
                .defineInRange("incognitoCooldownSeconds", 0, 0, 300);

        BUILDER.pop();

        // ── ANNONCE ───────────────────────────────────────────────────────────
        BUILDER.push("Annonce");

        ANNONCE_PLAY_SOUND = BUILDER
                .comment("If true, play a sound to all players when an announcement is sent.")
                .define("playSound", true);

        ANNONCE_SOUND = BUILDER
                .comment("Sound to play on announcement. Use a Minecraft resource location.",
                         "Example: minecraft:entity.arrow.hit_player")
                .define("sound", "minecraft:entity.arrow.hit_player");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
