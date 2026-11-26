package net.rp.rpessentials.api;

import net.rp.rpessentials.gameplay.DiceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.config.ConfigInspector;
import net.rp.rpessentials.identity.NicknameManager;
import net.rp.rpessentials.moderation.DeathRPManager;
import net.rp.rpessentials.moderation.VanishManager;
import net.rp.rpessentials.profession.LicenseManager;
import net.rp.rpessentials.profession.ProfessionRestrictionManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class RpEssentialsAPI {

    private RpEssentialsAPI() {}

    public static boolean hasLicense(UUID player, String profession) { return LicenseManager.hasLicense(player, profession); }

    public static List<String> getLicenses(UUID player) { return LicenseManager.getLicenses(player); }

    public static String getNickname(UUID player) { return NicknameManager.getNickname(player); }

    /** Nickname of the player, or the Minecraft name when there is none. */
    public static String getDisplayName(ServerPlayer player) { return NicknameManager.getDisplayName(player); }

    /** Id of the player's role ("" if none). */
    public static String getRole(ServerPlayer player) { return RpPlaceholders.roleId(player); }

    /** Vanish level of the player, 0 when visible. */
    public static int getVanishLevel(UUID player) { return VanishManager.isVanished(player) ? VanishManager.getLevel(player) : 0; }

    /** Display names of the player's professions. */
    public static List<String> getProfessionNames(ServerPlayer player) { return RpPlaceholders.professionNames(player); }

    /** Value of %rpessentials:name% for a player, null for an unknown variable. */
    public static String getPlaceholder(ServerPlayer player, String name) { return RpPlaceholders.get(player, name); }

    /** Replaces every %rpessentials:...% variable of a text. */
    public static String resolvePlaceholders(ServerPlayer player, String text) { return RpPlaceholders.resolve(player, text); }

    /** Adds a variable %rpessentials:name% that every RpEssentials-aware mod can use. */
    public static void registerPlaceholder(String name, java.util.function.Function<ServerPlayer, String> value) {
        RpPlaceholders.register(name, value);
    }

    public static boolean isStaff(ServerPlayer player) { return RpEssentialsPermissions.isStaff(player); }

    public static boolean isVanished(UUID player) { return VanishManager.isVanished(player); }

    public static boolean isDeathRpEnabled(UUID player) { return DeathRPManager.isDeathRPEnabled(player); }

    public static boolean canCraft(ServerPlayer player, ResourceLocation item) { return ProfessionRestrictionManager.canCraft(player, item); }

    public static boolean canUseItem(ServerPlayer player, ResourceLocation item) { return ProfessionRestrictionManager.canUseItem(player, item); }

    public static boolean canEquip(ServerPlayer player, ResourceLocation item) { return ProfessionRestrictionManager.canEquip(player, item); }

    public static boolean canBreakBlock(ServerPlayer player, ResourceLocation block) { return ProfessionRestrictionManager.canBreakBlock(player, block); }

    /** Dice types configured in rpessentials-rp.toml. */
    public static List<DiceManager.DiceType> getDiceTypes() { return DiceManager.getAvailableDice(); }

    /** A dice by name (case insensitive), or null. */
    public static DiceManager.DiceType getDice(String name) { return DiceManager.getDiceByName(name); }

    /** Full roll as with /roll: cooldown, DiceRollEvent, message to nearby players. False for an unknown dice. */
    public static boolean rollDice(ServerPlayer player, String dice) { return DiceManager.roll(player, dice, false); }

    /** Full roll, optionally ignoring the dice cooldown. */
    public static boolean rollDice(ServerPlayer player, String dice, boolean ignoreCooldown) {
        return DiceManager.roll(player, dice, ignoreCooldown);
    }

    /** Result only: no message, no cooldown, no event. Null for an unknown dice. */
    public static String rollDiceSilently(String dice) { return DiceManager.rollSilently(dice); }

    /**
     * Rolls a dice or an expression ("d20", "2d6+3", "adv d20", configured dice name) without message,
     * cooldown or event, and returns every detail (values, modifier, total). Null when it cannot be rolled.
     */
    public static DiceManager.Roll rollDiceDetailed(String expression) { return DiceManager.rollDetailed(expression); }

    /** Parses an expression without rolling it. The status says why it is refused, if it is. */
    public static DiceManager.Parsed parseDice(String expression) { return DiceManager.parse(expression); }

    /**
     * Teleports a player with the RpEssentials delay and countdown (cancelled if they move or are hurt).
     * Posts RpTeleportEvent with the given kind. seconds = 0 teleports at once.
     * @param after run after the teleport, may be null
     * @return false when the teleport was cancelled by another mod
     */
    public static boolean teleport(net.minecraft.server.level.ServerPlayer player, String kind,
                                   net.minecraft.server.level.ServerLevel level, double x, double y, double z,
                                   float yaw, float pitch, int seconds,
                                   java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> after) {
        return net.rp.rpessentials.gameplay.TeleportWarmupManager.start(player, kind, level, x, y, z, yaw, pitch, seconds, after);
    }

    /** True while a delayed teleport is running for this player. */
    public static boolean isTeleportPending(UUID player) { return net.rp.rpessentials.gameplay.TeleportWarmupManager.isPending(player); }

    // ── Character sheets ─────────────────────────────────────────────────────
    /** Values of a player's character sheet by field id (name, age, race, languages, appearance, description, lore, custom ids). */
    public static Map<String, String> getCharacterSheet(UUID player) {
        return net.rp.rpessentials.identity.CharacterSheetManager.get(player);
    }

    /** Sets one field of a character sheet (empty = cleared). Only enabled fields are stored. Returns true when it changed. */
    public static boolean setCharacterSheetField(UUID player, String fieldId, String value, String actor) {
        return net.rp.rpessentials.identity.CharacterSheetManager.set(player, fieldId, value, actor);
    }

    /**
     * Adds a field to every character sheet (shown after the config fields). Call it during mod setup.
     * {@code multiline} = several lines with the text editor.
     */
    public static void registerCharacterSheetField(String id, String label, boolean multiline) {
        net.rp.rpessentials.identity.CharacterSheetManager.registerField(id, label, multiline);
    }

    // ── Server schedule (opening / closing) ──────────────────────────────────
    /** True when players can join now (schedule, forced state and enableSchedule taken into account). */
    public static boolean isServerOpen() { return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.isServerOpen(); }

    /** Forced state set by /schedule forceopen / forceclose (NONE = the schedule decides). */
    public static net.rp.rpessentials.api.event.ServerScheduleEvent.ForceState getForceState() {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.getForceState();
    }

    /** Short status in English ("Open: closing in 2h05"...). */
    public static String getScheduleStatus() { return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.getTimeUntilNextEvent(); }

    /**
     * Opens the server now, like /schedule forceopen (message, staff notice, Discord, ServerScheduleEvent).
     * @param actor name shown to staff
     * @return false when another mod cancelled ServerScheduleEvent.ForceStateChanging
     */
    public static boolean forceOpenServer(MinecraftServer server, String actor) {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.forceOpen(server, actor,
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.API);
    }

    /** Closes the server now and kicks the players who are not exempt, like /schedule forceclose. */
    public static boolean forceCloseServer(MinecraftServer server, String actor) {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.forceClose(server, actor,
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.API);
    }

    /** Same, for some minutes (0 = until someone changes it) and with a reason ("" = none). */
    public static boolean forceOpenServer(MinecraftServer server, String actor, int minutes, String reason) {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.forceOpen(server, actor,
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.API, minutes, reason);
    }

    /** Same, for some minutes (0 = until someone changes it) and with a reason shown to the refused players. */
    public static boolean forceCloseServer(MinecraftServer server, String actor, int minutes, String reason) {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.forceClose(server, actor,
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.API, minutes, reason);
    }

    /** Back to the normal schedule, like /schedule resetforcestate. */
    public static boolean resetServerForceState(MinecraftServer server, String actor) {
        return net.rp.rpessentials.gameplay.RpEssentialsScheduleManager.resetForceState(server, actor,
                net.rp.rpessentials.api.event.ServerScheduleEvent.Cause.API);
    }

    public static void registerConfig(String mod, int modColor, String id, String displayName, int color,
                                      ConfigInspector.Theme theme, Class<?> clazz, ModConfigSpec spec,
                                      Consumer<MinecraftServer> onApplied) {
        ConfigInspector.register(mod, modColor, id, displayName, color, theme, clazz, spec, onApplied);
    }
}