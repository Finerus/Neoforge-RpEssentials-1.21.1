package net.rp.rpessentials.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.network.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ProfessionEditorScreen extends Screen {

    // =========================================================================
    // DONNÉES
    // =========================================================================
    private final List<OpenProfessionGuiPacket.ProfessionEntry> existingProfessions;
    private final List<OpenRolesGuiPacket.RoleEntry> existingRoles;

    // Onglet principal : 0 = Professions, 1 = Rôles
    private int mainTab;

    // =========================================================================
    // ÉTAT PROFESSIONS
    // =========================================================================
    private String profStateId       = "";
    private String profStateName     = "";
    private boolean profStateIsNew   = true;
    private int profSelectedIndex    = -1;
    private int profColorIndex       = 0;
    private int profActiveTab        = 0;
    private int profListScroll       = 0;
    private String profPendingDelete = null;
    private String rolePendingDelete = null;
    private Button profInCreationButton;
    private Button roleInCreationButton;
    private boolean profDirty = false;
    private boolean roleDirty = false;
    private final Set<String> roleDirtyPerms = new LinkedHashSet<>();
    private final Set<String> profDirtyEntries    = new HashSet<>();
    private final Set<String> globalDirtyEntries   = new HashSet<>();

    private final OpenGlobalRestrictionsPacket initialGlobal;
    private List<String> globalBlockedCrafts;
    private List<String> globalUnbreakableBlocks;
    private List<String> globalBlockedItems;
    private List<String> globalBlockedEquipment;
    private List<String> globalContainerRestrictions;
    private int  globalActiveCategory = 0;
    private String globalRestrictionInput = "";
    private int globalRestrictBoxX, globalRestrictBoxY, globalRestrictBoxW;

    private static final String[] GLOBAL_CATEGORY_KEYS = {
            "crafts", "blocks", "items", "equipment", "containers"
    };

    private List<String> allowedCrafts    = new ArrayList<>();
    private List<String> allowedBlocks    = new ArrayList<>();
    private List<String> allowedItems     = new ArrayList<>();
    private List<String> allowedEquipment = new ArrayList<>();

    private String profRestrictionInput  = "";
    private final List<String> suggestions = new ArrayList<>();
    private int suggestionIndex          = -1;
    private int restrictBoxX, restrictBoxY, restrictBoxW;

    // =========================================================================
    // ÉTAT RÔLES
    // =========================================================================
    private String roleStateId       = "";
    private String roleStateLp       = "";
    private boolean roleStateIsNew   = true;
    private int roleSelectedIndex    = -1;
    private int roleListScroll       = 0;
    private final Set<String> roleStatePerms = new LinkedHashSet<>();

    // Toutes les permissions disponibles avec leur libellé d'affichage
    private static final String[] ALL_PERMISSIONS = {
            "isStaff",
            "tabWhitelist",
            "scheduleWhitelist",
            "professionWhitelist",
            "seeNicknames",
            "seeAll",
            "bypassWorldBorder",
            "spyMessages",
            "bypassAutoUnwhitelist",
            "bypassDeathRpWhitelist"
    };

    private String permLabel(String permId) {
        return I18n.get("rpessentials.gui.roles.perm." + permId);
    }

    // =========================================================================
    // CONSTANTES LAYOUT
    // =========================================================================
    private static final char[]   COLOR_CHARS = { 0,'f','e','6','c','a','b','9','d','7','8','0','1','2','3','4','5' };
    private static final String[] COLOR_KEYS  = {
            "none","white","yellow","gold","red","green","cyan","blue","pink","gray","dark_gray",
            "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple"
    };
    private static final String[] PROF_TAB_KEYS = { "crafts","blocks","items","equipment" };

    private static final int LIST_W       = 140;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;
    private static final int LIST_VISIBLE = 9;
    private static final int MAX_SUGGEST  = 8;
    private static final int SUGGEST_H    = 12;

    private static List<String> allItemIds  = null;
    private static List<String> allBlockIds = null;

    public ProfessionEditorScreen(List<OpenProfessionGuiPacket.ProfessionEntry> professions) {
        this(professions, List.of(), null, 0);
    }

    public ProfessionEditorScreen(List<OpenProfessionGuiPacket.ProfessionEntry> professions,
                                  List<OpenRolesGuiPacket.RoleEntry> roles,
                                  OpenGlobalRestrictionsPacket global,
                                  int initialTab) {
        super(Component.translatable("rpessentials.gui.profession_editor.title"));
        this.existingProfessions = new ArrayList<>(professions);
        this.existingRoles       = new ArrayList<>(roles);
        this.initialGlobal       = global;
        this.mainTab             = initialTab;

        if (global != null) {
            this.globalBlockedCrafts          = new ArrayList<>(global.blockedCrafts());
            this.globalUnbreakableBlocks      = new ArrayList<>(global.unbreakableBlocks());
            this.globalBlockedItems           = new ArrayList<>(global.blockedItems());
            this.globalBlockedEquipment       = new ArrayList<>(global.blockedEquipment());
            this.globalContainerRestrictions  = new ArrayList<>(global.containerRestrictions());
        } else {
            this.globalBlockedCrafts         = new ArrayList<>();
            this.globalUnbreakableBlocks     = new ArrayList<>();
            this.globalBlockedItems          = new ArrayList<>();
            this.globalBlockedEquipment      = new ArrayList<>();
            this.globalContainerRestrictions = new ArrayList<>();
        }
        buildRegistryCache();

        if (!existingProfessions.isEmpty()) loadProfEntry(0, false);
        if (!existingRoles.isEmpty()) loadRoleEntry(0, false);
    }

    private static void buildRegistryCache() {
        if (allItemIds == null)
            allItemIds = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        if (allBlockIds == null)
            allBlockIds = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
    }

    // =========================================================================
    // INIT
    // =========================================================================
    @Override
    protected void init() {
        buildMainTabs();
        if      (mainTab == 0) initProfessions();
        else if (mainTab == 1) initRoles();
        else                   initGlobalRestrictions();
    }

    private void initGlobalRestrictions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        List<String> currentList = getGlobalActiveList();
        int listAreaY = y;
        List<String> pool = globalActiveCategory == 4 ? null
                : (globalActiveCategory == 1 ? allBlockIds : allItemIds);
        String hint = globalActiveCategory == 4 ? "§7e.g: minecraft:anvil;blacksmith" : "§7e.g: minecraft:diamond_sword";
        addRenderableWidget(Button.builder(
                        Component.literal(I18n.get("rpessentials.gui.global.manage_list", currentList.size())),
                        btn -> Minecraft.getInstance().setScreen(new RestrictionListSubScreen(
                                this, currentList, globalDirtyEntries, pool, hint, true, this::applyGlobalRestrictionsOnly)))
                .pos(formX, listAreaY).size(Math.min(formW - 4, 200), 18).build());
        y = listAreaY + 22;

        int fieldW = Math.min(formW - 36, 220);
        globalRestrictBoxX = formX + 16;
        globalRestrictBoxY = y;
        globalRestrictBoxW = fieldW;

        EditBox addBox = new EditBox(this.font, globalRestrictBoxX, globalRestrictBoxY, fieldW, 18,
                Component.literal("Entry"));
        addBox.setHint(Component.literal(
                globalActiveCategory == 4 ? "§7e.g: minecraft:anvil;blacksmith" : "§7e.g: minecraft:diamond_sword"));
        addBox.setMaxLength(256);
        addBox.setValue(globalRestrictionInput);
        addBox.setResponder(val -> {
            globalRestrictionInput = val;
            suggestionIndex = -1;
            updateGlobalSuggestions(val);
        });
        addRenderableWidget(addBox);
        addRenderableWidget(Button.builder(Component.literal("§a+"),
                        btn -> addGlobalRestriction())
                .pos(globalRestrictBoxX + fieldW + 2, y).size(16, 18).build());

        int btnY = this.height - 35;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.global.btn_save"),
                        btn -> saveGlobalRestrictions())
                .pos(formX, btnY).size(160, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 65, btnY).size(55, 20).build());

        buildGlobalLeftPanel();
    }

    private List<String> getGlobalActiveList() {
        return switch (globalActiveCategory) {
            case 1  -> globalUnbreakableBlocks;
            case 2  -> globalBlockedItems;
            case 3  -> globalBlockedEquipment;
            case 4  -> globalContainerRestrictions;
            default -> globalBlockedCrafts;
        };
    }

    private void addGlobalRestriction() {
        String val = globalRestrictionInput.trim();
        if (!val.isEmpty() && !getGlobalActiveList().contains(val)) {
            getGlobalActiveList().add(val);
            globalDirtyEntries.add(val);
        }
        globalRestrictionInput = "";
        closeSuggestions();
        rebuild();
    }

    private void removeProfRestriction(int index, String value) {
        getProfActiveList().remove(index);
        profDirty = true;
        closeSuggestions();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    I18n.get("rpessentials.gui.profession_editor.removed_feedback", value)), false);
        }
        rebuild();
    }

    private void removeGlobalRestriction(int index, String value) {
        getGlobalActiveList().remove(index);
        globalDirtyEntries.remove(value);
        closeSuggestions();
        saveGlobalRestrictions();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    I18n.get("rpessentials.gui.global.removed_feedback", value)), false);
        }
        rebuild();
    }

    private void updateGlobalSuggestions(String input) {
        suggestions.clear();
        suggestionIndex = -1;
        if (input.isBlank() || globalActiveCategory == 4) return;
        String lower = input.toLowerCase().trim();
        List<String> pool = switch (globalActiveCategory) {
            case 1  -> allBlockIds;
            default -> allItemIds;
        };
        for (String id : pool) {
            if (id.startsWith(lower)) suggestions.add(id);
            if (suggestions.size() >= MAX_SUGGEST * 3) break;
        }
        if (suggestions.size() < MAX_SUGGEST) {
            for (String id : pool) {
                if (!id.startsWith(lower) && id.contains(lower)) suggestions.add(id);
                if (suggestions.size() >= MAX_SUGGEST * 3) break;
            }
        }
    }

    private void saveGlobalRestrictions() {
        PacketDistributor.sendToServer(new SaveGlobalRestrictionsPacket(
                new ArrayList<>(globalBlockedCrafts),
                new ArrayList<>(globalUnbreakableBlocks),
                new ArrayList<>(globalBlockedItems),
                new ArrayList<>(globalBlockedEquipment),
                new ArrayList<>(globalContainerRestrictions)));
        globalDirtyEntries.clear();
    }

    private void applyGlobalRestrictionsOnly() {
        PacketDistributor.sendToServer(new SaveGlobalRestrictionsPacket(
                new ArrayList<>(globalBlockedCrafts),
                new ArrayList<>(globalUnbreakableBlocks),
                new ArrayList<>(globalBlockedItems),
                new ArrayList<>(globalBlockedEquipment),
                new ArrayList<>(globalContainerRestrictions)));
    }

    private void buildGlobalLeftPanel() {
        for (int i = 0; i < GLOBAL_CATEGORY_KEYS.length; i++) {
            final int ci = i;
            boolean selected = i == globalActiveCategory;
            String label = (selected ? "§e§l▶ " : "§7  ")
                    + I18n.get("rpessentials.gui.global." + GLOBAL_CATEGORY_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { globalActiveCategory = ci; closeSuggestions(); rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + i * ROW_H).size(LIST_W, ROW_H - 2).build());
        }
    }

    private void buildMainTabs() {
        int tabW  = 90;
        int gap   = 3;
        int total = tabW * 3 + gap * 2;
        int x     = (this.width - total) / 2;
        int y     = 2;

        String profLabel = mainTab == 0
                ? "§e§l" + I18n.get("rpessentials.gui.roles.tab_main_professions")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_professions");
        addRenderableWidget(Button.builder(Component.literal(profLabel),
                        btn -> { mainTab = 0; closeSuggestions(); rebuild(); })
                .pos(x, y).size(tabW, 13).build());

        String roleLabel = mainTab == 1
                ? "§9§l" + I18n.get("rpessentials.gui.roles.tab_main_roles")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_roles");
        addRenderableWidget(Button.builder(Component.literal(roleLabel),
                        btn -> { mainTab = 1; closeSuggestions(); rebuild(); })
                .pos(x + tabW + gap, y).size(tabW, 13).build());

        String globalLabel = mainTab == 2
                ? "§c§l" + I18n.get("rpessentials.gui.roles.tab_main_global")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_global");
        addRenderableWidget(Button.builder(Component.literal(globalLabel),
                        btn -> { mainTab = 2; closeSuggestions(); rebuild(); })
                .pos(x + (tabW + gap) * 2, y).size(tabW, 13).build());

        addRenderableWidget(Button.builder(Component.literal("§7↻"), btn -> {
                    ClientGuiOpener.requestedTab = mainTab;
                    PacketDistributor.sendToServer(new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PROFESSION));
                })
                .pos(this.width - MARGIN - 18, 2).size(18, 13)
                .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")))
                .build());
    }

    // =========================================================================
    // ONGLET PROFESSIONS — logique identique à l'original
    // =========================================================================
    private void initProfessions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        // ID
        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.profession_editor.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(profStateId);
        idBox.setEditable(profStateIsNew);
        idBox.setResponder(val -> profStateId = val);
        addRenderableWidget(idBox);
        y += 46;

        // Nom
        EditBox nameBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.name_label"));
        nameBox.setHint(Component.translatable("rpessentials.gui.profession_editor.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setValue(profStateName);
        nameBox.setResponder(val -> { profStateName = val; profDirty = true; });
        addRenderableWidget(nameBox);
        y += 46;

        // Palette couleurs
        int colBtnW = 58, colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            final int idx = i;
            int col = i % colCols, row = i / colCols;
            String colorLabel = I18n.get("rpessentials.gui.color." + COLOR_KEYS[i]);
            String btnLabel = i == 0
                    ? (i == profColorIndex ? "§l" : "") + "§7" + colorLabel
                    : (i == profColorIndex ? "§l" : "") + "§" + COLOR_CHARS[i] + colorLabel;
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                            btn -> { profColorIndex = idx; profDirty = true; rebuild(); })
                    .pos(formX + col * (colBtnW + 2), y + 14 + row * (colBtnH + 2))
                    .size(colBtnW, colBtnH).build());
        }
        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6;

        // Onglets restriction
        int tabW = Math.max(40, (formW - 6) / 4);
        for (int i = 0; i < PROF_TAB_KEYS.length; i++) {
            final int ti = i;
            String label = i == profActiveTab
                    ? "§e§l" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i])
                    : "§7" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { profActiveTab = ti; closeSuggestions(); rebuild(); })
                    .pos(formX + i * (tabW + 2), y).size(tabW, 16).build());
        }
        y += 20;

        // Liste restrictions courante
        List<String> currentList = getProfActiveList();
        int restrictListY = y;
        addRenderableWidget(Button.builder(
                        Component.literal(I18n.get("rpessentials.gui.profession_editor.manage_list", currentList.size())),
                        btn -> Minecraft.getInstance().setScreen(new RestrictionListSubScreen(
                                this, currentList, profDirtyEntries, getPoolForCurrentTab(),
                                "§7e.g: minecraft:iron_sword", false, () -> profDirty = true)))
                .pos(formX, restrictListY).size(Math.min(formW - 4, 200), 18).build());
        y = restrictListY + 22;

        // Champ d'ajout
        int fieldW = Math.min(formW - 36, 200);
        restrictBoxX = formX + 16;
        restrictBoxY = y;
        restrictBoxW = fieldW;

        EditBox restrictBox = new EditBox(this.font, restrictBoxX, restrictBoxY, fieldW, 18,
                Component.translatable("rpessentials.gui.profession_editor.restriction_label"));
        restrictBox.setHint(Component.translatable("rpessentials.gui.profession_editor.restriction_hint"));
        restrictBox.setMaxLength(128);
        restrictBox.setValue(profRestrictionInput);
        restrictBox.setResponder(val -> {
            profRestrictionInput = val;
            suggestionIndex = -1;
            updateSuggestions(val);
        });
        addRenderableWidget(restrictBox);
        addRenderableWidget(Button.builder(Component.literal("§a+"), btn -> addRestriction())
                .pos(restrictBoxX + fieldW + 2, y).size(16, 18).build());

        // Boutons bas
        int btnY = this.height - 35;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_save"),
                        btn -> saveProfession())
                .pos(formX, btnY).size(160, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 65, btnY).size(55, 20).build());

        // Liste gauche
        buildProfessionList();
    }

    private void buildProfessionList() {
        int listStart = profListScroll;
        int listEnd   = Math.min(existingProfessions.size(), listStart + LIST_VISIBLE);
        int listOffY  = 15;

        boolean canScrollUp   = profListScroll > 0;
        boolean canScrollDown = listEnd < existingProfessions.size();

        addRenderableWidget(Button.builder(Component.literal(canScrollUp ? "▲" : "§8▲"),
                        btn -> { if (canScrollUp) { profListScroll--; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14).size(LIST_W, 13).build());

        for (int i = listStart; i < listEnd; i++) {
            final int idx = i;
            String id = existingProfessions.get(i).id();
            boolean isPendingDelete = id.equals(profPendingDelete);
            boolean isDirtySelected = i == profSelectedIndex && profDirty && !isPendingDelete;

            String prefix = i == profSelectedIndex ? "§e> " : "  ";
            String style  = isPendingDelete ? "§c" : (isDirtySelected ? "§e§o" : "");
            String suffix = isDirtySelected ? " *" : "";
            String label  = prefix + style + id + suffix;

            int rowY = PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffY;

            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> loadProfEntry(idx))
                    .pos(MARGIN, rowY).size(LIST_W - 18, ROW_H - 2).build());

            if (isPendingDelete) {
                addRenderableWidget(Button.builder(Component.literal("§c!"),
                                btn -> {
                                    PacketDistributor.sendToServer(new DeleteProfessionPacket(id));
                                    existingProfessions.remove(idx);
                                    profSelectedIndex = -1;
                                    profPendingDelete = null;
                                    resetProfForm();
                                })
                        .pos(MARGIN + LIST_W - 16, rowY).size(16, ROW_H - 2)
                        .tooltip(Tooltip.create(Component.translatable(
                                "rpessentials.gui.profession_editor.confirm_delete_tooltip")))
                        .build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("§7x"),
                                btn -> { profPendingDelete = id; rebuild(); })
                        .pos(MARGIN + LIST_W - 16, rowY).size(16, ROW_H - 2)
                        .tooltip(Tooltip.create(Component.translatable(
                                "rpessentials.gui.profession_editor.delete_tooltip")))
                        .build());
            }
        }

        if (profStateIsNew) {
            int row = listEnd - listStart;
            if (row < LIST_VISIBLE) {
                String shown = profStateId.isEmpty() ? "?" : profStateId;
                profInCreationButton = Button.builder(
                                Component.literal("§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"),
                                btn -> {})
                        .pos(MARGIN, PANEL_TOP + 14 + row * ROW_H + listOffY).size(LIST_W - 18, ROW_H - 2).build();
                addRenderableWidget(profInCreationButton);
            } else {
                profInCreationButton = null;
            }
        } else {
            profInCreationButton = null;
        }

        addRenderableWidget(Button.builder(Component.literal(canScrollDown
                                ? "▼ " + I18n.get("rpessentials.gui.btn_more", existingProfessions.size() - listEnd)
                                : "§8▼"),
                        btn -> { if (canScrollDown) { profListScroll++; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + listOffY).size(LIST_W, 13).build());

        addRenderableWidget(Button.builder(
                        Component.literal(tabColorCode() + I18n.get("rpessentials.gui.profession_editor.btn_new")),
                        btn -> resetProfForm())
                .pos(MARGIN, this.height - 26).size(LIST_W, 16).build());
    }

    // =========================================================================
    // ONGLET RÔLES
    // =========================================================================
    private void initRoles() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        // ID
        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 160), 18,
                Component.translatable("rpessentials.gui.roles.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.roles.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(roleStateId);
        idBox.setEditable(roleStateIsNew);
        idBox.setResponder(val -> roleStateId = val);
        addRenderableWidget(idBox);
        y += 38;

        // LuckPerms group
        EditBox lpBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 160), 18,
                Component.translatable("rpessentials.gui.roles.lp_label"));
        lpBox.setHint(Component.translatable("rpessentials.gui.roles.lp_hint"));
        lpBox.setMaxLength(64);
        lpBox.setValue(roleStateLp);
        lpBox.setEditable(canManageRoles());
        lpBox.setResponder(val -> { roleStateLp = val; roleDirty = true; });
        addRenderableWidget(lpBox);
        y += 38;

        // Case
        int checkX = formX;
        int checkW = formW - 4;
        for (int i = 0; i < ALL_PERMISSIONS.length; i++) {
            final String perm = ALL_PERMISSIONS[i];
            boolean checked   = roleStatePerms.contains(perm);
            boolean modified  = roleDirtyPerms.contains(perm);
            String base       = checked ? "§a[✔] " : "§c[X] ";
            String textColor  = modified ? "§e§o" : "§f";
            String label       = base + textColor + permLabel(perm);
            Button permBtn = Button.builder(Component.literal(label), btn -> {
                if (roleStatePerms.contains(perm)) roleStatePerms.remove(perm);
                else roleStatePerms.add(perm);
                roleDirty = true;
                roleDirtyPerms.add(perm);
                rebuild();
            }).pos(checkX, y + i * 18).size(checkW, 16).build();
            permBtn.active = canManageRoles();
            addRenderableWidget(permBtn);
        }
        y += ALL_PERMISSIONS.length * 18 + 6;

        // Boutons bas
        int btnY = this.height - 35;
        Button saveRoleBtn = Button.builder(Component.translatable("rpessentials.gui.roles.btn_save"),
                        btn -> saveRole())
                .pos(formX, btnY).size(120, 20).build();
        saveRoleBtn.active = Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.hasPermissions(3);
        addRenderableWidget(saveRoleBtn);
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 65, btnY).size(55, 20).build());

        // Liste gauche
        buildRoleList();
    }

    private void buildRoleList() {
        int listStart = roleListScroll;
        int listEnd   = Math.min(existingRoles.size(), listStart + LIST_VISIBLE);
        int listOffY  = 15;

        boolean canScrollUp   = roleListScroll > 0;
        boolean canScrollDown = listEnd < existingRoles.size();

        addRenderableWidget(Button.builder(Component.literal(canScrollUp ? "▲" : "§8▲"),
                        btn -> { if (canScrollUp) { roleListScroll--; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14).size(LIST_W, 13).build());

        for (int i = listStart; i < listEnd; i++) {
            final int idx = i;
            String id = existingRoles.get(i).id();
            boolean isPendingDelete = id.equals(rolePendingDelete);
            boolean isDirtySelected = i == roleSelectedIndex && roleDirty && !isPendingDelete;

            String prefix = i == roleSelectedIndex ? "§e> " : "  ";
            String style  = isPendingDelete ? "§c" : (isDirtySelected ? "§e§o" : "");
            String suffix = isDirtySelected ? " *" : "";
            String label  = prefix + style + id + suffix;

            int rowY = PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffY;

            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> loadRoleEntry(idx))
                    .pos(MARGIN, rowY).size(LIST_W - 18, ROW_H - 2).build());

            if (isPendingDelete) {
                Button confirm = Button.builder(Component.literal("§c!"), btn -> {
                            PacketDistributor.sendToServer(new DeleteRolePacket(id));
                            existingRoles.remove(idx);
                            roleSelectedIndex = -1;
                            rolePendingDelete = null;
                            resetRoleForm();
                        })
                        .pos(MARGIN + LIST_W - 16, rowY).size(16, ROW_H - 2)
                        .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.roles.confirm_delete_tooltip")))
                        .build();
                confirm.active = canManageRoles();
                addRenderableWidget(confirm);
            } else {
                Button del = Button.builder(Component.literal("§7x"),
                                btn -> { rolePendingDelete = id; rebuild(); })
                        .pos(MARGIN + LIST_W - 16, rowY).size(16, ROW_H - 2)
                        .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.roles.delete_tooltip")))
                        .build();
                del.active = canManageRoles();
                addRenderableWidget(del);
            }
        }

        if (roleStateIsNew) {
            int row = listEnd - listStart;
            if (row < LIST_VISIBLE) {
                String shown = roleStateId.isEmpty() ? "?" : roleStateId;
                roleInCreationButton = Button.builder(
                                Component.literal("§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"),
                                btn -> {})
                        .pos(MARGIN, PANEL_TOP + 14 + row * ROW_H + listOffY).size(LIST_W - 18, ROW_H - 2).build();
                addRenderableWidget(roleInCreationButton);
            } else {
                roleInCreationButton = null;
            }
        } else {
            roleInCreationButton = null;
        }

        addRenderableWidget(Button.builder(Component.literal(canScrollDown
                                ? "▼ " + I18n.get("rpessentials.gui.btn_more", existingRoles.size() - listEnd)
                                : "§8▼"),
                        btn -> { if (canScrollDown) { roleListScroll++; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + listOffY).size(LIST_W, 13).build());

        addRenderableWidget(Button.builder(Component.literal(tabColorCode() + I18n.get("rpessentials.gui.roles.btn_new")),
                        btn -> resetRoleForm())
                .pos(MARGIN, this.height - 26).size(LIST_W, 16).build());
    }

    private boolean canManageRoles() {
        return Minecraft.getInstance().player != null
                && net.rp.rpessentials.RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
    }

    // =========================================================================
    // RENDU
    // =========================================================================
    private void renderInCreationTab(GuiGraphics g) {
        if (mainTab == 0 && profStateIsNew) {
            int listStart = profListScroll;
            int listEnd   = Math.min(existingProfessions.size(), listStart + LIST_VISIBLE);
            int row = listEnd - listStart;
            if (row < LIST_VISIBLE) {
                String shown = profStateId.isEmpty() ? "?" : profStateId;
                String label = "§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")";
                g.drawCenteredString(this.font, label, MARGIN + LIST_W / 2,
                        PANEL_TOP + 14 + row * ROW_H + 5, 0xFFFFFF);
            }
        } else if (mainTab == 1 && roleStateIsNew) {
            int listStart = roleListScroll;
            int listEnd   = Math.min(existingRoles.size(), listStart + LIST_VISIBLE);
            int row = listEnd - listStart;
            if (row < LIST_VISIBLE) {
                String shown = roleStateId.isEmpty() ? "?" : roleStateId;
                String label = "§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")";
                g.drawCenteredString(this.font, label, MARGIN + LIST_W / 2,
                        PANEL_TOP + 14 + row * ROW_H + 5, 0xFFFFFF);
            }
        }
    }

    private int tabLineColor() {
        return switch (mainTab) {
            case 1 -> 0xFF5555FF; // Rôles, bleu
            case 2 -> 0xFFFF5555; // Restrictions globales, rouge
            default -> 0xFFFFFF55; // Professions, jaune
        };
    }

    private String tabColorCode() {
        return switch (mainTab) {
            case 1 -> "§9";
            case 2 -> "§c";
            default -> "§e";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x99000000);

        // Panneau gauche
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

        if (mainTab == 0) {
            g.drawString(this.font,
                    I18n.get("rpessentials.gui.profession_editor.professions_header",
                            existingProfessions.size()),
                    MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);
            if (existingProfessions.isEmpty())
                g.drawString(this.font,
                        "§8" + I18n.get("rpessentials.gui.profession_editor.no_profession"),
                        MARGIN + 8, PANEL_TOP + 24, 0x666666, false);
        } else if (mainTab == 1) {
            g.drawString(this.font, I18n.get("rpessentials.gui.roles.header", existingRoles.size()),
                    MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);
            if (existingRoles.isEmpty())
                g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.roles.no_roles"),
                        MARGIN + 8, PANEL_TOP + 24, 0x666666, false);
        } else {
            g.drawString(this.font, I18n.get("rpessentials.gui.global.categories_header"),
                    MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);
        }

        // Panneau droit
        int formX = LIST_W + MARGIN * 3;
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, tabLineColor());

        if (mainTab == 0) renderProfessionsOverlay(g, formX);
        else if (mainTab == 1) renderRolesOverlay(g, formX);
        else renderGlobalRestrictionsOverlay(g, formX);

        if (profInCreationButton != null) {
            String shown = profStateId.isEmpty() ? "?" : profStateId;
            profInCreationButton.setMessage(Component.literal(
                    "§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"));
        }
        if (roleInCreationButton != null) {
            String shown = roleStateId.isEmpty() ? "?" : roleStateId;
            roleInCreationButton.setMessage(Component.literal(
                    "§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"));
        }

        super.render(g, mouseX, mouseY, delta);

        if (mainTab == 0 || mainTab == 2) renderSuggestions(g, mouseX, mouseY);
    }

    private void renderGlobalRestrictionsOverlay(GuiGraphics g, int formX) {
        g.drawString(this.font,
                tabColorCode() + I18n.get("rpessentials.gui.global.title") + " §8- §e"
                        + I18n.get("rpessentials.gui.global." + GLOBAL_CATEGORY_KEYS[globalActiveCategory]),
                formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int y = PANEL_TOP + 14;
    }

    private void renderProfessionsOverlay(GuiGraphics g, int formX) {
        String modeLabel = profStateIsNew
                ? tabColorCode() + I18n.get("rpessentials.gui.profession_editor.mode_new")
                : tabColorCode() + I18n.get("rpessentials.gui.profession_editor.mode_edit", profStateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int formW = this.width - formX - MARGIN;
        int y = PANEL_TOP + 14;
        g.drawString(this.font,
                I18n.get("rpessentials.gui.profession_editor.id_label_draw"),
                formX, y + 6, 0x888888, false);
        y += 46;
        g.drawString(this.font,
                I18n.get("rpessentials.gui.profession_editor.name_label_draw"),
                formX, y + 6, 0x888888, false);
        y += 46;

        g.drawString(this.font,
                I18n.get("rpessentials.gui.profession_editor.color_label"),
                formX, y + 4, 0x888888, false);
        if (!profStateName.isEmpty()) {
            String preview = profColorIndex == 0
                    ? profStateName
                    : "§" + COLOR_CHARS[profColorIndex] + profStateName;
            g.drawString(this.font, Component.literal(preview), formX + 70, y + 4, 0xFFFFFF, false);
        }

        int colBtnW = 58, colBtnH = 16;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        int sc = profColorIndex % colCols, sr = profColorIndex / colCols;
        g.fill(formX + sc * (colBtnW + 2) - 1, y + 14 + sr * (colBtnH + 2) - 1,
                formX + sc * (colBtnW + 2) + colBtnW + 1,
                y + 14 + sr * (colBtnH + 2) + colBtnH + 1, 0xFF_FFD700);

        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += 14 + colRows * (colBtnH + 2) + 6 + 20;

    }

    private void renderRolesOverlay(GuiGraphics g, int formX) {
        String modeLabel = roleStateIsNew
                ? tabColorCode() + I18n.get("rpessentials.gui.roles.mode_new")
                : tabColorCode() + I18n.get("rpessentials.gui.roles.mode_edit", roleStateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int y = PANEL_TOP + 14;
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.id_label"),
                formX, y + 6, 0x888888, false);
        y += 38;
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.lp_label"),
                formX, y + 6, 0x888888, false);
        y += 38;

        g.fill(formX, y - 2, this.width - MARGIN, y - 1, 0xFF333333);
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.permissions_header"), formX, y - 12, 0xFFD700, false);
    }

    private void renderSuggestions(GuiGraphics g, int mouseX, int mouseY) {
        if (suggestions.isEmpty()) return;

        int dropX = mainTab == 2 ? globalRestrictBoxX : restrictBoxX;
        int dropY = (mainTab == 2 ? globalRestrictBoxY : restrictBoxY) + 19;
        int dropW = mainTab == 2 ? globalRestrictBoxW : restrictBoxW;
        int count = Math.min(suggestions.size(), MAX_SUGGEST);
        int dropH = count * SUGGEST_H + 2;

        g.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1A1A1A);
        g.fill(dropX, dropY, dropX + dropW, dropY + 1, 0xFF555555);
        g.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, 0xFF555555);
        g.fill(dropX, dropY, dropX + 1, dropY + dropH, 0xFF555555);
        g.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, 0xFF555555);

        for (int i = 0; i < count; i++) {
            int lineY = dropY + 1 + i * SUGGEST_H;
            boolean hov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= lineY && mouseY < lineY + SUGGEST_H;
            boolean sel = i == suggestionIndex;
            if (sel)      g.fill(dropX + 1, lineY, dropX + dropW - 1, lineY + SUGGEST_H, 0xFF2A4A7F);
            else if (hov) g.fill(dropX + 1, lineY, dropX + dropW - 1, lineY + SUGGEST_H, 0xFF2A2A2A);
            g.drawString(this.font, suggestions.get(i), dropX + 3, lineY + 2,
                    sel ? 0xFFFFFF : 0xAAAAAA, false);
        }
        if (suggestions.size() > MAX_SUGGEST)
            g.drawString(this.font, "§8+" + (suggestions.size() - MAX_SUGGEST) + " ...",
                    dropX + 3, dropY + dropH + 1, 0x555555, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    // =========================================================================
    // CLAVIER
    // =========================================================================
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((mainTab == 0 || mainTab == 2) && !suggestions.isEmpty()) {
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
                if (suggestionIndex >= 0) {
                    if (mainTab == 2) addGlobalRestriction(); else addRestriction();
                    return true;
                }
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeSuggestions(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // =========================================================================
    // CLIC SOURIS
    // =========================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mainTab == 2 && !suggestions.isEmpty()) {
            int dropX = globalRestrictBoxX, dropY = globalRestrictBoxY + 19;
            int dropW = globalRestrictBoxW;
            int count = Math.min(suggestions.size(), MAX_SUGGEST);
            if (mx >= dropX && mx <= dropX + dropW
                    && my >= dropY && my <= dropY + count * SUGGEST_H) {
                int clicked = (int) ((my - dropY) / SUGGEST_H);
                if (clicked >= 0 && clicked < count) {
                    globalRestrictionInput = suggestions.get(clicked);
                    addGlobalRestriction();
                    return true;
                }
            }
            closeSuggestions();
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < LIST_W + MARGIN * 2) {
            if (mainTab == 0) {
                int max = Math.max(0, existingProfessions.size() - LIST_VISIBLE);
                profListScroll = (int) Math.max(0, Math.min(max, profListScroll - sy));
            } else {
                int max = Math.max(0, existingRoles.size() - LIST_VISIBLE);
                roleListScroll = (int) Math.max(0, Math.min(max, roleListScroll - sy));
            }
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // =========================================================================
    // LOGIQUE PROFESSIONS
    // =========================================================================
    private void addRestriction() {
        String val = profRestrictionInput.trim();
        if (!val.isEmpty() && !getProfActiveList().contains(val)) {
            getProfActiveList().add(val);
            profDirty = true;
            profDirtyEntries.add(val);
        }
        profRestrictionInput = "";
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
        for (String id : pool) {
            if (id.startsWith(lower)) suggestions.add(id);
            if (suggestions.size() >= MAX_SUGGEST * 3) break;
        }
        if (suggestions.size() < MAX_SUGGEST) {
            for (String id : pool) {
                if (!id.startsWith(lower) && id.contains(lower)) suggestions.add(id);
                if (suggestions.size() >= MAX_SUGGEST * 3) break;
            }
        }
    }

    private void applySuggestion(String value) {
        int targetX = mainTab == 2 ? globalRestrictBoxX : restrictBoxX;
        int targetY = mainTab == 2 ? globalRestrictBoxY : restrictBoxY;
        if (mainTab == 2) globalRestrictionInput = value; else profRestrictionInput = value;
        children().stream()
                .filter(w -> w instanceof EditBox)
                .map(w -> (EditBox) w)
                .filter(b -> b.getX() == targetX && b.getY() == targetY)
                .findFirst()
                .ifPresent(b -> b.setValue(value));
    }

    private void closeSuggestions() { suggestions.clear(); suggestionIndex = -1; }

    private void saveProfession() {
        String id   = profStateId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = profStateName.trim().replace(";", "");
        if (id.isEmpty() || name.isEmpty()) return;

        if (profStateIsNew) {
            for (OpenRolesGuiPacket.RoleEntry r : existingRoles) {
                if (r.id().equalsIgnoreCase(id)) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§c[RpEssentials] This ID is already used by a role."), false);
                    }
                    return;
                }
            }
        }

        String colorCode = profColorIndex == 0 ? "" : "&" + COLOR_CHARS[profColorIndex];
        PacketDistributor.sendToServer(new SaveProfessionPacket(id, name, colorCode,
                new ArrayList<>(allowedCrafts), new ArrayList<>(allowedBlocks),
                new ArrayList<>(allowedItems), new ArrayList<>(allowedEquipment), profStateIsNew));

        var entry = new OpenProfessionGuiPacket.ProfessionEntry(id, name, colorCode,
                allowedCrafts, allowedBlocks, allowedItems, allowedEquipment);
        if (profStateIsNew) {
            existingProfessions.add(entry);
            profSelectedIndex = existingProfessions.size() - 1;
            profStateIsNew = false;
            profStateId    = id;
        } else {
            existingProfessions.set(profSelectedIndex, entry);
        }
        profDirty = false;
        profDirtyEntries.clear();
        rebuild();
    }

    private void loadProfEntry(int index) {
        loadProfEntry(index, true);
    }

    private void loadProfEntry(int index, boolean rebuildAfter) {
        profDirty = false;
        profPendingDelete = null;
        profSelectedIndex = index;
        profStateIsNew    = false;
        OpenProfessionGuiPacket.ProfessionEntry e = existingProfessions.get(index);
        profStateId   = e.id();
        profStateName = e.displayName();
        profColorIndex = 0;
        String codeChar = e.color().replace("&", "").replace("§", "");
        if (!codeChar.isEmpty()) {
            for (int i = 1; i < COLOR_CHARS.length; i++)
                if (String.valueOf(COLOR_CHARS[i]).equals(codeChar)) { profColorIndex = i; break; }
        }
        allowedCrafts    = new ArrayList<>(e.allowedCrafts());
        allowedBlocks    = new ArrayList<>(e.allowedBlocks());
        allowedItems     = new ArrayList<>(e.allowedItems());
        allowedEquipment = new ArrayList<>(e.allowedEquipment());
        profActiveTab    = 0;
        profRestrictionInput = "";
        closeSuggestions();
        if (rebuildAfter) rebuild();
    }

    private void resetProfForm() {
        profDirty = false;
        profSelectedIndex = -1; profStateIsNew = true;
        profStateId = ""; profStateName = ""; profColorIndex = 0; profActiveTab = 0;
        allowedCrafts = new ArrayList<>(); allowedBlocks = new ArrayList<>();
        allowedItems = new ArrayList<>(); allowedEquipment = new ArrayList<>();
        profRestrictionInput = "";
        if (existingProfessions.size() >= LIST_VISIBLE) profListScroll = existingProfessions.size() - LIST_VISIBLE + 1;
        closeSuggestions(); rebuild();
    }

    private List<String> getProfActiveList() {
        return switch (profActiveTab) {
            case 1  -> allowedBlocks;
            case 2  -> allowedItems;
            case 3  -> allowedEquipment;
            default -> allowedCrafts;
        };
    }

    private List<String> getPoolForCurrentTab() {
        return switch (profActiveTab) {
            case 1       -> allBlockIds;
            case 2, 3    -> allItemIds;
            default      -> allItemIds;
        };
    }

    // =========================================================================
    // LOGIQUE RÔLES
    // =========================================================================
    private void saveRole() {
        String id = roleStateId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String lp = roleStateLp.trim().isEmpty() ? id : roleStateLp.trim();
        if (id.isEmpty()) return;

        if (roleStateIsNew) {
            for (OpenProfessionGuiPacket.ProfessionEntry p : existingProfessions) {
                if (p.id().equalsIgnoreCase(id)) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§c[RpEssentials] This ID is already used by a profession."), false);
                    }
                    return;
                }
            }
        }

        List<String> perms = new ArrayList<>(roleStatePerms);
        PacketDistributor.sendToServer(new SaveRolePacket(id, lp, perms, roleStateIsNew));

        OpenRolesGuiPacket.RoleEntry entry = new OpenRolesGuiPacket.RoleEntry(id, lp, perms);
        if (roleStateIsNew) {
            existingRoles.add(entry);
            roleSelectedIndex = existingRoles.size() - 1;
            roleStateIsNew    = false;
            roleStateId       = id;
        } else {
            existingRoles.set(roleSelectedIndex, entry);
        }
        roleDirty = false;
        roleDirtyPerms.clear();
        rebuild();
    }

    private void loadRoleEntry(int index) {
        loadRoleEntry(index, true);
    }

    private void loadRoleEntry(int index, boolean rebuildAfter) {
        roleDirty = false;
        rolePendingDelete = null;
        roleSelectedIndex = index;
        roleStateIsNew    = false;
        OpenRolesGuiPacket.RoleEntry e = existingRoles.get(index);
        roleStateId  = e.id();
        roleStateLp  = e.lpGroup();
        roleStatePerms.clear();
        roleStatePerms.addAll(e.permissions());
        roleDirtyPerms.clear();
        if (rebuildAfter) rebuild();
    }

    private void resetRoleForm() {
        roleDirty = false;
        roleSelectedIndex = -1; roleStateIsNew = true;
        roleStateId = ""; roleStateLp = "";
        roleDirtyPerms.clear();
        roleStatePerms.clear();
        if (existingRoles.size() >= LIST_VISIBLE) roleListScroll = existingRoles.size() - LIST_VISIBLE + 1;
        rebuild();
    }

    @OnlyIn(Dist.CLIENT)
    private static class RestrictionListSubScreen extends Screen {
        private final ProfessionEditorScreen parent;
        private final List<String> items;
        private final Set<String> dirtyEntries;
        private final List<String> pool;
        private final String hint;
        private final boolean instantApply;
        private final Runnable onChangeApplyOnly;

        private int scrollOffset = 0;
        private String input = "";
        private final List<String> suggestions = new ArrayList<>();
        private int suggestionIndex = -1;
        private int boxX, boxY, boxW;

        RestrictionListSubScreen(ProfessionEditorScreen parent, List<String> items, Set<String> dirtyEntries,
                                 List<String> pool, String hint, boolean instantApply, Runnable onChangeApplyOnly) {
            super(Component.literal(""));
            this.parent            = parent;
            this.items              = items;
            this.dirtyEntries       = dirtyEntries;
            this.pool               = pool;
            this.hint                = hint;
            this.instantApply        = instantApply;
            this.onChangeApplyOnly   = onChangeApplyOnly;
        }

        @Override
        protected void init() {
            int midX   = this.width / 2;
            int startY = 46;
            int rowH   = 20;
            int visMax = Math.max(1, (this.height - startY - 70) / rowH);
            int first  = Math.max(0, Math.min(scrollOffset, Math.max(0, items.size() - visMax)));
            int last   = Math.min(items.size() - 1, first + visMax - 1);

            for (int i = first; i <= last; i++) {
                final int idx = i;
                final String value = items.get(i);
                String color = dirtyEntries.contains(value) ? "§e§o" : "§7";

                addRenderableWidget(Button.builder(Component.literal("§c×"),
                                btn -> removeAt(idx, value))
                        .pos(midX - 160, startY + (i - first) * rowH).size(16, 18).build());

                addRenderableWidget(Button.builder(Component.literal(color + value), btn -> {})
                        .pos(midX - 140, startY + (i - first) * rowH).size(270, 18).build());
            }

            boolean canScrollUp = scrollOffset > 0;
            boolean canScrollDown = last < items.size() - 1;

            addRenderableWidget(Button.builder(Component.literal(canScrollUp ? "▲" : "§8▲"),
                            btn -> { if (canScrollUp) { scrollOffset--; rebuild(); } })
                    .pos(midX + 134, startY).size(16, 18).build());
            addRenderableWidget(Button.builder(Component.literal(canScrollDown ? "▼" : "§8▼"),
                            btn -> { if (canScrollDown) { scrollOffset++; rebuild(); } })
                    .pos(midX + 134, startY + Math.max(0, last - first) * rowH).size(16, 18).build());

            int addY = this.height - 60;
            boxX = midX - 150;
            boxY = addY;
            boxW = 260;
            EditBox box = new EditBox(this.font, boxX, boxY, boxW, 18, Component.literal("Entry"));
            box.setHint(Component.literal(hint));
            box.setMaxLength(256);
            box.setValue(input);
            box.setResponder(val -> { input = val; suggestionIndex = -1; updateSuggestions(val); });
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("§a+"), btn -> addEntry())
                    .pos(boxX + boxW + 2, addY).size(16, 18).build());

            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(parent))
                    .pos(midX - 40, this.height - 30).size(80, 20).build());
        }

        private void removeAt(int idx, String value) {
            items.remove(idx);
            dirtyEntries.remove(value);
            if (instantApply) onChangeApplyOnly.run();
            rebuild();
        }

        private void addEntry() {
            String val = input.trim();
            if (!val.isEmpty() && !items.contains(val)) {
                items.add(val);
                dirtyEntries.add(val);
                if (instantApply) onChangeApplyOnly.run();
            }
            input = "";
            closeSuggestions();
            rebuild();
        }

        private void updateSuggestions(String in) {
            suggestions.clear();
            suggestionIndex = -1;
            if (in.isBlank() || pool == null) return;
            String lower = in.toLowerCase().trim();
            for (String id : pool) {
                if (id.startsWith(lower)) suggestions.add(id);
                if (suggestions.size() >= 24) break;
            }
            if (suggestions.size() < 8) {
                for (String id : pool) {
                    if (!id.startsWith(lower) && id.contains(lower)) suggestions.add(id);
                    if (suggestions.size() >= 24) break;
                }
            }
        }

        private void closeSuggestions() { suggestions.clear(); suggestionIndex = -1; }
        private void rebuild() { clearWidgets(); init(); }

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
                    if (suggestionIndex >= 0) { addEntry(); return true; }
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeSuggestions(); return true; }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void applySuggestion(String value) {
            input = value;
            children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w)
                    .filter(b -> b.getX() == boxX && b.getY() == boxY)
                    .findFirst().ifPresent(b -> b.setValue(value));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!suggestions.isEmpty()) {
                int dropY = boxY + 19;
                int count = Math.min(suggestions.size(), 8);
                if (mx >= boxX && mx <= boxX + boxW && my >= dropY && my <= dropY + count * 12) {
                    int clicked = (int) ((my - dropY) / 12);
                    if (clicked >= 0 && clicked < count) {
                        input = suggestions.get(clicked);
                        addEntry();
                        return true;
                    }
                }
                closeSuggestions();
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            renderBackground(g, mx, my, delta);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font,
                    I18n.get("rpessentials.gui.restriction_list.count", items.size()),
                    this.width / 2, 20, 0xFFD700);
            if (!suggestions.isEmpty()) renderSuggestionsDropdown(g);
        }

        private void renderSuggestionsDropdown(GuiGraphics g) {
            int count = Math.min(suggestions.size(), 8);
            int dropY = boxY + 19;
            int dropH = count * 12 + 2;
            g.fill(boxX, dropY, boxX + boxW, dropY + dropH, 0xFF1A1A1A);
            for (int i = 0; i < count; i++) {
                int lineY = dropY + 1 + i * 12;
                boolean sel = i == suggestionIndex;
                if (sel) g.fill(boxX + 1, lineY, boxX + boxW - 1, lineY + 12, 0xFF2A4A7F);
                g.drawString(this.font, suggestions.get(i), boxX + 3, lineY + 2, sel ? 0xFFFFFF : 0xAAAAAA, false);
            }
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            int visMax     = Math.max(1, (this.height - 46 - 70) / 20);
            int maxScroll  = Math.max(0, items.size() - visMax);
            if (sy < 0 && scrollOffset < maxScroll) { scrollOffset++; rebuild(); return true; }
            if (sy > 0 && scrollOffset > 0) { scrollOffset--; rebuild(); return true; }
            return super.mouseScrolled(mx, my, sx, sy);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================
    private void rebuild() { clearWidgets(); init(); }

    @Override
    public boolean isPauseScreen() { return false; }
}