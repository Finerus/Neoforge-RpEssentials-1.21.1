package net.rp.rpessentials.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface VanishTrackable {
    void rpessentials$refresh(List<ServerPlayer> players);
}