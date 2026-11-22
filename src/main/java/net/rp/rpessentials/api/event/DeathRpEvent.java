package net.rp.rpessentials.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;

/** Annuler l'événement empêche le traitement RP (message, whitelist, historique, reset). */
@Cancelable
public class DeathRpEvent extends Event {
    private final ServerPlayer player;
    private final DamageSource source;

    public DeathRpEvent(ServerPlayer player, DamageSource source) {
        this.player = player;
        this.source = source;
    }

    public ServerPlayer getPlayer() { return player; }

    public DamageSource getSource() { return source; }
}