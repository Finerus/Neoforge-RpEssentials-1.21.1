package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.util.ColorHelper;

/**
 * Color palette shared by the Player Profile (nickname) and the Profession Editor (profession name),
 * so both screens look and behave the same way. The name preview uses the real chat colors
 * (&-codes, hex and gradients typed in the name are kept).
 */
public final class ColorPalette {

    public static final char[] CHARS = { 0, 'f', 'e', '6', 'c', 'a', 'b', '9', 'd', '7', '8', '0', '1', '2', '3', '4', '5' };
    public static final String[] KEYS = {
            "none", "white", "yellow", "gold", "red", "green", "cyan", "blue", "pink", "gray", "dark_gray",
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple"
    };

    private static final int NAMED_W = 58, SWATCH_W = 16, H = 16, ROW_GAP = 2;

    /** Size of the palette for a given width. Small color squares on short windows, named buttons otherwise. */
    public record Dims(int btnW, int btnH, int cols, boolean swatches) {
        public int rows() { return (CHARS.length + cols - 1) / cols; }
        public int height() { return rows() * (btnH + ROW_GAP); }
        public int x(int formX, int i) { return formX + (i % cols) * (btnW + UiLayout.GAP); }
        public int y(int top, int i) { return top + (i / cols) * (btnH + ROW_GAP); }
    }

    private ColorPalette() {}

    public static Dims dims(int formW, int screenH) {
        if (screenH < 320) {
            return new Dims(SWATCH_W, H - 2, Math.max(1, (formW + UiLayout.GAP) / (SWATCH_W + UiLayout.GAP)), true);
        }
        return new Dims(NAMED_W, H, Math.max(1, Math.min(8, (formW + UiLayout.GAP) / (NAMED_W + UiLayout.GAP))), false);
    }

    public static String name(int i) {
        return I18n.get("rpessentials.gui.color." + KEYS[i]);
    }

    /** Label of palette button {@code i}. */
    public static String label(int i, boolean selected, Dims d) {
        if (d.swatches()) return i == 0 ? "§7∅" : "§" + CHARS[i] + "■";
        return (selected ? "§l" : "") + (i == 0 ? "§7" : "§" + CHARS[i]) + name(i);
    }

    /** Gold frame around the selected color. */
    public static void drawSelection(GuiGraphics g, Dims d, int formX, int top, int selected) {
        int x = d.x(formX, selected), y = d.y(top, selected);
        g.fill(x - 1, y - 1, x + d.btnW() + 1, y + d.btnH() + 1, 0xFF_FFD700);
    }

    /** The name as it will look in game with the chosen color. */
    public static Component preview(String name, int colorIndex) {
        return ColorHelper.parseColors(colorIndex == 0 ? name : "&" + CHARS[colorIndex] + name);
    }

    /** Draws the preview right after a label. */
    public static void drawPreview(GuiGraphics g, Font font, String label, String name, int colorIndex, int x, int y) {
        if (name == null || name.isEmpty()) return;
        g.drawString(font, preview(name, colorIndex), x + font.width(label) + 6, y, 0xFFFFFFFF, false);
    }
}
