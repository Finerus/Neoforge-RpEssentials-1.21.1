package net.rp.rpessentials.identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.api.event.CharacterSheetChangedEvent;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.core.RpEssentialsDataPaths;
import net.rp.rpessentials.core.RpEssentialsIO;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.moderation.HistoryManager;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Character sheets: free fields filled by the staff (name, age, race, languages, appearance,
 * description, lore, custom fields from the config or from other mods). Stored by UUID in
 * character-sheets.json, so a sheet can be prepared before the player's first login.
 * Players never see them.
 */
public final class CharacterSheetManager {

    /** One field of the sheet. {@code label} is empty for built-in fields (translated on the client). */
    public record Field(String id, String label, boolean multiline) {}

    public static final int MAX_LINE_LENGTH = 128;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    private static class Sheet {
        String mcName;
        Map<String, String> values = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Sheet> sheets = new ConcurrentHashMap<>();
    /** Fields added by other mods through RpEssentialsAPI. */
    private static final List<Field> API_FIELDS = new CopyOnWriteArrayList<>();
    private static File dataFile = null;

    private CharacterSheetManager() {}

    // =========================================================================
    // FIELDS
    // =========================================================================
    public static boolean enabled() {
        return Cfg.get(RpConfig.SHEET_ENABLED, true);
    }

    /** Fields shown in the sheet, in order: built-in ones that are on, config fields, then API fields. */
    public static List<Field> fields() {
        List<Field> out = new ArrayList<>();
        addIf(out, RpConfig.SHEET_NAME, "name", false);
        addIf(out, RpConfig.SHEET_AGE, "age", false);
        addIf(out, RpConfig.SHEET_RACE, "race", false);
        addIf(out, RpConfig.SHEET_LANGUAGES, "languages", false);
        addIf(out, RpConfig.SHEET_APPEARANCE, "appearance", true);
        addIf(out, RpConfig.SHEET_DESCRIPTION, "description", true);
        addIf(out, RpConfig.SHEET_LORE, "lore", true);
        for (String line : Cfg.get(RpConfig.SHEET_CUSTOM_FIELDS, List.<String>of())) {
            String[] p = line.split(";", 3);
            if (p.length < 2) continue;
            String id = p[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (!ID.matcher(id).matches() || has(out, id)) continue;
            boolean multi = p.length >= 3 && p[2].trim().equalsIgnoreCase("TEXT");
            out.add(new Field(id, p[1].trim().isEmpty() ? id : p[1].trim(), multi));
        }
        for (Field f : API_FIELDS) if (!has(out, f.id())) out.add(f);
        return out;
    }

    private static void addIf(List<Field> out, ModConfigSpec.BooleanValue toggle, String id, boolean multi) {
        if (Cfg.get(toggle, true)) out.add(new Field(id, "", multi));
    }

    private static boolean has(List<Field> fields, String id) {
        for (Field f : fields) if (f.id().equals(id)) return true;
        return false;
    }

    /** Called by RpEssentialsAPI. A field with an id already used is ignored. */
    public static void registerField(String id, String label, boolean multiline) {
        String clean = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ID.matcher(clean).matches()) throw new IllegalArgumentException("Invalid character sheet field id: " + id);
        API_FIELDS.removeIf(f -> f.id().equals(clean));
        API_FIELDS.add(new Field(clean, label == null || label.isBlank() ? clean : label, multiline));
    }

    /** Label of a field in the server language (history, console). */
    public static String label(Field f) {
        if (!f.label().isEmpty()) return f.label();
        return ServerLang.get(null, switch (f.id()) {
            case "name" -> MessagesConfig.SHEET_F_NAME;
            case "age" -> MessagesConfig.SHEET_F_AGE;
            case "race" -> MessagesConfig.SHEET_F_RACE;
            case "languages" -> MessagesConfig.SHEET_F_LANGUAGES;
            case "appearance" -> MessagesConfig.SHEET_F_APPEARANCE;
            case "description" -> MessagesConfig.SHEET_F_DESCRIPTION;
            default -> MessagesConfig.SHEET_F_LORE;
        });
    }

    // =========================================================================
    // LOAD / SAVE
    // =========================================================================
    public static synchronized void reload() {
        sheets.clear();
        dataFile = RpEssentialsDataPaths.getDataFile("character-sheets.json");
        if (!dataFile.exists()) return;
        try (var reader = new InputStreamReader(Files.newInputStream(dataFile.toPath()), StandardCharsets.UTF_8)) {
            Map<String, Sheet> data = GSON.fromJson(reader, new TypeToken<Map<String, Sheet>>() {}.getType());
            if (data != null) {
                for (var e : data.entrySet()) {
                    try {
                        if (e.getValue() != null) sheets.put(UUID.fromString(e.getKey()), e.getValue());
                    } catch (IllegalArgumentException ex) {
                        RpEssentials.LOGGER.warn("[CharacterSheets] Invalid UUID key: {}", e.getKey());
                    }
                }
            }
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[CharacterSheets] Failed to load character-sheets.json", e);
            RpEssentialsIO.quarantine(dataFile);
        }
        RpEssentials.LOGGER.info("[CharacterSheets] {} sheet(s)", sheets.size());
    }

    private static void save() {
        if (dataFile == null) return;
        Map<String, Sheet> copy = new LinkedHashMap<>();
        for (var e : sheets.entrySet()) {
            Sheet s = new Sheet();
            s.mcName = e.getValue().mcName;
            s.values = new LinkedHashMap<>(e.getValue().values);
            copy.put(e.getKey().toString(), s);
        }
        RpEssentialsIO.saveJson(dataFile, copy, GSON);
    }

    // =========================================================================
    // ACCESS
    // =========================================================================
    /** Every stored value of a player, including fields that are currently turned off. */
    public static Map<String, String> get(UUID player) {
        Sheet s = sheets.get(player);
        return s == null ? Map.of() : new LinkedHashMap<>(s.values);
    }

    /**
     * Replaces the values of the enabled fields with {@code values} (missing or blank = cleared).
     * Values of fields that are turned off are kept. Returns the ids of the fields that changed.
     */
    public static synchronized List<String> update(UUID player, String mcName, Map<String, String> values, String actor) {
        List<String> changed = new ArrayList<>();
        Sheet s = sheets.computeIfAbsent(player, k -> new Sheet());
        if (mcName != null && !mcName.isBlank()) s.mcName = mcName;
        int maxText = Cfg.get(RpConfig.SHEET_MAX_TEXT_LENGTH, 4000);
        for (Field f : fields()) {
            String v = values.getOrDefault(f.id(), "");
            v = v == null ? "" : v.replace("\r", "");
            if (!f.multiline()) v = v.replace("\n", " ").strip();
            else v = v.stripTrailing();
            int max = f.multiline() ? maxText : MAX_LINE_LENGTH;
            if (v.length() > max) v = v.substring(0, max);
            String old = s.values.getOrDefault(f.id(), "");
            if (old.equals(v)) continue;
            if (v.isEmpty()) s.values.remove(f.id()); else s.values.put(f.id(), v);
            changed.add(f.id());
        }
        if (s.values.isEmpty() && s.mcName == null) sheets.remove(player);
        if (changed.isEmpty()) return changed;
        save();
        List<String> labels = new ArrayList<>();
        for (Field f : fields()) if (changed.contains(f.id())) labels.add(label(f));
        HistoryManager.record(player, mcName, HistoryManager.SHEET, actor, String.join(", ", labels));
        NeoForge.EVENT_BUS.post(new CharacterSheetChangedEvent(player, List.copyOf(changed)));
        return changed;
    }

    /** Sets one field (API). Returns true when it changed. */
    public static boolean set(UUID player, String fieldId, String value, String actor) {
        Map<String, String> values = new LinkedHashMap<>(get(player));
        values.put(fieldId, value == null ? "" : value);
        return !update(player, null, values, actor).isEmpty();
    }
}
