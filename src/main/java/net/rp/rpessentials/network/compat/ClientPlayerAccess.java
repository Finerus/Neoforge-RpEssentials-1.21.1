package net.rp.rpessentials.network.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Only loaded on the client. */
final class ClientPlayerAccess {
    private ClientPlayerAccess() {}

    static Player player() {
        return Minecraft.getInstance().player;
    }
}
