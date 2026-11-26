package net.rp.rpessentials.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.ConfigInspector;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.core.ConfigRefresh;
import net.rp.rpessentials.lang.Msg;
import net.rp.rpessentials.permission.RpEssentialsPermissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /rpessentials config set ...: changes any option of any config file, through the same path as the
 * Config Manager (validation, save, refresh of what the file affects).
 * <pre>
 *   set &lt;option&gt; &lt;value&gt;          option name, when only one file has it
 *   set &lt;file&gt; &lt;option&gt; &lt;value&gt;   any option of a file (core, chat, schedule, moderation, professions, messages, rp...)
 * </pre>
 * An option whose name is used twice in the same file is written with its sections, without spaces
 * ({@code DeathRP.PlayerToggle.enableMessage}); suggestions show the right form.
 * The names of the old setters (blur, proximity, deathRpDeathSound...) keep working.
 */
public final class ConfigSetCommand {

    private ConfigSetCommand() {}

    /** Old /config set names that differ from the option name: alias -> file, option name or path. */
    private static final Map<String, String[]> ALIASES = Map.ofEntries(
            Map.entry("proximity", new String[]{"core", "proximityDistance"}),
            Map.entry("enableWorldBorderWarning", new String[]{"core", "enableWorldBorder"}),
            Map.entry("blur", new String[]{"core", "enableBlur"}),
            Map.entry("deathRpGlobalEnabled", new String[]{"core", "globalEnabled"}),
            Map.entry("deathRpWhitelistRemove", new String[]{"core", "whitelistRemove"}),
            Map.entry("deathRpDeathMessage", new String[]{"core", "deathMessage"}),
            Map.entry("deathRpDeathSound", new String[]{"core", "deathSound"}),
            Map.entry("deathRpDeathSoundVolume", new String[]{"core", "deathSoundVolume"}),
            Map.entry("deathRpDeathSoundPitch", new String[]{"core", "deathSoundPitch"}),
            Map.entry("deathRpPlayerToggleSound", new String[]{"core", "toggleSound"}),
            Map.entry("deathRpGlobalToggleSound", new String[]{"core", "globalToggleSound"}),
            // enableMessage & co exist twice in core: resolved by their real constant below.
            Map.entry("deathRpPlayerEnableMsg", new String[]{"core", "#DEATH_RP_PLAYER_ENABLE_MSG"}),
            Map.entry("deathRpPlayerEnableMode", new String[]{"core", "#DEATH_RP_PLAYER_ENABLE_MODE"}),
            Map.entry("deathRpPlayerDisableMsg", new String[]{"core", "#DEATH_RP_PLAYER_DISABLE_MSG"}),
            Map.entry("deathRpPlayerDisableMode", new String[]{"core", "#DEATH_RP_PLAYER_DISABLE_MODE"}),
            Map.entry("deathRpGlobalEnableMsg", new String[]{"core", "#DEATH_RP_GLOBAL_ENABLE_MSG"}),
            Map.entry("deathRpGlobalEnableMode", new String[]{"core", "#DEATH_RP_GLOBAL_ENABLE_MODE"}),
            Map.entry("deathRpGlobalDisableMsg", new String[]{"core", "#DEATH_RP_GLOBAL_DISABLE_MSG"}),
            Map.entry("deathRpGlobalDisableMode", new String[]{"core", "#DEATH_RP_GLOBAL_DISABLE_MODE"}));

    /** Extra value suggestions for text options that only accept some words. */
    private static final List<String> DISPLAY_MODES = List.of("CHAT", "ACTION_BAR", "TITLE", "IMMERSIVE", "IMMERSIVE:<preset>");

    private static final Map<String, List<String>> VALUE_HINTS = Map.of(
            "chatMessageColor", List.of("WHITE", "GRAY", "DARK_GRAY", "BLACK", "RED", "DARK_RED", "GOLD", "YELLOW",
                    "GREEN", "DARK_GREEN", "AQUA", "DARK_AQUA", "BLUE", "DARK_BLUE", "LIGHT_PURPLE", "DARK_PURPLE"),
            "zoneMessageMode", DISPLAY_MODES,
            "enableMessageMode", DISPLAY_MODES,
            "disableMessageMode", DISPLAY_MODES,
            "hrpMessageMode", DISPLAY_MODES,
            "scheduleDeathHoursNotifyMode", DISPLAY_MODES,
            "forceState", List.of("NONE", "FORCE_OPEN", "FORCE_CLOSED"),
            "nametagBlurMode", List.of("OFF", "BLUR", "HIDE"),
            "blurStyle", List.of("OBFUSCATED", "DASHES", "HASHES", "STARS"));

    /** File of the texts: its options (several hundreds) are only suggested after {@code set messages}. */
    private static final String MESSAGES_FILE = "messages";

    /** One option of one file, with the name typed to reach it. */
    private record Option(String fileId, ConfigInspector.EntryData entry, String name) {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var set = Commands.literal("set");
        for (ConfigInspector.FileInfo f : ConfigInspector.getFileInfos()) {
            final String fileId = f.id();
            set.then(Commands.literal(fileId)
                    .then(Commands.argument("option", StringArgumentType.string())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                    options(ctx.getSource(), fileId).stream().map(Option::name), b))
                            .then(Commands.argument("value", StringArgumentType.greedyString())
                                    .suggests(valueSuggestions(fileId))
                                    .executes(ctx -> apply(ctx, fileId)))));
        }
        set.then(Commands.argument("option", StringArgumentType.string())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(shortNames(ctx.getSource()), b))
                .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(valueSuggestions(null))
                        .executes(ctx -> apply(ctx, null))));
        return set;
    }

    // =========================================================================
    // OPTIONS
    // =========================================================================
    /** Options of a file the source may change, each with the shortest name that is unique in the file. */
    private static List<Option> options(CommandSourceStack src, String fileId) {
        List<ConfigInspector.EntryData> entries = new ArrayList<>();
        for (ConfigInspector.EntryData e : ConfigInspector.getEntries(fileId, p -> net.rp.rpessentials.permission.RpPerms.has(src, p))) {
            if (!e.isSection()) entries.add(e);
        }
        Map<String, Integer> keyCount = new HashMap<>();
        for (ConfigInspector.EntryData e : entries) keyCount.merge(e.key(), 1, Integer::sum);
        List<Option> out = new ArrayList<>();
        for (ConfigInspector.EntryData e : entries) {
            String name = keyCount.get(e.key()) == 1 ? e.key() : e.fullPath().replace(" ", "");
            out.add(new Option(fileId, e, name));
        }
        return out;
    }

    /** Option names unique across every file (texts excluded), plus the old names: suggested after "set ". */
    private static Set<String> shortNames(CommandSourceStack src) {
        Map<String, Integer> count = new HashMap<>();
        for (ConfigInspector.FileInfo f : ConfigInspector.getFileInfos()) {
            if (f.id().equals(MESSAGES_FILE)) continue;
            for (Option o : options(src, f.id())) if (o.name().equals(o.entry().key())) count.merge(o.name(), 1, Integer::sum);
        }
        Set<String> out = new LinkedHashSet<>();
        count.forEach((k, n) -> { if (n == 1) out.add(k); });
        out.addAll(ALIASES.keySet());
        return out;
    }

    /** Finds an option from what was typed; {@code fileId} null = search every file. */
    private static Option find(CommandSourceStack src, String fileId, String typed) {
        List<Option> found = candidates(src, fileId, typed);
        return found.size() == 1 ? found.get(0) : null;
    }

    /** Every option matching what was typed (several = ambiguous name). */
    private static List<Option> candidates(CommandSourceStack src, String fileId, String typed) {
        String[] alias = fileId == null ? ALIASES.get(typed) : null;
        if (alias != null) {
            fileId = alias[0];
            typed = alias[1];
            if (typed.startsWith("#")) {
                Option o = byConstant(src, fileId, typed.substring(1));
                return o != null ? List.of(o) : List.of();
            }
        }
        List<Option> found = new ArrayList<>();
        for (ConfigInspector.FileInfo f : ConfigInspector.getFileInfos()) {
            if (fileId != null && !f.id().equals(fileId)) continue;
            for (Option o : options(src, f.id())) {
                if (o.entry().key().equalsIgnoreCase(typed) || o.name().equalsIgnoreCase(typed)
                        || o.entry().fullPath().replace(" ", "").equalsIgnoreCase(typed)) {
                    found.add(o);
                }
            }
        }
        // Without a file name, an option wins over a text with the same name (texts: "set messages <name>").
        if (fileId == null && found.stream().anyMatch(o -> !o.fileId().equals(MESSAGES_FILE))) {
            found.removeIf(o -> o.fileId().equals(MESSAGES_FILE));
        }
        // An exact name or path wins over options that only share the short key.
        final String name = typed;
        List<Option> exact = found.stream().filter(o -> o.name().equalsIgnoreCase(name)
                || o.entry().fullPath().replace(" ", "").equalsIgnoreCase(name)).toList();
        return exact.size() == 1 ? exact : found;
    }

    /** Option of the core file stored in RpEssentialsConfig.{@code constant} (for the old Death RP names). */
    private static Option byConstant(CommandSourceStack src, String fileId, String constant) {
        try {
            var cv = (net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>)
                    net.rp.rpessentials.config.RpEssentialsConfig.class.getField(constant).get(null);
            String path = String.join(".", cv.getPath());
            for (Option o : options(src, fileId)) if (o.entry().fullPath().equals(path)) return o;
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Constant renamed: the alias simply stops working.
        }
        return null;
    }

    private static SuggestionProvider<CommandSourceStack> valueSuggestions(String fileId) {
        return (ctx, b) -> {
            Option o = find(ctx.getSource(), fileId, StringArgumentType.getString(ctx, "option"));
            if (o == null) return b.buildFuture();
            List<String> values = new ArrayList<>();
            switch (o.entry().type()) {
                case BOOLEAN -> { values.add("true"); values.add("false"); }
                default -> {
                    List<String> hint = VALUE_HINTS.get(o.entry().key());
                    net.rp.rpessentials.config.ConfigRules.Rule rule = net.rp.rpessentials.config.ConfigRules.rule(o.entry().fullPath());
                    if (hint == null && rule != null) hint = List.of(rule.expected().split(", "));
                    // "IMMERSIVE:<preset>" explains the format in errors; it is not a value to insert.
                    if (hint != null) for (String h : hint) if (!h.contains("<")) values.add(h);
                }
            }
            String current = o.entry().currentValue();
            if (current != null && !current.isEmpty() && !current.contains("\n") && !values.contains(current)) {
                values.add(current);
            }
            return SharedSuggestionProvider.suggest(values, b);
        };
    }

    // =========================================================================
    // APPLY
    // =========================================================================
    private static int apply(CommandContext<CommandSourceStack> ctx, String fileId) {
        CommandSourceStack src = ctx.getSource();
        String typed = StringArgumentType.getString(ctx, "option");
        String value = StringArgumentType.getString(ctx, "value");
        List<Option> found = candidates(src, fileId, typed);
        if (found.size() > 1) {
            List<String> choices = new ArrayList<>();
            for (Option c : found) choices.add(c.fileId() + " " + c.name());
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_CFG_AMBIGUOUS_OPTION, "option", typed,
                    "choices", String.join(", ", choices)));
            return 0;
        }
        if (found.isEmpty()) {
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_CFG_UNKNOWN_OPTION, "option", typed));
            return 0;
        }
        Option o = found.get(0);
        // Lists are typed on one line, separated by ";" (one line per element in the file).
        if (o.entry().type() == ConfigInspector.ValueType.LIST_STRING || o.entry().type() == ConfigInspector.ValueType.LIST_INT) {
            value = String.join("\n", value.split(";"));
        }
        String old = o.entry().currentValue();
        if (o.entry().key().equals(net.rp.rpessentials.config.ProfessionConfig.ENABLE_KEY)
                && !needsNoConfirm(src, old, value)) {
            return 0;
        }
        int applied = ConfigInspector.applyAndSave(o.fileId(), Map.of(o.entry().fullPath(), value));
        if (applied <= 0) {
            src.sendFailure(Msg.c(src, MessagesConfig.STAFF_CFG_INVALID_VALUE, "option", o.name(), "value", value,
                    "expected", expected(o.entry())));
            return 0;
        }
        ConfigRefresh.refresh(src.getServer(), ConfigInspector.specOf(o.fileId()));
        // A secret (webhook URL, bot token) is never written in the chat of the staff nor in the log.
        String shown = ConfigInspector.mask(o.entry().key(), value).replace("\n", "; ");
        String before = ConfigInspector.mask(o.entry().key(), old == null ? "" : old).replace("\n", "; ");
        // A restricted option is not repeated to the other OPs.
        boolean tellOthers = !ConfigInspector.isRestricted(o.fileId(), o.entry().fullPath());
        src.sendSuccess(() -> Msg.c(src, MessagesConfig.STAFF_CFG_VALUE_CHANGED, "file", o.fileId(), "name", o.name(),
                "old", before, "value", shown), tellOthers);
        if (!tellOthers) net.rp.rpessentials.RpEssentials.LOGGER.info("[Config] {} changed '{}' ({})", src.getTextName(), o.name(), o.fileId());
        return 1;
    }

    /** Pending "turn the profession system on / off" per command source: value and time. */
    private static final Map<String, Long> professionConfirm = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CONFIRM_MS = 30_000L;

    /**
     * Turning the profession system on or off changes how the whole mod behaves: the same command must be typed
     * twice within 30 seconds. Returns true when the change can be applied now.
     */
    private static boolean needsNoConfirm(CommandSourceStack src, String old, String value) {
        boolean target = Boolean.parseBoolean(value.trim());
        if (!value.trim().equalsIgnoreCase("true") && !value.trim().equalsIgnoreCase("false")) return true; // invalid: normal error
        if (String.valueOf(target).equalsIgnoreCase(old == null ? "" : old.trim())) return true; // no change
        String key = src.getTextName() + "|" + target;
        Long at = professionConfirm.get(key);
        long now = System.currentTimeMillis();
        if (at != null && now - at <= CONFIRM_MS) {
            professionConfirm.remove(key);
            return true;
        }
        professionConfirm.put(key, now);
        String k = target ? "rpessentials.professions.confirm_enable" : "rpessentials.professions.confirm_disable";
        src.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback(k, target
                        ? "⚠ This turns the profession system back on: restrictions, license commands and the license item work again."
                        : "⚠ This turns off the whole profession system: no more restrictions, license commands or license item. Professions and licenses are kept.")
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        src.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback(
                "rpessentials.professions.confirm_again", "Type the same command again within 30 seconds to confirm.")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        return false;
    }

    /** What the option accepts, for the error message. */
    private static String expected(ConfigInspector.EntryData e) {
        String type = e.type().name().toLowerCase(Locale.ROOT);
        if (e.hasRange()) type += " [" + fmt(e.rangeMin()) + " - " + fmt(e.rangeMax()) + "]";
        List<String> hint = VALUE_HINTS.get(e.key());
        net.rp.rpessentials.config.ConfigRules.Rule rule = net.rp.rpessentials.config.ConfigRules.rule(e.fullPath());
        if (e.type() == ConfigInspector.ValueType.BOOLEAN) type = "true / false";
        else if (rule != null) type += " (" + rule.expected() + ")";
        else if (hint != null) type += " (" + String.join(", ", hint) + ")";
        return type;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
