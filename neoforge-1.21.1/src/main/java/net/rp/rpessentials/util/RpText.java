package net.rp.rpessentials.util;

import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Formatted free text (character sheets): {@code &} / {@code §} color and format codes, hex colors and
 * gradients (see {@link ColorHelper}), plus markdown: {@code **bold**}, {@code *italic*},
 * {@code __underline__}, {@code ~~strikethrough~~}.
 * <p>
 * Unlike a plain replacement, closing a markdown span goes back to the color and formats that were
 * active before it, so {@code &cRed **bold** still red} stays red.
 */
public final class RpText {

    private RpText() {}

    public static Component toComponent(String raw) {
        return ColorHelper.parseColors(markdownToCodes(raw == null ? "" : raw));
    }

    /** Markdown spans turned into § codes; the existing codes are kept. */
    public static String markdownToCodes(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 16);
        String color = "";
        Set<Character> codes = new LinkedHashSet<>();   // formats set with & codes
        Set<Character> md = new LinkedHashSet<>();      // formats opened by markdown
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            // Existing color / format codes: & or §, hex colors &#RRGGBB.
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                char k = Character.toLowerCase(raw.charAt(i + 1));
                if (k == '#' && raw.length() >= i + 8 && raw.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    color = "§#" + raw.substring(i + 2, i + 8);
                    codes.clear();
                    out.append(color);
                    for (char m : md) out.append('§').append(m);
                    i += 8;
                    continue;
                }
                if ("0123456789abcdef".indexOf(k) >= 0) {
                    color = "§" + k;
                    codes.clear();
                    out.append(color);
                    for (char m : md) out.append('§').append(m);
                    i += 2;
                    continue;
                }
                if ("klmno".indexOf(k) >= 0) {
                    codes.add(k);
                    out.append('§').append(k);
                    i += 2;
                    continue;
                }
                if (k == 'r') {
                    color = "";
                    codes.clear();
                    md.clear();
                    out.append("§r");
                    i += 2;
                    continue;
                }
            }
            // Markdown tokens, only when the span is closed later on the same line.
            String token = raw.startsWith("**", i) ? "**" : raw.startsWith("__", i) ? "__"
                    : raw.startsWith("~~", i) ? "~~" : c == '*' ? "*" : null;
            if (token != null) {
                char format = switch (token) { case "**" -> 'l'; case "__" -> 'n'; case "~~" -> 'm'; default -> 'o'; };
                if (md.contains(format)) {
                    md.remove(format);
                    out.append("§r").append(color);
                    for (char m : codes) out.append('§').append(m);
                    for (char m : md) out.append('§').append(m);
                    i += token.length();
                    continue;
                }
                if (closedLater(raw, i + token.length(), token)) {
                    md.add(format);
                    out.append('§').append(format);
                    i += token.length();
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean closedLater(String raw, int from, String token) {
        int end = raw.indexOf('\n', from);
        String rest = end < 0 ? raw.substring(from) : raw.substring(from, end);
        if (rest.isEmpty() || Character.isWhitespace(rest.charAt(0))) return false;
        int at = rest.indexOf(token);
        // A single * must not match the first star of a ** token.
        while (at >= 0 && token.equals("*") && rest.startsWith("**", at)) at = rest.indexOf('*', at + 2);
        return at > 0;
    }
}
