package net.rp.rpessentials.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface ClientboundPlayerInfoUpdatePacketAccessor {
    // remap = false force le mixin à utiliser le nom exact "entries" sans chercher d'obfuscation
    @Accessor(value = "entries", remap = false)
    List<ClientboundPlayerInfoUpdatePacket.Entry> getEntries();

    @Accessor(value = "entries", remap = false)
    @Mutable
    void setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}