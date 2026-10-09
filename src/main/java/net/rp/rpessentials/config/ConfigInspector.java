package net.rp.rpessentials.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.RpEssentials;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class ConfigInspector {

    public enum ValueType {
        BOOLEAN, INT, DOUBLE, LONG, STRING, LIST_STRING, LIST_INT, UNKNOWN
    }

    public record EntryData(
            String fullPath, String key, String comment, ValueType type,
            String currentValue, String defaultValue,
            boolean hasRange, double rangeMin, double rangeMax, boolean isSection
    ) {
        public static EntryData section(String displayName, String pathKey) {
            return new EntryData(pathKey, displayName, "", ValueType.UNKNOWN,
                    "", "", false, 0, 0, true);
        }
    }

    public record FileInfo(String id, String displayName) {}

    private record ConfigClassInfo(String id, String displayName, Class<?> clazz, ModConfigSpec spec) {}

    private static final List<ConfigClassInfo> CONFIGS = List.of(
            new ConfigClassInfo("core",        "Core",        RpEssentialsConfig.class, RpEssentialsConfig.SPEC),
            new ConfigClassInfo("chat",        "Chat",        ChatConfig.class,          ChatConfig.SPEC),
            new ConfigClassInfo("schedule",    "Schedule",    ScheduleConfig.class,      ScheduleConfig.SPEC),
            new ConfigClassInfo("moderation",  "Moderation",  ModerationConfig.class,    ModerationConfig.SPEC),
            new ConfigClassInfo("professions", "Professions", ProfessionConfig.class,    ProfessionConfig.SPEC),
            new ConfigClassInfo("messages",    "Messages",    MessagesConfig.class,      MessagesConfig.SPEC),
            new ConfigClassInfo("rp",    "RP",                RpConfig.class,            RpConfig.SPEC)
    );

    private static final Map<Class<?>, Map<String, ModConfigSpec.ConfigValue<?>>> VALUE_MAP_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    // =========================================================================
    // PUBLIC API
    // =========================================================================
    public static List<FileInfo> getFileInfos() {
        return CONFIGS.stream().map(c -> new FileInfo(c.id(), c.displayName())).toList();
    }

    public static List<EntryData> getEntries(String fileId) {
        return CONFIGS.stream()
                .filter(c -> c.id().equals(fileId))
                .findFirst()
                .map(ConfigInspector::inspectClass)
                .orElse(List.of());
    }

    public static int applyAndSave(String fileId, Map<String, String> changes) {
        Optional<ConfigClassInfo> opt = CONFIGS.stream().filter(c -> c.id().equals(fileId)).findFirst();
        if (opt.isEmpty()) return 0;

        ConfigClassInfo info = opt.get();
        Map<String, ModConfigSpec.ConfigValue<?>> valueMap = buildValueMap(info.clazz());

        // Pré-calcul des entryData pour la validation de range
        Map<String, EntryData> entryDataMap = getEntries(fileId).stream()
                .filter(e -> !e.isSection())
                .collect(java.util.stream.Collectors.toMap(EntryData::fullPath, e -> e));

        int applied = 0;

        for (Map.Entry<String, String> change : changes.entrySet()) {
            ModConfigSpec.ConfigValue<?> cv = valueMap.get(change.getKey());
            if (cv == null) {
                RpEssentials.LOGGER.warn("[ConfigInspector] Unknown path '{}' in file '{}'",
                        change.getKey(), fileId);
                continue;
            }

            EntryData entry = entryDataMap.get(change.getKey());
            if (entry != null && entry.hasRange()) {
                try {
                    double val = Double.parseDouble(change.getValue().trim());
                    if (val < entry.rangeMin() || val > entry.rangeMax()) {
                        RpEssentials.LOGGER.warn("[ConfigInspector] Value {} out of range [{}, {}] for '{}'",
                                val, entry.rangeMin(), entry.rangeMax(), change.getKey());
                        continue;
                    }
                } catch (NumberFormatException e) {
                    RpEssentials.LOGGER.warn("[ConfigInspector] Invalid numeric value '{}' for '{}'",
                            change.getValue(), change.getKey());
                    continue;
                }
            }

            ValueType type = detectType(cv);
            if (applyValue(cv, type, change.getValue())) applied++;
        }

        if (applied > 0) {
            try { info.spec().save(); }
            catch (Exception e) {
                RpEssentials.LOGGER.error("[ConfigInspector] Failed to save spec '{}': {}", fileId, e.getMessage());
            }
        }
        return applied;
    }

    // =========================================================================
    // INTROSPECTION
    // =========================================================================
    private static List<EntryData> inspectClass(ConfigClassInfo info) {
        List<EntryData> entries     = new ArrayList<>();
        String          lastSection = null;

        for (Field field : info.clazz().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;

            ModConfigSpec.ConfigValue<?> cv;
            try {
                field.setAccessible(true);
                cv = (ModConfigSpec.ConfigValue<?>) field.get(null);
            } catch (Exception e) { continue; }
            if (cv == null || cv.getPath().isEmpty()) continue;

            List<String> path = cv.getPath();

            // ── Section header ────────────────────────────────────────────────
            String sectionKey = path.size() > 1
                    ? String.join(".", path.subList(0, path.size() - 1))
                    : "";
            if (!Objects.equals(sectionKey, lastSection)) {
                if (!sectionKey.isEmpty()) {
                    String sectionDisplay = path.get(path.size() - 2);
                    entries.add(EntryData.section(sectionDisplay, sectionKey));
                }
                lastSection = sectionKey;
            }

            // ── Retrieve the ValueSpec object ─────────────────────────────────
            Object vs = getValueSpecObj(info.spec(), path);

            ValueType type       = detectType(cv);
            String    currentVal = serialize(cv.get(), type);
            String    comment    = extractComment(vs);
            String    defaultVal = extractDefault(vs, type);
            double[]  range      = extractRange(cv, vs);

            entries.add(new EntryData(
                    String.join(".", path), path.get(path.size() - 1),
                    comment, type, currentVal, defaultVal,
                    range != null, range != null ? range[0] : 0, range != null ? range[1] : 0,
                    false
            ));
        }
        return entries;
    }

    static Map<String, ModConfigSpec.ConfigValue<?>> buildValueMap(Class<?> clazz) {
        return VALUE_MAP_CACHE.computeIfAbsent(clazz, c -> {
            Map<String, ModConfigSpec.ConfigValue<?>> map = new LinkedHashMap<>();
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    ModConfigSpec.ConfigValue<?> cv = (ModConfigSpec.ConfigValue<?>) field.get(null);
                    if (cv == null) continue;
                    map.put(String.join(".", cv.getPath()), cv);
                } catch (Exception ignored) {}
            }
            return map;
        });
    }

    // =========================================================================
    // VALUE SPEC ACCESS
    // =========================================================================
    /**
     * Retrieves the NeoForge ValueSpec stored inside ModConfigSpec for the given path.
     * -
     * ModConfigSpec extends Night Config's UnmodifiableConfig.
     * The Builder stores ValueSpec objects as leaves of the spec config tree.
     * So ((UnmodifiableConfig) spec).get(path) returns the ValueSpec.
     * -
     * Three strategies tried in order:
     *   1. Cast to UnmodifiableConfig and call get(path)          [NeoForge 21.1 primary]
     *   2. Reflect on fields of ModConfigSpec looking for a Map   [fallback]
     *   3. Scan UnmodifiableConfig sub-fields                     [fallback]
     */
    private static Object getValueSpecObj(ModConfigSpec spec, List<String> path) {
        // Strategy 1 — Night Config UnmodifiableConfig.get(List<String>)
        try {
            Object result = ((UnmodifiableConfig) spec).get(path);
            if (result != null && isValueSpec(result)) return result;
        } catch (Exception ignored) {}

        // Strategy 2 — reflect on Map fields of ModConfigSpec
        Object fromMap = getValueSpecViaMapField(spec, path);
        if (fromMap != null) return fromMap;

        // Strategy 3 — scan UnmodifiableConfig sub-fields (some NeoForge builds
        //               keep the ValueSpec in a nested "spec" UnmodifiableConfig)
        try {
            for (Field f : getAllFields(spec.getClass())) {
                f.setAccessible(true);
                Object val = f.get(spec);
                if (val instanceof UnmodifiableConfig subCfg && val != spec) {
                    try {
                        Object result = subCfg.get(path);
                        if (result != null && isValueSpec(result)) return result;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /** True if obj has a public getComment() method (i.e. looks like a NeoForge ValueSpec). */
    private static boolean isValueSpec(Object obj) {
        if (obj == null) return false;
        try { obj.getClass().getMethod("getComment"); return true; }
        catch (NoSuchMethodException e) { return false; }
    }

    /** Walk all fields (including superclass) looking for a Map<List,ValueSpec>. */
    private static Object getValueSpecViaMapField(ModConfigSpec spec, List<String> path) {
        for (Field f : getAllFields(spec.getClass())) {
            try {
                f.setAccessible(true);
                Object val = f.get(spec);
                if (!(val instanceof Map<?, ?> map)) continue;
                Object candidate = map.get(path);
                if (candidate != null && isValueSpec(candidate)) return candidate;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // =========================================================================
    // TYPE DETECTION
    // =========================================================================

    public static ValueType detectType(ModConfigSpec.ConfigValue<?> cv) {
        if (cv instanceof ModConfigSpec.BooleanValue) return ValueType.BOOLEAN;
        if (cv instanceof ModConfigSpec.IntValue)     return ValueType.INT;
        if (cv instanceof ModConfigSpec.LongValue)    return ValueType.LONG;
        if (cv instanceof ModConfigSpec.DoubleValue)  return ValueType.DOUBLE;

        Object val;
        try { val = cv.get(); } catch (Exception e) { return ValueType.UNKNOWN; }

        if (val instanceof List<?> list) {
            if (list.isEmpty()) return ValueType.LIST_STRING;
            return (list.get(0) instanceof Number) ? ValueType.LIST_INT : ValueType.LIST_STRING;
        }
        if (val instanceof String) return ValueType.STRING;
        return ValueType.UNKNOWN;
    }

    // =========================================================================
    // SPEC FIELD EXTRACTION (reflection on ValueSpec)
    // =========================================================================

    private static String extractComment(Object vs) {
        if (vs == null) return "";
        // getComment() is public on NeoForge ValueSpec
        try {
            Object r = vs.getClass().getMethod("getComment").invoke(vs);
            return r instanceof String s ? s : "";
        } catch (NoSuchMethodException e) {
            // Older build: field named "comment"
            return getStringField(vs, "comment");
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractDefault(Object vs, ValueType type) {
        if (vs == null) return "";
        // Strategy A: getDefault()
        try {
            Object def = vs.getClass().getMethod("getDefault").invoke(vs);
            return def != null ? serialize(def, type) : "";
        } catch (NoSuchMethodException ignored) {}
        // Strategy B: field named "_default" or "default" (Java doesn't allow "default" as field name,
        //              NeoForge uses "_default" or similar)
        catch (Exception ignored) {}
        for (String name : new String[]{"_default", "defaultSupplier", "supplier"}) {
            Object f = getFieldValue(vs, name);
            if (f instanceof java.util.function.Supplier<?> sup) {
                try {
                    Object def = sup.get();
                    return def != null ? serialize(def, type) : "";
                } catch (Exception ignored2) {}
            } else if (f != null) {
                return serialize(f, type);
            }
        }
        return "";
    }

    /**
     * Tries to extract numeric [min, max] from the ValueSpec.
     * NeoForge 21.1 ValueSpec.getRange() → net.neoforged.neoforge.common.ModConfigSpec.Range
     * Range.getMin() / Range.getMax() return Comparable.
     */
    private static double[] extractRange(ModConfigSpec.ConfigValue<?> cv, Object vs) {
        if (!(cv instanceof ModConfigSpec.IntValue)
                && !(cv instanceof ModConfigSpec.DoubleValue)
                && !(cv instanceof ModConfigSpec.LongValue)) return null;
        if (vs == null) return null;

        // Strategy A: getRange()
        try {
            Object range = vs.getClass().getMethod("getRange").invoke(vs);
            if (range != null) return extractMinMax(range);
        } catch (NoSuchMethodException e) {
            // Strategy B: field named "range"
            Object rangeField = getFieldValue(vs, "range");
            if (rangeField != null) return extractMinMax(rangeField);
        } catch (Exception ignored) {}

        return null;
    }

    private static double[] extractMinMax(Object range) {
        if (range == null) return null;
        try {
            Object min = range.getClass().getMethod("getMin").invoke(range);
            Object max = range.getClass().getMethod("getMax").invoke(range);
            if (min instanceof Number mn && max instanceof Number mx)
                return new double[]{mn.doubleValue(), mx.doubleValue()};
        } catch (Exception ignored) {}
        return null;
    }

    // =========================================================================
    // SERIALIZATION / DESERIALIZATION
    // =========================================================================
    public static String serialize(Object value, ValueType type) {
        if (value == null) return "";
        if (value instanceof List<?> list)
            return list.stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        return value.toString();
    }

    public static String serialize(Object value) { return serialize(value, ValueType.UNKNOWN); }

    @SuppressWarnings("unchecked")
    public static boolean applyValue(ModConfigSpec.ConfigValue<?> cv, ValueType type, String raw) {
        try {
            Object parsed = parseValue(type, raw);
            if (parsed == null) return false;
            ((ModConfigSpec.ConfigValue<Object>) cv).set(parsed);
            return true;
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[ConfigInspector] Cannot apply '{}' as {}: {}", raw, type, e.getMessage());
            return false;
        }
    }

    public static Object parseValue(ValueType type, String raw) {
        if (raw == null) return null;
        return switch (type) {
            case BOOLEAN     -> Boolean.parseBoolean(raw.trim());
            case INT         -> Integer.parseInt(raw.trim());
            case LONG        -> Long.parseLong(raw.trim());
            case DOUBLE      -> Double.parseDouble(raw.trim());
            case STRING      -> raw;
            case LIST_STRING -> {
                List<String> out = new ArrayList<>();
                for (String s : raw.split("\n")) {
                    String t = s.trim();
                    if (!t.isEmpty()) out.add(t);
                }
                yield out;
            }
            case LIST_INT -> {
                List<Integer> out = new ArrayList<>();
                for (String s : raw.split("[\\n,]")) {
                    String t = s.trim();
                    if (!t.isEmpty()) out.add(Integer.parseInt(t));
                }
                yield out;
            }
            default -> raw;
        };
    }

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "autoUnwhitelistExtraCommands", "opLevelBypass", "staffTags",
            "useLuckPermsGroups", "luckPermsStaffGroups", "roles");

    public static List<EntryData> getEntries(String fileId, boolean includeSensitive) {
        List<EntryData> all = getEntries(fileId);
        if (includeSensitive) return all;
        return all.stream().filter(e -> e.isSection() || !SENSITIVE_KEYS.contains(e.key())).toList();
    }

    // =========================================================================
    // REFLECTION UTILITIES
    // =========================================================================
    /** Returns all declared fields of a class and all its superclasses. */
    private static List<Field> getAllFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>();
        while (cls != null && cls != Object.class) {
            fields.addAll(Arrays.asList(cls.getDeclaredFields()));
            cls = cls.getSuperclass();
        }
        return fields;
    }

    private static Object getFieldValue(Object obj, String name) {
        if (obj == null) return null;
        for (Field f : getAllFields(obj.getClass())) {
            if (f.getName().equals(name)) {
                try { f.setAccessible(true); return f.get(obj); }
                catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String getStringField(Object obj, String name) {
        Object val = getFieldValue(obj, name);
        return val instanceof String s ? s : "";
    }
}