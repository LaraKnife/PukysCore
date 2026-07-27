package com.pukyscraft.core.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class LocationManager {
    private static final File FILE = new File("config/PukysCore/database/locations.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LocationsData data = new LocationsData();

    public static class LocationsData {
        public Location joinLoc;
        public Location spawnLoc;
    }

    public static class Location {
        public double x, y, z;
        public float yaw, pitch;
        public String dimension;

        public Location(double x, double y, double z, float yaw, float pitch, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.dimension = dimension;
        }
    }

    public static void init() {
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    LocationsData loaded = GSON.fromJson(reader, LocationsData.class);
                    if (loaded != null) data = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[PukysCore Auth] Error al cargar locations.json: " + e.getMessage());
        }
    }

    public static void save() {
        final LocationsData dataCopy = data;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            synchronized (LocationManager.class) {
                try {
                    if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();

                    try (FileWriter writer = new FileWriter(FILE)) {
                        GSON.toJson(dataCopy, writer);
                        writer.flush();
                    }
                } catch (Exception e) {
                    System.err.println("[PukysCore Auth] Error al guardar locations.json: " + e.getMessage());
                }
            }
        });
    }

    public static void setJoin(ServerPlayer player) {
        data.joinLoc = new Location(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.level().dimension().location().toString());
        save();
    }

    public static void setSpawn(ServerPlayer player) {
        data.spawnLoc = new Location(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.level().dimension().location().toString());
        save();
    }

    public static void clearJoin() {
        data.joinLoc = null;
        save();
    }

    public static void clearSpawn() {
        data.spawnLoc = null;
        save();
    }

    public static Location getJoin() { return data.joinLoc; }
    public static Location getSpawn() { return data.spawnLoc; }

    public static void teleportTo(ServerPlayer player, Location loc) {
        if (loc == null) return;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(loc.dimension));
        ServerLevel targetLevel = player.server.getLevel(dimKey);
        if (targetLevel != null) {
            player.teleportTo(targetLevel, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
        }
    }
}