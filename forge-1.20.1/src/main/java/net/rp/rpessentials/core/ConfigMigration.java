package net.rp.rpessentials.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraftforge.fml.loading.FMLPaths;
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
 * {@code config/rpessentials} and {@code defaultconfigs/rpessentials}. A file is copied to
 * {@code <name>.bak-migration} before its first change. Running it again changes nothing.
 * <p>
 * Steps (each one only when the old layout is found):
 * <ol>
 *   <li><b>Roles moved into "Permissions System"</b> (6.0.0): the "Roles" section ({@code roles},
 *       {@code luckPermsSync}, {@code luckPermsImportAllGroups}) moves into "Permissions System".</li>
 *   <li><b>staffTags and luckPermsStaffGroups removed</b> (6.0.0): each tag / group listed there gets the
 *       {@code isStaff} permission through a role of the same name (added to the role when it exists,
 *       otherwise a role {@code name;name;isStaff} is created), so the same players stay staff.
 *       {@code luckPermsStaffGroups} only counted when {@code useLuckPermsGroups} was true.</li>
 *   <li><b>Dice messages</b> (4.1.x): {@code diceRollFormat} and {@code diceRollSpyFormat} moved from the
 *       RP file ("Dice System") to the messages file ("Dice").</li>
 * </ol>
 */
public final class ConfigMigration {

    private static final String PERMS = "Permissions System";
    private static final String ROLES = "Roles";

    private ConfigMigration() {}

    public static void run() {
        for (Path dir : List.of(FMLPaths.CONFIGDIR.get().resolve("rpessentials"),
                FMLPaths.GAMEDIR.get().resolve("defaultconfigs").resolve("rpessentials"))) {
            if (!Files.isDirectory(dir)) continue;
            try {
                migrateRoles(dir.resolve("rpessentials-core.toml"));
                migrateDice(dir.resolve("rpessentials-rp.toml"), dir.resolve("rpessentials-messages.toml"));
            } catch (Exception e) {
                // Never block the start: the file is left as it was.
                RpEssentials.LOGGER.error("[Migration] Could not update the config files in {}", dir, e);
            }
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
