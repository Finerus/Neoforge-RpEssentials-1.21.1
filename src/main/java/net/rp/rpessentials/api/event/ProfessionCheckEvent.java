package net.rp.rpessentials.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** Le résultat est mis en cache tant qu'aucune portée dim/zone n'est impliquée, jusqu'au prochain changement de licences ou de config. */
public class ProfessionCheckEvent extends Event {

    public enum Type { CRAFT, BREAK, USE, EQUIP }

    private final ServerPlayer player;
    private final ResourceLocation id;
    private final Type type;
    private boolean allowed;

    public ProfessionCheckEvent(ServerPlayer player, ResourceLocation id, Type type, boolean allowed) {
        this.player = player;
        this.id = id;
        this.type = type;
        this.allowed = allowed;
    }

    public ServerPlayer getPlayer() { return player; }

    public ResourceLocation getId() { return id; }

    public Type getType() { return type; }

    public boolean isAllowed() { return allowed; }

    public void setAllowed(boolean allowed) { this.allowed = allowed; }
}