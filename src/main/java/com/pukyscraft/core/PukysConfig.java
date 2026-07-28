package com.pukyscraft.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
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
import java.io.File;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.server.permission.nodes.PermissionNode;

@Mod.EventBusSubscriber(modid = PukysCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PukysConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ======== VALORES DE CONFIGURACIÓN TOML ========
    public static ForgeConfigSpec.IntValue auth_sessionTimeoutMinutes;
    public static ForgeConfigSpec.IntValue auth_reconnectWindowMinutes;
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

    public static ForgeConfigSpec.BooleanValue returnProtectionBlockOnBreak;

    public static ForgeConfigSpec.ConfigValue<List<? extends String>> protectionBlocksList;

    // Permitir lectura/escritura segura durante recargas en vivo
    public static final Map<String, ProtectionBlock> protectionBlocks = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        // --- SECCIÓN AUTENTICACIÓN ---
        BUILDER.comment("=== CONFIGURACION DE AUTENTICACION ===").push("auth");
        auth_sessionTimeoutMinutes = BUILDER.comment("Tiempo en minutos antes de requerir login nuevamente.")
                .defineInRange("sessionTimeoutMinutes", 60, 1, Integer.MAX_VALUE);
        auth_reconnectWindowMinutes = BUILDER.comment(
                "Minutos en los que un jugador puede reconectarse sin usar /login si su IP es exactamente la misma.",
                "Pon 0 para desactivar esta función."
        ).defineInRange("reconnectWindowMinutes", 10, 0, 1440);
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
        defaultMaxHomes = BUILDER.comment("Homes por defecto")
                .defineInRange("defaultMaxHomes", 2, 0, 100);
        BUILDER.pop();

        // --- SECCIÓN PROTECCIONES ---
        BUILDER.comment("=== CONFIGURACION DE PROTECCIONES ===").push("protections");
        returnProtectionBlockOnBreak = BUILDER.comment("Devolver el bloque de protección al jugador cuando lo rompe")
                .define("returnProtectionBlockOnBreak", true);
        BUILDER.pop();

        SERVER_SPEC = BUILDER.build();
    }

    public static void loadProtections() {
        protectionBlocks.clear();
        File blocksDir = FMLPaths.CONFIGDIR.get().resolve("PukysCore/blocks").toFile();

        if (!blocksDir.exists()) {
            blocksDir.mkdirs();
            createDefaultBlock(blocksDir);
        }

        File[] files = blocksDir.listFiles((dir, name) -> name.endsWith(".toml"));
        if (files != null) {
            for (File file : files) {
                try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
                    config.load();
                    String type = config.get("type");
                    String alias = config.get("alias");
                    String description = config.get("description");

                    int xRadius = config.get("region.x_radius");
                    int yRadius = config.get("region.y_radius");
                    int zRadius = config.get("region.z_radius");

                    String displayName = config.get("block_data.display_name");
                    List<String> lore = config.get("block_data.lore");
                    boolean enchanted = config.get("block_data.enchanted_effect");

                    protectionBlocks.put(alias.toLowerCase(), new ProtectionBlock(alias, type, description, xRadius, yRadius, zRadius, displayName, lore, enchanted));
                } catch (Exception e) {
                    System.err.println("[PukysCore] Error leyendo archivo de bloque: " + file.getName());
                }
            }
        }
        System.out.println("[PukysCore] Cargados " + protectionBlocks.size() + " bloques desde TOML.");
    }

    private static void createDefaultBlock(File folder) {
        File file = new File(folder, "bronce.toml");
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            config.setComment("type", " Bloque de minecraft que se usará\n Usa el identificador del bloque (ej. minecraft:copper_ore)");
            config.set("type", "minecraft:copper_ore");

            config.setComment("alias", " El identificador que se usará en el comando /pc give");
            config.set("alias", "bronce");

            config.setComment("description", " Breve descripción de lo que es el bloque");
            config.set("description", "10x10 block radius protection zone.");

            config.setComment("region", " =======================================\n Configuracion de la Region\n =======================================");
            config.set("region.x_radius", 10);
            config.setComment("region.y_radius", " Pon -1 si quieres que proteja todas las alturas (del fondo al cielo)");
            config.set("region.y_radius", 10);
            config.set("region.z_radius", 10);

            config.setComment("block_data", " =======================================\n Configuracion Visual del Bloque\n =======================================");
            config.setComment("block_data.display_name", " Soporta codigos de color con &");
            config.set("block_data.display_name", "&2&lProtección &5&lBronce");

            config.setComment("block_data.lore", " Texto que aparece en el lore del bloque");
            config.set("block_data.lore", List.of("", "&2&oProtege un área de &5&o&l10 bloques &n&oen todas las direcciones", ""));

            config.setComment("block_data.enchanted_effect", " Define si el bloque tendrá el efecto visual de encantamiento");
            config.set("block_data.enchanted_effect", true);

            config.save();
        }
    }

    public static int getMaxHomes(ServerPlayer player) {
        if (player == null) return defaultMaxHomes.get();
        for (int i = 100; i > 0; i--) {
            PermissionNode<Boolean> node = PukysPermissions.MAX_HOMES_NODES.get(i);
            if (node != null && PermissionAPI.getPermission(player, node)) {
                return i;
            }
        }
        return defaultMaxHomes.get();
    }

    public static class ProtectionBlock {
        public String alias; public String material; public String description;
        public int radiusX; public int radiusY; public int radiusZ;
        public String displayName; public List<String> lore; public boolean enchanted;

        public ProtectionBlock(String alias, String material, String description, int radiusX, int radiusY, int radiusZ, String displayName, List<String> lore, boolean enchanted) {
            this.alias = alias; this.material = material; this.description = description;
            this.radiusX = radiusX; this.radiusY = radiusY; this.radiusZ = radiusZ;
            this.displayName = displayName; this.lore = lore; this.enchanted = enchanted;
        }
    }
}