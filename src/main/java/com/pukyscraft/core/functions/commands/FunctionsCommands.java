package com.pukyscraft.core.functions.commands;

import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.functions.TeleportManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pukyscraft.core.permissions.PukysPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.server.permission.PermissionAPI;
import java.util.HashMap;
import java.util.Map;

public class FunctionsCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Creamos la regla universal para comandos de admin
        java.util.function.Predicate<CommandSourceStack> requireAdmin = source -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
            }
            return source.hasPermission(2);
        };

        // ================= HOMES =================
        dispatcher.register(Commands.literal("sethome")
                .requires(source -> source.hasPermission(0))
                .executes(c -> setHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c.getSource(), StringArgumentType.getString(c, "name")))));

        dispatcher.register(Commands.literal("home")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> tpHome(c.getSource(), StringArgumentType.getString(c, "name")))));
        dispatcher.register(Commands.literal("h")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word()).executes(c -> tpHome(c.getSource(), StringArgumentType.getString(c, "name")))));

        dispatcher.register(Commands.literal("delhome")
                .requires(source -> source.hasPermission(0))
                .executes(c -> delHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> delHome(c.getSource(), StringArgumentType.getString(c, "name")))));

        dispatcher.register(Commands.literal("homelist")
                .requires(source -> source.hasPermission(0))
                .executes(c -> listHomes(c.getSource())));

        // ================= WARPS =================
        dispatcher.register(Commands.literal("setwarp")
                .requires(requireAdmin)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setWarp(c.getSource(), StringArgumentType.getString(c, "name")))));

        dispatcher.register(Commands.literal("warps")
                .requires(source -> source.hasPermission(0))
                .executes(c -> listWarps(c.getSource())));

        dispatcher.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TeleportManager.serverWarps.keySet(), builder))
                        .requires(source -> source.hasPermission(0))
                        .executes(c -> tpWarp(c.getSource(), StringArgumentType.getString(c, "name")))));

        dispatcher.register(Commands.literal("delwarp")
                .requires(requireAdmin)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TeleportManager.serverWarps.keySet(), builder))
                        .executes(c -> delWarp(c.getSource(), StringArgumentType.getString(c, "name")))));

        // ================= TPA =================
        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> sendTpa(c.getSource(), EntityArgument.getPlayer(c, "target"), false))));

        dispatcher.register(Commands.literal("tphere")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> sendTpa(c.getSource(), EntityArgument.getPlayer(c, "target"), true))));

        dispatcher.register(Commands.literal("accept")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true)));
        dispatcher.register(Commands.literal("y")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true)));
        dispatcher.register(Commands.literal("s")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true)));

        dispatcher.register(Commands.literal("deny")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), false)));
        dispatcher.register(Commands.literal("n")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), false)));

        dispatcher.register(Commands.literal("tpall")
                .requires(requireAdmin)
                .executes(c -> tpAll(c.getSource())));

        // ================= BACK =================
        dispatcher.register(Commands.literal("back")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpBack(c.getSource(), false)));
        dispatcher.register(Commands.literal("backondeath")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpBack(c.getSource(), true)));
    }

    private static int setHome(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.ConcurrentHashMap<>());

        if (!homes.containsKey(name) && homes.size() >= PukysConfig.getMaxHomes(player)) {
            source.sendFailure(Component.literal("§cHas alcanzado el límite máximo de homes (" + PukysConfig.getMaxHomes(player) + ")."));
            return 0;
        }

        homes.put(name.toLowerCase(), new TeleportManager.LocationData(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.level().dimension().location().toString()
        ));

        TeleportManager.saveHomesAsync();
        source.sendSuccess(() -> Component.literal("§aHome '" + name + "' guardado exitosamente."), false);
        return 1;
    }

    private static int tpHome(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(player.getUUID());
        if (homes == null || !homes.containsKey(name.toLowerCase())) {
            source.sendFailure(Component.literal("§cNo tienes un home llamado '" + name + "'."));
            return 0;
        }

        TeleportManager.teleportPlayer(player, homes.get(name.toLowerCase()));
        source.sendSuccess(() -> Component.literal("§aTeletransportado a '" + name + "'."), false);
        return 1;
    }

    private static int delHome(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(player.getUUID());
        if (homes != null && homes.remove(name.toLowerCase()) != null) {
            TeleportManager.saveHomesAsync();
            source.sendSuccess(() -> Component.literal("§aHome '" + name + "' eliminado."), false);
        } else {
            source.sendFailure(Component.literal("§cNo se encontró el home '" + name + "'."));
        }
        return 1;
    }

    private static int listHomes(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            source.sendFailure(Component.literal("§cNo tienes ningún home guardado."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§eTus homes: §f" + String.join(", ", homes.keySet())), false);
        return 1;
    }

    private static int setWarp(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        TeleportManager.serverWarps.put(name.toLowerCase(), new TeleportManager.LocationData(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.level().dimension().location().toString()
        ));

        TeleportManager.saveWarpsAsync();
        source.sendSuccess(() -> Component.literal("§aWarp '" + name + "' establecido."), true);
        return 1;
    }

    private static int tpWarp(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        TeleportManager.LocationData loc = TeleportManager.serverWarps.get(name.toLowerCase());
        if (loc == null) {
            source.sendFailure(Component.literal("§cEl warp '" + name + "' no existe."));
            return 0;
        }

        TeleportManager.teleportPlayer(player, loc);
        source.sendSuccess(() -> Component.literal("§aTeletransportado al warp '" + name + "'."), false);
        return 1;
    }

    private static int listWarps(CommandSourceStack source) {
        if (TeleportManager.serverWarps.isEmpty()) {
            source.sendFailure(Component.literal("§cNo hay warps definidos en el servidor."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§eWarps disponibles: §f" + String.join(", ", TeleportManager.serverWarps.keySet())), false);
        return 1;
    }

    private static int sendTpa(CommandSourceStack source, ServerPlayer target, boolean isHere) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null || target == null) return 0;

        // Validación: El solicitante no puede ser el objetivo
        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("§cNo puedes enviarte una solicitud de teletransporte a ti mismo."));
            return 0;
        }

        if ((isHere && !PukysConfig.enableTphere.get()) || (!isHere && !PukysConfig.enableTpa.get())) {
            source.sendFailure(Component.literal("§cEste comando está desactivado en la configuración."));
            return 0;
        }

        TeleportManager.pendingTpa.put(target.getUUID(), new TeleportManager.TpaRequest(sender.getUUID(), isHere));

        source.sendSuccess(() -> Component.literal("§aSolicitud enviada a " + target.getName().getString()), false);
        target.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " §ate ha enviado una petición de TP. Usa §b/accept (o /y, /s) §aó §b/deny (o /n) §a(Expira en 30s)"));
        return 1;
    }

    private static int handleTpaResponse(CommandSourceStack source, boolean accept) {
        ServerPlayer receiver = source.getPlayer();
        if (receiver == null) return 0;

        TeleportManager.TpaRequest req = TeleportManager.pendingTpa.remove(receiver.getUUID());
        if (req == null || req.isExpired()) {
            source.sendFailure(Component.literal("§cNo tienes peticiones pendientes o ya expiraron."));
            return 0;
        }

        ServerPlayer sender = receiver.server.getPlayerList().getPlayer(req.sender);
        if (sender == null) {
            source.sendFailure(Component.literal("§cEl jugador que envió la petición ya no está online."));
            return 0;
        }

        if (accept) {
            if (req.isHere) {
                TeleportManager.teleportPlayer(receiver, new TeleportManager.LocationData(
                        sender.getX(), sender.getY(), sender.getZ(), sender.getYRot(), sender.getXRot(),
                        sender.level().dimension().location().toString())
                );
            } else {
                TeleportManager.teleportPlayer(sender, new TeleportManager.LocationData(
                        receiver.getX(), receiver.getY(), receiver.getZ(), receiver.getYRot(), receiver.getXRot(),
                        receiver.level().dimension().location().toString())
                );
            }
            sender.sendSystemMessage(Component.literal("§a" + receiver.getName().getString() + " aceptó tu petición."));
            source.sendSuccess(() -> Component.literal("§aPetición aceptada."), false);
        } else {
            sender.sendSystemMessage(Component.literal("§c" + receiver.getName().getString() + " denegó tu petición."));
            source.sendSuccess(() -> Component.literal("§cPetición denegada."), false);
        }
        return 1;
    }

    private static int tpAll(CommandSourceStack source) {
        ServerPlayer executor = source.getPlayer();
        if (executor == null) return 0;

        TeleportManager.LocationData dest = new TeleportManager.LocationData(
                executor.getX(), executor.getY(), executor.getZ(), executor.getYRot(), executor.getXRot(),
                executor.level().dimension().location().toString()
        );

        for (ServerPlayer p : executor.server.getPlayerList().getPlayers()) {
            if (!p.getUUID().equals(executor.getUUID())) {
                TeleportManager.teleportPlayer(p, dest);
                p.sendSystemMessage(Component.literal("§eHas sido teletransportado por un Administrador."));
            }
        }
        source.sendSuccess(() -> Component.literal("§aTodos los jugadores han sido traídos a ti."), true);
        return 1;
    }

    private static int tpBack(CommandSourceStack source, boolean isDeath) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        TeleportManager.LocationData loc = isDeath ? TeleportManager.deathLocations.get(player.getUUID()) : TeleportManager.backLocations.get(player.getUUID());

        if (loc == null) {
            source.sendFailure(Component.literal(isDeath ? "§cNo hay registro de tu última muerte (o fue anulado por un TP reciente)." : "§cNo tienes una ubicación previa guardada."));
            return 0;
        }

        TeleportManager.teleportPlayer(player, loc);
        source.sendSuccess(() -> Component.literal("§aTeletransportado a tu ubicación previa."), false);
        return 1;
    }

    private static int delWarp(CommandSourceStack source, String name) {
        if (TeleportManager.serverWarps.remove(name.toLowerCase()) != null) {
            TeleportManager.saveWarpsAsync();
            source.sendSuccess(() -> Component.literal("§aWarp '" + name + "' eliminado correctamente."), true);
        } else {
            source.sendFailure(Component.literal("§cNo existe un warp con el nombre '" + name + "'."));
        }
        return 1;
    }
}