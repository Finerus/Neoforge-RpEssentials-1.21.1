package net.rp.rpessentials.network.compat;

import net.minecraft.server.level.ServerPlayer;

/** Forge 1.20.1 stand-in for NeoForge's static {@code PacketDistributor} helpers. */
public final class PacketDistributor {

    private PacketDistributor() {}

    public static void sendToServer(CustomPacketPayload payload) {
        PayloadRegistrar.channel().sendToServer(new PayloadRegistrar.Envelope(payload));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PayloadRegistrar.channel().send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new PayloadRegistrar.Envelope(payload));
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PayloadRegistrar.channel().send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new PayloadRegistrar.Envelope(payload));
    }
}
