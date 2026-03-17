package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.network.OpenProfessionGuiPacket;
import net.rp.rpessentials.network.SaveProfessionPacket;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GUI d'édition des professions — CLIENT UNIQUEMENT
 * v2 : champs persistants entre rebuilds, couleurs colorées,
 *       autocomplete restrictions, indicateur scroll liste.
 */
@OnlyIn(Dist.CLIENT)
public class ProfessionEditorScreen extends Screen {

    // =========================================================================
    // ÉTAT — tout stocké ici, jamais lu depuis les widgets
    // =========================================================================

    private final List<OpenProfessionGuiPacket.ProfessionEntry> existingProfessions;

    // Formulaire en cours
    private String stateId          = "";
    private String stateName        = "";
    private boolean stateIsNew      = true;
    private int stateSelectedIndex  = -1;
    private int stateColorIndex     = 0;
    private int stateActiveTab      = 0;

    private List<String> allowedCrafts    = new ArrayList<>();
    private List<String> allowedBlocks    = new ArrayList<>();
    private List<String> allowedItems     = new ArrayList<>();
    private List<String> allowedEquipment = new ArrayList<>();

    // Champ de saisie restriction + suggestions
    private String stateRestrictionInput = "";
    private List<String> suggestions     = new ArrayList<>();

    // Scroll liste gauche
    private int profListScroll = 0;

    // ── Couleurs ─────────────────────────────────────────────────────────────
    // Format : code Minecraft §x, label, valeur int pour le rendu
    private static final char[]   COLOR_CHARS  = { 'f','e','6','c','a','b','9','d','7','8' };
    private static final String[] COLOR_LABELS = { "Blanc","Jaune","Or","Rouge","Vert","Cyan","Bleu","Rose","Gris","Gris foncé" };
    private static final int[]    COLOR_INT    = { 0xFFFFFF,0xFFFF55,0xFFAA00,0xFF5555,0x55FF55,0x55FFFF,0x5555FF,0xFF55FF,0xAAAAAA,0x555555 };

    private static final String[] TAB_LABELS = { "Crafts", "Blocs", "Items", "Équipement" };

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int LIST_W        = 140;
    private static final int MARGIN        = 8;
    private static final int PANEL_TOP     = 18;
    private static final int ROW_H         = 20;
    private static final int LIST_VISIBLE  = 9;
    private static final int MAX_SUGGEST   = 6;

    // ── Cache registre (calculé une seule fois) ───────────────────────────────
    private static List<String> allItemIds   = null;
    private static List<String> allBlockIds  = null;

    // =========================================================================
    // CONSTRUCTEUR
    // =========================================================================

    public ProfessionEditorScreen(List<OpenProfessionGuiPacket.ProfessionEntry> existing) {
        super(Component.literal("§6✦ Éditeur de Professions"));
        this.existingProfessions = new ArrayList<>(existing);
        buildRegistryCache();
    }

    /** Construit les listes d'IDs une seule fois au premier ouverture. */
    private static void buildRegistryCache() {
        if (allItemIds == null) {
            allItemIds = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (allBlockIds == null) {
            allBlockIds = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    // =========================================================================
    // INIT — lit UNIQUEMENT depuis l'état, jamais depuis les anciens widgets
    // =========================================================================

    @Override
    protected void init() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        // ── Champ ID ──────────────────────────────────────────────────────────
        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.literal("ID"));
        idBox.setHint(Component.literal("§7ex: forgeron"));
        idBox.setMaxLength(32);
        idBox.setValue(stateId);
        idBox.setEditable(stateIsNew);
        // Responder : met à jour l'état à chaque frappe, PAS de rebuild
        idBox.setResponder(val -> stateId = val);
        addRenderableWidget(idBox);
        y += 46;

        // ── Champ Nom ─────────────────────────────────────────────────────────
        EditBox nameBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.literal("Nom"));
        nameBox.setHint(Component.literal("§7ex: Forgeron"));
        nameBox.setMaxLength(64);
        nameBox.setValue(stateName);
        nameBox.setResponder(val -> stateName = val);
        addRenderableWidget(nameBox);
        y += 46;

        // ── Boutons couleur (texte coloré dans la couleur correspondante) ─────
        int colBtnW = 58;
        int colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            final int idx = i;
            int col = i % colCols;
            int row = i / colCols;
            // Le texte du bouton est dans la couleur correspondante
            String btnLabel = (i == stateColorIndex ? "§l" : "") + "§" + COLOR_CHARS[i] + COLOR_LABELS[i];
            addRenderableWidget(Button.builder(
                            Component.literal(btnLabel),
                            btn -> { stateColorIndex = idx; rebuild(); })
                    .pos(formX + col * (colBtnW + 2), y + 14 + row * (colBtnH + 2))
                    .size(colBtnW, colBtnH)
                    .build());
        }
        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6;

        // ── Onglets restrictions ──────────────────────────────────────────────
        int tabW = Math.max(40, (formW - 6) / 4);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int ti = i;
            String label = i == stateActiveTab ? "§e§l" + TAB_LABELS[i] : "§7" + TAB_LABELS[i];
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { stateActiveTab = ti; rebuild(); })
                    .pos(formX + i * (tabW + 2), y)
                    .size(tabW, 16)
                    .build());
        }
        y += 20;

        // ── Liste des restrictions actives (avec bouton × pour supprimer) ─────
        List<String> currentList = getActiveList();
        int restrictListY = y;
        int maxRestrictVisible = 4;
        for (int i = 0; i < Math.min(currentList.size(), maxRestrictVisible); i++) {
            final int ri = i;
            addRenderableWidget(Button.builder(Component.literal("§c×"),
                            btn -> { getActiveList().remove(ri); rebuild(); })
                    .pos(formX, restrictListY + i * 18)
                    .size(14, 16)
                    .build());
        }
        y = restrictListY + Math.min(Math.max(currentList.size(), 1), maxRestrictVisible) * 18 + 4;

        // ── Champ ajout restriction + bouton + ───────────────────────────────
        int fieldW = Math.min(formW - 36, 180);
        EditBox restrictBox = new EditBox(this.font, formX + 16, y, fieldW, 18,
                Component.literal("Restriction"));
        restrictBox.setHint(Component.literal("§7ex: minecraft:iron_sword  ou  minecraft:*"));
        restrictBox.setMaxLength(128);
        restrictBox.setValue(stateRestrictionInput);
        restrictBox.setResponder(val -> {
            stateRestrictionInput = val;
            updateSuggestions(val);
            // Pas de rebuild ici — on met juste à jour les suggestions
            // mais on évite le rebuild complet pour ne pas perdre le focus
        });
        addRenderableWidget(restrictBox);

        addRenderableWidget(Button.builder(Component.literal("§a+"),
                        btn -> addRestriction())
                .pos(formX + 16 + fieldW + 2, y)
                .size(16, 18)
                .build());
        y += 22;

        // ── Suggestions d'autocomplétion ─────────────────────────────────────
        for (int i = 0; i < Math.min(suggestions.size(), MAX_SUGGEST); i++) {
            final String sug = suggestions.get(i);
            addRenderableWidget(Button.builder(
                            Component.literal("§7" + sug),
                            btn -> {
                                stateRestrictionInput = sug;
                                updateSuggestions("");
                                addRestriction();
                            })
                    .pos(formX + 16, y + i * 14)
                    .size(fieldW, 13)
                    .build());
        }

        // ── Boutons bas ───────────────────────────────────────────────────────
        int btnY = this.height - 28;

        addRenderableWidget(Button.builder(
                        Component.literal("§aSauvegarder les modifications"),
                        btn -> saveProfession())
                .pos(formX, btnY)
                .size(160, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("§eNouvelle profession"),
                        btn -> resetForm())
                .pos(formX + 164, btnY)
                .size(120, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("§cFermer"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 55, btnY)
                .size(55, 20)
                .build());

        // ── Liste gauche des professions ──────────────────────────────────────
        int listStart = profListScroll;
        int listEnd   = Math.min(existingProfessions.size(), listStart + LIST_VISIBLE);
        for (int i = listStart; i < listEnd; i++) {
            final int idx = i;
            OpenProfessionGuiPacket.ProfessionEntry e = existingProfessions.get(i);
            String label = (i == stateSelectedIndex ? "§e▶ " : "  ") + e.id();
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> loadEntry(idx))
                    .pos(MARGIN, PANEL_TOP + 14 + (i - listStart) * ROW_H)
                    .size(LIST_W, ROW_H - 2)
                    .build());
        }

        // Boutons scroll liste
        if (profListScroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                            btn -> { profListScroll--; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 - 15)
                    .size(LIST_W, 13)
                    .build());
        }
        if (listEnd < existingProfessions.size()) {
            addRenderableWidget(Button.builder(Component.literal("▼ (" + (existingProfessions.size() - listEnd) + " de plus)"),
                            btn -> { profListScroll++; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + 2)
                    .size(LIST_W, 13)
                    .build());
        }
    }

    // =========================================================================
    // REBUILD — sauvegarder l'état depuis les widgets puis reconstruire
    // =========================================================================

    /**
     * Reconstruit les widgets.
     * NE PAS appeler depuis un setResponder — ça casserait le focus du champ.
     * Appeler uniquement depuis des clics de boutons.
     */
    private void rebuild() {
        clearWidgets();
        init();
    }

    // =========================================================================
    // RENDER
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Fond semi-transparent sans blur
        g.fill(0, 0, this.width, this.height, 0x99000000);

        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;

        // Panneau liste gauche
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, 0xFF8B6914);
        g.drawString(this.font, "§6Professions (" + existingProfessions.size() + ")", MARGIN + 3, PANEL_TOP + 4, 0xFFD700, false);

        if (existingProfessions.isEmpty()) {
            g.drawString(this.font, "§8Aucune profession", MARGIN + 8, PANEL_TOP + 24, 0x666666, false);
        }

        // Panneau formulaire droit
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, 0xFF8B6914);

        // Mode actuel en haut du formulaire
        String modeLabel = stateIsNew
                ? "§a✦ Nouvelle profession"
                : "§e✦ Édition : §f" + stateId;
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int y = PANEL_TOP + 14;
        g.drawString(this.font, "§7ID §8(non modifiable après création) :", formX, y + 6, 0x888888, false);
        y += 46;
        g.drawString(this.font, "§7Nom d'affichage :", formX, y + 6, 0x888888, false);
        y += 46;

        // Label couleur + aperçu du nom coloré
        g.drawString(this.font, "§7Couleur :", formX, y + 4, 0x888888, false);
        if (!stateName.isEmpty()) {
            String preview = "§" + COLOR_CHARS[stateColorIndex] + stateName;
            g.drawString(this.font, Component.literal(preview), formX + 70, y + 4, 0xFFFFFF, false);
        }

        // Surlignage couleur sélectionnée
        int colBtnW = 58;
        int colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        int sc = stateColorIndex % colCols;
        int sr = stateColorIndex / colCols;
        int hx = formX + sc * (colBtnW + 2) - 1;
        int hy = y + 14 + sr * (colBtnH + 2) - 1;
        g.fill(hx, hy, hx + colBtnW + 2, hy + colBtnH + 2, 0xFF_FFD700);

        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6;

        // Label onglet
        y += 20;

        // Restrictions actives
        List<String> currentList = getActiveList();
        int maxVisible = 4;
        if (currentList.isEmpty()) {
            g.drawString(this.font, "§8Aucune — tout est bloqué pour cette catégorie", formX + 16, y + 3, 0x555555, false);
        } else {
            for (int i = 0; i < Math.min(currentList.size(), maxVisible); i++) {
                g.drawString(this.font, "§7" + currentList.get(i), formX + 18, y + i * 18 + 3, 0xAAAAAA, false);
            }
            if (currentList.size() > maxVisible) {
                g.drawString(this.font, "§8... et " + (currentList.size() - maxVisible) + " de plus",
                        formX + 18, y + maxVisible * 18 + 3, 0x666666, false);
            }
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Vide — on gère le fond dans render()
    }

    // =========================================================================
    // LOGIQUE
    // =========================================================================

    private void addRestriction() {
        String val = stateRestrictionInput.trim();
        if (!val.isEmpty() && !getActiveList().contains(val)) {
            getActiveList().add(val);
        }
        stateRestrictionInput = "";
        suggestions.clear();
        rebuild();
    }

    private void updateSuggestions(String input) {
        suggestions.clear();
        if (input.length() < 2) return;

        String lower = input.toLowerCase();
        List<String> pool = switch (stateActiveTab) {
            case 1  -> allBlockIds;
            case 2, 3 -> allItemIds;
            default -> allItemIds; // crafts = items
        };

        if (pool == null) return;
        pool.stream()
                .filter(id -> id.contains(lower))
                .limit(MAX_SUGGEST)
                .forEach(suggestions::add);
    }

    private void saveProfession() {
        String id   = stateId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = stateName.trim();
        if (id.isEmpty() || name.isEmpty()) return;

        String colorCode = "&" + COLOR_CHARS[stateColorIndex];

        PacketDistributor.sendToServer(new SaveProfessionPacket(
                id, name, colorCode,
                new ArrayList<>(allowedCrafts),
                new ArrayList<>(allowedBlocks),
                new ArrayList<>(allowedItems),
                new ArrayList<>(allowedEquipment),
                stateIsNew
        ));

        // Mise à jour locale immédiate
        var entry = new OpenProfessionGuiPacket.ProfessionEntry(
                id, name, colorCode,
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

        // Trouve la couleur correspondante
        stateColorIndex = 0;
        String codeChar = e.color().replace("&", "").replace("§", "");
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            if (String.valueOf(COLOR_CHARS[i]).equals(codeChar)) { stateColorIndex = i; break; }
        }

        allowedCrafts    = new ArrayList<>(e.allowedCrafts());
        allowedBlocks    = new ArrayList<>(e.allowedBlocks());
        allowedItems     = new ArrayList<>(e.allowedItems());
        allowedEquipment = new ArrayList<>(e.allowedEquipment());
        stateActiveTab   = 0;
        stateRestrictionInput = "";
        suggestions.clear();

        rebuild();
    }

    private void resetForm() {
        stateSelectedIndex = -1;
        stateIsNew = true;
        stateId    = "";
        stateName  = "";
        stateColorIndex = 0;
        stateActiveTab  = 0;
        allowedCrafts    = new ArrayList<>();
        allowedBlocks    = new ArrayList<>();
        allowedItems     = new ArrayList<>();
        allowedEquipment = new ArrayList<>();
        stateRestrictionInput = "";
        suggestions.clear();
        rebuild();
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