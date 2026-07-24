package com.pukyscraft.core.auth;

import com.pukyscraft.core.PukysConfig;
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

    private static class SessionCache {
        long logoutTime;
        String ip;
        SessionCache(long logoutTime, String ip) {
            this.logoutTime = logoutTime;
            this.ip = ip;
        }
    }

    private static final ConcurrentHashMap<UUID, SessionCache> recentSessions = new ConcurrentHashMap<>();

    public static boolean isAuthenticated(UUID uuid) {
        return sessionMap.getOrDefault(uuid, false);
    }

    public static long getJoinTime(UUID uuid) {
        return joinTimes.getOrDefault(uuid, 0L);
    }

    public static boolean tryResumeSession(ServerPlayer player, String currentIp) {
        UUID uuid = player.getUUID();
        int windowMinutes = PukysConfig.auth_reconnectWindowMinutes.get();

        if (windowMinutes > 0 && recentSessions.containsKey(uuid)) {
            SessionCache cache = recentSessions.get(uuid);
            long elapsed = System.currentTimeMillis() - cache.logoutTime;

            if (elapsed <= windowMinutes * 60000L && cache.ip.equals(currentIp)) {
                recentSessions.remove(uuid);
                sessionMap.put(uuid, true);
                return true; // Sesión restaurada con éxito
            }
            recentSessions.remove(uuid); // IP distinta o tiempo expirado
        }
        return false;
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
        File file = new File(INV_DIR, uuid.toString() + ".dat");
        if (!file.exists()) {
            ListTag invNBT = new ListTag();
            player.getInventory().save(invNBT);
            saveInventoryToDisk(uuid, invNBT);
        }
        player.getInventory().clearContent();

        if (LocationManager.getJoin() != null) {
            LocationManager.teleportTo(player, LocationManager.getJoin());
        }
    }

    public static void onPremiumJoin(ServerPlayer player, boolean isFirstTime) {
        sessionMap.put(player.getUUID(), true);

        // Si es su primera vez en el servidor y hay un join definido, lo mandamos ahí.
        if (isFirstTime && LocationManager.getJoin() != null) {
            LocationManager.teleportTo(player, LocationManager.getJoin());
        } else if (LocationManager.getSpawn() != null) {
            // Verificamos si existe un spawn definido y se respeta.
            LocationManager.teleportTo(player, LocationManager.getSpawn());
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

    public static void onPlayerLeave(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Si estaba logueado, guardamos su IP y momento de salida
        if (isAuthenticated(uuid)) {
            String ip = com.pukyscraft.core.auth.SecurityManager.normalizeIp(player.getIpAddress());
            recentSessions.put(uuid, new SessionCache(System.currentTimeMillis(), ip));
        }

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