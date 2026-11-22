package net.rp.rpessentials.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraftforge.common.ForgeConfigSpec;
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

    public record FileInfo(String id, String displayName, int color, String mod, int modColor, Theme theme) {
        public FileInfo(String id, String displayName) { this(id, displayName, 0, "", 0, Theme.DEFAULT); }
    }

    private record ConfigClassInfo(String id, String displayName, int color, String mod, int modColor, Theme theme,
                                   Class<?> clazz, ForgeConfigSpec spec,
                                   java.util.function.Consumer<net.minecraft.server.MinecraftServer> onApplied) {
        ConfigClassInfo(String id, String displayName, Class<?> clazz, ForgeConfigSpec spec) {
            this(id, displayName, 0, "RpEssentials", 0xFFD700, Theme.DEFAULT, clazz, spec, null);
        }
    }

    private static final List<ConfigClassInfo> CONFIGS = List.of(
            new ConfigClassInfo("core",        "Core",        RpEssentialsConfig.class, RpEssentialsConfig.SPEC),
            new ConfigClassInfo("chat",        "Chat",        ChatConfig.class,          ChatConfig.SPEC),
            new ConfigClassInfo("schedule",    "Schedule",    ScheduleConfig.class,      ScheduleConfig.SPEC),
            new ConfigClassInfo("moderation",  "Moderation",  ModerationConfig.class,    ModerationConfig.SPEC),
            new ConfigClassInfo("professions", "Professions", ProfessionConfig.class,    ProfessionConfig.SPEC),
            new ConfigClassInfo("messages",    "Messages",    MessagesConfig.class,      MessagesConfig.SPEC),
            new ConfigClassInfo("rp",    "RP",                RpConfig.class,            RpConfig.SPEC)
    );

    private static final List<ConfigClassInfo> EXTERNAL = new java.util.concurrent.CopyOnWriteArrayList<>();

    public record Theme(int accent, int section, int title) {
        public static final Theme DEFAULT = new Theme(0, 0, 0);
    }

    public static void register(String id, String displayName, int color, Class<?> clazz, ForgeConfigSpec spec,
                                java.util.function.Consumer<net.minecraft.server.MinecraftServer> onApplied) {
        register("Other", 0xAAAAAA, id, displayName, color, Theme.DEFAULT, clazz, spec, onApplied);
    }

    public static void register(String mod, int modColor, String id, String displayName, int color, Theme theme,
                                Class<?> clazz, ForgeConfigSpec spec,
                                java.util.function.Consumer<net.minecraft.server.MinecraftServer> onApplied) {
        EXTERNAL.removeIf(c -> c.id().equals(id));
        EXTERNAL.add(new ConfigClassInfo(id, displayName, color, mod, modColor, theme, clazz, spec, onApplied));
    }

    private static List<ConfigClassInfo> all() {
        List<ConfigClassInfo> out = new ArrayList<>(CONFIGS);
        out.addAll(EXTERNAL);
        return out;
    }

    private static final Map<Class<?>, Map<String, ForgeConfigSpec.ConfigValue<?>>> VALUE_MAP_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    // =========================================================================
    // PUBLIC API
    // =========================================================================
    public static List<FileInfo> getFileInfos() {
        return all().stream()
                .map(c -> new FileInfo(c.id(), c.displayName(),
                        // Professions turned off: the file is greyed out.
                        isDisabledProfessions(c.id()) ? 0x666666 : c.color(), c.mod(), c.modColor(), c.theme()))
                .toList();
    }

    public static List<EntryData> getEntries(String fileId) {
        return all().stream()
                .filter(c -> c.id().equals(fileId))
                .findFirst()
                .map(ConfigInspector::inspectClass)
                .orElse(List.of());
    }

    public static int applyAndSave(String fileId, Map<String, String> changes) {
        Optional<ConfigClassInfo> opt = all().stream().filter(c -> c.id().equals(fileId)).findFirst();
        if (opt.isEmpty()) return 0;

        ConfigClassInfo info = opt.get();
        Map<String, ForgeConfigSpec.ConfigValue<?>> valueMap = buildValueMap(info.clazz());

        // Pré-calcul des entryData pour la validation de range
        Map<String, EntryData> entryDataMap = getEntries(fileId).stream()
                .filter(e -> !e.isSection())
                .collect(java.util.stream.Collectors.toMap(EntryData::fullPath, e -> e));

        int applied = 0;

        for (Map.Entry<String, String> change : changes.entrySet()) {
            ForgeConfigSpec.ConfigValue<?> cv = valueMap.get(change.getKey());
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
            if (info.onApplied() != null) {
                net.minecraft.server.MinecraftServer server =
                        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    try { info.onApplied().accept(server); }
                    catch (Exception e) {
                        RpEssentials.LOGGER.error("[ConfigInspector] Callback failed for '{}': {}", fileId, e.getMessage());
                    }
                }
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

        for (ForgeConfigSpec.ConfigValue<?> cv : buildValueMap(info.clazz()).values()) {
            List<String> path = cv.getPath();
            if (path.isEmpty()) continue;

            String sectionKey = path.size() > 1
                    ? String.join(".", path.subList(0, path.size() - 1))
                    : "";
            if (!Objects.equals(sectionKey, lastSection)) {
                if (!sectionKey.isEmpty()) {
                    entries.add(EntryData.section(path.get(path.size() - 2), sectionKey));
                }
                lastSection = sectionKey;
            }

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

    static Map<String, ForgeConfigSpec.ConfigValue<?>> buildValueMap(Class<?> clazz) {
        return VALUE_MAP_CACHE.computeIfAbsent(clazz, c -> {
            Map<String, ForgeConfigSpec.ConfigValue<?>> map = new LinkedHashMap<>();
            for (Field field : c.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (!ForgeConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    ForgeConfigSpec.ConfigValue<?> cv = (ForgeConfigSpec.ConfigValue<?>) field.get(null);
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
     * Retrieves the NeoForge ValueSpec stored inside ForgeConfigSpec for the given path.
     * -
     * ForgeConfigSpec extends Night Config's UnmodifiableConfig.
     * The Builder stores ValueSpec objects as leaves of the spec config tree.
     * So ((UnmodifiableConfig) spec).get(path) returns the ValueSpec.
     * -
     * Three strategies tried in order:
     *   1. Cast to UnmodifiableConfig and call get(path)          [NeoForge 21.1 primary]
     *   2. Reflect on fields of ForgeConfigSpec looking for a Map   [fallback]
     *   3. Scan UnmodifiableConfig sub-fields                     [fallback]
     */
    private static Object getValueSpecObj(ForgeConfigSpec spec, List<String> path) {
        // Strategy 1 — Night Config UnmodifiableConfig.get(List<String>)
        try {
            Object result = ((UnmodifiableConfig) spec).get(path);
            if (result != null && isValueSpec(result)) return result;
        } catch (Exception ignored) {}

        // Strategy 2 — reflect on Map fields of ForgeConfigSpec
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
    private static Object getValueSpecViaMapField(ForgeConfigSpec spec, List<String> path) {
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

    public static ValueType detectType(ForgeConfigSpec.ConfigValue<?> cv) {
        if (cv instanceof ForgeConfigSpec.BooleanValue) return ValueType.BOOLEAN;
        if (cv instanceof ForgeConfigSpec.IntValue)     return ValueType.INT;
        if (cv instanceof ForgeConfigSpec.LongValue)    return ValueType.LONG;
        if (cv instanceof ForgeConfigSpec.DoubleValue)  return ValueType.DOUBLE;

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
     * NeoForge 21.1 ValueSpec.getRange() → net.minecraftforge.common.ForgeConfigSpec.Range
     * Range.getMin() / Range.getMax() return Comparable.
     */
    private static double[] extractRange(ForgeConfigSpec.ConfigValue<?> cv, Object vs) {
        if (!(cv instanceof ForgeConfigSpec.IntValue)
                && !(cv instanceof ForgeConfigSpec.DoubleValue)
                && !(cv instanceof ForgeConfigSpec.LongValue)) return null;
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

    /** Rules of an option (Forge 1.20.1 has no ConfigValue.getSpec()). */
    private static ForgeConfigSpec.ValueSpec valueSpec(ForgeConfigSpec.ConfigValue<?> cv) {
        try {
            java.lang.reflect.Field f = ForgeConfigSpec.ConfigValue.class.getDeclaredField("spec");
            f.setAccessible(true);
            ForgeConfigSpec owner = (ForgeConfigSpec) f.get(cv);
            if (owner == null) return null;
            Object o = owner.getSpec().get(cv.getPath());
            return o instanceof ForgeConfigSpec.ValueSpec v ? v : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean applyValue(ForgeConfigSpec.ConfigValue<?> cv, ValueType type, String raw) {
        try {
            Object parsed = parseValue(type, raw);
            if (parsed == null) return false;
            // The option's own rules (allowed words, list element checks...), not only the range.
            ForgeConfigSpec.ValueSpec spec = valueSpec(cv);
            if (spec != null && !spec.test(parsed)) {
                RpEssentials.LOGGER.warn("[ConfigInspector] Value '{}' refused by the rules of '{}'", raw, String.join(".", cv.getPath()));
                return false;
            }
            // Words-only options (display modes, forceState...): a typo is refused here, never saved.
            if (parsed instanceof String str && ConfigRules.problem(String.join(".", cv.getPath()), str) != null) {
                RpEssentials.LOGGER.warn("[ConfigInspector] Value '{}' refused by the rules of '{}'", raw, String.join(".", cv.getPath()));
                return false;
            }
            ((ForgeConfigSpec.ConfigValue<Object>) cv).set(parsed);
            return true;
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[ConfigInspector] Cannot apply '{}' as {}: {}", raw, type, e.getMessage());
            return false;
        }
    }

    public static Object parseValue(ValueType type, String raw) {
        if (raw == null) return null;
        return switch (type) {
            case BOOLEAN     -> {
                // Only true / false: "yes" or a typo must not silently become false.
                String b = raw.trim().toLowerCase(java.util.Locale.ROOT);
                yield b.equals("true") ? Boolean.TRUE : b.equals("false") ? Boolean.FALSE : null;
            }
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
            "autoUnwhitelistExtraCommands", "opLevelBypass",
            "useLuckPermsGroups", "roles", "luckPermsSync", "luckPermsImportAllGroups",
            "discordWebhookUrl");

    /** True for the professions file while the profession system is turned off. */
    private static boolean isDisabledProfessions(String fileId) {
        return "professions".equals(fileId) && !ProfessionConfig.enabled();
    }

    public static List<EntryData> getEntries(String fileId, boolean includeSensitive) {
        List<EntryData> all = getEntries(fileId);
        if (isDisabledProfessions(fileId)) {
            // Only the switch is left (with its section), so the system can be turned on again.
            List<EntryData> out = new ArrayList<>();
            EntryData section = null;
            for (EntryData e : all) {
                if (e.isSection()) { section = e; continue; }
                if (!e.key().equals(ProfessionConfig.ENABLE_KEY)) continue;
                if (section != null) out.add(section);
                out.add(e);
            }
            return out;
        }
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

    public static String exportDefaults(Class<?>... classes) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        for (Class<?> c : classes) {
            for (Map.Entry<String, ForgeConfigSpec.ConfigValue<?>> e : buildValueMap(c).entrySet()) {
                if (e.getKey().startsWith("Message Source")) continue;
                if (e.getValue().getDefault() instanceof String s) o.addProperty(e.getKey(), s);
            }
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o);
    }
}