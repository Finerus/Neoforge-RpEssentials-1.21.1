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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.network.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ProfessionEditorScreen extends Screen {

    private record IconRow(int x, int y, String id) {}

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    // =========================================================================
    // DONNEES
    // =========================================================================
    private final List<OpenProfessionGuiPacket.ProfessionEntry> existingProfessions;
    private final List<OpenRolesGuiPacket.RoleEntry> existingRoles;
    private int mainTab;

    // Professions
    private String profStateId       = "";
    private String profStateName     = "";
    private boolean profStateIsNew   = true;
    private int profSelectedIndex    = -1;
    private int profColorIndex       = 0;
    private int profActiveTab        = 0;
    private int profListScroll       = 0;
    private String profPendingDelete = null;
    private Button profInCreationButton;
    private boolean profDirty = false;
    private final Set<String> profDirtyEntries = new HashSet<>();

    private List<String> allowedCrafts    = new ArrayList<>();
    private List<String> allowedBlocks    = new ArrayList<>();
    private List<String> allowedItems     = new ArrayList<>();
    private List<String> allowedEquipment = new ArrayList<>();

    // Roles
    private String roleStateId       = "";
    private String roleStateLp       = "";
    private boolean roleStateIsNew   = true;
    private int roleSelectedIndex    = -1;
    private int roleListScroll       = 0;
    private String rolePendingDelete = null;
    private Button roleInCreationButton;
    private boolean roleDirty = false;
    private final Set<String> roleDirtyPerms = new LinkedHashSet<>();
    private final Set<String> roleStatePerms = new LinkedHashSet<>();

    // Restrictions globales
    private List<String> globalBlockedCrafts;
    private List<String> globalUnbreakableBlocks;
    private List<String> globalBlockedItems;
    private List<String> globalBlockedEquipment;
    private List<String> globalContainerRestrictions;
    private final List<List<String>> globalSaved = new ArrayList<>();
    private final Set<String> globalDirtyEntries = new HashSet<>();
    private int globalActiveCategory = 0;

    // Liste affichee
    private int restrictListScroll = 0;
    private String restrictPendingDelete = null;
    private final List<IconRow> iconRows = new ArrayList<>();

    private static final String[] GLOBAL_CATEGORY_KEYS = {
            "crafts", "blocks", "items", "equipment", "containers"
    };

    private static final String[] ALL_PERMISSIONS = {
            "isStaff", "tabWhitelist", "scheduleWhitelist", "professionWhitelist", "seeNicknames",
            "seeAll", "bypassWorldBorder", "spyMessages", "bypassAutoUnwhitelist", "bypassDeathRpWhitelist"
    };

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
        this.mainTab             = initialTab;

        if (global != null) {
            this.globalBlockedCrafts         = new ArrayList<>(global.blockedCrafts());
            this.globalUnbreakableBlocks     = new ArrayList<>(global.unbreakableBlocks());
            this.globalBlockedItems          = new ArrayList<>(global.blockedItems());
            this.globalBlockedEquipment      = new ArrayList<>(global.blockedEquipment());
            this.globalContainerRestrictions = new ArrayList<>(global.containerRestrictions());
        } else {
            this.globalBlockedCrafts         = new ArrayList<>();
            this.globalUnbreakableBlocks     = new ArrayList<>();
            this.globalBlockedItems          = new ArrayList<>();
            this.globalBlockedEquipment      = new ArrayList<>();
            this.globalContainerRestrictions = new ArrayList<>();
        }
        snapshotGlobal();
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

    private static ItemStack iconFor(String raw) {
        return ICON_CACHE.computeIfAbsent(raw, key -> {
            String clean = key.contains(";") ? key.substring(0, key.indexOf(';')) : key;
            clean = clean.trim();
            if (clean.isEmpty() || clean.contains("*")) return ItemStack.EMPTY;
            ResourceLocation rl = ResourceLocation.tryParse(clean);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
            if (item == Items.AIR) item = BuiltInRegistries.BLOCK.getOptional(rl).map(Block::asItem).orElse(Items.AIR);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    private String permLabel(String permId) {
        return I18n.get("rpessentials.gui.roles.perm." + permId);
    }

    // =========================================================================
    // INIT
    // =========================================================================
    @Override
    protected void init() {
        iconRows.clear();
        buildMainTabs();
        if      (mainTab == 0) initProfessions();
        else if (mainTab == 1) initRoles();
        else                   initGlobalRestrictions();
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
                        btn -> { mainTab = 0; resetRestrictionView(); rebuild(); })
                .pos(x, y).size(tabW, 13).build());

        String roleLabel = mainTab == 1
                ? "§9§l" + I18n.get("rpessentials.gui.roles.tab_main_roles")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_roles");
        addRenderableWidget(Button.builder(Component.literal(roleLabel),
                        btn -> { mainTab = 1; resetRestrictionView(); rebuild(); })
                .pos(x + tabW + gap, y).size(tabW, 13).build());

        String globalLabel = mainTab == 2
                ? "§c§l" + I18n.get("rpessentials.gui.roles.tab_main_global")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_global");
        addRenderableWidget(Button.builder(Component.literal(globalLabel),
                        btn -> { mainTab = 2; resetRestrictionView(); rebuild(); })
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
    // LISTE DE RESTRICTIONS (commune)
    // =========================================================================
    private void resetRestrictionView() {
        restrictListScroll = 0;
        restrictPendingDelete = null;
    }

    private int buildRestrictionList(int formX, int formW, int top, int bottom, List<String> list,
                                     Set<String> dirty, BiConsumer<Integer, String> remover) {
        int rowH = 18;
        int visMax = Math.max(1, (bottom - top) / rowH);
        int maxScroll = Math.max(0, list.size() - visMax);
        restrictListScroll = Math.max(0, Math.min(restrictListScroll, maxScroll));
        int last = Math.min(list.size() - 1, restrictListScroll + visMax - 1);
        int rowW = Math.max(60, Math.min(formW - 70, 320));

        for (int i = restrictListScroll; i <= last; i++) {
            final int idx = i;
            final String value = list.get(i);
            int y = top + (i - restrictListScroll) * rowH;
            boolean pending = value.equals(restrictPendingDelete);

            addRenderableWidget(Button.builder(Component.literal(pending ? "§c!" : "§cx"), btn -> {
                        if (value.equals(restrictPendingDelete)) {
                            restrictPendingDelete = null;
                            remover.accept(idx, value);
                        } else {
                            restrictPendingDelete = value;
                            rebuild();
                        }
                    })
                    .pos(formX, y).size(16, 16)
                    .tooltip(Tooltip.create(Component.translatable(pending
                            ? "rpessentials.gui.restriction_list.confirm_delete_tooltip"
                            : "rpessentials.gui.restriction_list.delete_tooltip")))
                    .build());

            iconRows.add(new IconRow(formX + 19, y, value));

            String color = dirty.contains(value) ? "§e§o" : "§7";
            addRenderableWidget(Button.builder(Component.literal(color + value), btn -> {})
                    .pos(formX + 38, y).size(rowW, 16).build());
        }

        if (maxScroll > 0 && visMax >= 2) {
            int sx = formX + 38 + rowW + 2;
            boolean up = restrictListScroll > 0;
            boolean down = restrictListScroll < maxScroll;
            addRenderableWidget(Button.builder(Component.literal(up ? "▲" : "§8▲"),
                            btn -> { if (up) { restrictListScroll--; rebuild(); } })
                    .pos(sx, top).size(16, 16).build());
            addRenderableWidget(Button.builder(Component.literal(down ? "▼" : "§8▼"),
                            btn -> { if (down) { restrictListScroll++; rebuild(); } })
                    .pos(sx, top + (visMax - 1) * rowH).size(16, 16).build());
        }

        int shown = last - restrictListScroll + 1;
        return shown <= 0 ? top + 14 : top + shown * rowH + 2;
    }

    private void addManageButton(int formX, int formW, int y, List<String> list, Set<String> dirty,
                                 List<String> pool, String hint, Runnable onChange,
                                 Consumer<String> onRemove, Runnable onSave) {
        addRenderableWidget(Button.builder(
                        Component.literal(I18n.get("rpessentials.gui.restriction_list.manage", list.size())),
                        btn -> Minecraft.getInstance().setScreen(new RestrictionListSubScreen(
                                this, list, dirty, pool, hint, onChange, onRemove, onSave)))
                .pos(formX, y).size(Math.min(formW - 4, 200), 18).build());
    }

    // =========================================================================
    // ONGLET PROFESSIONS
    // =========================================================================
    private void initProfessions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.profession_editor.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(profStateId);
        idBox.setEditable(profStateIsNew);
        idBox.setResponder(val -> profStateId = val);
        addRenderableWidget(idBox);
        y += 46;

        EditBox nameBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.translatable("rpessentials.gui.profession_editor.name_label"));
        nameBox.setHint(Component.translatable("rpessentials.gui.profession_editor.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setValue(profStateName);
        nameBox.setResponder(val -> { profStateName = val; profDirty = true; });
        addRenderableWidget(nameBox);
        y += 46;

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

        int tabW = Math.max(40, (formW - 6) / 4);
        for (int i = 0; i < PROF_TAB_KEYS.length; i++) {
            final int ti = i;
            String label = i == profActiveTab
                    ? "§e§l" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i])
                    : "§7" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { profActiveTab = ti; resetRestrictionView(); rebuild(); })
                    .pos(formX + i * (tabW + 2), y).size(tabW, 16).build());
        }
        y += 20;

        addManageButton(formX, formW, y, getProfActiveList(), profDirtyEntries, getPoolForCurrentTab(),
                "§7e.g: minecraft:iron_sword", () -> profDirty = true, v -> profDirty = true,
                this::saveProfession);

        int btnY = this.height - 35;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_save"),
                        btn -> saveProfession())
                .pos(formX, btnY).size(160, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 65, btnY).size(55, 20).build());

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
    // ONGLET ROLES
    // =========================================================================
    private void initRoles() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        int y     = PANEL_TOP + 14;

        EditBox idBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 160), 18,
                Component.translatable("rpessentials.gui.roles.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.roles.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(roleStateId);
        idBox.setEditable(roleStateIsNew);
        idBox.setResponder(val -> roleStateId = val);
        addRenderableWidget(idBox);
        y += 38;

        EditBox lpBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 160), 18,
                Component.translatable("rpessentials.gui.roles.lp_label"));
        lpBox.setHint(Component.translatable("rpessentials.gui.roles.lp_hint"));
        lpBox.setMaxLength(64);
        lpBox.setValue(roleStateLp);
        lpBox.setEditable(canManageRoles());
        lpBox.setResponder(val -> { roleStateLp = val; roleDirty = true; });
        addRenderableWidget(lpBox);
        y += 38;

        int checkW = formW - 4;
        for (int i = 0; i < ALL_PERMISSIONS.length; i++) {
            final String perm = ALL_PERMISSIONS[i];
            boolean checked   = roleStatePerms.contains(perm);
            boolean modified  = roleDirtyPerms.contains(perm);
            String base       = checked ? "§a[✔] " : "§c[X] ";
            String textColor  = modified ? "§e§o" : "§f";
            Button permBtn = Button.builder(Component.literal(base + textColor + permLabel(perm)), btn -> {
                if (roleStatePerms.contains(perm)) roleStatePerms.remove(perm);
                else roleStatePerms.add(perm);
                roleDirty = true;
                roleDirtyPerms.add(perm);
                rebuild();
            }).pos(formX, y + i * 18).size(checkW, 16).build();
            permBtn.active = canManageRoles();
            addRenderableWidget(permBtn);
        }

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
    // ONGLET RESTRICTIONS GLOBALES
    // =========================================================================
    private void initGlobalRestrictions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;
        List<String> list = getGlobalActiveList();
        List<String> pool = globalActiveCategory == 4 ? null
                : (globalActiveCategory == 1 ? allBlockIds : allItemIds);
        String hint = globalActiveCategory == 4 ? "§7e.g: minecraft:anvil;blacksmith" : "§7e.g: minecraft:diamond_sword";

        int listEnd = buildRestrictionList(formX, formW, PANEL_TOP + 18, this.height - 70, list,
                globalDirtyEntries, this::removeGlobalRestriction);
        addManageButton(formX, formW, listEnd, list, globalDirtyEntries, pool, hint,
                () -> {}, this::onGlobalEntryRemoved, this::saveGlobalRestrictions);

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

    private void buildGlobalLeftPanel() {
        for (int i = 0; i < GLOBAL_CATEGORY_KEYS.length; i++) {
            final int ci = i;
            boolean selected = i == globalActiveCategory;
            String label = (selected ? "§e§l▶ " : "§7  ")
                    + I18n.get("rpessentials.gui.global." + GLOBAL_CATEGORY_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { globalActiveCategory = ci; resetRestrictionView(); rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + i * ROW_H).size(LIST_W, ROW_H - 2).build());
        }
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

    private List<List<String>> globalWorking() {
        return List.of(globalBlockedCrafts, globalUnbreakableBlocks, globalBlockedItems,
                globalBlockedEquipment, globalContainerRestrictions);
    }

    private void snapshotGlobal() {
        globalSaved.clear();
        for (List<String> l : globalWorking()) globalSaved.add(new ArrayList<>(l));
    }

    private void sendGlobal(List<List<String>> lists) {
        PacketDistributor.sendToServer(new SaveGlobalRestrictionsPacket(
                new ArrayList<>(lists.get(0)), new ArrayList<>(lists.get(1)), new ArrayList<>(lists.get(2)),
                new ArrayList<>(lists.get(3)), new ArrayList<>(lists.get(4))));
    }

    private void saveGlobalRestrictions() {
        sendGlobal(globalWorking());
        snapshotGlobal();
        globalDirtyEntries.clear();
    }

    private void onGlobalEntryRemoved(String value) {
        globalDirtyEntries.remove(value);
        if (!globalSaved.get(globalActiveCategory).remove(value)) return;
        sendGlobal(globalSaved);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    I18n.get("rpessentials.gui.global.removed_feedback", value)), false);
        }
    }

    private void removeGlobalRestriction(int index, String value) {
        getGlobalActiveList().remove(index);
        onGlobalEntryRemoved(value);
        rebuild();
    }

    // =========================================================================
    // RENDU
    // =========================================================================
    private int tabLineColor() {
        return switch (mainTab) {
            case 1 -> 0xFF5555FF;
            case 2 -> 0xFFFF5555;
            default -> 0xFFFFFF55;
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

        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

        if (mainTab == 0) {
            g.drawString(this.font,
                    I18n.get("rpessentials.gui.profession_editor.professions_header", existingProfessions.size()),
                    MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);
            if (existingProfessions.isEmpty())
                g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.profession_editor.no_profession"),
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

        for (IconRow r : iconRows) {
            ItemStack st = iconFor(r.id());
            if (!st.isEmpty()) g.renderItem(st, r.x(), r.y());
        }
    }

    private void renderGlobalRestrictionsOverlay(GuiGraphics g, int formX) {
        g.drawString(this.font,
                tabColorCode() + I18n.get("rpessentials.gui.global.title") + " §8- §e"
                        + I18n.get("rpessentials.gui.global." + GLOBAL_CATEGORY_KEYS[globalActiveCategory]),
                formX, PANEL_TOP + 5, 0xFFFFFF, false);
        if (getGlobalActiveList().isEmpty()) {
            g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.global.no_entries"),
                    formX, PANEL_TOP + 20, 0x666666, false);
        }
    }

    private void renderProfessionsOverlay(GuiGraphics g, int formX) {
        String modeLabel = profStateIsNew
                ? tabColorCode() + I18n.get("rpessentials.gui.profession_editor.mode_new")
                : tabColorCode() + I18n.get("rpessentials.gui.profession_editor.mode_edit", profStateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int formW = this.width - formX - MARGIN;
        int y = PANEL_TOP + 14;
        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.id_label_draw"),
                formX, y + 6, 0x888888, false);
        y += 46;
        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.name_label_draw"),
                formX, y + 6, 0x888888, false);
        y += 46;

        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.color_label"),
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
    }

    private void renderRolesOverlay(GuiGraphics g, int formX) {
        String modeLabel = roleStateIsNew
                ? tabColorCode() + I18n.get("rpessentials.gui.roles.mode_new")
                : tabColorCode() + I18n.get("rpessentials.gui.roles.mode_edit", roleStateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int y = PANEL_TOP + 14;
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.id_label"), formX, y + 6, 0x888888, false);
        y += 38;
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.lp_label"), formX, y + 6, 0x888888, false);
        y += 38;

        g.fill(formX, y - 2, this.width - MARGIN, y - 1, 0xFF333333);
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.permissions_header"), formX, y - 12, 0xFFD700, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < LIST_W + MARGIN * 2) {
            if (mainTab == 0) {
                int max = Math.max(0, existingProfessions.size() - LIST_VISIBLE);
                profListScroll = (int) Math.max(0, Math.min(max, profListScroll - sy));
            } else if (mainTab == 1) {
                int max = Math.max(0, existingRoles.size() - LIST_VISIBLE);
                roleListScroll = (int) Math.max(0, Math.min(max, roleListScroll - sy));
            } else {
                return super.mouseScrolled(mx, my, sx, sy);
            }
            rebuild();
            return true;
        }
        if (mainTab == 2) {
            restrictListScroll = (int) Math.max(0, restrictListScroll - sy);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // =========================================================================
    // LOGIQUE PROFESSIONS
    // =========================================================================
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
        profDirtyEntries.clear();
        resetRestrictionView();
        if (rebuildAfter) rebuild();
    }

    private void resetProfForm() {
        profDirty = false;
        profSelectedIndex = -1; profStateIsNew = true;
        profStateId = ""; profStateName = ""; profColorIndex = 0; profActiveTab = 0;
        allowedCrafts = new ArrayList<>(); allowedBlocks = new ArrayList<>();
        allowedItems = new ArrayList<>(); allowedEquipment = new ArrayList<>();
        profDirtyEntries.clear();
        if (existingProfessions.size() >= LIST_VISIBLE) profListScroll = existingProfessions.size() - LIST_VISIBLE + 1;
        resetRestrictionView();
        rebuild();
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
        return profActiveTab == 1 ? allBlockIds : allItemIds;
    }

    // =========================================================================
    // LOGIQUE ROLES
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

    private void rebuild() { clearWidgets(); init(); }

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================================
    // SOUS-ECRAN DE GESTION DES ENTREES
    // =========================================================================
    @OnlyIn(Dist.CLIENT)
    private static class RestrictionListSubScreen extends Screen {

        private static final int SUG_H = 14;
        private static final int SUG_MAX = 8;

        private final ProfessionEditorScreen parent;
        private final List<String> items;
        private final Set<String> dirtyEntries;
        private final List<String> pool;
        private final String hint;
        private final Runnable onChange;
        private final Consumer<String> onRemove;
        private final Runnable onSave;
        private final List<IconRow> rows = new ArrayList<>();
        private final List<String> suggestions = new ArrayList<>();

        private int scrollOffset = 0;
        private int sugScroll = 0;
        private String input = "";
        private String query = "";
        private String pendingDelete = null;
        private int suggestionIndex = -1;
        private boolean applying = false;
        private EditBox inputBox;
        private int boxX, boxY, boxW;

        RestrictionListSubScreen(ProfessionEditorScreen parent, List<String> items, Set<String> dirtyEntries,
                                 List<String> pool, String hint, Runnable onChange, Consumer<String> onRemove,
                                 Runnable onSave) {
            super(Component.literal(""));
            this.parent       = parent;
            this.items        = items;
            this.dirtyEntries = dirtyEntries;
            this.pool         = pool;
            this.hint         = hint;
            this.onChange     = onChange;
            this.onRemove     = onRemove;
            this.onSave       = onSave;
        }

        @Override
        protected void init() {
            rows.clear();
            int midX   = this.width / 2;
            int startY = 46;
            int rowH   = 20;
            int visMax = Math.max(1, (this.height - startY - 80) / rowH);
            int maxScroll = Math.max(0, items.size() - visMax);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            int first = scrollOffset;
            int last  = Math.min(items.size() - 1, first + visMax - 1);

            for (int i = first; i <= last; i++) {
                final int idx = i;
                final String value = items.get(i);
                int y = startY + (i - first) * rowH;
                String color = dirtyEntries.contains(value) ? "§e§o" : "§7";
                boolean pendingRemove = value.equals(pendingDelete);

                addRenderableWidget(Button.builder(Component.literal(pendingRemove ? "§c!" : "§c×"),
                                btn -> removeAt(idx, value))
                        .pos(midX - 170, y).size(16, 18)
                        .tooltip(Tooltip.create(Component.translatable(pendingRemove
                                ? "rpessentials.gui.restriction_list.confirm_delete_tooltip"
                                : "rpessentials.gui.restriction_list.delete_tooltip")))
                        .build());

                rows.add(new IconRow(midX - 151, y + 1, value));

                addRenderableWidget(Button.builder(Component.literal(color + value), btn -> {})
                        .pos(midX - 132, y).size(262, 18).build());
            }

            if (maxScroll > 0 && visMax >= 2) {
                boolean up = scrollOffset > 0;
                boolean down = scrollOffset < maxScroll;
                addRenderableWidget(Button.builder(Component.literal(up ? "▲" : "§8▲"),
                                btn -> { if (up) { scrollOffset--; rebuild(); } })
                        .pos(midX + 134, startY).size(16, 18).build());
                addRenderableWidget(Button.builder(Component.literal(down ? "▼" : "§8▼"),
                                btn -> { if (down) { scrollOffset++; rebuild(); } })
                        .pos(midX + 134, startY + (visMax - 1) * rowH).size(16, 18).build());
            }

            boxX = midX - 170;
            boxY = this.height - 60;
            boxW = 300;
            inputBox = new EditBox(this.font, boxX, boxY, boxW, 18, Component.literal("Entry"));
            inputBox.setHint(Component.literal(hint));
            inputBox.setMaxLength(256);
            inputBox.setValue(input);
            inputBox.setResponder(val -> {
                if (applying) return;
                input = val;
                query = val;
                suggestionIndex = -1;
                updateSuggestions(val);
            });
            addRenderableWidget(inputBox);
            addRenderableWidget(Button.builder(Component.literal("§a+"), btn -> addEntry())
                    .pos(boxX + boxW + 2, boxY).size(16, 18).build());

            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.restriction_list.save"),
                            btn -> { onSave.run(); rebuild(); })
                    .pos(midX - 130, this.height - 30).size(120, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(parent))
                    .pos(midX + 4, this.height - 30).size(80, 20).build());
        }

        private void removeAt(int idx, String value) {
            if (!value.equals(pendingDelete)) {
                pendingDelete = value;
                rebuild();
                return;
            }
            pendingDelete = null;
            items.remove(idx);
            dirtyEntries.remove(value);
            onRemove.accept(value);
            rebuild();
        }

        private void addEntry() {
            String val = input.trim();
            if (!val.isEmpty() && !items.contains(val)) {
                items.add(val);
                dirtyEntries.add(val);
                onChange.run();
            }
            input = "";
            query = "";
            closeSuggestions();
            rebuild();
        }

        private void updateSuggestions(String in) {
            sugScroll = 0;
            suggestions.clear();
            if (in.isBlank() || pool == null) return;
            String lower = in.toLowerCase().trim();
            for (String id : pool) {
                if (id.startsWith(lower)) suggestions.add(id);
                if (suggestions.size() >= 24) break;
            }
            if (suggestions.size() < 24) {
                for (String id : pool) {
                    if (!id.startsWith(lower) && id.contains(lower)) suggestions.add(id);
                    if (suggestions.size() >= 24) break;
                }
            }
        }

        private void closeSuggestions() { suggestions.clear(); suggestionIndex = -1; sugScroll = 0; }
        private void rebuild() { clearWidgets(); init(); }

        private void applySuggestion(String value) {
            input = value;
            applying = true;
            inputBox.setValue(value);
            applying = false;
        }

        private int sugStart() { return sugScroll; }

        private void cycle(int dir) {
            suggestionIndex = suggestionIndex < 0 && dir < 0
                    ? suggestions.size() - 1
                    : Math.floorMod(suggestionIndex + dir, suggestions.size());
            applySuggestion(suggestions.get(suggestionIndex));
            if (suggestionIndex < sugScroll) sugScroll = suggestionIndex;
            else if (suggestionIndex >= sugScroll + SUG_MAX) sugScroll = suggestionIndex - SUG_MAX + 1;
        }

        private int dropdownTop() {
            int count = Math.min(suggestions.size(), SUG_MAX);
            return boxY - (count * SUG_H + 4) - 2;
        }

        private int suggestionAt(double mx, double my) {
            int count = Math.min(suggestions.size(), SUG_MAX);
            int rowsTop = dropdownTop() + 2;
            if (mx < boxX || mx >= boxX + boxW || my < rowsTop || my >= rowsTop + count * SUG_H) return -1;
            return sugStart() + (int) ((my - rowsTop) / SUG_H);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!suggestions.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_TAB) {
                    cycle((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) { cycle(1); return true; }
                if (keyCode == GLFW.GLFW_KEY_UP) { cycle(-1); return true; }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeSuggestions(); return true; }
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && inputBox != null && inputBox.isFocused() && !input.isBlank()) {
                addEntry();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!suggestions.isEmpty()) {
                int idx = suggestionAt(mx, my);
                if (idx >= 0 && idx < suggestions.size()) {
                    input = suggestions.get(idx);
                    addEntry();
                    return true;
                }
                boolean onBox = mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + 18;
                if (!onBox) closeSuggestions();
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (!suggestions.isEmpty() && mx >= boxX && mx < boxX + boxW
                    && my >= dropdownTop() && my < boxY + 18) {
                int max = Math.max(0, suggestions.size() - SUG_MAX);
                sugScroll = Math.max(0, Math.min(max, sugScroll - (int) Math.signum(sy)));
                return true;
            }
            int visMax    = Math.max(1, (this.height - 46 - 80) / 20);
            int maxScroll = Math.max(0, items.size() - visMax);
            if (sy < 0 && scrollOffset < maxScroll) { scrollOffset++; rebuild(); return true; }
            if (sy > 0 && scrollOffset > 0) { scrollOffset--; rebuild(); return true; }
            return super.mouseScrolled(mx, my, sx, sy);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            g.fill(0, 0, this.width, this.height, 0xAA000000);
            super.render(g, mx, my, delta);
            for (IconRow r : rows) {
                ItemStack st = iconFor(r.id());
                if (!st.isEmpty()) g.renderItem(st, r.x(), r.y());
            }
            g.drawCenteredString(this.font,
                    I18n.get("rpessentials.gui.restriction_list.count", items.size()),
                    this.width / 2, 20, 0xFFD700);
            renderDropdown(g, mx, my);
        }

        private void renderDropdown(GuiGraphics g, int mx, int my) {
            if (suggestions.isEmpty()) return;
            int count = Math.min(suggestions.size(), SUG_MAX);
            int h = count * SUG_H + 4;
            int y = dropdownTop();
            int start = sugStart();
            int hovered = suggestionAt(mx, my);
            String lower = query.toLowerCase().trim();

            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(boxX - 1, y - 1, boxX + boxW + 1, y + h + 1, 0xFF555555);
            g.fill(boxX, y, boxX + boxW, y + h, 0xF01A1A1A);

            for (int i = 0; i < count; i++) {
                int real = start + i;
                int ly = y + 2 + i * SUG_H;
                String id = suggestions.get(real);
                boolean sel = real == suggestionIndex;
                boolean hov = real == hovered;
                if (sel) g.fill(boxX + 1, ly, boxX + boxW - 1, ly + SUG_H, 0xFF2A4A7F);
                if (hov) g.fill(boxX + 1, ly, boxX + boxW - 1, ly + SUG_H, 0xFF3B6FB5);

                ItemStack st = iconFor(id);
                if (!st.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(boxX + 3, ly + 1, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.renderItem(st, 0, 0);
                    g.pose().popPose();
                }

                int tx = boxX + 18;
                int base = (sel || hov) ? 0xFFFFFF : 0xAAAAAA;
                int at = lower.isEmpty() ? -1 : id.indexOf(lower);
                if (at < 0) {
                    g.drawString(this.font, id, tx, ly + 3, base, false);
                } else {
                    String a = id.substring(0, at);
                    String b = id.substring(at, at + lower.length());
                    String c = id.substring(at + lower.length());
                    g.drawString(this.font, a, tx, ly + 3, base, false);
                    int bx = tx + this.font.width(a);
                    g.drawString(this.font, b, bx, ly + 3, 0xFFFF55, false);
                    g.drawString(this.font, c, bx + this.font.width(b), ly + 3, base, false);
                }
            }
            if (suggestions.size() > SUG_MAX) {
                g.drawString(this.font, "§8+" + (suggestions.size() - SUG_MAX), boxX + 2, y - 10, 0x777777, false);
            }
            g.pose().popPose();
        }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

        @Override
        public boolean isPauseScreen() { return false; }
    }
}