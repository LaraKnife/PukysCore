package com.pukyscraft.core.protection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- VARIABLES PARA BLOQUES DE PROTECCIÓN ---
    private static final File REGIONS_FILE = FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/regions.json").toFile();

    public static List<Region> activeRegions = new CopyOnWriteArrayList<>();
    private static volatile Map<String, Map<Long, List<Region>>> spatialIndex = new ConcurrentHashMap<>();

    // --- VARIABLES PARA WORLD REGIONS ---
    private static final Map<UUID, PlayerSelection> ACTIVE_SELECTIONS = new HashMap<>();
    private static final Map<String, WorldRegion> WORLD_REGIONS = new HashMap<>();
    private static final Path WORLD_REGIONS_DIR = FMLPaths.CONFIGDIR.get().resolve("PukysCore/Regiones");

    private static final ExecutorService ASYNC_IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PukysCore-AsyncSaver");
        thread.setDaemon(true);
        return thread;
    });

    public static void init() {
        try {
            Files.createDirectories(WORLD_REGIONS_DIR);
            loadAllWorldRegions();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================================
    // LÓGICA DE ZONAS DE ADMIN (WORLD REGIONS)
    // ========================================
    public static PlayerSelection getSelection(UUID playerId) {
        return ACTIVE_SELECTIONS.computeIfAbsent(playerId, k -> new PlayerSelection());
    }

    public static void saveWorldRegion(WorldRegion region) {
        WORLD_REGIONS.put(region.getName().toLowerCase(), region);
        File file = WORLD_REGIONS_DIR.resolve(region.getName().toLowerCase() + ".json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(region, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadAllWorldRegions() {
        File[] files = WORLD_REGIONS_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    WorldRegion region = GSON.fromJson(reader, WorldRegion.class);
                    WORLD_REGIONS.put(region.getName().toLowerCase(), region);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!WORLD_REGIONS.containsKey("__global__")) {
            WorldRegion global = new WorldRegion("__global__", "all", BlockPos.ZERO, BlockPos.ZERO);
            saveWorldRegion(global);
        }
    }

    public static WorldRegion getWorldRegion(String name) {
        return WORLD_REGIONS.get(name.toLowerCase());
    }

    public static WorldRegion getWorldRegionAt(BlockPos pos, String dimension) {
        for (WorldRegion region : WORLD_REGIONS.values()) {
            if (!region.getName().equals("__global__") && region.contains(pos, dimension)) {
                return region;
            }
        }
        return WORLD_REGIONS.get("__global__");
    }

    // ============================================
    // LÓGICA DE BLOQUES DE PROTECCIÓN DE JUGADORES
    // ============================================
    public static synchronized void loadRegions() {
        if (!REGIONS_FILE.exists()) return;
        try (Reader reader = new FileReader(REGIONS_FILE)) {
            Type listType = new TypeToken<CopyOnWriteArrayList<Region>>(){}.getType();
            List<Region> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                activeRegions = loaded;
                rebuildSpatialIndex();
            }
        } catch (Exception e) {
            System.err.println("[PukysCore] Error critico al cargar las regiones.");
        }
    }

    public static void saveRegionsAsync() {
        final List<Region> snapshot = new ArrayList<>(activeRegions);
        ASYNC_IO.submit(() -> {
            REGIONS_FILE.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(REGIONS_FILE)) {
                GSON.toJson(snapshot, writer);
            } catch (Exception e) {
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
            if (region.contains(pos, dimension)) return region;
        }
        return null;
    }

    public static List<Region> getRegionsForPositions(List<BlockPos> positions, String dimension) {
        Set<Region> affectedRegions = new HashSet<>();
        Map<Long, List<Region>> dimMap = spatialIndex.get(dimension);
        if (dimMap == null) return new ArrayList<>();

        Set<Long> processedChunks = new HashSet<>();
        for (BlockPos pos : positions) {
            long chunkKey = ChunkPos.asLong(pos);
            if (processedChunks.add(chunkKey)) { // Solo extrae las regiones del chunk una vez
                List<Region> regionsInChunk = dimMap.get(chunkKey);
                if (regionsInChunk != null) {
                    affectedRegions.addAll(regionsInChunk);
                }
            }
        }
        return new ArrayList<>(affectedRegions);
    }

    public static boolean isAreaOverlapping(Region newRegion) {
        for (Region existing : activeRegions) {
            if (existing.overlaps(newRegion)) return true;
        }
        return false;
    }

    private static void rebuildSpatialIndex() {
        Map<String, Map<Long, List<Region>>> newIndex = new ConcurrentHashMap<>();
        for (Region region : activeRegions) indexRegionToMap(region, newIndex);
        spatialIndex = newIndex;
    }

    private static void indexRegion(Region region) {
        indexRegionToMap(region, spatialIndex);
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
                dimMap.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(region);
            }
        }
    }
}