package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.network.OpenPlayerProfileGuiPacket;
import net.rp.rpessentials.network.SetPlayerProfilePacket;

import java.util.List;

/**
 * GUI de gestion des profils RP — CLIENT UNIQUEMENT
 *
 * Toutes les données (joueurs, professions, rôles) viennent du serveur via packet.
 * Ce screen n'accède à AUCUNE config serveur — il est 100% client-safe.
 */
@OnlyIn(Dist.CLIENT)
public class PlayerProfileScreen extends Screen {

    // =========================================================================
    // ÉTAT — tout ici, jamais lu depuis les widgets
    // =========================================================================

    private final List<OpenPlayerProfileGuiPacket.PlayerData> players;
    private final List<String> availableProfessionIds;
    private final List<String> availableRoles;         // envoyés par le serveur via packet

    private int stateSelectedPlayer = 0;
    private int stateSelectedProf   = 0;
    private int stateNickColorIndex = 0;
    private int playerListScroll    = 0;

    private String stateNick = "";
    private String stateRole = "";

    // ── Couleurs ──────────────────────────────────────────────────────────────
    private static final char[]   COLOR_CHARS  = { 'f','e','6','c','a','b','9','d','7','8' };
    private static final String[] COLOR_LABELS = { "Blanc","Jaune","Or","Rouge","Vert","Cyan","Bleu","Rose","Gris","Gris foncé" };

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int LIST_W       = 130;
    private static final int MARGIN       = 8;
    private static final int PANEL_TOP    = 18;
    private static final int ROW_H        = 20;
    private static final int LIST_VISIBLE = 10;

    // =========================================================================
    // CONSTRUCTEUR
    // =========================================================================

    public PlayerProfileScreen(
            List<OpenPlayerProfileGuiPacket.PlayerData> players,
            List<String> availableProfessionIds,
            List<String> availableRoles) {
        super(Component.literal("§6✦ Gestionnaire de Profils RP"));
        this.players                = players;
        this.availableProfessionIds = availableProfessionIds;
        this.availableRoles         = availableRoles;

        if (!players.isEmpty()) loadPlayerState(0);
    }

    // =========================================================================
    // INIT
    // =========================================================================

    @Override
    protected void init() {
        int formX = LIST_W + MARGIN * 3;
        int formW = this.width - formX - MARGIN;

        // ── Liste des joueurs (colonne gauche) ────────────────────────────────
        if (playerListScroll > 0) {
            addRenderableWidget(Button.builder(Component.literal("▲"),
                            btn -> { playerListScroll--; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 12)
                    .size(LIST_W, 13)
                    .build());
        }

        int listStart = playerListScroll;
        int listEnd   = Math.min(players.size(), listStart + LIST_VISIBLE);
        int listOffsetY = playerListScroll > 0 ? 15 : 0;

        for (int i = listStart; i < listEnd; i++) {
            final int idx = i;
            OpenPlayerProfileGuiPacket.PlayerData p = players.get(i);
            String label = (i == stateSelectedPlayer ? "§e▶ " : "  ") + p.mcName();
            if (!p.currentNick().isEmpty()) label += " §8(" + stripColorCode(p.currentNick()) + ")";
            addRenderableWidget(Button.builder(Component.literal(label),
                            btn -> { loadPlayerState(idx); rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + (i - listStart) * ROW_H + listOffsetY)
                    .size(LIST_W, ROW_H - 2)
                    .build());
        }

        if (listEnd < players.size()) {
            addRenderableWidget(Button.builder(
                            Component.literal("▼ (" + (players.size() - listEnd) + " de plus)"),
                            btn -> { playerListScroll++; rebuild(); })
                    .pos(MARGIN, PANEL_TOP + 14 + LIST_VISIBLE * ROW_H + listOffsetY)
                    .size(LIST_W, 13)
                    .build());
        }

        if (players.isEmpty()) return;

        // ── Formulaire (colonne droite) ───────────────────────────────────────
        int y = PANEL_TOP + 28;

        // ── Champ Nickname ────────────────────────────────────────────────────
        EditBox nickBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.literal("Nickname"));
        nickBox.setHint(Component.literal("§7Nickname RP (vide = inchangé)"));
        nickBox.setMaxLength(64);
        nickBox.setValue(stateNick);
        nickBox.setResponder(val -> stateNick = val);
        addRenderableWidget(nickBox);
        y += 44;

        // ── Boutons couleur du nickname ───────────────────────────────────────
        int colBtnW  = 56;
        int colBtnH  = 15;
        int colCols  = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        for (int i = 0; i < COLOR_CHARS.length; i++) {
            final int ci = i;
            String btnLabel = (i == stateNickColorIndex ? "§l" : "") + "§" + COLOR_CHARS[i] + COLOR_LABELS[i];
            addRenderableWidget(Button.builder(Component.literal(btnLabel),
                            btn -> { stateNickColorIndex = ci; rebuild(); })
                    .pos(formX + (i % colCols) * (colBtnW + 2), y + (i / colCols) * (colBtnH + 2))
                    .size(colBtnW, colBtnH)
                    .build());
        }
        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += colRows * (colBtnH + 2) + 8;

        // ── Champ Rôle ────────────────────────────────────────────────────────
        EditBox roleBox = new EditBox(this.font, formX, y + 16, Math.min(formW - 4, 200), 18,
                Component.literal("Rôle"));
        roleBox.setHint(Component.literal("§7ex: joueur, modo, admin..."));
        roleBox.setMaxLength(32);
        roleBox.setValue(stateRole);
        roleBox.setResponder(val -> stateRole = val);
        addRenderableWidget(roleBox);
        y += 38;

        // ── Boutons raccourcis rôles (autocomplétion) ─────────────────────────
        if (!availableRoles.isEmpty()) {
            int roleBtnW = Math.max(40, Math.min(80, (formW - 4) / availableRoles.size() - 2));
            for (int i = 0; i < availableRoles.size(); i++) {
                final String role = availableRoles.get(i);
                boolean selected  = role.equalsIgnoreCase(stateRole);
                String label      = selected ? "§e§l" + role : "§7" + role;
                addRenderableWidget(Button.builder(Component.literal(label),
                                btn -> { stateRole = role; rebuild(); })
                        .pos(formX + i * (roleBtnW + 2), y)
                        .size(roleBtnW, 14)
                        .build());
            }
            y += 18;
        }
        y += 6;

        // ── Sélecteur de Profession ◀ [nom] ▶ ────────────────────────────────
        if (!availableProfessionIds.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("§7◀"),
                            btn -> {
                                stateSelectedProf = (stateSelectedProf - 1 + availableProfessionIds.size())
                                        % availableProfessionIds.size();
                                rebuild();
                            })
                    .pos(formX, y + 14).size(20, 20).build());

            addRenderableWidget(Button.builder(Component.literal("§7▶"),
                            btn -> {
                                stateSelectedProf = (stateSelectedProf + 1) % availableProfessionIds.size();
                                rebuild();
                            })
                    .pos(formX + 162, y + 14).size(20, 20).build());
        }

        // ── Bouton Appliquer ──────────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                        Component.literal("§a✔ Appliquer le profil"),
                        btn -> applyProfile())
                .pos(formX, this.height - 28)
                .size(150, 20)
                .build());

        // ── Bouton Fermer ─────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("§cFermer"),
                        btn -> onClose())
                .pos(this.width - MARGIN - 60, this.height - 28)
                .size(60, 20)
                .build());
    }

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

        // ── Panneau liste gauche ──────────────────────────────────────────────
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, this.height - 10, 0xBB111111);
        g.fill(MARGIN - 2, PANEL_TOP, MARGIN + LIST_W + 2, PANEL_TOP + 2, 0xFF8B6914);
        g.drawString(this.font, "§6Joueurs (" + players.size() + ")", MARGIN + 3, PANEL_TOP + 4, 0xFFD700, false);

        // ── Panneau formulaire droit ──────────────────────────────────────────
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, this.height - 10, 0xBB111111);
        g.fill(LIST_W + MARGIN * 2, PANEL_TOP, this.width - MARGIN, PANEL_TOP + 2, 0xFF8B6914);
        g.drawCenteredString(this.font, this.title,
                (LIST_W + MARGIN * 2 + this.width) / 2, PANEL_TOP + 5, 0xFFD700);

        if (players.isEmpty()) {
            g.drawCenteredString(this.font, "§7Aucun joueur connecté",
                    (LIST_W + MARGIN * 2 + this.width) / 2, this.height / 2, 0x888888);
            super.render(g, mouseX, mouseY, delta);
            return;
        }

        OpenPlayerProfileGuiPacket.PlayerData sel = players.get(stateSelectedPlayer);
        g.drawCenteredString(this.font, "§e" + sel.mcName(),
                (LIST_W + MARGIN * 2 + this.width) / 2, PANEL_TOP + 15, 0xFFFFFF);

        int y = PANEL_TOP + 28;

        // Label + aperçu nickname coloré
        g.drawString(this.font, "§7Nickname :", formX, y + 6, 0x888888, false);
        if (!stateNick.isEmpty()) {
            g.drawString(this.font,
                    Component.literal("→ §" + COLOR_CHARS[stateNickColorIndex] + stateNick),
                    formX + 100, y + 6, 0xFFFFFF, false);
        }
        y += 44;

        // Surbrillance bouton couleur sélectionné
        int colBtnW = 56;
        int colBtnH = 15;
        int colCols = Math.max(1, Math.min(5, formW / (colBtnW + 2)));
        int sc = stateNickColorIndex % colCols;
        int sr = stateNickColorIndex / colCols;
        g.fill(formX + sc * (colBtnW + 2) - 1, y + sr * (colBtnH + 2) - 1,
                formX + sc * (colBtnW + 2) + colBtnW + 1, y + sr * (colBtnH + 2) + colBtnH + 1,
                0xFF_FFD700);

        int colRows = (COLOR_CHARS.length + colCols - 1) / colCols;
        y += colRows * (colBtnH + 2) + 8;

        g.drawString(this.font, "§7Rôle :", formX, y + 6, 0x888888, false);
        y += 38;
        if (!availableRoles.isEmpty()) y += 18;
        y += 6;

        // Profession sélectionnée
        g.drawString(this.font, "§7Profession :", formX, y + 6, 0x888888, false);
        if (!availableProfessionIds.isEmpty()) {
            String profName = availableProfessionIds.get(stateSelectedProf);
            g.drawCenteredString(this.font, "§b" + profName, formX + 91, y + 18, 0xFFFFFF);
            g.drawString(this.font,
                    "§8(" + (stateSelectedProf + 1) + "/" + availableProfessionIds.size() + ")",
                    formX + 75, y + 28, 0x555555, false);
        } else {
            g.drawString(this.font, "§8Aucune profession configurée", formX + 22, y + 18, 0x666666, false);
        }

        // ── Résumé des modifications ──────────────────────────────────────────
        String nickDisplay = stateNick.isEmpty() ? "§8inchangé"
                : "§" + COLOR_CHARS[stateNickColorIndex] + stateNick;
        String roleDisplay = stateRole.isEmpty() ? "§8inchangé" : "§f" + stateRole;
        String profDisplay = availableProfessionIds.isEmpty() ? "§8—"
                : "§b" + availableProfessionIds.get(stateSelectedProf);
        g.drawString(this.font,
                "Nick: " + nickDisplay + " §8| Rôle: " + roleDisplay + " §8| Prof: " + profDisplay,
                formX, this.height - 42, 0xAAAAAA, false);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Vide — fond géré dans render()
    }

    // =========================================================================
    // LOGIQUE
    // =========================================================================

    private void loadPlayerState(int index) {
        stateSelectedPlayer = index;
        OpenPlayerProfileGuiPacket.PlayerData p = players.get(index);

        // Détecte la couleur si le nick contient un code couleur §x
        String rawNick = p.currentNick();
        stateNickColorIndex = 0;
        if (rawNick.startsWith("§") && rawNick.length() > 1) {
            char c = rawNick.charAt(1);
            for (int i = 0; i < COLOR_CHARS.length; i++) {
                if (COLOR_CHARS[i] == c) {
                    stateNickColorIndex = i;
                    rawNick = rawNick.substring(2); // retire le §x du champ
                    break;
                }
            }
        }
        stateNick = rawNick;
        stateRole = p.currentRole();

        // Trouve l'index de la profession actuelle
        stateSelectedProf = 0;
        if (!p.currentLicense().isEmpty()) {
            int found = availableProfessionIds.indexOf(p.currentLicense());
            if (found >= 0) stateSelectedProf = found;
        }
    }

    private void applyProfile() {
        if (players.isEmpty()) return;

        OpenPlayerProfileGuiPacket.PlayerData target = players.get(stateSelectedPlayer);

        // Préfixe la couleur choisie au nickname si non vide
        String finalNick = stateNick.trim().isEmpty()
                ? ""
                : "§" + COLOR_CHARS[stateNickColorIndex] + stateNick.trim();

        String license = availableProfessionIds.isEmpty()
                ? ""
                : availableProfessionIds.get(stateSelectedProf);

        PacketDistributor.sendToServer(new SetPlayerProfilePacket(
                target.uuid(), finalNick, stateRole.trim(), license));

        // Mise à jour locale pour retour visuel immédiat
        players.set(stateSelectedPlayer, new OpenPlayerProfileGuiPacket.PlayerData(
                target.uuid(), target.mcName(), finalNick, stateRole.trim(), license));

        rebuild();
    }

    /** Retire les codes couleur §x d'une chaîne pour l'affichage brut */
    private String stripColorCode(String s) {
        if (s.startsWith("§") && s.length() > 2) return s.substring(2);
        return s;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < LIST_W + MARGIN * 2) {
            int max = Math.max(0, players.size() - LIST_VISIBLE);
            playerListScroll = (int) Math.max(0, Math.min(max, playerListScroll - sy));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}