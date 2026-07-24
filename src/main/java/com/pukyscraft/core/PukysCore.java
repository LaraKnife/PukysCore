package com.pukyscraft.core;

import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.auth.commands.AuthCommands;
import com.pukyscraft.core.auth.commands.CoreCommands;
import com.pukyscraft.core.auth.events.AuthEventHandler;
import com.pukyscraft.core.functions.TeleportManager;
import com.pukyscraft.core.functions.commands.FunctionsCommands;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.commands.ProtectionCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@Mod(PukysCore.MODID)
public class PukysCore {
    public static final String MODID = "pukyscore";

    public PukysCore() {
        System.out.println("[PukysCore] Inicializando Core...");
        AuthDatabase.init();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PukysConfig.SERVER_SPEC, "PukysCore/config.toml");

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(AuthEventHandler.class);
        MinecraftForge.EVENT_BUS.register(AuthCommands.class);
        MinecraftForge.EVENT_BUS.register(CoreCommands.class);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        System.out.println("[PukysCore] Cargando e indexando módulos...");
        PukysConfig.loadProtections();
        RegionManager.loadRegions();
        TeleportManager.loadAll();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ProtectionCommands.register(event.getDispatcher());
        FunctionsCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        System.out.println("[PukysCore] Servidor apagándose. Forzando guardado síncrono de seguridad...");
        // AL APAGAR EL SERVIDOR SE DEBE GUARDAR EN EL HILO PRINCIPAL
        // Para asegurar que la JVM no se cierre antes de escribir en disco.

        AuthDatabase.saveSync();

        // Ejecución síncrona improvisada
        Runnable saveRegionsTask = () -> {
            try {
                java.io.File regionsFile = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/PukysCore/database/regions.json").toFile();
                regionsFile.getParentFile().mkdirs();
                try (java.io.Writer writer = new java.io.FileWriter(regionsFile)) {
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RegionManager.activeRegions, writer);
                }
            } catch (Exception e) { e.printStackTrace(); }
        };
        saveRegionsTask.run();

        System.out.println("[PukysCore] Datos guardados con éxito. Apagado seguro completado.");
    }
}