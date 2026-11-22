package net.rp.rpessentials.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Annuler l'événement empêche le traitement RP (message, whitelist, historique, reset). */
public class DeathRpEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final DamageSource source;

    public DeathRpEvent(ServerPlayer player, DamageSource source) {
        this.player = player;
        this.source = source;
    }

    public ServerPlayer getPlayer() { return player; }

    public DamageSource getSource() { return source; }
}