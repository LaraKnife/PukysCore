package com.pukyscraft.core.mixin;

import com.pukyscraft.core.auth.PremiumCache;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow @Final public Connection connection;

    @Shadow public abstract void handleHello(ServerboundHelloPacket packet);

    private boolean pukyscore$isAsyncChecked = false;

    @Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
    private void pukyscore$onHandleHelloAsync(ServerboundHelloPacket packet, CallbackInfo ci) {
        String playerName = packet.name();

        Boolean cachedStatus = PremiumCache.get(playerName);

        if (cachedStatus != null || pukyscore$isAsyncChecked) {
            return;
        }

        ci.cancel();
        pukyscore$isAsyncChecked = true;

        CompletableFuture.runAsync(() -> {
            boolean isPremium = checkMojangAPI(playerName);

            PremiumCache.put(playerName, isPremium);

            System.out.println("==================================================");
            System.out.println("[PukysCore Auth] Nuevo jugador procesado: " + playerName + " | Premium: " + isPremium);
            System.out.println("==================================================");

            if (this.connection == null || !this.connection.isConnected()) {
                return;
            }

            this.handleHello(packet);
        });
    }

    @Redirect(
            method = "handleHello",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z")
    )
    private boolean pukyscore$bypassAuth(MinecraftServer server, ServerboundHelloPacket packet) {
        Boolean isPremium = PremiumCache.get(packet.name());

        if (isPremium != null && isPremium) {
            return true;
        }

        return false;
    }

    private boolean checkMojangAPI(String playerName) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);

            int code = connection.getResponseCode();

            if (code == 200) {
                PremiumCache.put(playerName, true);
                return true;
            } else if (code == 204) {
                PremiumCache.put(playerName, false);
                return false;
            } else if (code == 429) {
                System.err.println("[PukysCore Auth] Rate limit de Mojang (HTTP 429) alcanzado al verificar a " + playerName);
                return false;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}