package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.util.RpText;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Several-lines text of a character sheet: Minecraft text area (automatic line wrap), color palette
 * and format buttons that insert {@code &} codes or markdown at the cursor, and a live preview.
 */
public class SheetTextEditorScreen extends net.rp.rpessentials.client.skin.CanvasScreen {

    private static final String K = "rpessentials.gui.sheet.editor.";
    private static final int SWATCH = 12;

    private final Screen back;
    private final String fieldName;
    private final int maxLength;
    private final Consumer<String> onDone;
    private String value;
    private final String original;
    private Button doneButton;
    private boolean confirmCancel = false;

    private MultiLineEditBox editor;
    private ButtonRowList preview;
    private int previewW;

    public SheetTextEditorScreen(Screen back, String fieldName, String value, int maxLength, Consumer<String> onDone) {
        super(Component.literal(fieldName));
        this.back = back;
        this.fieldName = fieldName;
        this.value = value == null ? "" : value;
        this.original = this.value;
        this.maxLength = maxLength;
        this.onDone = onDone;
    }

    @Override
    protected void initContent() {
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        int margin = 12;
        int areaW = this.width - margin * 2;
        boolean side = areaW >= 520;

        // ── Toolbar: colors, reset, then markdown formats ─────────────────────
        int x = margin;
        int y = 26;
        // Same palette as the nickname and profession colors (custom colors included), without "none".
        int colors = ColorPalette.count();
        for (int i = 1; i <= colors; i++) {
            if (x + SWATCH > this.width - margin) { x = margin; y += SWATCH + 2; }
            if (i == colors) {
                addRenderableWidget(Button.builder(Component.literal("§a+"),
                                b -> Minecraft.getInstance().setScreen(new CustomColorsScreen(this)))
                        .pos(x, y).size(SWATCH, SWATCH)
                        .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.color.add_tooltip")))
                        .build(SkinnedButton.named("color_add")));
            } else {
                final String code = ColorPalette.code(i);
                final boolean grad = ColorPalette.get(i).gradient();
                addRenderableWidget(Button.builder(ColorPalette.label(i, false, null),
                                b -> { if (grad) wrap(code, ColorPalette.GRAD_END); else insert(code); })
                        .pos(x, y).size(SWATCH, SWATCH)
                        .tooltip(Tooltip.create(ColorPalette.tooltip(i)))
                        .build(SkinnedButton.named("color")));
            }
            x += SWATCH + 1;
        }
        x += 4;
        if (x + 24 > this.width - margin) { x = margin; y += SWATCH + 2; }
        addRenderableWidget(tool("§7&r", "reset", 22, () -> insert("&r"), x, y));
        x += 24;
        int fy = y;
        if (x + 4 * 22 > this.width - margin) { x = margin; fy = y + SWATCH + 3; }
        addRenderableWidget(tool("§l B", "bold", 20, () -> wrap("**"), x, fy));
        addRenderableWidget(tool("§o I", "italic", 20, () -> wrap("*"), x + 22, fy));
        addRenderableWidget(tool("§n U", "underline", 20, () -> wrap("__"), x + 44, fy));
        addRenderableWidget(tool("§m S", "strike", 20, () -> wrap("~~"), x + 66, fy));

        // ── Text area and preview ─────────────────────────────────────────────
        int top = fy + SWATCH + 14;   // room for the "Preview" title above the preview
        int bottom = this.height - 46;
        int editorW = side ? (areaW - 8) / 2 : areaW;
        int editorH = side ? bottom - top : (bottom - top) / 2 - 4;
        editor = new MultiLineEditBox(this.font, margin, top, editorW, editorH,
                Component.translatable(K + "placeholder"), Component.literal(fieldName));
        editor.setCharacterLimit(maxLength);
        editor.setValue(value);
        editor.setValueListener(v -> {
            value = v;
            refreshPreview();
            SaveUi.refresh(doneButton, I18n.get(K + "done"), !value.equals(original));
        });
        addRenderableWidget(editor);

        int pX = side ? margin + editorW + 8 : margin;
        int pY = side ? top : top + editorH + 14;
        previewW = side ? areaW - editorW - 8 : areaW;
        int pH = side ? bottom - top : bottom - pY;
        if (preview == null) preview = new ButtonRowList(Minecraft.getInstance(), 10);
        preview.layout(pX - 6, pY, previewW + 12, pH);
        refreshPreview();
        addRenderableWidget(preview);
        setInitialFocus(editor);

        // ── Footer ────────────────────────────────────────────────────────────
        int btnY = this.height - 28;
        doneButton = Button.builder(Component.translatable(K + "done"), b -> {
                    onDone.accept(value);
                    Minecraft.getInstance().setScreen(back);
                })
                .pos(this.width / 2 - 124, btnY).size(120, 20).build(SkinnedButton.named("done"));
        SaveUi.refresh(doneButton, I18n.get(K + "done"), !value.equals(original));
        addRenderableWidget(doneButton);
        addRenderableWidget(Button.builder(Component.literal(confirmCancel
                                ? "§c" + I18n.get("rpessentials.gui.unsaved.discard") : I18n.get(K + "cancel")),
                        b -> onClose())
                .pos(this.width / 2 + 4, btnY).size(120, 20)
                .tooltip(SaveUi.closeTooltip(confirmCancel))
                .build(SkinnedButton.named("cancel")));
    }

    private Button tool(String label, String key, int w, Runnable action, int x, int y) {
        return Button.builder(Component.literal(label), b -> action.run())
                .pos(x, y).size(w, SWATCH)
                .tooltip(Tooltip.create(Component.translatable(K + "tool." + key)))
                .build(SkinnedButton.named("tool_" + key));
    }

    /** Types {@code text} at the cursor of the text area, as if it was typed on the keyboard. */
    private void insert(String text) {
        setFocused(editor);
        editor.setFocused(true);
        for (char c : text.toCharArray()) editor.charTyped(c, 0);
    }

    /** Inserts {@code open + close} and puts the cursor between them (gradient tags). */
    private void wrap(String open, String close) {
        insert(open + close);
        for (int i = 0; i < close.length(); i++) editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
    }

    /** Inserts an empty markdown span ({@code ****}) and puts the cursor inside it. */
    private void wrap(String token) {
        insert(token + token);
        for (int i = 0; i < token.length(); i++) editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
    }

    private void refreshPreview() {
        if (preview == null) return;
        List<ButtonRowList.Row> rows = new ArrayList<>();
        if (value.isBlank()) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get(K + "preview_empty"), 2, 1));
        } else {
            for (FormattedCharSequence line : this.font.split(RpText.toComponent(value), Math.max(40, previewW - 8))) {
                rows.add(new ButtonRowList.Row().text(line, 2, 1));
            }
        }
        preview.setRows(rows);
    }

    /** Cancel / Escape: asks again when the text was changed. */
    @Override
    public void onClose() {
        if (!value.equals(original) && !confirmCancel) {
            confirmCancel = true;
            rebuildWidgets();
            return;
        }
        Minecraft.getInstance().setScreen(back);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0xAA000000);
        renderScreen(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, "§6" + I18n.get(K + "title", fieldName), this.width / 2, 10, 0xFFD700);
        if (preview != null) {
            //#if forge
            //? g.drawString(this.font, "§8" + I18n.get(K + "preview"), preview.getLeft() + 6, preview.getTop() - 9, 0x888888, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
            //#else
            g.drawString(this.font, "§8" + I18n.get(K + "preview"), preview.getX() + 6, preview.getY() - 9, 0x888888, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
            //#endif
        }
        // The character count is drawn by the Minecraft text area itself (under it, on the right).
    }

    @Override
    //#if forge
    //? public void renderBackground(GuiGraphics g) {}
    //#else
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}
    //#endif

    @Override
    public boolean isPauseScreen() { return false; }
}
