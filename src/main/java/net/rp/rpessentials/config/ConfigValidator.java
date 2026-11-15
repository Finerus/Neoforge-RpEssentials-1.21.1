package net.rp.rpessentials.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.gameplay.WorldBorderManager;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Checks the config files for mistakes: bad lines, unknown professions, permissions, dimensions, sounds...
 * Used by /rpessentials config validate and at server startup.
 */
public final class ConfigValidator {

    private static final Pattern ITEM = Pattern.compile("#?[a-z0-9_.\\-/*:]+(\\|(dim|zone)=[A-Za-z0-9_.\\-/:]+)*");
    private static final Pattern SLOT = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d");
    private static final Set<String> PERMS = Set.of("isstaff", "tabwhitelist", "schedulewhitelist",
            "professionwhitelist", "seenicknames", "seeall", "bypassworldborder", "spymessages",
            "bypassautounwhitelist", "bypassdeathrpwhitelist");

    private ConfigValidator() {}

    /** Problems in the server language (startup log). */
    public static List<String> validate() {
        return validate(null);
    }

    /** Problems in the viewer's language. */
    public static List<String> validate(ServerPlayer v) {
        List<String> out = new ArrayList<>(invalidValues(v));
        for (String c : net.rp.rpessentials.commands.CommandToggles.activeConflicts())
            add(out, v, MessagesConfig.CFG_V_CMD_CONFLICT, "command", c);
        if (Cfg.ready(ModerationConfig.SPEC, ProfessionConfig.SPEC, RpConfig.SPEC, RpEssentialsConfig.SPEC, ScheduleConfig.SPEC)) {
            Set<String> profs = new HashSet<>();
            for (String l : ProfessionConfig.PROFESSIONS.get()) {
                String[] p = l.split(";", -1);
                if (p.length != 3 || p[0].isBlank() || p[1].isBlank()) badLine(out, v, "professions", l);
                else profs.add(p[0].trim().toLowerCase());
            }
            items(out, v, "globalBlockedCrafts", ProfessionConfig.GLOBAL_BLOCKED_CRAFTS.get());
            items(out, v, "globalUnbreakableBlocks", ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS.get());
            items(out, v, "globalBlockedItems", ProfessionConfig.GLOBAL_BLOCKED_ITEMS.get());
            items(out, v, "globalBlockedEquipment", ProfessionConfig.GLOBAL_BLOCKED_EQUIPMENT.get());
            overrides(out, v, "professionAllowedCrafts", ProfessionConfig.PROFESSION_ALLOWED_CRAFTS.get(), profs);
            overrides(out, v, "professionAllowedBlocks", ProfessionConfig.PROFESSION_ALLOWED_BLOCKS.get(), profs);
            overrides(out, v, "professionAllowedItems", ProfessionConfig.PROFESSION_ALLOWED_ITEMS.get(), profs);
            overrides(out, v, "professionAllowedEquipment", ProfessionConfig.PROFESSION_ALLOWED_EQUIPMENT.get(), profs);

            for (String l : ProfessionConfig.CONTAINER_OPEN_RESTRICTIONS.get()) {
                String[] p = l.split(";", 2);
                if (p.length != 2 || !ITEM.matcher(p[0].trim()).matches()) {
                    badLine(out, v, "containerOpenRestrictions", l);
                    continue;
                }
                for (String prof : p[1].split(",")) {
                    if (!profs.contains(prof.trim().toLowerCase()))
                        add(out, v, MessagesConfig.CFG_V_UNKNOWN_PROF_IN, "option", "containerOpenRestrictions",
                                "value", prof.trim(), "line", l);
                }
            }

            slots(out, v, "deathHoursSlots", ScheduleConfig.DEATH_HOURS_SLOTS.get());
            slots(out, v, "hrpToleratedSlots", ScheduleConfig.HRP_TOLERATED_SLOTS.get());
            slots(out, v, "hrpAllowedSlots", ScheduleConfig.HRP_ALLOWED_SLOTS.get());

            for (String z : RpEssentialsConfig.NAMED_ZONES.get()) {
                String[] p = z.split(";");
                boolean ok = p.length >= 5;
                if (ok) {
                    try {
                        Double.parseDouble(p[1].trim());
                        Double.parseDouble(p[2].trim());
                        ok = WorldBorderManager.parseShape(p[3]) != null;
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                if (ok && p.length >= 6 && p[4].contains(":") && ResourceLocation.tryParse(p[4].trim()) == null) ok = false;
                if (!ok) badLine(out, v, "namedZones", z);
            }

            for (String o : RpEssentialsConfig.WORLD_BORDER_DIMENSION_OVERRIDES.get()) {
                String[] p = o.split(";");
                boolean ok = p.length >= 3 && ResourceLocation.tryParse(p[0].trim()) != null;
                if (ok) {
                    try {
                        Integer.parseInt(p[1].trim());
                        if (p.length >= 4) Integer.parseInt(p[3].trim());
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                if (!ok) badLine(out, v, "worldBorderDimensionOverrides", o);
            }

            for (String d : RpConfig.DICE_TYPES.get()) {
                String[] p = d.split(";", 2);
                boolean ok = p.length == 2 && !p[0].isBlank();
                if (ok) {
                    String val = p[1].trim();
                    if (val.contains(",")) {
                        ok = Arrays.stream(val.split(",")).filter(s -> !s.isBlank()).count() >= 2;
                    } else {
                        try {
                            int n = Integer.parseInt(val);
                            ok = n >= 2 && n <= 100000;
                        } catch (NumberFormatException e) {
                            ok = false;
                        }
                    }
                }
                if (!ok) badLine(out, v, "diceTypes", d);
            }

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            for (String pl : ModerationConfig.PLATFORMS.get()) {
                String[] p = pl.split(";");
                boolean ok = p.length >= 6 && ResourceLocation.tryParse(p[2].trim()) != null;
                if (ok) {
                    try {
                        Double.parseDouble(p[3].trim());
                        Double.parseDouble(p[4].trim());
                        Double.parseDouble(p[5].trim());
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                if (!ok) badLine(out, v, "platforms", pl);
                else dimension(out, v, server, "platforms", p[2].trim());
            }

            // Dimensions and sounds referenced by the config
            dimension(out, v, server, "afkDimension", RpConfig.AFK_DIMENSION.get());
            if (RpConfig.ENABLE_SPAWN_COMMAND.get() && !RpConfig.SPAWN_USE_WORLD_SPAWN.get())
                dimension(out, v, server, "spawnDimension", RpConfig.SPAWN_DIMENSION.get());
            sound(out, v, RpConfig.ANNONCE_SOUND);
            sound(out, v, RpEssentialsConfig.DEATH_RP_DEATH_SOUND);
            sound(out, v, RpEssentialsConfig.DEATH_RP_PLAYER_TOGGLE_SOUND);
            sound(out, v, RpEssentialsConfig.DEATH_RP_GLOBAL_TOGGLE_SOUND);
            sound(out, v, ScheduleConfig.WELCOME_SOUND);

            for (String r : RpEssentialsConfig.ROLES.get()) {
                String[] p = r.split(";", 3);
                if (p[0].isBlank()) {
                    add(out, v, MessagesConfig.CFG_V_EMPTY_ROLE, "option", "roles", "line", r);
                    continue;
                }
                if (p.length >= 3) {
                    for (String perm : p[2].split(",")) {
                        if (!perm.isBlank() && !PERMS.contains(perm.trim().toLowerCase()))
                            add(out, v, MessagesConfig.CFG_V_UNKNOWN_PERM, "option", "roles", "value", perm.trim(), "line", r);
                    }
                }
            }

            // Licenses held by players for professions that no longer exist
            Map<String, Integer> orphan = new TreeMap<>();
            for (List<String> lics : net.rp.rpessentials.profession.LicenseManager.getAllLicenses().values()) {
                for (String id : lics) {
                    if (!profs.contains(id.toLowerCase()) && !net.rp.rpessentials.profession.ProfessionRestrictionManager.isReservedTag(id))
                        orphan.merge(id, 1, Integer::sum);
                }
            }
            orphan.forEach((id, n) -> add(out, v, MessagesConfig.CFG_V_ORPHAN_LICENSE, "value", id, "count", String.valueOf(n)));
        } else {
            add(out, v, MessagesConfig.CFG_V_NOT_LOADED);
        }
        return out;
    }

    /** Options holding a word they do not accept (kept in the file, the default is used meanwhile). */
    public static List<String> invalidValues(ServerPlayer v) {
        List<String> out = new ArrayList<>();
        for (ConfigRules.Problem p : ConfigRules.problems()) {
            add(out, v, MessagesConfig.CFG_V_INVALID_VALUE, "option", p.path(), "value", p.value(),
                    "expected", p.expected(), "default", p.defaultValue());
        }
        return out;
    }

    /** Tells a staff member about the wrong config values, if any (on join and after a reload). */
    public static void warnStaff(ServerPlayer staff) {
        if (!net.rp.rpessentials.permission.RpEssentialsPermissions.isStaff(staff)) return;
        List<String> lines = invalidValues(staff);
        if (lines.isEmpty()) return;
        staff.sendSystemMessage(net.rp.rpessentials.util.ColorHelper.parseColors(
                ServerLang.get(staff, MessagesConfig.STAFF_CFG_INVALID_WARN, "count", String.valueOf(lines.size()))));
        for (String l : lines) staff.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c- §f" + l));
    }

    /** {@link #warnStaff} for every staff member online, and the server log. */
    public static void warnOnlineStaff(MinecraftServer server) {
        for (String l : invalidValues(null)) net.rp.rpessentials.RpEssentials.LOGGER.warn("[Config] {}", l);
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) warnStaff(p);
    }

    private static void add(List<String> out, ServerPlayer v, ModConfigSpec.ConfigValue<String> key, String... args) {
        out.add(ServerLang.get(v, key, args));
    }

    private static void badLine(List<String> out, ServerPlayer v, String option, String line) {
        add(out, v, MessagesConfig.CFG_V_BAD_LINE, "option", option, "value", line);
    }

    private static void items(List<String> out, ServerPlayer v, String name, List<? extends String> list) {
        for (String s : list) {
            if (!ITEM.matcher(s.trim()).matches()) add(out, v, MessagesConfig.CFG_V_BAD_PATTERN, "option", name, "value", s);
        }
    }

    private static void overrides(List<String> out, ServerPlayer v, String name, List<? extends String> list, Set<String> profs) {
        for (String l : list) {
            String[] p = l.split(";", 2);
            if (p.length != 2) {
                badLine(out, v, name, l);
                continue;
            }
            if (!profs.contains(p[0].trim().toLowerCase()))
                add(out, v, MessagesConfig.CFG_V_UNKNOWN_PROF, "option", name, "value", p[0].trim());
            for (String item : p[1].split(",")) {
                if (!ITEM.matcher(item.trim()).matches())
                    add(out, v, MessagesConfig.CFG_V_BAD_PATTERN_IN, "option", name, "value", item.trim(), "line", p[0].trim());
            }
        }
    }

    private static void slots(List<String> out, ServerPlayer v, String name, List<? extends String> list) {
        for (String s : list) {
            if (!SLOT.matcher(s.trim()).matches()) add(out, v, MessagesConfig.CFG_V_BAD_SLOT, "option", name, "value", s);
        }
    }

    /** A dimension id that does not exist on this server. Skipped while the server is not running. */
    private static void dimension(List<String> out, ServerPlayer v, MinecraftServer server, String option, String id) {
        if (server == null || id == null) return;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, rl)) == null)
            add(out, v, MessagesConfig.CFG_V_UNKNOWN_DIMENSION, "option", option, "value", id);
    }

    /** A sound id that is not registered. Empty means "no sound". */
    private static void sound(List<String> out, ServerPlayer v, ModConfigSpec.ConfigValue<String> cv) {
        String id = cv.get();
        if (id == null || id.isBlank()) return;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null || !BuiltInRegistries.SOUND_EVENT.containsKey(rl)) {
            List<String> path = cv.getPath();
            add(out, v, MessagesConfig.CFG_V_UNKNOWN_SOUND, "option", path.get(path.size() - 1), "value", id);
        }
    }
}
