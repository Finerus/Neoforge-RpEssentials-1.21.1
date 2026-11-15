package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.config.ConfigInspector;
import net.rp.rpessentials.network.ConfigFileEntriesPacket;
import net.rp.rpessentials.network.ConfigGuiFilesPacket;
import net.rp.rpessentials.network.RequestConfigFilePacket;
import net.rp.rpessentials.network.SaveConfigEntriesPacket;

import java.util.*;

/**
 * Dynamic Config Manager GUI.
 * <p>
 * Layout:
 *  ┌──────────────┬──────────────────────────────────────────────┐
 *  │ File panel   │ Entries panel (scrollable)                   │
 *  │ ─────────── │ ─────────────────────────────────────────────│
 *  │ Core         │  [Section Header]                            │
 *  │ Chat         │  entryKey  [widget]  (i) comment indicator   │
 *  │ Schedule     │  entryKey  [widget]                          │
 *  │ Moderation   │  ...                                         │
 *  │ Professions  │ ─────────────────────────────────────────────│
 *  │ Messages     │  [Apply Changes]  [Discard]  [↑/↓ scroll]   │
 *  └──────────────┴──────────────────────────────────────────────┘
 * <p>
 * Scrolling: the file list and the entries are Minecraft list widgets (ButtonRowList).
 * Rebuilds widgets on scroll, file switch, or after server response.
 * <p>
 * Pending changes are stored in a Map<path, newValue> and highlighted in yellow.
 * "Apply" sends SaveConfigEntriesPacket; server responds with updated entries.
 */
@OnlyIn(Dist.CLIENT)
public class ConfigManagerScreen extends Screen {

    // =========================================================================
    // LAYOUT CONSTANTS
    // =========================================================================
    private static final int FILE_PANEL_W = 90;
    private static final int MARGIN       = 6;
    private static final int PANEL_TOP    = 16;
    private static final int FOOTER_H     = 30;

    /** Pixel height of each row of the entries list. */
    private static final int ENTRY_H   = 22;

    /** Widget widths for value editors. */
    private static final int BOOL_W    = 54;
    private static final int NUMERIC_W = 90;
    private static final int STRING_W  = 200;

    // =========================================================================
    // STATE
    // =========================================================================
    /** Config file list received from server. */
    private final List<ConfigGuiFilesPacket.FileEntry> files;

    /** Currently selected file id. null until first selection. */
    private String selectedFileId = null;

    /** Entries for the currently selected file. Null while loading. */
    private List<ConfigFileEntriesPacket.EntryTransfer> currentEntries = null;

    /** Pending edits: fullPath → new serialized value. Highlighted in yellow. */
    private final Map<String, String> pendingChanges = new LinkedHashMap<>();

    /** Current value of each EditBox, keyed by fullPath. */
    private final Map<String, String> editBoxValues  = new LinkedHashMap<>();

    /** "Loading…" state while waiting for server response. */
    private boolean loading = false;

    private boolean pendingRebuild = false;
    private int pendingRemoveIdx = -1;
    private Button applyButton;
    /** Close was clicked once with unapplied changes: the next click (or Escape) closes. */
    private boolean confirmClose = false;
    /** File clicked once while changes are not applied: clicking it again opens it (changes dropped). */
    private String confirmFile = null;
    private Button discardButton;
    /** Reset button clicked once (path of its option): the next click puts the default value back. */
    private String pendingResetPath = null;
    /** Discard clicked once: the next click drops every change. */
    private boolean confirmDiscard = false;

    private record ModHeader(int y, String name, int color) {}
    private final List<ModHeader> modHeaders = new ArrayList<>();
    /** File list: Minecraft list widget, kept between rebuilds (scroll position). */
    private ButtonRowList fileList;

    /** Search: text typed, the field (kept between rebuilds so it keeps focus), and entries of every file. */
    private String searchQuery = "";
    private net.minecraft.client.gui.components.EditBox searchBox;
    private final Map<String, List<ConfigFileEntriesPacket.EntryTransfer>> fileCache = new java.util.HashMap<>();
    private boolean prefetched = false;
    /** Entries of the selected file: Minecraft list widget, kept between rebuilds (scroll position). */
    private ButtonRowList entriesList;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public ConfigManagerScreen(List<ConfigGuiFilesPacket.FileEntry> files) {
        super(Component.translatable("rpessentials.gui.config.title"));
        this.files = files;
        // Auto-select first file
        if (!files.isEmpty()) {
            requestFile(files.get(0).id());
        }
    }

    // =========================================================================
    // PACKET CALLBACK — called by ConfigFileEntriesPacket handler
    // =========================================================================

    public void onFileDataReceived(ConfigFileEntriesPacket packet) {
        fileCache.put(packet.fileId(), packet.entries());
        if (selectedFileId != null && !packet.fileId().equals(selectedFileId)) {
            // Another file, fetched for the search: only the match counts change.
            rebuildKeepingSearchFocus();
            return;
        }
        selectedFileId  = packet.fileId();
        currentEntries  = packet.entries();
        loading         = false;
        if (entriesList != null) entriesList.setScrollAmount(0);
        // Preserve editBox values for entries that were already being edited
        // (happens on refresh after apply — keep any unsaved changes)
        // We only clear editBox values for entries that DON'T have pending changes
        for (ConfigFileEntriesPacket.EntryTransfer e : currentEntries) {
            if (!pendingChanges.containsKey(e.fullPath())) {
                editBoxValues.remove(e.fullPath());
            }
        }
        clearWidgets();
        init();
    }

    // =========================================================================
    // INIT — builds all visible widgets
    // =========================================================================

    @Override
    protected void init() {
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));

        buildSearchBox();
        buildFilePanel();
        if (loading) {
            buildLoadingPanel();
        } else if (currentEntries != null) {
            buildEntriesPanel();
        }
        buildFooter();
    }

    // ── File panel (left side) ─────────────────────────────────────────────
    private void buildFilePanel() {
        modHeaders.clear();
        if (fileList == null) fileList = new ButtonRowList(Minecraft.getInstance(), 18);
        fileList.layout(MARGIN - 2, PANEL_TOP + 14, FILE_PANEL_W + 12, this.height - 10 - (PANEL_TOP + 14));
        int rw = fileList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();
        String lastMod = null;
        for (ConfigGuiFilesPacket.FileEntry file : files) {
            if (!file.mod().isEmpty() && !file.mod().equals(lastMod)) {
                lastMod = file.mod();
                int color = file.modColor() == 0 ? 0xFFD700 : file.modColor();
                net.minecraft.client.gui.components.StringWidget title = new net.minecraft.client.gui.components.StringWidget(
                        rw, 16, Component.literal(this.font.plainSubstrByWidth(file.mod(), rw)).withStyle(s -> s.withColor(color)), this.font);
                title.alignLeft();
                rows.add(new ButtonRowList.Row().add(title, 2));
            }
            final String fid = file.id();
            final boolean selected = fid.equals(selectedFileId);
            int matches = searching() ? matchCount(fid) : -1;
            String count = !searching() ? "" : matches < 0 ? " §8…" : " §8(" + matches + ")";
            boolean dim = searching() && matches == 0;
            Component label = dim
                    ? Component.literal((selected ? "§8▶ " : "§8  ") + file.displayName() + count)
                    : file.color() == 0
                    ? Component.literal((selected ? "§e§l▶ " : "§7  ") + file.displayName() + count)
                    : Component.literal((selected ? "▶ " : "  ") + file.displayName()).withStyle(s -> s.withColor(file.color()).withBold(selected))
                            .append(Component.literal(count));
            boolean armed = fid.equals(confirmFile);
            if (armed) label = Component.literal(UiLayout.fit(this.font,
                    "§c" + I18n.get("rpessentials.gui.unsaved.discard_switch"), rw - 6));
            rows.add(new ButtonRowList.Row().add(Button.builder(label, btn -> {
                        if (!pendingChanges.isEmpty() && !fid.equals(selectedFileId) && !fid.equals(confirmFile)) {
                            confirmFile = fid;
                            rebuild();
                            return;
                        }
                        confirmFile = null;
                        requestFile(fid);
                    })
                    .tooltip(armed ? Tooltip.create(Component.translatable("rpessentials.gui.unsaved.discard_switch_tooltip")) : null)
                    .size(rw, 16).build(SkinnedButton.row(selected)), 0));
        }
        fileList.setRows(rows);
        addRenderableWidget(fileList);
    }

    private ConfigGuiFilesPacket.FileEntry selectedEntry() {
        for (ConfigGuiFilesPacket.FileEntry f : files) if (f.id().equals(selectedFileId)) return f;
        return null;
    }

    // ── Loading placeholder ────────────────────────────────────────────────
    private void buildLoadingPanel() {
        int cx = (FILE_PANEL_W + MARGIN * 3 + this.width) / 2;
        int cy = this.height / 2;
        // No widget needed — text drawn in render()
        // Just add a cancel button
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.config.btn_refresh"),
                        btn -> refreshFile())
                .pos(cx - 30, cy + 20).size(60, 18).build(SkinnedButton::new));
    }

    // ── Entries panel (right side, scrollable) ─────────────────────────────

    private void buildEntriesPanel() {
        if (currentEntries == null || currentEntries.isEmpty()) return;

        int formX  = FILE_PANEL_W + MARGIN * 3;
        int formW  = this.width - formX - MARGIN * 2;
        int startY = PANEL_TOP + 20;
        if (entriesList == null) entriesList = new ButtonRowList(Minecraft.getInstance(), ENTRY_H);
        entriesList.layout(formX - 6, startY, formW + 12, this.height - FOOTER_H - 4 - startY);
        int rw = entriesList.rowWidth();

        List<ButtonRowList.Row> rows = new ArrayList<>();
        List<ConfigFileEntriesPacket.EntryTransfer> shown = filtered(currentEntries);
        if (shown.isEmpty()) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get("rpessentials.gui.config.search_none"), 4, 7));
        }
        for (ConfigFileEntriesPacket.EntryTransfer entry : shown) {
            ButtonRowList.Row row = new ButtonRowList.Row();
            if (entry.isSection()) {
                row.block("section_header", 0xBB1A1A1A, 0, 1)
                        .text(Component.literal(formatSectionName(entry.key()))
                                .withStyle(s -> s.withBold(true).withColor(sectionColor())).getVisualOrderText(), 4, 7);
                rows.add(row);
                continue;
            }
            boolean isPending = pendingChanges.containsKey(entry.fullPath());
            // A value the option does not accept (typo in the file, or typed here): the row turns red.
            String problem = problemOf(entry, editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue()));
            row.block(GuiSkin.LIST_ROW, problem != null ? 0x55FF3333 : isPending ? 0x33FFAA00 : 0x22FFFFFF, 0, 1);

            // Key label and ℹ icon; hovering either shows the comment of the option
            // (and what is expected when the value is wrong).
            String keyLabel = (problem != null ? "⚠ " : "") + formatKeyName(entry.key());
            int labelColor = problem != null ? 0xFF6666 : isPending ? 0xFFDD44 : 0xCCCCCC;
            net.minecraft.client.gui.components.StringWidget label = new net.minecraft.client.gui.components.StringWidget(
                    this.font.width(keyLabel), 9, Component.literal(keyLabel).withStyle(s -> s.withColor(labelColor)), this.font);
            Tooltip tip = null;
            if (problem != null) {
                net.minecraft.network.chat.MutableComponent text = Component.translatable(
                        "rpessentials.gui.config.invalid_tooltip", problem).withStyle(net.minecraft.ChatFormatting.RED);
                if (!entry.comment().isBlank()) {
                    text.append(Component.literal("\n\n" + entry.comment()).withStyle(net.minecraft.ChatFormatting.WHITE));
                }
                tip = Tooltip.create(text);
            } else if (!entry.comment().isBlank()) {
                tip = Tooltip.create(Component.literal(entry.comment()));
            }
            label.setTooltip(tip);
            row.add(label, 18, 6);
            int infoX = 18 + this.font.width(keyLabel) + 4;
            if (!entry.comment().isBlank()) {
                net.minecraft.client.gui.components.StringWidget info = new net.minecraft.client.gui.components.StringWidget(
                        this.font.width("ℹ"), 9, Component.literal("ℹ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), this.font);
                info.setTooltip(tip);
                row.add(info, infoX, 6);
                infoX += 10;
            }
            if (entry.hasRange()) {
                row.text("§8[" + formatNum(entry.rangeMin()) + " – " + formatNum(entry.rangeMax()) + "]", infoX, 6);
            }

            // Value widget(s), built at row coordinates (0, 0) then placed in the row.
            List<net.minecraft.client.gui.components.AbstractWidget> widgets = new ArrayList<>();
            buildEntryWidget(entry, 0, rw, 0, widgets);
            for (net.minecraft.client.gui.components.AbstractWidget w : widgets) row.add(w, w.getX(), w.getY());
            rows.add(row);
        }
        entriesList.setRows(rows);
        addRenderableWidget(entriesList);
    }

    /**
     * Creates the appropriate widget for one entry row, positioned at (formX, y).
     * The key label and comment indicator are drawn in render() for performance.
     */
    private void buildEntryWidget(ConfigFileEntriesPacket.EntryTransfer entry,
                                  int formX, int formW, int y,
                                  List<net.minecraft.client.gui.components.AbstractWidget> out) {
        String currentEdit = editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue());
        int widgetY = y + 2;

        switch (entry.type()) {
            case BOOLEAN -> {
                // Toggle button
                boolean boolVal = "true".equalsIgnoreCase(currentEdit);
                String label = boolVal
                        ? I18n.get("rpessentials.gui.config.bool_on")
                        : I18n.get("rpessentials.gui.config.bool_off");
                out.add(Button.builder(Component.literal(label), btn -> {
                    boolean cur = "true".equalsIgnoreCase(
                            editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue()));
                    String newVal = cur ? "false" : "true";
                    markChange(entry.fullPath(), newVal, entry.currentValue());
                    rebuild();
                }).pos(formX + formW - BOOL_W - 2, widgetY).size(BOOL_W, 16).build(SkinnedButton::new));
            }

            case INT, LONG, DOUBLE -> {
                // Numeric EditBox
                int w = Math.min(NUMERIC_W, formW / 3);
                EditBox box = new SkinnedEditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(32);
                box.setValue(currentEdit);
                box.setTextColor(textColor(entry, currentEdit));
                box.setResponder(val -> {
                    markChange(entry.fullPath(), val, entry.currentValue());
                    box.setTextColor(textColor(entry, val));
                });
                out.add(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }

            case STRING -> {
                // String EditBox — wider
                int w = Math.min(STRING_W, formW - labelWidth(entry.key()) - 12);
                w = Math.max(w, 80);
                EditBox box = new SkinnedEditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(512);
                box.setValue(currentEdit);
                box.setTextColor(textColor(entry, currentEdit));
                box.setResponder(val -> {
                    markChange(entry.fullPath(), val, entry.currentValue());
                    box.setTextColor(textColor(entry, val));
                });
                out.add(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }

            case LIST_STRING, LIST_INT -> {
                int btnW = 40;
                int boxW = Math.max(formW - labelWidth(entry.key()) - btnW - 16, 80);
                int boxX = formX + formW - boxW - btnW - 4;

                EditBox box = new SkinnedEditBox(this.font, boxX, widgetY, boxW, 16, Component.literal(entry.key()));
                box.setMaxLength(8192);
                box.setValue(currentEdit.replace("\n", " | "));
                box.setEditable(false);
                out.add(box);
                editBoxValues.put(entry.fullPath(), currentEdit);

                final String path = entry.fullPath();
                final String entryKey = entry.key();
                final String original = entry.currentValue();
                out.add(Button.builder(Component.literal(
                                        I18n.get("rpessentials.gui.config.edit_list")),
                                btn -> openListEditor(path, entryKey, currentEdit, original))
                        .pos(boxX + boxW + 2, widgetY).size(btnW, 16).build(SkinnedButton::new));
            }

            default -> {
                // UNKNOWN: render as string EditBox
                int w = Math.min(STRING_W, formW / 2);
                EditBox box = new SkinnedEditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(512);
                box.setValue(currentEdit);
                box.setTextColor(textColor(entry, currentEdit));
                box.setResponder(val -> {
                    markChange(entry.fullPath(), val, entry.currentValue());
                    box.setTextColor(textColor(entry, val));
                });
                out.add(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }
        }

        // ── "Reset to default" button ────────────────────────────────────────
        if (!entry.defaultValue().isBlank()
                && !entry.isSection()
                && entry.type() != ConfigInspector.ValueType.UNKNOWN) {
            String def = entry.defaultValue();
            boolean differs = !editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue()).equals(def);
            // Two clicks: the first one arms the button ("!"), the second one puts the default value back.
            boolean armed = entry.fullPath().equals(pendingResetPath);
            out.add(Button.builder(Component.literal(armed ? "§c!" : differs ? "§e↺" : "§8↺"),
                            btn -> {
                                if (!differs) return;
                                if (!entry.fullPath().equals(pendingResetPath)) {
                                    pendingResetPath = entry.fullPath();
                                    rebuild();
                                    return;
                                }
                                pendingResetPath = null;
                                markChange(entry.fullPath(), def, entry.currentValue());
                                editBoxValues.put(entry.fullPath(), def);
                                rebuild();
                            })
                    .pos(formX + 2, y + 2).size(14, 16)
                    .tooltip(Tooltip.create(Component.translatable(armed
                            ? "rpessentials.gui.config.reset_confirm" : "rpessentials.gui.config.reset_tooltip", def)))
                    .build(SkinnedButton::new));
        }
    }

    // ── Scroll buttons ─────────────────────────────────────────────────────



    // ── Footer (Apply / Discard buttons) ──────────────────────────────────

    private void buildFooter() {
        int y     = this.height - FOOTER_H + 3;
        int formX = FILE_PANEL_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        int closeW = 52;
        // Apply | Discard | Refresh ............ Close
        int[] r = UiLayout.row(formX, formW - closeW - UiLayout.GAP * 2, UiLayout.GAP, 160, 60, 20);

        // Apply button
        applyButton = Button.builder(Component.literal(""), btn -> {
            if (!pendingChanges.isEmpty()) applyChanges();
        }).pos(r[0], y).size(r[1], 20).build(SkinnedButton::new);
        applyButton.active = !pendingChanges.isEmpty();
        addRenderableWidget(applyButton);

        // Discard
        // Discard asks once too: it drops every change typed.
        discardButton = Button.builder(Component.literal(confirmDiscard
                        ? "§c" + I18n.get("rpessentials.gui.config.btn_discard_confirm")
                        : I18n.get("rpessentials.gui.config.btn_discard")), btn -> {
            if (!confirmDiscard) {
                confirmDiscard = true;
                rebuild();
                return;
            }
            confirmDiscard = false;
            pendingChanges.clear();
            editBoxValues.clear();
            rebuild();
        }).pos(r[2], y).size(r[3], 20)
                .tooltip(confirmDiscard ? Tooltip.create(Component.translatable("rpessentials.gui.config.btn_discard_confirm_tooltip")) : null)
                .build(SkinnedButton::new);
        discardButton.active = !pendingChanges.isEmpty();
        addRenderableWidget(discardButton);

        addRenderableWidget(Button.builder(Component.literal("§e↻"), btn -> refreshFile())
                .pos(r[4], y).size(r[5], 20)
                .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")))
                .build(SkinnedButton::new));

        // Close (asks again when changes are not applied)
        String closeLabel = SaveUi.closeLabel(confirmClose, "rpessentials.gui.config.btn_close");
        int cw = Math.max(closeW, this.font.width(closeLabel) + 10);
        addRenderableWidget(Button.builder(Component.literal(closeLabel), btn -> onClose())
                .pos(formX + formW - cw, y).size(cw, 20)
                .tooltip(SaveUi.closeTooltip(confirmClose))
                .build(SkinnedButton::new));
    }

    // =========================================================================
    // RENDER
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean hasChanges = !pendingChanges.isEmpty();
        if (applyButton != null) applyButton.active = hasChanges;
        if (discardButton != null) discardButton.active = hasChanges;

        if (pendingRebuild) {
            pendingRebuild = false;
        }
        // Dim background
        GuiSkin.background(g, this.width, this.height, 0x99000000);

        // ── File panel background ────────────────────────────────────────────
        int panelRight = FILE_PANEL_W + MARGIN * 2;
        GuiSkin.panel(g, MARGIN - 2, PANEL_TOP, panelRight + 2, this.height - 6, 0xBB111111);
        GuiSkin.accent(g, MARGIN - 2, PANEL_TOP, panelRight + 2, PANEL_TOP + 2, modBarColor());
        g.drawString(this.font, UiLayout.fit(this.font, I18n.get("rpessentials.gui.config.files_header"), FILE_PANEL_W + 6),
                MARGIN + 2, PANEL_TOP + 4, 0xFFD700, false);
        for (ModHeader h : modHeaders) {
            g.drawString(this.font, this.font.plainSubstrByWidth(h.name(), FILE_PANEL_W),
                    MARGIN + 2, h.y() + 2, h.color(), false);
        }

        // ── Entries panel background ─────────────────────────────────────────
        int formX = FILE_PANEL_W + MARGIN * 3;
        GuiSkin.panel(g, formX - 2, PANEL_TOP, this.width - MARGIN + 2, this.height - 6, 0xBB111111);
        GuiSkin.accent(g, formX - 2, PANEL_TOP, this.width - MARGIN + 2, PANEL_TOP + 2, accentColor());

        // Title + file name
        String title = selectedFileId != null
                ? I18n.get("rpessentials.gui.config.title_with_file", selectedFileId)
                : I18n.get("rpessentials.gui.config.title");
        int titleW = (searchBox != null ? searchBox.getX() : this.width - MARGIN) - formX - UiLayout.GAP;
        g.drawString(this.font, UiLayout.fit(this.font, title, titleW), formX, PANEL_TOP + 5, titleColor(), false);

        // Pending indicator
        if (!pendingChanges.isEmpty()) {
            g.drawString(this.font, I18n.get("rpessentials.gui.config.unsaved", pendingChanges.size()),
                    formX, PANEL_TOP + 14, 0xFFDD44, false);
        }

        // ── Entries ──────────────────────────────────────────────────────────

        if (loading) {
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.config.loading"),
                    (formX + this.width) / 2, this.height / 2 - 10, 0x888888);
        } else if (currentEntries != null) {
            // Entries: list widget, drawn with the other widgets.
        } else {
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.config.select_file"),
                    (formX + this.width) / 2, this.height / 2, 0x666666);
        }

        // Footer separator
        GuiSkin.separator(g, formX - 2, this.height - FOOTER_H, this.width - MARGIN + 2,
                this.height - FOOTER_H + 1, 0xFF444444);

        if (applyButton != null) {
            // Bold when there is something to apply, like every save button.
            String applyLabel = !pendingChanges.isEmpty()
                    ? "§l" + I18n.get("rpessentials.gui.config.btn_apply", pendingChanges.size())
                    : I18n.get("rpessentials.gui.config.btn_no_change");
            if (!applyButton.getMessage().getString().equals(applyLabel)) applyButton.setMessage(Component.literal(applyLabel));
        }
        if (confirmClose && pendingChanges.isEmpty()) { confirmClose = false; rebuild(); }
        if (confirmFile != null && pendingChanges.isEmpty()) { confirmFile = null; rebuild(); }
        if (confirmDiscard && pendingChanges.isEmpty()) { confirmDiscard = false; rebuild(); }

        // Render all widgets (EditBox, Button…)
        super.render(g, mouseX, mouseY, delta);

    }




    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Intentionally empty — we draw our own background in render()
    }

    @Override
    public void onClose() {
        if (!pendingChanges.isEmpty() && !confirmClose) {
            confirmClose = true;
            rebuild();
            return;
        }
        super.onClose();
    }

    // =========================================================================
    // MOUSE WHEEL SCROLL
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int formX = FILE_PANEL_W + MARGIN * 3;
        
        
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    // ── Search ──────────────────────────────────────────────────────────────
    private boolean searching() {
        return !searchQuery.isBlank();
    }

    private void buildSearchBox() {
        int searchW = Math.min(150, (this.width - FILE_PANEL_W - MARGIN * 5) / 2);
        int x = this.width - MARGIN - 2 - searchW;
        if (searchBox == null) {
            searchBox = new net.rp.rpessentials.client.skin.SkinnedEditBox(this.font, x, PANEL_TOP + 3, searchW, 14,
                    Component.translatable("rpessentials.gui.config.search_hint"));
            searchBox.setHint(Component.literal(UiLayout.fit(this.font, "§7" + I18n.get("rpessentials.gui.config.search_hint"), searchW - 8)));
            searchBox.setTooltip(Tooltip.create(Component.translatable("rpessentials.gui.config.search_tooltip")));
            searchBox.setMaxLength(64);
            searchBox.setResponder(val -> {
                searchQuery = val;
                if (searching() && !prefetched) prefetchAllFiles();
                if (entriesList != null) entriesList.setScrollAmount(0);
                rebuildKeepingSearchFocus();
            });
        } else {
            searchBox.setX(x);
            searchBox.setWidth(searchW);
        }
        addRenderableWidget(searchBox);
    }

    /** Asks the server for every file once, so the file list can show how many options match. */
    private void prefetchAllFiles() {
        prefetched = true;
        for (ConfigGuiFilesPacket.FileEntry f : files) {
            if (!fileCache.containsKey(f.id()) && !f.id().equals(selectedFileId)) {
                PacketDistributor.sendToServer(new RequestConfigFilePacket(f.id()));
            }
        }
    }

    private void rebuildKeepingSearchFocus() {
        boolean focused = searchBox != null && searchBox.isFocused();
        rebuild();
        if (focused) setFocused(searchBox);
    }

    private boolean matches(ConfigFileEntriesPacket.EntryTransfer e) {
        String q = searchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        return e.key().toLowerCase(java.util.Locale.ROOT).contains(q)
                || formatKeyName(e.key()).toLowerCase(java.util.Locale.ROOT).contains(q)
                || e.comment().toLowerCase(java.util.Locale.ROOT).contains(q)
                || e.currentValue().toLowerCase(java.util.Locale.ROOT).contains(q);
    }

    /** Matching options of a file, -1 while it has not been received yet. */
    private int matchCount(String fileId) {
        List<ConfigFileEntriesPacket.EntryTransfer> entries =
                fileId.equals(selectedFileId) && currentEntries != null ? currentEntries : fileCache.get(fileId);
        if (entries == null) return -1;
        int n = 0;
        for (var e : entries) if (!e.isSection() && matches(e)) n++;
        return n;
    }

    /** Entries shown: all of them, or the matching options with their section titles. */
    private List<ConfigFileEntriesPacket.EntryTransfer> filtered(List<ConfigFileEntriesPacket.EntryTransfer> all) {
        if (!searching()) return all;
        List<ConfigFileEntriesPacket.EntryTransfer> out = new ArrayList<>();
        ConfigFileEntriesPacket.EntryTransfer section = null;
        for (var e : all) {
            if (e.isSection()) { section = e; continue; }
            if (!matches(e)) continue;
            if (section != null) { out.add(section); section = null; }
            out.add(e);
        }
        return out;
    }

    private void requestFile(String fileId) {
        if (fileId.equals(selectedFileId) && currentEntries != null) return;
        selectedFileId  = fileId;
        currentEntries  = null;
        loading         = true;
        if (entriesList != null) entriesList.setScrollAmount(0);
        pendingChanges.clear();
        editBoxValues.clear();
        PacketDistributor.sendToServer(new RequestConfigFilePacket(fileId));
        rebuild();
    }

    private void markChange(String fullPath, String newValue, String originalValue) {
        if (newValue.equals(originalValue) && !pendingChanges.containsKey(fullPath)) return;
        if (newValue.equals(originalValue)) {
            pendingChanges.remove(fullPath);
        } else {
            pendingChanges.put(fullPath, newValue);
        }
        editBoxValues.put(fullPath, newValue);

        pendingRebuild = true;
    }

    /**
     * What the option expects when {@code value} is not accepted (wrong word, not a number, out of range,
     * not true / false), null when the value is fine.
     */
    private static String problemOf(ConfigFileEntriesPacket.EntryTransfer e, String value) {
        if (value == null || e.isSection()) return null;
        String v = value.trim();
        switch (e.type()) {
            case BOOLEAN -> { return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false") ? null : "true / false"; }
            case INT, LONG, DOUBLE -> {
                String range = e.hasRange() ? formatNum(e.rangeMin()) + " – " + formatNum(e.rangeMax()) : null;
                double d;
                try {
                    d = e.type() == ConfigInspector.ValueType.DOUBLE ? Double.parseDouble(v) : Long.parseLong(v);
                } catch (NumberFormatException ex) {
                    return range != null ? range : I18n.get("rpessentials.gui.config.expected_number");
                }
                return e.hasRange() && (d < e.rangeMin() || d > e.rangeMax()) ? range : null;
            }
            case LIST_STRING, LIST_INT -> { return null; }
            default -> { return net.rp.rpessentials.config.ConfigRules.problem(e.fullPath(), value); }
        }
    }

    private static int textColor(ConfigFileEntriesPacket.EntryTransfer e, String value) {
        return problemOf(e, value) != null ? 0xFF5555 : 0xE0E0E0;
    }

    private void applyChanges() {
        if (selectedFileId == null || pendingChanges.isEmpty()) return;
        // Nothing is sent while a value is wrong: its row turns red and the chat says how many to fix.
        if (currentEntries != null) {
            int wrong = 0;
            for (ConfigFileEntriesPacket.EntryTransfer e : currentEntries) {
                String pending = pendingChanges.get(e.fullPath());
                if (pending != null && problemOf(e, pending) != null) wrong++;
            }
            if (wrong > 0) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("rpessentials.gui.config.invalid_values", wrong)
                                    .withStyle(net.minecraft.ChatFormatting.RED), false);
                }
                rebuild();
                return;
            }
        }
        Map<String, String> batch = new HashMap<>();
        List<String> sent = new ArrayList<>();
        int size = 0;
        int tooLong = 0;
        for (Map.Entry<String, String> e : pendingChanges.entrySet()) {
            if (e.getValue().length() > 8000) { tooLong++; continue; }
            int entrySize = e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 10;
            if (!batch.isEmpty() && size + entrySize > 20000) {
                PacketDistributor.sendToServer(new SaveConfigEntriesPacket(selectedFileId, batch));
                batch = new HashMap<>();
                size = 0;
            }
            batch.put(e.getKey(), e.getValue());
            sent.add(e.getKey());
            size += entrySize;
        }
        if (!batch.isEmpty()) PacketDistributor.sendToServer(new SaveConfigEntriesPacket(selectedFileId, batch));
        sent.forEach(pendingChanges::remove);
        if (tooLong > 0 && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("rpessentials.gui.config.too_long", tooLong), false);
        }
    }

    /**
     * Opens the ListEditorSubScreen for a LIST_STRING entry.
     * The sub-screen calls back via onListEdited().
     */
    private void openListEditor(String fullPath, String key, String current, String original) {
        Minecraft.getInstance().setScreen(new ListEditorSubScreen(this, fullPath, key, current, original));
    }

    /** Called by ListEditorSubScreen when the user saves the list. */
    public void onListEdited(String fullPath, String newCommaValue, String originalValue) {
        markChange(fullPath, newCommaValue, originalValue);
        editBoxValues.put(fullPath, newCommaValue);
        Minecraft.getInstance().setScreen(this);
        rebuild();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }

    /**
     * Computes which entry indices are visible given the available pixel height.
     * Returns {firstVisible, lastVisible}.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        
        return super.mouseReleased(mx, my, button);
    }

    private int labelWidth(String key) {
        return this.font.width(formatKeyName(key)) + 20;
    }

    /**
     * Converts camelCase/snake_case config keys to human-readable labels.
     * e.g. "enableBlur" → "Enable Blur", "worldBorderDistance" → "World Border Distance"
     */
    private static String formatKeyName(String key) {
        if (key == null || key.isEmpty()) return key;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : key.toCharArray()) {
            if (c == '_' || c == '.') {
                sb.append(' ');
                nextUpper = true;
            } else if (Character.isUpperCase(c) && !nextUpper) {
                sb.append(' ').append(c);
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Section names from the spec path (e.g. "Obfuscation Settings" → "Obfuscation Settings").
     * Preserves spaces that come from NeoForge's push("Section Name") calls.
     */
    private static String formatSectionName(String name) {
        if (name == null) return "";
        // The raw key from ModConfigSpec paths uses the exact string passed to push()
        return name;
    }

    /** Returns only the first line of a multi-line comment. */
    private static String firstLine(String comment) {
        if (comment == null || comment.isBlank()) return "";
        int nl = comment.indexOf('\n');
        return nl >= 0 ? comment.substring(0, nl) : comment;
    }

    /** Formats a double as int if it has no fractional part, otherwise 2 decimals. */
    private static String formatNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.2f", d);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private int accentColor() {
        ConfigGuiFilesPacket.FileEntry f = selectedEntry();
        if (f != null) {
            if (f.accent() != 0) return 0xFF000000 | f.accent();
            if (f.color() != 0) return 0xFF000000 | f.color();
        }
        return 0xFF8B6914;
    }

    private int titleColor() {
        ConfigGuiFilesPacket.FileEntry f = selectedEntry();
        return f != null && f.title() != 0 ? f.title() : 0xFFD700;
    }

    private int sectionColor() {
        ConfigGuiFilesPacket.FileEntry f = selectedEntry();
        return f != null && f.section() != 0 ? f.section() : 0xFFD700;
    }

    private int modBarColor() {
        ConfigGuiFilesPacket.FileEntry f = selectedEntry();
        return f != null && f.modColor() != 0 ? 0xFF000000 | f.modColor() : 0xFF8B6914;
    }

    // =========================================================================
    // LIST EDITOR SUB-SCREEN
    // =========================================================================
    /**
     * Sub-screen for editing LIST_STRING values as individual lines.
     * Each line = one list element. The user can add, remove, and reorder entries.
     */
    @OnlyIn(Dist.CLIENT)
    private static class ListEditorSubScreen extends Screen {

        private final ConfigManagerScreen parent;
        private final String              fullPath;
        private final String              originalComma;
        private final List<String>        items;
        private int pendingRemoveIdx = -1;
        private ButtonRowList list;

        /** Adds a widget built at screen x to a list row, relative to the row (originX = left of the row). */
        private static void rowAdd(ButtonRowList.Row row, int originX, net.minecraft.client.gui.components.AbstractWidget w) {
            row.add(w, w.getX() - originX, w.getY());
        }

        ListEditorSubScreen(ConfigManagerScreen parent, String fullPath, String key,
                            String rawValue, String originalValue) {
            super(Component.literal(I18n.get("rpessentials.gui.config.list.title",
                    ConfigManagerScreen.formatKeyName(key))));
            this.parent = parent;
            this.fullPath = fullPath;
            this.originalComma = originalValue;
            this.items = new ArrayList<>();
            for (String s : rawValue.split("\n")) {
                String t = s.trim();
                if (!t.isEmpty()) this.items.add(t);
            }
        }

        @Override
        protected void init() {
            int midX = this.width / 2;
            int startY = 40;
            int rowH = 22;
            // Minecraft list widget: one editable value per row, remove and move up / down buttons.
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), rowH);
            list.layout(midX - 166, startY, 338, this.height - 52 - startY);
            List<ButtonRowList.Row> listRows = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                final int idx = i;
                int rowY = 0;
                ButtonRowList.Row row = new ButtonRowList.Row();
                listRows.add(row);

                EditBox box = new SkinnedEditBox(this.font, midX - 160, rowY, 290, 18, Component.empty());
                box.setMaxLength(512);
                box.setValue(items.get(i));
                box.setResponder(val -> items.set(idx, val));
                rowAdd(row, midX - 160, box);

                boolean confirmRemove = pendingRemoveIdx == idx;
                rowAdd(row, midX - 160, Button.builder(Component.literal(confirmRemove ? "§c!" : "§c✗"),
                                btn -> {
                                    if (pendingRemoveIdx == idx) {
                                        pendingRemoveIdx = -1;
                                        items.remove(idx);
                                    } else {
                                        pendingRemoveIdx = idx;
                                    }
                                    rebuild();
                                })
                        .pos(midX + 134, rowY).size(16, 18).build(SkinnedButton::new));

                if (i > 0)
                    rowAdd(row, midX - 160, Button.builder(Component.literal("▲"),
                                    btn -> {
                                        pendingRemoveIdx = -1;
                                        Collections.swap(items, idx, idx - 1);
                                        rebuild();
                                    })
                            .pos(midX + 152, rowY).size(14, 8).build(SkinnedButton::new));

                if (i < items.size() - 1)
                    rowAdd(row, midX - 160, Button.builder(Component.literal("▼"),
                                    btn -> {
                                        pendingRemoveIdx = -1;
                                        Collections.swap(items, idx, idx + 1);
                                        rebuild();
                                    })
                            .pos(midX + 152, rowY + 10).size(14, 8).build(SkinnedButton::new));
            }

            list.setRows(listRows);
            addRenderableWidget(list);

            int btnY = this.height - 48;

            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_add"),
                            btn -> {
                                pendingRemoveIdx = -1;
                                items.add("");
                                rebuild();
                            })
                    .pos(midX - 170, btnY).size(110, 20).build(SkinnedButton::new));

            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_save"), btn -> {
                items.removeIf(String::isBlank);
                parent.onListEdited(fullPath, String.join("\n", items), originalComma);
            }).pos(midX - 55, btnY).size(110, 20).build(SkinnedButton::new));

            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_cancel"),
                            btn -> net.minecraft.client.Minecraft.getInstance().setScreen(parent))
                    .pos(midX + 59, btnY).size(70, 20).build(SkinnedButton::new));
        }

        private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            renderBackground(g, mx, my, delta);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFD700);
            g.drawCenteredString(this.font,
                    I18n.get("rpessentials.gui.config.list.entries", items.size()),
                    this.width / 2, 26, 0x666666);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    private void refreshFile() {
        if (selectedFileId == null) return;
        currentEntries = null;
        loading = true;
        if (entriesList != null) entriesList.setScrollAmount(0);
        pendingChanges.clear();
        editBoxValues.clear();
        PacketDistributor.sendToServer(new RequestConfigFilePacket(selectedFileId));
        rebuild();
    }
}