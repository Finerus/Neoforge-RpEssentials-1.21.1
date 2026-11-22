package net.rp.rpessentials.network.compat;

import net.minecraft.world.entity.player.Player;

import java.util.concurrent.CompletableFuture;

/** Forge 1.20.1 stand-in for NeoForge's {@code IPayloadContext}. */
public interface IPayloadContext {

    CompletableFuture<Void> enqueueWork(Runnable task);

    /** Server side: the sender. Client side: the local player. */
    Player player();
}
