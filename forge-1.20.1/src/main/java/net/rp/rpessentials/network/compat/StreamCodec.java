package net.rp.rpessentials.network.compat;

import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Forge 1.20.1 backport of the 1.20.5+ {@code StreamCodec}: only the parts RpEssentials uses.
 * Keeps the packet classes identical to the NeoForge version.
 */
public interface StreamCodec<B extends ByteBuf, V> {

    V decode(B buf);

    void encode(B buf, V value);

    static <B extends ByteBuf, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override public V decode(B buf) { return decoder.apply(buf); }
            @Override public void encode(B buf, V value) { encoder.accept(buf, value); }
        };
    }

    /** Encoder written as an instance method of the value ({@code value.write(buf)}). */
    static <B extends ByteBuf, V> StreamCodec<B, V> ofMember(BiConsumer<V, B> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override public V decode(B buf) { return decoder.apply(buf); }
            @Override public void encode(B buf, V value) { encoder.accept(value, buf); }
        };
    }

    static <B extends ByteBuf, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            Function<T1, C> factory) {
        return new StreamCodec<>() {
            @Override public C decode(B buf) { return factory.apply(c1.decode(buf)); }
            @Override public void encode(B buf, C v) { c1.encode(buf, g1.apply(v)); }
        };
    }

    static <B extends ByteBuf, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            BiFunction<T1, T2, C> factory) {
        return new StreamCodec<>() {
            @Override public C decode(B buf) {
                T1 a = c1.decode(buf);
                T2 b = c2.decode(buf);
                return factory.apply(a, b);
            }
            @Override public void encode(B buf, C v) {
                c1.encode(buf, g1.apply(v));
                c2.encode(buf, g2.apply(v));
            }
        };
    }

    interface Function3<A, B, C, R> { R apply(A a, B b, C c); }

    static <B extends ByteBuf, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            Function3<T1, T2, T3, C> factory) {
        return new StreamCodec<>() {
            @Override public C decode(B buf) {
                T1 a = c1.decode(buf);
                T2 b = c2.decode(buf);
                T3 c = c3.decode(buf);
                return factory.apply(a, b, c);
            }
            @Override public void encode(B buf, C v) {
                c1.encode(buf, g1.apply(v));
                c2.encode(buf, g2.apply(v));
                c3.encode(buf, g3.apply(v));
            }
        };
    }

    interface Function4<A, B, C, D, R> { R apply(A a, B b, C c, D d); }

    static <B extends ByteBuf, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, Function<C, T4> g4,
            Function4<T1, T2, T3, T4, C> factory) {
        return new StreamCodec<>() {
            @Override public C decode(B buf) {
                T1 a = c1.decode(buf);
                T2 b = c2.decode(buf);
                T3 c = c3.decode(buf);
                T4 d = c4.decode(buf);
                return factory.apply(a, b, c, d);
            }
            @Override public void encode(B buf, C v) {
                c1.encode(buf, g1.apply(v));
                c2.encode(buf, g2.apply(v));
                c3.encode(buf, g3.apply(v));
                c4.encode(buf, g4.apply(v));
            }
        };
    }

    /** {@code codec.apply(ByteBufCodecs.list())}. */
    default <O> O apply(Function<StreamCodec<B, V>, O> operation) {
        return operation.apply(this);
    }
}
