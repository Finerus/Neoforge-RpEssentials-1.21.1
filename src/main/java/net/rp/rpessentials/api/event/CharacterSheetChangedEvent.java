package net.rp.rpessentials.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.UUID;

/** Fired on the NeoForge event bus after a character sheet was changed (GUI or API). */
public class CharacterSheetChangedEvent extends Event {
    private final UUID player;
    private final List<String> changedFields;

    public CharacterSheetChangedEvent(UUID player, List<String> changedFields) {
        this.player = player;
        this.changedFields = changedFields;
    }

    public UUID getPlayer() { return player; }

    /** Ids of the fields that changed (name, age, race, languages, appearance, description, lore, custom ids). */
    public List<String> getChangedFields() { return changedFields; }
}
