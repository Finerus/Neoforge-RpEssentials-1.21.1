package net.rp.rpessentials.network.compat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Forge 1.20.1 backport of the 1.20.5+ {@code ByteBufCodecs} (the parts RpEssentials uses). */
public final class ByteBufCodecs {

    private ByteBufCodecs() {}

    private static FriendlyByteBuf friendly(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
    }

    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    public static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    public static final StreamCodec<ByteBuf, Integer> VAR_INT =
            StreamCodec.of((b, v) -> friendly(b).writeVarInt(v), b -> friendly(b).readVarInt());
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 =
            StreamCodec.of((b, v) -> friendly(b).writeUtf(v), b -> friendly(b).readUtf());

    public static <B extends ByteBuf, V> Function<StreamCodec<B, V>, StreamCodec<B, List<V>>> list() {
        return list(Integer.MAX_VALUE);
    }

    /** List of at most {@code maxSize} elements (a bigger size read from the network is refused). */
    public static <B extends ByteBuf, V> Function<StreamCodec<B, V>, StreamCodec<B, List<V>>> list(int maxSize) {
        return element -> StreamCodec.of(
                (buf, list) -> {
                    friendly(buf).writeVarInt(list.size());
                    for (V v : list) element.encode(buf, v);
                },
                buf -> {
                    int size = friendly(buf).readVarInt();
                    if (size < 0 || size > maxSize)
                        throw new io.netty.handler.codec.DecoderException(size + " elements exceeds the maximum of " + maxSize);
                    List<V> out = new ArrayList<>(Math.min(size, 65536));
                    for (int i = 0; i < size; i++) out.add(element.decode(buf));
                    return out;
                });
    }
}
