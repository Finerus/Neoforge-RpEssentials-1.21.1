package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.util.ColorHelper;
import net.rp.rpessentials.permission.RpEssentialsPermissions;
import net.rp.rpessentials.moderation.ManageService;
import net.rp.rpessentials.network.ManagePlayerPacket;
import net.rp.rpessentials.network.OpenPlayerProfileGuiPacket;
import net.rp.rpessentials.network.PlayerReportsPacket;
import net.rp.rpessentials.network.ReportActionPacket;
import net.rp.rpessentials.network.RequestPlayerReportsPacket;
import net.rp.rpessentials.network.PlayerNoteActionPacket;
import net.rp.rpessentials.network.SetPlayerProfilePacket;
import net.minecraft.client.Minecraft;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;

@OnlyIn(Dist.CLIENT)
public class PlayerProfileScreen extends Screen {

    private final List<OpenPlayerProfileGuiPacket.PlayerData> players;
    private final List<String> availableProfessionIds;
    private final List<String> availableRoles;
    private final List<String> availablePlatforms;

    private int stateSelectedPlayer = 0;
    private int stateSelectedProf   = 0;
    private int stateNickColorIndex = 0;
    /** Player list: Minecraft list widget, kept between rebuilds (scroll position). */
    private ButtonRowList playerList;
    /** Stats tab: Minecraft list widget. */
    private TextLinesList statsList;
    private int activeTab           = 0; // 0=Profil, 1=Stats, 2=Notes
    /** Notes and Manage tabs: Minecraft list widgets, kept between rebuilds (scroll position). */
    private ButtonRowList notesList, manageList;
    
    
    /** History tab: Minecraft list widget, kept between rebuilds so the scroll position stays. */
    private TextLinesList historyList;

    /** Reports tab: list widget, reports received from the server per player, open count per player. */
    private ButtonRowList reportsList;
    private final java.util.Map<java.util.UUID, List<PlayerReportsPacket.View>> reportsCache = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> reportsRequested = new java.util.HashSet<>();
    private final java.util.Map<java.util.UUID, Integer> reportCounts = new java.util.HashMap<>();
    private boolean reportsEnabled = true;
    private String stateReportNote = "";
    private int pendingDeleteReportId = -1;
    
    /** Extra stats received from the server, per player. */
    private final java.util.Map<java.util.UUID, List<String>> statsCache = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> statsRequested = new java.util.HashSet<>();

    // History tab: lines received from the server, per player.
    private final java.util.Map<java.util.UUID, List<String>> historyCache = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> historyRequested = new java.util.HashSet<>();
    private boolean historyEnabled = true;
    
    private int stateTempDays = 7;

    private String stateNick = "";
    private String stateRole = "";
    private String stateNoteInput = "";
    private String stateAddPlayerName = "";
    private int stateEditingNoteId = -1;
    private int tempNoteIdCounter  = -1;
    private static int filterMode = 0;
    private int sortButtonX, sortButtonY;
    private static final String[] FILTER_KEYS = { "online", "offline", "prepared", "all", "reported" };
    private String pendingDeletePlayerUuid = null;
    private boolean pendingNickReset = false;
    private int pendingDeleteNoteId = -1;

    private static final char[]   COLOR_CHARS = ColorPalette.CHARS;
    private static final String[] COLOR_KEYS  = ColorPalette.KEYS;

    private static final int LIST_W       = 130;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;
    private static final int MANAGE_ROW_H = 34;
    

    private static final String[] TAB_KEYS = { "profile", "stats", "notes", "manage", "history", "reports" };
    private static final String[] TAB_COLORS = { "§e", "§a", "§b", "§c", "§d", "§6" };

    private static final String[] MANAGE_SECTION_KEYS = {
            "access", "discipline", "teleport", "send", "permissions", "vanish",
            "inventory", "identity", "absence", "reset" };
    private static final ManageService.Action[][] MANAGE_ACTIONS = {
            { ManageService.Action.WHITELIST_ADD, ManageService.Action.WHITELIST_REMOVE },
            { ManageService.Action.KICK, ManageService.Action.BAN, ManageService.Action.TEMPBAN, ManageService.Action.PARDON },
            { ManageService.Action.TP_TO_PLAYER, ManageService.Action.TP_PLAYER_HERE },
            { ManageService.Action.TP_PLAYER_PLATFORM, ManageService.Action.TP_PLAYER_AFK },
            { ManageService.Action.OP, ManageService.Action.DEOP },
            { ManageService.Action.VANISH, ManageService.Action.UNVANISH },
            { ManageService.Action.CLEAR_INVENTORY, ManageService.Action.CLEAR_ENDER },
            { ManageService.Action.CLEAR_NICKNAME, ManageService.Action.CLEAR_ROLE, ManageService.Action.CLEAR_PROFESSION },
            { ManageService.Action.ABSENCE_ADD, ManageService.Action.ABSENCE_REMOVE },
            { ManageService.Action.RESET_ALL }
    };

    private static int ownLevel() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        for (int l = 4; l > 0; l--) if (p.hasPermissions(l)) return l;
        return 0;
    }

    private boolean selectedIsSelf() {
        var p = Minecraft.getInstance().player;
        return p != null && !players.isEmpty() && p.getUUID().equals(selectedData().uuid());
    }

    /** Rows of the player list that fit between the top arrow and the "add player" field. */
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

    private int defaultManageNumber(ManageService.Action a) {
        int own = ownLevel();
        if (a == ManageService.Action.OP) return selectedIsSelf() ? own : Math.max(1, Math.min(2, own));
        if (a == ManageService.Action.VANISH) return own;
        return 7;
    }

    private ManageService.Action manageAction = null;
    private String manageText = "";
    private int manageNumber = 2;

    public PlayerProfileScreen(List<OpenPlayerProfileGuiPacket.PlayerData> players,
                               List<String> availableProfessionIds,
                               List<String> availableRoles,
                               List<String> availablePlatforms,
                               java.util.Map<java.util.UUID, Integer> openReports) {
        super(Component.translatable("rpessentials.gui.player_profile.title"));
        this.reportCounts.putAll(openReports);
        this.players                = players;
        this.availableProfessionIds = availableProfessionIds;
        this.availableRoles         = availableRoles;
        this.availablePlatforms     = availablePlatforms;
        if (!players.isEmpty()) loadPlayerState(0);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private List<String> selectedLicenses() {
        if (players.isEmpty()) return List.of();
        return players.get(stateSelectedPlayer).currentLicenses();
    }

    private List<Integer> getVisibleIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(i);
            boolean match = switch (filterMode) {
                case 1 -> !p.isOnline() && !p.isPrepared();
                case 2 -> p.isPrepared();
                case 3 -> true;
                case 4 -> reportCounts.getOrDefault(p.uuid(), 0) > 0;
                default -> p.isOnline();
            };
            if (match) result.add(i);
        }
        return result;
    }

    private boolean selectedPlayerOwns(String profId) {
        return selectedLicenses().contains(profId);
    }

    private String selectedProfId() {
        if (availableProfessionIds.isEmpty()) return "";
        return availableProfessionIds.get(stateSelectedProf);
    }

    private OpenPlayerProfileGuiPacket.PlayerData selectedData() {
        return players.get(stateSelectedPlayer);
    }

    // =========================================================================
    // INIT
    // =========================================================================
    @Override
    protected void init() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        

        // ── Bouton de tri : cycle En ligne -> Hors ligne -> Prévu -> Tous ───
        sortButtonX = MARGIN + LIST_W - 20;
        sortButtonY = PANEL_TOP + 2 ;
        addRenderableWidget(Button.builder(Component.literal("§f⇅"),
                        btn -> {
                            filterMode = (filterMode + 1) % FILTER_KEYS.length;
                            if (playerList != null) playerList.setScrollAmount(0);
                            rebuild();
                        })
                .pos(sortButtonX, sortButtonY).size(18, 12)
                .tooltip(Tooltip.create(Component.translatable(
                        "rpessentials.gui.player_profile.filter." + FILTER_KEYS[filterMode])))
                .build(SkinnedButton::new));

        addRenderableWidget(Button.builder(Component.literal("§7↻"),
                        btn -> PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestOpenGuiPacket(
                                net.rp.rpessentials.network.RequestOpenGuiPacket.GuiType.PLAYER_PROFILE)))
                .pos(sortButtonX - 20, sortButtonY).size(18, 12)
                .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")))
                .build(SkinnedButton::new));

        // ── Liste joueurs (gauche) : widget de liste Minecraft ─────────────
        if (playerList == null) playerList = new ButtonRowList(Minecraft.getInstance(), ROW_H);
        playerList.layout(MARGIN - 2, PANEL_TOP + 14, LIST_W + 4, this.height - 39 - (PANEL_TOP + 14));
        int rw = playerList.rowWidth();
        List<ButtonRowList.Row> playerRows = new ArrayList<>();
        for (int idx : getVisibleIndices()) {
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(idx);

            String statusSymbol = p.isPrepared() ? "§b# " : (p.isOnline() ? "§a● " : "§7○ ");
            String label = (idx == stateSelectedPlayer ? "§e> " : "  ") + statusSymbol + "§f" + p.mcName();
            if (!p.currentNick().isEmpty()) label += " §7(" + stripColor(p.currentNick()) + ")";
            if (p.activeWarnCount() > 0)    label += " §c[" + p.activeWarnCount() + "W]";
            if (p.isMuted())                label += " §6[M]";
            int openReports = reportCounts.getOrDefault(p.uuid(), 0);
            if (openReports > 0)            label += " §6[" + openReports + "R]";

            ButtonRowList.Row row = new ButtonRowList.Row();
            row.add(Button.builder(Component.literal(label), btn -> { loadPlayerState(idx); rebuild(); })
                    .size(p.isPrepared() ? rw - 18 : rw, ROW_H - 2)
                    .build(SkinnedButton.row(idx == stateSelectedPlayer)), 0);

            if (p.isPrepared()) {
                String uuidStr = p.uuid().toString();
                Button side = uuidStr.equals(pendingDeletePlayerUuid)
                        ? Button.builder(Component.literal("§c!"),
                                        btn -> PacketDistributor.sendToServer(
                                                new net.rp.rpessentials.network.DeletePendingProfilePacket(p.uuid())))
                                .size(16, ROW_H - 2)
                                .tooltip(Tooltip.create(Component.translatable(
                                        "rpessentials.gui.player_profile.confirm_delete_prepared")))
                                .build(SkinnedButton::new)
                        : Button.builder(Component.literal("§c✕"),
                                        btn -> { pendingDeletePlayerUuid = uuidStr; rebuild(); })
                                .size(16, ROW_H - 2)
                                .tooltip(Tooltip.create(Component.translatable(
                                        "rpessentials.gui.player_profile.delete_prepared_tooltip")))
                                .build(SkinnedButton::new);
                row.add(side, rw - 16);
            }
            playerRows.add(row);
        }
        playerList.setRows(playerRows);
        addRenderableWidget(playerList);

        // ── Ajout d'un joueur jamais connecté ───────────────────────────────
        EditBox addPlayerBox = new SkinnedEditBox(this.font, MARGIN, this.height - 35, LIST_W - 40, 20,
                Component.translatable("rpessentials.gui.player_profile.add_label"));
        addPlayerBox.setHint(Component.translatable("rpessentials.gui.player_profile.add_label")
                .copy().withStyle(ChatFormatting.GRAY));
        addPlayerBox.setMaxLength(32);
        addPlayerBox.setValue(stateAddPlayerName);
        addPlayerBox.setResponder(val -> stateAddPlayerName = val);
        addPlayerBox.setTooltip(Tooltip.create(
                Component.translatable("rpessentials.gui.player_profile.add_tooltip")));
        addRenderableWidget(addPlayerBox);

        addRenderableWidget(Button.builder(Component.literal("§a+"),
                        btn -> sendAddPlayerRequest())
                .pos(MARGIN + LIST_W - 38, this.height - 35).size(38, 20)
                .tooltip(Tooltip.create(
                        Component.translatable("rpessentials.gui.player_profile.add_tooltip")))
                .build(SkinnedButton::new));

        if (players.isEmpty()) return;

        // ── Onglets ────────────────────────────────────────────────────────
        int tabW = UiLayout.cellW(formW, TAB_KEYS.length, UiLayout.TAB_GAP);
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int ti = i;
            String label = i == activeTab
                    ? TAB_COLORS[ti] + "§l" + I18n.get("rpessentials.gui.player_profile.tab." + TAB_KEYS[i])
                    : "§7" + I18n.get("rpessentials.gui.player_profile.tab." + TAB_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { activeTab = ti; pendingDeleteNoteId = -1; pendingDeleteReportId = -1; rebuild(); })
                    .pos(UiLayout.cellX(formX, formW, TAB_KEYS.length, UiLayout.TAB_GAP, i), PANEL_TOP + 14)
                    .size(tabW, 16).build(SkinnedButton.tab(ti == activeTab)));
        }

        int contentY = PANEL_TOP + 36;

        switch (activeTab) {
            case 0 -> buildProfileTab(formX, formW, contentY);
            case 1 -> { requestStats(); buildStatsList(formX, formW, contentY); }
            case 2 -> buildNotesTab(formX, formW, contentY);
            case 3 -> buildManageTab(formX, formW, contentY);
            case 4 -> { requestHistory(); buildHistoryList(formX, formW, contentY); }
            case 5 -> { requestReports(); buildReportsTab(formX, formW, contentY); }
        }

        // ── Bouton Appliquer (onglet profil seulement) ─────────────────────
        if (activeTab == 0) {
            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_apply"),
                            btn -> applyProfile())
                    .pos(formX, this.height - 35).size(150, 20).build(SkinnedButton::new));
            // Character sheet (staff only, prepared profiles included): opens its own screen.
            int sheetX = formX + 150 + UiLayout.GAP;
            int sheetW = Math.min(130, closeButtonX() - UiLayout.GAP - sheetX);
            if (sheetW >= 40) {
                OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
                addRenderableWidget(Button.builder(
                                Component.literal(UiLayout.fit(this.font,
                                        I18n.get("rpessentials.gui.player_profile.btn_sheet"), sheetW - 8)),
                                btn -> Minecraft.getInstance().setScreen(
                                        new CharacterSheetScreen(this, sel.uuid(), sel.mcName())))
                        .pos(sheetX, this.height - 35).size(sheetW, 20)
                        .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.player_profile.btn_sheet_tooltip")))
                        .build(SkinnedButton::new));
            }
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.player_profile.btn_close"),
                        btn -> onClose())
                .pos(closeButtonX(), this.height - 35).size(60, 20).build(SkinnedButton::new));
    }

    /** Left edge of the Close button, the footer fields stop before it. */
    private int closeButtonX() {
        return this.width - MARGIN - 65;
    }

    /** Space kept under a tab list for the footer: separator, then the field / buttons line. */
    private static final int FOOTER_LIST_GAP = 44;

    /** Separator right above the footer line (Notes and Reports tabs). */
    private void renderFooterSeparator(GuiGraphics g, int formX, int formRight) {
        GuiSkin.separator(g, formX, this.height - 41, formRight - MARGIN, this.height - 40, 0xFF333333);
    }

    private void sendAddPlayerRequest() {
        String name = stateAddPlayerName.trim();
        if (name.isEmpty()) return;
        PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestAddPlayerPacket(name));
        stateAddPlayerName = "";
        rebuild();
    }

    // =========================================================================
    // ONGLET PROFIL
    // =========================================================================
    private void buildProfileTab(int formX, int formW, int y) {

        // --- Nickname ---
        int nickBoxY = y + 16;
        int nickBoxW = Math.min(formW - 4, 200);
        EditBox nickBox = new SkinnedEditBox(this.font, formX, nickBoxY, nickBoxW, 18,
                Component.translatable("rpessentials.gui.player_profile.nick_label"));
        nickBox.setHint(Component.translatable("rpessentials.gui.player_profile.nick_hint"));
        nickBox.setMaxLength(64);
        nickBox.setValue(stateNick);
        nickBox.setResponder(val -> stateNick = val);
        addRenderableWidget(nickBox);

        addRenderableWidget(Button.builder(
                        Component.literal(pendingNickReset ? "§c!" : "§c✕"),
                        btn -> {
                            if (!pendingNickReset) {
                                pendingNickReset = true;
                                rebuild();
                                return;
                            }
                            pendingNickReset = false;
                            stateNick = "";
                            stateNickColorIndex = 0;
                            OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
                            PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                                    target.uuid(), "", "", "", false, true, 0));
                            players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                                    target.uuid(), target.mcName(), "", target.currentRole(),
                                    target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                                    target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                                    target.noteCount(), target.isOnline(), target.notes(), target.isPrepared()));
                            rebuild();
                        })
                .pos(formX + nickBoxW + 4, nickBoxY)
                .size(18, 18)
                .tooltip(Tooltip.create(Component.translatable(pendingNickReset
                        ? "rpessentials.gui.player_profile.nick_reset_confirm"
                        : "rpessentials.gui.player_profile.nick_reset_tooltip")))
                .build(SkinnedButton::new));
        y += 44;

        // --- Palette de couleurs ---
        ColorPalette.Dims pal = ColorPalette.dims(formW, this.height);
        ColorPalette.addButtons(this::addRenderableWidget, pal, formX, y,
                ci -> { stateNickColorIndex = ci; rebuild(); }, this);
        y += pal.height() + 8;

        // --- Role ---
        y += 12;

        if (!availableRoles.isEmpty()) {
            int maxBtnW = 80;
            int minBtnW = 40;
            int gap     = UiLayout.GAP;
            int availW  = formW;
            int cols    = Math.max(1, availW / (minBtnW + gap));
            int btnW    = Math.min(maxBtnW, (availW - gap * (cols - 1)) / cols);
            int rows    = (int) Math.ceil((double) availableRoles.size() / cols);

            for (int i = 0; i < availableRoles.size(); i++) {
                final String role = availableRoles.get(i);
                int col = i % cols;
                int row = i / cols;
                boolean sel = role.equalsIgnoreCase(stateRole);
                Button roleBtn = Button.builder(
                                Component.literal(sel ? "§e§l" + role : "§7" + role),
                                btn -> { stateRole = role; rebuild(); })
                        .pos(formX + col * (btnW + gap), y + row * 16)
                        .size(btnW, 14).build(SkinnedButton::new);
                roleBtn.active = Minecraft.getInstance().player != null
                        && RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
                addRenderableWidget(roleBtn);
            }
            y += rows * 16 + 4;
        }

        // --- Licences ---
        if (!availableProfessionIds.isEmpty()) {
            int longestW = availableProfessionIds.stream()
                    .mapToInt(id -> this.font.width(I18n.get("rpessentials.gui.player_profile.license_label") + id + " (" + availableProfessionIds.size() + "/" + availableProfessionIds.size() + ")"))
                    .max().orElse(0);
            int fixedGap = 90;
            int navX = formX + Math.max(longestW + 12, fixedGap);

            boolean fitsInline = navX + 153 <= formX + formW;
            int navY = fitsInline ? y : y + 12;
            if (!fitsInline) navX = formX;

            addRenderableWidget(Button.builder(Component.literal("§7<"),
                            btn -> { stateSelectedProf = Math.floorMod(stateSelectedProf - 1,
                                    availableProfessionIds.size()); rebuild(); })
                    .pos(navX, navY).size(18, 18).build(SkinnedButton::new));

            addRenderableWidget(Button.builder(Component.literal("§7>"),
                            btn -> { stateSelectedProf = (stateSelectedProf + 1)
                                    % availableProfessionIds.size(); rebuild(); })
                    .pos(navX + 22, navY).size(18, 18).build(SkinnedButton::new));

            String profId = selectedProfId();
            boolean owned = selectedPlayerOwns(profId);
            Button addBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_add_license"),
                            btn -> grantSelectedLicense())
                    .pos(navX + 44, navY).size(50, 18).build(SkinnedButton::new);
            addBtn.active = !owned;
            addRenderableWidget(addBtn);

            Button revokeBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_revoke_license"),
                            btn -> revokeSelectedLicense())
                    .pos(navX + 98, navY).size(55, 18).build(SkinnedButton::new);
            revokeBtn.active = owned;
            addRenderableWidget(revokeBtn);

            int tempY = navY + 22;
            EditBox daysBox = new SkinnedEditBox(this.font, navX, tempY, 40, 18, Component.translatable("rpessentials.gui.field.days"));
            daysBox.setHint(Component.translatable("rpessentials.gui.player_profile.temp_days_hint"));
            daysBox.setMaxLength(3);
            daysBox.setValue(String.valueOf(stateTempDays));
            daysBox.setResponder(val -> {
                try { stateTempDays = Integer.parseInt(val.trim()); }
                catch (NumberFormatException e) { stateTempDays = 0; }
            });
            addRenderableWidget(daysBox);

            boolean tempAllowed = !owned && !selectedData().isPrepared();
            Button tempBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_temp_license"),
                            btn -> grantTempLicense())
                    .pos(navX + 44, tempY).size(60, 18).build(SkinnedButton::new);
            tempBtn.active = tempAllowed;
            addRenderableWidget(tempBtn);
        }
    }

    // =========================================================================
    // ONGLET NOTES
    // =========================================================================
    private void buildNotesTab(int formX, int formW, int contentY) {
        if (players.isEmpty()) return;

        OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
        List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> notes = sel.notes();

        // One block per note: a first row with the delete / edit buttons and the author line,
        // then one row per line of text. Rows are 12 px high.
        if (notesList == null) notesList = new ButtonRowList(Minecraft.getInstance(), 12);
        notesList.layout(formX - 6, contentY, formW + 12, this.height - FOOTER_LIST_GAP - contentY);
        int rw = notesList.rowWidth();
        int textX = 32;
        List<ButtonRowList.Row> rows = new ArrayList<>();
        if (notes.isEmpty()) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get("rpessentials.gui.player_profile.notes.none"), textX, 2));
        }
        for (int ni = 0; ni < notes.size(); ni++) {
            final OpenPlayerProfileGuiPacket.PlayerData.NoteEntry note = notes.get(ni);
            boolean isEd = stateEditingNoteId == note.id();
            boolean isPending = note.id() < 0;
            int color = isEd ? 0x33FFFF00 : isPending ? 0x1100FFFF : (ni % 2 == 0 ? 0x22FFFFFF : 0x14FFFFFF);
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal("§f" + note.text()), Math.max(40, rw - textX - 4));
            int size = 1 + Math.max(1, wrapped.size());

            String by = I18n.get("rpessentials.gui.player_profile.notes.by");
            String meta = (isPending ? "" : "§e#" + note.id() + " ") + "§8[" + note.timestamp() + "] §7" + by + " §f" + note.authorName();
            ButtonRowList.Row first = new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, color, 0, size).text(meta, textX, 2);
            if (!isPending) {
                boolean confirmDelete = pendingDeleteNoteId == note.id();
                first.add(Button.builder(Component.literal(confirmDelete ? "§c!" : "§c✗"),
                                btn -> {
                                    if (pendingDeleteNoteId == note.id()) {
                                        pendingDeleteNoteId = -1;
                                        deleteNote(note.id());
                                    } else {
                                        pendingDeleteNoteId = note.id();
                                        rebuild();
                                    }
                                })
                        .size(14, 12)
                        .tooltip(Tooltip.create(Component.translatable(confirmDelete
                                ? "rpessentials.gui.player_profile.notes.confirm_delete"
                                : "rpessentials.gui.player_profile.notes.delete_tooltip")))
                        .build(SkinnedButton::new), 1, 0);
                first.add(Button.builder(Component.literal(isEd ? "§e§l✎" : "§e✎"),
                                btn -> {
                                    if (stateEditingNoteId == note.id()) {
                                        stateEditingNoteId = -1;
                                        stateNoteInput = "";
                                    } else {
                                        stateEditingNoteId = note.id();
                                        stateNoteInput = note.text();
                                    }
                                    rebuild();
                                })
                        .size(14, 12).build(SkinnedButton::new), 16, 0);
            }
            rows.add(first);
            for (int li = 0; li < wrapped.size(); li++) {
                rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, color, li + 1, size).text(wrapped.get(li), textX, 1));
            }
            if (wrapped.isEmpty()) rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, color, 1, size));
        }
        notesList.setRows(rows);
        addRenderableWidget(notesList);

        // Footer: on the same line and with the same height as the Close button.
        int addBoxY = this.height - 35;
        int buttonsW = 44 + UiLayout.GAP + (stateEditingNoteId != -1 ? 46 + UiLayout.GAP : 0);
        int addBoxW = Math.max(60, closeButtonX() - UiLayout.GAP - buttonsW - formX);

        EditBox noteBox = new SkinnedEditBox(this.font, formX, addBoxY, addBoxW, 20,
                Component.translatable("rpessentials.gui.field.note"));
        noteBox.setHint(Component.literal(stateEditingNoteId != -1
                ? I18n.get("rpessentials.gui.player_profile.notes.edit_hint")
                : I18n.get("rpessentials.gui.player_profile.notes.new_hint")));
        noteBox.setMaxLength(256);
        noteBox.setValue(stateNoteInput);
        noteBox.setResponder(val -> stateNoteInput = val);
        addRenderableWidget(noteBox);

        String addLabel = stateEditingNoteId != -1
                ? I18n.get("rpessentials.gui.player_profile.notes.btn_save")
                : I18n.get("rpessentials.gui.player_profile.notes.btn_add");
        addRenderableWidget(Button.builder(Component.literal(addLabel),
                        btn -> addNote())
                .pos(formX + addBoxW + UiLayout.GAP, addBoxY).size(44, 20).build(SkinnedButton::new));

        if (stateEditingNoteId != -1) {
            addRenderableWidget(Button.builder(Component.literal(I18n.get("rpessentials.gui.player_profile.notes.btn_cancel")),
                            btn -> { stateEditingNoteId = -1; stateNoteInput = ""; rebuild(); })
                    .pos(formX + addBoxW + UiLayout.GAP + 44 + UiLayout.GAP, addBoxY).size(46, 20).build(SkinnedButton::new));
        }
    }

    // =========================================================================
    // ONGLET SIGNALEMENTS
    // =========================================================================
    /** Asks the server for the reports against the selected player, once per player while the screen is open. */
    private void requestReports() {
        if (players.isEmpty()) return;
        java.util.UUID id = selectedData().uuid();
        if (reportsRequested.add(id)) {
            PacketDistributor.sendToServer(new RequestPlayerReportsPacket(id));
        }
    }

    /** Called when the server sends the reports of a player (on request or after an action). */
    public void setReports(java.util.UUID uuid, boolean enabled, List<PlayerReportsPacket.View> reports) {
        reportsEnabled = enabled;
        reportsCache.put(uuid, reports);
        int open = 0;
        for (PlayerReportsPacket.View r : reports) if (r.isOpen()) open++;
        if (open > 0) reportCounts.put(uuid, open); else reportCounts.remove(uuid);
        rebuild();
    }

    private void reportAction(int id, ReportActionPacket.Action action) {
        if (players.isEmpty()) return;
        String note = action == ReportActionPacket.Action.CLOSE ? stateReportNote.trim() : "";
        PacketDistributor.sendToServer(new ReportActionPacket(selectedData().uuid(), id, action, note));
        if (action == ReportActionPacket.Action.CLOSE) stateReportNote = "";
        pendingDeleteReportId = -1;
    }

    private Button reportButton(String label, String tooltipKey, int width, Runnable onPress) {
        return Button.builder(Component.literal(label), btn -> onPress.run())
                .size(width, 12)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build(SkinnedButton::new);
    }

    private void buildReportsTab(int formX, int formW, int contentY) {
        if (players.isEmpty()) return;
        java.util.UUID uuid = selectedData().uuid();
        String k = "rpessentials.gui.player_profile.reports.";

        if (reportsList == null) reportsList = new ButtonRowList(Minecraft.getInstance(), 12);
        reportsList.layout(formX - 6, contentY, formW + 12, this.height - FOOTER_LIST_GAP - contentY);
        int rw = reportsList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();
        List<PlayerReportsPacket.View> reports = reportsCache.get(uuid);

        if (!reportsEnabled) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get(k + "disabled"), 4, 2));
        } else if (reports == null) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get(k + "loading"), 4, 2));
        } else if (reports.isEmpty()) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get(k + "none"), 4, 2));
        }

        if (reports != null && reportsEnabled) {
            for (PlayerReportsPacket.View r : reports) {
                boolean open = r.isOpen();
                int color = !open ? 0x10FFFFFF : "CLAIMED".equals(r.status()) ? 0x22FFAA00 : 0x22FF5555;

                // Text lines of the block: reason, place, then who handled it and the closing note.
                List<FormattedCharSequence> lines = new ArrayList<>(
                        this.font.split(Component.literal("§f" + r.reason()), Math.max(40, rw - 8)));
                lines.add(Component.literal("§8" + r.place()).getVisualOrderText());
                if (!r.handler().isEmpty()) {
                    lines.add(Component.literal("§7" + I18n.get(k + "handled_by") + " §f" + r.handler()).getVisualOrderText());
                }
                if (!r.note().isEmpty()) {
                    lines.addAll(this.font.split(Component.literal("§7" + I18n.get(k + "close_note") + " §f" + r.note()),
                            Math.max(40, rw - 8)));
                }
                int size = 1 + lines.size();

                // Header: action buttons, then id, date, reporter and status.
                ButtonRowList.Row first = new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, color, 0, size);
                int bx = 1;
                if ("OPEN".equals(r.status())) {
                    first.add(reportButton("§6⚑", k + "claim_tooltip", 14,
                            () -> reportAction(r.id(), ReportActionPacket.Action.CLAIM)), bx, 0);
                    bx += 15;
                }
                if (open) {
                    first.add(reportButton("§a✔", k + "close_tooltip", 14,
                            () -> reportAction(r.id(), ReportActionPacket.Action.CLOSE)), bx, 0);
                } else {
                    first.add(reportButton("§e↺", k + "reopen_tooltip", 14,
                            () -> reportAction(r.id(), ReportActionPacket.Action.REOPEN)), bx, 0);
                }
                bx += 15;
                first.add(reportButton("§bTP", k + "tp_tooltip", 18,
                        () -> reportAction(r.id(), ReportActionPacket.Action.TP)), bx, 0);
                bx += 19;
                boolean confirmDelete = pendingDeleteReportId == r.id();
                first.add(reportButton(confirmDelete ? "§c!" : "§c✗",
                        k + (confirmDelete ? "confirm_delete" : "delete_tooltip"), 14, () -> {
                            if (pendingDeleteReportId == r.id()) {
                                reportAction(r.id(), ReportActionPacket.Action.DELETE);
                            } else {
                                pendingDeleteReportId = r.id();
                                rebuild();
                            }
                        }), bx, 0);
                bx += 18;
                String status = I18n.get(k + "status." + r.status().toLowerCase(java.util.Locale.ROOT));
                first.text("§e#" + r.id() + " §8[" + r.date() + "] §7" + I18n.get(k + "by") + " §f" + r.reporter()
                        + " §8- " + status, bx, 2);
                rows.add(first);
                for (int li = 0; li < lines.size(); li++) {
                    rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, color, li + 1, size).text(lines.get(li), 4, 1));
                }
            }
        }
        reportsList.setRows(rows);
        addRenderableWidget(reportsList);

        // Optional closing note, used by the next close (check mark) click.
        int boxY = this.height - 35;
        EditBox noteBox = new SkinnedEditBox(this.font, formX, boxY,
                Math.max(60, closeButtonX() - UiLayout.GAP - formX), 20,
                Component.translatable(k + "note_hint"));
        noteBox.setHint(Component.literal("§7" + I18n.get(k + "note_hint")));
        noteBox.setMaxLength(ReportActionPacket.MAX_NOTE);
        noteBox.setValue(stateReportNote);
        noteBox.setResponder(val -> stateReportNote = val);
        addRenderableWidget(noteBox);
    }

    private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }


    private void buildManageTab(int formX, int formW, int y) {
        if (manageAction != null) { buildManagePopup(formX, formW, y); return; }

        boolean sensitive = Minecraft.getInstance().player != null
                && RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
        boolean online = selectedData().isOnline();
        boolean self = selectedIsSelf();
        int gap = UiLayout.GAP;
        if (manageList == null) manageList = new ButtonRowList(Minecraft.getInstance(), MANAGE_ROW_H);
        manageList.layout(formX - 6, y, formW + 12, this.height - 16 - y);
        int rowW = manageList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();

        for (int s = 0; s < MANAGE_ACTIONS.length; s++) {
            String sectionColor = MANAGE_SECTION_KEYS[s].equals("reset") ? "§c" : "§e";
            ButtonRowList.Row row = new ButtonRowList.Row()
                    .text(sectionColor + I18n.get("rpessentials.gui.player_profile.manage.section." + MANAGE_SECTION_KEYS[s]), 0, 0);
            int n = MANAGE_ACTIONS[s].length;
            for (int c = 0; c < n; c++) {
                final ManageService.Action a = MANAGE_ACTIONS[s][c];
                boolean reset = a == ManageService.Action.RESET_ALL;
                String label = I18n.get("rpessentials.gui.player_profile.manage.action." + a.name().toLowerCase());
                Button b = Button.builder(Component.literal(reset ? "§c§l" + label : label),
                                btn -> {
                                    manageAction = a;
                                    manageText = "";
                                    manageNumber = defaultManageNumber(a);
                                    rebuild();
                                })
                        .size(UiLayout.cellW(rowW, n, gap), 16).build(SkinnedButton::new);
                if (a.sensitive() && !sensitive) {
                    b.active = false;
                    b.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.sensitive_tooltip")));
                } else if (self && a.selfBlocked()) {
                    b.active = false;
                    b.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.self_tooltip")));
                } else if (a.needsOnline() && !online) {
                    b.active = false;
                    b.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.offline_tooltip")));
                } else if (a.queueable() && !online) {
                    b.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.queued_tooltip")));
                }
                row.add(b, UiLayout.cellX(0, rowW, n, gap, c), 11);
            }
            rows.add(row);
        }
        manageList.setRows(rows);
        addRenderableWidget(manageList);
    }

    // =========================================================================
    // MANAGE
    // =========================================================================
    private boolean canConfirmManage() {
        int own = ownLevel();
        return switch (manageAction) {
            case RESET_ALL -> manageText.trim().equalsIgnoreCase(selectedData().mcName());
            case TP_PLAYER_PLATFORM -> !manageText.trim().isEmpty();
            case OP -> manageNumber >= 1 && manageNumber <= own && !(selectedIsSelf() && manageNumber < own);
            case VANISH -> manageNumber >= 0 && manageNumber <= own;
            default -> true;
        };
    }

    private void buildManagePopup(int formX, int formW, int y) {
        ManageService.Action a = manageAction;
        int boxW = Math.min(formW - 4, 260);
        int cy = y + 34;
        int btnY = y + 90;
        boolean resetAll = a == ManageService.Action.RESET_ALL;
        int confirmW = resetAll ? 130 : 100;

        Button confirmBtn = Button.builder(
                        Component.translatable(resetAll
                                ? "rpessentials.gui.player_profile.manage.btn_confirm_reset"
                                : "rpessentials.gui.player_profile.manage.btn_confirm"),
                        btn -> sendManage())
                .pos(formX, btnY).size(confirmW, 20).build(SkinnedButton::new);
        confirmBtn.active = canConfirmManage();

        if (a == ManageService.Action.OP || a == ManageService.Action.VANISH) {
            int own = ownLevel();
            boolean self = selectedIsSelf();
            int from = a == ManageService.Action.OP ? 1 : 0;
            for (int lvl = from; lvl <= 4; lvl++) {
                final int l = lvl;
                Button lb = Button.builder(Component.literal(manageNumber == l ? "§e§l" + l : "§7" + l),
                                btn -> { manageNumber = l; rebuild(); })
                        .pos(formX + (lvl - from) * 24, cy).size(22, 18).build(SkinnedButton::new);
                if (l > own) {
                    lb.active = false;
                    lb.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.level_too_high_tooltip")));
                } else if (a == ManageService.Action.OP && self && l < own) {
                    lb.active = false;
                    lb.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.cannot_lower_tooltip")));
                }
                addRenderableWidget(lb);
            }
        }

        if (a == ManageService.Action.ABSENCE_ADD || a == ManageService.Action.TEMPBAN) {
            EditBox days = new SkinnedEditBox(this.font, formX, cy, 80, 18, Component.translatable("rpessentials.gui.field.days"));
            days.setHint(Component.translatable("rpessentials.gui.player_profile.manage.days_hint"));
            days.setMaxLength(3);
            days.setValue(String.valueOf(manageNumber));
            days.setResponder(val -> {
                try { manageNumber = Integer.parseInt(val.trim()); }
                catch (NumberFormatException e) { manageNumber = 0; }
            });
            addRenderableWidget(days);
            cy += 24;
        }

        if (a == ManageService.Action.KICK || a == ManageService.Action.BAN || a == ManageService.Action.TEMPBAN || a == ManageService.Action.ABSENCE_ADD) {
            EditBox reason = new SkinnedEditBox(this.font, formX, cy, boxW, 18, Component.translatable("rpessentials.gui.field.reason"));
            reason.setHint(Component.translatable("rpessentials.gui.player_profile.manage.reason_hint"));
            reason.setMaxLength(200);
            reason.setValue(manageText);
            reason.setResponder(val -> manageText = val);
            addRenderableWidget(reason);
        }

        if (resetAll) {
            EditBox nameBox = new SkinnedEditBox(this.font, formX, cy + 14, boxW, 18, Component.translatable("rpessentials.gui.field.name"));
            nameBox.setHint(Component.translatable("rpessentials.gui.player_profile.manage.type_name_hint"));
            nameBox.setMaxLength(32);
            nameBox.setValue(manageText);
            nameBox.setResponder(val -> {
                manageText = val;
                confirmBtn.active = canConfirmManage();
            });
            addRenderableWidget(nameBox);
        }

        if (a == ManageService.Action.TP_PLAYER_PLATFORM) {
            int cols = Math.max(1, Math.min(3, (formW + UiLayout.GAP) / (80 + UiLayout.GAP)));
            int shown = Math.min(availablePlatforms.size(), cols * 2);
            for (int i = 0; i < shown; i++) {
                final String id = availablePlatforms.get(i);
                boolean sel = id.equals(manageText);
                addRenderableWidget(Button.builder(Component.literal(sel ? "§e§l" + id : "§7" + id),
                                btn -> { manageText = id; rebuild(); })
                        .pos(formX + (i % cols) * (80 + UiLayout.GAP), cy + (i / cols) * 20).size(80, 16).build(SkinnedButton::new));
            }
        }

        addRenderableWidget(confirmBtn);
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.player_profile.manage.btn_cancel"),
                        btn -> { manageAction = null; manageText = ""; rebuild(); })
                .pos(formX + confirmW + 4, btnY).size(80, 20).build(SkinnedButton::new));
    }

    private void sendManage() {
        if (players.isEmpty() || manageAction == null) return;
        if ((manageAction == ManageService.Action.ABSENCE_ADD || manageAction == ManageService.Action.TEMPBAN)
                && (manageNumber < 1 || manageNumber > 365)) return;
        String text = manageAction == ManageService.Action.RESET_ALL ? "" : manageText.trim();
        // Tempban: the GUI asks for days, the server expects minutes.
        int number = manageAction == ManageService.Action.TEMPBAN ? manageNumber * 1440 : manageNumber;
        PacketDistributor.sendToServer(new ManagePlayerPacket(
                selectedData().uuid(), manageAction, text, number));
        manageAction = null;
        manageText = "";
        rebuild();
    }

    private void renderManageTab(GuiGraphics g, int formX, int y, OpenPlayerProfileGuiPacket.PlayerData sel) {
        if (manageAction == null) return; // sections are rows of the manage list widget
        String label = I18n.get("rpessentials.gui.player_profile.manage.action." + manageAction.name().toLowerCase());
        g.drawString(this.font, "§c§l" + label, formX, y, 0xFFFFFF, false);
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.manage.confirm_generic", label, sel.mcName()),
                formX, y + 12, 0xAAAAAA, false);
        if (manageAction == ManageService.Action.RESET_ALL) {
            g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.manage.reset_all_warning"),
                    formX, y + 24, 0xFF5555, false);
        }
        if (manageAction == ManageService.Action.TP_PLAYER_PLATFORM) {
            g.drawString(this.font, I18n.get(availablePlatforms.isEmpty()
                            ? "rpessentials.gui.player_profile.manage.no_platform"
                            : "rpessentials.gui.player_profile.manage.platform_label"),
                    formX, y + 24, 0xAAAAAA, false);
        }
        if (manageAction == ManageService.Action.OP) {
            g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.manage.level_label"),
                    formX, y + 24, 0xAAAAAA, false);
        }
        if (manageAction == ManageService.Action.VANISH) {
            g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.manage.vanish_level_label"),
                    formX, y + 24, 0xAAAAAA, false);
        }
    }


    // =========================================================================
    // RENDU
    // =========================================================================
    private int tabLineColor() {
        return switch (activeTab) {
            case 1 -> 0xFF55FF55; // Stats
            case 2 -> 0xFF55FFFF; // Notes
            case 3 -> 0xFFFF5555; // Manage
            case 4 -> 0xFFFF55FF; // History
            case 5 -> 0xFFFFAA00; // Reports
            default -> 0xFFFFFF55; // Profil
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0x99000000);
        int formX = LIST_W + MARGIN * 3;

        List<Integer> visible = getVisibleIndices();

        // Panneau liste
        GuiSkin.panel(g, MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.players_header", visible.size()),
                MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);

        if (!players.isEmpty() && visible.isEmpty()) {
            g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.player_profile.filter_empty"),
                    MARGIN + 3, PANEL_TOP + 26, 0x666666, false);
        }

        // Panneau formulaire
        GuiSkin.panel(g, LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, tabLineColor());

        if (players.isEmpty()) {
            g.drawCenteredString(this.font, "§7" + I18n.get("rpessentials.gui.player_profile.no_players"),
                    (LIST_W + MARGIN * 2 + this.width) / 2, this.height / 2, 0x888888);
            super.render(g, mouseX, mouseY, delta);
            return;
        }

        OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
        String statusDot = sel.isOnline() ? "§a● " : "§7o ";
        g.drawCenteredString(this.font, statusDot + "§e" + sel.mcName(),
                (LIST_W + MARGIN * 2 + this.width) / 2, PANEL_TOP + 5, 0xFFFFFF);

        int contentY  = PANEL_TOP + 36;
        int formRight = this.width - MARGIN;

        switch (activeTab) {
            case 0 -> renderProfileTab(g, formX, formRight - formX - MARGIN, contentY, sel);
            case 1 -> renderStatsTab(g, formX, formRight, contentY, sel);
            case 2 -> renderNotesTab(g, formX, formRight, contentY, sel);
            case 3 -> renderManageTab(g, formX, contentY, sel);
            case 4 -> {} // History: list widget, drawn with the other widgets
            case 5 -> renderFooterSeparator(g, formX, formRight); // Reports: list widget + footer field
        }

        super.render(g, mouseX, mouseY, delta);
        
    }

    private void renderProfileTab(GuiGraphics g, int formX, int formW, int y,
                                  OpenPlayerProfileGuiPacket.PlayerData sel) {
        String nickLabel = I18n.get("rpessentials.gui.player_profile.nick_label_draw");
        g.drawString(this.font, nickLabel, formX, y + 6, 0x888888, false);
        ColorPalette.drawPreview(g, this.font, nickLabel, stateNick, stateNickColorIndex, formX, y + 6);
        y += 44;

        ColorPalette.Dims pal = ColorPalette.dims(this.width - formX - MARGIN * 2, this.height);
        ColorPalette.drawSelection(g, pal, formX, y, stateNickColorIndex);
        y += pal.height() + 8;

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.role_label_draw"),
                formX, y, 0x888888, false);
        y += 12;

        if (!availableRoles.isEmpty()) {
            int maxBtnW = 80;
            int minBtnW = 40;
            int gap     = UiLayout.GAP;
            int availW  = formW;
            int cols    = Math.max(1, availW / (minBtnW + gap));
            int rows    = (int) Math.ceil((double) availableRoles.size() / cols);
            y += rows * 16 + 4;
        }

        if (!availableProfessionIds.isEmpty()) {
            int longestW = availableProfessionIds.stream()
                    .mapToInt(id -> this.font.width(I18n.get("rpessentials.gui.player_profile.license_label") + id + " (" + availableProfessionIds.size() + "/" + availableProfessionIds.size() + ")"))
                    .max().orElse(0);
            int fixedGap = 90;
            int navX = formX + Math.max(longestW + 12, fixedGap);
            boolean fitsInline = navX + 153 <= formX + formW;
            int navY = fitsInline ? y : y + 12;

            String profId  = selectedProfId();
            boolean owned  = selectedPlayerOwns(profId);
            String color   = owned ? "§a" : "§7";
            g.drawString(this.font,
                    "§8" + I18n.get("rpessentials.gui.player_profile.license_label") + color + profId
                            + " §8(" + (stateSelectedProf + 1) + "/" + availableProfessionIds.size() + ")",
                    formX, navY + 5, 0xAAAAAA, false);
        }

        List<String> lics = sel.currentLicenses();
        if (!lics.isEmpty()) {
            int textY = this.height - 50;
            g.drawString(this.font,
                    I18n.get("rpessentials.gui.player_profile.owned") + String.join("§8, §7", lics),
                    formX, textY, 0x777777, false);
        }
    }

    /** Playtime in the client's language ("3d 4h 12min"). */
    private static String formatPlaytime(long ms) {
        if (ms < 60_000L) return I18n.get("rpessentials.time.less_minute");
        long totalMin = ms / 60_000L;
        long days = totalMin / 1440L;
        long hours = (totalMin % 1440L) / 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(I18n.get("rpessentials.time.days", days)).append(' ');
        if (hours > 0) sb.append(I18n.get("rpessentials.time.hours", hours)).append(' ');
        sb.append(I18n.get("rpessentials.time.minutes", totalMin % 60L));
        return sb.toString();
    }

    /** Asks the server for the selected player's extra stats, once per player while the screen is open. */
    private void requestStats() {
        if (players.isEmpty()) return;
        java.util.UUID id = selectedData().uuid();
        if (statsRequested.add(id)) {
            PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestPlayerStatsPacket(id));
        }
    }

    /** Called when the server answers a stats request. */
    public void setStats(java.util.UUID uuid, List<String> lines) {
        statsCache.put(uuid, lines);
        if (statsList != null && activeTab == 1 && !players.isEmpty() && selectedData().uuid().equals(uuid)) {
            statsList.setLines(uuid, statsLines(selectedData()));
        }
    }

    private void buildStatsList(int formX, int formW, int contentY) {
        if (players.isEmpty()) return;
        if (statsList == null) {
            statsList = new TextLinesList(Minecraft.getInstance());
            statsList.setStriped(false);
        }
        statsList.layout(formX, contentY + 16, formW, this.height - 56 - (contentY + 16));
        OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
        statsList.setLines(sel.uuid(), statsLines(sel));
        addRenderableWidget(statsList);
    }

    /** Base stats (from the profile data) followed by the extra stats sent by the server. */
    private List<String> statsLines(OpenPlayerProfileGuiPacket.PlayerData sel) {
        List<String> lines = new ArrayList<>();
        String warnColor = sel.activeWarnCount() == 0 ? "§a" : sel.activeWarnCount() >= 3 ? "§c" : "§e";
        lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.warns") + warnColor + sel.activeWarnCount());
        lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.muted") + (sel.isMuted()
                ? I18n.get("rpessentials.gui.player_profile.stats.muted_yes") + " §8(" + sel.muteExpiry() + ")"
                : I18n.get("rpessentials.gui.player_profile.stats.muted_no")));
        lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.playtime") + "§f" + formatPlaytime(sel.playtimeMs()));
        if (sel.isOnline() && sel.sessionMs() > 60_000L)
            lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.session") + "§b" + formatPlaytime(sel.sessionMs()));
        lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.notes") + (sel.noteCount() == 0
                ? I18n.get("rpessentials.gui.player_profile.stats.notes_none")
                : "§e" + I18n.get("rpessentials.gui.player_profile.stats.notes_count", sel.noteCount())));
        List<String> lics = sel.currentLicenses();
        lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.licenses") + (lics.isEmpty()
                ? "§8" + I18n.get("rpessentials.gui.player_profile.stats.licenses_none")
                : "§f" + String.join("§7, §f", lics)));
        lines.add("");
        List<String> extra = statsCache.get(sel.uuid());
        if (extra == null) lines.add("§8" + I18n.get("rpessentials.gui.player_profile.history.loading"));
        else lines.addAll(extra);
        return lines;
    }

    /** Asks the server for the selected player's history, once per player while the screen is open. */
    private void requestHistory() {
        if (players.isEmpty()) return;
        java.util.UUID id = selectedData().uuid();
        if (historyRequested.add(id)) {
            PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestPlayerHistoryPacket(id));
        }
    }

    /** Called when the server answers a history request. */
    public void setHistory(java.util.UUID uuid, boolean enabled, List<String> lines) {
        historyEnabled = enabled;
        historyCache.put(uuid, lines);
        if (historyList != null && activeTab == 4 && !players.isEmpty() && selectedData().uuid().equals(uuid)) {
            historyList.setLines(uuid, historyLines(uuid));
        }
    }

    /** Lines shown in the History tab, or one status line (disabled / loading / empty). */
    private List<String> historyLines(java.util.UUID uuid) {
        if (!historyEnabled) return List.of("§8" + I18n.get("rpessentials.gui.player_profile.history.disabled"));
        List<String> lines = historyCache.get(uuid);
        if (lines == null) return List.of("§8" + I18n.get("rpessentials.gui.player_profile.history.loading"));
        if (lines.isEmpty()) return List.of("§8" + I18n.get("rpessentials.gui.player_profile.history.none"));
        return lines;
    }

    private void buildHistoryList(int formX, int formW, int contentY) {
        if (players.isEmpty()) return;
        if (historyList == null) historyList = new TextLinesList(Minecraft.getInstance());
        historyList.layout(formX, contentY, formW, this.height - 40 - contentY);
        java.util.UUID id = selectedData().uuid();
        historyList.setLines(id, historyLines(id));
        addRenderableWidget(historyList);
    }


    private void renderStatsTab(GuiGraphics g, int formX, int formRight, int y,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        // The lines are a list widget (buildStatsList); only the title and the footer are drawn here.
        g.drawString(this.font, "§l" + I18n.get("rpessentials.gui.player_profile.stats.header"),
                formX, y, tabLineColor(), false);
        int footerY = this.height - 52;
        GuiSkin.separator(g, formX, footerY, formRight - MARGIN, footerY + 1, 0xFF444444);
        g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.stats.footer"),
                formX, footerY + 6, 0x555555, false);
    }

    private void renderNotesTab(GuiGraphics g, int formX, int formRight, int contentY,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        // The notes are a list widget and the field is in the footer (buildNotesTab): only the separator is drawn here.
        renderFooterSeparator(g, formX, formRight);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    // =========================================================================
    // SCROLL MOLETTE
    // =========================================================================

    // Mouse wheel: handled by the list widgets of each tab.

    // =========================================================================
    // LOGIQUE
    // =========================================================================
    private void loadPlayerState(int idx) {
        stateSelectedPlayer = idx;
        if (notesList != null) notesList.setScrollAmount(0);
        stateEditingNoteId  = -1;
        pendingDeletePlayerUuid = null;
        pendingNickReset = false;
        pendingDeleteNoteId = -1;
        OpenPlayerProfileGuiPacket.PlayerData p = players.get(idx);
        String rawNick = p.currentNick();
        stateNickColorIndex = 0;
        String lead = ColorPalette.leadingCode(rawNick);
        if (!lead.isEmpty()) {
            int colorIdx = ColorPalette.ensure(lead);
            if (colorIdx > 0) { stateNickColorIndex = colorIdx; rawNick = rawNick.substring(lead.length()); }
        }
        stateNick = rawNick.replace('§', '&');
        stateRole  = p.currentRole();
        stateSelectedProf = 0;
        stateNoteInput    = "";

        manageAction = null;
        if (manageList != null) manageList.setScrollAmount(0);
    }

    private void applyProfile() {
        if (players.isEmpty()) return;
        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        String finalNick = stateNick.trim().isEmpty()
                ? ""
                : stateNickColorIndex == 0
                ? stateNick.trim()
                : ColorPalette.code(stateNickColorIndex).replace('&', '§') + stateNick.trim();

        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), finalNick, stateRole.trim(), "", false, false, 0));

        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), finalNick, stateRole.trim(),
                target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                target.noteCount(), target.isOnline(), target.notes(), target.isPrepared()));
        rebuild();
    }

    private void grantSelectedLicense() {
        if (players.isEmpty()) return;
        String profId = selectedProfId();
        if (profId.isEmpty() || selectedPlayerOwns(profId)) return;

        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), "", "", profId, false, false, 0));

        List<String> updated = new ArrayList<>(target.currentLicenses());
        updated.add(profId);
        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                updated, target.activeWarnCount(), target.isMuted(), target.muteExpiry(),
                target.playtimeMs(), target.sessionMs(), target.noteCount(), target.isOnline(),
                target.notes(), target.isPrepared()));
        rebuild();
    }

    private void grantTempLicense() {
        if (players.isEmpty() || stateTempDays < 1 || stateTempDays > 365) return;
        String profId = selectedProfId();
        if (profId.isEmpty() || selectedPlayerOwns(profId)) return;

        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        if (target.isPrepared()) return;
        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), "", "", profId, false, false, stateTempDays));

        List<String> updated = new ArrayList<>(target.currentLicenses());
        updated.add(profId);
        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                updated, target.activeWarnCount(), target.isMuted(), target.muteExpiry(),
                target.playtimeMs(), target.sessionMs(), target.noteCount(), target.isOnline(),
                target.notes(), target.isPrepared()));
        rebuild();
    }

    private void revokeSelectedLicense() {
        if (players.isEmpty()) return;
        String profId = selectedProfId();
        if (profId.isEmpty() || !selectedPlayerOwns(profId)) return;

        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), "", "", profId, true, false, 0));

        List<String> updated = new ArrayList<>(target.currentLicenses());
        updated.remove(profId);
        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                updated, target.activeWarnCount(), target.isMuted(), target.muteExpiry(),
                target.playtimeMs(), target.sessionMs(), target.noteCount(), target.isOnline(),
                target.notes(), target.isPrepared()));
        rebuild();
    }

    private void addNote() {
        if (players.isEmpty() || stateNoteInput.trim().isEmpty()) return;
        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        String text   = stateNoteInput.trim();
        String author = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getName().getString() : "you";
        String now    = LocalDate.now().toString();

        if (stateEditingNoteId != -1) {
            int oldId = stateEditingNoteId;
            PacketDistributor.sendToServer(new PlayerNoteActionPacket(target.uuid(), false, oldId, text));

            List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> updated = new ArrayList<>();
            for (OpenPlayerProfileGuiPacket.PlayerData.NoteEntry n : target.notes()) {
                if (n.id() == oldId)
                    updated.add(new OpenPlayerProfileGuiPacket.PlayerData.NoteEntry(
                            tempNoteIdCounter--, text, author, now));
                else
                    updated.add(n);
            }
            players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                    target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                    target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                    target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                    target.noteCount(), target.isOnline(), updated, target.isPrepared()));
            stateEditingNoteId = -1;
        } else {
            PacketDistributor.sendToServer(new PlayerNoteActionPacket(target.uuid(), false, 0, text));

            List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> updated = new ArrayList<>(target.notes());
            updated.add(new OpenPlayerProfileGuiPacket.PlayerData.NoteEntry(
                    tempNoteIdCounter--, text, author, now));
            players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                    target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                    target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                    target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                    target.noteCount() + 1, target.isOnline(), updated, target.isPrepared()));
            if (notesList != null) notesList.scrollToEndOnce();
        }

        stateNoteInput = "";
        rebuild();
    }

    private void deleteNote(int noteId) {
        if (players.isEmpty()) return;
        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        PacketDistributor.sendToServer(new PlayerNoteActionPacket(
                target.uuid(), true, noteId, ""));

        List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> updated = new ArrayList<>();
        for (OpenPlayerProfileGuiPacket.PlayerData.NoteEntry n : target.notes()) {
            if (n.id() != noteId) updated.add(n);
        }
        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), target.currentNick(), target.currentRole(),
                target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                Math.max(0, target.noteCount() - 1), target.isOnline(), updated, target.isPrepared()));
        rebuild();
    }

    private String stripColor(String s) {
        return ColorHelper.stripAll(s);
    }



    @Override public boolean isPauseScreen() { return false; }
}