package com.pukyscraft.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.server.permission.PermissionAPI;
import com.pukyscraft.core.permissions.PukysPermissions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = PukysCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PukysConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ======== VALORES DE CONFIGURACIÓN TOML ========
    public static ForgeConfigSpec.IntValue auth_sessionTimeoutMinutes;
    public static ForgeConfigSpec.IntValue auth_minPasswordLength;
    public static ForgeConfigSpec.IntValue auth_maxPasswordLength;
    public static ForgeConfigSpec.IntValue auth_maxAccountsPerIp;
    public static ForgeConfigSpec.BooleanValue enableBlindness;
    public static ForgeConfigSpec.IntValue auth_maxFailedAttempts;
    public static ForgeConfigSpec.IntValue auth_banTimeMinutes;

    public static ForgeConfigSpec.BooleanValue enableTpa;
    public static ForgeConfigSpec.BooleanValue enableTphere;
    public static ForgeConfigSpec.BooleanValue enableBack;
    public static ForgeConfigSpec.IntValue defaultMaxHomes;

    public static ForgeConfigSpec.ConfigValue<List<? extends String>> protectionBlocksList;

    // Permitir lectura/escritura segura durante recargas en vivo
    public static final Map<String, ProtectionBlock> protectionBlocks = new ConcurrentHashMap<>();

    static {
        // --- SECCIÓN AUTENTICACIÓN ---
        BUILDER.comment("=== CONFIGURACION DE AUTENTICACION ===").push("auth");
        auth_sessionTimeoutMinutes = BUILDER.comment("Tiempo en minutos antes de requerir login nuevamente.")
                .defineInRange("sessionTimeoutMinutes", 60, 1, Integer.MAX_VALUE);
        auth_minPasswordLength = BUILDER.comment("Longitud minima de la contraseña")
                .defineInRange("minPasswordLength", 6, 1, 64);
        auth_maxPasswordLength = BUILDER.comment("Longitud maxima de la contraseña")
                .defineInRange("maxPasswordLength", 16, 1, 64);
        auth_maxAccountsPerIp = BUILDER.comment("Limite de registros por IP")
                .defineInRange("maxAccountsPerIp", 3, 1, 100);
        enableBlindness = BUILDER.comment("Aplica el efecto de ceguera mientras no se haya iniciado sesion")
                .define("enableBlindness", true);
        auth_maxFailedAttempts = BUILDER.comment("Intentos fallidos antes de ban")
                .defineInRange("maxFailedAttempts", 5, 1, 100);
        auth_banTimeMinutes = BUILDER.comment("Minutos de ban por intentos fallidos")
                .defineInRange("banTimeMinutes", 15, 1, 10000);
        BUILDER.pop();

        // --- SECCIÓN TELETRANSPORTE ---
        BUILDER.comment("=== CONFIGURACION DE TELETRANSPORTE ===").push("teleport");
        enableTpa = BUILDER.comment("Habilitar comando /tpa").define("enableTpa", true);
        enableTphere = BUILDER.comment("Habilitar comando /tphere").define("enableTphere", true);
        enableBack = BUILDER.comment("Habilitar comando /back").define("enableBack", true);
        defaultMaxHomes = BUILDER.comment("Homes por defecto si LuckPerms no asigna un valor")
                .defineInRange("defaultMaxHomes", 2, 0, 100);
        BUILDER.pop();

        // --- SECCIÓN PROTECCIONES ---
        BUILDER.comment("=== CONFIGURACION DE PROTECCIONES ===").push("protections");
        protectionBlocksList = BUILDER.comment(
                "Configura los bloques de proteccion.",
                "Formato: id,material,radio,tipo,nombre_mostrar"
        ).defineList("blocks",
                List.of(
                        "lapislazuli,minecraft:lapis_block,10,basica,Protección Básica",
                        "esmeralda,minecraft:emerald_block,20,media,Protección Media",
                        "diamante,minecraft:diamond_block,30,avanzada,Protección Avanzada"
                ),
                obj -> obj instanceof String && ((String) obj).split(",").length >= 5
        );
        BUILDER.pop();

        SERVER_SPEC = BUILDER.build();
    }

    // Evento para recargar la configuración
    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            loadProtections();
        }
    }

    public static void loadProtections() {
        protectionBlocks.clear();
        for (String entry : protectionBlocksList.get()) {
            String[] parts = entry.split(",", 5);
            if (parts.length >= 5) {
                try {
                    String id = parts[0].trim().toLowerCase();
                    protectionBlocks.put(id, new ProtectionBlock(parts[1].trim(), Integer.parseInt(parts[2].trim()), parts[3].trim(), parts[4].trim()));
                } catch (NumberFormatException e) {
                    System.err.println("[PukysCore] Error de sintaxis en el bloque de protección: " + entry);
                }
            }
        }
        System.out.println("[PukysCore] Cargados " + protectionBlocks.size() + " bloques de proteccion desde TOML.");
    }

    public static int getMaxHomes(ServerPlayer player) {
        if (player == null) return defaultMaxHomes.get();
        return PermissionAPI.getPermission(player, PukysPermissions.MAX_HOMES);
    }

    public static class ProtectionBlock {
        public String material;
        public int radius;
        public String type;
        public String displayName;

        public ProtectionBlock(String material, int radius, String type, String displayName) {
            this.material = material; this.radius = radius; this.type = type; this.displayName = displayName;
        }
    }
}