package com.pukyscraft.core.protection.events;

import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.PukysConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class ProtectionEventHandler {

    private static final Map<UUID, Region> playerRegions = new ConcurrentHashMap<>();

    // Busca el nombre de jugador de forma óptima.
    private static String getSafePlayerName(ServerPlayer player, UUID targetId) {
        ServerPlayer targetPlayer = player.getServer().getPlayerList().getPlayer(targetId);
        if (targetPlayer != null) return targetPlayer.getName().getString();

        return player.getServer().getProfileCache().get(targetId)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse("Desconocido");
    }

    // Mensajes de Entrada y Salida
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient() || event.phase == TickEvent.Phase.END) return;
        if (event.player.tickCount % 10 != 0) return;

        ServerPlayer player = (ServerPlayer) event.player;
        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().location().toString();

        Region currentRegion = RegionManager.getRegionAt(player.blockPosition(), dimension);
        Region lastRegion = playerRegions.get(uuid);

        if (currentRegion != lastRegion) {
            if (lastRegion != null) {
                String ownerName = getSafePlayerName(player, lastRegion.owner);
                player.sendSystemMessage(Component.literal("§eHas salido de la zona de §c" + ownerName));
            }
            if (currentRegion != null) {
                String ownerName = getSafePlayerName(player, currentRegion.owner);
                player.sendSystemMessage(Component.literal("§eHas entrado a la zona de §a" + ownerName));
            }

            if (currentRegion == null) {
                playerRegions.remove(uuid);
            } else {
                playerRegions.put(uuid, currentRegion);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        playerRegions.remove(event.getEntity().getUUID());
    }

    // Prevenir Desync (cliente-server)
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;

        Region region = RegionManager.getRegionAt(event.getPos(), player.level().dimension().location().toString());
        if (region != null && !region.isMemberOrOwner(player.getUUID())) {
            event.setCanceled(true);
            player.inventoryMenu.sendAllDataToRemote();
            player.sendSystemMessage(Component.literal("§cNo puedes construir en la zona de otro jugador."));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        String dimension = player.level().dimension().location().toString();
        BlockPos pos = event.getPos();

        Region region = RegionManager.getRegionAt(pos, dimension);

        if (region != null) {
            if (pos.equals(region.center)) {
                if (region.owner.equals(player.getUUID()) || player.hasPermissions(2)) {
                    event.setCanceled(true);
                    event.getLevel().destroyBlock(pos, false);
                    RegionManager.removeRegion(region);

                    PukysConfig.ProtectionBlock protType = PukysConfig.protectionBlocks.get(region.type);
                    if (protType != null) {
                        String[] parts = protType.material.split(":");
                        ResourceLocation res = parts.length == 2 ? new ResourceLocation(parts[0], parts[1]) : new ResourceLocation("minecraft", protType.material);
                        ItemStack stone = new ItemStack(ForgeRegistries.ITEMS.getValue(res));

                        stone.setHoverName(Component.literal(protType.displayName));
                        CompoundTag nbt = stone.getOrCreateTag();
                        nbt.putString("PukysProtectionType", region.type);
                        stone.setTag(nbt);

                        if (!player.getInventory().add(stone)) player.drop(stone, false);
                    }
                    player.sendSystemMessage(Component.literal("§aZona desprotegida. Bloque recuperado."));
                } else {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo puedes destruir el bloque de protección."));
                }
                return;
            }

            if (!region.isMemberOrOwner(player.getUUID()) && !player.hasPermissions(2)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cNo tienes permiso para romper bloques aquí."));
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;

        Region region = RegionManager.getRegionAt(event.getPos(), player.level().dimension().location().toString());
        if (region != null && !region.isMemberOrOwner(player.getUUID())) {
            event.setCanceled(true);
            player.inventoryMenu.sendAllDataToRemote();
            player.sendSystemMessage(Component.literal("§cNo puedes interactuar con objetos aquí."));
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        String dimension = event.getLevel().dimension().location().toString();
        event.getAffectedBlocks().removeIf(pos -> RegionManager.getRegionAt(pos, dimension) != null);
    }


}