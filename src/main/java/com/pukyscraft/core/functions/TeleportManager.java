package com.pukyscraft.core.functions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TeleportManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File HOMES_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/homes.json").toFile();
    private static final File WARPS_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/warps.json").toFile();

    private static final ExecutorService ASYNC_IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PukysCore-FunctionsIO");
        thread.setDaemon(true); return thread;
    });

    public static final Map<UUID, Map<String, LocationData>> userHomes = new ConcurrentHashMap<>();
    public static final Map<String, LocationData> serverWarps = new ConcurrentHashMap<>();
    public static final Map<UUID, LocationData> backLocations = new ConcurrentHashMap<>();
    public static final Map<UUID, LocationData> deathLocations = new ConcurrentHashMap<>();
    public static final Map<UUID, TpaRequest> pendingTpa = new ConcurrentHashMap<>();

    public static class LocationData {
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;

        public LocationData(String dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
    }

    public static class TpaRequest {
        public UUID sender;
        public long timestamp;
        public boolean isHere; // true = tphere, false = tpa

        public TpaRequest(UUID sender, boolean isHere) {
            this.sender = sender;
            this.timestamp = System.currentTimeMillis();
            this.isHere = isHere;
        }
        public boolean isExpired() { return (System.currentTimeMillis() - timestamp) > 30000; } // 30 segundos
    }

    public static boolean teleportPlayer(ServerPlayer player, LocationData loc) {
        // Guardar el back location ANTES de teletransportar
        backLocations.put(player.getUUID(), new LocationData(
                player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
        ));

        // Borrar el deathLocation por seguridad (evitar abusos)
        deathLocations.remove(player.getUUID());

        ResourceLocation dimRes = new ResourceLocation(loc.dimension.split(":")[0], loc.dimension.split(":")[1]);
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, dimRes);
        ServerLevel targetLevel = player.server.getLevel(key);

        if (targetLevel != null) {
            player.teleportTo(targetLevel, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
            return true;
        }
        return false;
    }

    // I/O ASÍNCRONO
    public static void loadAll() {
        try {
            if (HOMES_FILE.exists()) {
                Type homeType = new TypeToken<Map<UUID, Map<String, LocationData>>>(){}.getType();
                try (Reader r = new FileReader(HOMES_FILE)) { userHomes.putAll(GSON.fromJson(r, homeType)); }
            }
            if (WARPS_FILE.exists()) {
                Type warpType = new TypeToken<Map<String, LocationData>>(){}.getType();
                try (Reader r = new FileReader(WARPS_FILE)) { serverWarps.putAll(GSON.fromJson(r, warpType)); }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void saveHomesAsync() {
        final Map<UUID, Map<String, LocationData>> copy = new HashMap<>(userHomes);
        ASYNC_IO.submit(() -> {
            HOMES_FILE.getParentFile().mkdirs();
            try (Writer w = new FileWriter(HOMES_FILE)) { GSON.toJson(copy, w); } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static void saveWarpsAsync() {
        final Map<String, LocationData> copy = new HashMap<>(serverWarps);
        ASYNC_IO.submit(() -> {
            WARPS_FILE.getParentFile().mkdirs();
            try (Writer w = new FileWriter(WARPS_FILE)) { GSON.toJson(copy, w); } catch (Exception e) { e.printStackTrace(); }
        });
    }
}