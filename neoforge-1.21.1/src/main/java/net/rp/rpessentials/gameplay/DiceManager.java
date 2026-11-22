package net.rp.rpessentials.gameplay;

import net.rp.rpessentials.permission.RpPerm;
import net.rp.rpessentials.permission.RpPerms;

import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.api.event.DiceRollEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.config.RpConfig;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;

import java.util.*;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.util.RpCooldownManager;

/**
 * Gestionnaire des lancers de dé côté serveur.
 * Les types de dés sont configurables dans rpessentials-rp.toml.
 */
public class DiceManager {

    // =========================================================================
    // DICE TYPES
    // =========================================================================
    public record DiceType(String name, int maxValue, List<String> customFaces) {

        /** Retourne true si ce dé a des faces personnalisées (ex: Pile/Face) */
        public boolean hasCustomFaces() {
            return customFaces != null && !customFaces.isEmpty();
        }

        /** Effectue un lancer et retourne le résultat sous forme de String */
        public String roll(Random random) {
            if (hasCustomFaces()) {
                return customFaces.get(random.nextInt(customFaces.size()));
            }
            return String.valueOf(random.nextInt(maxValue) + 1);
        }
    }

    private static final Random RANDOM = new Random();

    // =========================================================================
    // PARSING CONFIG
    // =========================================================================
    private static volatile List<? extends String> cachedSource = null;
    private static volatile List<DiceType> cachedDice = List.of();

    /** Dice from the config, parsed once and parsed again only when the config list changes. */
    public static List<DiceType> getAvailableDice() {
        List<? extends String> source;
        if (Cfg.ready(RpConfig.SPEC)) {
            source = RpConfig.DICE_TYPES.get();
        } else {
            return List.of();
        }
        if (source != cachedSource) {
            List<DiceType> result = new ArrayList<>();
            for (String entry : source) {
                DiceType type = parseDiceType(entry);
                if (type != null) result.add(type);
            }
            cachedDice = List.copyOf(result);
            cachedSource = source;
        }
        return cachedDice;
    }

    public static void clearCache() {
        cachedSource = null;
    }

    private static DiceType parseDiceType(String entry) {
        if (!entry.contains(";")) return null;
        String[] parts = entry.split(";", 2);
        String name = parts[0].trim();
        String value = parts[1].trim();
        if (name.isEmpty()) return null;

        if (value.contains(",")) {
            List<String> faces = Arrays.stream(value.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).toList();
            if (faces.size() < 2) {
                RpEssentials.LOGGER.warn("[DiceManager] Invalid dice entry: {}", entry);
                return null;
            }
            return new DiceType(name, 0, faces);
        }
        try {
            int max = Integer.parseInt(value);
            if (max < 2 || max > 100000) {
                RpEssentials.LOGGER.warn("[DiceManager] Invalid dice entry: {}", entry);
                return null;
            }
            return new DiceType(name, max, null);
        } catch (NumberFormatException e) {
            RpEssentials.LOGGER.warn("[DiceManager] Invalid dice entry: {}", entry);
            return null;
        }
    }

    public static DiceType getDiceByName(String name) {
        return getAvailableDice().stream()
                .filter(d -> d.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    // =========================================================================
    // ROLL
    // =========================================================================
    /**
     * Effectue un lancer et diffuse le résultat aux joueurs proches.
     * @param player  le joueur qui lance
     * @param diceName le nom du type de dé
     * @return true si le lancer a réussi, false si le type est introuvable
     */
    public static boolean roll(ServerPlayer player, String diceName) {
        return roll(player, diceName, false);
    }

    /**
     * Same as {@link #roll(ServerPlayer, String)}, with the option to skip the cooldown.
     * Posts {@link DiceRollEvent.Pre} (cancellable, result can be replaced) and {@link DiceRollEvent.Post}.
     */
    public static boolean roll(ServerPlayer player, String diceName, boolean ignoreCooldown) {
        return rollInput(player, diceName, ignoreCooldown) != Status.UNKNOWN;
    }

    // =========================================================================
    // EXPRESSIONS: "d20", "2d6+3", "adv d20", "dis d20", "secret 2d6", "<configured dice>+2"
    // =========================================================================

    public enum Mode { NORMAL, ADVANTAGE, DISADVANTAGE }

    public enum Status { OK, UNKNOWN, EXPRESSIONS_DISABLED, SECRET_DISABLED, TOO_MANY, COOLDOWN, CANCELLED }

    /**
     * What to roll, parsed from the player's input.
     * @param dice       configured dice, or a dice built from the expression (d20 -> maxValue 20)
     * @param expression text shown as the dice name (for example "2d6+3")
     */
    public record Request(DiceType dice, int count, int modifier, Mode mode, boolean secret, String expression) {}

    /**
     * A roll and its details.
     * @param rolls     kept values (numeric dice), empty for custom faces
     * @param discarded the value set aside by advantage / disadvantage, empty otherwise
     * @param result    what is shown: the total, or the face of a custom dice
     */
    public record Roll(Request request, List<Integer> rolls, List<Integer> discarded, String result) {

        public boolean isNumeric() { return !request.dice().hasCustomFaces(); }

        /** Total of the roll (modifier included), or null for a custom face. */
        public Integer total() {
            if (!isNumeric()) return null;
            int t = request.modifier();
            for (int r : rolls) t += r;
            return t;
        }

        /** True when the roll has details worth showing (several dice, modifier, advantage). */
        public boolean hasDetail() {
            return isNumeric() && (rolls.size() > 1 || request.modifier() != 0 || !discarded.isEmpty());
        }
    }

    private static final java.util.regex.Pattern EXPR = java.util.regex.Pattern.compile("^(\\d*)d(\\d+)([+-]\\d+)?$");
    private static final java.util.regex.Pattern NAMED = java.util.regex.Pattern.compile("^(.+?)([+-]\\d+)?$");

    private static int intOpt(net.neoforged.neoforge.common.ModConfigSpec.IntValue v, int def) {
        return Cfg.get(v, def);
    }

    private static boolean boolOpt(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v, boolean def) {
        return Cfg.get(v, def);
    }

    /** Parse result: the request, or why it is refused. */
    public record Parsed(Request request, Status status) {}

    public static Parsed parse(String input) {
        boolean secret = false;
        Mode mode = Mode.NORMAL;
        StringBuilder spec = new StringBuilder();
        for (String tok : (input == null ? "" : input.trim()).split("\\s+")) {
            String t = tok.toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            switch (t) {
                case "secret" -> secret = true;
                case "adv", "advantage", "avantage" -> mode = Mode.ADVANTAGE;
                case "dis", "disadvantage", "desavantage", "désavantage" -> mode = Mode.DISADVANTAGE;
                default -> spec.append(tok);
            }
        }
        if (secret && !boolOpt(RpConfig.DICE_ALLOW_SECRET, true)) return new Parsed(null, Status.SECRET_DISABLED);

        String s = spec.toString();
        if (s.isEmpty()) {
            List<DiceType> all = getAvailableDice();
            if (all.isEmpty()) return new Parsed(null, Status.UNKNOWN);
            s = all.get(0).name();
        }

        // A configured dice name always wins ("d20" configured with custom faces stays custom).
        DiceType named = getDiceByName(s);
        if (named != null) return check(new Request(named, 1, 0, mode, secret, named.name()));

        java.util.regex.Matcher m = EXPR.matcher(s.toLowerCase(Locale.ROOT));
        if (m.matches()) {
            if (!boolOpt(RpConfig.DICE_ALLOW_EXPRESSIONS, true)) return new Parsed(null, Status.EXPRESSIONS_DISABLED);
            int count, sides, mod;
            try {
                count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
                sides = Integer.parseInt(m.group(2));
                mod = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            } catch (NumberFormatException e) {
                return new Parsed(null, Status.TOO_MANY);
            }
            if (count < 1 || sides < 2) return new Parsed(null, Status.UNKNOWN);
            if (count > intOpt(RpConfig.DICE_MAX_COUNT, 20) || sides > intOpt(RpConfig.DICE_MAX_SIDES, 1000)
                    || Math.abs(mod) > 100000) return new Parsed(null, Status.TOO_MANY);
            return check(new Request(new DiceType("d" + sides, sides, null), count, mod, mode, secret, s.toLowerCase(Locale.ROOT)));
        }

        // "<configured dice>+3"
        java.util.regex.Matcher n = NAMED.matcher(s);
        if (n.matches() && n.group(2) != null) {
            DiceType d = getDiceByName(n.group(1));
            if (d != null && !d.hasCustomFaces()) {
                if (!boolOpt(RpConfig.DICE_ALLOW_EXPRESSIONS, true)) return new Parsed(null, Status.EXPRESSIONS_DISABLED);
                int mod;
                try { mod = Integer.parseInt(n.group(2)); } catch (NumberFormatException e) { return new Parsed(null, Status.TOO_MANY); }
                return check(new Request(d, 1, mod, mode, secret, d.name() + n.group(2)));
            }
        }
        return new Parsed(null, Status.UNKNOWN);
    }

    /** Advantage only makes sense for a single numeric dice. */
    private static Parsed check(Request r) {
        if (r.mode() != Mode.NORMAL && (r.dice().hasCustomFaces() || r.count() != 1)) {
            r = new Request(r.dice(), r.count(), r.modifier(), Mode.NORMAL, r.secret(), r.expression());
        }
        return new Parsed(r, Status.OK);
    }

    /** Rolls a request, without message, cooldown or event. */
    public static Roll execute(Request r) {
        if (r.dice().hasCustomFaces()) return new Roll(r, List.of(), List.of(), r.dice().roll(RANDOM));
        int sides = r.dice().maxValue();
        List<Integer> rolls = new ArrayList<>();
        List<Integer> discarded = new ArrayList<>();
        if (r.mode() != Mode.NORMAL) {
            int a = RANDOM.nextInt(sides) + 1, b = RANDOM.nextInt(sides) + 1;
            boolean keepA = r.mode() == Mode.ADVANTAGE ? a >= b : a <= b;
            rolls.add(keepA ? a : b);
            discarded.add(keepA ? b : a);
        } else {
            for (int i = 0; i < r.count(); i++) rolls.add(RANDOM.nextInt(sides) + 1);
        }
        int total = r.modifier();
        for (int v : rolls) total += v;
        return new Roll(r, List.copyOf(rolls), List.copyOf(discarded), String.valueOf(total));
    }

    /** Details of a roll in the viewer's language ("4 + 6 + 3", "advantage: 17 | 8"). Empty if none. */
    public static String detail(ServerPlayer viewer, Roll roll) {
        if (!roll.hasDetail()) return "";
        StringBuilder sb = new StringBuilder();
        Request r = roll.request();
        if (!roll.discarded().isEmpty()) {
            sb.append(ServerLang.get(viewer, r.mode() == Mode.ADVANTAGE ? MessagesConfig.DICE_ADVANTAGE : MessagesConfig.DICE_DISADVANTAGE))
                    .append(": ").append(roll.rolls().get(0)).append(" | §m").append(roll.discarded().get(0)).append("§r§8");
        } else {
            for (int i = 0; i < roll.rolls().size(); i++) {
                if (i > 0) sb.append(" + ");
                sb.append(roll.rolls().get(i));
            }
        }
        if (r.modifier() != 0) sb.append(r.modifier() > 0 ? " + " : " - ").append(Math.abs(r.modifier()));
        return ServerLang.get(viewer, MessagesConfig.DICE_ROLL_DETAIL, "detail", sb.toString());
    }

    /**
     * Full roll from a player's input, with cooldown, events and messages.
     * Posts {@link DiceRollEvent.Pre} (cancellable, result can be replaced) and {@link DiceRollEvent.Post}.
     */
    public static Status rollInput(ServerPlayer player, String input, boolean ignoreCooldown) {
        Parsed parsed = parse(input);
        if (parsed.status() != Status.OK) return parsed.status();
        Request req = parsed.request();

        if (!ignoreCooldown && RpCooldownManager.isOnCooldown(player.getUUID(), "dice")) {
            long remaining = RpCooldownManager.getRemainingSeconds(player.getUUID(), "dice");
            player.displayClientMessage(ColorHelper.parseColors(
                    ServerLang.get(player, MessagesConfig.RP_COOLDOWN_MESSAGE,
                            "command", "roll", "seconds", String.valueOf(remaining))), true);
            return Status.COOLDOWN;
        }

        Roll roll = execute(req);
        DiceRollEvent.Pre pre = new DiceRollEvent.Pre(player, req.dice(), roll.result(), roll);
        if (NeoForge.EVENT_BUS.post(pre).isCanceled()) return Status.CANCELLED;
        if (!ignoreCooldown) RpCooldownManager.setCooldown(player.getUUID(), "dice");

        String result = pre.getResult();
        boolean replaced = !result.equals(roll.result());
        String playerName = NicknameManager.getDisplayName(player);
        String shownDice = req.expression();

        int distance;
        if (Cfg.ready(RpConfig.SPEC)) { distance = RpConfig.DICE_ROLL_DISTANCE.get(); } else { distance = 32; }
        boolean global = distance < 0;
        double distSq = (double) distance * distance;

        List<ServerPlayer> audience = new ArrayList<>();
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            String shown = result + (replaced ? "" : detail(p, roll));
            boolean self = p.getUUID().equals(player.getUUID());
            if (req.secret()) {
                if (self || RpPerms.has(p, RpPerm.NOTIFY_DICE)) {
                    p.sendSystemMessage(ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.DICE_ROLL_SECRET_FORMAT,
                            "player", playerName, "dice", shownDice, "result", shown)));
                    audience.add(p);
                }
                continue;
            }
            if (global || self || (p.level() == player.level() && p.distanceToSqr(player) <= distSq)) {
                p.sendSystemMessage(ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.DICE_ROLL_FORMAT,
                        "player", playerName, "dice", shownDice, "result", shown)));
                audience.add(p);
            } else if (RpPerms.has(p, RpPerm.NOTIFY_DICE)) {
                p.sendSystemMessage(ColorHelper.parseColors(ServerLang.get(p, MessagesConfig.DICE_ROLL_SPY_FORMAT,
                        "player", playerName, "dice", shownDice, "result", shown)));
            }
        }

        RpEssentials.LOGGER.info("[DICE] {} rolled {}{} and got {} {}", playerName, shownDice,
                req.secret() ? " (secret)" : "", result, roll.rolls());
        NeoForge.EVENT_BUS.post(new DiceRollEvent.Post(player, req.dice(), result, audience, roll));
        return Status.OK;
    }

    /** Rolls a dice or an expression without message, cooldown or event. Returns null when it cannot be rolled. */
    public static String rollSilently(String diceName) {
        Roll r = rollDetailed(diceName);
        return r == null ? null : r.result();
    }

    /** Same as {@link #rollSilently}, with every detail of the roll. */
    public static Roll rollDetailed(String input) {
        Parsed p = parse(input);
        return p.status() == Status.OK ? execute(p.request()) : null;
    }
}