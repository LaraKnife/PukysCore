package com.pukyscraft.core.protection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File REGIONS_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/regions.json").toFile();

    // Hilo secundario I/O de disco
    private static final ExecutorService ASYNC_IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PukysCore-AsyncSaver");
        thread.setDaemon(true);
        return thread;
    });

    public static List<Region> activeRegions = new ArrayList<>();

    private static volatile Map<String, Map<Long, List<Region>>> spatialIndex = new ConcurrentHashMap<>();

    public static synchronized void loadRegions() {
        if (!REGIONS_FILE.exists()) return;
        try (Reader reader = new FileReader(REGIONS_FILE)) {
            Type listType = new TypeToken<ArrayList<Region>>(){}.getType();
            List<Region> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                activeRegions = loaded;
                rebuildSpatialIndex();
            }
        } catch (Exception e) {
            System.err.println("[PukysCore] Error critico al cargar las regiones.");
            e.printStackTrace();
        }
    }

    public static void saveRegionsAsync() {
        final List<Region> snapshot = new ArrayList<>(activeRegions);

        ASYNC_IO.submit(() -> {
            REGIONS_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(REGIONS_FILE)) {
                GSON.toJson(snapshot, writer);
            } catch (Exception e) {
                System.err.println("[PukysCore] Error al guardar regiones en hilo secundario.");
                e.printStackTrace();
            }
        });
    }

    public static synchronized void addRegion(Region region) {
        activeRegions.add(region);
        indexRegion(region);
        saveRegionsAsync();
    }

    public static synchronized void removeRegion(Region region) {
        activeRegions.remove(region);
        rebuildSpatialIndex();
        saveRegionsAsync();
    }

    public static Region getRegionAt(BlockPos pos, String dimension) {
        Map<Long, List<Region>> dimMap = spatialIndex.get(dimension);
        if (dimMap == null) return null;

        long chunkKey = ChunkPos.asLong(pos);
        List<Region> possibleRegions = dimMap.get(chunkKey);

        if (possibleRegions == null || possibleRegions.isEmpty()) return null;

        for (Region region : possibleRegions) {
            if (region.contains(pos, dimension)) {
                return region;
            }
        }
        return null;
    }

    // Reconstruye el índice de chunks para evitar búsquedas globales
    private static void rebuildSpatialIndex() {
        Map<String, Map<Long, List<Region>>> newIndex = new ConcurrentHashMap<>();

        for (Region region : activeRegions) {
            indexRegionToMap(region, newIndex);
        }

        spatialIndex = newIndex;
    }

    private static void indexRegionToMap(Region region, Map<String, Map<Long, List<Region>>> targetMap) {
        Map<Long, List<Region>> dimMap = targetMap.computeIfAbsent(region.dimension, k -> new ConcurrentHashMap<>());

        int minChunkX = (region.center.getX() - region.radiusX) >> 4;
        int maxChunkX = (region.center.getX() + region.radiusX) >> 4;
        int minChunkZ = (region.center.getZ() - region.radiusZ) >> 4;
        int maxChunkZ = (region.center.getZ() + region.radiusZ) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = ChunkPos.asLong(cx, cz);
                // Prevenir crasheo si se lee y escribe en simultaneo
                dimMap.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(region);
            }
        }
    }

    public static boolean isAreaOverlapping(Region newRegion) {
        for (Region existing : activeRegions) {
            if (existing.overlaps(newRegion)) {
                return true;
            }
        }
        return false;
    }

    private static void indexRegion(Region region) {
        indexRegionToMap(region, spatialIndex);
    }
}