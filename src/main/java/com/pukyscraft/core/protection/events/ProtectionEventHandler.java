package com.pukyscraft.core.protection.events;

import com.pukyscraft.core.permissions.PukysPermissions;
import com.pukyscraft.core.protection.PlayerSelection;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "pukyscore")
public class ProtectionEventHandler {

    private static final Map<UUID, Region> playerRegions = new ConcurrentHashMap<>();

    // Comprobar permisos de administrador
    private static boolean isAdmin(ServerPlayer player) {
        return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS) || player.hasPermissions(2);
    }

    // Herramienta de selección
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getItemStack().getItem() == Items.STICK && event.getEntity() instanceof ServerPlayer player) {
            if (isAdmin(player)) {
                PlayerSelection sel = RegionManager.getSelection(player.getUUID());
                sel.pos1 = event.getPos();
                player.sendSystemMessage(Component.literal("§aPosición 1 marcada: " + sel.pos1.toShortString()));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean hasAdmin = isAdmin(player);

        // Lógica del palo (Pos2)
        if (event.getItemStack().getItem() == Items.STICK) {
            if (hasAdmin) {
                PlayerSelection sel = RegionManager.getSelection(player.getUUID());
                sel.pos2 = event.getPos();
                player.sendSystemMessage(Component.literal("§dPosición 2 marcada: " + sel.pos2.toShortString()));
                event.setCanceled(true);
                return;
            }
        }

        String dimension = player.level().dimension().location().toString();
        BlockPos pos = event.getPos();

        Region pRegion = RegionManager.getRegionAt(pos, dimension);
        if (pRegion != null) {
            if (!pRegion.isMemberOrOwner(player.getUUID()) && !hasAdmin) {
                if (!pRegion.getFlag("block_interact")) {
                    event.setCanceled(true);
                    player.inventoryMenu.sendAllDataToRemote();
                    player.sendSystemMessage(Component.literal("§cEl dueño ha bloqueado las interacciones aquí."));
                }
            }
        } else {
            WorldRegion wRegion = RegionManager.getWorldRegionAt(pos, dimension);
            if (!wRegion.getFlag("block_interact") && !hasAdmin) {
                event.setCanceled(true);
                player.inventoryMenu.sendAllDataToRemote();
                player.sendSystemMessage(Component.literal("§cNo puedes interactuar con bloques aquí."));
            }
        }
    }

    // Mensajes de Entrada y Salida de zona protegida
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
                player.sendSystemMessage(Component.literal("§eHas salido de la zona de §c" + lastRegion.ownerName));
            }
            if (currentRegion != null) {
                player.sendSystemMessage(Component.literal("§eHas entrado a la zona de §a" + currentRegion.ownerName));
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
        if (isAdmin(player)) return;

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
        boolean hasAdmin = isAdmin(player);

        Region pRegion = RegionManager.getRegionAt(pos, dimension);
        if (pRegion != null) {
            // Lógica para romper el bloque de protección
            if (pos.equals(pRegion.center)) {
                if (pRegion.owner.equals(player.getUUID()) || hasAdmin) {
                    event.setCanceled(true); // Cancelamos el drop natural del bloque

                    event.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    RegionManager.removeRegion(pRegion);

                    PukysConfig.ProtectionBlock protType = PukysConfig.protectionBlocks.get(pRegion.type);
                    if (protType != null) {
                        String[] parts = protType.material.split(":");
                        ResourceLocation res = parts.length == 2 ? new ResourceLocation(parts[0], parts[1]) : new ResourceLocation("minecraft", protType.material);
                        ItemStack protectionBlock = new ItemStack(ForgeRegistries.ITEMS.getValue(res));

                        protectionBlock.setHoverName(Component.literal(protType.displayName));
                        CompoundTag nbt = protectionBlock.getOrCreateTag();
                        nbt.putString("PukysProtectionType", pRegion.type);
                        protectionBlock.setTag(nbt);

                        if (!player.getInventory().add(protectionBlock)) player.drop(protectionBlock, false);
                    }
                    player.sendSystemMessage(Component.literal("§aZona desprotegida. Bloque recuperado."));
                } else {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo puedes destruir el bloque de protección."));
                }
                return;
            }

            // Flag regular de la zona de jugador
            if (!pRegion.isMemberOrOwner(player.getUUID()) && !hasAdmin) {
                if (!pRegion.getFlag("block_break")) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo tienes permiso para romper bloques aquí."));
                }
            }
        } else {
            WorldRegion wRegion = RegionManager.getWorldRegionAt(pos, dimension);
            if (!wRegion.getFlag("block_break") && !hasAdmin) {
                player.sendSystemMessage(Component.literal("§cNo puedes romper bloques aquí."));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        String dimension = event.getEntity().level().dimension().location().toString();

        Region pRegion = RegionManager.getRegionAt(event.getEntity().blockPosition(), dimension);
        WorldRegion wRegion = RegionManager.getWorldRegionAt(event.getEntity().blockPosition(), dimension);

        boolean allowPvp = (pRegion != null) ? pRegion.getFlag("pvp") : wRegion.getFlag("pvp");
        boolean allowFall = (pRegion != null) ? pRegion.getFlag("fall_damage") : wRegion.getFlag("fall_damage");
        boolean allowMobDamage = wRegion.getFlag("mob_damage");

        boolean isVictimPlayer = event.getEntity() instanceof Player;
        boolean isSourcePlayer = event.getSource() != null && event.getSource().getEntity() instanceof Player;
        boolean isSourceMob = event.getSource() != null && event.getSource().getEntity() instanceof net.minecraft.world.entity.Mob;
        boolean isVictimMob = event.getEntity() instanceof net.minecraft.world.entity.Mob;

        if (isVictimPlayer && isSourcePlayer) {
            if (!allowPvp) event.setCanceled(true);
            return;
        }

        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL) && isVictimPlayer) {
            if (!allowFall) event.setCanceled(true);
            return;
        }

        if ((isVictimPlayer && isSourceMob) || (isVictimMob && isSourcePlayer)) {
            if (!allowMobDamage) event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        String dimension = event.getLevel().dimension().location().toString();
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();

        // Obtenemos solo el puñado de regiones que tocan la explosión en lugar de consultar cada bloque
        List<Region> localRegions = RegionManager.getRegionsForPositions(affectedBlocks, dimension);

        affectedBlocks.removeIf(pos -> {
            for (Region pRegion : localRegions) {
                if (pRegion.contains(pos, dimension)) {
                    return !pRegion.getFlag("explosion_damage");
                }
            }
            WorldRegion wRegion = RegionManager.getWorldRegionAt(pos, dimension);
            return !wRegion.getFlag("explosion_damage");
        });
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String dimension = player.level().dimension().location().toString();
        boolean hasAdmin = isAdmin(player);

        // Evaluar Zona Global
        WorldRegion worldRegion = RegionManager.getWorldRegionAt(event.getEntity().blockPosition(), dimension);
        if (!worldRegion.getFlag("entity_interact") && !hasAdmin) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§cNo puedes interactuar con entidades aquí."));
            return;
        }

        // Evaluar Zona de Bloques de protección
        Region region = RegionManager.getRegionAt(event.getEntity().blockPosition(), dimension);
        if (region != null && !region.isMemberOrOwner(player.getUUID()) && !hasAdmin) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("§cNo puedes interactuar con entidades en la zona de otro jugador."));
        }
    }
}