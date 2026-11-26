package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.permission.RpPerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Marks of the options, declared once at the end of each config class:
 * <ul>
 *   <li>{@link #client}: the client needs the value. Every other option stays on the server
 *       (see {@link net.rp.rpessentials.core.ConfigSyncFilter}), so a new option is private unless it is marked;</li>
 *   <li>{@link #sensitive}: only seen and changed with {@code rpessentials.config.sensitive};</li>
 *   <li>{@link #secret}: sensitive, and shown as stars in the messages and the log;</li>
 *   <li>{@link #node} / {@link #file}: the option (or the whole file) does the same thing as a command with its own
 *       node, so it needs this node too (Config Manager and /rpessentials config set).</li>
 * </ul>
 * Filled while the config classes load, only read afterwards. Identity maps: on Forge the hash of a spec reads the
 * whole config.
 */
public final class ConfigAccess {

    private static final Map<ModConfigSpec, List<ModConfigSpec.ConfigValue<?>>> CLIENT = new IdentityHashMap<>();
    private static final Set<ModConfigSpec.ConfigValue<?>> SENSITIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> SECRET_KEYS = new HashSet<>();
    private static final Map<ModConfigSpec.ConfigValue<?>, RpPerm> NODES = new IdentityHashMap<>();
    private static final Map<ModConfigSpec, RpPerm> FILES = new IdentityHashMap<>();

    private ConfigAccess() {}

    // ── Declaration ──────────────────────────────────────────────────────────

    static synchronized void client(ModConfigSpec spec, ModConfigSpec.ConfigValue<?>... options) {
        CLIENT.computeIfAbsent(spec, k -> new ArrayList<>()).addAll(List.of(options));
    }

    static synchronized void sensitive(ModConfigSpec.ConfigValue<?>... options) {
        SENSITIVE.addAll(List.of(options));
    }

    static synchronized void secret(ModConfigSpec.ConfigValue<?>... options) {
        for (ModConfigSpec.ConfigValue<?> o : options) {
            SENSITIVE.add(o);
            SECRET_KEYS.add(o.getPath().get(o.getPath().size() - 1));
        }
    }

    static synchronized void node(RpPerm perm, ModConfigSpec.ConfigValue<?>... options) {
        for (ModConfigSpec.ConfigValue<?> o : options) NODES.put(o, perm);
    }

    static synchronized void file(RpPerm perm, ModConfigSpec spec) {
        FILES.put(spec, perm);
    }

    // ── Use ──────────────────────────────────────────────────────────────────

    /** Options of a file that the client needs. */
    public static List<ModConfigSpec.ConfigValue<?>> clientOptions(ModConfigSpec spec) {
        return CLIENT.getOrDefault(spec, List.of());
    }

    /** True when not every staff member may see the option. */
    public static boolean restricted(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> option) {
        return SENSITIVE.contains(option) || NODES.containsKey(option) || FILES.containsKey(spec);
    }

    public static boolean isSensitive(ModConfigSpec.ConfigValue<?> option) {
        return SENSITIVE.contains(option);
    }

    /** May someone see and change this option? {@code has} = their permission nodes. */
    public static boolean visible(ModConfigSpec spec, ModConfigSpec.ConfigValue<?> option, Predicate<RpPerm> has) {
        if (SENSITIVE.contains(option) && !has.test(RpPerm.CONFIG_SENSITIVE)) return false;
        RpPerm node = NODES.get(option);
        if (node != null && !has.test(node)) return false;
        RpPerm file = FILES.get(spec);
        return file == null || has.test(file);
    }

    /** True for the last part of the path of a secret ("discordBotToken"). */
    public static boolean isSecretKey(String key) {
        return SECRET_KEYS.contains(key);
    }
}
