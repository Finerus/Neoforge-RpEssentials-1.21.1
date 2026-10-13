package net.rp.rpessentials.client.gui;

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
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.moderation.ManageService;
import net.rp.rpessentials.network.ManagePlayerPacket;
import net.rp.rpessentials.moderation.PlaytimeManager;
import net.rp.rpessentials.network.OpenPlayerProfileGuiPacket;
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
    private int playerListScroll    = 0;
    private int activeTab           = 0; // 0=Profil, 1=Stats, 2=Notes
    private int stateNotesScroll    = 0;
    private int stateTempDays = 7;

    private String stateNick = "";
    private String stateRole = "";
    private String stateNoteInput = "";
    private String stateAddPlayerName = "";
    private int stateEditingNoteId = -1;
    private int tempNoteIdCounter  = -1;
    private static int filterMode = 0;
    private int sortButtonX, sortButtonY;
    private static final String[] FILTER_KEYS = { "online", "offline", "prepared", "all" };
    private String pendingDeletePlayerUuid = null;
    private boolean pendingNickReset = false;
    private int pendingDeleteNoteId = -1;

    private static final char[]   COLOR_CHARS = { 0, 'f','e','6','c','a','b','9','d','7','8','0','1','2','3','4','5' };
    private static final String[] COLOR_KEYS  = {
            "none","white","yellow","gold","red","green","cyan","blue","pink","gray","dark_gray",
            "black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple"
    };

    private static final int LIST_W       = 130;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;
    private static final int LIST_VISIBLE = 10;
    private static final int NOTE_ROW_H   = 40;
    private static final int MANAGE_ROW_H = 34;
    private int manageScroll = 0;

    private static final String[] TAB_KEYS = { "profile", "stats", "notes", "manage" };
    private static final String[] TAB_COLORS = { "§e", "§a", "§b", "§c" };

    private static final String[] MANAGE_SECTION_KEYS = {
            "access", "discipline", "teleport", "send", "permissions", "vanish",
            "inventory", "identity", "absence", "reset" };
    private static final ManageService.Action[][] MANAGE_ACTIONS = {
            { ManageService.Action.WHITELIST_ADD, ManageService.Action.WHITELIST_REMOVE },
            { ManageService.Action.KICK, ManageService.Action.BAN, ManageService.Action.PARDON },
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
                               List<String> availablePlatforms) {
        super(Component.translatable("rpessentials.gui.player_profile.title"));
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
        int formW = this.width - formX - MARGIN;

        // ── Bouton de tri : cycle En ligne -> Hors ligne -> Prévu -> Tous ───
        sortButtonX = MARGIN + LIST_W - 20;
        sortButtonY = PANEL_TOP + 2 ;
        addRenderableWidget(Button.builder(Component.literal("§f⇅"),
                        btn -> {
                            filterMode = (filterMode + 1) % FILTER_KEYS.length;
                            playerListScroll = 0;
                            rebuild();
                        })
                .pos(sortButtonX, sortButtonY).size(18, 12)
                .tooltip(Tooltip.create(Component.translatable(
                        "rpessentials.gui.player_profile.filter." + FILTER_KEYS[filterMode])))
                .build());

        addRenderableWidget(Button.builder(Component.literal("§7↻"),
                        btn -> PacketDistributor.sendToServer(new net.rp.rpessentials.network.RequestOpenGuiPacket(
                                net.rp.rpessentials.network.RequestOpenGuiPacket.GuiType.PLAYER_PROFILE)))
                .pos(sortButtonX - 20, sortButtonY).size(18, 12)
                .tooltip(Tooltip.create(Component.translatable("rpessentials.gui.refresh_tooltip")))
                .build());

        // ── Liste joueurs (gauche) ─────────────────────────────────────────
        List<Integer> visible = getVisibleIndices();

        boolean canScrollUp   = playerListScroll > 0;
        boolean canScrollDown = playerListScroll + LIST_VISIBLE < visible.size();

        addRenderableWidget(Button.builder(Component.literal(canScrollUp ? "▲" : "§8▲"),
                        btn -> { if (canScrollUp) { playerListScroll--; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14).size(LIST_W, 13).build());

        int listStart = playerListScroll;
        int listEnd   = Math.min(visible.size(), listStart + LIST_VISIBLE);
        int listOffY  = 15;

        for (int i = listStart; i < listEnd; i++) {
            final int idx = visible.get(i);
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(idx);

            String statusSymbol = p.isPrepared() ? "§b# " : (p.isOnline() ? "§a● " : "§7○ ");
            String label = (idx == stateSelectedPlayer ? "§e> " : "  ") + statusSymbol + "§f" + p.mcName();
            if (!p.currentNick().isEmpty()) label += " §7(" + stripColor(p.currentNick()) + ")";
            if (p.activeWarnCount() > 0)    label += " §c[" + p.activeWarnCount() + "W]";
            if (p.isMuted())                label += " §6[M]";

            int rowWidth = p.isPrepared() ? LIST_W - 18 : LIST_W;
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { loadPlayerState(idx); rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffY)
                    .size(rowWidth, ROW_H - 2).build());

            if (p.isPrepared()) {
                String uuidStr = p.uuid().toString();
                if (uuidStr.equals(pendingDeletePlayerUuid)) {
                    addRenderableWidget(Button.builder(Component.literal("§c!"),
                                    btn -> PacketDistributor.sendToServer(
                                            new net.rp.rpessentials.network.DeletePendingProfilePacket(p.uuid())))
                            .pos(MARGIN + LIST_W - 16, PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffY)
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "rpessentials.gui.player_profile.confirm_delete_prepared")))
                            .build());
                } else {
                    addRenderableWidget(Button.builder(Component.literal("§c✕"),
                                    btn -> { pendingDeletePlayerUuid = uuidStr; rebuild(); })
                            .pos(MARGIN + LIST_W - 16, PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffY)
                            .size(16, ROW_H - 2)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "rpessentials.gui.player_profile.delete_prepared_tooltip")))
                            .build());
                }
            }
        }

        addRenderableWidget(Button.builder(Component.literal(canScrollDown
                                ? "▼ " + I18n.get("rpessentials.gui.btn_more", Math.max(0, visible.size() - listEnd))
                                : "§8▼"),
                        btn -> { if (canScrollDown) { playerListScroll++; rebuild(); } })
                .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + listOffY)
                .size(LIST_W, 13).build());

        // ── Ajout d'un joueur jamais connecté ───────────────────────────────
        EditBox addPlayerBox = new EditBox(this.font, MARGIN, this.height - 30, LIST_W - 40, 18,
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
                .pos(MARGIN + LIST_W - 38, this.height - 30).size(38, 18)
                .tooltip(Tooltip.create(
                        Component.translatable("rpessentials.gui.player_profile.add_tooltip")))
                .build());

        if (players.isEmpty()) return;

        // ── Onglets ────────────────────────────────────────────────────────
        int tabW = (formW - 4) / TAB_KEYS.length;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            final int ti = i;
            String label = i == activeTab
                    ? TAB_COLORS[ti] + "§l" + I18n.get("rpessentials.gui.player_profile.tab." + TAB_KEYS[i])
                    : "§7" + I18n.get("rpessentials.gui.player_profile.tab." + TAB_KEYS[i]);
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { activeTab = ti; pendingDeleteNoteId = -1; rebuild(); })
                    .pos(formX - 2 + i * (tabW + 2), PANEL_TOP + 14)
                    .size(tabW, 16).build());
        }

        int contentY = PANEL_TOP + 36;

        switch (activeTab) {
            case 0 -> buildProfileTab(formX, formW, contentY);
            case 1 -> {} // Stats : render only
            case 2 -> buildNotesTab(formX, formW, contentY);
            case 3 -> buildManageTab(formX, formW, contentY);
        }

        // ── Bouton Appliquer (onglet profil seulement) ─────────────────────
        if (activeTab == 0) {
            addRenderableWidget(Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_apply"),
                            btn -> applyProfile())
                    .pos(formX, this.height - 35).size(150, 20).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.player_profile.btn_close"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 65, this.height - 35).size(60, 20).build());
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
        EditBox nickBox = new EditBox(this.font, formX, nickBoxY, nickBoxW, 18,
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
                .build());
        y += 44;

        // --- Palette de couleurs ---
        int colBtnW = 56, colBtnH = 15;
        int colCols = Math.max(1, Math.min(5, (this.width - LIST_W - MARGIN * 4) / (colBtnW + 2)));
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            final int ci = i;
            String btnLabel = ci == 0
                    ? (ci == stateNickColorIndex ? "§l" : "") + "§7" + I18n.get("rpessentials.gui.color." + COLOR_KEYS[ci])
                    : (ci == stateNickColorIndex ? "§l" : "") + "§" + COLOR_CHARS[ci] + I18n.get("rpessentials.gui.color." + COLOR_KEYS[ci]);
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                            btn -> { stateNickColorIndex = ci; rebuild(); })
                    .pos(formX + (i % colCols) * (colBtnW + 2), y + (i / colCols) * (colBtnH + 2))
                    .size(colBtnW, colBtnH).build());
        }
        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += colRows * (colBtnH + 2) + 8;

        // --- Role ---
        y += 12;

        if (!availableRoles.isEmpty()) {
            int maxBtnW = 80;
            int minBtnW = 40;
            int gap     = 2;
            int availW  = formW - 4;
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
                        .size(btnW, 14).build();
                roleBtn.active = Minecraft.getInstance().player != null
                        && RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
                addRenderableWidget(roleBtn);
            }
            y += rows * 16 + 4;
        }

        // --- Licences ---
        if (!availableProfessionIds.isEmpty()) {
            int longestW = availableProfessionIds.stream()
                    .mapToInt(id -> this.font.width("License: " + id + " (" + availableProfessionIds.size() + "/" + availableProfessionIds.size() + ")"))
                    .max().orElse(0);
            int fixedGap = 90;
            int navX = formX + Math.max(longestW + 12, fixedGap);

            boolean fitsInline = navX + 140 < formX + formW;
            int navY = fitsInline ? y : y + 12;
            if (!fitsInline) navX = formX;

            addRenderableWidget(Button.builder(Component.literal("§7<"),
                            btn -> { stateSelectedProf = Math.floorMod(stateSelectedProf - 1,
                                    availableProfessionIds.size()); rebuild(); })
                    .pos(navX, navY).size(18, 18).build());

            addRenderableWidget(Button.builder(Component.literal("§7>"),
                            btn -> { stateSelectedProf = (stateSelectedProf + 1)
                                    % availableProfessionIds.size(); rebuild(); })
                    .pos(navX + 20, navY).size(18, 18).build());

            String profId = selectedProfId();
            boolean owned = selectedPlayerOwns(profId);
            Button addBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_add_license"),
                            btn -> grantSelectedLicense())
                    .pos(navX + 40, navY).size(50, 18).build();
            addBtn.active = !owned;
            addRenderableWidget(addBtn);

            Button revokeBtn = Button.builder(
                            Component.translatable("rpessentials.gui.player_profile.btn_revoke_license"),
                            btn -> revokeSelectedLicense())
                    .pos(navX + 93, navY).size(55, 18).build();
            revokeBtn.active = owned;
            addRenderableWidget(revokeBtn);

            int tempY = navY + 22;
            EditBox daysBox = new EditBox(this.font, navX, tempY, 40, 18, Component.literal("Days"));
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
                    .pos(navX + 44, tempY).size(60, 18).build();
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

        int listAreaBottom = this.height - 58;
        int listAreaHeight = listAreaBottom - contentY;
        int maxTextW       = noteTextW();
        int maxScroll      = Math.max(0, notes.size() - 1);
        if (stateNotesScroll > maxScroll) stateNotesScroll = maxScroll;

        int first  = stateNotesScroll;
        int last   = first - 1;
        int totalH = 0;
        for (int i = first; i < notes.size(); i++) {
            int rh = noteRowH(notes.get(i), maxTextW);
            if (totalH + rh > listAreaHeight && last >= first) break;
            totalH += rh;
            last = i;
        }

        if (stateNotesScroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                            btn -> { stateNotesScroll = Math.max(0, stateNotesScroll - 1); rebuild(); })
                    .pos(this.width - MARGIN - 20, contentY).size(16, 12).build());
        }
        if (stateNotesScroll < maxScroll) {
            addRenderableWidget(Button.builder(Component.literal("▼"),
                            btn -> { stateNotesScroll = Math.min(maxScroll, stateNotesScroll + 1); rebuild(); })
                    .pos(this.width - MARGIN - 20, listAreaBottom - 12).size(16, 12).build());
        }

        int rowY = contentY;
        for (int i = first; i <= last; i++) {
            final OpenPlayerProfileGuiPacket.PlayerData.NoteEntry note = notes.get(i);
            int rh   = noteRowH(note, maxTextW);
            int btnY = rowY + rh / 2 - 8;

            if (note.id() >= 0) {
                boolean confirmDelete = pendingDeleteNoteId == note.id();
                addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "§c!" : "§c✗"),
                                btn -> {
                                    if (pendingDeleteNoteId == note.id()) {
                                        pendingDeleteNoteId = -1;
                                        deleteNote(note.id());
                                    } else {
                                        pendingDeleteNoteId = note.id();
                                        rebuild();
                                    }
                                })
                        .pos(formX, btnY).size(14, 16)
                        .tooltip(Tooltip.create(Component.translatable(confirmDelete
                                ? "rpessentials.gui.player_profile.notes.confirm_delete"
                                : "rpessentials.gui.player_profile.notes.delete_tooltip")))
                        .build());

                boolean isEditing = stateEditingNoteId == note.id();
                addRenderableWidget(Button.builder(
                                Component.literal(isEditing ? "§e§l✎" : "§e✎"),
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
                        .pos(formX + 16, btnY).size(14, 16).build());
            }
            rowY += rh;
        }

        int addBoxY = this.height - 50;
        int addBoxW = Math.min(formW - 70, 280);

        EditBox noteBox = new EditBox(this.font, formX, addBoxY, addBoxW, 18,
                Component.literal("Note"));
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
                .pos(formX + addBoxW + 4, addBoxY).size(44, 18).build());

        if (stateEditingNoteId != -1) {
            addRenderableWidget(Button.builder(Component.literal(I18n.get("rpessentials.gui.player_profile.notes.btn_cancel")),
                            btn -> { stateEditingNoteId = -1; stateNoteInput = ""; rebuild(); })
                    .pos(formX + addBoxW + 50, addBoxY).size(46, 18).build());
        }
    }

    private void rebuild() { clearWidgets(); init(); }


    private void buildManageTab(int formX, int formW, int y) {
        if (manageAction != null) { buildManagePopup(formX, formW, y); return; }

        boolean sensitive = Minecraft.getInstance().player != null
                && RpEssentialsPermissions.hasSensitiveLevel(Minecraft.getInstance().player);
        boolean online = selectedData().isOnline();
        boolean self = selectedIsSelf();
        int gap = 6;
        int w = (formW - 28 - gap * 2) / 3;
        int visible = manageVisibleSections();
        int maxScroll = MANAGE_ACTIONS.length - visible;
        manageScroll = Math.max(0, Math.min(manageScroll, maxScroll));

        for (int s = manageScroll; s < manageScroll + visible; s++) {
            int rowY = y + (s - manageScroll) * MANAGE_ROW_H;
            for (int c = 0; c < MANAGE_ACTIONS[s].length; c++) {
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
                        .pos(formX + c * (w + gap), rowY + 11)
                        .size(reset ? w * 3 + gap * 2 : w, 16).build();
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
                addRenderableWidget(b);
            }
        }

        if (maxScroll > 0) {
            boolean up = manageScroll > 0;
            boolean down = manageScroll < maxScroll;
            int ax = formX + formW - 20;
            addRenderableWidget(Button.builder(Component.literal(up ? "▲" : "§8▲"),
                            btn -> { if (up) { manageScroll--; rebuild(); } })
                    .pos(ax, y).size(18, 14).build());
            addRenderableWidget(Button.builder(Component.literal(down ? "▼" : "§8▼"),
                            btn -> { if (down) { manageScroll++; rebuild(); } })
                    .pos(ax, y + visible * MANAGE_ROW_H - 18).size(18, 14).build());
        }
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
                .pos(formX, btnY).size(confirmW, 20).build();
        confirmBtn.active = canConfirmManage();

        if (a == ManageService.Action.OP || a == ManageService.Action.VANISH) {
            int own = ownLevel();
            boolean self = selectedIsSelf();
            int from = a == ManageService.Action.OP ? 1 : 0;
            for (int lvl = from; lvl <= 4; lvl++) {
                final int l = lvl;
                Button lb = Button.builder(Component.literal(manageNumber == l ? "§e§l" + l : "§7" + l),
                                btn -> { manageNumber = l; rebuild(); })
                        .pos(formX + (lvl - from) * 24, cy).size(22, 18).build();
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

        if (a == ManageService.Action.ABSENCE_ADD) {
            EditBox days = new EditBox(this.font, formX, cy, 80, 18, Component.literal("Days"));
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

        if (a == ManageService.Action.KICK || a == ManageService.Action.BAN || a == ManageService.Action.ABSENCE_ADD) {
            EditBox reason = new EditBox(this.font, formX, cy, boxW, 18, Component.literal("Reason"));
            reason.setHint(Component.translatable("rpessentials.gui.player_profile.manage.reason_hint"));
            reason.setMaxLength(200);
            reason.setValue(manageText);
            reason.setResponder(val -> manageText = val);
            addRenderableWidget(reason);
        }

        if (resetAll) {
            EditBox nameBox = new EditBox(this.font, formX, cy + 14, boxW, 18, Component.literal("Name"));
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
            int shown = Math.min(availablePlatforms.size(), 6);
            for (int i = 0; i < shown; i++) {
                final String id = availablePlatforms.get(i);
                boolean sel = id.equals(manageText);
                addRenderableWidget(Button.builder(Component.literal(sel ? "§e§l" + id : "§7" + id),
                                btn -> { manageText = id; rebuild(); })
                        .pos(formX + (i % 3) * 84, cy + (i / 3) * 20).size(80, 16).build());
            }
        }

        addRenderableWidget(confirmBtn);
        addRenderableWidget(Button.builder(
                        Component.translatable("rpessentials.gui.player_profile.manage.btn_cancel"),
                        btn -> { manageAction = null; manageText = ""; rebuild(); })
                .pos(formX + confirmW + 4, btnY).size(80, 20).build());
    }

    private void sendManage() {
        if (players.isEmpty() || manageAction == null) return;
        if (manageAction == ManageService.Action.ABSENCE_ADD && (manageNumber < 1 || manageNumber > 365)) return;
        String text = manageAction == ManageService.Action.RESET_ALL ? "" : manageText.trim();
        PacketDistributor.sendToServer(new ManagePlayerPacket(
                selectedData().uuid(), manageAction, text, manageNumber));
        manageAction = null;
        manageText = "";
        rebuild();
    }

    private void renderManageTab(GuiGraphics g, int formX, int y, OpenPlayerProfileGuiPacket.PlayerData sel) {
        if (manageAction == null) {
            int visible = manageVisibleSections();
            for (int s = manageScroll; s < manageScroll + visible && s < MANAGE_SECTION_KEYS.length; s++) {
                String color = MANAGE_SECTION_KEYS[s].equals("reset") ? "§c" : "§e";
                g.drawString(this.font,
                        color + I18n.get("rpessentials.gui.player_profile.manage.section." + MANAGE_SECTION_KEYS[s]),
                        formX, y + (s - manageScroll) * MANAGE_ROW_H, 0xFFFFFF, false);
            }
            return;
        }
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

    private int manageVisibleSections() {
        int avail = this.height - 16 - (PANEL_TOP + 36);
        return Math.max(1, Math.min(MANAGE_ACTIONS.length, avail / MANAGE_ROW_H));
    }

    // =========================================================================
    // RENDU
    // =========================================================================
    private int tabLineColor() {
        return switch (activeTab) {
            case 1 -> 0xFF55FF55; // Stats
            case 2 -> 0xFF55FFFF; // Notes
            case 3 -> 0xFFFF5555; // Manage
            default -> 0xFFFFFF55; // Profil
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
        int formX = LIST_W + MARGIN * 3;

        List<Integer> visible = getVisibleIndices();

        // Panneau liste
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, tabLineColor());

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.players_header", visible.size()),
                MARGIN + 3, PANEL_TOP + 4, tabLineColor(), false);

        if (!players.isEmpty() && visible.isEmpty()) {
            g.drawString(this.font, "§8" + I18n.get("rpessentials.gui.player_profile.filter_empty"),
                    MARGIN + 3, PANEL_TOP + 26, 0x666666, false);
        }

        // Panneau formulaire
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, tabLineColor());

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
            case 0 -> renderProfileTab(g, formX, formRight - formX, contentY, sel);
            case 1 -> renderStatsTab(g, formX, formRight, contentY, sel);
            case 2 -> renderNotesTab(g, formX, formRight, contentY, sel);
            case 3 -> renderManageTab(g, formX, contentY, sel);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderProfileTab(GuiGraphics g, int formX, int formW, int y,
                                  OpenPlayerProfileGuiPacket.PlayerData sel) {
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.nick_label_draw"),
                formX, y + 6, 0x888888, false);

        if (!stateNick.isEmpty()) {
            String preview = stateNickColorIndex == 0
                    ? "-> " + stateNick
                    : "-> §" + COLOR_CHARS[stateNickColorIndex] + stateNick;
            g.drawString(this.font, Component.literal(preview), formX + 110, y + 6, 0xFFFFFF, false);
        }
        y += 44;

        int colBtnW = 56, colBtnH = 15;
        int colCols = Math.max(1, Math.min(5, (this.width - LIST_W - MARGIN * 4) / (colBtnW + 2)));
        int sc = stateNickColorIndex % colCols, sr = stateNickColorIndex / colCols;
        g.fill(formX + sc * (colBtnW + 2) - 1, y + sr * (colBtnH + 2) - 1,
                formX + sc * (colBtnW + 2) + colBtnW + 1, y + sr * (colBtnH + 2) + colBtnH + 1,
                0xFF_FFD700);

        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += colRows * (colBtnH + 2) + 8;

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.role_label_draw"),
                formX, y, 0x888888, false);
        y += 12;

        if (!availableRoles.isEmpty()) {
            int maxBtnW = 80;
            int minBtnW = 40;
            int gap     = 2;
            int availW  = formW - 4;
            int cols    = Math.max(1, availW / (minBtnW + gap));
            int rows    = (int) Math.ceil((double) availableRoles.size() / cols);
            y += rows * 16 + 4;
        }

        if (!availableProfessionIds.isEmpty()) {
            int longestW = availableProfessionIds.stream()
                    .mapToInt(id -> this.font.width("License: " + id + " (" + availableProfessionIds.size() + "/" + availableProfessionIds.size() + ")"))
                    .max().orElse(0);
            int fixedGap = 90;
            int navX = formX + Math.max(longestW + 12, fixedGap);
            boolean fitsInline = navX + 140 < formX + formW;
            int navY = fitsInline ? y : y + 12;

            String profId  = selectedProfId();
            boolean owned  = selectedPlayerOwns(profId);
            String color   = owned ? "§a" : "§7";
            g.drawString(this.font,
                    "§8License: " + color + profId
                            + " §8(" + (stateSelectedProf + 1) + "/" + availableProfessionIds.size() + ")",
                    formX, navY + 5, 0xAAAAAA, false);
        }

        List<String> lics = sel.currentLicenses();
        if (!lics.isEmpty()) {
            int textY = this.height - 50;
            g.drawString(this.font,
                    "§8Owned: §7" + String.join("§8, §7", lics),
                    formX, textY, 0x777777, false);
        }
    }

    private void renderStatsTab(GuiGraphics g, int formX, int formRight, int y,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        int lineH = 14;

        g.drawString(this.font, "§l" + I18n.get("rpessentials.gui.player_profile.stats.header"),
                formX, y, tabLineColor(), false);
        y += lineH + 4;

        String warnColor = sel.activeWarnCount() == 0 ? "§a"
                : sel.activeWarnCount() >= 3 ? "§c" : "§e";
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.stats.warns") + warnColor + sel.activeWarnCount(),
                formX, y, 0xAAAAAA, false);
        y += lineH;

        String muteStr = sel.isMuted()
                ? I18n.get("rpessentials.gui.player_profile.stats.muted_yes")
                + " §8(" + sel.muteExpiry() + ")"
                : I18n.get("rpessentials.gui.player_profile.stats.muted_no");
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.stats.muted") + muteStr,
                formX, y, 0xAAAAAA, false);
        y += lineH;

        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.stats.playtime")
                        + "§f" + PlaytimeManager.format(sel.playtimeMs()),
                formX, y, 0xAAAAAA, false);
        y += lineH;

        if (sel.isOnline() && sel.sessionMs() > 60_000L) {
            g.drawString(this.font,
                    I18n.get("rpessentials.gui.player_profile.stats.session")
                            + "§b" + PlaytimeManager.format(sel.sessionMs()),
                    formX, y, 0xAAAAAA, false);
            y += lineH;
        }

        String notesStr = sel.noteCount() == 0
                ? I18n.get("rpessentials.gui.player_profile.stats.notes_none")
                : "§e" + sel.noteCount() + " note(s)";
        g.drawString(this.font,
                I18n.get("rpessentials.gui.player_profile.stats.notes") + notesStr,
                formX, y, 0xAAAAAA, false);
        y += lineH;

        List<String> lics = sel.currentLicenses();
        String licsText = lics.isEmpty()
                ? "§8" + I18n.get("rpessentials.gui.player_profile.stats.licenses_none")
                : "§f" + String.join("§7, §f", lics);

        List<FormattedCharSequence> wrapped = this.font.split(
                Component.literal(I18n.get("rpessentials.gui.player_profile.stats.licenses") + licsText),
                formRight - MARGIN - formX);
        for (FormattedCharSequence line : wrapped) {
            g.drawString(this.font, line, formX, y, 0xAAAAAA, false);
            y += 10;
        }
        y += 6;

        g.fill(formX, y, formRight - MARGIN, y + 1, 0xFF444444);
        y += 6;
        g.drawString(this.font, I18n.get("rpessentials.gui.player_profile.stats.footer"),
                formX, y, 0x555555, false);
    }

    private void renderNotesTab(GuiGraphics g, int formX, int formRight, int contentY,
                                OpenPlayerProfileGuiPacket.PlayerData sel) {
        List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> notes = sel.notes();

        int listAreaBottom = this.height - 58;
        int listAreaHeight = listAreaBottom - contentY;
        int maxTextW       = noteTextW();
        int textX          = formX + 32;

        int first  = stateNotesScroll;
        int last   = first - 1;
        int totalH = 0;
        for (int i = first; i < notes.size(); i++) {
            int rh = noteRowH(notes.get(i), maxTextW);
            if (totalH + rh > listAreaHeight && last >= first) break;
            totalH += rh;
            last = i;
        }

        if (notes.isEmpty()) {
            g.drawString(this.font,
                    "§8" + I18n.get("rpessentials.gui.player_profile.notes.none"),
                    textX, contentY + 6, 0x555555, false);
        } else {
            int rowY = contentY;
            for (int i = first; i <= last; i++) {
                OpenPlayerProfileGuiPacket.PlayerData.NoteEntry n = notes.get(i);
                int rh       = noteRowH(n, maxTextW);
                boolean isEd = stateEditingNoteId == n.id();
                boolean isPending = n.id() < 0;

                g.fill(formX, rowY, formRight - MARGIN - 24, rowY + rh - 2,
                        isEd ? 0x33FFFF00 : isPending ? 0x1100FFFF : 0x22FFFFFF);

                String meta = isPending
                        ? "§8[" + n.timestamp() + "] §7by §f" + n.authorName()
                        : "§e#" + n.id() + " §8[" + n.timestamp() + "] §7by §f" + n.authorName();
                g.drawString(this.font, meta, textX, rowY + 3, 0xAAAAAA, false);

                List<FormattedCharSequence> wrapped = this.font.split(
                        Component.literal("§f" + n.text()), maxTextW);
                int textLineY = rowY + 14;
                for (int li = 0; li < wrapped.size(); li++) {
                    g.drawString(this.font, wrapped.get(li), textX, textLineY + li * 10, 0xFFFFFF, false);
                }
                rowY += rh;
            }

            if (stateNotesScroll > 0)
                g.drawString(this.font, "§8▲ " + stateNotesScroll + " more",
                        textX, contentY - 1, 0x444444, false);
            int below = notes.size() - 1 - last;
            if (below > 0)
                g.drawString(this.font, "§8▼ " + below + " more",
                        textX, listAreaBottom - 9, 0x444444, false);
        }

        g.fill(formX, this.height - 56, formRight - MARGIN, this.height - 55, 0xFF333333);
        String footerLabel = stateEditingNoteId != -1
                ? String.format(I18n.get("rpessentials.gui.player_profile.notes.footer_edit"), stateEditingNoteId)
                : I18n.get("rpessentials.gui.player_profile.notes.footer_new");
        g.drawString(this.font, footerLabel, formX, this.height - 52, 0x888888, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    // =========================================================================
    // SCROLL MOLETTE
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int formX = LIST_W + MARGIN * 3;

        if (mx < LIST_W + MARGIN * 2) {
            int max = Math.max(0, getVisibleIndices().size() - LIST_VISIBLE);
            playerListScroll = (int) Math.max(0, Math.min(max, playerListScroll - sy));
            rebuild();
            return true;
        }

        if (activeTab == 3 && manageAction == null && mx >= formX) {
            int max = Math.max(0, MANAGE_ACTIONS.length - manageVisibleSections());
            manageScroll = (int) Math.max(0, Math.min(max, manageScroll - sy));
            rebuild();
            return true;
        }

        if (activeTab == 2 && mx >= formX) {
            int maxScroll = Math.max(0, selectedData().notes().size() - 1);
            stateNotesScroll = (int) Math.max(0, Math.min(maxScroll, stateNotesScroll - sy));
            rebuild();
            return true;
        }

        return super.mouseScrolled(mx, my, sx, sy);
    }

    // =========================================================================
    // LOGIQUE
    // =========================================================================
    private void loadPlayerState(int idx) {
        stateSelectedPlayer = idx;
        stateNotesScroll    = 0;
        stateEditingNoteId  = -1;
        pendingDeletePlayerUuid = null;
        pendingNickReset = false;
        pendingDeleteNoteId = -1;
        OpenPlayerProfileGuiPacket.PlayerData p = players.get(idx);
        String rawNick = p.currentNick();
        stateNickColorIndex = 0;
        if (rawNick.startsWith("§") && rawNick.length() > 1) {
            char c = rawNick.charAt(1);
            for (int i = 1; i < COLOR_CHARS.length; i++) {
                if (COLOR_CHARS[i] == c) { stateNickColorIndex = i; rawNick = rawNick.substring(2); break; }
            }
        }
        stateNick  = rawNick;
        stateRole  = p.currentRole();
        stateSelectedProf = 0;
        stateNoteInput    = "";

        manageAction = null;
        manageScroll = 0;
    }

    private void applyProfile() {
        if (players.isEmpty()) return;
        OpenPlayerProfileGuiPacket.PlayerData target = selectedData();
        String finalNick = stateNick.trim().isEmpty()
                ? ""
                : stateNickColorIndex == 0
                ? stateNick.trim()
                : "§" + COLOR_CHARS[stateNickColorIndex] + stateNick.trim();

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
            stateNotesScroll = updated.size() - 1;
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
        stateNotesScroll = Math.max(0, stateNotesScroll - 1);
        rebuild();
    }

    private String stripColor(String s) {
        if (s.startsWith("§") && s.length() > 2) return s.substring(2);
        return s;
    }

    private int noteTextW() {
        int formX = LIST_W + MARGIN * 3;
        // textX = formX + 32 (2 boutons), 24 = marge scroll
        return (this.width - MARGIN) - MARGIN - (formX + 32) - 24;
    }

    private int noteRowH(OpenPlayerProfileGuiPacket.PlayerData.NoteEntry n, int maxTextW) {
        List<FormattedCharSequence> wrapped = this.font.split(
                Component.literal("§f" + n.text()), maxTextW);
        // 14 = meta, 10px/ligne, 6 = padding bas
        return 14 + Math.max(1, wrapped.size()) * 10 + 6;
    }

    @Override public boolean isPauseScreen() { return false; }
}