package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Rules of the options that only accept some words (display modes, forceState, blurStyle...).
 * <p>
 * The rules are "soft": a wrong value written in the file (a typo such as {@code HIDDE}) is kept in the
 * file instead of being replaced by the default, so the admin sees what they wrote. Meanwhile:
 * <ul>
 *   <li>the mod uses the default value ({@link #value});</li>
 *   <li>the line is shown in red in the Config Manager, and the Config Manager and
 *       /rpessentials config set refuse a wrong value;</li>
 *   <li>staff are told in the chat when they join and after a reload, and the server log lists it
 *       ({@link #problems}, also in /rpessentials config validate).</li>
 * </ul>
 * Rules are found by the option path ("Section.option"), on the server and on the client.
 */
public final class ConfigRules {

    private static final Pattern DISPLAY_MODE = Pattern.compile("CHAT|ACTION_BAR|TITLE|IMMERSIVE(:[A-Z0-9_\\-]+)?");
    public static final String DISPLAY_MODES = "CHAT, ACTION_BAR, TITLE, IMMERSIVE, IMMERSIVE:<preset>";

    /** A rule: the test and the text shown to say what is expected. */
    public record Rule(ModConfigSpec.ConfigValue<String> option, Predicate<String> test, String expected) {}

    /** A wrong value found in a loaded file. */
    public record Problem(String path, String value, String expected, String defaultValue) {}

    private static final Map<String, Rule> RULES = new ConcurrentHashMap<>();

    private ConfigRules() {}

    /** Display mode of a message: CHAT, ACTION_BAR, TITLE, IMMERSIVE or IMMERSIVE:<preset> (any case). */
    public static boolean isDisplayMode(Object o) {
        return o instanceof String s && DISPLAY_MODE.matcher(s.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /** Declares a display mode option (call it right after the define). */
    public static ModConfigSpec.ConfigValue<String> displayMode(ModConfigSpec.ConfigValue<String> option) {
        return add(option, ConfigRules::isDisplayMode, DISPLAY_MODES);
    }

    /** Declares an option that accepts one of these words, written exactly like this. */
    public static ModConfigSpec.ConfigValue<String> oneOf(ModConfigSpec.ConfigValue<String> option, List<String> allowed) {
        List<String> copy = List.copyOf(allowed);
        return add(option, copy::contains, String.join(", ", copy));
    }

    private static ModConfigSpec.ConfigValue<String> add(ModConfigSpec.ConfigValue<String> option,
                                                         Predicate<String> test, String expected) {
        RULES.put(String.join(".", option.getPath()), new Rule(option, test, expected));
        return option;
    }

    /** The rule of an option, or null when it accepts any value. */
    public static Rule rule(String path) {
        return path == null ? null : RULES.get(path);
    }

    /** What the option expects when {@code value} is wrong, null when the value is fine (or there is no rule). */
    public static String problem(String path, String value) {
        Rule r = rule(path);
        if (r == null || value == null) return null;
        return r.test().test(value) ? null : r.expected();
    }

    /** Value of the option, or its default while the file holds a wrong value (or is not loaded). */
    public static String value(ModConfigSpec.ConfigValue<String> option) {
        String v = Cfg.get(option, option.getDefault());
        Rule r = RULES.get(String.join(".", option.getPath()));
        return r == null || r.test().test(v) ? v : option.getDefault();
    }

    /** Wrong values in the loaded files. */
    public static List<Problem> problems() {
        List<Problem> out = new ArrayList<>();
        for (Map.Entry<String, Rule> e : RULES.entrySet()) {
            Rule r = e.getValue();
            String v;
            try { v = r.option().get(); } catch (IllegalStateException | NullPointerException notLoaded) { continue; }
            if (!r.test().test(v)) out.add(new Problem(e.getKey(), v, r.expected(), r.option().getDefault()));
        }
        out.sort(java.util.Comparator.comparing(Problem::path));
        return out;
    }
}
