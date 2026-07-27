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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File HOMES_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/homes.json").toFile();
    private static final File WARPS_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/warps.json").toFile();

    public static final Map<UUID, Map<String, LocationData>> userHomes = new ConcurrentHashMap<>();
    public static final Map<String, LocationData> serverWarps = new ConcurrentHashMap<>();
    public static final Map<UUID, LocationData> backLocations = new ConcurrentHashMap<>();
    public static final Map<UUID, LocationData> deathLocations = new ConcurrentHashMap<>();
    public static final Map<UUID, TpaRequest> pendingTpa = new ConcurrentHashMap<>();

    public static class LocationData {
        public double x, y, z;
        public float yaw, pitch;
        public String dimension;

        public LocationData(double x, double y, double z, float yaw, float pitch, String dimension) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch; this.dimension = dimension;
        }
    }

    public static class TpaRequest {
        public UUID sender;
        public long timestamp;
        public boolean isHere;

        public TpaRequest(UUID sender, boolean isHere) {
            this.sender = sender;
            this.timestamp = System.currentTimeMillis();
            this.isHere = isHere;
        }
        public boolean isExpired() { return (System.currentTimeMillis() - timestamp) > 30000; }
    }

    public static boolean teleportPlayer(ServerPlayer player, LocationData loc) {
        backLocations.put(player.getUUID(), new LocationData(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                player.level().dimension().location().toString()
        ));

        deathLocations.remove(player.getUUID());

        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(loc.dimension));
        ServerLevel targetLevel = player.server.getLevel(key);

        if (targetLevel != null) {
            player.teleportTo(targetLevel, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
            return true;
        }
        return false;
    }

    public static void loadAll() {
        try {
            if (HOMES_FILE.exists()) {
                Type homeType = new TypeToken<Map<UUID, Map<String, LocationData>>>(){}.getType();
                try (Reader reader = new FileReader(HOMES_FILE)) { userHomes.putAll(GSON.fromJson(reader, homeType)); }
            }
            if (WARPS_FILE.exists()) {
                Type warpType = new TypeToken<Map<String, LocationData>>(){}.getType();
                try (Reader reader = new FileReader(WARPS_FILE)) { serverWarps.putAll(GSON.fromJson(reader, warpType)); }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Guardado unificado con CompletableFuture como en el Auth
    public static void saveHomesAsync() {
        final Map<UUID, Map<String, LocationData>> copy = new HashMap<>(userHomes);
        CompletableFuture.runAsync(() -> {
            try {
                if (!HOMES_FILE.getParentFile().exists()) HOMES_FILE.getParentFile().mkdirs();
                try (Writer writer = new FileWriter(HOMES_FILE)) { GSON.toJson(copy, writer); }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public static void saveWarpsAsync() {
        final Map<String, LocationData> copy = new HashMap<>(serverWarps);
        CompletableFuture.runAsync(() -> {
            try {
                if (!WARPS_FILE.getParentFile().exists()) WARPS_FILE.getParentFile().mkdirs();
                try (Writer writer = new FileWriter(WARPS_FILE)) { GSON.toJson(copy, writer); }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}