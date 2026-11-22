package net.rp.rpessentials.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.rp.rpessentials.config.Cfg;
import net.rp.rpessentials.config.ClientConfig;
import net.rp.rpessentials.util.ColorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Color palette shared by the Player Profile (nickname), the Profession Editor (profession name) and
 * the character sheet editor: small color squares, the name of the color in its own color in the
 * tooltip, then the custom colors of the player (customColors in rpessentials-client.toml) and a "+"
 * square to add more.
 * <p>
 * A color is stored as a code: "" (none), "&c" (Minecraft color), "&#RRGGBB" (hex) or
 * "<grad:#RRGGBB:#RRGGBB>" (gradient, closed by "</grad>" after the text, see {@link #apply}).
 */
public final class ColorPalette {

    public static final char[] CHARS = { 0, 'f', 'e', '6', 'c', 'a', 'b', '9', 'd', '7', '8', '0', '1', '2', '3', '4', '5' };
    public static final String[] KEYS = {
            "none", "white", "yellow", "gold", "red", "green", "cyan", "blue", "pink", "gray", "dark_gray",
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple"
    };

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");
    private static final Pattern GRAD = Pattern.compile("(?i)<grad:#([0-9a-f]{6}):#([0-9a-f]{6})>");
    public static final String GRAD_END = "</grad>";
    private static final int SWATCH_W = 16, SWATCH_H = 14, ROW_GAP = 2;

    /**
     * One color of the palette. {@code custom}: added by the player, can be removed.
     * {@code rgb2}: end color of a gradient, -1 for a plain color.
     */
    public record Entry(String code, String name, int rgb, int rgb2, boolean custom) {
        public Entry(String code, String name, int rgb, boolean custom) { this(code, name, rgb, -1, custom); }
        public boolean gradient() { return rgb2 >= 0; }
    }

    /** Gradient code of two colors. */
    public static String gradCode(int from, int to) {
        return String.format(Locale.ROOT, "<grad:#%06X:#%06X>", from & 0xFFFFFF, to & 0xFFFFFF);
    }

    /** Default name of a gradient: "#RRGGBB -> #RRGGBB". */
    public static String gradName(int from, int to) {
        return String.format(Locale.ROOT, "#%06X → #%06X", from & 0xFFFFFF, to & 0xFFFFFF);
    }

    /** Hex colors met in a nickname or a profession but not in the palette: kept for this game session. */
    private static final List<Entry> SESSION = new ArrayList<>();

    /** Size of the palette: color squares, plus one square for "+". */
    public record Dims(int btnW, int btnH, int cols, boolean swatches) {
        public int rows() { return (count() + 1 + cols - 1) / cols; }
        public int height() { return rows() * (btnH + ROW_GAP); }
        public int x(int formX, int i) { return formX + (i % cols) * (btnW + UiLayout.GAP); }
        public int y(int top, int i) { return top + (i / cols) * (btnH + ROW_GAP); }
    }

    private ColorPalette() {}

    public static Dims dims(int formW, int screenH) {
        return new Dims(SWATCH_W, SWATCH_H, Math.max(1, (formW + UiLayout.GAP) / (SWATCH_W + UiLayout.GAP)), true);
    }

    // =========================================================================
    // ENTRIES
    // =========================================================================
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < CHARS.length; i++) {
            if (i == 0) { out.add(new Entry("", name(0), 0x888888, false)); continue; }
            ChatFormatting f = ChatFormatting.getByCode(CHARS[i]);
            Integer rgb = f != null ? f.getColor() : null;
            out.add(new Entry("&" + CHARS[i], name(i), rgb != null ? rgb : 0xFFFFFF, false));
        }
        for (String line : Cfg.get(ClientConfig.CUSTOM_COLORS, List.<String>of())) {
            Entry e = parseCustom(line);
            if (e != null && indexIn(out, e.code()) < 0) out.add(e);
        }
        for (Entry e : SESSION) if (indexIn(out, e.code()) < 0) out.add(e);
        return out;
    }

    public static int count() { return entries().size(); }

    /** "Name;RRGGBB" or "Name;RRGGBB>RRGGBB" (gradient; a leading # is accepted) from the client config. */
    private static Entry parseCustom(String line) {
        String[] p = line.split(";", 2);
        if (p.length < 2) return null;
        String[] g = p[1].trim().replace("#", "").split(">", 2);
        if (g.length == 2) {
            String a = g[0].trim(), b = g[1].trim();
            if (!HEX.matcher(a).matches() || !HEX.matcher(b).matches()) return null;
            int ra = Integer.parseInt(a, 16), rb = Integer.parseInt(b, 16);
            String name = p[0].trim().isEmpty() ? gradName(ra, rb) : p[0].trim();
            return new Entry(gradCode(ra, rb), name, ra, rb, true);
        }
        String hex = g[0].trim();
        if (!HEX.matcher(hex).matches()) return null;
        String name = p[0].trim().isEmpty() ? "#" + hex.toUpperCase(Locale.ROOT) : p[0].trim();
        return new Entry("&#" + hex.toUpperCase(Locale.ROOT), name, Integer.parseInt(hex, 16), true);
    }

    private static int indexIn(List<Entry> list, String code) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).code().equalsIgnoreCase(code)) return i;
        return -1;
    }

    public static Entry get(int i) {
        List<Entry> l = entries();
        return i >= 0 && i < l.size() ? l.get(i) : l.get(0);
    }

    /** Code of color {@code i} ("" for none, "&c", "&#RRGGBB"). */
    public static String code(int i) { return get(i).code(); }

    /**
     * Index of a color code (& or §, Minecraft or hex). An unknown hex color is added for this session,
     * so saving never loses it. Unknown other codes give 0 (none).
     */
    public static int ensure(String rawCode) {
        if (rawCode == null || rawCode.isEmpty()) return 0;
        String code = rawCode.replace('§', '&');
        int i = indexIn(entries(), code);
        if (i >= 0) return i;
        var gm = GRAD.matcher(code);
        if (gm.matches()) {
            int a = Integer.parseInt(gm.group(1), 16), b = Integer.parseInt(gm.group(2), 16);
            String c = gradCode(a, b);
            if (indexIn(entries(), c) < 0) SESSION.add(new Entry(c, gradName(a, b), a, b, false));
            return indexIn(entries(), c);
        }
        if (code.startsWith("&#") && code.length() == 8 && HEX.matcher(code.substring(2)).matches()) {
            String hex = code.substring(2).toUpperCase(Locale.ROOT);
            SESSION.add(new Entry("&#" + hex, "#" + hex, Integer.parseInt(hex, 16), false));
            return indexIn(entries(), "&#" + hex);
        }
        return 0;
    }

    /** Leading color code of a text (§c / &c / §#RRGGBB / &#RRGGBB / <grad:#..:#..>), or "" when there is none. */
    public static String leadingCode(String text) {
        if (text == null) return "";
        var gm = GRAD.matcher(text);
        if (gm.lookingAt()) return text.substring(0, gm.end());
        if (text.length() < 2 || (text.charAt(0) != '§' && text.charAt(0) != '&')) return "";
        char c = Character.toLowerCase(text.charAt(1));
        if (c == '#' && text.length() >= 8 && HEX.matcher(text.substring(2, 8)).matches()) return text.substring(0, 8);
        if ("0123456789abcdef".indexOf(c) >= 0) return text.substring(0, 2);
        return "";
    }

    /** A colored text cut into its palette color and the rest. */
    public record Split(int index, String text) {}

    /**
     * Palette color of a text and the text without it ("&cBob" -> red + "Bob",
     * "<grad:#..:#..>Bob</grad>" -> that gradient + "Bob"). An unknown hex or gradient is added for the session.
     */
    public static Split split(String raw) {
        if (raw == null) return new Split(0, "");
        String lead = leadingCode(raw);
        if (lead.isEmpty()) return new Split(0, raw);
        int idx = ensure(lead);
        if (idx <= 0) return new Split(0, raw);
        String rest = raw.substring(lead.length());
        if (get(idx).gradient() && rest.toLowerCase(Locale.ROOT).endsWith(GRAD_END)) {
            rest = rest.substring(0, rest.length() - GRAD_END.length());
        }
        return new Split(idx, rest);
    }

    /** The text with palette color {@code i} applied ("&c" + text, or gradient + text + "</grad>"). */
    public static String apply(int i, String text) {
        if (i <= 0 || text == null || text.isEmpty()) return text == null ? "" : text;
        Entry e = get(i);
        return e.code() + text + (e.gradient() ? GRAD_END : "");
    }

    // =========================================================================
    // CUSTOM COLORS (client config)
    // =========================================================================
    public static boolean addCustom(String name, String hex) {
        String h = hex == null ? "" : hex.trim().replace("#", "").toUpperCase(Locale.ROOT);
        if (!HEX.matcher(h).matches() || !Cfg.ready(ClientConfig.SPEC)) return false;
        List<String> list = new ArrayList<>(ClientConfig.CUSTOM_COLORS.get());
        list.removeIf(l -> { Entry e = parseCustom(l); return e != null && e.code().equalsIgnoreCase("&#" + h); });
        list.add((name == null || name.isBlank() ? "#" + h : name.trim().replace(";", ",")) + ";" + h);
        ClientConfig.CUSTOM_COLORS.set(list);
        ClientConfig.SPEC.save();
        return true;
    }

    /** Adds a gradient to the custom colors (stored as "Name;RRGGBB>RRGGBB"). */
    public static boolean addCustomGradient(String name, int from, int to) {
        if (!Cfg.ready(ClientConfig.SPEC)) return false;
        String a = String.format(Locale.ROOT, "%06X", from & 0xFFFFFF), b = String.format(Locale.ROOT, "%06X", to & 0xFFFFFF);
        String code = gradCode(from, to);
        List<String> list = new ArrayList<>(ClientConfig.CUSTOM_COLORS.get());
        list.removeIf(l -> { Entry e = parseCustom(l); return e != null && e.code().equalsIgnoreCase(code); });
        list.add((name == null || name.isBlank() ? gradName(from, to) : name.trim().replace(";", ",")) + ";" + a + ">" + b);
        ClientConfig.CUSTOM_COLORS.set(list);
        ClientConfig.SPEC.save();
        return true;
    }

    public static void removeCustom(String code) {
        if (!Cfg.ready(ClientConfig.SPEC)) return;
        List<String> list = new ArrayList<>(ClientConfig.CUSTOM_COLORS.get());
        list.removeIf(l -> { Entry e = parseCustom(l); return e != null && e.code().equalsIgnoreCase(code); });
        ClientConfig.CUSTOM_COLORS.set(list);
        ClientConfig.SPEC.save();
    }

    // =========================================================================
    // DISPLAY
    // =========================================================================
    public static String name(int i) {
        return I18n.get("rpessentials.gui.color." + KEYS[i]);
    }

    /** The square of color {@code i} ("∅" for none). */
    public static Component label(int i, boolean selected, Dims d) {
        Entry e = get(i);
        if (e.code().isEmpty()) return Component.literal("§7∅");
        if (e.gradient()) return ColorHelper.parseColors(e.code() + "■■" + GRAD_END);
        return Component.literal("■").withStyle(s -> s.withColor(e.rgb()));
    }

    /** Tooltip: the name in its own color, then its code. */
    public static Component tooltip(int i) {
        Entry e = get(i);
        MutableComponent name = Component.literal(e.name());
        if (e.gradient()) name = Component.empty().append(ColorHelper.parseColors(e.code() + e.name() + GRAD_END));
        else if (!e.code().isEmpty()) name = name.withStyle(s -> s.withColor(e.rgb()));
        String code = e.code().isEmpty() ? ""
                : e.gradient() ? gradName(e.rgb(), e.rgb2())
                : e.code().startsWith("&#") ? "#" + e.code().substring(2) : e.code();
        if (!code.isEmpty()) name.append(Component.literal(" §8(" + code + ")"));
        if (e.custom()) name.append(Component.literal("\n§7" + I18n.get("rpessentials.gui.color.custom_hint")));
        return name;
    }

    /** Gold frame around the selected color. */
    public static void drawSelection(GuiGraphics g, Dims d, int formX, int top, int selected) {
        int x = d.x(formX, selected), y = d.y(top, selected);
        g.fill(x - 1, y - 1, x + d.btnW() + 1, y + d.btnH() + 1, 0xFF_FFD700);
    }

    /** The name as it will look in game with the chosen color. */
    public static Component preview(String name, int colorIndex) {
        return ColorHelper.parseColors(apply(colorIndex, name));
    }

    /** Draws the preview right after a label. */
    public static void drawPreview(GuiGraphics g, Font font, String label, String name, int colorIndex, int x, int y) {
        if (name == null || name.isEmpty()) return;
        g.drawString(font, preview(name, colorIndex), x + font.width(label) + 6, y, 0xFFFFFFFF, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
    }

    /**
     * Adds the palette buttons (colors then "+") at {@code (formX, top)}: clicking a color calls
     * {@code onPick}, "+" opens the custom colors screen and comes back to {@code back}.
     */
    public static void addButtons(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> add,
                                  Dims d, int formX, int top, java.util.function.IntConsumer onPick,
                                  net.minecraft.client.gui.screens.Screen back) {
        int n = count();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            add.accept(net.minecraft.client.gui.components.Button.builder(label(i, false, d), b -> onPick.accept(idx))
                    .pos(d.x(formX, i), d.y(top, i)).size(d.btnW(), d.btnH())
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(tooltip(i)))
                    .build(net.rp.rpessentials.client.skin.SkinnedButton::new));
        }
        add.accept(net.minecraft.client.gui.components.Button.builder(Component.literal("§a+"),
                        b -> net.minecraft.client.Minecraft.getInstance().setScreen(new CustomColorsScreen(back)))
                .pos(d.x(formX, n), d.y(top, n)).size(d.btnW(), d.btnH())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("rpessentials.gui.color.add_tooltip")))
                .build(net.rp.rpessentials.client.skin.SkinnedButton::new));
    }
}
