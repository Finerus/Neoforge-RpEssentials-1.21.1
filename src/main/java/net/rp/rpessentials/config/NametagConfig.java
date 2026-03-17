package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration du système de nametag.
 * Fichier : config/rpessentials/rpessentials-nametag.toml
 *
 * Ce fichier est SERVER-side (chargé par le serveur, poussé au client via packet).
 * Le client ne lit jamais ce fichier directement — il reçoit les données via SyncNametagDataPacket.
 */
public class NametagConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // =========================================================================
    // COMPORTEMENT GLOBAL
    // =========================================================================

    /** Si false, aucun nametag custom n'est affiché (comportement actuel du mod). */
    public static final ModConfigSpec.BooleanValue ENABLED;

    /** Si true, le nametag est masqué quand un bloc est entre le joueur et la cible. */
    public static final ModConfigSpec.BooleanValue HIDE_BEHIND_BLOCKS;

    // =========================================================================
    // OBFUSCATION
    // =========================================================================

    /**
     * Si true, le nametag est obfusqué au-delà de OBFUSCATION_DISTANCE.
     * Même logique que l'obfuscation TabList existante.
     */
    public static final ModConfigSpec.BooleanValue OBFUSCATION_ENABLED;

    /**
     * Distance en blocs au-delà de laquelle le nametag est obfusqué.
     * En dessous : nom lisible. Au-delà : nom obfusqué (§k????).
     */
    public static final ModConfigSpec.DoubleValue OBFUSCATION_DISTANCE;

    /**
     * Code couleur (§ ou &) appliqué au texte obfusqué.
     * Ex: "&8" pour gris foncé, "&7" pour gris.
     */
    public static final ModConfigSpec.ConfigValue<String> OBFUSCATION_COLOR;

    /**
     * Longueur du texte obfusqué (nombre de '?' affichés avec §k).
     * Par défaut suit la longueur du nom réel si -1.
     * Mettre une valeur fixe pour ne pas révéler la longueur du nom.
     */
    public static final ModConfigSpec.IntValue OBFUSCATION_LENGTH;

    // =========================================================================
    // FORMAT
    // =========================================================================

    /**
     * Format du nametag quand le joueur est lisible (distance proche).
     * Tokens disponibles : $prefix $name $suffix $profession $title
     *
     * Exemples :
     *   "$prefix $name"               → [Admin] Steve
     *   "$name §7($profession)"       → Steve §7(Forgeron)
     *   "$prefix$name$suffix"         → [Garde] Steve [Garde Royal]
     */
    public static final ModConfigSpec.ConfigValue<String> FORMAT;

    /**
     * Format utilisé quand le joueur est obfusqué (distance lointaine).
     * Seul le token $obfuscated est disponible ici.
     * Ex: "$obfuscated" → §k????
     */
    public static final ModConfigSpec.ConfigValue<String> FORMAT_OBFUSCATED;

    // =========================================================================
    // RENDU
    // =========================================================================

    /**
     * Distance maximale (en blocs) à laquelle le nametag est visible du tout.
     * Au-delà : pas de rendu. Minecraft vanilla est ~64 blocs.
     * -1 = utiliser la valeur vanilla.
     */
    public static final ModConfigSpec.DoubleValue RENDER_DISTANCE;

    /**
     * Si true, afficher le nametag même en sneakant (vanilla le masque en sneak).
     * Utile pour les serveurs RP qui veulent toujours voir les noms.
     */
    public static final ModConfigSpec.BooleanValue SHOW_WHILE_SNEAKING;

    /**
     * Si true, afficher le nametag même quand le joueur n'est pas ciblé (vanilla
     * le masque quand on ne regarde pas dans la direction du joueur).
     */
    public static final ModConfigSpec.BooleanValue ALWAYS_RENDER;

    // =========================================================================
    // STAFF
    // =========================================================================

    /**
     * Si true, les membres du staff (isStaff()) voient toujours les nametags
     * lisibles, indépendamment de la distance et de l'occlusion.
     */
    public static final ModConfigSpec.BooleanValue STAFF_ALWAYS_SEE_REAL;

    static {
        // =====================================================================
        BUILDER.push("Behaviour");

        ENABLED = BUILDER
                .comment(
                        "Master switch for the custom nametag system.",
                        "false = no nametags at all (current default behaviour).",
                        "true  = custom nametags with distance obfuscation and block occlusion."
                )
                .define("enabled", false);

        HIDE_BEHIND_BLOCKS = BUILDER
                .comment(
                        "Hide the nametag when a block is between the viewer and the target.",
                        "Uses a client-side raycast (ClipContext.Block.COLLIDER).",
                        "Has no performance impact when enabled = false."
                )
                .define("hideBehindBlocks", true);

        BUILDER.pop();

        // =====================================================================
        BUILDER.push("Obfuscation");

        OBFUSCATION_ENABLED = BUILDER
                .comment(
                        "Enable distance-based name obfuscation.",
                        "Works the same way as TabList obfuscation — names are replaced",
                        "with §k (obfuscated) characters beyond obfuscationDistance."
                )
                .define("obfuscationEnabled", true);

        OBFUSCATION_DISTANCE = BUILDER
                .comment(
                        "Distance in blocks below which the real name is shown.",
                        "Beyond this distance, the name is obfuscated.",
                        "Should match or complement proximityDistance in rpessentials-core.toml."
                )
                .defineInRange("obfuscationDistance", 10.0, 1.0, 256.0);

        OBFUSCATION_COLOR = BUILDER
                .comment(
                        "Color code (§ or & style) applied to the obfuscated name.",
                        "Examples: '&8' = dark grey, '&7' = grey, '&f' = white."
                )
                .define("obfuscationColor", "&8");

        OBFUSCATION_LENGTH = BUILDER
                .comment(
                        "Fixed length of the obfuscated name (number of §k characters).",
                        "Set to -1 to use the real name's length (reveals name length).",
                        "Set to a fixed value (e.g. 6) to always show the same length."
                )
                .defineInRange("obfuscationLength", -1, -1, 32);

        BUILDER.pop();

        // =====================================================================
        BUILDER.push("Format");

        FORMAT = BUILDER
                .comment(
                        "Nametag format when the player is within obfuscation distance (readable).",
                        "Available tokens:",
                        "  $prefix     — LuckPerms prefix (e.g. '[Admin] ')",
                        "  $name       — Display name (nickname if set, real name otherwise)",
                        "  $realname   — Always the real Minecraft username",
                        "  $suffix     — LuckPerms suffix",
                        "  $profession — First active license/profession id",
                        "  $title      — Not implemented yet, reserved for future use",
                        "Supports § and & color codes.",
                        "Example: '$prefix$name' → '[Admin] Steve'"
                )
                .define("format", "$prefix$name");

        FORMAT_OBFUSCATED = BUILDER
                .comment(
                        "Nametag format when the player is beyond obfuscation distance.",
                        "Available token: $obfuscated (replaced by the obfuscated string).",
                        "Example: '$obfuscated' → §8§k??????"
                )
                .define("formatObfuscated", "$obfuscated");

        BUILDER.pop();

        // =====================================================================
        BUILDER.push("Rendering");

        RENDER_DISTANCE = BUILDER
                .comment(
                        "Maximum distance (blocks) at which any nametag is rendered.",
                        "Set to -1 to use Minecraft's vanilla distance (~64 blocks).",
                        "Lower values improve client performance."
                )
                .defineInRange("renderDistance", -1.0, -1.0, 512.0);

        SHOW_WHILE_SNEAKING = BUILDER
                .comment(
                        "If true, nametags are visible even when the target is sneaking.",
                        "Vanilla hides nametags while sneaking."
                )
                .define("showWhileSneaking", false);

        ALWAYS_RENDER = BUILDER
                .comment(
                        "If true, nametags are always rendered regardless of view angle.",
                        "Vanilla only shows nametags when looking roughly at the player."
                )
                .define("alwaysRender", false);

        BUILDER.pop();

        // =====================================================================
        BUILDER.push("Staff");

        STAFF_ALWAYS_SEE_REAL = BUILDER
                .comment(
                        "If true, staff members (isStaff() = true) always see real names",
                        "regardless of distance and block occlusion.",
                        "Useful for moderation."
                )
                .define("staffAlwaysSeeReal", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
