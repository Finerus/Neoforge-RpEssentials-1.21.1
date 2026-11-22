package net.rp.rpessentials.network.compat;

/** Client handler and server handler of one payload (either may be null). */
public record DirectionalPayloadHandler<T>(IPayloadHandler<T> clientSide, IPayloadHandler<T> serverSide) {}
