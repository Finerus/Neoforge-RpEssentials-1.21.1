package net.rp.rpessentials.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper pour parser les codes couleur Minecraft (§ et &)
 */
public class ColorHelper {

    private static final Pattern AMP_CODE = Pattern.compile("&(?=[0-9a-fk-orA-FK-OR#])");
    private static final Pattern STRIP_CODE = Pattern.compile("(?i)§(?:#[0-9a-f]{6}|[0-9a-fk-or])");
    private static final Pattern ANY_CODE = Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)");
    private static final Pattern GRADIENT = Pattern.compile("<grad:#([0-9a-fA-F]{6}):#([0-9a-fA-F]{6})>(.*?)</grad>");

    /**
     * Parse un texte avec des codes couleur et retourne un Component
     * Support § et & comme préfixes
     */
    public static Component parseColors(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return parseSection(AMP_CODE.matcher(expandGradients(text)).replaceAll("§"));
    }

    public static Component parseSection(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.indexOf('§') < 0) return Component.literal(text);
        MutableComponent result = Component.empty();
        for (TextSegment segment : parseSegments(text)) {
            result.append(Component.literal(segment.text).setStyle(segment.style));
        }
        return result;
    }

    public static String stripAll(String text) {
        if (text == null) return "";
        return ANY_CODE.matcher(text).replaceAll("");
    }

    /**
     * Parse le texte en segments avec leur style
     */
    private static List<TextSegment> parseSegments(String text) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        Style currentStyle = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                if (currentText.length() > 0) {
                    segments.add(new TextSegment(currentText.toString(), currentStyle));
                    currentText = new StringBuilder();
                }
                char code = text.charAt(i + 1);
                if (code == '#' && i + 7 < text.length() + 0 && text.length() >= i + 8
                        && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    currentStyle = currentStyle.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
                    i += 7;
                } else {
                    currentStyle = applyFormatting(currentStyle, code);
                    i++;
                }
            } else {
                currentText.append(text.charAt(i));
            }
        }

        if (currentText.length() > 0) {
            segments.add(new TextSegment(currentText.toString(), currentStyle));
        }
        return segments;
    }

    private static String expandGradients(String text) {
        if (text.indexOf("<grad:") < 0) return text;
        Matcher m = GRADIENT.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int a = Integer.parseInt(m.group(1), 16);
            int b = Integer.parseInt(m.group(2), 16);
            String body = m.group(3);
            int n = Math.max(1, body.length() - 1);
            StringBuilder g = new StringBuilder();
            for (int i = 0; i < body.length(); i++) {
                float t = (float) i / n;
                int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
                int gr = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
                int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
                g.append(String.format("§#%02x%02x%02x", r, gr, bl)).append(body.charAt(i));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(g.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }


    /**
     * Applique un code de formatage à un style
     */
    private static Style applyFormatting(Style style, char rawCode) {
        char code = Character.toLowerCase(rawCode);
        ChatFormatting color = switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            default -> null;
        };
        if (color != null) return Style.EMPTY.withColor(color);
        return switch (code) {
            case 'k' -> style.withObfuscated(true);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderlined(true);
            case 'o' -> style.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> style;
        };
    }

    /**
     * Retire tous les codes couleur d'un texte
     */
    public static String stripColors(String text) {
        if (text == null) return "";
        return STRIP_CODE.matcher(text).replaceAll("");
    }

    /**
     * Convertit & en § (pour compatibilité)
     */
    public static String translateAlternateColorCodes(String text) {
        if (text == null) return "";
        return AMP_CODE.matcher(expandGradients(text)).replaceAll("§");
    }

    /**
     * Classe interne pour représenter un segment de texte avec son style
     */
    private static class TextSegment {
        final String text;
        final Style style;

        TextSegment(String text, Style style) {
            this.text = text;
            this.style = style;
        }
    }
}