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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.util.RpEssentialsPatternUtils;
import net.rp.rpessentials.network.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ProfessionEditorScreen extends Screen {


    private static final Map<String, List<ItemStack>> ICON_CACHE = new HashMap<>();

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
    /** Left lists: Minecraft list widgets, kept between rebuilds (scroll position). */
    private ButtonRowList profList, roleList;
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
    /** Restriction list of the Global tab: Minecraft list widget. */
    private ButtonRowList restrictList;
    private String restrictPendingDelete = null;

    private static final String[] GLOBAL_CATEGORY_KEYS = {
            "crafts", "blocks", "items", "equipment", "containers"
    };

    private static final String[] ALL_PERMISSIONS = {
            "isStaff", "tabWhitelist", "scheduleWhitelist", "professionWhitelist", "seeNicknames",
            "seeAll", "bypassWorldBorder", "spyMessages", "bypassAutoUnwhitelist", "bypassDeathRpWhitelist"
    };

    private static final char[]   COLOR_CHARS = ColorPalette.CHARS;
    private static final String[] COLOR_KEYS  = ColorPalette.KEYS;
    private static final String[] PROF_TAB_KEYS = { "crafts","blocks","items","equipment" };

    private static final int LIST_W       = 140;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;

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

    private static List<String> containerPool = null;

    private static boolean opensMenu(ResourceLocation id, Block b) {
        if (b instanceof AbstractChestBlock<?> || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock
                || b instanceof AbstractFurnaceBlock || b instanceof BrewingStandBlock || b instanceof HopperBlock
                || b instanceof DispenserBlock || b instanceof CraftingTableBlock || b instanceof AnvilBlock
                || b instanceof GrindstoneBlock || b instanceof StonecutterBlock || b instanceof LoomBlock
                || b instanceof CartographyTableBlock || b instanceof SmithingTableBlock
                || b instanceof EnchantingTableBlock || b instanceof BeaconBlock || b instanceof LecternBlock
                || b instanceof CrafterBlock) return true;
        return b instanceof BaseEntityBlock && !id.getNamespace().equals("minecraft");
    }

    private static List<String> containerPool() {
        if (containerPool == null) {
            containerPool = BuiltInRegistries.BLOCK.keySet().stream()
                    .filter(id -> opensMenu(id, BuiltInRegistries.BLOCK.get(id)))
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        }
        return containerPool;
    }

    private static List<ItemStack> resolveIcons(String raw) {
        String clean = (raw.contains(";") ? raw.substring(0, raw.indexOf(';')) : raw).trim();
        List<ItemStack> out = new ArrayList<>();
        if (clean.isEmpty()) return out;
        if (clean.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(clean.substring(1));
            if (rl == null) return out;
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl)).ifPresent(set -> {
                for (var h : set) {
                    if (out.size() >= 64) break;
                    out.add(new ItemStack(h.value()));
                }
            });
            if (out.isEmpty()) {
                BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).ifPresent(set -> {
                    for (var h : set) {
                        if (out.size() >= 64) break;
                        Item it = h.value().asItem();
                        if (it != Items.AIR) out.add(new ItemStack(it));
                    }
                });
            }
            return out;
        }
        if (clean.contains("*")) {
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (out.size() >= 64) break;
                if (RpEssentialsPatternUtils.matchesPattern(id.toString(), clean)) {
                    Item it = BuiltInRegistries.ITEM.get(id);
                    if (it != Items.AIR) out.add(new ItemStack(it));
                }
            }
            return out;
        }
        ResourceLocation rl = ResourceLocation.tryParse(clean);
        if (rl == null) return out;
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
        if (item == Items.AIR) item = BuiltInRegistries.BLOCK.getOptional(rl).map(Block::asItem).orElse(Items.AIR);
        if (item != Items.AIR) out.add(new ItemStack(item));
        return out;
    }

    private static ItemStack iconFor(String raw) {
        List<ItemStack> l = ICON_CACHE.computeIfAbsent(raw, ProfessionEditorScreen::resolveIcons);
        if (l.isEmpty()) return ItemStack.EMPTY;
        return l.get((int) ((System.currentTimeMillis() / 1000L) % l.size()));
    }

    private String permLabel(String permId) {
        return I18n.get("rpessentials.gui.roles.perm." + permId);
    }

    // =========================================================================
    // INIT
    // =========================================================================
    @Override
    protected void init() {
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        
        buildMainTabs();
        if      (mainTab == 0) initProfessions();
        else if (mainTab == 1) initRoles();
        else                   initGlobalRestrictions();
    }

    private void buildMainTabs() {
        int gap   = 3;
        int tabW  = Math.min(90, (this.width - 48 - gap * 2) / 3);
        int total = tabW * 3 + gap * 2;
        int x     = (this.width - total) / 2;
        int y     = 2;

        String profLabel = mainTab == 0
                ? "§e§l" + I18n.get("rpessentials.gui.roles.tab_main_professions")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_professions");
        addRenderableWidget(Button.builder(Component.literal(profLabel),
                        btn -> { mainTab = 0; resetRestrictionView(); rebuild(); })
                .pos(x, y).size(tabW, 13).build(SkinnedButton.tab(mainTab == 0)));

        String roleLabel = mainTab == 1
                ? "§9§l" + I18n.get("rpessentials.gui.roles.tab_main_roles")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_roles");
        addRenderableWidget(Button.builder(Component.literal(roleLabel),
                        btn -> { mainTab = 1; resetRestrictionView(); rebuild(); })
                .pos(x + tabW + gap, y).size(tabW, 13).build(SkinnedButton.tab(mainTab == 1)));

        String globalLabel = mainTab == 2
                ? "§c§l" + I18n.get("rpessentials.gui.roles.tab_main_global")
                : "§7" + I18n.get("rpessentials.gui.roles.tab_main_global");
        addRenderableWidget(Button.builder(Component.literal(globalLabel),
                        btn -> { mainTab = 2; resetRestrictionView(); rebuild(); })
                .pos(x + (tabW + gap) * 2, y).size(tabW, 13).build(SkinnedButton.tab(mainTab == 2)));

        addRenderableWidget(Button.builder(Component.literal("§7↻"), btn -> {
                    ClientGuiOpener.requestedTab = mainTab;
                    PacketDistributor.sendToServer(new RequestOpenGuiPacket(RequestOpenGuiPacket.GuiType.PROFESSION));
                })
                .pos(this.width - MARGIN - 18, 2).size(18, 13)
                .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")))
                .build(SkinnedButton::new));
    }

    // =========================================================================
    // LISTE DE RESTRICTIONS (commune)
    // =========================================================================
    private void resetRestrictionView() {
        if (restrictList != null) restrictList.setScrollAmount(0);
        restrictPendingDelete = null;
    }

    private int buildRestrictionList(int formX, int formW, int top, int bottom, List<String> list,
                                     Set<String> dirty, BiConsumer<Integer, String> remover,
                                     java.util.function.Function<String, Warning> warnings) {
        // Minecraft list widget: delete button, item icon, value. Kept between rebuilds (scroll position).
        if (restrictList == null) restrictList = new ButtonRowList(Minecraft.getInstance(), 18);
        restrictList.layout(formX - 6, top, Math.min(formW, 38 + 320 + 6) + 12, bottom - top);
        int rw = restrictList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            final String value = list.get(i);
            boolean pending = value.equals(restrictPendingDelete);
            Button del = Button.builder(Component.literal(pending ? "§c!" : "§cx"), btn -> {
                        if (value.equals(restrictPendingDelete)) {
                            restrictPendingDelete = null;
                            remover.accept(idx, value);
                        } else {
                            restrictPendingDelete = value;
                            rebuild();
                        }
                    })
                    .size(16, 16)
                    .tooltip(Tooltip.create(Component.translatable(pending
                            ? "rpessentials.gui.restriction_list.confirm_delete_tooltip"
                            : "rpessentials.gui.restriction_list.delete_tooltip")))
                    .build(SkinnedButton::new);
            String color = dirty.contains(value) ? "§e§o" : "§7";
            Warning warn = warnings != null ? warnings.apply(value) : null;
            Button val = valueButton(color, value, globalActiveCategory == 4 ? null : poolFor(globalActiveCategory), this,
                    Math.max(40, rw - 38 - (warn != null ? 18 : 0)), 16);
            ButtonRowList.Row row = new ButtonRowList.Row().add(del, 0).item(() -> iconFor(value), 19, 0).add(val, 38);
            if (warn != null) row.add(warningButton(warn, 16, this::rebuild), rw - 16);
            rows.add(row);
        }
        restrictList.setRows(rows);
        addRenderableWidget(restrictList);
        return bottom + 2;
    }

    private void addManageButton(int formX, int formW, int y, List<String> list, Set<String> dirty,
                                 List<String> pool, String hint, Runnable onChange,
                                 Consumer<String> onRemove, Runnable onSave, String emptyKey,
                                 java.util.function.Function<String, Warning> warnings) {
        addRenderableWidget(Button.builder(
                        Component.literal(I18n.get("rpessentials.gui.restriction_list.manage", list.size())),
                        btn -> Minecraft.getInstance().setScreen(new RestrictionListSubScreen(
                                this, list, dirty, pool, hint, onChange, onRemove, onSave, emptyKey, warnings)))
                .pos(formX, y).size(Math.min(formW - 4, 200), 18).build(SkinnedButton::new));
    }

    // =========================================================================
    // ONGLET PROFESSIONS
    // =========================================================================
    /** Positions of the profession form, shared by init() and render(). */
    private record ProfRows(int fieldW, int nameX, int nameY, int colorY, int colCols, int tabsY, int manageY) {}

    private ProfRows profRows(int formX, int formW) {
        int y = PANEL_TOP + 14;
        // ID and name side by side when there is room, one under the other otherwise.
        boolean side = formW >= 2 * 120 + UiLayout.GAP;
        int fieldW = side ? Math.min(200, (formW - UiLayout.GAP) / 2) : Math.min(formW, 200);
        int nameX = side ? formX + fieldW + UiLayout.GAP : formX;
        int nameY = side ? y : y + 40;
        y = nameY + 40;
        int colorY = y;
        ColorPalette.Dims pal = ColorPalette.dims(formW, this.height);
        int colCols = pal.cols();
        y += 14 + pal.height() + 4;
        int tabsY = y;
        return new ProfRows(fieldW, nameX, nameY, colorY, colCols, tabsY, tabsY + 20);
    }

    private void initProfessions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        ProfRows r = profRows(formX, formW);
        int y     = PANEL_TOP + 14;

        EditBox idBox = new SkinnedEditBox(this.font, formX, y + 16, r.fieldW(), 18,
                Component.translatable("rpessentials.gui.profession_editor.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.profession_editor.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(profStateId);
        idBox.setEditable(profStateIsNew);
        idBox.setResponder(val -> profStateId = val);
        addRenderableWidget(idBox);

        EditBox nameBox = new SkinnedEditBox(this.font, r.nameX(), r.nameY() + 16, r.fieldW(), 18,
                Component.translatable("rpessentials.gui.profession_editor.name_label"));
        nameBox.setHint(Component.translatable("rpessentials.gui.profession_editor.name_hint"));
        nameBox.setMaxLength(64);
        nameBox.setValue(profStateName);
        nameBox.setResponder(val -> { profStateName = val; profDirty = true; });
        addRenderableWidget(nameBox);
        y = r.colorY();

        ColorPalette.Dims pal = ColorPalette.dims(formW, this.height);
        ColorPalette.addButtons(this::addRenderableWidget, pal, formX, y + 14,
                idx -> { profColorIndex = idx; profDirty = true; rebuild(); }, this);
        y = r.tabsY();

        int tabW = UiLayout.cellW(formW, PROF_TAB_KEYS.length, UiLayout.TAB_GAP);
        for (int i = 0; i < PROF_TAB_KEYS.length; i++) {
            final int ti = i;
            String label = i == profActiveTab
                    ? "§e§l" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i])
                    : "§7" + I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { profActiveTab = ti; resetRestrictionView(); rebuild(); })
                    .pos(UiLayout.cellX(formX, formW, PROF_TAB_KEYS.length, UiLayout.TAB_GAP, i), y).size(tabW, 16).build(SkinnedButton.tab(ti == profActiveTab)));
        }
        y = r.manageY();

        addManageButton(formX, formW, y, getProfActiveList(), profDirtyEntries, getPoolForCurrentTab(),
                I18n.get("rpessentials.gui.profession_editor.hint_item"), () -> profDirty = true, v -> profDirty = true,
                this::saveProfession, "rpessentials.gui.empty.prof." + PROF_TAB_KEYS[profActiveTab],
                profWarnings(profActiveTab));

        int btnY = this.height - 35;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_save"),
                        btn -> saveProfession())
                .pos(formX, btnY).size(160, 20).build(SkinnedButton::new));
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN * 2 - 55, btnY).size(55, 20).build(SkinnedButton::new));

        buildProfessionList();
    }

    /** Rows of the left list that fit between the top arrow and the "new" button. */
    

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

    private void buildProfessionList() {
        if (profList == null) profList = new ButtonRowList(Minecraft.getInstance(), ROW_H);
        profList.layout(MARGIN - 2, PANEL_TOP + 14, LIST_W + 4, this.height - 39 - (PANEL_TOP + 14));
        int rw = profList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();

        for (int i = 0; i < existingProfessions.size(); i++) {
            final int idx = i;
            String id = existingProfessions.get(i).id();
            boolean isPendingDelete = id.equals(profPendingDelete);
            boolean isDirtySelected = i == profSelectedIndex && profDirty && !isPendingDelete;

            String prefix = i == profSelectedIndex ? "§e> " : "  ";
            String style  = isPendingDelete ? "§c" : (isDirtySelected ? "§e§o" : "");
            String suffix = isDirtySelected ? " *" : "";
            OpenProfessionGuiPacket.ProfessionEntry pe = existingProfessions.get(i);
            String shownName = (isPendingDelete || isDirtySelected)
                    ? pe.displayName()
                    : pe.color().replace("&", "§") + pe.displayName();
            String label = prefix + style + shownName + " §8(" + id + ")" + suffix;

            Button main = Button.builder(Component.literal(label), btn -> loadProfEntry(idx))
                    .size(rw - 18, ROW_H - 2).build(SkinnedButton.row(i == profSelectedIndex));
            Button side = isPendingDelete
                    ? Button.builder(Component.literal("§c!"),
                                    btn -> {
                                        PacketDistributor.sendToServer(new DeleteProfessionPacket(id));
                                        existingProfessions.remove(idx);
                                        profSelectedIndex = -1;
                                        profPendingDelete = null;
                                        resetProfForm();
                                    })
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "rpessentials.gui.profession_editor.confirm_delete_tooltip")))
                            .build(SkinnedButton::new)
                    : Button.builder(Component.literal("§7x"), btn -> { profPendingDelete = id; rebuild(); })
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "rpessentials.gui.profession_editor.delete_tooltip")))
                            .build(SkinnedButton::new);
            rows.add(new ButtonRowList.Row().add(main, 0).add(side, rw - 16));
        }

        if (profStateIsNew) {
            String shown = profStateId.isEmpty() ? "?" : profStateId;
            profInCreationButton = Button.builder(
                            Component.literal("§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"),
                            btn -> {})
                    .size(rw - 18, ROW_H - 2).build(SkinnedButton::new);
            rows.add(new ButtonRowList.Row().add(profInCreationButton, 0));
        } else {
            profInCreationButton = null;
        }
        profList.setRows(rows);
        addRenderableWidget(profList);

        addRenderableWidget(Button.builder(
                        Component.literal(tabColorCode() + I18n.get("rpessentials.gui.profession_editor.btn_new")),
                        btn -> resetProfForm())
                .pos(MARGIN, this.height - 35).size(LIST_W, 20).build(SkinnedButton::new));
    }

    // =========================================================================
    // ONGLET ROLES
    // =========================================================================
    /** Positions of the role form, shared by init() and render(). */
    private record RoleRows(int fieldW, int lpX, int lpY, int permY, int permCols, int permRows, int permW) {}

    private RoleRows roleRows(int formX, int formW) {
        int y = PANEL_TOP + 14;
        boolean side = formW >= 2 * 110 + UiLayout.GAP;
        int fieldW = side ? Math.min(160, (formW - UiLayout.GAP) / 2) : Math.min(formW, 160);
        int lpX = side ? formX + fieldW + UiLayout.GAP : formX;
        int lpY = side ? y : y + 38;
        int permY = lpY + 38 + 14; // room for the "Permissions" title
        // Permissions in as many columns as needed to stay above the footer buttons.
        int n = ALL_PERMISSIONS.length;
        int rowsFit = Math.max(1, (this.height - 35 - 4 - permY) / 18);
        int maxCols = Math.max(1, (formW + UiLayout.GAP) / (110 + UiLayout.GAP));
        int cols = Math.max(1, Math.min(maxCols, (n + rowsFit - 1) / rowsFit));
        int rows = (n + cols - 1) / cols;
        return new RoleRows(fieldW, lpX, lpY, permY, cols, rows, UiLayout.cellW(formW, cols, UiLayout.GAP));
    }

    private void initRoles() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        RoleRows r = roleRows(formX, formW);
        int y     = PANEL_TOP + 14;

        EditBox idBox = new SkinnedEditBox(this.font, formX, y + 16, r.fieldW(), 18,
                Component.translatable("rpessentials.gui.roles.id_label"));
        idBox.setHint(Component.translatable("rpessentials.gui.roles.id_hint"));
        idBox.setMaxLength(32);
        idBox.setValue(roleStateId);
        idBox.setEditable(roleStateIsNew);
        idBox.setResponder(val -> roleStateId = val);
        addRenderableWidget(idBox);

        EditBox lpBox = new SkinnedEditBox(this.font, r.lpX(), r.lpY() + 16, r.fieldW(), 18,
                Component.translatable("rpessentials.gui.roles.lp_label"));
        lpBox.setHint(Component.translatable("rpessentials.gui.roles.lp_hint"));
        lpBox.setMaxLength(64);
        lpBox.setValue(roleStateLp);
        lpBox.setEditable(canManageRoles());
        lpBox.setResponder(val -> { roleStateLp = val; roleDirty = true; });
        addRenderableWidget(lpBox);
        y = r.permY();

        for (int i = 0; i < ALL_PERMISSIONS.length; i++) {
            final String perm = ALL_PERMISSIONS[i];
            boolean checked   = roleStatePerms.contains(perm);
            boolean modified  = roleDirtyPerms.contains(perm);
            String base       = checked ? "§a[✔] " : "§c[X] ";
            String textColor  = modified ? "§e§o" : "§f";
            int col = i / r.permRows(), row = i % r.permRows();
            Button permBtn = Button.builder(Component.literal(base + textColor + permLabel(perm)), btn -> {
                if (roleStatePerms.contains(perm)) roleStatePerms.remove(perm);
                else roleStatePerms.add(perm);
                roleDirty = true;
                roleDirtyPerms.add(perm);
                rebuild();
            }).pos(UiLayout.cellX(formX, formW, r.permCols(), UiLayout.GAP, col), y + row * 18)
                    .size(r.permW(), 16).build(SkinnedButton::new);
            permBtn.active = canManageRoles();
            addRenderableWidget(permBtn);
        }

        int btnY = this.height - 35;
        Button saveRoleBtn = Button.builder(Component.translatable("rpessentials.gui.roles.btn_save"),
                        btn -> saveRole())
                .pos(formX, btnY).size(120, 20).build(SkinnedButton::new);
        saveRoleBtn.active = Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.hasPermissions(3);
        addRenderableWidget(saveRoleBtn);
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN * 2 - 55, btnY).size(55, 20).build(SkinnedButton::new));

        buildRoleList();
    }

    private void buildRoleList() {
        if (roleList == null) roleList = new ButtonRowList(Minecraft.getInstance(), ROW_H);
        roleList.layout(MARGIN - 2, PANEL_TOP + 14, LIST_W + 4, this.height - 39 - (PANEL_TOP + 14));
        int rw = roleList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();

        for (int i = 0; i < existingRoles.size(); i++) {
            final int idx = i;
            String id = existingRoles.get(i).id();
            boolean isPendingDelete = id.equals(rolePendingDelete);
            boolean isDirtySelected = i == roleSelectedIndex && roleDirty && !isPendingDelete;

            String prefix = i == roleSelectedIndex ? "§e> " : "  ";
            String style  = isPendingDelete ? "§c" : (isDirtySelected ? "§e§o" : "");
            String suffix = isDirtySelected ? " *" : "";
            String label = prefix + style + id + " §8(" + existingRoles.get(i).lpGroup() + ")" + suffix;

            Button main = Button.builder(Component.literal(label), btn -> loadRoleEntry(idx))
                    .size(rw - 18, ROW_H - 2).build(SkinnedButton.row(i == roleSelectedIndex));
            Button side = isPendingDelete
                    ? Button.builder(Component.literal("§c!"), btn -> {
                                PacketDistributor.sendToServer(new DeleteRolePacket(id));
                                existingRoles.remove(idx);
                                roleSelectedIndex = -1;
                                rolePendingDelete = null;
                                resetRoleForm();
                            })
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.roles.confirm_delete_tooltip")))
                            .build(SkinnedButton::new)
                    : Button.builder(Component.literal("§7x"), btn -> { rolePendingDelete = id; rebuild(); })
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.roles.delete_tooltip")))
                            .build(SkinnedButton::new);
            side.active = canManageRoles();
            rows.add(new ButtonRowList.Row().add(main, 0).add(side, rw - 16));
        }

        if (roleStateIsNew) {
            String shown = roleStateId.isEmpty() ? "?" : roleStateId;
            roleInCreationButton = Button.builder(
                            Component.literal("§e§o" + shown + " §7(" + I18n.get("rpessentials.gui.in_creation") + ")"),
                            btn -> {})
                    .size(rw - 18, ROW_H - 2).build(SkinnedButton::new);
            rows.add(new ButtonRowList.Row().add(roleInCreationButton, 0));
        } else {
            roleInCreationButton = null;
        }
        roleList.setRows(rows);
        addRenderableWidget(roleList);

        addRenderableWidget(Button.builder(Component.literal(tabColorCode() + I18n.get("rpessentials.gui.roles.btn_new")),
                        btn -> resetRoleForm())
                .pos(MARGIN, this.height - 35).size(LIST_W, 20).build(SkinnedButton::new));
    }

    private boolean canManageRoles() {
        return Minecraft.getInstance().player != null
                && net.rp.rpessentials.permission.RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
    }

    // =========================================================================
    // ONGLET RESTRICTIONS GLOBALES
    // =========================================================================
    private void initGlobalRestrictions() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        List<String> list = getGlobalActiveList();
        List<String> pool = globalActiveCategory == 4 ? null
                : (globalActiveCategory == 1 ? allBlockIds : allItemIds);
        String hint = I18n.get(globalActiveCategory == 4 ? "rpessentials.gui.profession_editor.hint_container" : "rpessentials.gui.profession_editor.hint_global_item");

        java.util.function.Function<String, Warning> warnings = globalWarnings(globalActiveCategory);
        // The list stops so that the Manage button (18 px) sits right above Save (y = height - 35).
        int listEnd = buildRestrictionList(formX, formW, PANEL_TOP + 18, this.height - 35 - UiLayout.GAP - 18 - 2, list,
                globalDirtyEntries, this::removeGlobalRestriction, warnings);
        if (globalActiveCategory == 4) {
            addRenderableWidget(Button.builder(
                            Component.literal(I18n.get("rpessentials.gui.restriction_list.manage", list.size())),
                            btn -> Minecraft.getInstance().setScreen(new ContainerListSubScreen(
                                    this, list, globalDirtyEntries,
                                    existingProfessions,
                                    this::onGlobalEntryRemoved, this::saveGlobalRestrictions)))
                    .pos(formX, listEnd).size(Math.min(formW - 4, 200), 18).build(SkinnedButton::new));
        } else {
            addManageButton(formX, formW, listEnd, list, globalDirtyEntries, pool, hint,
                    () -> {}, this::onGlobalEntryRemoved, this::saveGlobalRestrictions,
                    "rpessentials.gui.empty.global." + GLOBAL_CATEGORY_KEYS[globalActiveCategory], warnings);
        }

        int btnY = this.height - 35;
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.global.btn_save"),
                        btn -> saveGlobalRestrictions())
                .pos(formX, btnY).size(160, 20).build(SkinnedButton::new));
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.profession_editor.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN * 2 - 55, btnY).size(55, 20).build(SkinnedButton::new));

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
                    .pos(MARGIN, PANEL_TOP + 14 + i * ROW_H).size(LIST_W, ROW_H - 2).build(SkinnedButton.row(selected)));
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
    // AVERTISSEMENTS (profession <-> global)
    // =========================================================================
    /** A ⚠ next to a restriction entry: what is wrong, and what clicking it does. */
    record Warning(Component tooltip, Runnable fix) {}

    /** How many ids a tooltip lists before "and N more". */
    private static final int WARN_LIST_MAX = 8;

    /** ⚠ button: shows the tooltip, applies the fix on click, then refreshes the list. */
    static Button warningButton(Warning w, int height, Runnable refresh) {
        return Button.builder(Component.literal("§6⚠"), btn -> { w.fix().run(); refresh.run(); })
                .size(16, height)
                .tooltip(Tooltip.create(w.tooltip()))
                .build(SkinnedButton::new);
    }

    /**
     * Button showing an entry. For a {@code *} pattern or a {@code #tag}, clicking it lists everything it contains.
     */
    static Button valueButton(String color, String value, List<String> pool, Screen back, int width, int height) {
        // No pool = container entries (block;professions), never expanded.
        boolean pattern = pool != null && RestrictionCoverage.isPattern(value);
        Button.Builder b = Button.builder(Component.literal(color + value + (pattern ? " §8▸" : "")),
                btn -> {
                    if (pattern) Minecraft.getInstance().setScreen(new PatternContentsSubScreen(back, value, pool));
                })
                .size(width, height);
        if (pattern) b.tooltip(Tooltip.create(Component.translatable("rpessentials.gui.pattern_contents.open_tooltip",
                RestrictionCoverage.expand(value, pool).size())));
        return b.build(SkinnedButton::new);
    }

    private static List<String> poolFor(int category) {
        return category == 1 ? allBlockIds : allItemIds;
    }

    /** "title / explanation / ids (if a pattern) / click hint", one component with line breaks. */
    private static Component warningText(String titleKey, String bodyKey, String someKey, String fixKey,
                                         String entry, List<String> ids, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.get(titleKey)).append('\n');
        if (RestrictionCoverage.isPattern(entry)) {
            sb.append("§7").append(I18n.get(someKey, ids.size(), total));
            for (int i = 0; i < Math.min(ids.size(), WARN_LIST_MAX); i++) sb.append("\n§f- ").append(ids.get(i));
            if (ids.size() > WARN_LIST_MAX) {
                sb.append("\n§8").append(I18n.get("rpessentials.gui.coverage.more", ids.size() - WARN_LIST_MAX));
            }
        } else {
            sb.append("§7").append(I18n.get(bodyKey));
        }
        sb.append("\n").append(I18n.get(fixKey));
        return Component.literal(sb.toString());
    }

    /**
     * Profession lists: an allowed entry that is not blocked globally changes nothing, everyone can
     * already do it. Clicking adds the entry to the matching global list and saves it.
     */
    private java.util.function.Function<String, Warning> profWarnings(int category) {
        if (category < 0 || category > 3) return null;
        return entry -> {
            List<String> global = globalWorking().get(category);
            List<String> pool = poolFor(category);
            int total = RestrictionCoverage.expand(entry, pool).size();
            List<String> open = RestrictionCoverage.uncovered(entry, global, pool);
            if (open.isEmpty()) return null;
            Component text = warningText("rpessentials.gui.coverage.not_blocked.title",
                    "rpessentials.gui.coverage.not_blocked." + PROF_TAB_KEYS[category],
                    "rpessentials.gui.coverage.not_blocked.some." + PROF_TAB_KEYS[category],
                    "rpessentials.gui.coverage.not_blocked.fix", entry, open, total);
            return new Warning(text, () -> blockGlobally(category, RestrictionCoverage.base(entry)));
        };
    }

    /** Adds {@code entry} to a global list (saved state and screen state) and sends it to the server. */
    private void blockGlobally(int category, String entry) {
        List<String> working = globalWorking().get(category);
        if (!working.contains(entry)) working.add(entry);
        if (globalSaved.isEmpty()) snapshotGlobal();
        List<String> saved = globalSaved.get(category);
        if (!saved.contains(entry)) saved.add(entry);
        sendGlobal(globalSaved);
        feedback(I18n.get("rpessentials.gui.coverage.added_global", entry,
                I18n.get("rpessentials.gui.global." + GLOBAL_CATEGORY_KEYS[category])));
    }

    /**
     * Global lists: a blocked entry that no profession allows is blocked for everyone (except exempted
     * staff). Clicking opens a list of professions to allow it for one of them.
     */
    private java.util.function.Function<String, Warning> globalWarnings(int category) {
        if (category < 0 || category > 3) return null;
        return entry -> {
            List<String> allowedAnywhere = new ArrayList<>();
            for (OpenProfessionGuiPacket.ProfessionEntry pe : existingProfessions) {
                allowedAnywhere.addAll(profList(pe, category));
            }
            List<String> pool = poolFor(category);
            int total = RestrictionCoverage.expand(entry, pool).size();
            List<String> closed = RestrictionCoverage.uncovered(entry, allowedAnywhere, pool);
            if (closed.isEmpty()) return null;
            Component text = warningText("rpessentials.gui.coverage.no_profession.title",
                    "rpessentials.gui.coverage.no_profession." + PROF_TAB_KEYS[category],
                    "rpessentials.gui.coverage.no_profession.some." + PROF_TAB_KEYS[category],
                    existingProfessions.isEmpty() ? "rpessentials.gui.coverage.no_profession.fix_none"
                            : "rpessentials.gui.coverage.no_profession.fix", entry, closed, total);
            Runnable fix = existingProfessions.isEmpty() ? () -> {}
                    : () -> Minecraft.getInstance().setScreen(new ProfessionPickerSubScreen(
                            Minecraft.getInstance().screen, this, category, RestrictionCoverage.base(entry)));
            return new Warning(text, fix);
        };
    }

    private static List<String> profList(OpenProfessionGuiPacket.ProfessionEntry pe, int category) {
        return switch (category) {
            case 1 -> pe.allowedBlocks();
            case 2 -> pe.allowedItems();
            case 3 -> pe.allowedEquipment();
            default -> pe.allowedCrafts();
        };
    }

    /** Allows {@code entry} for the profession at {@code index} and saves that profession. */
    void allowForProfession(int index, int category, String entry) {
        OpenProfessionGuiPacket.ProfessionEntry pe = existingProfessions.get(index);
        List<List<String>> lists = new ArrayList<>(List.of(
                new ArrayList<>(pe.allowedCrafts()), new ArrayList<>(pe.allowedBlocks()),
                new ArrayList<>(pe.allowedItems()), new ArrayList<>(pe.allowedEquipment())));
        if (!lists.get(category).contains(entry)) lists.get(category).add(entry);
        PacketDistributor.sendToServer(new SaveProfessionPacket(pe.id(), pe.displayName(), pe.color(),
                lists.get(0), lists.get(1), lists.get(2), lists.get(3), false));
        existingProfessions.set(index, new OpenProfessionGuiPacket.ProfessionEntry(pe.id(), pe.displayName(), pe.color(),
                lists.get(0), lists.get(1), lists.get(2), lists.get(3)));
        // Keep the form in sync when this profession is the one being edited.
        if (index == profSelectedIndex && !profStateIsNew) {
            List<String> working = switch (category) {
                case 1 -> allowedBlocks;
                case 2 -> allowedItems;
                case 3 -> allowedEquipment;
                default -> allowedCrafts;
            };
            if (!working.contains(entry)) working.add(entry);
        }
        feedback(I18n.get("rpessentials.gui.coverage.added_profession", entry, pe.displayName(),
                I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[category])));
    }

    private static void feedback(String text) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text), false);
        }
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
        GuiSkin.background(g, this.width, this.height, 0x99000000);

        GuiSkin.panel(g, MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

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
        GuiSkin.panel(g, LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, tabLineColor());

        
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

        int formW = this.width - formX - MARGIN * 2;
        ProfRows r = profRows(formX, formW);
        int y = PANEL_TOP + 14;
        int idLabelW = r.nameX() > formX ? r.nameX() - formX - UiLayout.GAP : formW;
        g.drawString(this.font, UiLayout.fit(this.font, I18n.get("rpessentials.gui.profession_editor.id_label_draw"), idLabelW),
                formX, y + 6, 0x888888, false);
        String nameLabel = I18n.get("rpessentials.gui.profession_editor.name_label_draw");
        g.drawString(this.font, nameLabel, r.nameX(), r.nameY() + 6, 0x888888, false);
        // Same preview as the nickname of the Player Profile: real colors, hex and gradients included.
        ColorPalette.drawPreview(g, this.font, nameLabel, profStateName, profColorIndex, r.nameX(), r.nameY() + 6);
        y = r.colorY();

        g.drawString(this.font, I18n.get("rpessentials.gui.profession_editor.color_label"),
                formX, y + 4, 0x888888, false);

        ColorPalette.drawSelection(g, ColorPalette.dims(formW, this.height), formX, y + 14, profColorIndex);
    }

    private void renderRolesOverlay(GuiGraphics g, int formX) {
        String modeLabel = roleStateIsNew
                ? tabColorCode() + I18n.get("rpessentials.gui.roles.mode_new")
                : tabColorCode() + I18n.get("rpessentials.gui.roles.mode_edit", roleStateId);
        g.drawString(this.font, modeLabel, formX, PANEL_TOP + 5, 0xFFFFFF, false);

        int formW = this.width - formX - MARGIN * 2;
        RoleRows r = roleRows(formX, formW);
        int y = PANEL_TOP + 14;
        int idLabelW = r.lpX() > formX ? r.lpX() - formX - UiLayout.GAP : formW;
        g.drawString(this.font, UiLayout.fit(this.font, I18n.get("rpessentials.gui.roles.id_label"), idLabelW),
                formX, y + 6, 0x888888, false);
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.lp_label"), r.lpX(), r.lpY() + 6, 0x888888, false);
        y = r.permY();

        GuiSkin.separator(g, formX, y - 3, formX + formW, y - 2, 0xFF333333);
        g.drawString(this.font, I18n.get("rpessentials.gui.roles.permissions_header"), formX, y - 13, 0xFFD700, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < LIST_W + MARGIN * 2) return super.mouseScrolled(mx, my, sx, sy); // list widgets
        
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
                                Component.translatable("rpessentials.gui.profession_editor.id_used_role"), false);
                    }
                    return;
                }
            }
        }

        String colorCode = ColorPalette.code(profColorIndex);
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
        profColorIndex = ColorPalette.ensure(ColorPalette.leadingCode(e.color()));
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
        if (profList != null) profList.scrollToEndOnce();
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
                                Component.translatable("rpessentials.gui.profession_editor.id_used_profession"), false);
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
        if (roleList != null) roleList.scrollToEndOnce();
        rebuild();
    }

    private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }

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
        private final String emptyKey;
        private final Runnable onChange;
        private final Consumer<String> onRemove;
        private final Runnable onSave;
        private final java.util.function.Function<String, Warning> warnings;
        private final List<String> suggestions = new ArrayList<>();

        private ButtonRowList list;
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
                                 Runnable onSave, String emptyKey, java.util.function.Function<String, Warning> warnings) {
            super(Component.literal(""));
            this.parent       = parent;
            this.items        = items;
            this.dirtyEntries = dirtyEntries;
            this.pool         = pool;
            this.hint         = hint;
            this.onChange     = onChange;
            this.onRemove     = onRemove;
            this.onSave       = onSave;
            this.emptyKey = emptyKey;
            this.warnings = warnings;
        }

        @Override
        protected void init() {
            int midX   = this.width / 2;
            int startY = 46;
            // Minecraft list widget: delete button, item icon, value.
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), 20);
            list.layout(midX - 176, startY - 4, 352, this.height - 80 - (startY - 4));
            int rw = list.rowWidth();
            List<ButtonRowList.Row> listRows = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                final int idx = i;
                final String value = items.get(i);
                String color = dirtyEntries.contains(value) ? "§e§o" : "§7";
                boolean pendingRemove = value.equals(pendingDelete);
                Button del = Button.builder(Component.literal(pendingRemove ? "§c!" : "§c×"),
                                btn -> removeAt(idx, value))
                        .size(16, 18)
                        .tooltip(Tooltip.create(Component.translatable(pendingRemove
                                ? "rpessentials.gui.restriction_list.confirm_delete_tooltip"
                                : "rpessentials.gui.restriction_list.delete_tooltip")))
                        .build(SkinnedButton::new);
                Warning warn = warnings != null ? warnings.apply(value) : null;
                Button val = valueButton(color, value, pool, this, rw - 38 - (warn != null ? 18 : 0), 18);
                ButtonRowList.Row row = new ButtonRowList.Row().add(del, 0).item(() -> iconFor(value), 19, 1).add(val, 38);
                if (warn != null) row.add(warningButton(warn, 18, this::rebuild), rw - 16);
                listRows.add(row);
            }
            list.setRows(listRows);
            addRenderableWidget(list);

            boxX = midX - 170;
            boxY = this.height - 60;
            boxW = 300;
            inputBox = new SkinnedEditBox(this.font, boxX, boxY, boxW, 18, Component.translatable("rpessentials.gui.field.entry"));
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
                    .pos(boxX + boxW + 2, boxY).size(16, 18).build(SkinnedButton::new));

            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.restriction_list.save"),
                            btn -> { onSave.run(); rebuild(); })
                    .pos(midX - 130, this.height - 30).size(120, 20).build(SkinnedButton::new));
            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(parent))
                    .pos(midX + 4, this.height - 30).size(80, 20).build(SkinnedButton::new));
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
                if (list != null) list.scrollToEndOnce();
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

            if (lower.startsWith("#")) {
                (pool == allBlockIds ? BuiltInRegistries.BLOCK.getTagNames() : BuiltInRegistries.ITEM.getTagNames())
                        .map(k -> "#" + k.location())
                        .filter(s -> s.contains(lower))
                        .sorted()
                        .limit(24)
                        .forEach(suggestions::add);
                return;
            }
            if (lower.contains("*")) {
                for (String id : pool) {
                    if (RpEssentialsPatternUtils.matchesPattern(id, lower)) suggestions.add(id);
                    if (suggestions.size() >= 24) break;
                }
                return;
            }

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
            suggestions.add(0, lower + "*");
        }

        private void closeSuggestions() { suggestions.clear(); suggestionIndex = -1; sugScroll = 0; }
        private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }

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
            
            return super.mouseScrolled(mx, my, sx, sy);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            GuiSkin.background(g, this.width, this.height, 0xAA000000);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font,
                    I18n.get("rpessentials.gui.restriction_list.count", items.size()),
                    this.width / 2, 20, 0xFFD700);
            if (items.isEmpty()) {
                var lines = this.font.split(Component.translatable(emptyKey), 280);
                int y0 = this.height / 2 - lines.size() * 5 - 20;
                for (int i = 0; i < lines.size(); i++) {
                    g.drawCenteredString(this.font, lines.get(i), this.width / 2, y0 + i * 10, 0x888888);
                }
            }
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
            if (!GuiSkin.draw(g, GuiSkin.PANEL, boxX - 1, y - 1, boxW + 2, h + 2)) {
                g.fill(boxX - 1, y - 1, boxX + boxW + 1, y + h + 1, 0xFF555555);
                g.fill(boxX, y, boxX + boxW, y + h, 0xF01A1A1A);
            }

            for (int i = 0; i < count; i++) {
                int real = start + i;
                int ly = y + 2 + i * SUG_H;
                String id = suggestions.get(real);
                boolean sel = real == suggestionIndex;
                boolean hov = real == hovered;
                if (sel || hov) GuiSkin.row(g, boxX + 1, ly, boxX + boxW - 1, ly + SUG_H, sel, hov, hov ? 0xFF3B6FB5 : 0xFF2A4A7F);

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

    /** Read-only list of the items or blocks matched by a {@code *} pattern or a {@code #tag}. */
    @OnlyIn(Dist.CLIENT)
    private static class PatternContentsSubScreen extends Screen {

        private final Screen back;
        private final String entry;
        private final List<String> ids;
        private ButtonRowList list;

        PatternContentsSubScreen(Screen back, String entry, List<String> pool) {
            super(Component.literal(""));
            this.back = back;
            this.entry = entry;
            this.ids = RestrictionCoverage.expand(entry, pool);
        }

        @Override
        protected void init() {
            int midX = this.width / 2;
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), 18);
            list.layout(midX - 160, 34, 320, this.height - 34 - 40);
            List<ButtonRowList.Row> rows = new ArrayList<>();
            for (String id : ids) {
                rows.add(new ButtonRowList.Row().item(() -> iconFor(id), 2, 1).text("§f" + id, 22, 5));
            }
            if (ids.isEmpty()) {
                rows.add(new ButtonRowList.Row().text("§8" + I18n.get("rpessentials.gui.pattern_contents.empty"), 4, 5));
            }
            list.setRows(rows);
            addRenderableWidget(list);
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(back))
                    .pos(midX - 40, this.height - 30).size(80, 20).build(SkinnedButton::new));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            GuiSkin.background(g, this.width, this.height, 0xAA000000);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.pattern_contents.title", entry, ids.size()),
                    this.width / 2, 16, 0xFFD700);
        }

        @Override
        public void onClose() { Minecraft.getInstance().setScreen(back); }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

        @Override
        public boolean isPauseScreen() { return false; }
    }

    /** Check list of the professions that will be allowed to use a globally blocked entry. */
    @OnlyIn(Dist.CLIENT)
    private static class ProfessionPickerSubScreen extends Screen {

        private final Screen back;
        private final ProfessionEditorScreen editor;
        private final int category;
        private final String entry;
        /** Indexes of the professions ticked by the user (not the ones that already allow it). */
        private final Set<Integer> checked = new HashSet<>();
        private ButtonRowList list;

        ProfessionPickerSubScreen(Screen back, ProfessionEditorScreen editor, int category, String entry) {
            super(Component.literal(""));
            this.back = back;
            this.editor = editor;
            this.category = category;
            this.entry = entry;
        }

        @Override
        protected void init() {
            int midX = this.width / 2;
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), 20);
            list.layout(midX - 120, 44, 240, this.height - 44 - 40);
            int rw = list.rowWidth();
            List<ButtonRowList.Row> rows = new ArrayList<>();
            for (int i = 0; i < editor.existingProfessions.size(); i++) {
                final int idx = i;
                var pe = editor.existingProfessions.get(i);
                String color = pe.color().replace("&", "§");
                // A profession that already allows the whole entry is shown ticked and cannot be changed here.
                boolean already = RestrictionCoverage.uncovered(entry, profList(pe, category), poolFor(category)).isEmpty();
                boolean on = already || checked.contains(idx);
                String box = already ? "§8☑ " : on ? "§a☑ " : "§7☐ ";
                String suffix = already ? " §8" + I18n.get("rpessentials.gui.coverage.picker_already") : "";
                Button b = Button.builder(Component.literal(box + color + pe.displayName() + " §8(" + pe.id() + ")" + suffix),
                                btn -> {
                                    if (!checked.remove(idx)) checked.add(idx);
                                    rebuildWidgets();
                                })
                        .size(rw, 18).build(SkinnedButton::new);
                b.active = !already;
                rows.add(new ButtonRowList.Row().add(b, 0));
            }
            list.setRows(rows);
            addRenderableWidget(list);

            Button confirm = Button.builder(Component.literal(
                                    I18n.get("rpessentials.gui.coverage.picker_confirm", checked.size())),
                            btn -> {
                                for (int idx : new TreeSet<>(checked)) editor.allowForProfession(idx, category, entry);
                                Minecraft.getInstance().setScreen(back);
                            })
                    .pos(midX - 124, this.height - 30).size(120, 20).build(SkinnedButton::new);
            confirm.active = !checked.isEmpty();
            addRenderableWidget(confirm);
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(back))
                    .pos(midX + 4, this.height - 30).size(80, 20).build(SkinnedButton::new));
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            GuiSkin.background(g, this.width, this.height, 0xAA000000);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.coverage.picker_title", entry),
                    this.width / 2, 18, 0xFFD700);
            g.drawCenteredString(this.font, "§7" + I18n.get("rpessentials.gui.coverage.picker_hint",
                            I18n.get("rpessentials.gui.profession_editor.tab." + PROF_TAB_KEYS[category])),
                    this.width / 2, 30, 0xAAAAAA);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

        @Override
        public boolean isPauseScreen() { return false; }
    }

    @OnlyIn(Dist.CLIENT)
    private static class ContainerListSubScreen extends Screen {

        private static final int PER_PAGE = 8;
        private static final int SUG_H = 14;
        private static final int SUG_MAX = 6;

        private final ProfessionEditorScreen parent;
        private final List<String> items;
        private final Set<String> dirty;
        private final List<OpenProfessionGuiPacket.ProfessionEntry> professions;
        private final Consumer<String> onRemove;
        private final Runnable onSave;
        private final Set<String> selected = new LinkedHashSet<>();
        private final List<String> suggestions = new ArrayList<>();

        private ButtonRowList list;
        private int profPage = 0;
        private int sugIndex = -1;
        private int sugScroll = 0;
        private int boxX, boxY, boxW;
        private String block = "";
        private String editing = null;
        private String pendingDelete = null;
        private boolean applying = false;
        private EditBox blockBox;

        ContainerListSubScreen(ProfessionEditorScreen parent, List<String> items, Set<String> dirty,
                               List<OpenProfessionGuiPacket.ProfessionEntry> professions,
                               Consumer<String> onRemove, Runnable onSave) {
            super(Component.literal(""));
            this.parent = parent;
            this.items = items;
            this.dirty = dirty;
            this.professions = professions;
            this.onRemove = onRemove;
            this.onSave = onSave;
        }

        @Override
        protected void init() {
            int midX = this.width / 2;
            int startY = 32;
            int rowH = 20;
            boxX = midX - 170;
            boxY = this.height - 128;
            boxW = 300;
            // Minecraft list widget: delete button, item icon, "block > professions" (click to edit).
            if (list == null) list = new ButtonRowList(Minecraft.getInstance(), rowH);
            list.layout(midX - 176, startY - 4, 352, boxY - 6 - (startY - 4));
            int rw = list.rowWidth();
            List<ButtonRowList.Row> listRows = new ArrayList<>();
            for (String value : items) {
                boolean pending = value.equals(pendingDelete);
                Button del = Button.builder(Component.literal(pending ? "§c!" : "§cx"), btn -> {
                            if (!value.equals(pendingDelete)) {
                                pendingDelete = value;
                            } else {
                                pendingDelete = null;
                                items.remove(value);
                                dirty.remove(value);
                                onRemove.accept(value);
                            }
                            rebuild();
                        })
                        .size(16, 18).build(SkinnedButton::new);
                int sep = value.indexOf(';');
                final String name = sep < 0 ? value : value.substring(0, sep);
                final String profs = sep < 0 ? "" : value.substring(sep + 1);
                String label = (dirty.contains(value) ? "§e§o" : "§7") + name + " §8> §f"
                        + profs.replace(",", "§7, §f");
                Button val = Button.builder(Component.literal(label), btn -> {
                            editing = value;
                            block = name;
                            selected.clear();
                            for (String p : profs.split(",")) if (!p.isBlank()) selected.add(p.trim());
                            closeSuggestions();
                            rebuild();
                        })
                        .size(rw - 38, 18).build(SkinnedButton::new);
                listRows.add(new ButtonRowList.Row().add(del, 0).item(() -> iconFor(name), 19, 1).add(val, 38));
            }
            list.setRows(listRows);
            addRenderableWidget(list);

            blockBox = new SkinnedEditBox(this.font, boxX, boxY, boxW, 18, Component.translatable("rpessentials.gui.field.block"));
            blockBox.setHint(Component.translatable("rpessentials.gui.profession_editor.hint_block_list"));
            blockBox.setMaxLength(128);
            blockBox.setValue(block);
            blockBox.setResponder(val -> {
                if (applying) return;
                block = val;
                sugIndex = -1;
                updateSuggestions(val);
            });
            addRenderableWidget(blockBox);

            int pages = Math.max(1, (professions.size() + PER_PAGE - 1) / PER_PAGE);
            profPage = Math.min(profPage, pages - 1);
            int from = profPage * PER_PAGE;
            int to = Math.min(professions.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                final OpenProfessionGuiPacket.ProfessionEntry e = professions.get(i);
                int col = (i - from) % 4;
                int row = (i - from) / 4;
                boolean on = selected.contains(e.id());
                String label = (on ? "§a✔ " : "") + e.color().replace("&", "§") + e.displayName();
                addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                            if (!selected.remove(e.id())) selected.add(e.id());
                            rebuild();
                        })
                        .pos(boxX + col * 76, boxY + 34 + row * 16).size(74, 14)
                        .tooltip(Tooltip.create(Component.literal(e.id()))).build(SkinnedButton::new));
            }
            if (pages > 1) {
                addRenderableWidget(Button.builder(Component.literal("<"),
                                btn -> { profPage = Math.max(0, profPage - 1); rebuild(); })
                        .pos(boxX + 308, boxY + 34).size(14, 14).build(SkinnedButton::new));
                addRenderableWidget(Button.builder(Component.literal(">"),
                                btn -> { profPage = Math.min(pages - 1, profPage + 1); rebuild(); })
                        .pos(boxX + 308, boxY + 50).size(14, 14).build(SkinnedButton::new));
            }

            addRenderableWidget(Button.builder(Component.translatable(editing != null
                                    ? "rpessentials.gui.container_list.update"
                                    : "rpessentials.gui.container_list.add"),
                            btn -> commit())
                    .pos(boxX, this.height - 60).size(130, 18).build(SkinnedButton::new));
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.container_list.clear"),
                            btn -> { clearForm(); rebuild(); })
                    .pos(boxX + 134, this.height - 60).size(100, 18).build(SkinnedButton::new));

            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.container_list.save_all"),
                            btn -> { onSave.run(); rebuild(); })
                    .pos(midX - 130, this.height - 30).size(120, 20).build(SkinnedButton::new));
            addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.restriction_list.close"),
                            btn -> Minecraft.getInstance().setScreen(parent))
                    .pos(midX + 4, this.height - 30).size(80, 20).build(SkinnedButton::new));
        }

        private void commit() {
            String b = block.trim().toLowerCase();
            if (b.isEmpty() || selected.isEmpty()) return;
            String entry = b + ";" + String.join(",", selected);
            int idx = editing != null ? items.indexOf(editing) : -1;
            if (idx >= 0) {
                dirty.remove(editing);
                items.set(idx, entry);
                dirty.add(entry);
            } else if (!items.contains(entry)) {
                items.add(entry);
                dirty.add(entry);
            }
            clearForm();
            rebuild();
        }

        private void clearForm() {
            editing = null;
            block = "";
            selected.clear();
            closeSuggestions();
        }

        private void updateSuggestions(String in) {
            sugScroll = 0;
            suggestions.clear();
            String lower = in.toLowerCase().trim();
            if (lower.isEmpty()) return;
            List<String> pool = containerPool();
            if (lower.startsWith("#")) {
                BuiltInRegistries.BLOCK.getTagNames()
                        .map(k -> "#" + k.location())
                        .filter(s -> s.contains(lower))
                        .sorted().limit(24).forEach(suggestions::add);
                return;
            }
            if (lower.contains("*")) {
                for (String id : pool) {
                    if (RpEssentialsPatternUtils.matchesPattern(id, lower)) suggestions.add(id);
                    if (suggestions.size() >= 24) break;
                }
                return;
            }
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
            suggestions.add(0, lower + "*");
        }

        private void closeSuggestions() { suggestions.clear(); sugIndex = -1; sugScroll = 0; }

        private void applySuggestion(String value) {
            block = value;
            applying = true;
            blockBox.setValue(value);
            applying = false;
        }

        private void cycle(int dir) {
            sugIndex = sugIndex < 0 && dir < 0 ? suggestions.size() - 1
                    : Math.floorMod(sugIndex + dir, suggestions.size());
            applySuggestion(suggestions.get(sugIndex));
            if (sugIndex < sugScroll) sugScroll = sugIndex;
            else if (sugIndex >= sugScroll + SUG_MAX) sugScroll = sugIndex - SUG_MAX + 1;
        }

        private int dropdownTop() {
            return boxY - (Math.min(suggestions.size(), SUG_MAX) * SUG_H + 4) - 2;
        }

        private int suggestionAt(double mx, double my) {
            int count = Math.min(suggestions.size(), SUG_MAX);
            int top = dropdownTop() + 2;
            if (mx < boxX || mx >= boxX + boxW || my < top || my >= top + count * SUG_H) return -1;
            return sugScroll + (int) ((my - top) / SUG_H);
        }

        private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!suggestions.isEmpty()) {
                if (keyCode == GLFW.GLFW_KEY_TAB) { cycle((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1); return true; }
                if (keyCode == GLFW.GLFW_KEY_DOWN) { cycle(1); return true; }
                if (keyCode == GLFW.GLFW_KEY_UP) { cycle(-1); return true; }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeSuggestions(); return true; }
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && blockBox != null && blockBox.isFocused()) {
                closeSuggestions();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            
            if (!suggestions.isEmpty()) {
                int idx = suggestionAt(mx, my);
                if (idx >= 0 && idx < suggestions.size()) {
                    applySuggestion(suggestions.get(idx));
                    closeSuggestions();
                    return true;
                }
                boolean onBox = mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + 18;
                if (!onBox) closeSuggestions();
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            if (!suggestions.isEmpty() && mx >= boxX && mx < boxX + boxW && my >= dropdownTop() && my < boxY + 18) {
                int max = Math.max(0, suggestions.size() - SUG_MAX);
                sugScroll = Math.max(0, Math.min(max, sugScroll - (int) Math.signum(sy)));
                return true;
            }
            
            return super.mouseScrolled(mx, my, sx, sy);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            GuiSkin.background(g, this.width, this.height, 0xAA000000);
            super.render(g, mx, my, delta);
            g.drawCenteredString(this.font, I18n.get("rpessentials.gui.restriction_list.count", items.size()),
                    this.width / 2, 14, 0xFFD700);
            if (items.isEmpty()) {
                var lines = this.font.split(Component.translatable("rpessentials.gui.empty.global.containers"), 280);
                int y0 = (32 + boxY) / 2 - lines.size() * 5;
                for (int i = 0; i < lines.size(); i++) {
                    g.drawCenteredString(this.font, lines.get(i), this.width / 2, y0 + i * 10, 0x888888);
                }
            }
            g.drawString(this.font, I18n.get("rpessentials.gui.container_list.professions"),
                    boxX, boxY + 22, 0xAAAAAA, false);
            renderDropdown(g, mx, my);
        }

        private void renderDropdown(GuiGraphics g, int mx, int my) {
            if (suggestions.isEmpty()) return;
            int count = Math.min(suggestions.size(), SUG_MAX);
            int h = count * SUG_H + 4;
            int y = dropdownTop();
            int hovered = suggestionAt(mx, my);

            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            if (!GuiSkin.draw(g, GuiSkin.PANEL, boxX - 1, y - 1, boxW + 2, h + 2)) {
                g.fill(boxX - 1, y - 1, boxX + boxW + 1, y + h + 1, 0xFF555555);
                g.fill(boxX, y, boxX + boxW, y + h, 0xF01A1A1A);
            }
            for (int i = 0; i < count; i++) {
                int real = sugScroll + i;
                int ly = y + 2 + i * SUG_H;
                String id = suggestions.get(real);
                boolean sel = real == sugIndex;
                boolean hov = real == hovered;
                if (sel || hov) GuiSkin.row(g, boxX + 1, ly, boxX + boxW - 1, ly + SUG_H, sel, hov, hov ? 0xFF3B6FB5 : 0xFF2A4A7F);
                ItemStack st = iconFor(id);
                if (!st.isEmpty()) {
                    g.pose().pushPose();
                    g.pose().translate(boxX + 3, ly + 1, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.renderItem(st, 0, 0);
                    g.pose().popPose();
                }
                g.drawString(this.font, id, boxX + 18, ly + 3, (sel || hov) ? 0xFFFFFF : 0xAAAAAA, false);
            }
            g.pose().popPose();
        }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

        @Override
        public boolean isPauseScreen() { return false; }
    }
}