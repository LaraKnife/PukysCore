package com.pukyscraft.core.functions.commands;

import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.functions.TeleportManager;
import com.pukyscraft.core.functions.InventoryManager;
import com.pukyscraft.core.permissions.PukysPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.server.permission.PermissionAPI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FunctionsCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Regla universal para comandos de admin
        java.util.function.Predicate<CommandSourceStack> requireAdmin = source -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
            }
            return source.hasPermission(2);
        };

        // Proveedor de sugerencias para los Homes del jugador
        com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> suggestUserHomes = (context, builder) -> {
            ServerPlayer player = context.getSource().getPlayer();
            if (player != null) {
                Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(player.getUUID());
                if (homes != null && !homes.isEmpty()) {
                    return SharedSuggestionProvider.suggest(homes.keySet(), builder);
                }
            }
            return builder.buildFuture();
        };

        // ================= HOMES =================
        dispatcher.register(Commands.literal("sethome")
                .requires(source -> source.hasPermission(0))
                .executes(c -> setHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        dispatcher.register(Commands.literal("home")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestUserHomes)
                        .executes(c -> tpHome(c.getSource(), StringArgumentType.getString(c, "name"))))
        );
        dispatcher.register(Commands.literal("h")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestUserHomes)
                        .executes(c -> tpHome(c.getSource(), StringArgumentType.getString(c, "name"))))
        );
        dispatcher.register(Commands.literal("H")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestUserHomes)
                        .executes(c -> tpHome(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        dispatcher.register(Commands.literal("delhome")
                .requires(source -> source.hasPermission(0))
                .executes(c -> delHome(c.getSource(), "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestUserHomes)
                        .executes(c -> delHome(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        dispatcher.register(Commands.literal("homelist")
                .requires(source -> source.hasPermission(0))
                .executes(c -> listHomes(c.getSource()))
        );

        // ================= WARPS =================
        dispatcher.register(Commands.literal("setwarp")
                .requires(requireAdmin)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setWarp(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        dispatcher.register(Commands.literal("warps")
                .requires(source -> source.hasPermission(0))
                .executes(c -> listWarps(c.getSource()))
        );

        dispatcher.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TeleportManager.serverWarps.keySet(), builder))
                        .requires(source -> source.hasPermission(0))
                        .executes(c -> tpWarp(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        dispatcher.register(Commands.literal("delwarp")
                .requires(requireAdmin)
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TeleportManager.serverWarps.keySet(), builder))
                        .executes(c -> delWarp(c.getSource(), StringArgumentType.getString(c, "name"))))
        );

        // ================= TPA =================
        dispatcher.register(Commands.literal("tpa")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> sendTpa(c.getSource(), EntityArgument.getPlayer(c, "target"), false)))
        );

        dispatcher.register(Commands.literal("tphere")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> sendTpa(c.getSource(), EntityArgument.getPlayer(c, "target"), true)))
        );

        dispatcher.register(Commands.literal("accept")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true))
        );
        dispatcher.register(Commands.literal("y")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true))
        );
        dispatcher.register(Commands.literal("s")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true))
        );
        dispatcher.register(Commands.literal("Y")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true))
        );
        dispatcher.register(Commands.literal("S")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), true))
        );

        dispatcher.register(Commands.literal("deny")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), false))
        );
        dispatcher.register(Commands.literal("n")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), false))
        );
        dispatcher.register(Commands.literal("N")
                .requires(source -> source.hasPermission(0))
                .executes(c -> handleTpaResponse(c.getSource(), false))
        );

        dispatcher.register(Commands.literal("tpall")
                .requires(requireAdmin)
                .executes(c -> tpAll(c.getSource()))
        );

        // ================= BACK =================
        dispatcher.register(Commands.literal("back")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpBack(c.getSource(), c.getSource().getPlayerOrException(), false))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(requireAdmin)
                        .executes(c -> tpBack(c.getSource(), EntityArgument.getPlayer(c, "target"), false)))
        );

        dispatcher.register(Commands.literal("backondeath")
                .requires(source -> source.hasPermission(0))
                .executes(c -> tpBack(c.getSource(), c.getSource().getPlayerOrException(), true))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(requireAdmin)
                        .executes(c -> tpBack(c.getSource(), EntityArgument.getPlayer(c, "target"), true)))
        );

        // ================= TPOFFLINE =================
        dispatcher.register(Commands.literal("tpoffline")
                .requires(requireAdmin)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            String target = StringArgumentType.getString(context, "player").toLowerCase();
                            TeleportManager.LocationData loc = TeleportManager.logoutLocations.get(target);

                            if (loc != null) {
                                TeleportManager.teleportPlayer(context.getSource().getPlayerOrException(), loc);
                                context.getSource().sendSuccess(() -> Component.literal("§aTeletransportado a la última ubicación de " + target), true);
                            } else {
                                context.getSource().sendFailure(Component.literal("§cNo se encontró registro de desconexión para " + target));
                            }
                            return 1;
                        }))
        );

        // ================= HOMEOTHERS =================
        dispatcher.register(Commands.literal("homesother")
                .requires(requireAdmin)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            String targetName = StringArgumentType.getString(context, "player");
                            UUID targetUuid = getOfflineUUID(context.getSource(), targetName);

                            if (targetUuid == null) {
                                context.getSource().sendFailure(Component.literal("§cJugador no encontrado en la caché del servidor."));
                                return 0;
                            }

                            Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(targetUuid);
                            if (homes == null || homes.isEmpty()) {
                                context.getSource().sendFailure(Component.literal("§c" + targetName + " no tiene ningún home registrado."));
                            } else {
                                context.getSource().sendSuccess(() -> Component.literal("§eHomes de " + targetName + ": §f" + String.join(", ", homes.keySet())), false);
                            }
                            return 1;
                        })
                        .then(Commands.argument("home", StringArgumentType.word())
                                .executes(context -> {
                                    String targetName = StringArgumentType.getString(context, "player");
                                    String homeName = StringArgumentType.getString(context, "home");
                                    UUID targetUuid = getOfflineUUID(context.getSource(), targetName);

                                    if (targetUuid == null) {
                                        context.getSource().sendFailure(Component.literal("§cJugador no encontrado."));
                                        return 0;
                                    }

                                    Map<String, TeleportManager.LocationData> homes = TeleportManager.userHomes.get(targetUuid);
                                    if (homes != null && homes.containsKey(homeName)) {
                                        TeleportManager.teleportPlayer(context.getSource().getPlayerOrException(), homes.get(homeName));
                                        context.getSource().sendSuccess(() -> Component.literal("§aTeletransportado al home '" + homeName + "' de " + targetName), true);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("§cEl home '" + homeName + "' no existe para este jugador."));
                                    }
                                    return 1;
                                })))
        );

        // ================= INVSEE =================
        dispatcher.register(Commands.literal("invsee")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            ServerPlayer admin = context.getSource().getPlayerOrException();
                            InventoryManager.openOnlineInv(admin, target);
                            return 1;
                        }))
        );

        dispatcher.register(Commands.literal("invoffline")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            String targetName = StringArgumentType.getString(context, "player");
                            ServerPlayer admin = context.getSource().getPlayerOrException();
                            UUID targetUuid = getOfflineUUID(context.getSource(), targetName);

                            if (targetUuid == null) {
                                context.getSource().sendFailure(Component.literal("§cJugador no encontrado en el registro."));
                                return 0;
                            }

                            if (context.getSource().getServer().getPlayerList().getPlayer(targetUuid) != null) {
                                context.getSource().sendFailure(Component.literal("§cEl jugador está online. Usa /invsee en su lugar."));
                                return 0;
                            }

                            boolean success = InventoryManager.openOfflineInv(admin, targetUuid, targetName);
                            if (!success) {
                                context.getSource().sendFailure(Component.literal("§cNo se pudo encontrar el archivo de inventario de " + targetName));
                            }
                            return 1;
                        }))
        );

        // ================= HEAL =================
        dispatcher.register(Commands.literal("heal")
                .requires(requireAdmin)
                .executes(c -> healPlayer(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> healPlayer(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        // ================= REPAIR =================
        dispatcher.register(Commands.literal("repair")
                .requires(requireAdmin)
                .executes(c -> repairHand(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> repairHand(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        dispatcher.register(Commands.literal("repairall")
                .requires(requireAdmin)
                .executes(c -> repairAll(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> repairAll(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        // ================= FLY =================
        dispatcher.register(Commands.literal("fly")
                .requires(requireAdmin)
                .executes(c -> toggleFly(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> toggleFly(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        // ================= ENDERCHEST (/ec) =================
        dispatcher.register(Commands.literal("ec")
                .requires(source -> source.hasPermission(0))
                .executes(c -> openEnderChest(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> openEnderChest(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );
        dispatcher.register(Commands.literal("ender")
                .requires(source -> source.hasPermission(0))
                .executes(c -> openEnderChest(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> openEnderChest(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        // ================= SMITE =================
        dispatcher.register(Commands.literal("rayo")
                .requires(requireAdmin)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> smitePlayer(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );
        dispatcher.register(Commands.literal("smite")
                .requires(requireAdmin)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> smitePlayer(c.getSource(), EntityArgument.getPlayer(c, "target"))))
        );

        // ================= HAT =================
        dispatcher.register(Commands.literal("hat")
                .requires(source -> source.hasPermission(0)) // O 'requireAdmin' si prefieres que sea VIP/Admin
                .executes(c -> putHat(c.getSource())));

        // ================= PING =================
        dispatcher.register(Commands.literal("ping")
                .requires(source -> source.hasPermission(0))
                .executes(c -> checkPing(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(requireAdmin)
                        .executes(c -> checkPing(c.getSource(), EntityArgument.getPlayer(c, "target")))));

        // ================= TOP =================
        dispatcher.register(Commands.literal("top")
                .requires(requireAdmin)
                .executes(c -> tpTop(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> tpTop(c.getSource(), EntityArgument.getPlayer(c, "target")))));
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

    private static int tpBack(CommandSourceStack source, ServerPlayer target, boolean isDeath) {
        TeleportManager.LocationData loc = isDeath ? TeleportManager.deathLocations.get(target.getUUID()) : TeleportManager.backLocations.get(target.getUUID());

        if (loc == null) {
            String msg = isDeath ? "§cNo hay registro de la última muerte de " + target.getName().getString() + "."
                    : "§cNo hay ubicación previa guardada para " + target.getName().getString() + ".";
            source.sendFailure(Component.literal(msg));
            return 0;
        }

        TeleportManager.teleportPlayer(target, loc);

        if (source.getPlayer() != null && source.getPlayer().equals(target)) {
            source.sendSuccess(() -> Component.literal("§aTeletransportado a tu ubicación previa."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§aHas enviado a " + target.getName().getString() + " a su ubicación previa."), true);
            target.sendSystemMessage(Component.literal("§eUn administrador te ha devuelto a tu ubicación anterior."));
        }
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

    private static UUID getOfflineUUID(CommandSourceStack source, String playerName) {
        UUID authUuid = AuthDatabase.getUUIDByName(playerName);
        if (authUuid != null) {
            return authUuid;
        }
        Optional<com.mojang.authlib.GameProfile> profile = source.getServer().getProfileCache().get(playerName);
        return profile.map(com.mojang.authlib.GameProfile::getId).orElse(null);
    }

    private static int healPlayer(CommandSourceStack source, ServerPlayer target) {
        target.setHealth(target.getMaxHealth());
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(20.0F);
        target.removeAllEffects();
        target.clearFire();

        source.sendSuccess(() -> Component.literal("§aHas curado a " + target.getName().getString()), true);
        if (!source.getPlayer().equals(target)) {
            target.sendSystemMessage(Component.literal("§aHas sido curado completamente por un Administrador."));
        }
        return 1;
    }

    private static int repairHand(CommandSourceStack source, ServerPlayer target) {
        net.minecraft.world.item.ItemStack stack = target.getMainHandItem();
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            source.sendFailure(Component.literal("§c" + target.getName().getString() + " no sostiene un objeto reparable en su mano principal."));
            return 0;
        }
        stack.setDamageValue(0);
        source.sendSuccess(() -> Component.literal("§aObjeto reparado para " + target.getName().getString()), true);
        return 1;
    }

    private static int repairAll(CommandSourceStack source, ServerPlayer target) {
        boolean repairedAny = false;
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = target.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.isDamaged()) {
                stack.setDamageValue(0);
                repairedAny = true;
            }
        }

        if (repairedAny) {
            source.sendSuccess(() -> Component.literal("§aTodos los objetos de " + target.getName().getString() + " han sido reparados."), true);
        } else {
            source.sendFailure(Component.literal("§cNo había ningún objeto dañado en el inventario de " + target.getName().getString() + "."));
            return 0;
        }
        return 1;
    }

    private static int toggleFly(CommandSourceStack source, ServerPlayer target) {
        target.getAbilities().mayfly = !target.getAbilities().mayfly;
        if (!target.getAbilities().mayfly) {
            target.getAbilities().flying = false;
        }
        target.onUpdateAbilities();

        String state = target.getAbilities().mayfly ? "§aActivado" : "§cDesactivado";
        source.sendSuccess(() -> Component.literal("§eModo de vuelo para " + target.getName().getString() + ": " + state), true);
        if (!source.getPlayer().equals(target)) {
            target.sendSystemMessage(Component.literal("§eUn administrador ha " + state + " §etu modo de vuelo."));
        }
        return 1;
    }

    private static int openEnderChest(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer admin = source.getPlayer();
        if (admin == null) return 0;

        admin.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, target.getEnderChestInventory()),
                Component.literal("§5Ender Chest: " + target.getName().getString())
        ));
        return 1;
    }

    private static int smitePlayer(CommandSourceStack source, ServerPlayer target) {
        net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(target.level());
        if (lightning != null) {
            lightning.moveTo(target.position());
            lightning.setVisualOnly(true);
            target.level().addFreshEntity(lightning);

            source.sendSuccess(() -> Component.literal("§e¡Has asustado a " + target.getName().getString() + " con un rayo!"), true);
        }
        return 1;
    }

    private static int putHat(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        net.minecraft.world.item.ItemStack handItem = player.getMainHandItem();
        net.minecraft.world.item.ItemStack headItem = player.getInventory().armor.get(3); // Slot de la cabeza

        if (handItem.isEmpty()) {
            source.sendFailure(Component.literal("§cNo tienes nada en la mano para ponerte de sombrero."));
            return 0;
        }

        player.getInventory().armor.set(3, handItem.copy());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, headItem.copy());

        source.sendSuccess(() -> Component.literal("§a¡Bonito sombrero!"), false);
        return 1;
    }

    private static int checkPing(CommandSourceStack source, ServerPlayer target) {
        int ping = target.latency;
        String color = ping < 100 ? "§a" : (ping < 200 ? "§e" : "§c");

        source.sendSuccess(() -> Component.literal("§7El ping de §f" + target.getName().getString() + " §7es: " + color + ping + "ms"), false);
        return 1;
    }

    private static int tpTop(CommandSourceStack source, ServerPlayer target) {
        net.minecraft.server.level.ServerLevel level = target.serverLevel();
        net.minecraft.core.BlockPos pos = target.blockPosition();

        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());

        if (target.getY() >= topY) {
            source.sendFailure(Component.literal("§c" + target.getName().getString() + " ya se encuentra en la superficie."));
            return 0;
        }

        target.teleportTo(target.getX(), topY + 0.1, target.getZ());
        source.sendSuccess(() -> Component.literal("§aTeletransportado a la superficie."), true);
        return 1;
    }
}