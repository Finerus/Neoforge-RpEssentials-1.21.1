package net.rp.rpessentials;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

public class RpEssentialsConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // === OBFUSCATION CONFIGURATION ===
    public static final ModConfigSpec.IntValue PROXIMITY_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_BLUR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_VISIBLE_LIST;
    public static final ModConfigSpec.BooleanValue BLUR_SPECTATORS;
    public static final ModConfigSpec.BooleanValue WHITELIST_EXEMPT_PROFESSIONS;
    public static final ModConfigSpec.IntValue OBFUSCATED_NAME_LENGTH;
    public static final ModConfigSpec.BooleanValue OBFUSCATE_PREFIX;
    public static final ModConfigSpec.BooleanValue ENABLE_SNEAK_STEALTH;
    public static final ModConfigSpec.IntValue SNEAK_PROXIMITY_DISTANCE;
    public static final ModConfigSpec.BooleanValue OPS_SEE_ALL;
    public static final ModConfigSpec.BooleanValue DEBUG_SELF_BLUR;
    public static final ModConfigSpec.BooleanValue HIDE_NAMETAGS;
    public static final ModConfigSpec.BooleanValue SHOW_NAMETAG_PREFIX_SUFFIX;

    // === PERMISSIONS SYSTEM ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAFF_TAGS;
    public static final ModConfigSpec.IntValue OP_LEVEL_BYPASS;
    public static final ModConfigSpec.BooleanValue USE_LUCKPERMS_GROUPS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LUCKPERMS_STAFF_GROUPS;

    // === WORLD BORDER & ZONES ===
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_BORDER_WARNING;
    public static final ModConfigSpec.IntValue WORLD_BORDER_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> WORLD_BORDER_MESSAGE;
    public static final ModConfigSpec.IntValue WORLD_BORDER_CHECK_INTERVAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMED_ZONES;
    public static final ModConfigSpec.ConfigValue<String> ZONE_MESSAGE_MODE;

    // Activation & comportement
    public static ModConfigSpec.BooleanValue DEATH_RP_GLOBAL_ENABLED;
    public static ModConfigSpec.BooleanValue DEATH_RP_WHITELIST_REMOVE;

    // Message de mort + son global
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_DEATH_MESSAGE;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_DEATH_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_DEATH_SOUND_PITCH;

    // Notification individuelle (activation)
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_ENABLE_MODE;
    // Notification individuelle (désactivation)
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_DISABLE_MODE;
    // Son individuel de toggle
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_PLAYER_TOGGLE_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH;

    // Notification globale toggle (activation)
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_ENABLE_MODE;
    // Notification globale toggle (désactivation)
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MSG;
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_DISABLE_MODE;
    // Son global de toggle
    public static ModConfigSpec.ConfigValue<String> DEATH_RP_GLOBAL_TOGGLE_SOUND;
    public static ModConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME;
    public static ModConfigSpec.DoubleValue         DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH;

    public static ModConfigSpec.ConfigValue<List<? extends String>> ROLES;

    static {
        // ===============================================================================
        // CATEGORY: OBFUSCATION (TabList & Nametags)
        // ===============================================================================
        BUILDER.push("Obfuscation Settings");

        PROXIMITY_DISTANCE = BUILDER
                .comment("CONFIGURATION: Proximity Distance",
                        "The distance (in blocks) required to see another player's name clearly.",
                        "If the player is further away, the name will be replaced by '???'.",
                        "Default value: 8 blocks.")
                .defineInRange("proximityDistance", 8, 1, 128);

        ENABLE_BLUR = BUILDER
                .comment("CONFIGURATION: Blur Effect",
                        "Enables or disables the name blurring system.",
                        "Setting this to 'false' completely disables the client-side visual effect.")
                .define("enableBlur", true);

        OBFUSCATED_NAME_LENGTH = BUILDER
                .comment("CONFIGURATION: Hidden Name Length",
                        "Number of characters used to replace the name (e.g., 5 results in '?????').")
                .defineInRange("obfuscatedNameLength", 5, 1, 16);

        OBFUSCATE_PREFIX = BUILDER
                .comment("CONFIGURATION: Hide Ranks/Prefixes",
                        "If 'true', prefixes (ranks, titles) will also be hidden in the TabList.",
                        "Recommended for strict RP.")
                .define("obfuscatePrefix", true);

        ENABLE_SNEAK_STEALTH = BUILDER
                .comment("CONFIGURATION: Sneak Stealth Mode",
                        "If 'true', players who are sneaking become harder to detect.",
                        "Their name will only be visible at a much closer distance.")
                .define("enableSneakStealth", true);

        SNEAK_PROXIMITY_DISTANCE = BUILDER
                .comment("CONFIGURATION: Sneak Detection Distance",
                        "The distance (in blocks) at which sneaking players can be detected.",
                        "Only applies when enableSneakStealth is true.",
                        "Default value: 2 blocks.")
                .defineInRange("sneakProximityDistance", 2, 1, 32);

        OPS_SEE_ALL = BUILDER
                .comment("CONFIGURATION: Admin View",
                        "If 'true', operators and staff always see all names clearly.")
                .define("opsSeeAll", true);

        DEBUG_SELF_BLUR = BUILDER
                .comment("DEBUG: Self-Blur",
                        "If 'true', applies the blur to yourself for testing purposes.",
                        "Never leave enabled in production.")
                .define("debugSelfBlur", false);

        HIDE_NAMETAGS = BUILDER
                .comment("CONFIGURATION: Hide Nametags",
                        "If 'true', hides all player nametags above their heads.",
                        "Uses scoreboard teams to hide names server-side.")
                .define("hideNametags", false);

        SHOW_NAMETAG_PREFIX_SUFFIX = BUILDER
                .comment("CONFIGURATION: Show Prefix/Suffix on Nametags",
                        "If 'true', displays LuckPerms prefix/suffix above player heads.")
                .define("showNametagPrefixSuffix", true);

        WHITELIST = BUILDER
                .comment("WHITELIST: Immune Players",
                        "List of usernames that always see everything clearly, even without OP.",
                        "These players are never obfuscated for others either.")
                .defineList("whitelist", List.of(), obj -> obj instanceof String);

        BLACKLIST = BUILDER
                .comment("BLACKLIST: Always Hidden Players",
                        "List of usernames that are always obfuscated, regardless of proximity.")
                .defineList("blacklist", List.of(), obj -> obj instanceof String);

        ALWAYS_VISIBLE_LIST = BUILDER
                .comment("ALWAYS VISIBLE: Always Shown in TabList",
                        "List of usernames that are always shown clearly in the TabList.")
                .defineList("alwaysVisibleList", List.of(), obj -> obj instanceof String);

        BLUR_SPECTATORS = BUILDER
                .comment("CONFIGURATION: Blur Spectators",
                        "If 'true', spectators are also subject to name blurring.")
                .define("blurSpectators", false);

        WHITELIST_EXEMPT_PROFESSIONS = BUILDER
                .comment("CONFIGURATION: Whitelist Exempts Profession Restrictions",
                        "If 'true', players in the whitelist are also exempt from profession restrictions.")
                .define("whitelistExemptProfessions", false);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: PERMISSIONS
        // ===============================================================================
        BUILDER.push("Permissions System");

        STAFF_TAGS = BUILDER
                .comment("List of LuckPerms tags/groups considered as 'staff'.",
                        "Used to determine who receives staff notifications.")
                .defineList("staffTags", Arrays.asList("admin", "moderateur", "modo", "staff", "builder"), obj -> obj instanceof String);

        OP_LEVEL_BYPASS = BUILDER
                .comment("Minimum OP level required to bypass all restrictions.",
                        "Set to 0 to disable OP bypass entirely.")
                .defineInRange("opLevelBypass", 2, 0, 4);

        USE_LUCKPERMS_GROUPS = BUILDER
                .comment("If 'true', uses LuckPerms groups to determine staff status.",
                        "If 'false', uses OP level only.")
                .define("useLuckPermsGroups", true);

        LUCKPERMS_STAFF_GROUPS = BUILDER
                .comment("LuckPerms groups considered as staff.",
                        "Only used if useLuckPermsGroups is true.")
                .defineList("luckPermsStaffGroups", Arrays.asList("admin", "moderateur", "staff"), obj -> obj instanceof String);

        BUILDER.pop();

        // ===============================================================================
        // CATEGORY: WORLD BORDER & ZONES
        // ===============================================================================
        BUILDER.push("World Border Warning");

        ENABLE_WORLD_BORDER_WARNING = BUILDER
                .comment("Enable warning when players reach world border distance.")
                .define("enableWorldBorderWarning", true);

        WORLD_BORDER_DISTANCE = BUILDER
                .comment("Distance from spawn (in blocks) before warning is triggered.")
                .defineInRange("worldBorderDistance", 2000, 100, 100000);

        WORLD_BORDER_MESSAGE = BUILDER
                .comment("Message displayed when player reaches border.",
                        "Variables: {distance}, {player}")
                .define("worldBorderMessage", "§c§l⚠ WARNING §r§7You've reached the limit of the world! (§c{distance} blocks§7)");

        WORLD_BORDER_CHECK_INTERVAL = BUILDER
                .comment("Check interval in ticks (20 ticks = 1 second).",
                        "Higher values = less frequent checks = better performance.")
                .defineInRange("worldBorderCheckInterval", 40, 20, 200);

        NAMED_ZONES = BUILDER
                .comment("Named zones that trigger a message when entered/exited.",
                        "Format: name;centerX;centerZ;radius;messageEnter;messageExit",
                        "Example: Village;100;200;150;§aBienvenue au Village!;§7Vous quittez le Village.")
                .defineList("namedZones", Arrays.asList(), obj -> obj instanceof String);

        ZONE_MESSAGE_MODE = BUILDER
                .comment("Display mode for zone and world border messages.",
                        "IMMERSIVE = ImmersiveMessageAPI overlay (requires client mod).",
                        "CHAT = Standard chat message.",
                        "ACTION_BAR = Action bar (vanilla, no client mod needed).")
                .define("zoneMessageMode", "ACTION_BAR");

        BUILDER.pop();

        BUILDER.push("DeathRP");

        DEATH_RP_GLOBAL_ENABLED = BUILDER
                .comment("Etat global du systeme de mort RP. Quand actif, tous les joueurs sans override individuel sont concernes.")
                .define("globalEnabled", false);

        DEATH_RP_WHITELIST_REMOVE = BUILDER
                .comment("Retirer automatiquement le joueur de la whitelist lors d'une mort RP.")
                .define("whitelistRemove", false);

        BUILDER.push("deathEvent");

        DEATH_RP_DEATH_MESSAGE = BUILDER
                .comment("Message diffuse a tous les joueurs lors d'une mort RP. Variables : %player% (nickname), %realname% (nom MC). Supporte les codes couleur & et §.")
                .define("deathMessage", "&c[Mort RP] &f%player% &7(%realname%) &cvient de perdre la vie de facon permanente.");

        DEATH_RP_DEATH_SOUND = BUILDER
                .comment("Son joue a tous les joueurs lors d'une mort RP. Format : namespace:sound_id. 'none' pour desactiver.")
                .define("deathSound", "minecraft:entity.wither.death");

        DEATH_RP_DEATH_SOUND_VOLUME = BUILDER
                .comment("Volume du son de mort RP (0.0 - 10.0).")
                .defineInRange("deathSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_DEATH_SOUND_PITCH = BUILDER
                .comment("Pitch du son de mort RP (0.5 - 2.0).")
                .defineInRange("deathSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.pop(); // deathEvent

        BUILDER.push("playerToggle");

        DEATH_RP_PLAYER_ENABLE_MSG = BUILDER
                .comment("Message envoye au joueur quand sa mort RP est activee individuellement. Variables : %player%, %realname%.")
                .define("enableMessage", "&6[Mort RP] &fVotre mort RP a ete &aactivee&f. Votre prochaine mort sera definitive.");

        DEATH_RP_PLAYER_ENABLE_MODE = BUILDER
                .comment("Mode d'affichage du message d'activation individuel : CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("enableMessageMode", "CHAT");

        DEATH_RP_PLAYER_DISABLE_MSG = BUILDER
                .comment("Message envoye au joueur quand sa mort RP est desactivee individuellement. Variables : %player%, %realname%.")
                .define("disableMessage", "&6[Mort RP] &fVotre mort RP a ete &cdesactivee&f.");

        DEATH_RP_PLAYER_DISABLE_MODE = BUILDER
                .comment("Mode d'affichage du message de desactivation individuel : CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("disableMessageMode", "CHAT");

        DEATH_RP_PLAYER_TOGGLE_SOUND = BUILDER
                .comment("Son joue au joueur lors d'un toggle individuel. 'none' pour desactiver.")
                .define("toggleSound", "minecraft:block.note_block.pling");

        DEATH_RP_PLAYER_TOGGLE_SOUND_VOLUME = BUILDER
                .comment("Volume du son de toggle individuel (0.0 - 10.0).")
                .defineInRange("toggleSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_PLAYER_TOGGLE_SOUND_PITCH = BUILDER
                .comment("Pitch du son de toggle individuel (0.5 - 2.0).")
                .defineInRange("toggleSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.pop(); // playerToggle

        BUILDER.push("globalToggle");

        DEATH_RP_GLOBAL_ENABLE_MSG = BUILDER
                .comment("Message diffuse quand le systeme de mort RP est active globalement. Variables : %staff%.")
                .define("enableMessage", "&6[Mort RP] &fLe systeme de mort RP a ete &aactive &fpar %staff%.");

        DEATH_RP_GLOBAL_ENABLE_MODE = BUILDER
                .comment("Mode d'affichage lors de l'activation globale : CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("enableMessageMode", "CHAT");

        DEATH_RP_GLOBAL_DISABLE_MSG = BUILDER
                .comment("Message diffuse quand le systeme de mort RP est desactive globalement. Variables : %staff%.")
                .define("disableMessage", "&6[Mort RP] &fLe systeme de mort RP a ete &cdesactive &fpar %staff%.");

        DEATH_RP_GLOBAL_DISABLE_MODE = BUILDER
                .comment("Mode d'affichage lors de la desactivation globale : CHAT, ACTION_BAR, TITLE, IMMERSIVE.")
                .define("disableMessageMode", "CHAT");

        DEATH_RP_GLOBAL_TOGGLE_SOUND = BUILDER
                .comment("Son joue a tous lors d'un toggle global. 'none' pour desactiver.")
                .define("globalToggleSound", "minecraft:ui.toast.challenge_complete");

        DEATH_RP_GLOBAL_TOGGLE_SOUND_VOLUME = BUILDER
                .comment("Volume du son de toggle global (0.0 - 10.0).")
                .defineInRange("globalToggleSoundVolume", 1.0, 0.0, 10.0);

        DEATH_RP_GLOBAL_TOGGLE_SOUND_PITCH = BUILDER
                .comment("Pitch du son de toggle global (0.5 - 2.0).")
                .defineInRange("globalToggleSoundPitch", 1.0, 0.5, 2.0);

        BUILDER.push("Roles");
        ROLES = BUILDER
                .comment("Configurable roles for /rpessentials setrole.",
                        "Format: roleId;lpGroup",
                        "Example: [\"admin;admin\", \"modo;modo\", \"builder;builder\", \"joueur;joueur\"]",
                        "The tag added = roleId. The LP parent set = lpGroup.",
                        "All other role tags are removed automatically when setting a new role.")
                .defineList("roles",
                        java.util.Arrays.asList("admin;admin", "modo;modo", "builder;builder", "joueur;joueur"),
                        obj -> obj instanceof String && ((String) obj).contains(";"));
        BUILDER.pop();

        BUILDER.pop(); // globalToggle

        BUILDER.pop(); // DeathRP

        SPEC = BUILDER.build();
    }
}
