package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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
