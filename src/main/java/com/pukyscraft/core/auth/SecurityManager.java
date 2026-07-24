package com.pukyscraft.core.auth;

import com.pukyscraft.core.PukysConfig;
import java.util.concurrent.ConcurrentHashMap;

public class SecurityManager {
    private static final ConcurrentHashMap<String, Long> ipBans = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private static long lastLoginSecond = 0;
    private static int loginsThisSecond = 0;

    public static boolean isIpBanned(String ip) {
        if (!ipBans.containsKey(ip)) return false;
        long expiration = ipBans.get(ip);
        if (System.currentTimeMillis() > expiration) {
            ipBans.remove(ip);
            return false;
        }
        return true;
    }

    public static void registerFailedAttempt(String ip) {
        int fails = failedAttempts.getOrDefault(ip, 0) + 1;
        int maxAttempts = PukysConfig.auth_maxFailedAttempts.get();
        long banTimeMs = PukysConfig.auth_banTimeMinutes.get() * 60000L; // Minutos a ms

        if (fails >= maxAttempts) {
            ipBans.put(ip, System.currentTimeMillis() + banTimeMs);
            failedAttempts.remove(ip);
            System.out.println("[PukysCore Auth] <ALERTA> IP " + ip + " baneada temporalmente por " + PukysConfig.auth_banTimeMinutes.get() + " minutos.");
        } else {
            failedAttempts.put(ip, fails);
        }
    }

    public static void clearFailedAttempts(String ip) {
        failedAttempts.remove(ip);
    }

    public static boolean isSignificantIpChange(String oldIp, String newIp) {
        if (oldIp == null || oldIp.isEmpty() || oldIp.equals(newIp)) return false;
        try {
            String[] oldParts = oldIp.split("\\.");
            String[] newParts = newIp.split("\\.");
            if (oldParts.length == 4 && newParts.length == 4) {
                return !(oldParts[0].equals(newParts[0]) && oldParts[1].equals(newParts[1]));
            }
        } catch (Exception e) {}
        return true;
    }

    public static boolean allowConnectionThrottled() {
        long currentSecond = System.currentTimeMillis() / 1000;
        if (currentSecond != lastLoginSecond) {
            lastLoginSecond = currentSecond;
            loginsThisSecond = 0;
        }
        loginsThisSecond++;
        return loginsThisSecond <= 4;
    }

    // Conservamos la IPv4
    public static String normalizeIp(String rawIp) {
        if (rawIp == null) return "127.0.0.1";

        if (rawIp.equals("0:0:0:0:0:0:0:1") || rawIp.equals("::1")) {
            return "127.0.0.1";
        }

        String lowerIp = rawIp.toLowerCase();
        if (lowerIp.startsWith("::ffff:") || lowerIp.startsWith("0:0:0:0:0:ffff:")) {
            return rawIp.substring(rawIp.lastIndexOf(':') + 1);
        }

        return rawIp;
    }
}