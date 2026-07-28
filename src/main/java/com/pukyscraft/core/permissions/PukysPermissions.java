package com.pukyscraft.core.permissions;

import com.pukyscraft.core.PukysConfig;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PukysPermissions {

    public static PermissionNode<Boolean> ADMIN_COMMANDS;
    public static final Map<Integer, PermissionNode<Boolean>> MAX_HOMES_NODES = new HashMap<>();

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {

        ADMIN_COMMANDS = new PermissionNode<>(
                "pukyscore", "admin", PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.hasPermissions(2)
        );
        event.addNodes(ADMIN_COMMANDS);

        for (int i = 1; i <= 100; i++) {
            PermissionNode<Boolean> node = new PermissionNode<>(
                    "pukyscore", "max_homes." + i, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> false
            );
            MAX_HOMES_NODES.put(i, node);
            event.addNodes(node);
        }
    }
}