package net.rp.rpessentials.lang;

import net.rp.rpessentials.config.Cfg;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.config.MessagesConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.rp.rpessentials.RpEssentials;

public final class ServerLang {

    private static final Pattern LANG = Pattern.compile("[a-z]{2,3}_[a-z]{2,4}");
    private static final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private ServerLang() {}

    private static String normalize(String l) {
        l = l == null ? "" : l.toLowerCase(Locale.ROOT);
        return LANG.matcher(l).matches() ? l : "en_us";
    }

    public static String lang(ServerPlayer p) {
        return normalize(p.clientInformation().language());
    }

    private static boolean modSource() {
        if (Cfg.ready(MessagesConfig.SPEC)) { return "MOD".equalsIgnoreCase(net.rp.rpessentials.config.ConfigRules.value(MessagesConfig.MESSAGE_SOURCE)); } else { return false; }
    }

    private static String serverLang() {
        if (Cfg.ready(MessagesConfig.SPEC)) { return normalize(MessagesConfig.SERVER_LANGUAGE.get()); } else { return "en_us"; }
    }

    private static Map<String, String> read(String l) {
        try (InputStream in = ServerLang.class.getResourceAsStream("/data/rpessentials/messages/" + l + ".json")) {
            if (in == null) return Map.of();
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> m = new HashMap<>();
            for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            return m;
        } catch (Exception e) {
            RpEssentials.LOGGER.warn("[Lang] Could not read {}: {}", l, e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> load(String lang) {
        Map<String, String> m = cache.get(lang);
        if (m != null) return m;
        if (missing.contains(lang)) return Map.of();
        m = read(lang);
        if (m.isEmpty()) m = read(lang.substring(0, 2) + "_" + lang.substring(0, 2));
        if (m.isEmpty()) {
            if (missing.size() < 64) missing.add(lang);
            return m;
        }
        cache.put(lang, m);
        return m;
    }

    public static void reload() {
        cache.clear();
        missing.clear();
    }

    private static String fill(String value, String... replacements) {
        value = value.replace("\r", "");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return value;
    }

    public static String get(ServerPlayer p, ModConfigSpec.ConfigValue<String> cv, String... replacements) {
        String value = Cfg.get(cv, cv.getDefault());
        if (modSource()) {
            String key = String.join(".", cv.getPath());
            String t = p != null ? load(lang(p)).get(key) : null;
            if (t == null) t = load(serverLang()).get(key);
            if (t == null) t = load("en_us").get(key);
            if (t != null) value = t;
        }
        return fill(value, replacements);
    }
}