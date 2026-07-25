package com.pukyscraft.core.protection.events;

import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.permissions.PukysPermissions;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ProtectionPlacementEvent {

    private static boolean isAdmin(Player player) {
        if (!(player instanceof ServerPlayer sp)) return false;
        return PermissionAPI.getPermission(sp, PukysPermissions.ADMIN_COMMANDS) || sp.hasPermissions(2);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return; // Validamos que sea jugador al inicio

        String dimension = player.level().dimension().location().toString();
        boolean hasAdmin = isAdmin(player);

        Region pRegion = RegionManager.getRegionAt(event.getPos(), dimension);
        if (pRegion != null) {
            if (!pRegion.isMemberOrOwner(player.getUUID()) && !hasAdmin) {
                if (!pRegion.getFlag("block_place")) {
                    player.sendSystemMessage(Component.literal("§cEl dueño prohíbe colocar bloques aquí."));
                    event.setCanceled(true);
                    if (player instanceof ServerPlayer sp) sp.inventoryMenu.sendAllDataToRemote();
                    return;
                }
            }
        } else {
            WorldRegion wRegion = RegionManager.getWorldRegionAt(event.getPos(), dimension);
            if (!wRegion.getFlag("block_place") && !hasAdmin) {
                player.sendSystemMessage(Component.literal("§cNo puedes colocar bloques aquí."));
                event.setCanceled(true);
                if (player instanceof ServerPlayer sp) sp.inventoryMenu.sendAllDataToRemote();
                return;
            }
        }

        ItemStack heldItem = player.getMainHandItem();

        if (heldItem.hasTag() && heldItem.getTag().contains("PukysProtectionType")) {
            String typeId = heldItem.getTag().getString("PukysProtectionType");

            PukysConfig.ProtectionBlock protType = PukysConfig.protectionBlocks.get(typeId);

            if (protType != null) {
                Region newRegion = new Region(
                        player.getUUID(),
                        player.getName().getString(),
                        event.getPos(),
                        protType.radiusX,
                        protType.radiusY,
                        protType.radiusZ,
                        dimension,
                        typeId
                );

                // Comprobamos colisiones
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