package net.rp.rpessentials.mixin;

import net.neoforged.neoforge.network.ConfigSync;
//#if forge
//? import net.minecraftforge.network.HandshakeMessages;
//#else
import net.neoforged.neoforge.network.payload.ConfigFilePayload;
//#endif
import net.rp.rpessentials.core.ConfigSyncFilter;
//#if forge
//? import org.apache.commons.lang3.tuple.Pair;
//#else
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** The config files of the mod sent to a client that joins (see {@link ConfigSyncFilter}). Must always apply. */
//#if forge
//? @Mixin(value = ConfigSync.class, remap = false)
//#else
@Mixin(value = ConfigSync.class, remap = false)
//#endif
public abstract class MixinConfigSync {

    //#if forge
    //? @Inject(method = "syncConfigs", at = @At("RETURN"), cancellable = true, remap = false)
    //? private void rpessentials$filterConfigs(boolean isLocal,
                                            //? CallbackInfoReturnable<List<Pair<String, HandshakeMessages.S2CConfigData>>> cir) {
        //? List<Pair<String, HandshakeMessages.S2CConfigData>> out = new ArrayList<>(cir.getReturnValue().size());
        //? for (Pair<String, HandshakeMessages.S2CConfigData> p : cir.getReturnValue()) {
            //? byte[] bytes = ConfigSyncFilter.replacement(p.getRight().getFileName());
            //? out.add(bytes == null ? p
                    //? : Pair.of(p.getLeft(), new HandshakeMessages.S2CConfigData(p.getRight().getFileName(), bytes)));
    //#else
    @Inject(method = "syncConfigs", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rpessentials$filterConfigs(CallbackInfoReturnable<List<ConfigFilePayload>> cir) {
        List<ConfigFilePayload> out = new ArrayList<>(cir.getReturnValue().size());
        for (ConfigFilePayload p : cir.getReturnValue()) {
            byte[] bytes = ConfigSyncFilter.replacement(p.fileName());
            out.add(bytes == null ? p : new ConfigFilePayload(p.fileName(), bytes));
    //#endif
        }
        cir.setReturnValue(out);
    }
}
