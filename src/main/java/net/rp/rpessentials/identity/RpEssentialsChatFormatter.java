package net.rp.rpessentials.identity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.commands.RpChatUi;
import net.rp.rpessentials.config.ChatConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Gestionnaire du formatage des messages de chat.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * VARIABLES DISPONIBLES DANS LES FORMATS DE CHAT ($xxx) :
 *
 *   RÉTROCOMPATIBLES (comportement inchangé) :
 *     $name     — nickname si défini, sinon MC username  (identique à $nick)
 *     $prefix   — préfixe LuckPerms
 *     $suffix   — suffixe LuckPerms
 *
 *   NOUVELLES (opt-in, n'affectent pas les configs existantes) :
 *     $nick      — nickname si défini, sinon MC username  (alias de $name)
 *     $real      — toujours le MC username brut
 *     $nick_real — "Nickname (RealName)" quand ils diffèrent, sinon juste le nom
 *
 * VARIABLES DISPONIBLES DANS LES COMMANDES RP ({xxx}) :
 *
 *   RÉTROCOMPATIBLES :
 *     {player}   — nickname si défini, sinon MC username
 *     {nickname} — idem (join/leave uniquement)
 *
 *   NOUVELLES :
 *     {nick}      — alias de {player}
 *     {real}      — toujours le MC username
 *     {nick_real} — "Nickname (RealName)" quand ils diffèrent
 *
 *   MESSAGES PRIVÉS (nouvelles) :
 *     {target_nick}, {target_real}, {target_nick_real}
 *     {sender_nick}, {sender_real}, {sender_nick_real}
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TOGGLE AUTOMATIQUE :
 *   ChatConfig.SHOW_REAL_NAME_IN_CHAT = true
 *   → ajoute "(RealName)" automatiquement partout sans modifier les formats.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class RpEssentialsChatFormatter {

    private static final Pattern MARKDOWN_BOLD         = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern MARKDOWN_ITALIC        = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern MARKDOWN_UNDERLINE     = Pattern.compile("__(.+?)__");
    private static final Pattern MARKDOWN_STRIKETHROUGH = Pattern.compile("~~(.+?)~~");

    // =========================================================================
    // CHAT PRINCIPAL
    // =========================================================================

    /**
     * Formate un message de chat complet.
     * @param isGlobal true = format global, false = format proximité
     */
    public static Component formatChatMessage(ServerPlayer sender, String message, boolean isGlobal) {
        if (!safeGetBool(ChatConfig.ENABLE_CHAT_FORMAT)) {
            return Component.literal("<" + sender.getName().getString() + "> " + message);
        }

        String timestamp  = buildTimestamp();
        String body       = safeGetBool(ChatConfig.MARKDOWN_ENABLED) ? applyMarkdown(message) : message;
        String color      = getColorCode(safeGetString(ChatConfig.CHAT_MESSAGE_COLOR, "WHITE"));
        String finalBody  = color + body;

        // Pre-calcul des variantes de nom — utilisees dans playerNameFormat ET chatMessageFormat
        String nick     = NicknameManager.getNickname(sender.getUUID());
        String real     = sender.getName().getString();
        String display  = nick != null ? nick : real;
        String nickReal = buildNickRealStr(nick, real);
        String prefix   = nullToEmpty(RpEssentials.getPlayerPrefix(sender));
        String suffix   = nullToEmpty(RpEssentials.getPlayerSuffix(sender));

        // Nom formate avec les variables de playerNameFormat
        String playerName = buildFormattedNameFromParts(
                safeGetString(ChatConfig.PLAYER_NAME_FORMAT, "$name"),
                display, real, nickReal, prefix, suffix, nick);

        String fmt;
        try {
            fmt = isGlobal ? ChatConfig.GLOBAL_CHAT_FORMAT.get() : ChatConfig.PROXIMITY_CHAT_FORMAT.get();
        } catch (IllegalStateException e) {
            fmt = safeGetString(ChatConfig.CHAT_MESSAGE_FORMAT, "$name: $msg");
        }

        // Resolution des variables dans le format de message lui-meme
        // (permet d'utiliser $nick_real, $real, $nick directement dans chatMessageFormat)
        fmt = fmt.replace("$nick_real", nickReal)
                .replace("$real",      real)
                .replace("$nick",      display)
                .replace("$time",      timestamp)
                .replace("$name",      playerName)
                .replace("$msg",       finalBody);

        return ColorHelper.parseColors(ColorHelper.translateAlternateColorCodes(fmt));
    }

    /** Surcharge sans isGlobal (rétrocompatibilité). */
    public static Component formatChatMessage(ServerPlayer sender, String message) {
        return formatChatMessage(sender, message, true);
    }

    // =========================================================================
    // RÉSOLUTION DES PLACEHOLDERS — COMMANDES RP
    // =========================================================================

    /**
     * Remplace tous les placeholders de nom dans un format de commande RP.
     *
     * Rétrocompatibles : {player}
     * Nouvelles        : {nick}, {real}, {nick_real}
     *
     * Si SHOW_REAL_NAME_IN_CHAT est actif et que {player} est utilisé,
     * le realname est automatiquement ajouté.
     */
    public static String resolveRpPlaceholders(String format, ServerPlayer player) {
        String nick     = NicknameManager.getNickname(player.getUUID());
        String realName = player.getName().getString();
        String display  = nick != null ? nick : realName;
        String nickReal = buildNickRealStr(nick, realName);

        format = format.replace("{nick_real}", nickReal);
        format = format.replace("{real}",      realName);
        format = format.replace("{nick}",      display);
        format = format.replace("{player}",    buildPlayerValue(display, realName));
        return format;
    }

    public static String resolveRpPlaceholders(String format, ServerPlayer player, String precomputedNick) {
        String realName = player.getName().getString();
        String display  = precomputedNick != null ? precomputedNick : realName;
        String nickReal = buildNickRealStr(precomputedNick, realName);

        format = format.replace("{nick_real}", nickReal);
        format = format.replace("{real}",      realName);
        format = format.replace("{nick}",      display);
        format = format.replace("{player}",    buildPlayerValue(display, realName));
        return format;
    }

    /**
     * Remplace les placeholders dans les messages join/leave.
     *
     * Rétrocompatibles : {player} (MC username), {nickname} (nick ou real)
     * Nouvelles        : {nick}, {real}, {nick_real}
     */
    public static String resolveJoinLeavePlaceholders(String format, ServerPlayer player) {
        if (format == null || "none".equalsIgnoreCase(format)) return format;

        String nick     = NicknameManager.getNickname(player.getUUID());
        String realName = player.getName().getString();
        String display  = nick != null ? nick : realName;
        String nickReal = buildNickRealStr(nick, realName);

        // Nouvelles variables en premier
        format = format.replace("{nick_real}", nickReal);
        format = format.replace("{real}",      realName);
        format = format.replace("{nick}",      display);

        // Rétrocompatibles
        format = format.replace("{player}",   realName);   // {player} = MC username dans join/leave
        format = format.replace("{nickname}", display);     // {nickname} = nick ou real

        return format;
    }

    // =========================================================================
    // HELPERS PUBLICS
    // =========================================================================

    /**
     * Retourne le nom d'affichage pour les commandes RP,
     * en appliquant showRealNameInChat si actif.
     */
    public static String getDisplayForRp(ServerPlayer player) {
        String nick     = NicknameManager.getNickname(player.getUUID());
        String realName = player.getName().getString();
        String display  = nick != null ? nick : realName;
        return buildPlayerValue(display, realName);
    }

    /**
     * Construit "Nickname (RealName)" si différents, sinon le nom effectif.
     * Méthode publique pour réutilisation (MP, inspect, etc.).
     */
    public static String buildNickReal(ServerPlayer player) {
        String nick     = NicknameManager.getNickname(player.getUUID());
        String realName = player.getName().getString();
        return buildNickRealStr(nick, realName);
    }

    // =========================================================================
    // PRIVATE — CONSTRUCTION DU NOM
    // =========================================================================

    /**
     * Construit la partie "nom" a partir des pieces deja calculees.
     * Appele depuis formatChatMessage qui a deja les valeurs pour eviter un double appel
     * a NicknameManager et LuckPerms.
     */
    private static String buildFormattedNameFromParts(String playerNameFmt,
                                                      String display, String real, String nickReal,
                                                      String prefix, String suffix, String nick) {

        String fmt = playerNameFmt;

        // Nouvelles variables en premier
        fmt = fmt.replace("$nick_real", nickReal);
        fmt = fmt.replace("$real",      real);
        fmt = fmt.replace("$nick",      display);

        // Retrocompatibles
        fmt = fmt.replace("$name",   display);
        fmt = fmt.replace("$prefix", prefix);
        fmt = fmt.replace("$suffix", suffix);

        // showRealNameInChat : auto-ajout si le format n'utilise pas encore les nouvelles vars
        if (isShowRealEnabled() && nick != null && !nick.equals(real)) {
            boolean alreadyShows = playerNameFmt.contains("$real")
                    || playerNameFmt.contains("$nick_real");
            if (!alreadyShows) {
                fmt = fmt + " §8(" + real + ")";
            }
        }

        return fmt.replaceAll("\\s+", " ").trim() + "§r";
    }

    /** Valeur de {player} pour les commandes RP avec showRealName. */
    private static String buildPlayerValue(String display, String realName) {
        if (isShowRealEnabled() && !display.equals(realName)) {
            return display + " §8(" + realName + ")§r";
        }
        return display;
    }

    /** "Nickname (RealName)" si différents, sinon le nom effectif. */
    private static String buildNickRealStr(String nick, String realName) {
        if (nick != null && !nick.equals(realName)) {
            return nick + " §8(" + realName + ")§r";
        }
        return nick != null ? nick : realName;
    }

    // =========================================================================
    // PRIVATE — UTILITAIRES
    // =========================================================================

    private static boolean isShowRealEnabled() {
        try {
            return ChatConfig.SHOW_REAL_NAME_IN_CHAT != null && ChatConfig.SHOW_REAL_NAME_IN_CHAT.get();
        } catch (IllegalStateException e) { return false; }
    }

    private static String buildTimestamp() {
        if (!safeGetBool(ChatConfig.ENABLE_TIMESTAMP)) return "";
        try {
            return new SimpleDateFormat(ChatConfig.TIMESTAMP_FORMAT.get()).format(new Date());
        } catch (Exception e) { return ""; }
    }

    private static boolean safeGetBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v) {
        try { return v != null && v.get(); }
        catch (IllegalStateException e) { return false; }
    }

    private static String safeGetString(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> v, String def) {
        try { return v != null ? v.get() : def; }
        catch (IllegalStateException e) { return def; }
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }

    // =========================================================================
    // MARKDOWN
    // =========================================================================

    private static String applyMarkdown(String message) {
        message = MARKDOWN_BOLD.matcher(message).replaceAll("§l$1§r");
        message = MARKDOWN_ITALIC.matcher(message).replaceAll("§o$1§r");
        message = MARKDOWN_UNDERLINE.matcher(message).replaceAll("§n$1§r");
        message = MARKDOWN_STRIKETHROUGH.matcher(message).replaceAll("§m$1§r");
        return message;
    }

    private static String getColorCode(String name) {
        return switch (name.toUpperCase()) {
            case "AQUA"         -> "§b";
            case "RED"          -> "§c";
            case "LIGHT_PURPLE" -> "§d";
            case "YELLOW"       -> "§e";
            case "WHITE"        -> "§f";
            case "BLACK"        -> "§0";
            case "GOLD"         -> "§6";
            case "GRAY"         -> "§7";
            case "BLUE"         -> "§9";
            case "GREEN"        -> "§a";
            case "DARK_GRAY"    -> "§8";
            case "DARK_AQUA"    -> "§3";
            case "DARK_RED"     -> "§4";
            case "DARK_PURPLE"  -> "§5";
            case "DARK_GREEN"   -> "§2";
            case "DARK_BLUE"    -> "§1";
            default             -> "§f";
        };
    }

    // =========================================================================
    // /colors
    // =========================================================================
    public static Component getColorsHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append(RpChatUi.header("Available Colors"));

        String[][] colors = {
                {"§0","0","BLACK"},     {"§1","1","DARK_BLUE"},
                {"§2","2","DARK_GREEN"},{"§3","3","DARK_AQUA"},
                {"§4","4","DARK_RED"},  {"§5","5","DARK_PURPLE"},
                {"§6","6","GOLD"},      {"§7","7","GRAY"},
                {"§8","8","DARK_GRAY"}, {"§9","9","BLUE"},
                {"§a","a","GREEN"},     {"§b","b","AQUA"},
                {"§c","c","RED"},       {"§d","d","LIGHT_PURPLE"},
                {"§e","e","YELLOW"},    {"§f","f","WHITE"}
        };
        for (String[] c : colors)
            sb.append(c[0]).append("███ §7").append(c[2]).append(" §8(&").append(c[1]).append(")\n");

        sb.append(RpChatUi.section("Formatting"));
        sb.append("§lBold§r §7(&l)   §oItalic§r §7(&o)   §nUnderline§r §7(&n)\n");
        sb.append("§mStrikethrough§r §7(&m)   §kObfuscated§r §7(&k)   §rReset §7(&r)\n");

        sb.append(RpChatUi.section("Chat Variables"));
        sb.append("§f$name/$nick §8nick or username\n");
        sb.append("§f$real §8always MC username\n");
        sb.append("§f$nick_real §8Nick (RealName)\n");

        sb.append(RpChatUi.section("RP Command Variables"));
        sb.append("§f{player}/{nick} §8nick or username\n");
        sb.append("§f{real} §8always MC name\n");
        sb.append("§f{nick_real} §8Nick (RealName)\n");

        sb.append(RpChatUi.LINE);
        return Component.literal(sb.toString());
    }
}