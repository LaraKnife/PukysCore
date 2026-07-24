package com.pukyscraft.core.protection.events;

import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class ProtectionPlacementEvent {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack heldItem = player.getMainHandItem();

        if (heldItem.hasTag() && heldItem.getTag().contains("PukysProtectionType")) {
            String typeId = heldItem.getTag().getString("PukysProtectionType");

            PukysConfig.ProtectionBlock protType = PukysConfig.protectionBlocks.get(typeId);

            if (protType != null) {
                String currentDimension = player.level().dimension().location().toString();

                Region existingRegion = RegionManager.getRegionAt(event.getPos(), currentDimension);
                if (existingRegion != null) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo puedes colocar una protección dentro de una zona ya protegida."));
                    return;
                }

                Region newRegion = new Region(
                        player.getUUID(),
                        event.getPos(),
                        protType.radius,
                        currentDimension,
                        typeId
                );

                RegionManager.addRegion(newRegion);

                player.sendSystemMessage(Component.literal(
                        "§a¡Has protegido esta zona! (" + protType.radius + " bloques a la redonda)."
                ));
            }
        }
    }
}