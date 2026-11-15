package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.SkinnedEditBox;

import java.util.ArrayList;
import java.util.List;

/** The player's own palette colors (customColors in rpessentials-client.toml): add with a hex code, remove. */
public class CustomColorsScreen extends Screen {

    private static final String K = "rpessentials.gui.color.";

    private final Screen back;
    private ButtonRowList list;
    private String name = "";
    private String hex = "";
    private String pendingDelete = null;

    public CustomColorsScreen(Screen back) {
        super(Component.translatable(K + "custom_title"));
        this.back = back;
    }

    private boolean validHex() {
        return hex.replace("#", "").trim().matches("[0-9a-fA-F]{6}");
    }

    private int hexRgb() {
        return validHex() ? Integer.parseInt(hex.replace("#", "").trim(), 16) : 0x555555;
    }

    @Override
    protected void init() {
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        int w = Math.min(320, this.width - 24);
        int x = (this.width - w) / 2;
        int top = 34;

        if (list == null) list = new ButtonRowList(Minecraft.getInstance(), 20);
        list.layout(x - 6, top, w + 12, this.height - 104 - top);
        int rw = list.rowWidth();
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
                    .build(SkinnedButton::new);
            rows.add(new ButtonRowList.Row().add(del, 0)
                    .text(Component.literal("■ ").withStyle(s -> s.withColor(e.rgb()))
                            .append(Component.literal(e.name()).withStyle(s -> s.withColor(e.rgb())))
                            .append(Component.literal("  §8#" + e.code().substring(2))).getVisualOrderText(), 22, 5));
        }
        if (rows.isEmpty()) rows.add(new ButtonRowList.Row().text("§8" + I18n.get(K + "custom_none"), 4, 5));
        list.setRows(rows);
        addRenderableWidget(list);

        // ── Add a color ───────────────────────────────────────────────────────
        int y = this.height - 92;
        int nameW = w - 90 - UiLayout.GAP;
        EditBox nameBox = new SkinnedEditBox(this.font, x, y + 12, nameW, 18, Component.translatable(K + "field_name"));
        nameBox.setHint(Component.literal("§8" + I18n.get(K + "name_hint")));
        nameBox.setMaxLength(24);
        nameBox.setValue(name);
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        EditBox hexBox = new SkinnedEditBox(this.font, x + nameW + UiLayout.GAP, y + 12, 70, 18, Component.translatable(K + "field_hex"));
        hexBox.setHint(Component.literal("§8#FF8800"));
        hexBox.setMaxLength(7);
        hexBox.setValue(hex);
        hexBox.setResponder(v -> hex = v);
        addRenderableWidget(hexBox);

        Button add = Button.builder(Component.translatable(K + "btn_add"), b -> {
                    if (ColorPalette.addCustom(name, hex)) {
                        name = "";
                        hex = "";
                        if (list != null) list.scrollToEndOnce();
                    }
                    rebuildWidgets();
                })
                .pos(x, y + 36).size(w, 18).build(SkinnedButton::new);
        addRenderableWidget(add);

        addRenderableWidget(Button.builder(Component.translatable(K + "btn_close"),
                        b -> Minecraft.getInstance().setScreen(back))
                .pos(this.width / 2 - 50, this.height - 28).size(100, 20).build(SkinnedButton::new));
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(back); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0xAA000000);
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFD700);
        g.drawCenteredString(this.font, "§7" + I18n.get(K + "custom_subtitle"), this.width / 2, 22, 0xAAAAAA);

        int w = Math.min(320, this.width - 24);
        int x = (this.width - w) / 2;
        int y = this.height - 92;
        g.drawString(this.font, "§7" + I18n.get(K + "add_header"), x, y, 0xAAAAAA, false);
        // Live preview of the color being typed, right of the hex field.
        int px = x + w - 14, py = y + 13;
        g.fill(px - 1, py - 1, px + 15, py + 17, 0xFF888888);
        g.fill(px, py, px + 14, py + 16, 0xFF000000 | hexRgb());
        if (!hex.isBlank() && !validHex()) {
            g.drawString(this.font, "§c" + I18n.get(K + "bad_hex"), x + w - this.font.width(I18n.get(K + "bad_hex")), y, 0xFF5555, false);
        } else if (validHex() && !name.isBlank()) {
            String preview = name;
            g.drawString(this.font, Component.literal(preview).withStyle(s -> s.withColor(hexRgb())),
                    x + this.font.width(I18n.get(K + "add_header")) + 8, y, 0xFFFFFF, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
