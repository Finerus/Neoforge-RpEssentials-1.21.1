package net.rp.rpessentials.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.rp.rpessentials.RpEssentialsPatternUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientTooltipRestrictions {

    private static final String[] LABELS = {"craft", "mining", "usage", "equip"};

    private record ClientZone(int shape, double cx, double cz, double hx, double hz, String dim) {}
    private record Hit(boolean active, String scope) {}
    private record Built(List<Component> lines, boolean cacheable) {}

    private static volatile List<List<String>> blocked = List.of();
    private static volatile List<List<String>> allowed = List.of();
    private static volatile Map<String, String> names = Map.of();
    private static volatile Set<String> licenses = Set.of();
    private static volatile Map<String, ClientZone> zones = Map.of();
    private static volatile boolean exempt = false;
    private static volatile String idMode = "NAME_AND_ID";
    private static final Map<Item, List<Component>> cache = new ConcurrentHashMap<>();

    private ClientTooltipRestrictions() {}

    public static String idMode() { return idMode; }

    public static String displayName(String id) {
        Map<String, String> m = names;
        String n = m.get(id);
        return n != null ? n : m.get(id.toLowerCase());
    }

    public static void update(List<List<String>> b, List<List<String>> a, List<String> n,
                              List<String> l, List<String> z, String mode) {
        Map<String, String> nm = new HashMap<>();
        for (String s : n) {
            int i = s.indexOf(';');
            if (i > 0) nm.put(s.substring(0, i), s.substring(i + 1));
        }
        Set<String> lic = new HashSet<>();
        for (String s : l) lic.add(s.toLowerCase());
        Map<String, ClientZone> zm = new HashMap<>();
        for (String s : z) {
            String[] p = s.split(";", -1);
            if (p.length < 7) continue;
            try {
                zm.put(p[0].toLowerCase(), new ClientZone(Integer.parseInt(p[3]), Double.parseDouble(p[1]),
                        Double.parseDouble(p[2]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), p[6]));
            } catch (NumberFormatException ignored) {}
        }
        exempt = lic.contains("@exempt");
        blocked = b;
        allowed = a;
        names = nm;
        licenses = lic;
        zones = zm;
        idMode = mode == null ? "NAME_AND_ID" : mode;
        cache.clear();
    }

    public static void clear() {
        update(List.of(), List.of(), List.of(), List.of(), List.of(), "NAME_AND_ID");
    }

    public static void clearCache() {
        cache.clear();
    }

    public static List<Component> lines(Item item) {
        List<Component> cached = cache.get(item);
        if (cached != null) return cached;
        Built b = build(item);
        if (b.cacheable()) cache.put(item, b.lines());
        return b.lines();
    }

    private static boolean anyMatch(List<String> patterns, String id) {
        for (String p : patterns) if (RpEssentialsPatternUtils.matchesPattern(id, p)) return true;
        return false;
    }

    private static boolean inZone(String name) {
        ClientZone z = zones.get(name.toLowerCase());
        Minecraft mc = Minecraft.getInstance();
        if (z == null || mc.player == null || mc.level == null) return false;
        if (!z.dim().isEmpty() && !z.dim().equals(mc.level.dimension().location().toString())) return false;
        double dx = mc.player.getX() - z.cx();
        double dz = mc.player.getZ() - z.cz();
        return switch (z.shape()) {
            case 1 -> Math.abs(dx) <= z.hx() && Math.abs(dz) <= z.hz();
            case 2 -> (dx * dx) / (z.hx() * z.hx()) + (dz * dz) / (z.hz() * z.hz()) <= 1;
            default -> dx * dx + dz * dz <= z.hx() * z.hx();
        };
    }

    private static Hit match(String raw, String id) {
        int bar = raw.indexOf('|');
        if (bar < 0) return RpEssentialsPatternUtils.matchesPattern(id, raw) ? new Hit(true, "") : null;
        if (!RpEssentialsPatternUtils.matchesPattern(id, raw.substring(0, bar))) return null;

        String dim = null;
        String zone = null;
        for (String mod : raw.substring(bar + 1).split("\\|")) {
            int eq = mod.indexOf('=');
            if (eq < 0) continue;
            String k = mod.substring(0, eq).trim().toLowerCase();
            String v = mod.substring(eq + 1).trim();
            if (k.equals("dim")) dim = v;
            else if (k.equals("zone")) zone = v;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean active = true;
        StringBuilder label = new StringBuilder();
        if (dim != null) {
            label.append(dim);
            if (mc.level == null || !mc.level.dimension().location().toString().equals(dim)) active = false;
        }
        if (zone != null) {
            if (label.length() > 0) label.append(", ");
            label.append(zone);
            if (!inZone(zone)) active = false;
        }
        return new Hit(active, label.toString());
    }

    private static String exemptMark() {
        return "§b✔ §3" + I18n.get("rpessentials.tooltip.restriction.exempt");
    }

    private static Component line(String label, String mark, String scope, Set<String> required) {
        label = I18n.get("rpessentials.tooltip.restriction." + label);
        String profs = required.isEmpty() ? "§8-" : String.join("§7, ", required);
        String sc = scope.isEmpty() ? "" : " §8(" + scope + ")";
        return Component.literal("§7" + label + ": " + mark + sc + "§8: §7" + profs);
    }

    /**
     * True when the client is sure the item cannot be used (right click) here, so the use is cancelled
     * locally too and no ghost animation plays. The server still receives the action and stays the authority.
     */
    public static boolean isUseBlocked(Item item) {
        if (exempt || blocked.size() < 5 || allowed.size() < 4) return false;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        boolean active = false;
        for (String raw : blocked.get(2)) {
            Hit h = match(raw, id);
            if (h != null && h.active()) { active = true; break; }
        }
        if (!active) return false;
        for (String entry : allowed.get(2)) {
            int i = entry.indexOf(';');
            if (i < 0) continue;
            String prof = entry.substring(0, i).trim().toLowerCase();
            if (licenses.contains(prof) && anyMatch(Arrays.asList(entry.substring(i + 1).split(",")), id)) return false;
        }
        return true;
    }

    private static Built build(Item item) {
        if (blocked.size() < 5 || allowed.size() < 4) return new Built(List.of(), true);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ResourceLocation blockId = itemId;
        if (item instanceof BlockItem bi) blockId = BuiltInRegistries.BLOCK.getKey(bi.getBlock());

        List<Component> out = new ArrayList<>();
        boolean cacheable = true;

        for (int k = 0; k < 4; k++) {
            String id = (k == 1 ? blockId : itemId).toString();
            boolean active = false;
            String activeScope = "";
            String idleScope = "";
            boolean matched = false;
            for (String raw : blocked.get(k)) {
                Hit h = match(raw, id);
                if (h == null) continue;
                matched = true;
                if (!h.scope().isEmpty()) cacheable = false;
                if (h.active()) {
                    active = true;
                    if (activeScope.isEmpty()) activeScope = h.scope();
                } else if (idleScope.isEmpty()) {
                    idleScope = h.scope();
                }
            }
            if (!matched) continue;

            Set<String> required = new LinkedHashSet<>();
            boolean ok = false;
            for (String entry : allowed.get(k)) {
                int i = entry.indexOf(';');
                if (i < 0) continue;
                String prof = entry.substring(0, i).trim().toLowerCase();
                if (!anyMatch(Arrays.asList(entry.substring(i + 1).split(",")), id)) continue;
                required.add(names.getOrDefault(prof, prof));
                if (licenses.contains(prof)) ok = true;
            }

            if (!active) {
                out.add(line(LABELS[k], "§e~", idleScope, required));
            } else {
                String mark = exempt ? exemptMark() : ok ? "§a✔" : "§c✘";
                out.add(line(LABELS[k], mark, activeScope, required));
            }
        }

        String blockStr = blockId.toString();
        for (String entry : blocked.get(4)) {
            int i = entry.indexOf(';');
            if (i < 0 || !RpEssentialsPatternUtils.matchesPattern(blockStr, entry.substring(0, i).trim())) continue;
            Set<String> required = new LinkedHashSet<>();
            boolean ok = false;
            for (String p : entry.substring(i + 1).split(",")) {
                String prof = p.trim().toLowerCase();
                required.add(names.getOrDefault(prof, prof));
                if (licenses.contains(prof)) ok = true;
            }
            String mark = exempt ? exemptMark() : ok ? "§a✔" : "§c✘";
            out.add(line("open", mark, "", required));
            break;
        }
        return new Built(out, cacheable);
    }
}