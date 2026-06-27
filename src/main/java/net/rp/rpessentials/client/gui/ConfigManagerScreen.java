package net.rp.rpessentials.client.gui;

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
 * Scrolling: managed by scrollOffset (entry index).
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
    private static final int FOOTER_H     = 28;

    /** Pixel height of each entry row (section: SECTION_H, normal: ENTRY_H). */
    private static final int SECTION_H = 14;
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

    /** How many entry rows are scrolled past the top. */
    private int scrollOffset = 0;

    /** "Loading…" state while waiting for server response. */
    private boolean loading = false;

    /**
     * Comment tooltip deferred rendering.
     * Set by renderEntries() when the mouse hovers the ℹ icon.
     * Actually drawn AFTER super.render() so it always appears above all widgets.
     */
    private String  deferredTooltipComment = null;
    private int     deferredTooltipX       = 0;
    private int     deferredTooltipY       = 0;

    private boolean pendingRebuild = false;

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
        selectedFileId  = packet.fileId();
        currentEntries  = packet.entries();
        loading         = false;
        scrollOffset    = 0;
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
        int x = MARGIN;
        int y = PANEL_TOP + 14;

        for (ConfigGuiFilesPacket.FileEntry file : files) {
            final String fid = file.id();
            boolean selected = fid.equals(selectedFileId);
            String label = selected ? "§e▶ " + file.displayName() : "§7  " + file.displayName();
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> requestFile(fid))
                    .pos(x, y).size(FILE_PANEL_W, 16).build());
            y += 18;
        }
    }

    // ── Loading placeholder ────────────────────────────────────────────────

    private void buildLoadingPanel() {
        int cx = (FILE_PANEL_W + MARGIN * 3 + this.width) / 2;
        int cy = this.height / 2;
        // No widget needed — text drawn in render()
        // Just add a cancel button
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.config.btn_close_loading"),
                        btn -> onClose())
                .pos(cx - 30, cy + 20).size(60, 18).build());
    }

    // ── Entries panel (right side, scrollable) ─────────────────────────────

    private void buildEntriesPanel() {
        if (currentEntries == null || currentEntries.isEmpty()) return;

        int formX         = FILE_PANEL_W + MARGIN * 3;
        int formW         = this.width - formX - MARGIN;
        int availableH    = this.height - PANEL_TOP - FOOTER_H - 24;
        int startY        = PANEL_TOP + 20;

        int[] visibleRange = computeVisibleRange(availableH, startY);
        int firstVisible   = visibleRange[0];
        int lastVisible    = visibleRange[1];

        int y = startY;

        for (int i = firstVisible; i <= lastVisible && i < currentEntries.size(); i++) {
            ConfigFileEntriesPacket.EntryTransfer entry = currentEntries.get(i);
            int rh = rowHeight(entry);

            if (entry.isSection()) {
                y += rh;
                continue;
            }

            buildEntryWidget(entry, formX, formW, y);
            y += rh;
        }

        buildScrollButtons(formX, formW, startY, availableH);
    }

    /**
     * Creates the appropriate widget for one entry row, positioned at (formX, y).
     * The key label and comment indicator are drawn in render() for performance.
     */
    private void buildEntryWidget(ConfigFileEntriesPacket.EntryTransfer entry,
                                  int formX, int formW, int y) {
        String currentEdit = editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue());
        int widgetY = y + 2;

        switch (entry.type()) {
            case BOOLEAN -> {
                // Toggle button
                boolean boolVal = "true".equalsIgnoreCase(currentEdit);
                String label = boolVal
                        ? I18n.get("rpessentials.gui.config.bool_on")
                        : I18n.get("rpessentials.gui.config.bool_off");
                addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                    boolean cur = "true".equalsIgnoreCase(
                            editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue()));
                    String newVal = cur ? "false" : "true";
                    markChange(entry.fullPath(), newVal, entry.currentValue());
                    rebuild();
                }).pos(formX + formW - BOOL_W - 2, widgetY).size(BOOL_W, 16).build());
            }

            case INT, LONG, DOUBLE -> {
                // Numeric EditBox
                int w = Math.min(NUMERIC_W, formW / 3);
                EditBox box = new EditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(32);
                box.setValue(currentEdit);
                box.setResponder(val -> markChange(entry.fullPath(), val, entry.currentValue()));
                addRenderableWidget(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }

            case STRING -> {
                // String EditBox — wider
                int w = Math.min(STRING_W, formW - labelWidth(entry.key()) - 12);
                w = Math.max(w, 80);
                EditBox box = new EditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(512);
                box.setValue(currentEdit);
                box.setResponder(val -> markChange(entry.fullPath(), val, entry.currentValue()));
                addRenderableWidget(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }

            case LIST_STRING, LIST_INT -> {
                int btnW = 40;
                int boxW = formW - labelWidth(entry.key()) - btnW - 16;
                boxW = Math.max(boxW, 80);
                int boxX = formX + formW - boxW - btnW - 4;

                EditBox box = new EditBox(this.font, boxX, widgetY, boxW, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(1024);
                box.setValue(currentEdit);
                box.setResponder(val -> markChange(entry.fullPath(), val, entry.currentValue()));
                if (!entry.comment().isBlank()) {
                    box.setTooltip(Tooltip.create(
                            Component.literal(String.format(
                                    I18n.get("rpessentials.gui.config.comma_separated"),
                                    firstLine(entry.comment())))));
                }
                addRenderableWidget(box);
                editBoxValues.put(entry.fullPath(), currentEdit);

                // "Edit List" button — opens the list editor sub-screen
                final String path      = entry.fullPath();
                final String entryKey  = entry.key();
                addRenderableWidget(Button.builder(Component.literal(
                                        I18n.get("rpessentials.gui.config.edit_list")),
                                btn -> openListEditor(path, entryKey, currentEdit))
                        .pos(boxX + boxW + 2, widgetY).size(btnW, 16).build());
            }

            default -> {
                // UNKNOWN: render as string EditBox
                int w = Math.min(STRING_W, formW / 2);
                EditBox box = new EditBox(this.font,
                        formX + formW - w - 2, widgetY, w, 16,
                        Component.literal(entry.key()));
                box.setMaxLength(512);
                box.setValue(currentEdit);
                box.setResponder(val -> markChange(entry.fullPath(), val, entry.currentValue()));
                addRenderableWidget(box);
                editBoxValues.put(entry.fullPath(), currentEdit);
            }
        }

        // ── "Reset to default" button ────────────────────────────────────────
        if (!entry.defaultValue().isBlank()
                && !entry.isSection()
                && entry.type() != ConfigInspector.ValueType.UNKNOWN) {
            String def = entry.defaultValue();
            addRenderableWidget(Button.builder(Component.literal("§8↺"),
                            btn -> {
                                markChange(entry.fullPath(), def, entry.currentValue());
                                editBoxValues.put(entry.fullPath(), def);
                                rebuild();
                            })
                    .pos(FILE_PANEL_W + MARGIN * 3 + 2, y + 2).size(14, 16)
                    .build());
        }
    }

    // ── Scroll buttons ─────────────────────────────────────────────────────

    private void buildScrollButtons(int formX, int formW, int startY, int availableH) {
        if (currentEntries == null || currentEntries.isEmpty()) return;

        boolean canScrollUp   = scrollOffset > 0;
        boolean canScrollDown = canScrollDown(availableH);

        if (canScrollUp) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                            btn -> { scrollOffset = Math.max(0, scrollOffset - 1); rebuild(); })
                    .pos(formX + formW - 20, PANEL_TOP + 4).size(18, 12).build());
        }
        if (canScrollDown) {
            addRenderableWidget(Button.builder(Component.literal("▼"),
                            btn -> { scrollOffset++; rebuild(); })
                    .pos(formX + formW - 20, this.height - FOOTER_H - 16).size(18, 12).build());
        }
    }

    // ── Footer (Apply / Discard buttons) ──────────────────────────────────

    private void buildFooter() {
        int y   = this.height - FOOTER_H + 4;
        int mid = (FILE_PANEL_W + MARGIN * 3 + this.width) / 2;

        // Apply button
        addRenderableWidget(Button.builder(Component.literal(""), btn -> {
            if (!pendingChanges.isEmpty()) applyChanges();
        }).pos(mid - 110, y).size(160, 20).build());

        // Discard
        addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.btn_discard"), btn -> {
            pendingChanges.clear();
            editBoxValues.clear();
            rebuild();
        }).pos(mid + 54, y).size(60, 20).build());

        // Close
        addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.btn_close"), btn -> onClose())
                .pos(this.width - MARGIN - 52, y).size(52, 20).build());
    }

    // =========================================================================
    // RENDER
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (pendingRebuild) {
            pendingRebuild = false;
        }
        // Dim background
        g.fill(0, 0, this.width, this.height, 0x99000000);

        // ── File panel background ────────────────────────────────────────────
        int panelRight = FILE_PANEL_W + MARGIN * 2;
        g.fill(MARGIN - 2, PANEL_TOP, panelRight + 2, this.height - 6, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, panelRight + 2, PANEL_TOP + 2, 0xFF8B6914);
        g.drawString(this.font, I18n.get("rpessentials.gui.config.files_header"), MARGIN + 2, PANEL_TOP + 4, 0xFFD700, false);

        // ── Entries panel background ─────────────────────────────────────────
        int formX = FILE_PANEL_W + MARGIN * 3;
        g.fill(formX - 2, PANEL_TOP, this.width - MARGIN + 2, this.height - 6, 0xBB111111);
        g.fill(formX - 2, PANEL_TOP, this.width - MARGIN + 2, PANEL_TOP + 2, 0xFF8B6914);

        // Title + file name
        String title = selectedFileId != null
                ? String.format(I18n.get("rpessentials.gui.config.title_with_file"), selectedFileId)
                : I18n.get("rpessentials.gui.config.title");
        g.drawString(this.font, title, formX, PANEL_TOP + 5, 0xFFD700, false);

        // Pending indicator
        if (!pendingChanges.isEmpty()) {
            g.drawString(this.font, String.format(I18n.get("rpessentials.gui.config.unsaved"), pendingChanges.size()),
                    formX, PANEL_TOP + 14, 0xFFDD44, false);
        }

        // ── Entries ──────────────────────────────────────────────────────────
        // Reset deferred tooltip before scanning entries
        deferredTooltipComment = null;

        if (loading) {
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.config.loading"),
                    (formX + this.width) / 2, this.height / 2 - 10, 0x888888);
        } else if (currentEntries != null) {
            renderEntries(g, formX, this.width - MARGIN, mouseX, mouseY);
        } else {
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.config.select_file"),
                    (formX + this.width) / 2, this.height / 2, 0x666666);
        }

        // Footer separator
        g.fill(formX - 2, this.height - FOOTER_H, this.width - MARGIN + 2,
                this.height - FOOTER_H + 1, 0xFF444444);

        // Render all widgets (EditBox, Button…) first
        super.render(g, mouseX, mouseY, delta);

        int applyBtnX = (FILE_PANEL_W + MARGIN * 3 + this.width) / 2 - 110;
        int applyBtnY = this.height - FOOTER_H + 4;
        String applyLabel = !pendingChanges.isEmpty()
                ? String.format(I18n.get("rpessentials.gui.config.btn_apply"), pendingChanges.size())
                : I18n.get("rpessentials.gui.config.btn_no_change");
        g.drawCenteredString(this.font, applyLabel, applyBtnX + 80, applyBtnY + 6, 0xFFFFFF);

        // ── Deferred comment tooltip — drawn LAST so it appears above all widgets ──
        if (deferredTooltipComment != null) {
            renderCommentTooltip(g, deferredTooltipComment, deferredTooltipX, deferredTooltipY);
        }
    }

    private void renderEntries(GuiGraphics g, int formX, int formRight, int mouseX, int mouseY) {
        int availableH = this.height - PANEL_TOP - FOOTER_H - 24;
        int startY     = PANEL_TOP + 20;
        int[] range    = computeVisibleRange(availableH, startY);
        int first      = range[0];
        int last       = range[1];

        int y = startY;

        int clipTop    = PANEL_TOP + 20;
        int clipBottom = this.height - FOOTER_H - 4;

        for (int i = first; i <= last && i < currentEntries.size(); i++) {
            ConfigFileEntriesPacket.EntryTransfer entry = currentEntries.get(i);
            int rh = rowHeight(entry);

            if (y > clipBottom) break;
            if (y + rh < clipTop) { y += rh; continue; }

            if (entry.isSection()) {
                // Section header
                g.fill(formX, y + 2, formRight, y + rh, 0xBB1A1A1A);
                g.fill(formX, y + rh - 1, formRight, y + rh, 0xFF8B6914);
                g.drawString(this.font, "§6§l" + formatSectionName(entry.key()),
                        formX + 4, y + 3, 0xFFD700, false);
            } else {
                // Entry row
                boolean isPending = pendingChanges.containsKey(entry.fullPath());
                int bgColor = isPending ? 0x33FFAA00 : 0x22FFFFFF;
                if (mouseX >= formX && mouseX <= formRight - 20
                        && mouseY >= y && mouseY < y + rh) {
                    bgColor = isPending ? 0x55FFAA00 : 0x44FFFFFF;
                }
                g.fill(formX, y, formRight, y + rh - 1, bgColor);

                // Key label
                String keyLabel = formatKeyName(entry.key());
                int labelColor = isPending ? 0xFFDD44 : 0xCCCCCC;
                g.drawString(this.font, keyLabel, formX + 18, y + 6, labelColor, false);

                // Comment indicator (ℹ) — defers tooltip rendering to after super.render()
                if (!entry.comment().isBlank()) {
                    g.drawString(this.font, "§8ℹ", formX + 18 + this.font.width(keyLabel) + 4,
                            y + 6, 0x666666, false);
                    // Store tooltip data; it will be drawn after all widgets
                    int cx = formX + 18 + this.font.width(keyLabel) + 4;
                    if (mouseX >= cx && mouseX <= cx + 8 && mouseY >= y && mouseY < y + rh) {
                        deferredTooltipComment = entry.comment();
                        deferredTooltipX       = mouseX;
                        deferredTooltipY       = mouseY;
                    }
                }

                // Range hint (shown in light gray below key for numeric types)
                if (entry.hasRange()) {
                    String rangeHint = "§f[" + formatNum(entry.rangeMin()) + " – " + formatNum(entry.rangeMax()) + "]";
                    g.drawString(this.font, rangeHint, formX + 18, y + 14, 0x555555, false);
                }

                // Default indicator — shows if value differs from default
                if (!entry.defaultValue().isBlank()) {
                    String current = editBoxValues.getOrDefault(entry.fullPath(), entry.currentValue());
                    if (!current.equals(entry.defaultValue())) {
                        g.drawString(this.font, "§8≠def", formRight - 72, y + 6, 0x444444, false);
                    }
                }
            }
            y += rh;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            g.drawCenteredString(this.font,
                    String.format(I18n.get("rpessentials.gui.config.scroll_above"), scrollOffset),
                    (formX + formRight) / 2, clipTop, 0x444444);
        }
        long below = currentEntries.stream().skip(last + 1).count();
        if (below > 0) {
            g.drawCenteredString(this.font,
                    String.format(I18n.get("rpessentials.gui.config.scroll_below"), below),
                    (formX + formRight) / 2, clipBottom - 8, 0x444444);
        }
    }

    private void renderCommentTooltip(GuiGraphics g, String comment, int mx, int my) {
        List<String> lines = new ArrayList<>();
        for (String line : comment.split("\n")) {
            // Word-wrap at 50 chars
            while (line.length() > 50) {
                lines.add(line.substring(0, 50));
                line = line.substring(50);
            }
            lines.add(line);
        }
        int tw = lines.stream().mapToInt(this.font::width).max().orElse(60) + 8;
        int th = lines.size() * 10 + 6;
        int tx = Math.min(mx + 4, this.width - tw - 4);
        int ty = my - th - 4;
        if (ty < 0) ty = my + 16;

        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xFF222222);
        g.fill(tx, ty, tx + tw, ty + th, 0xFF111111);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(this.font, "§7" + lines.get(i), tx + 4, ty + 3 + i * 10, 0xAAAAAA, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Intentionally empty — we draw our own background in render()
    }

    // =========================================================================
    // MOUSE WHEEL SCROLL
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int formX = FILE_PANEL_W + MARGIN * 3;
        if (mx > formX && currentEntries != null) {
            if (scrollY < 0 && canScrollDown(this.height - PANEL_TOP - FOOTER_H - 24)) {
                scrollOffset++;
                rebuild();
                return true;
            } else if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuild();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    private void requestFile(String fileId) {
        if (fileId.equals(selectedFileId) && currentEntries != null) return;
        selectedFileId  = fileId;
        currentEntries  = null;
        loading         = true;
        scrollOffset    = 0;
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

    private void applyChanges() {
        if (selectedFileId == null || pendingChanges.isEmpty()) return;
        PacketDistributor.sendToServer(new SaveConfigEntriesPacket(selectedFileId,
                new HashMap<>(pendingChanges)));
        pendingChanges.clear();
        // Server will respond with updated entries (see onFileDataReceived)
    }

    /**
     * Opens the ListEditorSubScreen for a LIST_STRING entry.
     * The sub-screen calls back via onListEdited().
     */
    private void openListEditor(String fullPath, String key, String currentCommaValue) {
        Minecraft.getInstance().setScreen(
                new ListEditorSubScreen(this, fullPath, key, currentCommaValue));
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

    private void rebuild() { clearWidgets(); init(); }

    /**
     * Computes which entry indices are visible given the available pixel height.
     * Returns {firstVisible, lastVisible}.
     */
    private int[] computeVisibleRange(int availableH, int startY) {
        if (currentEntries == null || currentEntries.isEmpty()) return new int[]{0, -1};

        int first = Math.max(0, scrollOffset);
        int usedH = 0;
        int last  = first - 1;

        for (int i = first; i < currentEntries.size(); i++) {
            int rh = rowHeight(currentEntries.get(i));
            if (usedH + rh > availableH) break;
            usedH += rh;
            last = i;
        }
        return new int[]{first, last};
    }

    private boolean canScrollDown(int availableH) {
        if (currentEntries == null) return false;
        int[] r = computeVisibleRange(availableH, 0);
        return r[1] < currentEntries.size() - 1;
    }

    private int rowHeight(ConfigFileEntriesPacket.EntryTransfer entry) {
        if (entry.isSection()) return SECTION_H;
        // Numeric entries with a range hint get a tiny extra line
        return entry.hasRange() ? ENTRY_H + 6 : ENTRY_H;
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
        private int scrollOffset = 0;

        ListEditorSubScreen(ConfigManagerScreen parent, String fullPath,
                            String key, String commaValue) {
            super(Component.literal(String.format(I18n.get("rpessentials.gui.config.list.title"),
                    ConfigManagerScreen.formatKeyName(key))));
            this.parent        = parent;
            this.fullPath      = fullPath;
            this.originalComma = commaValue;
            this.items = new ArrayList<>();
            if (!commaValue.isBlank()) {
                for (String s : commaValue.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) this.items.add(t);
                }
            }
        }

        @Override
        protected void init() {
            int midX = this.width / 2;
            int startY = 40;
            int rowH   = 22;
            int visMax = (this.height - startY - 60) / rowH;

            int first = Math.max(0, scrollOffset);
            int last  = Math.min(items.size() - 1, first + visMax - 1);

            for (int i = first; i <= last; i++) {
                final int idx = i;

                // EditBox for the item
                EditBox box = new EditBox(this.font, midX - 160, startY + (i - first) * rowH + 2,
                        290, 18, Component.empty());
                box.setMaxLength(512);
                box.setValue(items.get(i));
                box.setResponder(val -> items.set(idx, val));
                addRenderableWidget(box);

                // Remove button
                addRenderableWidget(Button.builder(Component.literal("§c✗"),
                                btn -> { items.remove(idx); rebuild(); })
                        .pos(midX + 134, startY + (i - first) * rowH + 2).size(16, 18).build());

                // Move up
                if (i > 0)
                    addRenderableWidget(Button.builder(Component.literal("▲"),
                                    btn -> { Collections.swap(items, idx, idx - 1); rebuild(); })
                            .pos(midX + 152, startY + (i - first) * rowH + 2).size(14, 8).build());

                // Move down
                if (i < items.size() - 1)
                    addRenderableWidget(Button.builder(Component.literal("▼"),
                                    btn -> { Collections.swap(items, idx, idx + 1); rebuild(); })
                            .pos(midX + 152, startY + (i - first) * rowH + 12).size(14, 8).build());
            }

            int btnY = this.height - 48;

            // Scroll
            if (scrollOffset > 0)
                addRenderableWidget(Button.builder(Component.literal("▲"),
                                btn -> { scrollOffset = Math.max(0, scrollOffset - 1); rebuild(); })
                        .pos(midX - 170, startY).size(16, 18).build());
            if (last < items.size() - 1)
                addRenderableWidget(Button.builder(Component.literal("▼"),
                                btn -> { scrollOffset++; rebuild(); })
                        .pos(midX - 170, startY + (last - first) * rowH).size(16, 18).build());

            // Add new entry
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_add"),
                            btn -> { items.add(""); rebuild(); })
                    .pos(midX - 170, btnY).size(110, 20).build());

            // Save
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_save"), btn -> {
                // Remove empty entries
                items.removeIf(String::isBlank);
                String result = String.join(", ", items);
                parent.onListEdited(fullPath, result, originalComma);
            }).pos(midX - 55, btnY).size(110, 20).build());

            // Cancel
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.config.list.btn_cancel"),
                            btn -> net.minecraft.client.Minecraft.getInstance().setScreen(parent))
                    .pos(midX + 59, btnY).size(70, 20).build());
        }

        private void rebuild() { clearWidgets(); init(); }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            int visMax = (this.height - 40 - 60) / 22;
            int maxScroll = Math.max(0, items.size() - visMax);
            if (scrollY < 0 && scrollOffset < maxScroll) {
                scrollOffset++;
                rebuild();
                return true;
            } else if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                rebuild();
                return true;
            }
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            renderBackground(g, mx, my, delta);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFD700);
            g.drawCenteredString(this.font,
                    String.format(I18n.get("rpessentials.gui.config.list.entries"), items.size()),
                    this.width / 2, 26, 0x666666);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}