package net.rp.rpessentials.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

public class LicenseRevokedEvent extends Event {
    private final UUID player;
    private final String profession;

    public LicenseRevokedEvent(UUID player, String profession) {
        this.player = player;
        this.profession = profession;
    }

    public UUID getPlayer() { return player; }

    public String getProfession() { return profession; }
}