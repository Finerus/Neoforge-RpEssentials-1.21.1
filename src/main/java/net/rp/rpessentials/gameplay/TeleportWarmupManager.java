package net.rp.rpessentials.gameplay;

import net.rp.rpessentials.config.Cfg;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.rp.rpessentials.api.event.RpTeleportEvent;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.config.RpConfig;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.lang.ServerLang;
import net.rp.rpessentials.util.ColorHelper;

/**
 * Teleports with a delay: the player must stay still (and not be hurt) for a few seconds.
 * A countdown is shown in the action bar. Every step posts a {@link RpTeleportEvent}.
 */
@EventBusSubscriber(modid = RpEssentials.MODID)
public final class TeleportWarmupManager {

    private static final class Pending {
        final String kind;
        final ServerLevel level;
        final Vec3 destination;
        final float yaw, pitch;
        final Vec3 start;
        final int total;
        final boolean cancelOnMove, cancelOnDamage;
        final Consumer<ServerPlayer> after;
        int left;

        Pending(String kind, ServerLevel level, Vec3 destination, float yaw, float pitch, Vec3 start,
                int total, boolean cancelOnMove, boolean cancelOnDamage, Consumer<ServerPlayer> after) {
            this.kind = kind;
            this.level = level;
            this.destination = destination;
            this.yaw = yaw;
            this.pitch = pitch;
            this.start = start;
            this.total = total;
            this.left = total;
            this.cancelOnMove = cancelOnMove;
            this.cancelOnDamage = cancelOnDamage;
            this.after = after;
        }
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private TeleportWarmupManager() {}

    /** Delay configured for a kind of teleport ("spawn", "afk"), in seconds. */
    public static int configuredSeconds(String kind) {
        if (Cfg.ready(RpConfig.SPEC)) {
            return switch (kind) {
                case "spawn" -> RpConfig.SPAWN_WARMUP_SECONDS.get();
                case "afk" -> RpConfig.AFK_WARMUP_SECONDS.get();
                default -> 0;
            };
        } else {
            return 0;
        }
    }

    private static boolean opt(net.minecraftforge.common.ForgeConfigSpec.BooleanValue v, boolean def) {
        return Cfg.get(v, def);
    }

    /**
     * Starts a teleport. With no delay the player is teleported at once.
     * @param after run after the teleport (message, cooldown, bonus commands...)
     * @return false when another mod cancelled the teleport
     */
    public static boolean start(ServerPlayer player, String kind, ServerLevel level, double x, double y, double z,
                                float yaw, float pitch, int seconds, Consumer<ServerPlayer> after) {
        Vec3 dest = new Vec3(x, y, z);
        RpTeleportEvent.Warmup warmup = new RpTeleportEvent.Warmup(player, kind, level, dest, Math.max(0, seconds) * 20,
                opt(RpConfig.TELEPORT_CANCEL_ON_MOVE, true), opt(RpConfig.TELEPORT_CANCEL_ON_DAMAGE, true));
        if (MinecraftForge.EVENT_BUS.post(warmup)) return false;

        Pending old = PENDING.remove(player.getUUID());
        if (old != null) MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Cancelled(player, old.kind, old.level, old.destination,
                RpTeleportEvent.Cancelled.Reason.REPLACED));

        Pending p = new Pending(kind, level, dest, yaw, pitch, player.position(), warmup.getWarmupTicks(),
                warmup.cancelsOnMove(), warmup.cancelsOnDamage(), after);
        if (p.total <= 0) {
            finish(player, p);
            return true;
        }
        PENDING.put(player.getUUID(), p);
        countdown(player, p);
        return true;
    }

    public static boolean isPending(UUID player) {
        return PENDING.containsKey(player);
    }

    /** Cancels a pending teleport without message. */
    public static void cancel(ServerPlayer player) {
        Pending p = PENDING.remove(player.getUUID());
        if (p != null) MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Cancelled(player, p.kind, p.level, p.destination,
                RpTeleportEvent.Cancelled.Reason.OTHER));
    }

    private static void countdown(ServerPlayer player, Pending p) {
        int seconds = (p.left + 19) / 20;
        player.displayClientMessage(ColorHelper.parseColors(ServerLang.get(player, MessagesConfig.TP_WARMUP_COUNTDOWN,
                "seconds", String.valueOf(seconds))), true);
    }

    private static void abort(ServerPlayer player, Pending p, RpTeleportEvent.Cancelled.Reason reason) {
        PENDING.remove(player.getUUID());
        player.displayClientMessage(ColorHelper.parseColors(ServerLang.get(player,
                reason == RpTeleportEvent.Cancelled.Reason.DAMAGED ? MessagesConfig.TP_WARMUP_CANCELLED_DAMAGE
                        : MessagesConfig.TP_WARMUP_CANCELLED_MOVE)), true);
        MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Cancelled(player, p.kind, p.level, p.destination, reason));
    }

    private static void finish(ServerPlayer player, Pending p) {
        if (MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Pre(player, p.kind, p.level, p.destination))) return;
        ServerLevel fromLevel = player.serverLevel();
        Vec3 from = player.position();
        player.teleportTo(p.level, p.destination.x, p.destination.y, p.destination.z, java.util.Set.of(), p.yaw, p.pitch);
        if (p.after != null) p.after.accept(player);
        MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Post(player, p.kind, p.level, p.destination, fromLevel, from));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (p.cancelOnMove && player.position().distanceToSqr(p.start) > 0.25) {
                abort(player, p, RpTeleportEvent.Cancelled.Reason.MOVED);
                continue;
            }
            p.left--;
            MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Tick(player, p.kind, p.level, p.destination, p.left, p.total));
            if (p.left <= 0) {
                it.remove();
                finish(player, p);
            } else if (p.left % 20 == 0) {
                countdown(player, p);
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending p = PENDING.get(player.getUUID());
        if (p != null && p.cancelOnDamage && event.getAmount() > 0) abort(player, p, RpTeleportEvent.Cancelled.Reason.DAMAGED);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending p = PENDING.remove(player.getUUID());
        if (p != null) MinecraftForge.EVENT_BUS.post(new RpTeleportEvent.Cancelled(player, p.kind, p.level, p.destination,
                RpTeleportEvent.Cancelled.Reason.DISCONNECTED));
    }
}
