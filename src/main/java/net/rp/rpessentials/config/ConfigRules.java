package net.rp.rpessentials.config;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation rules shared by several config options. A value that breaks its rule is replaced by the
 * default when the file is loaded (NeoForge keeps a .bak copy of the file), and is refused by the
 * Config Manager and /rpessentials config set.
 */
public final class ConfigRules {

    private static final Pattern DISPLAY_MODE = Pattern.compile("CHAT|ACTION_BAR|TITLE|IMMERSIVE(:[A-Z0-9_\\-]+)?");

    private ConfigRules() {}

    /** Display mode of a message: CHAT, ACTION_BAR, TITLE, IMMERSIVE or IMMERSIVE:<preset> (any case). */
    public static boolean isDisplayMode(Object o) {
        return o instanceof String s && DISPLAY_MODE.matcher(s.trim().toUpperCase(Locale.ROOT)).matches();
    }
}
