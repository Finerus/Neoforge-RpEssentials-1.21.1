package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player preferences, stored on the client (rpessentials-client.toml). */
public final class ClientConfig {

    public enum GuiStyle { TEXTURED, LEGACY }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<GuiStyle> GUI_STYLE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CUSTOM_COLORS;

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
                .defineList("customColors", java.util.List.of(), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
