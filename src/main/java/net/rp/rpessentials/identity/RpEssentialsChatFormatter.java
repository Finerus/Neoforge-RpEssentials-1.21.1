package net.rp.rpessentials.identity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.rp.rpessentials.ColorHelper;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.ChatConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Gestionnaire du formatage des messages de chat
 */
public class RpEssentialsChatFormatter {

    private static final Pattern MARKDOWN_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern MARKDOWN_ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern MARKDOWN_UNDERLINE = Pattern.compile("__(.+?)__");
    private static final Pattern MARKDOWN_STRIKETHROUGH = Pattern.compile("~~(.+?)~~");

    /**
     * Formate un message avec indicateur global/proximité.
     * @param isGlobal true = format global, false = format proximité
     */
    public static Component formatChatMessage(ServerPlayer sender, String message, boolean isGlobal) {
        if (!ChatConfig.ENABLE_CHAT_FORMAT.get()) {
            return Component.literal("<" + sender.getName().getString() + "> " + message);
        }

        String playerName = formatPlayerName(sender);

        String timestamp = "";
        if (ChatConfig.ENABLE_TIMESTAMP.get()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(ChatConfig.TIMESTAMP_FORMAT.get());
                timestamp = sdf.format(new Date());
            } catch (Exception e) {
                timestamp = "";
            }
        }

        String formattedBody = ChatConfig.MARKDOWN_ENABLED.get() ? applyMarkdown(message) : message;
        String messageColor = getColorCode(ChatConfig.CHAT_MESSAGE_COLOR.get());
        String finalMessageBody = messageColor + formattedBody;

        // Feature 1 — sélection du format selon global/proximité
        String chatFormat;
        try {
            chatFormat = isGlobal
                    ? ChatConfig.GLOBAL_CHAT_FORMAT.get()
                    : ChatConfig.PROXIMITY_CHAT_FORMAT.get();
        } catch (IllegalStateException e) {
            chatFormat = ChatConfig.CHAT_MESSAGE_FORMAT.get();
        }

        chatFormat = chatFormat.replace("$time", timestamp);
        chatFormat = chatFormat.replace("$name", playerName);
        chatFormat = chatFormat.replace("$msg",  finalMessageBody);

        return ColorHelper.parseColors(ColorHelper.translateAlternateColorCodes(chatFormat));
    }

    // Garder l'ancienne signature comme delegate pour ne pas casser les appels existants
    public static Component formatChatMessage(ServerPlayer sender, String message) {
        return formatChatMessage(sender, message, true);
    }

    /**
     * Formate le nom d'un joueur et assure la réinitialisation du formatage après
     */
    private static String formatPlayerName(ServerPlayer player) {
        String format = ChatConfig.PLAYER_NAME_FORMAT.get();

        String prefix = RpEssentials.getPlayerPrefix(player);
        if (prefix == null) prefix = "";

        String suffix = RpEssentials.getPlayerSuffix(player);
        if (suffix == null) suffix = "";

        String name = NicknameManager.getDisplayName(player);

        format = format.replace("$prefix", prefix);
        format = format.replace("$name", name);
        format = format.replace("$suffix", suffix);

        // Nettoyage et ajout de §r pour éviter que la couleur du grade ne bave sur le message
        return format.replaceAll("\\s+", " ").trim() + "§r";
    }

    /**
     * Applique le formatage markdown au message
     */
    private static String applyMarkdown(String message) {
        message = MARKDOWN_BOLD.matcher(message).replaceAll("§l$1§r");
        message = MARKDOWN_ITALIC.matcher(message).replaceAll("§o$1§r");
        message = MARKDOWN_UNDERLINE.matcher(message).replaceAll("§n$1§r");
        message = MARKDOWN_STRIKETHROUGH.matcher(message).replaceAll("§m$1§r");
        return message;
    }

    /**
     * Convertit un nom de couleur en code couleur Minecraft
     */
    private static String getColorCode(String colorName) {
        return switch (colorName.toUpperCase()) {
            case "AQUA" -> "§b";
            case "RED" -> "§c";
            case "LIGHT_PURPLE" -> "§d";
            case "YELLOW" -> "§e";
            case "WHITE" -> "§f";
            case "BLACK" -> "§0";
            case "GOLD" -> "§6";
            case "GRAY" -> "§7";
            case "BLUE" -> "§9";
            case "GREEN" -> "§a";
            case "DARK_GRAY" -> "§8";
            case "DARK_AQUA" -> "§3";
            case "DARK_RED" -> "§4";
            case "DARK_PURPLE" -> "§5";
            case "DARK_GREEN" -> "§2";
            case "DARK_BLUE" -> "§1";
            default -> "§f";
        };
    }

    /**
     * Génère le message d'aide pour la commande /colors
     */
    public static Component getColorsHelp() {
        StringBuilder help = new StringBuilder();
        help.append("§6╔═══════════════════════════════╗\n");
        help.append("§6║  §e§lAVAILABLE COLORS§r          §6║\n");
        help.append("§6╠═══════════════════════════════╣\n");

        String[][] colors = {
                {"§0", "BLACK", "§00"}, {"§1", "DARK_BLUE", "§11"},
                {"§2", "DARK_GREEN", "§22"}, {"§3", "DARK_AQUA", "§33"},
                {"§4", "DARK_RED", "§44"}, {"§5", "DARK_PURPLE", "§55"},
                {"§6", "GOLD", "§66"}, {"§7", "GRAY", "§77"},
                {"§8", "DARK_GRAY", "§88"}, {"§9", "BLUE", "§99"},
                {"§a", "GREEN", "§aa"}, {"§b", "AQUA", "§bb"},
                {"§c", "RED", "§cc"}, {"§d", "LIGHT_PURPLE", "§dd"},
                {"§e", "YELLOW", "§ee"}, {"§f", "WHITE", "§ff"}
        };

        for (String[] color : colors) {
            help.append(String.format("§6║ %s %-15s %s §6║\n", color[0] + "███", color[1], color[2]));
        }

        help.append("§6║                               §6║\n");
        help.append("§6║ §7Formatting Codes:           §6║\n");
        help.append("§6║ §l§lBold§r §7(§l)                 §6║\n");
        help.append("§6║ §o§oItalic§r §7(§o)               §6║\n");
        help.append("§6║ §n§nUnderline§r §7(§n)           §6║\n");
        help.append("§6║ §m§mStrikethrough§r §7(§m)        §6║\n");
        help.append("§6║ §k§kObfuscated§r §7(§k)          §6║\n");
        help.append("§6║ §r§rReset§r §7(§r)                §6║\n");
        help.append("§6╚═══════════════════════════════╝");

        return Component.literal(help.toString());
    }
}