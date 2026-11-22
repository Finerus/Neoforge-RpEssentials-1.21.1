package net.rp.rpessentials.network.compat;

import net.minecraft.resources.ResourceLocation;

/** Forge 1.20.1 backport of the 1.20.5+ {@code CustomPacketPayload}. */
public interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
