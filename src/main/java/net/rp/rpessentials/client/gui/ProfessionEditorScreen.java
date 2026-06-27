package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.network.OpenProfessionGuiPacket;
import net.rp.rpessentials.network.SaveProfessionPacket;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ProfessionEditorScreen extends Screen {

    private final List<OpenProfessionGuiPacket.ProfessionEntry> existingProfessions;

    private String stateId         = "";
    private String stateName       = "";
    private boolean stateIsNew     = true;
    private int stateSelectedIndex = -1;
    private int stateColorIndex    = 0;
    private int stateActiveTab     = 0;

    private List<String> allowedCrafts    = new ArrayList<>();
    private List<String> allowedBlocks    = new ArrayList<>();
    private List<String> allowedItems     = new ArrayList<>();
    private List<String> allowedEquipment = new ArrayList<>();

    private String stateRestrictionInput = "";

    private final List<String> suggestions = new ArrayList<>();
    private int  suggestionIndex           = -1;
    private int  restrictBoxX, restrictBoxY, restrictBoxW;

    private static final int MAX_SUGGEST = 8;
    private static final int SUGGEST_H   = 12;

    private int profListScroll = 0;

    private static final char[]   COLOR_CHARS = { 0, 'f','e','6','c','a','b','9','d','7','8','0','1','2','3','4','5' };
    private static final String[] COLOR_KEYS  = {
            "none","white","yellow","gold","red","green","cyan","blue","pink","gray","dark_gray",
            "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple"
    };
    private static final String[] TAB_KEYS = { "crafts", "blocks", "items", "equipment" };

    private static final int LIST_W       = 140;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;
    private static final int LIST_VISIBLE = 9;

    private static List<String> allItemIds  = null;
    private static List<String> allBlockIds = null;

    public ProfessionEditorScreen(List<OpenProfessionGuiPacket.ProfessionEntry> existing) {
        super(Component.translatable("rpessentials.gui.profession_editor.title"));
        this.existingProfessions = new ArrayList<>(existing);
        buildRegistryCache();
    }

    private static void buildRegistryCache() {
        if (allItemIds == null) {
            allItemIds = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        }
        if (allBlockIds == null) {
            allBlockIds = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        }
    }

    @Override
    protected void init() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.profession_editor.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(stateId);
        idBox.setEditable(stateIsNew);
        idBox.setResponder(val -> stateId = val);
        addRenderableWidget(idBox);
        y += 46;

        EditBox nameBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.name_label"));
        nameBox.setHint(Component.translatable("rpessentials.gui.profession_editor.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setValue(stateName);
        nameBox.setResponder(val -> stateName = val);
        addRenderableWidget(nameBox);
        y += 46;

        int colBtnW = 58;
        int colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            final int idx = i;
            int col = i % colCols;
            int row = i / colCols;
            String colorLabel = I18n.get("rpessentials.gui.color." + COLOR_KEYS[i]);
            String btnLabel = i == 0
                    ? (i == stateColorIndex ? "§l" : "") + "§7" + colorLabel
                    : (i == stateColorIndex ? "§l" : "") + "§" + COLOR_CHARS[i] + colorLabel;
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                            btn -> { stateColorIndex = idx; rebuild(); })
                    .pos(formX + col * (colBtnW + 2), y + 14 + row * (colBtnH + 2))
                    .size(colBtnW, colBtnH).build());
        }
        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6;

        int tabW = Math.max(40, (formW - 6) / 4);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int ti = i;
            String tabName = I18n.get("rpessentials.gui.profession_editor.tab." + TAB_KEYS[i]);
            String label   = i == stateActiveTab ? "§e§l" + tabName : "§7" + tabName;
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { stateActiveTab = ti; closeSuggestions(); rebuild(); })
                    .pos(formX + i * (tabW + 2), y).size(tabW, 16).build());
        }
        y += 20;

        List<String> currentList = getActiveList();
        int restrictListY      = y;
        int maxRestrictVisible = 4;
        for (int i = 0; i < Math.min(currentList.size(), maxRestrictVisible); i++) {
            final int ri = i;
            addRenderableWidget(Button.builder(Component.literal("§c×"),
                            btn -> { getActiveList().remove(ri); closeSuggestions(); rebuild(); })
                    .pos(formX, restrictListY + i * 18).size(14, 16).build());
        }
        y = restrictListY + Math.min(Math.max(currentList.size(), 1), maxRestrictVisible) * 18 + 4;

        int fieldW  = Math.min(formW - 36, 200);
        restrictBoxX = formX + 16;
        restrictBoxY = y;
        restrictBoxW = fieldW;

        EditBox restrictBox = new EditBox(this.font, restrictBoxX, restrictBoxY, fieldW, 18,
                Component.translatable("rpessentials.gui.profession_editor.restriction_label"));
        restrictBox.setHint(Component.translatable("rpessentials.gui.profession_editor.restriction_hint"));
        restrictBox.setMaxLength(128);
        restrictBox.setValue(stateRestrictionInput);
        restrictBox.setResponder(val -> {
            stateRestrictionInput = val;
            suggestionIndex = -1;
            updateSuggestions(val);
        });
        addRenderableWidget(restrictBox);

        addRenderableWidget(Button.builder(Component.literal("§a+"), btn -> addRestriction())
                .pos(restrictBoxX + fieldW + 2, y).size(16, 18).build());

        int btnY = this.height - 28;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_save"), btn -> saveProfession())
                .pos(formX, btnY).size(160, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"), btn -> onClose())
                .pos(this.width - MARGIN - 55, btnY).size(55, 20).build());

        // ── Liste gauche + bouton "Nouvelle profession" en bas du panneau ─────
        int listStart = profListScroll;
        int listEnd   = Math.min(existingProfessions.size(), listStart + LIST_VISIBLE);
        for (int i = listStart; i < listEnd; i++) {
            final int idx = i;
            OpenProfessionGuiPacket.ProfessionEntry e = existingProfessions.get(i);
            String label = (i == stateSelectedIndex ? "§e▶ " : "  ") + e.id();
            addRenderableWidget(Button.builder(Component.literal(label), btn -> loadEntry(idx))
                    .pos(MARGIN, PANEL_TOP + 14 + (i - listStart) * ROW_H).size(LIST_W, ROW_H - 2).build());
        }
        if (profListScroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"), btn -> { profListScroll--; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 - 15).size(LIST_W, 13).build());
        }
        if (listEnd < existingProfessions.size()) {
            int remaining = existingProfessions.size() - listEnd;
            addRenderableWidget(Button.builder(
                            Component.literal(I18n.get("rpessentials.gui.btn_more", remaining)),
                            btn -> { profListScroll++; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + 2).size(LIST_W, 13).build());
        }
        // "New profession" anchored at the bottom of the left panel
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_new"),
                        btn -> resetForm())
                .pos(MARGIN, this.height - 26)
                .size(LIST_W, 16)
                .build());
    }

    private void rebuild() { clearWidgets(); init(); }

    // =========================================================================
    // CLAVIER
    // =========================================================================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!suggestions.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                int dir = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                suggestionIndex = Math.floorMod(suggestionIndex + dir, suggestions.size());
                applySuggestion(suggestions.get(suggestionIndex));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                suggestionIndex = Math.floorMod(suggestionIndex + 1, suggestions.size());
                applySuggestion(suggestions.get(suggestionIndex));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                suggestionIndex = Math.floorMod(suggestionIndex - 1, suggestions.size());
                applySuggestion(suggestions.get(suggestionIndex));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (suggestionIndex >= 0) { addRestriction(); return true; }
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeSuggestions(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applySuggestion(String value) {
        stateRestrictionInput = value;
        children().stream()
                .filter(w -> w instanceof EditBox)
                .map(w -> (EditBox) w)
                .filter(b -> b.getX() == restrictBoxX && b.getY() == restrictBoxY)
                .findFirst()
                .ifPresent(b -> b.setValue(value));
    }

    private void closeSuggestions() { suggestions.clear(); suggestionIndex = -1; }

    // =========================================================================
    // CLIC
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!suggestions.isEmpty()) {
            int dropX = restrictBoxX;
            int dropY = restrictBoxY + 19;
            int dropW = restrictBoxW;
            int count = Math.min(suggestions.size(), MAX_SUGGEST);
            if (mx >= dropX && mx <= dropX + dropW && my >= dropY && my <= dropY + count * SUGGEST_H) {
                int clicked = (int) ((my - dropY) / SUGGEST_H);
                if (clicked >= 0 && clicked < count) {
                    stateRestrictionInput = suggestions.get(clicked);
                    addRestriction();
                    return true;
                }
            }
            closeSuggestions();
        }
        return super.mouseClicked(mx, my, button);
    }

    // =========================================================================
    // RENDER
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x99000000);

        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;

        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, 0xFF8B6914);
        g.drawString(this.font,
                "§6" + I18n.get("rpessentials.gui.profession_editor.professions_header", existingProfessions.size()),
                MARGIN + 3, PANEL_TOP + 4, 0xFFD700, false);
        if (existingProfessions.isEmpty())
            g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.profession_editor.no_profession"),
                    MARGIN + 8, PANEL_TOP + 24, 0x666666, false);

        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, 0xFF8B6914);

        String modeLabel = stateIsNew
                ? I18n.get("rpessentials.gui.profession_editor.mode_new")
                : I18n.get("rpessentials.gui.profession_editor.mode_edit", stateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int y = PANEL_TOP + 14;
        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.id_label_draw"), formX, y + 6, 0x888888, false);
        y += 46;
        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.name_label_draw"), formX, y + 6, 0x888888, false);
        y += 46;

        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.color_label"), formX, y + 4, 0x888888, false);
        if (!stateName.isEmpty()) {
            String preview = stateColorIndex == 0
                    ? stateName
                    : "§" + COLOR_CHARS[stateColorIndex] + stateName;
            g.drawString(this.font, Component.literal(preview), formX + 70, y + 4, 0xFFFFFF, false);
        }

        int colBtnW = 58, colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        int sc = stateColorIndex % colCols, sr = stateColorIndex / colCols;
        g.fill(formX + sc * (colBtnW + 2) - 1, y + 14 + sr * (colBtnH + 2) - 1,
                formX + sc * (colBtnW + 2) + colBtnW + 1, y + 14 + sr * (colBtnH + 2) + colBtnH + 1, 0xFF_FFD700);

        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6 + 20; // +20 onglets

        List<String> currentList = getActiveList();
        int maxVisible = 4;
        if (currentList.isEmpty()) {
            g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.profession_editor.no_restriction"),
                    formX + 16, y + 3, 0x555555, false);
        } else {
            for (int i = 0; i < Math.min(currentList.size(), maxVisible); i++)
                g.drawString(this.font, "§7" + currentList.get(i), formX + 18, y + i * 18 + 3, 0xAAAAAA, false);
            if (currentList.size() > maxVisible)
                g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.profession_editor.and_more", currentList.size() - maxVisible),
                        formX + 18, y + maxVisible * 18 + 3, 0x666666, false);
        }

        // Widgets normaux
        super.render(g, mouseX, mouseY, delta);

        // Dropdown suggestions — toujours rendu EN DERNIER
        renderSuggestions(g, mouseX, mouseY);
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (suggestions.isEmpty()) return;

        int count = Math.min(suggestions.size(), MAX_SUGGEST);
        int dropX = restrictBoxX;
        int dropY = restrictBoxY + 19;
        int dropW = restrictBoxW;
        int dropH = count * SUGGEST_H + 2;

        // Fond + bordures
        g.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1A1A1A);
        g.fill(dropX, dropY, dropX + dropW, dropY + 1, 0xFF555555);
        g.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, 0xFF555555);
        g.fill(dropX, dropY, dropX + 1, dropY + dropH, 0xFF555555);
        g.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, 0xFF555555);

        for (int i = 0; i < count; i++) {
            int lineY    = dropY + 1 + i * SUGGEST_H;
            boolean hov  = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= lineY && mouseY < lineY + SUGGEST_H;
            boolean sel  = i == suggestionIndex;

            if (sel)      g.fill(dropX + 1, lineY, dropX + dropW - 1, lineY + SUGGEST_H, 0xFF2A4A7F);
            else if (hov) g.fill(dropX + 1, lineY, dropX + dropW - 1, lineY + SUGGEST_H, 0xFF2A2A2A);

            g.drawString(this.font, suggestions.get(i), dropX + 3, lineY + 2, sel ? 0xFFFFFF : 0xAAAAAA, false);
        }

        if (suggestions.size() > MAX_SUGGEST)
            g.drawString(this.font, "§8+" + (suggestions.size() - MAX_SUGGEST) + " ...",
                    dropX + 3, dropY + dropH + 1, 0x555555, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    // =========================================================================
    // LOGIQUE
    // =========================================================================

    private void addRestriction() {
        String val = stateRestrictionInput.trim();
        if (!val.isEmpty() && !getActiveList().contains(val)) getActiveList().add(val);
        stateRestrictionInput = "";
        closeSuggestions();
        rebuild();
    }

    private void updateSuggestions(String input) {
        suggestions.clear();
        suggestionIndex = -1;
        if (input.isBlank()) return;

        String lower = input.toLowerCase().trim();
        List<String> pool = getPoolForCurrentTab();
        if (pool == null) return;

        // Passe 1 : préfixe
        for (String id : pool) {
            if (id.startsWith(lower)) suggestions.add(id);
            if (suggestions.size() >= MAX_SUGGEST * 3) break;
        }
        // Passe 2 : contient (complément)
        if (suggestions.size() < MAX_SUGGEST) {
            for (String id : pool) {
                if (!id.startsWith(lower) && id.contains(lower)) suggestions.add(id);
                if (suggestions.size() >= MAX_SUGGEST * 3) break;
            }
        }
    }

    private List<String> getPoolForCurrentTab() {
        return switch (stateActiveTab) {
            case 1  -> allBlockIds;
            case 2, 3 -> allItemIds;
            default -> allItemIds;
        };
    }

    private void saveProfession() {
        String id   = stateId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = stateName.trim();
        if (id.isEmpty() || name.isEmpty()) return;
        String colorCode = stateColorIndex == 0 ? "" : "&" + COLOR_CHARS[stateColorIndex];
        PacketDistributor.sendToServer(new SaveProfessionPacket(id, name, colorCode,
                new ArrayList<>(allowedCrafts), new ArrayList<>(allowedBlocks),
                new ArrayList<>(allowedItems), new ArrayList<>(allowedEquipment), stateIsNew));
        var entry = new OpenProfessionGuiPacket.ProfessionEntry(id, name, colorCode,
                allowedCrafts, allowedBlocks, allowedItems, allowedEquipment);
        if (stateIsNew) {
            existingProfessions.add(entry);
            stateSelectedIndex = existingProfessions.size() - 1;
            stateIsNew = false;
            stateId = id;
        } else {
            existingProfessions.set(stateSelectedIndex, entry);
        }
        rebuild();
    }

    private void loadEntry(int index) {
        stateSelectedIndex = index;
        stateIsNew = false;
        OpenProfessionGuiPacket.ProfessionEntry e = existingProfessions.get(index);
        stateId   = e.id();
        stateName = e.displayName();
        stateColorIndex = 0;
        String codeChar = e.color().replace("&", "").replace("§", "");
        if (!codeChar.isEmpty()) {
            for (int i = 1; i < COLOR_CHARS.length; i++)
                if (String.valueOf(COLOR_CHARS[i]).equals(codeChar)) { stateColorIndex = i; break; }
        }
        allowedCrafts    = new ArrayList<>(e.allowedCrafts());
        allowedBlocks    = new ArrayList<>(e.allowedBlocks());
        allowedItems     = new ArrayList<>(e.allowedItems());
        allowedEquipment = new ArrayList<>(e.allowedEquipment());
        stateActiveTab   = 0;
        stateRestrictionInput = "";
        closeSuggestions();
        rebuild();
    }

    private void resetForm() {
        stateSelectedIndex = -1; stateIsNew = true; stateId = ""; stateName = "";
        stateColorIndex = 0; stateActiveTab = 0;
        allowedCrafts = new ArrayList<>(); allowedBlocks = new ArrayList<>();
        allowedItems = new ArrayList<>(); allowedEquipment = new ArrayList<>();
        stateRestrictionInput = ""; closeSuggestions(); rebuild();
    }

    private List<String> getActiveList() {
        return switch (stateActiveTab) {
            case 1  -> allowedBlocks;
            case 2  -> allowedItems;
            case 3  -> allowedEquipment;
            default -> allowedCrafts;
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < LIST_W + MARGIN * 2) {
            int max = Math.max(0, existingProfessions.size() - LIST_VISIBLE);
            profListScroll = (int) Math.max(0, Math.min(max, profListScroll - sy));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}