package com.pukyscraft.core.auth;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSessionManager {
    private static final ConcurrentHashMap<UUID, Boolean> sessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, LocationManager.Location> lastLocations = new ConcurrentHashMap<>();
    private static final File INV_DIR = new File("config/PukysCore/Auth/inventories");

    public static boolean isAuthenticated(UUID uuid) {
        return sessionMap.getOrDefault(uuid, false);
    }

    public static long getJoinTime(UUID uuid) {
        return joinTimes.getOrDefault(uuid, 0L);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        sessionMap.put(uuid, false);
        joinTimes.put(uuid, System.currentTimeMillis());

        lastLocations.put(uuid, new LocationManager.Location(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.level().dimension().location().toString()
        ));

        // Respaldo en disco del inventario
        ListTag invNBT = new ListTag();
        player.getInventory().save(invNBT);
        saveInventoryToDisk(uuid, invNBT);
        player.getInventory().clearContent();

        if (LocationManager.getJoin() != null) {
            LocationManager.teleportTo(player, LocationManager.getJoin());
        }
    }

    public static void onPlayerAuth(ServerPlayer player) {
        UUID uuid = player.getUUID();
        sessionMap.put(uuid, true);
        joinTimes.remove(uuid); // Autenticado, detenemos el temporizador

        // Restaurar inventario desde el disco duro
        ListTag invNBT = loadInventoryFromDisk(uuid);
        if (invNBT != null) {
            player.getInventory().load(invNBT);
        }

        // Eliminar efectos restrictivos de seguridad
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.JUMP);

        // TP a última ubicación o Spawn establecido
        if (LocationManager.getSpawn() != null) {
            LocationManager.teleportTo(player, LocationManager.getSpawn());
        } else if (lastLocations.containsKey(uuid)) {
            LocationManager.teleportTo(player, lastLocations.remove(uuid));
        } else {
            ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                BlockPos spawnPos = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
            }
        }
    }

    public static void removePlayer(UUID uuid) {
        sessionMap.remove(uuid);
        joinTimes.remove(uuid);
        lastLocations.remove(uuid);
    }

    private static void saveInventoryToDisk(UUID uuid, ListTag invNBT) {
        if (!INV_DIR.exists()) INV_DIR.mkdirs();
        try {
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("Inventory", invNBT);
            NbtIo.writeCompressed(wrapper, new File(INV_DIR, uuid.toString() + ".dat"));
        } catch (Exception e) {
            System.err.println("[PukysCore Auth] Error crítico guardando inventario de " + uuid);
        }
    }

    private static ListTag loadInventoryFromDisk(UUID uuid) {
        File file = new File(INV_DIR, uuid.toString() + ".dat");
        if (file.exists()) {
            try {
                CompoundTag wrapper = NbtIo.readCompressed(file);
                file.delete(); // Borrar el respaldo tras restaurarlo exitosamente
                return (ListTag) wrapper.get("Inventory");
            } catch (Exception e) {
                System.err.println("[PukysCore Auth] Error cargando inventario de " + uuid);
            }
        }
        return null;
    }
}