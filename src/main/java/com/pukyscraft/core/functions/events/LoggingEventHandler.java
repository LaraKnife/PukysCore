package com.pukyscraft.core.functions.events;

import com.pukyscraft.core.functions.LogManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class LoggingEventHandler {

    @SubscribeEvent
    public static void onCommandExecute(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String command = event.getParseResults().getReader().getString();
            String coords = String.format("[X: %.0f, Y: %.0f, Z: %.0f]", player.getX(), player.getY(), player.getZ());
            String playerName = player.getName().getString();

            LogManager.logCommand(playerName, coords, command);
        }
    }

    @SubscribeEvent
    public static void onInventoryOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            if (!(event.getContainer() instanceof InventoryMenu)) {
                String coords = String.format("[X: %.0f, Y: %.0f, Z: %.0f]", player.getX(), player.getY(), player.getZ());
                String containerType = event.getContainer().getType().toString();
                String playerName = player.getName().getString();

                LogManager.logInventory(playerName, coords, containerType);
            }
        }
    }
}