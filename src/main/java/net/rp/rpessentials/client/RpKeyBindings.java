package net.rp.rpessentials.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.rp.rpessentials.RpEssentials;

/**
 * Définition des touches configurables dans Options > Contrôles.
 * Catégorie : "Oneria RP"
 *
 * Enregistrement via modEventBus.addListener() dans RpEssentials (constructeur),
 * car RegisterKeyMappingsEvent est sur le MOD bus et @EventBusSubscriber(bus = Bus.MOD)
 * est deprecated depuis NeoForge 1.21.1.
 */
public class RpKeyBindings {

    public static KeyMapping OPEN_PROFESSION_GUI;
    public static KeyMapping OPEN_PLAYER_PROFILE_GUI;
    public static KeyMapping OPEN_DICE_GUI;

    public static final String CATEGORY = "key.categories.rpessentials";

    /**
     * Appelé depuis RpEssentials constructeur :
     *   modEventBus.addListener(RpKeyBindings::onRegisterKeyMappings);
     */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_PROFESSION_GUI = new KeyMapping(
                "key.rpessentials.open_profession_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        );
        OPEN_PLAYER_PROFILE_GUI = new KeyMapping(
                "key.rpessentials.open_player_profile_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        );
        OPEN_DICE_GUI = new KeyMapping(
                "key.rpessentials.open_dice_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        );

        event.register(OPEN_PROFESSION_GUI);
        event.register(OPEN_PLAYER_PROFILE_GUI);
        event.register(OPEN_DICE_GUI);

        RpEssentials.LOGGER.debug("[RPEssentials] Keybindings registered");
    }
}