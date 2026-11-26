package net.rp.rpessentials.network;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.client.ClientPerms;
import net.rp.rpessentials.client.gui.ConfigManagerScreen;
import net.rp.rpessentials.client.gui.PlayerProfileScreen;
import net.rp.rpessentials.client.gui.ProfessionEditorScreen;

import net.rp.rpessentials.permission.RpPerm;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientGuiOpener {

    private static List<OpenProfessionGuiPacket.ProfessionEntry> pendingProfessions = null;
    private static List<OpenRolesGuiPacket.RoleEntry>            pendingRoles       = null;
    private static OpenGlobalRestrictionsPacket                   pendingGlobal      = null;

    public static void openProfessionGui(List<OpenProfessionGuiPacket.ProfessionEntry> professions) {
        pendingProfessions = professions;
        pendingRoles       = null;
        pendingGlobal      = null;
        tryOpenProfessionEditor(0);
    }

    public static void openRolesGui(List<OpenRolesGuiPacket.RoleEntry> roles) {
        if (pendingProfessions != null) {
            pendingRoles = roles;
            tryOpenProfessionEditor(0);
        } else {
            // Ouverture directe sur l'onglet Roles
            Minecraft.getInstance().setScreen(
                    new ProfessionEditorScreen(List.of(), roles, null, 1));
        }
    }

    public static void openGlobalRestrictionsGui(OpenGlobalRestrictionsPacket packet) {
        if (pendingProfessions != null) {
            pendingGlobal = packet;
            tryOpenProfessionEditor(0);
        } else {
            Minecraft.getInstance().setScreen(
                    new ProfessionEditorScreen(List.of(), List.of(), packet, 2));
        }
    }

    public static int requestedTab = 0;

    private static void tryOpenProfessionEditor(int ignored) {
        if (pendingProfessions != null && pendingRoles != null && pendingGlobal != null) {
            int tab = requestedTab;
            requestedTab = 0;
            ProfessionEditorScreen screen = new ProfessionEditorScreen(
                    pendingProfessions, pendingRoles, pendingGlobal, tab);
            // Refresh of an open editor: the view (tab, selection, scroll) is kept.
            if (Minecraft.getInstance().screen instanceof ProfessionEditorScreen old) screen.keepView(old);
            Minecraft.getInstance().setScreen(screen);
            pendingProfessions = null;
            pendingRoles       = null;
            pendingGlobal      = null;
        }
    }

    /** The player lost the permission of the screen they have open: the server would refuse everything done in it. */
    public static void closeRefused() {
        Minecraft mc = Minecraft.getInstance();
        boolean allowed = true;
        if (mc.screen instanceof ConfigManagerScreen) allowed = ClientPerms.has(RpPerm.CONFIG_GUI);
        else if (mc.screen instanceof PlayerProfileScreen) allowed = ClientPerms.has(RpPerm.PROFILE);
        else if (mc.screen instanceof ProfessionEditorScreen)
            allowed = ClientPerms.has(RpPerm.PROFESSIONS_EDIT) || ClientPerms.has(RpPerm.ROLES_MANAGE);
        if (!allowed) mc.setScreen(null);
    }

    public static void receiveStats(java.util.UUID uuid, List<String> lines) {
        if (Minecraft.getInstance().screen instanceof PlayerProfileScreen s) s.setStats(uuid, lines);
    }

    public static void receiveCharacterSheet(CharacterSheetPacket packet) {
        if (Minecraft.getInstance().screen instanceof net.rp.rpessentials.client.gui.CharacterSheetScreen s) s.setData(packet);
    }

    public static void receiveReports(java.util.UUID uuid, boolean enabled, List<PlayerReportsPacket.View> reports) {
        if (Minecraft.getInstance().screen instanceof PlayerProfileScreen s) s.setReports(uuid, enabled, reports);
    }

    public static void receiveNotes(java.util.UUID uuid, List<OpenPlayerProfileGuiPacket.PlayerData.NoteEntry> notes) {
        if (Minecraft.getInstance().screen instanceof PlayerProfileScreen s) s.setNotes(uuid, notes);
    }

    public static void receiveHistory(java.util.UUID uuid, boolean enabled, List<String> lines) {
        if (Minecraft.getInstance().screen instanceof PlayerProfileScreen s) s.setHistory(uuid, enabled, lines);
    }

    public static void openPlayerProfileGui(
            List<OpenPlayerProfileGuiPacket.PlayerData> players,
            List<String> professionIds,
            List<String> roles,
            List<String> platforms,
            java.util.Map<java.util.UUID, Integer> openReports) {
        PlayerProfileScreen screen = new PlayerProfileScreen(players, professionIds, roles, platforms, openReports);
        // Refresh of an open profile: the view (tab, player, scroll) is kept.
        if (Minecraft.getInstance().screen instanceof PlayerProfileScreen old) screen.keepView(old);
        Minecraft.getInstance().setScreen(screen);
    }

    /**
     * Opens the Config Manager GUI with the given list of available config files.
     * The screen will request individual file entries from the server on demand.
     */
    public static void openConfigManagerGui(List<ConfigGuiFilesPacket.FileEntry> files) {
        Minecraft.getInstance().setScreen(new ConfigManagerScreen(files));
    }
}