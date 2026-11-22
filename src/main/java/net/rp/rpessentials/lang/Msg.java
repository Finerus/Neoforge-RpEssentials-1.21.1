package net.rp.rpessentials.lang;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.rp.rpessentials.util.ColorHelper;

public final class Msg {

    private Msg() {}

    public static String get(ServerPlayer p, ForgeConfigSpec.ConfigValue<String> cv, String... r) {
        return ServerLang.get(p, cv, r);
    }

    public static String get(CommandSourceStack s, ForgeConfigSpec.ConfigValue<String> cv, String... r) {
        return ServerLang.get(s.getEntity() instanceof ServerPlayer sp ? sp : null, cv, r);
    }

    public static Component c(ServerPlayer p, ForgeConfigSpec.ConfigValue<String> cv, String... r) {
        return ColorHelper.parseSection(get(p, cv, r));
    }

    public static Component c(CommandSourceStack s, ForgeConfigSpec.ConfigValue<String> cv, String... r) {
        return ColorHelper.parseSection(get(s, cv, r));
    }
}
