package net.rp.rpessentials.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.neoforged.fml.loading.FMLPaths;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Updates config files written by older versions, once, before NeoForge loads them (NeoForge drops the
 * options it does not know, so their values would be lost). Runs from the mod constructor on
 * {@code config/rpessentials}, {@code defaultconfigs/rpessentials} and the {@code serverconfig/rpessentials} folder
 * of the worlds (where Forge 1.20.1 keeps the server configs, and where NeoForge reads overrides). A file is copied
 * to {@code <name>.bak-migration} before its first change. Running it again changes nothing.
 * <p>
 * Steps (each one only when the old layout is found):
 * <ol>
 *   <li><b>Roles moved into "Permissions System"</b> (6.0.0): the "Roles" section ({@code roles},
 *       {@code luckPermsSync}, {@code luckPermsImportAllGroups}) moves into "Permissions System".</li>
 *   <li><b>staffTags and luckPermsStaffGroups removed</b> (6.0.0): each tag / group listed there gets the
 *       {@code isStaff} permission through a role of the same name (added to the role when it exists,
 *       otherwise a role {@code name;name;isStaff} is created), so the same players stay staff.
 *       {@code luckPermsStaffGroups} only counted when {@code useLuckPermsGroups} was true.</li>
 *   <li><b>World border</b> (6.2.0): the section "World Border Warning" becomes "World Border", and {@code enableWorldBorderWarning} becomes {@code enableWorldBorder}. It also
 *       turned the named zones off, so {@code false} is written in the new {@code enableNamedZones} too.</li>
 *   <li><b>Closed message</b> (6.2.0): {@code msgServerClosed} gets the time left when it was never changed.</li>
 *   <li><b>Dice messages</b> (4.1.x): {@code diceRollFormat} and {@code diceRollSpyFormat} moved from the
 *       RP file ("Dice System") to the messages file ("Dice").</li>
 * </ol>
 */
public final class ConfigMigration {

    private static final String PERMS = "Permissions System";
    private static final String ROLES = "Roles";
    private static final String OLD_BORDER = "World Border Warning";
    private static final String BORDER = "World Border";

    private ConfigMigration() {}

    public static void run() {
        for (Path dir : folders()) {
            if (!Files.isDirectory(dir)) continue;
            try {
                migrate(dir);
            } catch (Exception e) {
                // Never block the start: the file is left as it was.
                RpEssentials.LOGGER.error("[Migration] Could not update the config files in {}", dir, e);
            }
        }
    }

    static void migrate(Path dir) throws Exception {
        migrateRoles(dir.resolve("rpessentials-core.toml"));
        migrateWorldBorder(dir.resolve("rpessentials-core.toml"));
        migrateClosedMessage(dir.resolve("rpessentials-schedule.toml"));
        migrateDice(dir.resolve("rpessentials-rp.toml"), dir.resolve("rpessentials-messages.toml"));
    }

    /** Every folder that may hold the config files of the mod. */
    private static List<Path> folders() {
        Path game = FMLPaths.GAMEDIR.get();
        List<Path> out = new ArrayList<>();
        out.add(FMLPaths.CONFIGDIR.get().resolve("rpessentials"));
        out.add(game.resolve("defaultconfigs").resolve("rpessentials"));
        Set<Path> worlds = new LinkedHashSet<>();
        // Dedicated server: the world of server.properties.
        Path properties = game.resolve("server.properties");
        if (Files.isRegularFile(properties)) {
            java.util.Properties p = new java.util.Properties();
            try (java.io.Reader in = Files.newBufferedReader(properties)) {
                p.load(in);
                worlds.add(game.resolve(p.getProperty("level-name", "world").trim()));
            } catch (Exception e) {
                RpEssentials.LOGGER.warn("[Migration] Could not read server.properties: {}", e.toString());
            }
        }
        // Singleplayer: every world.
        Path saves = game.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (java.util.stream.Stream<Path> list = Files.list(saves)) {
                list.filter(Files::isDirectory).forEach(worlds::add);
            } catch (Exception e) {
                RpEssentials.LOGGER.warn("[Migration] Could not list the worlds: {}", e.toString());
            }
        }
        for (Path world : worlds) out.add(world.resolve("serverconfig").resolve("rpessentials"));
        return out;
    }

    // =========================================================================
    // WORLD BORDER (6.2.0)
    // =========================================================================
    private static void migrateWorldBorder(Path file) throws Exception {
        if (!Files.isRegularFile(file)) return;
        try (CommentedFileConfig cfg = open(file)) {
            if (!(cfg.get(List.of(OLD_BORDER)) instanceof CommentedConfig old)) return;
            backup(file);

            // 1. The old switch turned the border and the named zones off. A value already written with the new name wins.
            List<String> zones = List.of(BORDER, "enableNamedZones");
            if (old.contains("enableWorldBorderWarning")) {
                boolean off = Boolean.FALSE.equals(old.get("enableWorldBorderWarning"));
                if (!old.contains("enableWorldBorder") && !cfg.contains(List.of(BORDER, "enableWorldBorder")))
                    cfg.set(List.of(BORDER, "enableWorldBorder"), !off);
                if (off && !old.contains("enableNamedZones") && !cfg.contains(zones)) cfg.set(zones, false);
                old.remove("enableWorldBorderWarning");
                RpEssentials.LOGGER.info("[Migration] {}: 'enableWorldBorderWarning' is now 'enableWorldBorder' ({}){}", file,
                        !off, off ? ", named zones kept off (enableNamedZones)" : "");
            }

            // 2. Every other option of the section (values already in the new one win).
            for (CommentedConfig.Entry e : new ArrayList<>(old.entrySet())) {
                List<String> to = List.of(BORDER, e.getKey());
                if (cfg.contains(to)) continue;
                cfg.set(to, e.getValue());
                if (e.getComment() != null) cfg.setComment(to, e.getComment());
            }
            cfg.remove(List.of(OLD_BORDER));
            cfg.save();
            RpEssentials.LOGGER.info("[Migration] {}: section '{}' renamed '{}'", file, OLD_BORDER, BORDER);
        }
    }

    // =========================================================================
    // CLOSED MESSAGE (6.2.0)
    // =========================================================================
    private static void migrateClosedMessage(Path file) throws Exception {
        if (!Files.isRegularFile(file)) return;
        List<String> path = List.of("Schedule Messages", "msgServerClosed");
        try (CommentedFileConfig cfg = open(file)) {
            // Only the old default: a message written by the admin is kept as it is.
            if (!"§c§lThe server is now closed. See you on {day} at {open}!".equals(cfg.get(path))) return;
            backup(file);
            cfg.set(path, net.rp.rpessentials.config.ScheduleConfig.MSG_SERVER_CLOSED.getDefault());
            cfg.save();
            RpEssentials.LOGGER.info("[Migration] {}: 'msgServerClosed' now shows the time left before the opening", file);
        }
    }

    // =========================================================================
    // ROLES + PERMISSIONS SYSTEM (6.0.0)
    // =========================================================================
    private static void migrateRoles(Path file) throws Exception {
        if (!Files.isRegularFile(file)) return;
        try (CommentedFileConfig cfg = open(file)) {
            boolean oldRoles = cfg.get(List.of(ROLES)) instanceof CommentedConfig;
            boolean oldTags = cfg.contains(List.of(PERMS, "staffTags")) || cfg.contains(List.of(PERMS, "luckPermsStaffGroups"));
            if (!oldRoles && !oldTags) return;
            backup(file);

            // 1. The Roles section moves into Permissions System (values already there win).
            if (oldRoles) {
                for (String key : List.of("roles", "luckPermsSync", "luckPermsImportAllGroups")) {
                    List<String> from = List.of(ROLES, key);
                    List<String> to = List.of(PERMS, key);
                    if (cfg.contains(from) && !cfg.contains(to)) {
                        cfg.set(to, cfg.get(from));
                        String comment = cfg.getComment(from);
                        if (comment != null) cfg.setComment(to, comment);
                    }
                }
                cfg.remove(List.of(ROLES));
                RpEssentials.LOGGER.info("[Migration] {}: section 'Roles' moved into '{}'", file.getFileName(), PERMS);
            }

            // 2. Staff tags / LuckPerms staff groups become the isStaff permission of a role.
            if (oldTags) {
                Set<String> staff = new LinkedHashSet<>(strings(cfg.get(List.of(PERMS, "staffTags"))));
                Object useGroups = cfg.get(List.of(PERMS, "useLuckPermsGroups"));
                if (!Boolean.FALSE.equals(useGroups)) staff.addAll(strings(cfg.get(List.of(PERMS, "luckPermsStaffGroups"))));

                List<String> roles = cfg.contains(List.of(PERMS, "roles"))
                        ? strings(cfg.get(List.of(PERMS, "roles")))
                        : new ArrayList<>(RpEssentialsConfig.ROLES.getDefault());
                for (String name : staff) {
                    String change = giveStaff(roles, name);
                    if (change != null) RpEssentials.LOGGER.info("[Migration] {}: {}", file.getFileName(), change);
                }
                cfg.set(List.of(PERMS, "roles"), roles);
                cfg.remove(List.of(PERMS, "staffTags"));
                cfg.remove(List.of(PERMS, "luckPermsStaffGroups"));
                RpEssentials.LOGGER.info("[Migration] {}: 'staffTags' and 'luckPermsStaffGroups' removed (now handled by the roles)",
                        file.getFileName());
            }
            cfg.save();
        }
    }

    /**
     * Makes sure the role {@code name} has isStaff (case does not matter).
     * @return what was changed, null when the role already had it
     */
    static String giveStaff(List<String> roles, String name) {
        for (int i = 0; i < roles.size(); i++) {
            String[] p = roles.get(i).split(";", 3);
            if (!p[0].trim().equalsIgnoreCase(name)) continue;
            String perms = p.length >= 3 ? p[2].trim() : "";
            for (String perm : perms.split(",")) {
                if (perm.trim().equalsIgnoreCase("isStaff")) return null;
            }
            String lp = p.length >= 2 ? p[1].trim() : p[0].trim();
            roles.set(i, p[0].trim() + ";" + lp + ";" + (perms.isEmpty() ? "isStaff" : perms + ",isStaff"));
            return "role '" + p[0].trim() + "' gets isStaff (was a staff tag / group)";
        }
        roles.add(name + ";" + name + ";isStaff");
        return "role '" + name + "' created with isStaff (was a staff tag / group)";
    }

    // =========================================================================
    // DICE MESSAGES (4.1.x)
    // =========================================================================
    private static void migrateDice(Path rpFile, Path messagesFile) throws Exception {
        if (!Files.isRegularFile(rpFile)) return;
        try (CommentedFileConfig rp = open(rpFile)) {
            List<String> keys = new ArrayList<>();
            for (String key : List.of("diceRollFormat", "diceRollSpyFormat")) {
                if (rp.contains(List.of("Dice System", key))) keys.add(key);
            }
            if (keys.isEmpty()) return;
            backup(rpFile);
            if (Files.isRegularFile(messagesFile)) backup(messagesFile);
            try (CommentedFileConfig messages = open(messagesFile)) {
                for (String key : keys) {
                    if (!messages.contains(List.of("Dice", key))) messages.set(List.of("Dice", key), rp.get(List.of("Dice System", key)));
                    rp.remove(List.of("Dice System", key));
                }
                messages.save();
            }
            rp.save();
            RpEssentials.LOGGER.info("[Migration] {} moved from {} to {}", keys, rpFile.getFileName(), messagesFile.getFileName());
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private static CommentedFileConfig open(Path file) {
        CommentedFileConfig cfg = CommentedFileConfig.builder(file, TomlFormat.instance())
                .preserveInsertionOrder().sync().build();
        cfg.load();
        return cfg;
    }

    /** Copy of the file as it was before the first migration. */
    private static void backup(Path file) throws Exception {
        Path bak = file.resolveSibling(file.getFileName() + ".bak-migration");
        if (!Files.exists(bak)) Files.copy(file, bak, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) continue;
                String s = o.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }
}
