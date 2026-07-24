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

                // 1. Creamos la región temporalmente para comprobar colisiones
                Region newRegion = new Region(
                        player.getUUID(),
                        event.getPos(),
                        protType.radiusX,
                        protType.radiusY,
                        protType.radiusZ,
                        currentDimension,
                        typeId
                );

                // 2. Comprobamos si el área de esta nueva región choca con alguna existente
                if (RegionManager.isAreaOverlapping(newRegion)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo puedes colocar tu protección aquí, sus bordes chocan con otra zona cercana."));
                    return;
                }

                RegionManager.addRegion(newRegion);

                player.sendSystemMessage(Component.literal(
                        "§a¡Has protegido esta zona! (" + protType.radiusX + " X " + protType.radiusZ + " bloques a la redonda)."
                ));
            }
        }
    }
}