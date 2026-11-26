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

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;

@OnlyIn(Dist.CLIENT)
public class PlayerProfileScreen extends net.rp.rpessentials.client.skin.CanvasScreen implements net.rp.rpessentials.client.skin.SkinScope {

    /** Textures of the open tab: sprites/player_profile/<tab>/ (see GuiSkin). */
    @Override
    public String skinTab() { return TAB_KEYS[activeTab]; }


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
    /** Field of the Notes tab, focused again after a note is sent so several can be typed in a row. */
    private EditBox noteBox;
    private static int filterMode = 0;
    private int sortButtonX, sortButtonY;
    private static final String[] FILTER_KEYS = { "online", "offline", "prepared", "all", "reported" };
    /** Icon of the filter button for each filter (textures/gui/icons/). */
    private static final String[] FILTER_ICONS = {
            "steve_head_online", "steve_head_offline", "steve_head_prepared", "steve_head", "steve_head_reported" };
    private String pendingDeletePlayerUuid = null;
    private boolean pendingNickReset = false;
    private int pendingDeleteNoteId = -1;
    /** Apply button of the Profile tab, refreshed every frame (greyed / bold). */
    private Button applyButton;
    /** Close was clicked once with unapplied profile changes: the next click (or Escape) closes. */
    private boolean confirmClose = false;
    /** Player clicked once while the profile has unapplied changes: clicking them again selects them. */
    private int confirmPlayer = -1;

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

    /**
     * The server sent the players again (refresh, profile prepared or deleted): same tab, same player and same
     * scroll positions as the screen it replaces. The list widgets keep their scroll when their rows change.
     */
    public PlayerProfileScreen keepView(PlayerProfileScreen old) {
        playerList = old.playerList;
        activeTab  = old.activeTab;
        if (old.players.isEmpty()) return this;
        java.util.UUID selected = old.selectedData().uuid();
        for (int i = 0; i < players.size(); i++) {
            if (!players.get(i).uuid().equals(selected)) continue;
            loadPlayerState(i);
            notesList   = old.notesList;
            manageList  = old.manageList;
            reportsList = old.reportsList;
            historyList = old.historyList;
            statsList   = old.statsList;
            break;
        }
        return this;
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
    protected void initContent() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN * 2;
        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        

        // ── Bouton de tri : cycle En ligne -> Hors ligne -> Prévu -> Tous ───
        // Everything stays inside the panel frame (7 px border of the textures).
        sortButtonX = MARGIN + LIST_W - 24;
        sortButtonY = PANEL_TOP + 8;
        addRenderableWidget(net.rp.rpessentials.client.skin.IconButton.create(FILTER_ICONS[filterMode], "filter",
                sortButtonX, sortButtonY, 18, 12,
                btn -> {
                    filterMode = (filterMode + 1) % FILTER_KEYS.length;
                    if (playerList != null) playerList.setScrollAmount(0);
                    rebuild();
                },
                Tooltip.create(Component.translatable(
                        "rpessentials.gui.player_profile.filter." + FILTER_KEYS[filterMode]))));

        addRenderableWidget(net.rp.rpessentials.client.skin.IconButton.refresh(sortButtonX - 20, sortButtonY, 18, 12,
                btn -> PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestOpenGuiPacket(
                        net.rp.rpessentials.network.RequestOpenGuiPacket.GuiType.PLAYER_PROFILE))));

        // ── Liste joueurs (gauche) : widget de liste Minecraft ─────────────
        if (playerList == null) playerList = new ButtonRowList(Minecraft.getInstance(), ROW_H);
        playerList.layout(MARGIN - 3, PANEL_TOP + 22, LIST_W - 2, footerY() - 4 - (PANEL_TOP + 22));
        int rw = playerList.rowWidth();
        List<ButtonRowList.Row> playerRows = new ArrayList<>();
        for (int idx : getVisibleIndices()) {
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(idx);

            // "Nickname (RealName)": the nickname in its own colors, the real name in grey; the selected one in bold.
            boolean selected = idx == stateSelectedPlayer;
            String statusSymbol = p.isPrepared() ? "§b# " : (p.isOnline() ? "§a● " : "§7○ ");
            net.minecraft.network.chat.MutableComponent name = Component.empty().withStyle(s -> s.withBold(selected));
            net.minecraft.network.chat.MutableComponent label = Component.literal((selected ? "§e> " : "  ") + statusSymbol);
            if (!p.currentNick().isEmpty()) {
                name.append(net.rp.rpessentials.util.ColorHelper.parseSection(
                        "§f" + net.rp.rpessentials.util.ColorHelper.translateAlternateColorCodes(p.currentNick())));
                label.append(name).append(Component.literal(" §7(" + p.mcName() + ")"));
            } else {
                label.append(name.append(Component.literal("§f" + p.mcName())));
            }
            String badges = "";
            if (p.activeWarnCount() > 0)    badges += " §c[" + p.activeWarnCount() + "W]";
            if (p.isMuted())                badges += " §6[M]";
            int openReports = reportCounts.getOrDefault(p.uuid(), 0);
            if (openReports > 0)            badges += " §6[" + openReports + "R]";
            if (!badges.isEmpty()) label.append(Component.literal(badges));

            ButtonRowList.Row row = new ButtonRowList.Row();
            int rowW = p.isPrepared() ? rw - 18 : rw;
            boolean armed = idx == confirmPlayer;
            if (armed) label = Component.literal(UiLayout.fit(this.font, "§c" + I18n.get("rpessentials.gui.unsaved.discard_switch"), rowW - 6));
            row.add(Button.builder(label, btn -> {
                        // Another player would replace the nickname / role typed but not applied: ask first.
                        if (idx != stateSelectedPlayer && profileDirty() && idx != confirmPlayer) {
                            confirmPlayer = idx;
                            rebuild();
                            return;
                        }
                        confirmPlayer = -1;
                        loadPlayerState(idx);
                        rebuild();
                    })
                    .size(rowW, ROW_H - 2)
                    .tooltip(armed ? Tooltip.create(Component.translatable("rpessentials.gui.unsaved.discard_switch_tooltip")) : null)
                    .build(SkinnedButton.row(idx == stateSelectedPlayer, "players")), 0);

            if (p.isPrepared()) {
                String uuidStr = p.uuid().toString();
                Button side = uuidStr.equals(pendingDeletePlayerUuid)
                        ? Button.builder(Component.literal("§c!"),
                                        btn -> PacketDistributor.sendToServer(
                                                new net.rp.rpessentials.network.DeletePendingProfilePacket(p.uuid())))
                                .size(16, ROW_H - 2)
                                .tooltip(Tooltip.create(Component.translatable(
                                        "rpessentials.gui.player_profile.confirm_delete_prepared")))
                                .build(SkinnedButton.named("player_delete_confirm"))
                        : Button.builder(Component.literal("§c✕"),
                                        btn -> { pendingDeletePlayerUuid = uuidStr; rebuild(); })
                                .size(16, ROW_H - 2)
                                .tooltip(Tooltip.create(Component.translatable(
                                        "rpessentials.gui.player_profile.delete_prepared_tooltip")))
                                .build(SkinnedButton.named("player_delete"));
                row.add(side, rw - 16);
            }
            playerRows.add(row);
        }
        playerList.setRows(playerRows);
        addRenderableWidget(playerList);

        // ── Ajout d'un joueur jamais connecté ───────────────────────────────
        EditBox addPlayerBox = new SkinnedEditBox(this.font, MARGIN + 6, footerY(), LIST_W - 44, 20,
                Component.translatable("rpessentials.gui.player_profile.add_label")).skin("add_player");
        addPlayerBox.setHint(Component.translatable("rpessentials.gui.player_profile.add_label")
                .copy().withStyle(ChatFormatting.GRAY));
        addPlayerBox.setMaxLength(32);
        addPlayerBox.setValue(stateAddPlayerName);
        addPlayerBox.setTooltip(Tooltip.create(
                Component.translatable("rpessentials.gui.player_profile.add_tooltip")));
        addRenderableWidget(addPlayerBox);

        Button addPlayerButton = addRenderableWidget(Button.builder(Component.literal("§a+"),
                        btn -> sendAddPlayerRequest())
                .pos(MARGIN + LIST_W - 34, footerY()).size(28, 20)
                .tooltip(Tooltip.create(
                        Component.translatable("rpessentials.gui.player_profile.add_tooltip")))
                .build(SkinnedButton.named("add_player")));
        // Greyed out while no name is typed.
        Runnable refreshAdd = () -> {
            boolean ok = !stateAddPlayerName.isBlank();
            addPlayerButton.active = ok;
            addPlayerButton.setMessage(Component.literal(ok ? "§a+" : "§7+"));
        };
        refreshAdd.run();
        addPlayerBox.setResponder(val -> { stateAddPlayerName = val; refreshAdd.run(); });

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
                    .pos(UiLayout.cellX(formX, formW, TAB_KEYS.length, UiLayout.TAB_GAP, i), PANEL_TOP + 18)
                    .size(tabW, 16).build(SkinnedButton.tab(ti == activeTab, TAB_KEYS[ti])));
        }

        int contentY = PANEL_TOP + 36;

        // Content of the open tab: its textures are in player_profile/<tab>/ (the rest is shared).
        GuiSkin.beginTab();
        try {
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
            applyButton = Button.builder(Component.translatable("rpessentials.gui.player_profile.btn_apply"),
                            btn -> applyProfile())
                    .pos(formX, footerY()).size(150, 20).build(SkinnedButton.named("apply"));
            SaveUi.refresh(applyButton, I18n.get("rpessentials.gui.player_profile.btn_apply"), profileDirty());
            addRenderableWidget(applyButton);
            // Character sheet (staff only, prepared profiles included): opens its own screen.
            int sheetX = formX + 150 + UiLayout.GAP;
            int sheetW = Math.min(130, closeButtonX() - UiLayout.GAP - sheetX);
            if (sheetW >= 40) {
                OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
                addRenderableWidget(Button.builder(
                                Component.literal(UiLayout.fit(this.font,
                                        I18n.get("rpessentials.gui.player_profile.btn_sheet"), sheetW - 8)),
                                btn -> Minecraft.getInstance().setScreen(
                                        new CharacterSheetScreen(this, sel.uuid(), sel.mcName(), sel.currentNick(),
                                                nick -> setNicknameFromSheet(sel.uuid(), nick))))
                        .pos(sheetX, footerY()).size(sheetW, 20)
                        .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.player_profile.btn_sheet_tooltip")))
                        .build(SkinnedButton.named("sheet")));
            }
        }
        } finally {
            GuiSkin.endTab();
        }

        addRenderableWidget(Button.builder(
                        Component.literal(UiLayout.fit(this.font,
                                SaveUi.closeLabel(confirmClose, "rpessentials.gui.player_profile.btn_close"), 56)),
                        btn -> onClose())
                .pos(closeButtonX(), footerY()).size(60, 20)
                .tooltip(SaveUi.closeTooltip(confirmClose))
                .build(SkinnedButton.named("close")));
    }

    /** The nickname or role typed in the Profile tab differs from the player's current ones. */
    private boolean profileDirty() {
        if (players.isEmpty()) return false;
        OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
        String nick = stateNick.trim().isEmpty() ? ""
                : ColorPalette.apply(stateNickColorIndex, stateNick.trim()).replace('&', '§');
        String current = sel.currentNick() == null ? "" : sel.currentNick().replace('&', '§');
        boolean nickChanged = !nick.isEmpty() && !nick.equals(current);
        String role = stateRole.trim();
        boolean roleChanged = !role.isEmpty() && !role.equals(sel.currentRole() == null ? "" : sel.currentRole().trim());
        return nickChanged || roleChanged;
    }

    @Override
    public void onClose() {
        if (profileDirty() && !confirmClose) {
            confirmClose = true;
            rebuild();
            return;
        }
        super.onClose();
    }

    /** Left edge of the Close button, the footer fields stop before it. */
    /** Y of the bottom buttons and fields, inside the panel frame. */
    private int footerY() {
        return this.height - 38;
    }

    private int closeButtonX() {
        return this.width - MARGIN - 68;
    }

    /** Space kept under a tab list for the footer: separator, then the field / buttons line. */
    private static final int FOOTER_LIST_GAP = 44;

    /** Separator right above the footer line (Notes and Reports tabs). */
    private void renderFooterSeparator(GuiGraphics g, int formX, int formRight) {
        GuiSkin.separator(g, "footer", formX, this.height - 41, formRight - MARGIN, this.height - 40, 0xFF333333);
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
                Component.translatable("rpessentials.gui.player_profile.nick_label")).skin("nick");
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
                .build(SkinnedButton.named("nick_reset")));
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
                        .size(btnW, 14).build(SkinnedButton.named("role"));
                roleBtn.active = net.rp.rpessentials.client.ClientPerms.has(net.rp.rpessentials.permission.RpPerm.ROLES_SET);
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
                    .pos(navX, navY).size(18, 18).build(SkinnedButton.named("license_prev")));

            addRenderableWidget(Button.builder(Component.literal("§7>"),
                            btn -> { stateSelectedProf = (stateSelectedProf + 1)
                                    % availableProfessionIds.size(); rebuild(); })
                    .pos(navX + 22, navY).size(18, 18).build(SkinnedButton.named("license_next")));

            String profId = selectedProfId();
            boolean owned = selectedPlayerOwns(profId);
            Button addBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_add_license"),
                            btn -> grantSelectedLicense())
                    .pos(navX + 44, navY).size(50, 18).build(SkinnedButton.named("license_add"));
            addBtn.active = !owned;
            addRenderableWidget(addBtn);

            Button revokeBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_revoke_license"),
                            btn -> revokeSelectedLicense())
                    .pos(navX + 98, navY).size(55, 18).build(SkinnedButton.named("license_revoke"));
            revokeBtn.active = owned;
            addRenderableWidget(revokeBtn);

            int tempY = navY + 22;
            EditBox daysBox = new SkinnedEditBox(this.font, navX, tempY, 40, 18, Component.translatable("rpessentials.gui.field.days")).skin("license_days");
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
                    .pos(navX + 44, tempY).size(60, 18).build(SkinnedButton.named("license_temp"));
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
        notesList.layout(formX - 7, contentY, formW + 11, this.height - FOOTER_LIST_GAP - contentY);
        int rw = notesList.rowWidth();
        int textX = 32;
        List<ButtonRowList.Row> rows = new ArrayList<>();
        if (notes.isEmpty()) {
            rows.add(new ButtonRowList.Row().text("§8" + I18n.get("rpessentials.gui.player_profile.notes.none"), 4, 2));
        }
        for (int ni = 0; ni < notes.size(); ni++) {
            final OpenPlayerProfileGuiPacket.PlayerData.NoteEntry note = notes.get(ni);
            boolean isEd = stateEditingNoteId == note.id();
            int color = isEd ? 0x33FFFF00 : (ni % 2 == 0 ? 0x22FFFFFF : 0x14FFFFFF);
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal("§f" + note.text()), Math.max(40, rw - textX - 4));
            String by = I18n.get("rpessentials.gui.player_profile.notes.by");
            String meta = "§e#" + note.id() + " §8[" + note.timestamp() + "] §7" + by + " §f" + note.authorName();
            // The header ("edited by ..." included) wraps instead of going out of the note.
            List<FormattedCharSequence> metaLines = this.font.split(
                    net.rp.rpessentials.util.ColorHelper.parseSection(meta), Math.max(40, rw - textX - 4));
            if (metaLines.isEmpty()) metaLines = List.of(FormattedCharSequence.EMPTY);
            int size = metaLines.size() + Math.max(1, wrapped.size());
            ButtonRowList.Row first = new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "notes", color, 0, size).text(metaLines.get(0), textX, 2);
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
                    .build(SkinnedButton.named("note_delete")), 1, 0);
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
                    .size(14, 12).build(SkinnedButton.named("note_edit")), 16, 0);
            rows.add(first);
            int m = metaLines.size();
            for (int mi = 1; mi < m; mi++) {
                rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "notes", color, mi, size).text(metaLines.get(mi), textX, 1));
            }
            for (int li = 0; li < wrapped.size(); li++) {
                rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "notes", color, m + li, size).text(wrapped.get(li), textX, 1));
            }
            if (wrapped.isEmpty()) rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "notes", color, m, size));
        }
        notesList.setRows(rows);
        addRenderableWidget(notesList);

        // Footer: on the same line and with the same height as the Close button.
        int addBoxY = footerY();
        int buttonsW = 44 + UiLayout.GAP + (stateEditingNoteId != -1 ? 46 + UiLayout.GAP : 0);
        int addBoxW = Math.max(60, closeButtonX() - UiLayout.GAP - buttonsW - formX);

        noteBox = new SkinnedEditBox(this.font, formX, addBoxY, addBoxW, 20,
                Component.translatable("rpessentials.gui.field.note")).skin("note");
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
                .pos(formX + addBoxW + UiLayout.GAP, addBoxY).size(44, 20).build(SkinnedButton.named("note_add")));

        if (stateEditingNoteId != -1) {
            addRenderableWidget(Button.builder(Component.literal(I18n.get("rpessentials.gui.player_profile.notes.btn_cancel")),
                            btn -> { stateEditingNoteId = -1; stateNoteInput = ""; rebuild(); })
                    .pos(formX + addBoxW + UiLayout.GAP + 44 + UiLayout.GAP, addBoxY).size(46, 20).build(SkinnedButton.named("note_cancel")));
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
        historyRequested.clear(); // the action is written in the history: asked again when the tab is opened
        if (action == ReportActionPacket.Action.CLOSE) stateReportNote = "";
        pendingDeleteReportId = -1;
    }

    private Button reportButton(String label, String tooltipKey, int width, Runnable onPress) {
        return Button.builder(Component.literal(label), btn -> onPress.run())
                .size(width, 12)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                // Own texture per action: button_report_claim, button_report_close...
                .build(SkinnedButton.named("report_" + tooltipKey.substring(tooltipKey.lastIndexOf('.') + 1).replace("_tooltip", "")));
    }

    private void buildReportsTab(int formX, int formW, int contentY) {
        if (players.isEmpty()) return;
        java.util.UUID uuid = selectedData().uuid();
        String k = "rpessentials.gui.player_profile.reports.";

        if (reportsList == null) reportsList = new ButtonRowList(Minecraft.getInstance(), 12);
        reportsList.layout(formX - 7, contentY, formW + 11, this.height - FOOTER_LIST_GAP - contentY);
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
                ButtonRowList.Row first = new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "reports", color, 0, size);
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
                    rows.add(new ButtonRowList.Row().block(GuiSkin.NOTE_ROW, "reports", color, li + 1, size).text(lines.get(li), 4, 1));
                }
            }
        }
        reportsList.setRows(rows);
        addRenderableWidget(reportsList);

        // Optional closing note, used by the next close (check mark) click.
        int boxY = footerY();
        EditBox noteBox = new SkinnedEditBox(this.font, formX, boxY,
                Math.max(60, closeButtonX() - UiLayout.GAP - formX), 20,
                Component.translatable(k + "note_hint")).skin("report_note");
        noteBox.setHint(Component.literal("§7" + I18n.get(k + "note_hint")));
        noteBox.setMaxLength(ReportActionPacket.MAX_NOTE);
        noteBox.setValue(stateReportNote);
        noteBox.setResponder(val -> stateReportNote = val);
        addRenderableWidget(noteBox);
    }

    private void rebuild() { if (this.minecraft == null) return; /* not shown yet: init() will run when it opens */ clearWidgets(); init(); }


    private void buildManageTab(int formX, int formW, int y) {
        if (manageAction != null) { buildManagePopup(formX, formW, y); return; }

        boolean online = selectedData().isOnline();
        boolean self = selectedIsSelf();
        int gap = UiLayout.GAP;
        if (manageList == null) manageList = new ButtonRowList(Minecraft.getInstance(), MANAGE_ROW_H);
        manageList.layout(formX - 7, y, formW + 11, this.height - FOOTER_LIST_GAP - y);
        int rowW = manageList.rowWidth();
        List<ButtonRowList.Row> rows = new ArrayList<>();

        for (int s = 0; s < MANAGE_ACTIONS.length; s++) {
            String sectionColor = MANAGE_SECTION_KEYS[s].equals("reset") ? "§c" : "§e";
            ButtonRowList.Row row = new ButtonRowList.Row()
                    .text(sectionColor + I18n.get("rpessentials.gui.player_profile.manage.section." + MANAGE_SECTION_KEYS[s]), 0, 0);
            // Profession system turned off: no "clear professions" action.
            ManageService.Action[] acts = java.util.Arrays.stream(MANAGE_ACTIONS[s])
                    .filter(x -> x != ManageService.Action.CLEAR_PROFESSION
                            || net.rp.rpessentials.client.ClientFeatures.professionsEnabled())
                    .toArray(ManageService.Action[]::new);
            int n = acts.length;
            for (int c = 0; c < n; c++) {
                final ManageService.Action a = acts[c];
                boolean reset = a == ManageService.Action.RESET_ALL;
                String label = I18n.get("rpessentials.gui.player_profile.manage.action." + a.name().toLowerCase());
                Button b = Button.builder(Component.literal(reset ? "§c§l" + label : label),
                                btn -> {
                                    manageAction = a;
                                    manageText = "";
                                    manageNumber = defaultManageNumber(a);
                                    rebuild();
                                })
                        // One fixed-size texture per row width: button_manage_action_<buttons in the row>.png (1 to 4).
                        .size(UiLayout.cellW(rowW, n, gap), 16).build(SkinnedButton.named("manage_action_" + n));
                if (!net.rp.rpessentials.client.ClientPerms.has(net.rp.rpessentials.permission.RpPerm.manage(a))) {
                    b.active = false;
                    b.setTooltip(Tooltip.create(Component.translatable(
                            "rpessentials.gui.player_profile.manage.no_permission_tooltip",
                            net.rp.rpessentials.permission.RpPerm.manage(a).node())));
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

    /** Red warning of the full reset, wrapped to the width of the right panel. */
    private List<net.minecraft.util.FormattedCharSequence> resetWarning(int formX) {
        int w = this.width - formX - MARGIN * 2;
        return this.font.split(Component.literal(I18n.get("rpessentials.gui.player_profile.manage.reset_all_warning")), w);
    }

    private void buildManagePopup(int formX, int formW, int y) {
        ManageService.Action a = manageAction;
        int boxW = Math.min(formW - 4, 260);
        boolean resetAll = a == ManageService.Action.RESET_ALL;
        // The red warning of the full reset wraps: what follows goes down by its extra lines.
        int extra = resetAll ? Math.max(0, resetWarning(formX).size() - 2) * this.font.lineHeight : 0;
        int cy = y + 34 + extra;
        int btnY = y + 90 + extra;
        int confirmW = resetAll ? 130 : 100;

        Button confirmBtn = Button.builder(
                        Component.translatable(resetAll
                                ? "rpessentials.gui.player_profile.manage.btn_confirm_reset"
                                : "rpessentials.gui.player_profile.manage.btn_confirm"),
                        btn -> sendManage())
                .pos(formX, btnY).size(confirmW, 20).build(SkinnedButton.named("manage_confirm"));
        confirmBtn.active = canConfirmManage();

        if (a == ManageService.Action.OP || a == ManageService.Action.VANISH) {
            int own = ownLevel();
            boolean self = selectedIsSelf();
            int from = a == ManageService.Action.OP ? 1 : 0;
            for (int lvl = from; lvl <= 4; lvl++) {
                final int l = lvl;
                Button lb = Button.builder(Component.literal(manageNumber == l ? "§e§l" + l : "§7" + l),
                                btn -> { manageNumber = l; rebuild(); })
                        .pos(formX + (lvl - from) * 24, cy).size(22, 18).build(SkinnedButton.named("manage_level"));
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
            EditBox days = new SkinnedEditBox(this.font, formX, cy, 80, 18, Component.translatable("rpessentials.gui.field.days")).skin("manage_days");
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
            EditBox reason = new SkinnedEditBox(this.font, formX, cy, boxW, 18, Component.translatable("rpessentials.gui.field.reason")).skin("manage_reason");
            reason.setHint(Component.translatable("rpessentials.gui.player_profile.manage.reason_hint"));
            reason.setMaxLength(200);
            reason.setValue(manageText);
            reason.setResponder(val -> manageText = val);
            addRenderableWidget(reason);
        }

        if (resetAll) {
            EditBox nameBox = new SkinnedEditBox(this.font, formX, cy + 14, boxW, 18, Component.translatable("rpessentials.gui.field.name")).skin("manage_name");
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
                        .pos(formX + (i % cols) * (80 + UiLayout.GAP), cy + (i / cols) * 20).size(80, 16).build(SkinnedButton.named("manage_choice")));
            }
        }

        addRenderableWidget(confirmBtn);
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.player_profile.manage.btn_cancel"),
                        btn -> { manageAction = null; manageText = ""; rebuild(); })
                .pos(formX + confirmW + 4, btnY).size(80, 20).build(SkinnedButton.named("manage_cancel")));
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
        g.drawString(this.font, "§c§l" + label, formX, y, 0xFFFFFF, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.manage.confirm_generic", label, sel.mcName()),
                formX, y + 12, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        if (manageAction == ManageService.Action.RESET_ALL) {
            int ly = y + 24;
            for (var line : resetWarning(formX)) {
                g.drawString(this.font, line, formX, ly, 0xFF5555, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
                ly += this.font.lineHeight;
            }
        }
        if (manageAction == ManageService.Action.TP_PLAYER_PLATFORM) {
            g.drawString(this.font, I18n.get(availablePlatforms.isEmpty()
                            ? "rpessentials.gui.player_profile.manage.no_platform"
                            : "rpessentials.gui.player_profile.manage.platform_label"),
                    formX, y + 24, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        }
        if (manageAction == ManageService.Action.OP) {
            g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.manage.level_label"),
                    formX, y + 24, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        }
        if (manageAction == ManageService.Action.VANISH) {
            g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.manage.vanish_level_label"),
                    formX, y + 24, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
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
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (activeTab == 0) SaveUi.refresh(applyButton, I18n.get("rpessentials.gui.player_profile.btn_apply"), profileDirty());
        if (confirmClose && !profileDirty()) { confirmClose = false; rebuild(); }
        if (confirmPlayer >= 0 && !profileDirty()) { confirmPlayer = -1; rebuild(); }
        GuiSkin.background(g, this.width, this.height, 0x99000000);
        int formX = LIST_W + MARGIN * 3;

        List<Integer> visible = getVisibleIndices();

        // Panneau liste
        GuiSkin.panel(g, "players", MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.players_header", visible.size()),
                MARGIN + 6, PANEL_TOP + 10, tabLineColor(), net.rp.rpessentials.client.skin.GuiSkin.textShadow());

        if (!players.isEmpty() && visible.isEmpty()) {
            int ly = PANEL_TOP + 26;
            for (var line : this.font.split(Component.literal(I18n.get("rpessentials.gui.player_profile.filter_empty")), LIST_W - 12)) {
                g.drawString(this.font, line, MARGIN + 6, ly, 0x666666, GuiSkin.textShadow());
                ly += this.font.lineHeight;
            }
        }

        // Panneau formulaire
        GuiSkin.panel(g, "content", LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        GuiSkin.accent(g, LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, tabLineColor());

        if (players.isEmpty()) {
            g.drawCenteredString(this.font, "§7" + I18n.get("rpessentials.gui.player_profile.no_players"),
                    (LIST_W + MARGIN * 2 + this.width) / 2, this.height / 2, 0x888888);
            renderScreen(g, mouseX, mouseY, delta);
            return;
        }

        OpenPlayerProfileGuiPacket.PlayerData sel = selectedData();
        String statusDot = sel.isOnline() ? "§a● " : "§7o ";
        g.drawCenteredString(this.font, statusDot + "§e" + sel.mcName(),
                (LIST_W + MARGIN * 2 + this.width) / 2, PANEL_TOP + 9, 0xFFFFFF);

        int contentY  = PANEL_TOP + 36;
        int formRight = this.width - MARGIN;

        GuiSkin.beginTab();
        try {
        switch (activeTab) {
            case 0 -> renderProfileTab(g, formX, formRight - formX - MARGIN, contentY, sel);
            case 1 -> renderStatsTab(g, formX, formRight, contentY, sel);
            case 2 -> renderNotesTab(g, formX, formRight, contentY, sel);
            case 3 -> { renderManageTab(g, formX, contentY, sel); renderFooterSeparator(g, formX, formRight); }
            case 4 -> renderFooterSeparator(g, formX, formRight); // History: list widget + separator above Close
            case 5 -> renderFooterSeparator(g, formX, formRight); // Reports: list widget + footer field
        }
        } finally {
            GuiSkin.endTab();
        }

        renderScreen(g, mouseX, mouseY, delta);
        
    }

    private void renderProfileTab(GuiGraphics g, int formX, int formW, int y,
                                  OpenPlayerProfileGuiPacket.PlayerData sel) {
        String nickLabel = I18n.get("rpessentials.gui.player_profile.nick_label_draw");
        g.drawString(this.font, nickLabel, formX, y + 6, 0x888888, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        ColorPalette.drawPreview(g, this.font, nickLabel, stateNick, stateNickColorIndex, formX, y + 6);
        y += 44;

        ColorPalette.Dims pal = ColorPalette.dims(this.width - formX - MARGIN * 2, this.height);
        ColorPalette.drawSelection(g, pal, formX, y, stateNickColorIndex);
        y += pal.height() + 8;

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.role_label_draw"),
                formX, y, 0x888888, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
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
                    formX, navY + 5, 0xAAAAAA, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        }

        List<String> lics = sel.currentLicenses();
        if (!lics.isEmpty() && net.rp.rpessentials.client.ClientFeatures.professionsEnabled()) {
            int textY = this.height - 50;
            g.drawString(this.font,
                    I18n.get("rpessentials.gui.player_profile.owned") + String.join("§8, §7", lics),
                    formX, textY, 0x777777, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
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
        if (net.rp.rpessentials.client.ClientFeatures.professionsEnabled()) {
            lines.add("§7" + I18n.get("rpessentials.gui.player_profile.stats.licenses") + (lics.isEmpty()
                    ? "§8" + I18n.get("rpessentials.gui.player_profile.stats.licenses_none")
                    : "§f" + String.join("§7, §f", lics)));
        }
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
        historyList.layout(formX, contentY, formW, this.height - FOOTER_LIST_GAP - contentY);
        java.util.UUID id = selectedData().uuid();
        historyList.setLines(id, historyLines(id));
        addRenderableWidget(historyList);
    }


    private void renderStatsTab(GuiGraphics g, int formX, int formRight, int y,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        // The lines are a list widget (buildStatsList); only the title and the footer are drawn here.
        g.drawString(this.font, "§l" + I18n.get("rpessentials.gui.player_profile.stats.header"),
                formX, y, tabLineColor(), net.rp.rpessentials.client.skin.GuiSkin.textShadow());
        int footerY = this.height - 52;
        GuiSkin.separator(g, "stats_footer", formX, footerY, formRight - MARGIN, footerY + 1, 0xFF444444);
        g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.stats.footer"),
                formX, footerY + 6, 0x555555, net.rp.rpessentials.client.skin.GuiSkin.textShadow());
    }

    private void renderNotesTab(GuiGraphics g, int formX, int formRight, int contentY,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        // The notes are a list widget and the field is in the footer (buildNotesTab): only the separator is drawn here.
        renderFooterSeparator(g, formX, formRight);
    }

    @Override
    //#if forge
    //? public void renderBackground(GuiGraphics g) {}
    //#else
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}
    //#endif

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
        ColorPalette.Split nickSplit = ColorPalette.split(p.currentNick());
        stateNickColorIndex = nickSplit.index();
        stateNick = nickSplit.text().replace('§', '&');
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
                : ColorPalette.apply(stateNickColorIndex, stateNick.trim());

        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), finalNick, stateRole.trim(), "", false, false, 0));

        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), finalNick, stateRole.trim(),
                target.currentLicenses(), target.activeWarnCount(), target.isMuted(),
                target.muteExpiry(), target.playtimeMs(), target.sessionMs(),
                target.noteCount(), target.isOnline(), target.notes(), target.isPrepared()));
        rebuild();
    }

    /**
     * Character sheet "use as nickname": sends only the nickname (an empty role means "unchanged",
     * the same checks as /nick apply on the server) and updates the profile form.
     */
    private void setNicknameFromSheet(java.util.UUID uuid, String nick) {
        int index = -1;
        for (int i = 0; i < players.size(); i++) if (players.get(i).uuid().equals(uuid)) { index = i; break; }
        if (index < 0 || nick.isBlank()) return;
        OpenPlayerProfileGuiPacket.PlayerData p = players.get(index);
        String finalNick = nick.trim().replace('&', '§');
        PacketDistributor.sendToServer(new SetPlayerProfilePacket(uuid, nick.trim(), "", "", false, false, 0));
        players.set(index, new OpenPlayerProfileGuiPacket.PlayerData(
                p.uuid(), p.mcName(), finalNick, p.currentRole(),
                p.currentLicenses(), p.activeWarnCount(), p.isMuted(),
                p.muteExpiry(), p.playtimeMs(), p.sessionMs(),
                p.noteCount(), p.isOnline(), p.notes(), p.isPrepared()));
        if (index == stateSelectedPlayer) {
            ColorPalette.Split nickSplit = ColorPalette.split(finalNick);
            stateNickColorIndex = nickSplit.index();
            stateNick = nickSplit.text().replace('§', '&');
        }
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

    /** Adds a note, or saves the one being edited. The list changes when the server answers (setNotes). */
    private void addNote() {
        if (players.isEmpty() || stateNoteInput.trim().isEmpty()) return;
        PacketDistributor.sendToServer(new PlayerNoteActionPacket(selectedData().uuid(), false,
                Math.max(0, stateEditingNoteId), stateNoteInput.trim()));
        stateEditingNoteId = -1;
        stateNoteInput = "";
        rebuild();
        if (noteBox != null) setFocused(noteBox);
    }

    private void deleteNote(int noteId) {
        if (players.isEmpty()) return;
        if (stateEditingNoteId == noteId) {
            stateEditingNoteId = -1;
            stateNoteInput = "";
        }
        PacketDistributor.sendToServer(new PlayerNoteActionPacket(selectedData().uuid(), true, noteId, ""));
        rebuild();
    }

    /** Called when the server sends the notes of a player, after a note was added, edited or deleted. */
    public void setNotes(java.util.UUID uuid, List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> notes) {
        for (int i = 0; i < players.size(); i++) {
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(i);
            if (!p.uuid().equals(uuid)) continue;
            List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> old = p.notes();
            // A new note takes the first free number: go to the end only when it is really the last one.
            boolean newLast = !notes.isEmpty() && notes.size() > old.size()
                    && (old.isEmpty() || notes.get(notes.size() - 1).id() > old.get(old.size() - 1).id());
            players.set(i, new OpenPlayerProfileGuiPacket.PlayerData(
                    p.uuid(), p.mcName(), p.currentNick(), p.currentRole(), p.currentLicenses(), p.activeWarnCount(),
                    p.isMuted(), p.muteExpiry(), p.playtimeMs(), p.sessionMs(), notes.size(), p.isOnline(),
                    notes, p.isPrepared()));
            historyRequested.remove(uuid); // the change is written in the history: asked again when the tab is opened
            if (i == stateSelectedPlayer && newLast && notesList != null) notesList.scrollToEndOnce();
            break;
        }
        if (activeTab != 1 && activeTab != 2) return; // only the Stats and Notes tabs show them
        boolean typing = noteBox != null && getFocused() == noteBox;
        rebuild();
        if (typing && noteBox != null && children().contains(noteBox)) setFocused(noteBox);
    }

    @Override public boolean isPauseScreen() { return false; }
}