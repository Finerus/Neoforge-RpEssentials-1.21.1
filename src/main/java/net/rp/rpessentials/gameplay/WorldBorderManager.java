package net.rp.rpessentials.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.rp.rpessentials.config.RpEssentialsConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.permission.RpEssentialsRoleManager;
import net.rp.rpessentials.util.ImmersivePresetHelper;

public class WorldBorderManager {

    private static final Map<UUID, Boolean> hasBeenPrewarned = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> hasBeenWarned    = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> playerZoneState = new ConcurrentHashMap<>();
    private static boolean systemInitialized = false;

    private record Zone(String name, double cx, double cz, int shape, double hx, double hz,
                        String dimension, String msgEnter, String msgExit) {}
    private record ZoneAction(String zone, boolean enter, String command) {}
    private record DimOverride(int distance, boolean teleport, int prewarn) {}

    private static volatile List<Zone> zonesCache = null;
    private static volatile List<ZoneAction> actionsCache = null;
    private static volatile Map<String, DimOverride> overridesCache = null;

    public static void reloadConfig() {
        zonesCache = null;
        actionsCache = null;
        overridesCache = null;
    }

    private static List<Zone> zones() {
        List<Zone> z = zonesCache;
        if (z != null) return z;
        List<Zone> out = new ArrayList<>();
        try {
            for (String zoneDef : RpEssentialsConfig.NAMED_ZONES.get()) {
                String[] parts = zoneDef.split(";");
                if (parts.length < 5) continue;
                try {
                    double[] shape = parseShape(parts[3]);
                    if (shape == null) {
                        RpEssentials.LOGGER.warn("[WorldBorder] Invalid zone shape: {}", zoneDef);
                        continue;
                    }
                    String dimension = null;
                    String enter;
                    String exit;
                    if (parts.length >= 6 && parts[4].contains(":")) {
                        dimension = parts[4].trim();
                        enter = parts[5].trim();
                        exit = parts.length >= 7 ? parts[6].trim() : "";
                    } else {
                        enter = parts[4].trim();
                        exit = parts.length >= 6 ? parts[5].trim() : "";
                    }
                    out.add(new Zone(parts[0].trim(), Double.parseDouble(parts[1].trim()),
                            Double.parseDouble(parts[2].trim()), (int) shape[0], shape[1], shape[2],
                            dimension, enter, exit));
                } catch (NumberFormatException e) {
                    RpEssentials.LOGGER.warn("[WorldBorder] Invalid zone: {}", zoneDef);
                }
            }
        } catch (IllegalStateException e) {
            return List.of();
        }
        zonesCache = out;
        return out;
    }

    private static List<ZoneAction> actions() {
        List<ZoneAction> a = actionsCache;
        if (a != null) return a;
        List<ZoneAction> out = new ArrayList<>();
        try {
            for (String line : RpEssentialsConfig.ZONE_ACTIONS.get()) {
                String[] p = line.split(";", 3);
                if (p.length < 3) continue;
                out.add(new ZoneAction(p[0].trim(), p[1].trim().equalsIgnoreCase("enter"), p[2].trim()));
            }
        } catch (IllegalStateException e) {
            return List.of();
        }
        actionsCache = out;
        return out;
    }

    private static boolean inside(Zone z, double dx, double dz) {
        return switch (z.shape()) {
            case 1 -> Math.abs(dx) <= z.hx() && Math.abs(dz) <= z.hz();
            case 2 -> (dx * dx) / (z.hx() * z.hx()) + (dz * dz) / (z.hz() * z.hz()) <= 1;
            default -> dx * dx + dz * dz <= z.hx() * z.hx();
        };
    }

    private static void runActions(ServerPlayer player, String zone, boolean enter) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ZoneAction a : actions()) {
            if (a.enter() != enter || !a.zone().equalsIgnoreCase(zone)) continue;
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    a.command().replace("{player}", player.getGameProfile().getName()));
        }
    }

    private static Map<String, DimOverride> overrides() {
        Map<String, DimOverride> m = overridesCache;
        if (m != null) return m;
        Map<String, DimOverride> out = new HashMap<>();
        try {
            for (String entry : RpEssentialsConfig.WORLD_BORDER_DIMENSION_OVERRIDES.get()) {
                String[] parts = entry.split(";", 4);
                if (parts.length < 3) continue;
                try {
                    int dist = Integer.parseInt(parts[1].trim());
                    boolean tp = Boolean.parseBoolean(parts[2].trim());
                    int prewarn = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : -1;
                    out.put(parts[0].trim(), new DimOverride(dist, tp, prewarn));
                } catch (NumberFormatException e) {
                    RpEssentials.LOGGER.warn("[WorldBorder] Invalid dimension override: {}", entry);
                }
            }
        } catch (IllegalStateException e) {
            return Map.of();
        }
        overridesCache = out;
        return out;
    }

    public static double[] parseShape(String raw) {
        try {
            String s = raw.trim().toLowerCase();
            int i = s.indexOf(':');
            String type = i < 0 ? "circle" : s.substring(0, i);
            String v = i < 0 ? s : s.substring(i + 1);
            double a;
            double b;
            if (v.contains("x")) {
                String[] p = v.split("x");
                a = Double.parseDouble(p[0].trim());
                b = Double.parseDouble(p[1].trim());
            } else {
                a = Double.parseDouble(v.trim());
                b = a;
            }
            if (a <= 0 || b <= 0) return null;
            return switch (type) {
                case "circle" -> new double[]{0, a, a};
                case "square", "rect" -> new double[]{1, a, b};
                case "ellipse" -> new double[]{2, a, b};
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    // =========================================================================
    // AFFICHAGE DES ZONES
    // =========================================================================
    private record ZoneView(String zone, long until) {}
    private static final Map<UUID, ZoneView> zoneViews = new ConcurrentHashMap<>();

    public static List<String> zoneNames() {
        List<String> out = new ArrayList<>();
        for (Zone z : zones()) out.add(z.name());
        return out;
    }

    private static Zone findZone(String name) {
        for (Zone z : zones()) if (z.name().equalsIgnoreCase(name)) return z;
        return null;
    }

    public static boolean showZone(ServerPlayer p, String name) {
        Zone z = findZone(name);
        if (z == null) return false;
        zoneViews.put(p.getUUID(), new ZoneView(z.name(), System.currentTimeMillis() + 120_000L));
        return true;
    }

    public static void hideZone(UUID id) { zoneViews.remove(id); }

    public static boolean isInZone(ServerPlayer p, String name) {
        Zone z = findZone(name);
        if (z == null) return false;
        if (z.dimension() != null && !z.dimension().equals(p.level().dimension().location().toString())) return false;
        return inside(z, p.getX() - z.cx(), p.getZ() - z.cz());
    }

    private static List<double[]> outline(Zone z) {
        List<double[]> out = new ArrayList<>();
        if (z.shape() == 1) {
            int nx = Math.max(1, (int) Math.ceil(z.hx()));
            int nz = Math.max(1, (int) Math.ceil(z.hz()));
            for (int i = 0; i <= nx; i++) {
                double x = z.cx() - z.hx() + 2 * z.hx() * i / nx;
                out.add(new double[]{x, z.cz() - z.hz()});
                out.add(new double[]{x, z.cz() + z.hz()});
            }
            for (int j = 1; j < nz; j++) {
                double zz = z.cz() - z.hz() + 2 * z.hz() * j / nz;
                out.add(new double[]{z.cx() - z.hx(), zz});
                out.add(new double[]{z.cx() + z.hx(), zz});
            }
            return out;
        }
        int n = (int) Math.max(24, Math.min(360, Math.PI * (z.hx() + z.hz())));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            out.add(new double[]{z.cx() + z.hx() * Math.cos(a), z.cz() + z.hz() * Math.sin(a)});
        }
        return out;
    }

    public static void tickZoneViews(MinecraftServer server) {
        if (zoneViews.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ZoneView> e : zoneViews.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Zone z = findZone(e.getValue().zone());
            if (p == null || z == null || e.getValue().until() < now) {
                zoneViews.remove(e.getKey());
                continue;
            }
            if (z.dimension() != null && !z.dimension().equals(p.level().dimension().location().toString())) continue;
            ServerLevel level = p.serverLevel();
            int sent = 0;
            for (double[] pt : outline(z)) {
                double dx = pt[0] - p.getX();
                double dz = pt[1] - p.getZ();
                if (dx * dx + dz * dz > 48 * 48) continue;
                level.sendParticles(p, ParticleTypes.END_ROD, true, pt[0], p.getY() + 1.0, pt[1], 1, 0, 0, 0, 0);
                level.sendParticles(p, ParticleTypes.END_ROD, true, pt[0], p.getY() + 2.5, pt[1], 1, 0, 0, 0, 0);
                if (++sent >= 120) break;
            }
        }
    }

    public static List<String> zoneDefs() {
        List<String> out = new ArrayList<>();
        for (Zone z : zones()) {
            out.add(z.name() + ";" + z.cx() + ";" + z.cz() + ";" + z.shape() + ";" + z.hx() + ";" + z.hz()
                    + ";" + (z.dimension() == null ? "" : z.dimension()));
        }
        return out;
    }

    // =========================================================================
    // TICK PRINCIPAL
    // =========================================================================
    public static void tick(MinecraftServer server) {
        try {
            if (RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING == null) return;
            if (!RpEssentialsConfig.ENABLE_WORLD_BORDER_WARNING.get()) return;
            if (!systemInitialized) {
                systemInitialized = true;
                RpEssentials.LOGGER.info("[RpEssentials] System initialized. Hi, have a great day! - Finerus");
            }
        } catch (Exception e) { return; }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                checkPlayerDistance(server, player);
                checkNamedZones(player);
            } catch (Exception e) {
                RpEssentials.LOGGER.error("[WorldBorder] Error checking player {}", player.getName().getString(), e);
            }
        }
    }

    // =========================================================================
    // DISTANCE + TELEPORT
    // =========================================================================
    private static void checkPlayerDistance(MinecraftServer server, ServerPlayer player) {
        if (RpEssentialsRoleManager.has(player, RpEssentialsRoleManager.Permission.BYPASS_WORLD_BORDER))
            return;

        String dimId = player.level().dimension().location().toString();
        DimBorderConfig cfg = resolveDimConfig(dimId);
        if (cfg.distance <= 0) return;

        boolean square = !"CIRCLE".equalsIgnoreCase(RpEssentialsConfig.WORLD_BORDER_SHAPE.get());
        int prewarnDist = resolvePrewarnDistance(dimId, cfg.distance);

        var spawn = player.serverLevel().getSharedSpawnPos();
        double dx = player.getX() - spawn.getX();
        double dz = player.getZ() - spawn.getZ();
        double actual = square ? Math.max(Math.abs(dx), Math.abs(dz)) : Math.sqrt(dx * dx + dz * dz);
        UUID uuid = player.getUUID();

        boolean outsideBorder = actual > cfg.distance;
        boolean outsidePrewarn = prewarnDist > 0 && actual > prewarnDist;

        if (outsidePrewarn && !outsideBorder) {
            if (!hasBeenPrewarned.getOrDefault(uuid, false)) {
                sendPrewarnMessage(player, actual, cfg.distance);
                hasBeenPrewarned.put(uuid, true);
            }
        } else if (!outsidePrewarn) {
            hasBeenPrewarned.put(uuid, false);
        }

        if (outsideBorder) {
            if (!hasBeenWarned.getOrDefault(uuid, false)) {
                sendBorderWarning(player, actual);
                hasBeenWarned.put(uuid, true);
                hasBeenPrewarned.put(uuid, true);

                if (cfg.teleport) {
                    teleportToOpposite(server, player, spawn.getX(), spawn.getZ(), cfg.distance, actual);
                }
            }
        } else {
            hasBeenWarned.put(uuid, false);
        }
    }

    private static void teleportToOpposite(MinecraftServer server, ServerPlayer player,
                                           double spawnX, double spawnZ, int distance, double actual) {
        if (actual == 0) return;
        double dx = player.getX() - spawnX;
        double dz = player.getZ() - spawnZ;

        double ratio = (distance * 0.95) / actual;
        double targetX = spawnX - dx * ratio;
        double targetZ = spawnZ - dz * ratio;

        ServerLevel level = player.serverLevel();
        double targetY = findSafeY(level, (int) Math.floor(targetX), (int) Math.floor(targetZ), player.getY());

        player.teleportTo(level, targetX, targetY, targetZ,
                player.getYRot(), player.getXRot());

        RpEssentials.LOGGER.info("[WorldBorder] Teleported {} to opposite side ({},{},{}).",
                player.getName().getString(),
                Math.floor(targetX), targetY, Math.floor(targetZ));
    }

    private static double findSafeY(ServerLevel level, int x, int z, double currentY) {
        int min = level.getMinBuildHeight();
        boolean ceiling = level.dimensionType().hasCeiling();
        int roof = min + level.dimensionType().logicalHeight();
        boolean onRoof = ceiling && currentY >= roof;

        int top = (ceiling && !onRoof) ? roof - 6 : level.getMaxBuildHeight() - 1;
        int bottom = onRoof ? roof - 2 : min;

        for (int y = top; y > bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolid()
                    && !level.getBlockState(pos.above()).isSolid()
                    && !level.getBlockState(pos.above(2)).isSolid()) {
                return y + 1;
            }
        }
        return onRoof ? currentY : 64;
    }

    // =========================================================================
    // NAMED ZONE
    // =========================================================================
    private static void checkNamedZones(ServerPlayer player) {
        List<Zone> list = zones();
        if (list.isEmpty()) return;

        Set<String> current = playerZoneState.computeIfAbsent(player.getUUID(),
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        String playerDim = null;

        for (Zone z : list) {
            if (z.dimension() != null) {
                if (playerDim == null) playerDim = player.level().dimension().location().toString();
                if (!playerDim.equals(z.dimension())) {
                    if (current.remove(z.name())) {
                        if (!z.msgExit().isEmpty()) sendZoneMessage(player, z.msgExit());
                        runActions(player, z.name(), false);
                    }
                    continue;
                }
            }

            boolean inZone = inside(z, player.getX() - z.cx(), player.getZ() - z.cz());
            boolean wasIn = current.contains(z.name());

            if (inZone && !wasIn) {
                current.add(z.name());
                sendZoneMessage(player, z.msgEnter());
                runActions(player, z.name(), true);
            } else if (!inZone && wasIn) {
                current.remove(z.name());
                if (!z.msgExit().isEmpty()) sendZoneMessage(player, z.msgExit());
                runActions(player, z.name(), false);
            }
        }
    }

    // =========================================================================
    // RÉSOLUTION CONFIG PAR DIMENSION
    // =========================================================================
    private record DimBorderConfig(int distance, boolean teleport) {}

    private static DimBorderConfig resolveDimConfig(String dimId) {
        try {
            DimOverride o = overrides().get(dimId);
            if (o != null) {
                int effective = o.distance() > 0 ? o.distance() : RpEssentialsConfig.WORLD_BORDER_DISTANCE.get();
                return new DimBorderConfig(effective, o.teleport());
            }
            return new DimBorderConfig(
                    RpEssentialsConfig.WORLD_BORDER_DISTANCE.get(),
                    RpEssentialsConfig.WORLD_BORDER_TELEPORT_ENABLED.get());
        } catch (IllegalStateException e) {
            return new DimBorderConfig(0, false);
        }
    }

    private static int resolvePrewarnDistance(String dimId, int borderDistance) {
        try {
            DimOverride o = overrides().get(dimId);
            if (o != null && o.prewarn() >= 0) {
                return o.prewarn() > 0 && o.prewarn() < borderDistance ? o.prewarn() : 0;
            }
            int global = RpEssentialsConfig.WORLD_BORDER_PREWARN_DISTANCE.get();
            return global > 0 && global < borderDistance ? global : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    // =========================================================================
    // ENVOI
    // =========================================================================
    private static void sendBorderWarning(ServerPlayer player, double distance) {
        try {
            String message = RpEssentialsConfig.WORLD_BORDER_MESSAGE.get()
                    .replace("{distance}", String.format("%.0f", distance))
                    .replace("{player}",   player.getName().getString());
            sendMessage(player, message);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 1.0f, 0.5f);
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WorldBorder] Error sending border warning.", e);
        }
    }

    private static void sendPrewarnMessage(ServerPlayer player, double distance, int borderDistance) {
        try {
            String message = RpEssentialsConfig.WORLD_BORDER_PREWARN_MESSAGE.get()
                    .replace("{distance}", String.format("%.0f", distance))
                    .replace("{player}",   player.getName().getString())
                    .replace("{border}",   String.valueOf(borderDistance));
            sendMessage(player, message);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.2f);
        } catch (Exception e) {
            RpEssentials.LOGGER.error("[WorldBorder] Error sending prewarn message.", e);
        }
    }

    private static void sendZoneMessage(ServerPlayer player, String message) {
        try { sendMessage(player, message); }
        catch (Exception e) { RpEssentials.LOGGER.error("[WorldBorder] Error sending zone message.", e); }
    }

    private static void sendMessage(ServerPlayer player, String message) {
        String mode = "ACTION_BAR";
        try { mode = RpEssentialsConfig.ZONE_MESSAGE_MODE.get().toUpperCase(); }
        catch (Exception ignored) {}
        ImmersivePresetHelper.send(player, message, mode);
    }

    // =========================================================================
    // CACHE
    // =========================================================================
    public static void clearCache(UUID id) {
        hasBeenWarned.remove(id);
        hasBeenPrewarned.remove(id);
        playerZoneState.remove(id);
        zoneViews.remove(id);
    }

    public static void clearAllCache() {
        hasBeenWarned.clear();
        hasBeenPrewarned.clear();
        playerZoneState.clear();
        zoneViews.clear();
        systemInitialized = false;
    }
}