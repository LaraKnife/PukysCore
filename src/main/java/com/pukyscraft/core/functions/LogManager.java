package com.pukyscraft.core.functions;

import net.minecraftforge.fml.loading.FMLPaths;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void logCommand(String playerName, String coords, String command) {
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] %s %s -> %s", time, playerName, coords, command);

        // Guardará en: logs/PukysCore/commands/commands_****-**-**.log
        writeToFile("commands/commands_" + date + ".log", message);
    }

    public static void logInventory(String playerName, String coords, String containerType) {
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] %s abrió %s en %s", time, playerName, containerType, coords);

        // Guardará en: logs/PukysCore/inventories/inv_****-**-**.log
        writeToFile("inventories/inv_" + date + ".log", message);
    }

    private static void writeToFile(String relativePath, String message) {
        EXECUTOR.execute(() -> {
            File logFile = FMLPaths.GAMEDIR.get().resolve("logs/PukysCore/" + relativePath).toFile();
            try {
                if (!logFile.getParentFile().exists()) {
                    logFile.getParentFile().mkdirs();
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                    writer.write(message);
                    writer.newLine();
                }
            } catch (IOException e) {
                System.err.println("[PukysCore Logs] Error al escribir en disco: " + e.getMessage());
            }
        });
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}