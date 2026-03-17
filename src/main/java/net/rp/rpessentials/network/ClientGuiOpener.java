package net.rp.rpessentials.network;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.rp.rpessentials.client.gui.PlayerProfileScreen;
import net.rp.rpessentials.client.gui.ProfessionEditorScreen;

import java.util.List;

/**
 * Ouvre les Screens GUI depuis les handlers de packets côté client.
 * Classe séparée pour éviter que le classloader charge Screen (client-only)
 * côté serveur lors du traitement des packets.
 */
@OnlyIn(Dist.CLIENT)
public class ClientGuiOpener {

    public static void openProfessionGui(List<OpenProfessionGuiPacket.ProfessionEntry> professions) {
        Minecraft.getInstance().setScreen(new ProfessionEditorScreen(professions));
    }

    public static void openPlayerProfileGui(
            List<OpenPlayerProfileGuiPacket.PlayerData> players,
            List<String> professionIds,
            List<String> roles) {
        Minecraft.getInstance().setScreen(new PlayerProfileScreen(players, professionIds, roles));
    }
}