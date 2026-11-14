package net.rp.rpessentials;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Msg {

    private Msg() {}

    public static String get(ServerPlayer p, ModConfigSpec.ConfigValue<String> cv, String... r) {
        return ServerLang.get(p, cv, r);
    }

    public static String get(CommandSourceStack s, ModConfigSpec.ConfigValue<String> cv, String... r) {
        return ServerLang.get(s.getEntity() instanceof ServerPlayer sp ? sp : null, cv, r);
    }

    public static Component c(ServerPlayer p, ModConfigSpec.ConfigValue<String> cv, String... r) {
        return ColorHelper.parseSection(get(p, cv, r));
    }

    public static Component c(CommandSourceStack s, ModConfigSpec.ConfigValue<String> cv, String... r) {
        return ColorHelper.parseSection(get(s, cv, r));
    }
}
