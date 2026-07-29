package com.pukyscraft.core.functions.events;

import com.pukyscraft.core.functions.InventoryManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class AdminEventHandler {
    @SubscribeEvent
    public static void onInventoryClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer admin) {
            InventoryManager.saveOfflineInv(admin.getUUID());
        }
    }
}