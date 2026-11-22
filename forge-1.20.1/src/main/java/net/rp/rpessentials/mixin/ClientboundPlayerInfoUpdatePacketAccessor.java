package net.rp.rpessentials.mixin;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface ClientboundPlayerInfoUpdatePacketAccessor {
    @Accessor(value = "entries")
    List<ClientboundPlayerInfoUpdatePacket.Entry> getEntries();

    @Accessor(value = "entries")
    @Mutable
    void setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}