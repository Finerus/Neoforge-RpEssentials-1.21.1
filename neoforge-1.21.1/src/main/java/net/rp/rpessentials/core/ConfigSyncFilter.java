package net.rp.rpessentials.core;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loader sends the SERVER config files in full to each client that joins (Discord token, roles, commands, staff
 * places...). The files of the mod are replaced by their default values, plus the current value of the few options
 * the client needs ({@link ConfigAccess#client}): an option that is not marked never leaves the server.
 * <p>
 * The bytes are built once and kept until one of these few values changes.
 */
public final class ConfigSyncFilter {

    private static final String PREFIX = "rpessentials/rpessentials-";

    private static final Map<String, ModConfigSpec> FILES = Map.of(
            PREFIX + "core.toml", RpEssentialsConfig.SPEC,
            PREFIX + "chat.toml", ChatConfig.SPEC,
            PREFIX + "schedule.toml", ScheduleConfig.SPEC,
            PREFIX + "moderation.toml", ModerationConfig.SPEC,
            PREFIX + "professions.toml", ProfessionConfig.SPEC,
            PREFIX + "messages.toml", MessagesConfig.SPEC,
            PREFIX + "rp.toml", RpConfig.SPEC);

    private record Built(List<Object> values, byte[] bytes) {}

    private static final Map<String, Built> CACHE = new ConcurrentHashMap<>();

    private ConfigSyncFilter() {}

    /** What to send instead of a file, or null when the file does not belong to the mod. */
    public static byte[] replacement(String fileName) {
        String name = fileName.replace('\\', '/');
        if (!name.startsWith(PREFIX)) return null;
        ModConfigSpec spec = FILES.get(name);
        // Never the real file: an empty one is completed with the default values by the client.
        if (spec == null) return new byte[0];
        try {
            List<ModConfigSpec.ConfigValue<?>> options = ConfigAccess.clientOptions(spec);
            List<Object> values = new ArrayList<>(options.size());
            for (ModConfigSpec.ConfigValue<?> o : options) values.add(Cfg.ready(spec) ? o.get() : o.getDefault());
            Built built = CACHE.get(name);
            if (built == null || !built.values().equals(values)) {
                built = new Built(values, build(spec, options, values));
                CACHE.put(name, built);
            }
            return built.bytes();
        } catch (RuntimeException e) {
            RpEssentials.LOGGER.error("[ConfigSync] Could not prepare {} for the clients, nothing is sent", name, e);
            return new byte[0];
        }
    }

    private static byte[] build(ModConfigSpec spec, List<ModConfigSpec.ConfigValue<?>> options, List<Object> values) {
        CommentedConfig config = TomlFormat.newConfig();
        spec.correct(config); // an empty config corrected = the default values, with the comments
        for (int i = 0; i < options.size(); i++) config.set(options.get(i).getPath(), values.get(i));
        return TomlFormat.instance().createWriter().writeToString(config).getBytes(StandardCharsets.UTF_8);
    }
}
