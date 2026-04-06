package net.rp.rpessentials.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.rp.rpessentials.RpEssentials;
import net.rp.rpessentials.RpEssentialsPermissions;
import net.rp.rpessentials.config.*;
import net.rp.rpessentials.moderation.*;
import net.rp.rpessentials.profession.LicenseManager;

import java.util.*;

public class RpStaffCommands {

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PLATFORM_SUGGESTIONS =
            (ctx, builder) -> {
                try {
                    if (ModerationConfig.PLATFORMS != null && ModerationConfig.PLATFORMS.get() != null) {
                        for (String platform : ModerationConfig.PLATFORMS.get()) {
                            String[] parts = platform.split(";");
                            if (parts.length > 0) builder.suggest(parts[0]);
                        }
                    }
                } catch (IllegalStateException ignored) {}
                return builder.buildFuture();
            };

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        var staffNode = Commands.literal("staff")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()));

        staffNode.then(Commands.literal("gamemode")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("survival").suggest("creative").suggest("adventure").suggest("spectator");
                            return builder.buildFuture();
                        })
                        .executes(RpStaffCommands::silentGamemodeSelf)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(RpStaffCommands::silentGamemodeTarget))));

        staffNode.then(Commands.literal("tp")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpStaffCommands::silentTeleport)));

        staffNode.then(Commands.literal("effect")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("effect", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> {
                                    BuiltInRegistries.MOB_EFFECT.keySet().forEach(loc -> builder.suggest(loc.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                .executes(RpStaffCommands::silentEffect))))));

        staffNode.then(Commands.literal("platform")
                .executes(RpStaffCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpStaffCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(RpStaffCommands::platformTargetSpecific))));

        staffNode.then(Commands.literal("broadcast")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(RpStaffCommands::staffBroadcast)));

        return staffNode;
    }

    /** Registers /setplatform and /platform as standalone aliases */
    public static void registerAliases(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setplatform")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("platform_name", StringArgumentType.word())
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(RpStaffCommands::setPlatform)))))));

        dispatcher.register(Commands.literal("platform")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .executes(RpStaffCommands::platformSelf)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(RpStaffCommands::platformTarget)
                        .then(Commands.argument("platform_id", StringArgumentType.word())
                                .suggests(PLATFORM_SUGGESTIONS)
                                .executes(RpStaffCommands::platformTargetSpecific))));
    }

    /** /rpessentials stats */
    public static LiteralArgumentBuilder<CommandSourceStack> buildStats() {
        return Commands.literal("stats")
                .requires(src -> RpEssentialsPermissions.isStaff(src.getPlayer()))
                .executes(RpStaffCommands::showStats);
    }

    // =========================================================================
    // HANDLERS
    // =========================================================================

    private static int silentGamemodeSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"));
    }

    private static int silentGamemodeTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setGamemode(ctx, EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "mode"));
    }

    private static int setGamemode(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String modeName) {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }
        GameType type = parseGameMode(modeName);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid gamemode."));
            return 0;
        }
        target.setGameMode(type);
        String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : "Console";
        logToStaff(ctx.getSource(), sourceName + " set " + target.getName().getString() + " to " + modeName);
        if (ModerationConfig.NOTIFY_TARGET.get() && target != ctx.getSource().getEntity()) {
            target.sendSystemMessage(Component.literal("§7[Staff] Your gamemode has been changed to " + modeName));
        }
        return 1;
    }

    private static int silentTeleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }
        ServerPlayer executor = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        executor.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        logToStaff(ctx.getSource(), executor.getName().getString() + " TP'd to " + target.getName().getString());
        return 1;
    }

    private static int silentEffect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!ModerationConfig.ENABLE_SILENT_COMMANDS.get()) {
            ctx.getSource().sendFailure(Component.literal("§cSilent commands are disabled."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ResourceLocation effectId = ResourceLocationArgument.getId(ctx, "effect");
        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        int amplifier = IntegerArgumentType.getInteger(ctx, "amplifier");
        Optional<Holder.Reference<MobEffect>> effect =
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(BuiltInRegistries.MOB_EFFECT.key(), effectId));
        if (effect.isPresent()) {
            target.addEffect(new MobEffectInstance(effect.get(), duration * 20, amplifier, false, false));
            String sourceName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : "Console";
            logToStaff(ctx.getSource(), sourceName + " gave effect " + effectId + " to " + target.getName().getString());
            if (ModerationConfig.NOTIFY_TARGET.get()) {
                target.sendSystemMessage(Component.literal("§7[Staff] An effect has been applied to you."));
            }
        } else {
            ctx.getSource().sendFailure(Component.literal("§cInvalid effect."));
            return 0;
        }
        return 1;
    }

    private static int platformSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), null, "platform1");
    }

    private static int platformTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), "platform1");
    }

    private static int platformTargetSpecific(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return teleportToPlatform(ctx.getSource(), ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "target"), StringArgumentType.getString(ctx, "platform_id"));
    }

    private static int teleportToPlatform(CommandSourceStack source, ServerPlayer executor, ServerPlayer target, String platformId) {
        if (!ModerationConfig.ENABLE_PLATFORMS.get()) {
            source.sendFailure(Component.literal("§cPlatforms system is disabled."));
            return 0;
        }
        for (String pData : ModerationConfig.PLATFORMS.get()) {
            String[] parts = pData.split(";");
            if (parts.length >= 6 && parts[0].equals(platformId)) {
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(parts[2]));
                ServerLevel level = source.getServer().getLevel(dim);
                if (level == null) { source.sendFailure(Component.literal("§cDimension not found: " + parts[2])); continue; }
                Vec3 pos = new Vec3(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), Double.parseDouble(parts[5]));
                if (target != null) {
                    target.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    target.sendSystemMessage(Component.literal("§e[Staff] You have been teleported to: " + parts[1]));
                    logToStaff(source, executor.getName().getString() + " TP'd " + target.getName().getString() + " to " + parts[1]);
                } else {
                    executor.teleportTo(level, pos.x, pos.y, pos.z, 0, 0);
                    executor.sendSystemMessage(Component.literal("§aTeleported to: " + parts[1]));
                }
                return 1;
            }
        }
        source.sendFailure(Component.literal("§cPlatform not found: " + platformId));
        return 0;
    }

    private static int setPlatform(CommandContext<CommandSourceStack> ctx) {
        final String platformName = StringArgumentType.getString(ctx, "platform_name");
        final ResourceLocation dimension = ResourceLocationArgument.getId(ctx, "dimension");
        final int x = IntegerArgumentType.getInteger(ctx, "x");
        final int y = IntegerArgumentType.getInteger(ctx, "y");
        final int z = IntegerArgumentType.getInteger(ctx, "z");
        String platformEntry = platformName + ";" + platformName + ";" + dimension + ";" + x + ";" + y + ";" + z;
        List<String> platforms = new ArrayList<>(ModerationConfig.PLATFORMS.get());
        boolean updated = false;
        for (int i = 0; i < platforms.size(); i++) {
            if (platforms.get(i).startsWith(platformName + ";")) {
                platforms.set(i, platformEntry);
                updated = true;
                break;
            }
        }
        if (!updated) platforms.add(platformEntry);
        ModerationConfig.PLATFORMS.set(platforms);
        ModerationConfig.SPEC.save();
        final boolean wasUpdated = updated;
        ctx.getSource().sendSuccess(() -> Component.literal("§a[RpEssentials] Platform '" + platformName + "' " +
                (wasUpdated ? "updated" : "created") + " at " + dimension + " " + x + " " + y + " " + z), true);
        return 1;
    }

    private static int staffBroadcast(CommandContext<CommandSourceStack> ctx) {
        String message = StringArgumentType.getString(ctx, "message");
        String staffName = ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer().getName().getString() : "Console";
        Component formatted = net.rp.rpessentials.ColorHelper.parseColors("§8[STAFF] §e" + staffName + "§7: §f" + message);
        int count = 0;
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p)) { p.sendSystemMessage(formatted); count++; }
        }
        RpEssentials.LOGGER.info("[STAFF-BROADCAST] {}: {}", staffName, message);
        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal("§a[STAFF] Broadcast sent to §e" + finalCount + " §astaff member(s)."), false);
        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        StringBuilder sb = new StringBuilder();
        sb.append(MessagesConfig.get(MessagesConfig.STATS_HEADER)).append("\n");
        sb.append("§6╠═══════════════════════════════════\n");

        int playersWithLicense = LicenseManager.getAllLicenses().size();
        sb.append("§6║ §7Players with profession: §e").append(playersWithLicense).append("\n");

        long activeWarns = WarnManager.getAll().stream().filter(w -> !w.isExpired()).count();
        sb.append("§6║ §7Active warns            : §e").append(activeWarns).append("\n");

        java.time.LocalDate in7Days = java.time.LocalDate.now().plusDays(7);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        long expiringSoon = LicenseManager.getAllTempLicenses().stream().filter(e -> {
            try {
                java.time.LocalDate expiry = java.time.LocalDate.parse(e.expiresAt, fmt);
                return !expiry.isBefore(java.time.LocalDate.now()) && !expiry.isAfter(in7Days);
            } catch (Exception ex) { return false; }
        }).count();
        sb.append("§6║ §7Licenses expiring (7d)  : §e").append(expiringSoon).append("\n");

        long activeMutes = MuteManager.getAllMutes().size();
        sb.append("§6║ §7Active mutes            : §e").append(activeMutes).append("\n");

        long deathCount = DeathRPManager.getAllHistory().size();
        sb.append("§6║ §7Death RP recorded       : §e").append(deathCount).append("\n");

        sb.append("§6╠═══════════════════════════════════\n");
        sb.append("§6║ §7Recent connections:\n");
        var recent = LastConnectionManager.getAllSortedByLogin();
        int shown = 0;
        for (var e : recent) {
            if (shown++ >= 5) break;
            String name = e.getValue().mcName != null ? e.getValue().mcName : e.getKey().toString().substring(0, 8);
            boolean online = server.getPlayerList().getPlayer(e.getKey()) != null;
            String bullet = online ? "§a●" : "§7○";
            sb.append("§6║  ").append(bullet).append(" §e").append(name)
                    .append(" §8— §7").append(e.getValue().lastLogin != null ? e.getValue().lastLogin : "?").append("\n");
        }
        sb.append("§6╚═══════════════════════════════════╝");
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // =========================================================================
    // UTILS
    // =========================================================================

    static void logToStaff(CommandSourceStack source, String msg) {
        if (!ModerationConfig.LOG_TO_STAFF.get()) return;
        Component txt = Component.literal("§7§o[StaffLog] " + msg);
        source.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(txt);
        });
        if (ModerationConfig.LOG_TO_CONSOLE.get()) RpEssentials.LOGGER.info("[StaffLog] " + msg);
    }

    /** Used by multiple command handlers */
    static void broadcastToStaff(MinecraftServer server, String message) {
        if (message == null || message.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RpEssentialsPermissions.isStaff(p)) p.sendSystemMessage(Component.literal(message));
        }
    }

    private static GameType parseGameMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "survival", "s", "0" -> GameType.SURVIVAL;
            case "creative", "c", "1" -> GameType.CREATIVE;
            case "adventure", "a", "2" -> GameType.ADVENTURE;
            case "spectator", "sp", "3" -> GameType.SPECTATOR;
            default -> null;
        };
    }
}
