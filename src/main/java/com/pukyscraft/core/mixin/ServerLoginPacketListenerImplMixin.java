package com.pukyscraft.core.mixin;

import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Redirect(
            method = "handleHello",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z")
    )
    private boolean pukyscore$bypassAuth(MinecraftServer server, ServerboundHelloPacket packet) {
        String playerName = packet.name();
        boolean isPremium = false;

        try {
            // Ejecución asíncrona con límite de 800ms para no bloquear Netty
            isPremium = CompletableFuture.supplyAsync(() -> checkMojangAPI(playerName))
                    .get(800, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            System.err.println("[PukysCore Auth] Timeout verificando API para " + playerName + ". Asumiendo No-Premium.");
        }

        System.out.println("==================================================");
        System.out.println("[PukysCore Auth] Conexión entrante: " + playerName);
        System.out.println("[PukysCore Auth] Estado Premium: " + isPremium);
        System.out.println("==================================================");

        if (!isPremium) {
            return false;
        }

        return server.usesAuthentication();
    }

    private boolean checkMojangAPI(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(800);
            connection.setReadTimeout(800);

            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}