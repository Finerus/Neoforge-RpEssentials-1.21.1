package net.rp.rpessentials.network;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.client.gui.ConfigManagerScreen;
import net.rp.rpessentials.client.gui.PlayerProfileScreen;
import net.rp.rpessentials.client.gui.ProfessionEditorScreen;

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
            Minecraft.getInstance().setScreen(new ProfessionEditorScreen(
                    pendingProfessions, pendingRoles, pendingGlobal, tab));
            pendingProfessions = null;
            pendingRoles       = null;
            pendingGlobal      = null;
        }
    }

    public static void openPlayerProfileGui(
            List<OpenPlayerProfileGuiPacket.PlayerData> players,
            List<String> professionIds,
            List<String> roles) {
        Minecraft.getInstance().setScreen(new PlayerProfileScreen(players, professionIds, roles));
    }

    /**
     * Opens the Config Manager GUI with the given list of available config files.
     * The screen will request individual file entries from the server on demand.
     */
    public static void openConfigManagerGui(List<ConfigGuiFilesPacket.FileEntry> files) {
        Minecraft.getInstance().setScreen(new ConfigManagerScreen(files));
    }
}