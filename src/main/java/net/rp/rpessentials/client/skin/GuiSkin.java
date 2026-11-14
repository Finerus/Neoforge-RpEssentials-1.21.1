package net.rp.rpessentials.client.skin;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.config.ClientConfig;

/**
 * Textured look of the RpEssentials screens.
 * <p>
 * Every element is a sprite of {@code assets/rpessentials/textures/gui/sprites/}. In TEXTURED mode a sprite
 * is drawn when its PNG exists, otherwise the classic flat look is drawn for that element. In LEGACY mode
 * the classic look is always used. Resource packs can replace the sprites.
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
            return true;
        }
    }

    public static void toggle() {
        if (Cfg.ready(ClientConfig.SPEC)) {
            ClientConfig.GUI_STYLE.set(textured() ? ClientConfig.GuiStyle.LEGACY : ClientConfig.GuiStyle.TEXTURED);
            ClientConfig.SPEC.save();
        }
    }

    /** True when the sprite has a PNG (the atlas returns the missing texture otherwise). */
    public static boolean has(String name) {
        TextureAtlasSprite s = Minecraft.getInstance().getGuiSprites().getSprite(id(name));
        return !s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** True when this sprite must be drawn (textured mode and PNG present). */
    public static boolean active(String name) {
        return textured() && has(name);
    }

    /** Draws a sprite when active. Returns false when the caller must draw the classic look. */
    public static boolean draw(GuiGraphics g, String name, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || !active(name)) return false;
        g.blitSprite(id(name), x, y, w, h);
        return true;
    }

    // ── Elements (x1, y1, x2, y2 like GuiGraphics.fill) ──────────────────────

    /** Full screen background. */
    public static void background(GuiGraphics g, int width, int height, int legacyColor) {
        if (!draw(g, BACKGROUND, 0, 0, width, height)) g.fill(0, 0, width, height, legacyColor);
    }

    /** Panel or frame. */
    public static void panel(GuiGraphics g, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, PANEL, x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Colored accent line of the classic look (only drawn in the classic look). */
    public static void accent(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (!active(PANEL)) g.fill(x1, y1, x2, y2, color);
    }

    /** Horizontal separator line. */
    public static void separator(GuiGraphics g, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, SEPARATOR, x1, y1, x2 - x1, Math.max(1, y2 - y1))) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** Any other named box (note_row...), with the classic color as fallback. */
    public static void box(GuiGraphics g, String name, int x1, int y1, int x2, int y2, int legacyColor) {
        if (!draw(g, name, x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    /** List line: normal, hovered or selected. */
    public static void row(GuiGraphics g, int x1, int y1, int x2, int y2, boolean selected, boolean hovered, int legacyColor) {
        String n = selected ? LIST_ROW + "_selected" : hovered ? LIST_ROW + "_hover" : LIST_ROW;
        if (!draw(g, n, x1, y1, x2 - x1, y2 - y1)) g.fill(x1, y1, x2, y2, legacyColor);
    }

    // ── Switch button ────────────────────────────────────────────────────────

    /** Small button at the top right of a screen that switches between the textured and the classic look. */
    public static Button toggleButton(int screenWidth, Runnable rebuild) {
        Button b = Button.builder(Component.literal(textured() ? "✎" : "▦"), btn -> {
                    toggle();
                    rebuild.run();
                })
                .pos(screenWidth - 18, 2).size(16, 12)
                .build(SkinnedButton::new);
        b.setTooltip(Tooltip.create(Component.translatable(textured()
                ? "rpessentials.gui.style.to_legacy" : "rpessentials.gui.style.to_textured")));
        return b;
    }
}
