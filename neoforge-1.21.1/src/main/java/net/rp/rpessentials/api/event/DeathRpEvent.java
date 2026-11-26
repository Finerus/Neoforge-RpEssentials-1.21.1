package net.rp.rpessentials.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;
//#if forge
//? import net.minecraftforge.eventbus.api.Cancelable;
//#else
import net.neoforged.bus.api.ICancellableEvent;
//#endif

/** Annuler l'événement empêche le traitement RP (message, whitelist, historique, reset). */
//#if forge
//? @Cancelable
//? public class DeathRpEvent extends Event {
//#else
public class DeathRpEvent extends Event implements ICancellableEvent {
//#endif
    private final ServerPlayer player;
    private final DamageSource source;

    public DeathRpEvent(ServerPlayer player, DamageSource source) {
        this.player = player;
        this.source = source;
    }

    public ServerPlayer getPlayer() { return player; }

    public DamageSource getSource() { return source; }
}