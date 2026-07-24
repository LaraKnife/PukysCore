package com.pukyscraft.core.auth.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.auth.AuthSessionManager;
import com.pukyscraft.core.auth.SecurityManager;
import com.pukyscraft.core.PukysConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AuthCommands {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        System.out.println("[PukysCore Auth] Inicializando base de datos...");
        AuthDatabase.init();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        System.out.println("[PukysCore Auth] Registrando comandos /register y /login...");
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Comando Register
        dispatcher.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.string())
                        .then(Commands.argument("confirmPassword", StringArgumentType.string())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String ip = SecurityManager.normalizeIp(player.getIpAddress());
                                    String pass1 = StringArgumentType.getString(context, "password");
                                    String pass2 = StringArgumentType.getString(context, "confirmPassword");

                                    if (AuthDatabase.isRegistered(player.getUUID())) {
                                        player.sendSystemMessage(Component.literal("§cYa estás registrado. Usa /login <contraseña>"));
                                        return 0;
                                    }

                                    if (AuthDatabase.getAccountsCountForIp(ip) >= PukysConfig.auth_maxAccountsPerIp.get()) {
                                        player.sendSystemMessage(Component.literal("§cSe ha alcanzado el límite de registros para tu red."));
                                        return 0;
                                    }

                                    // Longitud de la Contraseña
                                    if (pass1.length() < PukysConfig.auth_minPasswordLength.get() || pass1.length() > PukysConfig.auth_maxPasswordLength.get()) {
                                        player.sendSystemMessage(Component.literal("§cLa contraseña debe tener entre " + PukysConfig.auth_minPasswordLength.get() + " y " + PukysConfig.auth_maxPasswordLength.get() + " caracteres."));
                                        return 0;
                                    }

                                    // Caracteres admitidos (mínimo 1 letra y 1 número)
                                    boolean hasLetter = pass1.matches(".*[a-zA-ZñÑáéíóúÁÉÍÓÚ].*");
                                    boolean hasNumber = pass1.matches(".*\\d.*");

                                    if (!hasLetter || !hasNumber) {
                                        player.sendSystemMessage(Component.literal("§cLa contraseña debe contener al menos una letra y un número."));
                                        return 0;
                                    }

                                    if (!pass1.equals(pass2)) {
                                        player.sendSystemMessage(Component.literal("§cLas contraseñas no coinciden."));
                                        return 0;
                                    }

                                    AuthDatabase.registerUser(player.getUUID(), player.getScoreboardName(), pass1, ip);
                                    AuthSessionManager.onPlayerAuth(player);
                                    player.sendSystemMessage(Component.literal("§a¡Registro exitoso!"));
                                    return 1;
                                }))));

        // Comando Login
        dispatcher.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String ip = SecurityManager.normalizeIp(player.getIpAddress());
                            String password = StringArgumentType.getString(context, "password");

                            if (AuthSessionManager.isAuthenticated(player.getUUID())) return 0;

                            if (!AuthDatabase.isRegistered(player.getUUID())) {
                                player.sendSystemMessage(Component.literal("§cNo estás registrado en el servidor. Usa §e/register <contraseña> <contraseña>"));
                                return 0;
                            }

                            if (AuthDatabase.checkPasswordAndUpdateIp(player.getUUID(), password, ip, player.getScoreboardName())) {
                                SecurityManager.clearFailedAttempts(ip);
                                AuthSessionManager.onPlayerAuth(player);
                                player.sendSystemMessage(Component.literal("§a¡Inicio de sesión exitoso!"));
                            } else {
                                player.sendSystemMessage(Component.literal("§cContraseña incorrecta."));
                                SecurityManager.registerFailedAttempt(ip);

                                if (SecurityManager.isIpBanned(ip)) {
                                    player.connection.disconnect(Component.literal("§cDemasiados intentos. IP baneada temporalmente."));
                                }
                            }
                            return 1;
                        })));
    }
}