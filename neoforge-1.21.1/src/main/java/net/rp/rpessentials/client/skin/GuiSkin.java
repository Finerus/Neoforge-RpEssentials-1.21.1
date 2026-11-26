package net.rp.rpessentials.client.skin;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//#if forge
//#else
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.ClientConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textured look of the RpEssentials screens.
 * <p>
 * Every element is a sprite of {@code assets/rpessentials/textures/gui/sprites/}, sorted by category:
 * <pre>
 *   &lt;screen&gt;/        elements shared by every tab (player list, tab buttons, Close...)
 *   &lt;screen&gt;/&lt;tab&gt;/  elements that only exist in one tab (built between {@link #beginTab()} and {@link #endTab()})
 *   (root)           generic textures, used when the screen has none
 * </pre>
 * A tab element is looked up in its tab folder, then the screen folder, then the root; a shared element in the
 * screen folder, then the root. Elements with an id ({@code button_apply}) are tried before the generic name
 * ({@code button}):
 * <pre>
 *   player_profile/profile/button_apply_hover → player_profile/button_apply_hover → button_apply_hover
 *   → player_profile/profile/button_hover → player_profile/button_hover → button_hover
 * </pre>
 * Panels are numbered in drawing order ({@code panel_1}, {@code panel_2}...) so each panel of a screen can differ.
 * The screen folder comes from the screen class ({@code PlayerProfileScreen} → {@code player_profile}),
 * the tab folder from {@link SkinScope}. In TEXTURED mode the first sprite found is drawn, otherwise the classic
 * flat look is drawn for that element. In LEGACY mode the classic look is always used. Resource packs can replace
 * the sprites. {@link GuiTemplateExporter} writes a template of every element at its exact size.
 */
public final class GuiSkin {

    private GuiSkin() {}

    public static final String BACKGROUND = "background";
    public static final String PANEL = "panel";
    public static final String LIST_ROW = "list_row";
    public static final String BUTTON = "button";
    public static final String TAB = "tab";
    public static final String TEXT_FIELD = "text_field";
    public static final String NOTE_ROW = "note_row";
    public static final String SEPARATOR = "separator";

    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(RpEssentials.MODID, name);
    }

    // ── Mode ─────────────────────────────────────────────────────────────────

    public static boolean textured() {
        if (Cfg.ready(ClientConfig.SPEC)) {
            return ClientConfig.GUI_STYLE.get() == ClientConfig.GuiStyle.TEXTURED;
        } else {
            return false;
        }
    }

    public static void toggle() {
        if (Cfg.ready(ClientConfig.SPEC)) {
            ClientConfig.GUI_STYLE.set(textured() ? ClientConfig.GuiStyle.LEGACY : ClientConfig.GuiStyle.TEXTURED);
            ClientConfig.SPEC.save();
        }
    }

    /** Text shadow of the RpEssentials screens: on in the textured look (contrast on textures), off in the legacy look. */
    public static boolean textShadow() {
        return textured();
    }

    //#if forge
    //? /** True when the sprite has a PNG (see {@link GuiSprites}). */
    //#else
    /** True when the sprite has a PNG (the atlas returns the missing texture otherwise). */
    //#endif
    public static boolean has(String name) {
        //#if forge
        //? return GuiSprites.exists(id(name));
        //#else
        TextureAtlasSprite s = Minecraft.getInstance().getGuiSprites().getSprite(id(name));
        return !s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
        //#endif
    }

    // ── Scope (screen / tab) ─────────────────────────────────────────────────

    /** True while the content of the open tab is built or drawn (see {@link #beginTab()}). */
    private static boolean tabContent = false;

    /** Start of the elements of the open tab (widgets built and things drawn until {@link #endTab()}). */
    public static void beginTab() { tabContent = true; }

    public static void endTab() { tabContent = false; }

    /** True while tab content is built / drawn. Widgets keep this value from their creation. */
    public static boolean inTab() { return tabContent; }

    /** Sets the category of the element being drawn; returns the previous one, to put back after. */
    public static boolean setTab(boolean tab) {
        boolean old = tabContent;
        tabContent = tab;
        return old;
    }

    /** Folders to look into, most specific first, ending with "" (the sprites root). */
    public static List<String> scopes() {
        List<String> out = new ArrayList<>(3);
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            String root = screenFolder(screen);
            if (tabContent && screen instanceof SkinScope s) {
                String tab = s.skinTab();
                if (tab != null && !tab.isEmpty()) out.add(root + "/" + tab + "/");
            }
            out.add(root + "/");
        }
        out.add("");
        return out;
    }

    /** Folder of a screen: its class name in snake_case without "Screen" ({@code ConfigManagerScreen$ListEdit} → {@code config_manager/list_edit}). */
    public static String screenFolder(Screen screen) {
        String name = screen.getClass().getName();
        name = name.substring(name.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder();
        for (String part : name.split("\\$")) {
            if (part.isEmpty() || Character.isDigit(part.charAt(0))) continue; // anonymous classes
            if (part.endsWith("SubScreen") && part.length() > 9) part = part.substring(0, part.length() - 9);
            else if (part.endsWith("Screen") && part.length() > 6) part = part.substring(0, part.length() - 6);
            if (out.length() > 0) out.append('/');
            out.append(part.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /**
     * Sprite names to try for an element, most specific first.
     * @param base  element ("button", "panel", "list_row"...)
     * @param id    element id ("refresh", "tab_notes", panel number...), or null
     * @param state state suffix ("", "_hover", "_disabled", "_selected"...)
     */
    public static List<String> candidates(String base, String id, String state) {
        List<String> scopes = scopes();
        List<String> out = new ArrayList<>(scopes.size() * 2);
        if (id != null) for (String s : scopes) out.add(s + base + "_" + id + state);
        for (String s : scopes) out.add(s + base + state);
        return out;
    }

    /** First existing sprite for an element in TEXTURED mode, null otherwise (classic look). */
    public static String resolve(String base, String id, String state) {
        if (!textured()) return null;
        for (String n : candidates(base, id, state)) if (has(n)) return n;
        return null;
    }

    /** True when this sprite must be drawn (textured mode and a PNG found for the current screen). */
    public static boolean active(String name) {
        return resolve(name, null, "") != null;
    }

    /** Draws an element. Returns false when the caller must draw the classic look. */
    public static boolean draw(GuiGraphics g, String name, int x, int y, int w, int h) {
        return draw(g, name, null, "", x, y, w, h);
    }

    /** Draws an element with an id and a state. Returns false when the caller must draw the classic look. */
    public static boolean draw(GuiGraphics g, String base, String id, String state, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return false;
        GuiTemplateExporter.record(base, id, state, x, y, w, h);
        String sprite = resolve(base, id, state);
        if (sprite == null) return false;
        //#if forge
        //? GuiSprites.blit(g, id(sprite), x, y, w, h);
        //#else
        g.blitSprite(id(sprite), x, y, w, h);
        //#endif
        return true;
    }

    // ── Panel numbering (reset before each frame) ────────────────────────────

    private static final Map<String, Integer> drawCounts = new HashMap<>();

    /** Called before a screen is drawn. */
    public static void beginFrame() {
        drawCounts.clear();
    }

    private static String nextNumber(String base) {
        int n = drawCounts.merge(base, 1, Integer::sum);
        return String.valueOf(n);
    }

    // ── Elements (x1, y1, x2, y2 like GuiGraphics.fill) ──────────────────────

    /**
     * Full screen background: the vanilla darkening in the textured look (no texture), the flat color in the
     * legacy look. On a fixed canvas the whole window is already darkened by {@link CanvasScreen}.
     */
    public static void background(GuiGraphics g, int width, int height, int legacyColor) {
        if (!textured()) {
            g.fill(0, 0, width, height, legacyColor);
            return;
        }
        if (CanvasScreen.rendering() != null) return;
        vanillaDarken(g, width, height);
    }

    /** Darkening drawn by vanilla menus over the world. */
    public static void vanillaDarken(GuiGraphics g, int width, int height) {
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    /** Panel or frame (numbered: panel_1, panel_2... in drawing order). */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, PANEL, nextNumber(PANEL), "", x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Panel with its own texture: panel_<id>.png. */
    public static void panel(GuiGraphics g, String id, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, PANEL, id, "", x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Colored accent line of the classic look (only drawn in the classic look). */
    public static void accent(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (!active(PANEL)) g.fill(x1, y1, x2, y2, color);
    }

    /** Horizontal separator line. */
    public static void separator(GuiGraphics g, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, SEPARATOR, x1, y1, x2 - x1, Math.max(1, y2 - y1))) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Separator with its own texture: separator_<id>.png. */
    public static void separator(GuiGraphics g, String id, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, SEPARATOR, id, "", x1, y1, x2 - x1, Math.max(1, y2 - y1))) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Any other named box (note_row...), with the classic color as fallback. */
    public static void box(GuiGraphics g, String name, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, name, x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** List line: normal, hovered or selected. */
    public static void row(GuiGraphics g, int x1, int y1, int x2, int y2, boolean selected, boolean hovered, int legacyColor) {
        String state = selected ? "_selected" : hovered ? "_hover" : "";
        if (!draw(g, LIST_ROW, null, state, x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    // ── Switch button ────────────────────────────────────────────────────────

    /** Small button at the top right of a screen that switches between the textured and the classic look. */
    public static Button toggleButton(int screenWidth, Runnable rebuild) {
        Button b = Button.builder(Component.literal(textured() ? "✎" : "▦"), btn -> {
                    toggle();
                    rebuild.run();
                })
                .pos(screenWidth - 18, 2).size(16, 12)
                .build(SkinnedButton.named("style"));
        b.setTooltip(Tooltip.create(Component.translatable(textured()
                ? "rpessentials.gui.style.to_legacy" : "rpessentials.gui.style.to_textured")));
        return b;
    }
}
