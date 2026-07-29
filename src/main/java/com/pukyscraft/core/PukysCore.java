package com.pukyscraft.core;

import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.auth.commands.AuthCommands;
import com.pukyscraft.core.auth.commands.CoreCommands;
import com.pukyscraft.core.functions.LogManager;
import com.pukyscraft.core.protection.commands.ProtectionCommands;
import com.pukyscraft.core.auth.events.AuthEventHandler;
import com.pukyscraft.core.functions.TeleportManager;
import com.pukyscraft.core.functions.commands.FunctionsCommands;
import com.pukyscraft.core.protection.RegionManager;
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
        RegionManager.init();
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

        AuthDatabase.saveSync();
        RegionManager.saveRegionsSync();
        LogManager.shutdown();
        System.out.println("[PukysCore] Datos guardados con éxito. Apagado seguro completado.");
    }
}