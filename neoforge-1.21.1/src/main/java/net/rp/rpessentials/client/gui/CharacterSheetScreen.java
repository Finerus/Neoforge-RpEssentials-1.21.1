package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.identity.CharacterSheetManager;
import net.rp.rpessentials.network.CharacterSheetPacket;
import net.rp.rpessentials.network.RequestCharacterSheetPacket;
import net.rp.rpessentials.network.SaveCharacterSheetPacket;
import net.rp.rpessentials.util.ColorHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Character sheet of a player (staff only), opened from the Profile tab of the player profile GUI.
 * One-line fields are edited here; several-lines fields open {@link SheetTextEditorScreen}.
 */
public class CharacterSheetScreen extends net.rp.rpessentials.client.skin.CanvasScreen {

    private static final int ROW_H = 24;
    private static final int LABEL_W = 110;
    private static final String K = "rpessentials.gui.sheet.";

    private final Screen back;
    private final UUID target;
    private final String targetName;
    /** Current nickname of the player (§ codes) and how to change it; null setter = no button. */
    private String currentNick;
    private final java.util.function.Consumer<String> nickSetter;
    private Button useAsNickButton;

    private boolean requested = false;
    private boolean loaded = false;
    private boolean enabled = true;
    private int maxText = 4000;
    private List<CharacterSheetManager.Field> fields = List.of();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> saved = new LinkedHashMap<>();
    private boolean confirmDiscard = false;
    private ButtonRowList list;
    private Button saveButton;

    public CharacterSheetScreen(Screen back, UUID target, String targetName,
                                String currentNick, java.util.function.Consumer<String> nickSetter) {
        super(Component.translatable(K + "title", targetName));
        this.back = back;
        this.target = target;
        this.targetName = targetName;
        this.currentNick = currentNick == null ? "" : currentNick;
        this.nickSetter = nickSetter;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replace('&', '§');
    }

    /** "Use as nickname" is only useful when the RP name is set and differs from the current nickname. */
    private void refreshNickButton() {
        if (useAsNickButton == null) return;
        String rp = normalize(values.getOrDefault("name", ""));
        useAsNickButton.active = !rp.isEmpty() && !rp.equals(normalize(currentNick));
    }

    /** Called when the server sends the sheet (on open and after a save). */
    public void setData(CharacterSheetPacket packet) {
        if (!packet.targetUuid().equals(target)) return;
        loaded = true;
        enabled = packet.enabled();
        maxText = packet.maxTextLength();
        fields = packet.fields();
        values.clear();
        values.putAll(packet.values());
        saved.clear();
        saved.putAll(packet.values());
        confirmDiscard = false;
        rebuildWidgets();
    }

    private boolean dirty() {
        for (CharacterSheetManager.Field f : fields) {
            if (!values.getOrDefault(f.id(), "").equals(saved.getOrDefault(f.id(), ""))) return true;
        }
        return false;
    }

    static String fieldLabel(CharacterSheetManager.Field f) {
        return f.label().isEmpty() ? I18n.get(K + "field." + f.id()) : f.label();
    }

    @Override
    protected void initContent() {
        if (!requested) {
            requested = true;
            PacketDistributor.sendToServer(new RequestCharacterSheetPacket(target));
        }
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        int panelW = Math.min(this.width - 24, 420);
        int x = (this.width - panelW) / 2;
        int top = 34;

        if (loaded && enabled) {
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), ROW_H);
            list.layout(x - 6, top, panelW + 12, this.height - 40 - top);
            int rw = list.rowWidth();
            int widgetX = Math.min(LABEL_W, rw / 3);
            int widgetW = rw - widgetX;
            List<ButtonRowList.Row> rows = new ArrayList<>();
            for (CharacterSheetManager.Field f : fields) {
                String label = UiLayout.fit(this.font, "§7" + fieldLabel(f), widgetX - 4);
                ButtonRowList.Row row = new ButtonRowList.Row().text(label, 0, 8);
                if (f.multiline()) {
                    row.add(textButton(f, widgetW), widgetX, 2);
                } else if (f.id().equals("name") && nickSetter != null) {
                    // RP name: field + button that copies it to the player's nickname.
                    row.add(lineBox(f, widgetW - 22), widgetX, 3);
                    useAsNickButton = Button.builder(Component.literal("§e⇄"), b -> {
                                String rp = values.getOrDefault("name", "").trim();
                                nickSetter.accept(rp);
                                currentNick = normalize(rp);
                                refreshNickButton();
                            })
                            .size(20, 18)
                            .tooltip(Tooltip.create(Component.translatable(K + "use_as_nick_tooltip")))
                            .build(SkinnedButton.named("use_as_nick"));
                    row.add(useAsNickButton, widgetX + widgetW - 20, 3);
                    refreshNickButton();
                } else {
                    row.add(lineBox(f, widgetW), widgetX, 3);
                }
                rows.add(row);
            }
            if (fields.isEmpty()) rows.add(new ButtonRowList.Row().text("§8" + I18n.get(K + "no_fields"), 0, 8));
            list.setRows(rows);
            addRenderableWidget(list);
        }

        int btnY = this.height - 30;
        saveButton = Button.builder(Component.translatable(K + "btn_save"), btn -> save())
                .pos(this.width / 2 - 124, btnY).size(120, 20).build(SkinnedButton.named("save"));
        refreshSaveButton();
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.literal(SaveUi.closeLabel(confirmDiscard, K + "btn_close")),
                        btn -> close())
                .pos(this.width / 2 + 4, btnY).size(120, 20)
                .tooltip(SaveUi.closeTooltip(confirmDiscard))
                .build(SkinnedButton.named("close")));
    }

    private EditBox lineBox(CharacterSheetManager.Field f, int width) {
        EditBox box = new SkinnedEditBox(this.font, 0, 0, width, 18, Component.literal(fieldLabel(f))).skin("line");
        box.setMaxLength(CharacterSheetManager.MAX_LINE_LENGTH);
        box.setValue(values.getOrDefault(f.id(), ""));
        String hintKey = K + "hint." + f.id();
        if (I18n.exists(hintKey)) box.setHint(Component.literal("§8" + I18n.get(hintKey)));
        box.setResponder(v -> {
            values.put(f.id(), v);
            confirmDiscard = false;
            refreshSaveButton();
            refreshNickButton();
        });
        return box;
    }

    private Button textButton(CharacterSheetManager.Field f, int width) {
        String value = values.getOrDefault(f.id(), "");
        String first = value.isEmpty() ? "§8" + I18n.get(K + "empty")
                : ColorHelper.stripAll(value.split("\n", 2)[0]);
        String label = "§e✎ §f" + UiLayout.fit(this.font, first, width - 50)
                + (value.isEmpty() ? "" : " §8(" + value.length() + ")");
        return Button.builder(Component.literal(label), btn -> Minecraft.getInstance().setScreen(
                        new SheetTextEditorScreen(this, fieldLabel(f), value, maxText, edited -> {
                            values.put(f.id(), edited);
                            confirmDiscard = false;
                        })))
                .size(width, 20)
                .tooltip(value.isEmpty() ? null : Tooltip.create(net.rp.rpessentials.util.RpText.toComponent(
                        value.length() > 600 ? value.substring(0, 600) + "…" : value)))
                .build(SkinnedButton.named("edit_text"));
    }

    /** Enables Save as soon as something changed, without rebuilding (the focused field keeps its cursor). */
    private void refreshSaveButton() {
        SaveUi.refresh(saveButton, I18n.get(K + "btn_save"), loaded && enabled && dirty());
    }

    private void save() {
        Map<String, String> out = new LinkedHashMap<>();
        for (CharacterSheetManager.Field f : fields) out.put(f.id(), values.getOrDefault(f.id(), ""));
        PacketDistributor.sendToServer(new SaveCharacterSheetPacket(target, targetName, out));
    }

    private void close() {
        if (dirty() && !confirmDiscard) {
            confirmDiscard = true;
            rebuildWidgets();
            return;
        }
        Minecraft.getInstance().setScreen(back);
    }

    @Override
    public void onClose() { close(); }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0xAA000000);
        renderScreen(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFD700);
        String status = !loaded ? I18n.get(K + "loading")
                : !enabled ? I18n.get(K + "disabled")
                : dirty() ? I18n.get(K + "unsaved") : I18n.get(K + "staff_only");
        g.drawCenteredString(this.font, "§7" + status, this.width / 2, 22, 0xAAAAAA);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
