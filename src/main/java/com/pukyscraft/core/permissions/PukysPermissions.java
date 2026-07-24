package com.pukyscraft.core.permissions;

import com.pukyscraft.core.PukysConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PukysPermissions {

    public static PermissionNode<Boolean> ADMIN_COMMANDS;

    public static PermissionNode<Integer> MAX_HOMES;

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {

        ADMIN_COMMANDS = new PermissionNode<>(
                "pukyscore", "admin", PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(2)
        );

        MAX_HOMES = new PermissionNode<>(
                "pukyscore", "max_homes", PermissionTypes.INTEGER,
                (player, uuid, context) -> PukysConfig.defaultMaxHomes.get()
        );

        event.addNodes(ADMIN_COMMANDS, MAX_HOMES);
    }
}