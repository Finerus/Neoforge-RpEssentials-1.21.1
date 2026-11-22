package net.rp.rpessentials.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.rp.rpessentials.RpEssentials;

/**
 * Définition des touches configurables dans Options > Contrôles.
 * Catégorie : "RP Essentials"
 *
 * Enregistrement via modEventBus.addListener() dans RpEssentials (constructeur),
 * car RegisterKeyMappingsEvent est sur le MOD bus.
 */
public class RpKeyBindings {

    public static KeyMapping OPEN_PROFESSION_GUI;
    public static KeyMapping OPEN_PLAYER_PROFILE_GUI;
    public static KeyMapping OPEN_CONFIG_MANAGER_GUI;
    public static KeyMapping OPEN_DICE_GUI;
    /** Inside an RpEssentials screen: writes the texture templates of that screen (see GuiTemplateExporter). */
    public static KeyMapping EXPORT_GUI_TEMPLATES;

    public static final String CATEGORY = "key.categories.rpessentials";

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

        OPEN_CONFIG_MANAGER_GUI = new KeyMapping(
                "key.rpessentials.open_config_manager_gui",
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

        EXPORT_GUI_TEMPLATES = new KeyMapping(
                "key.rpessentials.export_gui_templates",
                InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_F8,
                CATEGORY
        );

        event.register(OPEN_PROFESSION_GUI);
        event.register(OPEN_PLAYER_PROFILE_GUI);
        event.register(OPEN_CONFIG_MANAGER_GUI);
        event.register(OPEN_DICE_GUI);
        // Texture tool: only in the development environment or with -Drpessentials.guiExport=true.
        if (net.rp.rpessentials.client.skin.GuiTemplateExporter.enabled()) event.register(EXPORT_GUI_TEMPLATES);

        RpEssentials.LOGGER.debug("[RpEssentials] GUI keybindings registered (including Config Manager)");
    }
}