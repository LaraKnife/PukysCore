package com.pukyscraft.core.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthDatabase {
    private static final File FILE = new File("config/PukysCore/database/users_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, UserData> users = new ConcurrentHashMap<>();
    private static final Object FILE_LOCK = new Object();

    public static class UserData {
        public String username;
        public String passwordHash;
        public String salt;
        public String lastIp;

        public UserData(String username, String passwordHash, String salt, String lastIp) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.lastIp = lastIp;
        }
    }

    public static void init() {
        try {
            if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    Type type = new TypeToken<ConcurrentHashMap<String, UserData>>(){}.getType();
                    Map<String, UserData> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) users = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[PukysCore Auth] Error al inicializar JSON: " + e.getMessage());
        }
    }

    public static void save() {
        final Map<String, UserData> copy = new HashMap<>(users);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            synchronized (FILE_LOCK) {
                try {
                    if (!FILE.getParentFile().exists()) FILE.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(FILE)) {
                        GSON.toJson(copy, writer);
                        writer.flush();
                    }
                } catch (Exception e) {
                    System.err.println("[PukysCore Auth] Error al guardar database en hilo asíncrono.");
                }
            }
        });
    }

    public static boolean isRegistered(UUID uuid) {
        return users.containsKey(uuid.toString());
    }

    public static int getAccountsCountForIp(String ip) {
        return (int) users.values().stream().filter(u -> ip.equals(u.lastIp)).count();
    }

    // Valida la contraseña y actualiza la IP si es correcta, lanzando la alerta si corresponde
    public static boolean checkPasswordAndUpdateIp(UUID uuid, String password, String currentIp, String username) {
        UserData data = users.get(uuid.toString());
        if (data != null) {
            String hashToVerify = hashPassword(password, data.salt);
            if (hashToVerify.equals(data.passwordHash)) {
                if (SecurityManager.isSignificantIpChange(data.lastIp, currentIp)) {
                    System.out.println("[PukysCore Auth] <ALERTA> El usuario " + username + " se está conectando desde una IP muy distinta. Anterior: " + data.lastIp + " | Actual: " + currentIp);
                }
                data.lastIp = currentIp;
                save();
                return true;
            }
        }
        return false;
    }

    public static void registerUser(UUID uuid, String username, String password, String ip) {
        String salt = generateSalt();
        String hash = hashPassword(password, salt);
        users.put(uuid.toString(), new UserData(username, hash, salt, ip));
        save();
    }

    // Encriptar contraseña
    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean unregisterUserByName(String username) {
        String targetUuid = null;

        for (Map.Entry<String, UserData> entry : users.entrySet()) {
            if (entry.getValue().username.equalsIgnoreCase(username)) {
                targetUuid = entry.getKey();
                break;
            }
        }
        if (targetUuid != null) {
            users.remove(targetUuid);
            save();
            System.out.println("[PukysCore Auth] Un administrador eliminó el registro de: " + username);
            return true;
        }
        return false;
    }
}