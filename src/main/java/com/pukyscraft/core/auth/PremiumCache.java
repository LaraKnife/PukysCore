package com.pukyscraft.core.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class PremiumCache {
    private static final File FILE = new File("config/PukysCore/database/premium_cache.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Boolean> cache = new ConcurrentHashMap<>();
    private static final Object FILE_LOCK = new Object();

    public static void init() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    Type type = new TypeToken<ConcurrentHashMap<String, Boolean>>(){}.getType();
                    Map<String, Boolean> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) cache = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[PukysCore Auth] Error al inicializar Premium Cache: " + e.getMessage());
        }
    }

    public static void save() {
        final Map<String, Boolean> copy = new ConcurrentHashMap<>(cache);

        CompletableFuture.runAsync(() -> {
            synchronized (FILE_LOCK) {
                try {
                    if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(FILE)) {
                        GSON.toJson(copy, writer);
                        writer.flush();
                    }
                } catch (Exception e) {
                    System.err.println("[PukysCore Auth] Error al guardar Premium Cache en disco.");
                }
            }
        });
    }

    public static Boolean get(String username) {
        return cache.get(username.toLowerCase());
    }

    public static void put(String username, boolean isPremium) {
        cache.put(username.toLowerCase(), isPremium);
        save();
    }
}