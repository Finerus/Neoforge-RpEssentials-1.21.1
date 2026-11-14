package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.util.RpEssentialsPatternUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Compares restriction lists for the editor warnings: which ids of an entry (exact id,
 * {@code *} pattern or {@code #tag}) are not covered by another list.
 * <p>
 * A scope ({@code |dim=...}, {@code |zone=...}) is ignored: a scoped entry counts as covering.
 */
final class RestrictionCoverage {

    private RestrictionCoverage() {}

    /** The entry without its scope. */
    static String base(String entry) {
        int bar = entry.indexOf('|');
        return (bar >= 0 ? entry.substring(0, bar) : entry).trim();
    }

    static boolean isPattern(String entry) {
        String b = base(entry);
        return b.startsWith("#") || b.contains("*");
    }

    /** Ids of {@code pool} matched by the entry; an exact id is returned as is, even if unknown. */
    static List<String> expand(String entry, List<String> pool) {
        String b = base(entry);
        if (!isPattern(b)) return List.of(b);
        List<String> out = new ArrayList<>();
        if (pool == null) return out;
        for (String id : pool) if (RpEssentialsPatternUtils.matchesPattern(id, b)) out.add(id);
        return out;
    }

    /** True when one of {@code entries} matches {@code id}. */
    static boolean covered(String id, Collection<String> entries) {
        for (String e : entries) {
            String b = base(e);
            if (b.isEmpty()) continue;
            if (b.equals(id) || RpEssentialsPatternUtils.matchesPattern(id, b)) return true;
        }
        return false;
    }

    /** Ids of the entry that none of {@code entries} covers. */
    static List<String> uncovered(String entry, Collection<String> entries, List<String> pool) {
        List<String> out = new ArrayList<>();
        for (String id : expand(entry, pool)) if (!covered(id, entries)) out.add(id);
        return out;
    }
}
