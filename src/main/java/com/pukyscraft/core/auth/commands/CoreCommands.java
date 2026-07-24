package com.pukyscraft.core.auth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.auth.LocationManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import com.pukyscraft.core.permissions.PukysPermissions;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CoreCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Creamos la regla universal para tus comandos de admin
        java.util.function.Predicate<CommandSourceStack> requireAdmin = source -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
            }
            return source.hasPermission(2); // Para la consola
        };

        // Bloqueamos el comando raíz y quitamos los requires internos redundantes
        dispatcher.register(Commands.literal("pc").requires(requireAdmin)
                .then(Commands.literal("set")
                        .then(Commands.literal("join").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            LocationManager.setJoin(player);
                            player.sendSystemMessage(Component.literal("§aUbicación de JOIN (Pre-Login) establecida."));
                            return 1;
                        }))
                        .then(Commands.literal("spawn").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            LocationManager.setSpawn(player);
                            player.sendSystemMessage(Component.literal("§aUbicación de SPAWN (Post-Login) establecida."));
                            return 1;
                        }))
                )
                .then(Commands.literal("unregister")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .executes(context -> {
                                    String targetName = StringArgumentType.getString(context, "target");
                                    boolean removed = AuthDatabase.unregisterUserByName(targetName);
                                    if (removed) {
                                        context.getSource().sendSuccess(() -> Component.literal("§aRegistro de §e" + targetName + " §aeliminado."), true);
                                    } else {
                                        context.getSource().sendFailure(Component.literal("§cNo se encontró a " + targetName + "."));
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("unset")
                        .then(Commands.literal("join").executes(context -> {
                            LocationManager.clearJoin();
                            context.getSource().sendSuccess(() -> Component.literal("§aUbicación de JOIN (Pre-Login) eliminada."), true);
                            return 1;
                        }))
                        .then(Commands.literal("spawn").executes(context -> {
                            LocationManager.clearSpawn();
                            context.getSource().sendSuccess(() -> Component.literal("§aUbicación de SPAWN (Post-Login) eliminada."), true);
                            return 1;
                        }))
                )
        );
    }
}