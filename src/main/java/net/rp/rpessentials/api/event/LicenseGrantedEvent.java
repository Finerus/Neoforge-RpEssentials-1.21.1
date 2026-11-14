package net.rp.rpessentials.api.event;

import net.neoforged.bus.api.Event;

import java.util.UUID;

public class LicenseGrantedEvent extends Event {
    private final UUID player;
    private final String profession;

    public LicenseGrantedEvent(UUID player, String profession) {
        this.player = player;
        this.profession = profession;
    }

    public UUID getPlayer() { return player; }

    public String getProfession() { return profession; }
}