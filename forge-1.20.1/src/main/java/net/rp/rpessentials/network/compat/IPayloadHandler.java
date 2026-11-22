package net.rp.rpessentials.network.compat;

@FunctionalInterface
public interface IPayloadHandler<T> {
    void handle(T payload, IPayloadContext context);
}
