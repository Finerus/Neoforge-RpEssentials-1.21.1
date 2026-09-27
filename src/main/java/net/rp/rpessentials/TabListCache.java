package net.rp.rpessentials;

import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Cache des listes blacklist/alwaysVisible en Set pour éviter les lookups O(n)
 * à chaque entrée du tab list, recalculé uniquement au reload de config.
 */
public class TabListCache {

    private static volatile Set<String> blacklist = Collections.emptySet();
    private static volatile Set<String> alwaysVisible = Collections.emptySet();
    private static volatile boolean loaded = false;

    public static void reload() {
        try {
            blacklist = new HashSet<>(RpEssentialsConfig.BLACKLIST.get());
            alwaysVisible = new HashSet<>(RpEssentialsConfig.ALWAYS_VISIBLE_LIST.get());
            loaded = true;
        } catch (IllegalStateException e) {
            loaded = false;
        }
    }

    public static boolean isBlacklisted(String name) {
        if (!loaded) reload();
        return blacklist.contains(name);
    }

    public static boolean isAlwaysVisible(String name) {
        if (!loaded) reload();
        return alwaysVisible.contains(name);
    }
}