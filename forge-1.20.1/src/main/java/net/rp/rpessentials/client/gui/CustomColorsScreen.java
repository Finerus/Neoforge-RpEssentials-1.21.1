package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.util.ColorHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's own palette colors (customColors in rpessentials-client.toml): the list on the left
 * (remove), a color picker on the right (shade square + hue bar, or a hex code) to add a plain color
 * or a gradient between two colors, with a live preview.
 */
public class CustomColorsScreen extends net.rp.rpessentials.client.skin.CanvasScreen {

    private static final String K = "rpessentials.gui.color.";
    private static final int PICKER_W = 176;
    private static final int HUE_W = 12;

    private final Screen back;
    private ButtonRowList list;
    private String name = "";
    private String pendingDelete = null;

    // Picker state, kept across rebuilds (resize, skin toggle...).
    private boolean gradient = false;
    private final int[] colors = { 0xFF8800, 0x8E2DE2 };
    private int active = 0;
    private float hue, sat, val;
    private EditBox hexBox;
    private boolean syncing = false;

    // Layout of the picker, computed in init().
    private int px, sqY, sq;

    public CustomColorsScreen(Screen back) {
        super(Component.translatable(K + "custom_title"));
        this.back = back;
        loadHsv();
    }

    // =========================================================================
    // COLOR STATE
    // =========================================================================
    private int current() { return colors[active]; }

    private void loadHsv() {
        float[] hsv = rgbToHsv(current());
        // Grays have no hue: keep the hue of the bar instead of jumping to red.
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private void setFromHsv() {
        colors[active] = Mth.hsvToRgb(Mth.clamp(hue, 0f, 0.9999f), Mth.clamp(sat, 0f, 1f), Mth.clamp(val, 0f, 1f));
        if (hexBox != null) {
            syncing = true;
            hexBox.setValue(String.format(Locale.ROOT, "#%06X", current()));
            syncing = false;
        }
    }

    private void onHexTyped(String v) {
        if (syncing) return;
        String h = v.replace("#", "").trim();
        if (!h.matches("[0-9a-fA-F]{6}")) return;
        colors[active] = Integer.parseInt(h, 16);
        loadHsv();
    }

    private boolean validHex() {
        return hexBox == null || hexBox.getValue().replace("#", "").trim().matches("[0-9a-fA-F]{6}");
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        return new float[]{ h, max == 0f ? 0f : d / max, max };
    }

    /** The code that will be added: a hex color or a gradient. */
    private String code() {
        return gradient ? ColorPalette.gradCode(colors[0], colors[1])
                : String.format(Locale.ROOT, "&#%06X", colors[0]);
    }

    private Component colored(String text) {
        return ColorHelper.parseColors(code() + text + (gradient ? ColorPalette.GRAD_END : ""));
    }

    // =========================================================================
    // WIDGETS
    // =========================================================================
    @Override
    protected void initContent() {
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        int w = Math.min(460, this.width - 24);
        int x = (this.width - w) / 2;
        int top = 34;
        int pw = Math.min(PICKER_W, w / 2);
        int listW = w - pw - 10;
        px = x + listW + 10;

        // ── List of the custom colors ────────────────────────────────────────
        if (list == null) list = new ButtonRowList(Minecraft.getInstance(), 20);
        list.layout(x - 6, top, listW + 12, this.height - 34 - top);
        List<ButtonRowList.Row> rows = new ArrayList<>();
        for (ColorPalette.Entry e : ColorPalette.entries()) {
            if (!e.custom()) continue;
            boolean pending = e.code().equals(pendingDelete);
            Button del = Button.builder(Component.literal(pending ? "§c!" : "§c✗"), b -> {
                        if (e.code().equals(pendingDelete)) {
                            ColorPalette.removeCustom(e.code());
                            pendingDelete = null;
                        } else {
                            pendingDelete = e.code();
                        }
                        rebuildWidgets();
                    })
                    .size(16, 18)
                    .tooltip(Tooltip.create(Component.translatable(pending ? K + "confirm_delete" : K + "delete_tooltip")))
                    .build(SkinnedButton.named("delete"));
            Component label = e.gradient()
                    ? Component.empty().append(ColorHelper.parseColors(e.code() + "■ " + e.name() + ColorPalette.GRAD_END))
                    : Component.literal("■ " + e.name()).withStyle(s -> s.withColor(e.rgb()));
            String code = e.gradient() ? ColorPalette.gradName(e.rgb(), e.rgb2()) : "#" + e.code().substring(2);
            rows.add(new ButtonRowList.Row().add(del, 0)
                    .text(Component.empty().append(label).append(Component.literal("  §8" + code)).getVisualOrderText(), 22, 5));
        }
        if (rows.isEmpty()) rows.add(new ButtonRowList.Row().text("§8" + I18n.get(K + "custom_none"), 4, 5));
        list.setRows(rows);
        addRenderableWidget(list);

        // ── Picker: mode, slots ──────────────────────────────────────────────
        int modeW = 62;
        addRenderableWidget(Button.builder(Component.translatable(gradient ? K + "mode_gradient" : K + "mode_plain"), b -> {
                    gradient = !gradient;
                    if (!gradient) active = 0;
                    loadHsv();
                    rebuildWidgets();
                })
                .pos(px, top).size(modeW, 18)
                .tooltip(Tooltip.create(Component.translatable(K + "mode_tooltip")))
                .build(SkinnedButton.named("mode")));
        if (gradient) {
            int slotW = (pw - modeW - 18 - 3 * 2) / 2;
            for (int i = 0; i < 2; i++) {
                final int slot = i;
                String label = (active == i ? "§e▶ " : "") + I18n.get(i == 0 ? K + "slot_from" : K + "slot_to");
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                            active = slot;
                            loadHsv();
                            rebuildWidgets();
                        })
                        .pos(px + modeW + 2 + i * (slotW + 2), top).size(slotW, 18)
                        .build(SkinnedButton.named("slot")));
            }
            addRenderableWidget(Button.builder(Component.literal("⇄"), b -> {
                        int t = colors[0]; colors[0] = colors[1]; colors[1] = t;
                        loadHsv();
                        rebuildWidgets();
                    })
                    .pos(px + pw - 16, top).size(16, 18)
                    .tooltip(Tooltip.create(Component.translatable(K + "swap_tooltip")))
                    .build(SkinnedButton.named("swap")));
        }

        // ── Shade square + hue bar ───────────────────────────────────────────
        sqY = top + 22;
        sq = Mth.clamp(Math.min(pw - HUE_W - 4, this.height - 154), 40, 110);
        addRenderableWidget(new ShadeSquare(px, sqY, sq));
        addRenderableWidget(new HueBar(px + sq + 4, sqY, HUE_W, sq));

        // ── Name, hex, add ───────────────────────────────────────────────────
        int rowY = sqY + sq + 28;
        int hexW = 64;
        EditBox nameBox = new SkinnedEditBox(this.font, px, rowY, pw - hexW - 4, 18, Component.translatable(K + "field_name")).skin("name");
        // The hint is cut to the box (the box is narrow next to the hex field).
        nameBox.setHint(Component.literal("§8" + fit(I18n.get(K + "name_hint"), pw - hexW - 4 - 8)));
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        hexBox = new SkinnedEditBox(this.font, px + pw - hexW, rowY, hexW, 18, Component.translatable(K + "field_hex")).skin("hex");
        hexBox.setHint(Component.literal("§8#FF8800"));
        hexBox.setMaxLength(7);
        syncing = true;
        hexBox.setValue(String.format(Locale.ROOT, "#%06X", current()));
        syncing = false;
        hexBox.setResponder(this::onHexTyped);
        addRenderableWidget(hexBox);

        addRenderableWidget(Button.builder(Component.translatable(gradient ? K + "btn_add_gradient" : K + "btn_add"), b -> {
                    boolean ok = gradient ? ColorPalette.addCustomGradient(name, colors[0], colors[1])
                            : ColorPalette.addCustom(name, String.format(Locale.ROOT, "%06X", colors[0]));
                    if (ok) {
                        name = "";
                        if (list != null) list.scrollToEndOnce();
                    }
                    rebuildWidgets();
                })
                .pos(px, rowY + 22).size(pw, 18).build(SkinnedButton.named("add")));

        addRenderableWidget(Button.builder(Component.translatable(K + "btn_close"),
                        b -> Minecraft.getInstance().setScreen(back))
                .pos(this.width / 2 - 50, this.height - 26).size(100, 20).build(SkinnedButton.named("close")));
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(back); }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0xAA000000);
        renderScreen(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFD700);
        g.drawCenteredString(this.font, "§7" + I18n.get(K + "custom_subtitle"), this.width / 2, 22, 0xAAAAAA);

        int pw = Math.min(PICKER_W, Math.min(460, this.width - 24) / 2);
        // Bar of the color / gradient, then the name (or "Preview") written with it.
        int barY = sqY + sq + 4;
        GuiSkin.box(g, "frame_preview", px - 1, barY - 1, px + pw + 1, barY + 7, 0xFF555555);
        for (int i = 0; i < pw; i++) {
            float t = pw <= 1 ? 0f : (float) i / (pw - 1);
            int c = gradient ? lerp(colors[0], colors[1], t) : colors[0];
            g.fill(px + i, barY, px + i + 1, barY + 6, 0xFF000000 | c);
        }
        String label = I18n.get(K + "preview");
        String sample = name.isBlank() ? I18n.get(K + "preview_sample") : name;
        g.drawString(this.font, "§7" + label, px, barY + 10, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        g.drawString(this.font, colored(sample), px + this.font.width(label) + 4, barY + 10, 0xFFFFFF, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        if (!validHex()) {
            String bad = I18n.get(K + "bad_hex");
            g.drawString(this.font, "§c" + bad, px + pw - this.font.width(bad), sqY + sq + 28 + 42, 0xFF5555, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        }
    }

    /** {@code text}, cut with "…" so it fits in {@code width} pixels. */
    private String fit(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================================
    // PICKER WIDGETS
    // =========================================================================

    /** Saturation (left to right) and brightness (bottom to top) of the current hue. */
    private class ShadeSquare extends AbstractWidget {
        ShadeSquare(int x, int y, int size) {
            super(x, y, size, size, Component.translatable(K + "picker_hint"));
            setTooltip(Tooltip.create(Component.translatable(K + "picker_hint")));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), s = getWidth();
            GuiSkin.box(g, "frame_shade", x - 1, y - 1, x + s + 1, y + s + 1, 0xFF555555);
            for (int i = 0; i < s; i++) {
                int topColor = Mth.hsvToRgb(Mth.clamp(hue, 0f, 0.9999f), (float) i / (s - 1), 1f);
                g.fillGradient(x + i, y, x + i + 1, y + s, 0xFF000000 | topColor, 0xFF000000);
            }
            int cx = x + Math.round(sat * (s - 1)), cy = y + Math.round((1f - val) * (s - 1));
            int ring = val > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
            g.fill(cx - 2, cy - 2, cx + 3, cy - 1, ring);
            g.fill(cx - 2, cy + 2, cx + 3, cy + 3, ring);
            g.fill(cx - 2, cy - 1, cx - 1, cy + 2, ring);
            g.fill(cx + 2, cy - 1, cx + 3, cy + 2, ring);
        }

        private void pick(double mx, double my) {
            sat = Mth.clamp((float) (mx - getX()) / (getWidth() - 1), 0f, 1f);
            val = 1f - Mth.clamp((float) (my - getY()) / (getHeight() - 1), 0f, 1f);
            setFromHsv();
        }

        @Override
        public void onClick(double mx, double my) { pick(mx, my); }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) { pick(mx, my); }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    /** Hue from top (red) to bottom. */
    private class HueBar extends AbstractWidget {
        HueBar(int x, int y, int w, int h) {
            super(x, y, w, h, Component.translatable(K + "picker_hint"));
            setTooltip(Tooltip.create(Component.translatable(K + "picker_hint")));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            GuiSkin.box(g, "frame_hue", x - 1, y - 1, x + w + 1, y + h + 1, 0xFF555555);
            for (int j = 0; j < h; j++) {
                g.fill(x, y + j, x + w, y + j + 1, 0xFF000000 | Mth.hsvToRgb(Math.min(0.9999f, (float) j / (h - 1)), 1f, 1f));
            }
            int cy = y + Math.round(hue * (h - 1));
            g.fill(x - 2, cy - 1, x + w + 2, cy, 0xFFFFFFFF);
            g.fill(x - 2, cy + 1, x + w + 2, cy + 2, 0xFFFFFFFF);
        }

        private void pick(double my) {
            hue = Mth.clamp((float) (my - getY()) / (getHeight() - 1), 0f, 1f);
            // Picking a hue on a gray or black color would change nothing visible: make it vivid.
            if (sat < 0.05f) sat = 1f;
            if (val < 0.05f) val = 1f;
            setFromHsv();
        }

        @Override
        public void onClick(double mx, double my) { pick(my); }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) { pick(my); }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager sm) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }
}
