package net.rp.rpessentials.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSink;

/**
 * Same walk through a formatted string as vanilla {@code StringDecomposer.iterateFormatted}, plus the
 * {@code §#RRGGBB} hex color code. Like a vanilla color code, a hex color resets bold, italic... and
 * keeps click / hover events.
 */
public final class HexColorText {

    private static final char SECTION = '§';
    private static final int REPLACEMENT = 0xFFFD;

    private HexColorText() {}

    public static boolean iterateFormatted(String text, int skip, Style current, Style reset, FormattedCharSink sink) {
        int len = text.length();
        Style style = current;
        for (int i = skip; i < len; i++) {
            char c = text.charAt(i);
            if (c == SECTION) {
                if (i + 1 >= len) break;
                char code = text.charAt(i + 1);
                if (code == '#' && i + 8 <= len) {
                    int rgb = hex(text, i + 2);
                    if (rgb >= 0) {
                        style = style.applyLegacyFormat(ChatFormatting.WHITE).withColor(TextColor.fromRgb(rgb));
                        i += 7;
                        continue;
                    }
                }
                ChatFormatting f = ChatFormatting.getByCode(code);
                if (f != null) style = f == ChatFormatting.RESET ? reset : style.applyLegacyFormat(f);
                i++;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, style, REPLACEMENT)) return false;
                    break;
                }
                char low = text.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c, low))) return false;
                    i++;
                } else if (!sink.accept(i, style, REPLACEMENT)) {
                    return false;
                }
            } else if (!sink.accept(i, style, Character.isSurrogate(c) ? REPLACEMENT : c)) {
                return false;
            }
        }
        return true;
    }

    /** The 6 hex digits starting at {@code from} as RGB, -1 when they are not hex digits. */
    private static int hex(String s, int from) {
        int v = 0;
        for (int k = from; k < from + 6; k++) {
            int d = Character.digit(s.charAt(k), 16);
            if (d < 0) return -1;
            v = (v << 4) | d;
        }
        return v;
    }
}
