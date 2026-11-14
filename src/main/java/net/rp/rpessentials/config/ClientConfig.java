package net.rp.rpessentials.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player preferences, stored on the client (rpessentials-client.toml). */
public final class ClientConfig {

    public enum GuiStyle { TEXTURED, LEGACY }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<GuiStyle> GUI_STYLE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("Interface");
        GUI_STYLE = b
                .comment("Look of the RpEssentials screens.",
                        "TEXTURED: drawn textures (textures/gui/sprites), the classic look is used for any missing texture.",
                        "LEGACY: the classic flat look. Can also be switched with the button at the top right of each screen.")
                .defineEnum("guiStyle", GuiStyle.TEXTURED);
        b.pop();
        SPEC = b.build();
    }

    private ClientConfig() {}
}
