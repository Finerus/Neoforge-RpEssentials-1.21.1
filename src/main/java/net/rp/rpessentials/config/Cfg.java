package net.rp.rpessentials.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Safe access to the config files.
 *
 * The server configs are only loaded once a world starts, and a client only gets them
 * when it joins a server that has the mod. Before that, reading a value throws.
 * Use {@link #ready} to skip code that needs the config, or {@link #get} to read a
 * single value with a fallback.
 */
public final class Cfg {

    private Cfg() {}

    /** True when the given config file is loaded and its values can be read and changed. */
    public static boolean ready(ForgeConfigSpec spec) {
        return spec.isLoaded();
    }

    /** True when every given config file is loaded. */
    public static boolean ready(ForgeConfigSpec... specs) {
        for (ForgeConfigSpec spec : specs) {
            if (!spec.isLoaded()) return false;
        }
        return true;
    }

    /** The value, or {@code fallback} when its config file is not loaded yet. */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException | NullPointerException e) {
            // NeoForge gives no public way to reach the file of a single value:
            // the "not loaded yet" signal is this exception.
            return fallback;
        }
    }
}
