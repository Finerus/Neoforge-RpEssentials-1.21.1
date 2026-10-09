package net.rp.rpessentials.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PacketValidation {

    public static final int MAX_LIST = 2000;
    public static final int MAX_STRING = 256;

    private static final Pattern ITEM = Pattern.compile("[a-z0-9_.\\-/*:]+");
    private static final Pattern CONTAINER = Pattern.compile("[a-z0-9_.\\-/*:]+;[a-z0-9_]+(,[a-z0-9_]+)*");

    private PacketValidation() {}

    public static int readCount(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LIST || n > buf.readableBytes()) {
            throw new DecoderException("Invalid element count: " + n);
        }
        return n;
    }

    public static List<String> readList(FriendlyByteBuf buf) {
        int n = readCount(buf);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(MAX_STRING));
        return out;
    }

    public static List<String> cleanItems(List<String> in) {
        return in.stream().map(String::trim).filter(s -> ITEM.matcher(s).matches()).distinct().toList();
    }

    public static List<String> cleanContainers(List<String> in) {
        return in.stream().map(String::trim).filter(s -> CONTAINER.matcher(s).matches()).distinct().toList();
    }
}