package net.rp.rpessentials.client.gui;

/**
 * Small layout helpers so that rows of buttons always fit the space they are given,
 * whatever the window size or the number of buttons.
 */
public final class UiLayout {

    /** Standard space between two buttons. */
    public static final int GAP = 4;
    /** Space between tabs. */
    public static final int TAB_GAP = 2;
    /** Smallest width a button is shrunk to. */
    public static final int MIN_W = 16;

    private UiLayout() {}

    /** Width of each of {@code n} equal cells filling {@code width}, with {@code gap} between them. */
    /**
     * The text, cut with "…" when it is wider than {@code maxWidth} (formatting codes kept and not counted).
     */
    public static String fit(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) return text;
        int room = Math.max(0, maxWidth - font.width("…"));
        return font.plainSubstrByWidth(text, room) + "…";
    }

    public static int cellW(int width, int n, int gap) {
        if (n <= 0) return width;
        return Math.max(MIN_W, (width - gap * (n - 1)) / n);
    }

    /** X of cell {@code i} for {@link #cellW}; the last cell is aligned on the right edge. */
    public static int cellX(int x, int width, int n, int gap, int i) {
        int w = cellW(width, n, gap);
        if (i == n - 1 && n > 1) return x + width - w;
        return x + i * (w + gap);
    }

    /**
     * Places buttons of preferred widths left to right in {@code width}.
     * When they do not fit, every width is reduced in proportion (never under {@link #MIN_W}).
     * @return {x0, w0, x1, w1, ...}
     */
    public static int[] row(int x, int width, int gap, int... preferred) {
        int n = preferred.length;
        int total = gap * Math.max(0, n - 1);
        for (int p : preferred) total += p;
        double scale = total <= width ? 1.0 : Math.max(0, width - gap * (n - 1)) / (double) (total - gap * (n - 1));
        int[] out = new int[n * 2];
        int cx = x;
        for (int i = 0; i < n; i++) {
            int w = Math.max(MIN_W, (int) Math.floor(preferred[i] * scale));
            out[i * 2] = cx;
            out[i * 2 + 1] = w;
            cx += w + gap;
        }
        return out;
    }
}
