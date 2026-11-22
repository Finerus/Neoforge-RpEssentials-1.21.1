package net.rp.rpessentials.network.compat;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 stand-in for NeoForge's payload registrar.
 * <p>
 * All payloads travel inside ONE {@link SimpleChannel} message ({@link Envelope}) that carries the payload id,
 * so the packet classes and their codecs stay the same as in the NeoForge version.
 */
public final class PayloadRegistrar {

    private record Entry<T extends CustomPacketPayload>(StreamCodec<? super FriendlyByteBuf, T> codec,
                                                        DirectionalPayloadHandler<T> handler,
                                                        boolean toClient) {}

    private static final Map<ResourceLocation, Entry<?>> ENTRIES = new HashMap<>();
    private static SimpleChannel channel;

    private PayloadRegistrar() {}

    /** Creates the channel (once) and returns a registrar for the payloads. */
    public static PayloadRegistrar create(ResourceLocation name, String version) {
        if (channel == null) {
            channel = NetworkRegistry.newSimpleChannel(name, () -> version, version::equals, version::equals);
            channel.registerMessage(0, Envelope.class, PayloadRegistrar::encode, PayloadRegistrar::decode,
                    PayloadRegistrar::handle);
        }
        return new PayloadRegistrar();
    }

    static SimpleChannel channel() {
        if (channel == null) throw new IllegalStateException("RpEssentials network channel not created yet");
        return channel;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type,
                                                                         StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                         DirectionalPayloadHandler<T> handler) {
        ENTRIES.put(type.id(), new Entry<>(codec, handler, true));
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type,
                                                                         StreamCodec<? super FriendlyByteBuf, T> codec,
                                                                         DirectionalPayloadHandler<T> handler) {
        ENTRIES.put(type.id(), new Entry<>(codec, handler, false));
        return this;
    }

    /** The message actually sent on the SimpleChannel. */
    record Envelope(CustomPacketPayload payload) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void encode(Envelope env, FriendlyByteBuf buf) {
        ResourceLocation id = env.payload().type().id();
        Entry entry = ENTRIES.get(id);
        if (entry == null) throw new IllegalStateException("Unregistered RpEssentials payload " + id);
        buf.writeResourceLocation(id);
        entry.codec().encode(buf, env.payload());
    }

    private static Envelope decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Entry<?> entry = ENTRIES.get(id);
        if (entry == null) throw new IllegalStateException("Unknown RpEssentials payload " + id);
        return new Envelope((CustomPacketPayload) entry.codec().decode(buf));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handle(Envelope env, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
        Entry entry = ENTRIES.get(env.payload().type().id());
        if (entry == null) return;
        boolean onClient = ctx.getDirection().getReceptionSide() == LogicalSide.CLIENT;
        // A payload only goes one way: a client-bound payload sent by a client is ignored, and vice versa.
        if (entry.toClient() != onClient) return;
        IPayloadHandler h = onClient ? entry.handler().clientSide() : entry.handler().serverSide();
        if (h == null) return;
        h.handle(env.payload(), new IPayloadContext() {
            @Override
            public CompletableFuture<Void> enqueueWork(Runnable task) {
                return ctx.enqueueWork(task);
            }

            @Override
            public Player player() {
                return onClient ? ClientPlayerAccess.player() : ctx.getSender();
            }
        });
    }
}
