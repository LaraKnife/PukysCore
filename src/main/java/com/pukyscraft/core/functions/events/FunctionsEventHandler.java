package com.pukyscraft.core.functions.events;

import com.pukyscraft.core.functions.TeleportManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class FunctionsEventHandler {

    // Registra la ubicación exacta cuando un jugador muere
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportManager.deathLocations.put(player.getUUID(), new TeleportManager.LocationData(
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                    player.level().dimension().location().toString()
            ));
        }
    }

    // Limpia la caché temporal para evitar abusos y fugas de RAM
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID uuid = event.getEntity().getUUID();
        TeleportManager.pendingTpa.remove(uuid);
        TeleportManager.backLocations.remove(uuid);
        TeleportManager.deathLocations.remove(uuid);
    }
}