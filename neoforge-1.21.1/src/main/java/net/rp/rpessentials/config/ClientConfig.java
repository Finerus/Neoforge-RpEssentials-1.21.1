package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player preferences, stored on the client (rpessentials-client.toml). */
public final class ClientConfig {

    public enum GuiStyle { TEXTURED, LEGACY }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<GuiStyle> GUI_STYLE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CUSTOM_COLORS;
    public static final ModConfigSpec.BooleanValue SERVER_LIST_BADGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("Interface");
        GUI_STYLE = b
                .comment("Look of the RpEssentials screens.",
                        "TEXTURED: drawn textures (textures/gui/sprites), the classic look is used for any missing texture.",
                        "LEGACY: the classic flat look. Can also be switched with the button at the top right of each screen.")
                .defineEnum("guiStyle", GuiStyle.LEGACY);
        CUSTOM_COLORS = b
                .comment("Your own colors in the color palettes (nickname, profession, character sheet), as Name;RRGGBB.",
                        "Added and removed with the + square of the palette. Example: [\"Burgundy;800020\"]")
                //#if forge
                //? .defineListAllowEmpty("customColors", java.util.List.of(), o -> o instanceof String);
                //#else
                .defineList("customColors", java.util.List.of(), o -> o instanceof String);
                //#endif
        b.pop();
        b.push("Multiplayer List");
        SERVER_LIST_BADGE = b
                .comment("Badge next to the name of the servers that have opening hours (\"Closed, opens in 2h15\").",
                        "false: no badge. The open / closed line above the player names stays (it is sent by the server).")
                .define("showServerBadge", true);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
