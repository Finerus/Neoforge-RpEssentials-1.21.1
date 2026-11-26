package net.rp.rpessentials.util;

import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.RpEssentialsConfig;
import net.rp.rpessentials.config.ScheduleConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A config list of players. An entry is {@code UUID (Name)}, so the player keeps their place after changing name and
 * nobody gets it by taking their old one. A plain name (typed in the file or the Config Manager) works too, and
 * becomes a UUID when this player joins.
 */
public final class PlayerRefs {

    public static final PlayerRefs SCHEDULE = new PlayerRefs(ScheduleConfig.SPEC, ScheduleConfig.SCHEDULE_WHITELIST);
    public static final PlayerRefs TAB_WHITELIST = new PlayerRefs(RpEssentialsConfig.SPEC, RpEssentialsConfig.WHITELIST);
    public static final PlayerRefs TAB_BLACKLIST = new PlayerRefs(RpEssentialsConfig.SPEC, RpEssentialsConfig.BLACKLIST);
    public static final PlayerRefs TAB_ALWAYS_VISIBLE = new PlayerRefs(RpEssentialsConfig.SPEC, RpEssentialsConfig.ALWAYS_VISIBLE_LIST);

    private record Entry(UUID id, String name) {
        static Entry parse(String raw) {
            String s = raw.trim();
            int space = s.indexOf(' ');
            String first = space < 0 ? s : s.substring(0, space);
            if (first.length() == 36) {
                try {
                    UUID id = UUID.fromString(first);
                    String name = space < 0 ? "" : s.substring(space + 1).trim();
                    if (name.startsWith("(") && name.endsWith(")")) name = name.substring(1, name.length() - 1);
                    return new Entry(id, name);
                } catch (IllegalArgumentException ignored) {
                    // A name then.
                }
            }
            return new Entry(null, s);
        }

        String text() { return id == null ? name : name.isEmpty() ? id.toString() : id + " (" + name + ")"; }

        /** The player this entry is about; without UUID, whoever has the name. */
        boolean is(UUID uuid, String playerName) { return id != null ? id.equals(uuid) : name.equalsIgnoreCase(playerName); }
    }

    private record Parsed(List<? extends String> source, Set<UUID> ids, Set<String> names) {}

    private final ModConfigSpec spec;
    private final ModConfigSpec.ConfigValue<List<? extends String>> option;
    private volatile Parsed parsed = null;

    private PlayerRefs(ModConfigSpec spec, ModConfigSpec.ConfigValue<List<? extends String>> option) {
        this.spec = spec;
        this.option = option;
    }

    /** Read once, again only when the list changes (the config gives the same list until then). */
    private Parsed parsed() {
        List<? extends String> source = Cfg.get(option, List.of());
        Parsed p = parsed;
        if (p != null && p.source() == source) return p;
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (String raw : source) {
            Entry e = Entry.parse(raw);
            if (e.id() != null) ids.add(e.id());
            else if (!e.name().isEmpty()) names.add(e.name().toLowerCase(Locale.ROOT));
        }
        p = new Parsed(source, ids, names);
        parsed = p;
        return p;
    }

    public boolean contains(GameProfile profile) {
        Parsed p = parsed();
        if (p.ids().contains(profile.getId())) return true;
        return !p.names().isEmpty() && p.names().contains(profile.getName().toLowerCase(Locale.ROOT));
    }

    /** Adds a player ({@code id} null = not known yet, kept by name). False when already there. */
    public boolean add(UUID id, String name) {
        List<String> list = new ArrayList<>(option.get());
        for (String raw : list) if (Entry.parse(raw).is(id, name)) return false;
        list.add(new Entry(id, name).text());
        save(list);
        return true;
    }

    /** Removes a player by name or UUID. False when not in the list. */
    public boolean remove(String nameOrId, UUID id) {
        List<String> list = new ArrayList<>(option.get());
        boolean removed = list.removeIf(raw -> {
            Entry e = Entry.parse(raw);
            return e.name().equalsIgnoreCase(nameOrId) || (e.id() != null && (e.id().equals(id) || e.id().toString().equalsIgnoreCase(nameOrId)));
        });
        if (removed) save(list);
        return removed;
    }

    /** Names of the list, to show and to complete. */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (String raw : Cfg.get(option, List.<String>of())) {
            Entry e = Entry.parse(raw);
            out.add(e.name().isEmpty() ? String.valueOf(e.id()) : e.name());
        }
        return out;
    }

    private void save(List<String> list) {
        option.set(list);
        spec.save();
    }

    /** A player joins: their name becomes their UUID, and the name written next to the UUID follows theirs. */
    private void onJoin(GameProfile profile) {
        Parsed p = parsed();
        if (!p.ids().contains(profile.getId()) && !p.names().contains(profile.getName().toLowerCase(Locale.ROOT))) return;
        List<String> list = new ArrayList<>(option.get());
        String wanted = new Entry(profile.getId(), profile.getName()).text();
        boolean changed = false, seen = false;
        for (int i = 0; i < list.size(); i++) {
            if (!Entry.parse(list.get(i)).is(profile.getId(), profile.getName())) continue;
            if (seen) { list.remove(i--); changed = true; continue; }
            seen = true;
            if (!list.get(i).trim().equals(wanted)) { list.set(i, wanted); changed = true; }
        }
        if (changed) save(list);
    }

    /** Called once a player is accepted. */
    public static void joined(GameProfile profile) {
        if (!Cfg.ready(ScheduleConfig.SPEC, RpEssentialsConfig.SPEC)) return;
        for (PlayerRefs r : List.of(SCHEDULE, TAB_WHITELIST, TAB_BLACKLIST, TAB_ALWAYS_VISIBLE)) r.onJoin(profile);
    }
}
