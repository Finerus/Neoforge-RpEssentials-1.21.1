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

    // =========================================================================
    // SELF NICK
    // =========================================================================
    public static final ModConfigSpec.BooleanValue ENABLE_SELF_NICK;
    public static final ModConfigSpec.IntValue     SELF_NICK_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue SELF_NICK_ALLOW_COLORS;
    public static final ModConfigSpec.IntValue     SELF_NICK_MAX_LENGTH;

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
        BUILDER.pop();

        // ── ACTION / ME ───────────────────────────────────────────────────────
        BUILDER.push("Action");
        ACTION_DISTANCE = BUILDER
                .comment("Radius in blocks for /rp action and /me messages.")
                .defineInRange("actionDistance", 16, 1, 256);
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
        DICE_ROLL_DISTANCE   = BUILDER.comment("Radius (-1 = global).").defineInRange("diceRollDistance", 32, -1, 256);
        BUILDER.pop();

        // ── COOLDOWN ──────────────────────────────────────────────────────────
        BUILDER.push("RP Cooldowns");
        ACTION_COOLDOWN_SECONDS    = BUILDER.comment("Cooldown for /rp action and /me (0=disabled).").defineInRange("actionCooldownSeconds",    0, 0, 300);
        COMMERCE_COOLDOWN_SECONDS  = BUILDER.comment("Cooldown for /rp commerce (0=disabled).").defineInRange("commerceCooldownSeconds",  0, 0, 300);
        INCOGNITO_COOLDOWN_SECONDS = BUILDER.comment("Cooldown for /rp incognito (0=disabled).").defineInRange("incognitoCooldownSeconds", 0, 0, 300);
        BUILDER.pop();

        // ── SELF NICK ─────────────────────────────────────────────────────
        BUILDER.push("Self Nick");
        ENABLE_SELF_NICK = BUILDER
                .comment("Allow players to set their own nickname via /rp selfnick.")
                .define("enableSelfNick", false);
        SELF_NICK_COOLDOWN_SECONDS = BUILDER
                .comment("Cooldown for /rp selfnick in seconds (0 = disabled).")
                .defineInRange("selfNickCooldownSeconds", 300, 0, 86400);
        SELF_NICK_ALLOW_COLORS = BUILDER
                .comment("Allow color codes (& and §) in self-assigned nicknames.")
                .define("selfNickAllowColors", false);
        SELF_NICK_MAX_LENGTH = BUILDER
                .comment("Maximum nickname length (excluding color codes).")
                .defineInRange("selfNickMaxLength", 24, 1, 64);
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

        SPEC = BUILDER.build();
    }
}