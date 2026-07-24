package com.pukyscraft.core.protection.commands;

import com.pukyscraft.core.PukysConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pukyscraft.core.permissions.PukysPermissions;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.PermissionAPI;

public class ProtectionCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // Creamos la regla universal para tus comandos de admin
        java.util.function.Predicate<CommandSourceStack> requireAdmin = source -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
            }
            return source.hasPermission(2); // Para la consola
        };

        dispatcher.register(Commands.literal("pc")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("give")
                        .requires(requireAdmin)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(PukysConfig.protectionBlocks.keySet(), builder))
                                        .executes(context -> giveProtectionBlock(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                StringArgumentType.getString(context, "type")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("add")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> manageMember(context.getSource(), EntityArgument.getPlayer(context, "player"), true))
                        )
                )
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> manageMember(context.getSource(), EntityArgument.getPlayer(context, "player"), false))
                        )
                )
                .then(Commands.literal("info")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> showInfo(context.getSource()))
                )
        );
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§e=== Comandos de PukysCore Protection ==="), false);
        source.sendSuccess(() -> Component.literal("§b/pc add <jugador> §7- Añadir miembro a tu zona."), false);
        source.sendSuccess(() -> Component.literal("§b/pc remove <jugador> §7- Remover miembro de tu zona."), false);
        source.sendSuccess(() -> Component.literal("§b/pc info §7- Ver datos de la zona actual."), false);
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("§c/pc give <jugador> <tipo> §7- Dar bloque de protección."), false);
        }
        return 1;
    }

    private static int giveProtectionBlock(CommandSourceStack source, ServerPlayer target, String typeId) {
        PukysConfig.ProtectionBlock protType = PukysConfig.protectionBlocks.get(typeId.toLowerCase());
        if (protType == null) {
            source.sendFailure(Component.literal("§cTipo inválido. Opciones: " + String.join(", ", PukysConfig.protectionBlocks.keySet())));
            return 0;
        }

        String[] parts = protType.material.split(":");
        ResourceLocation resource = parts.length == 2 ? new ResourceLocation(parts[0], parts[1]) : new ResourceLocation("minecraft", protType.material);

        ItemStack protectionStone = new ItemStack(ForgeRegistries.ITEMS.getValue(resource));
        protectionStone.setHoverName(Component.literal(protType.displayName));

        CompoundTag nbt = protectionStone.getOrCreateTag();
        nbt.putString("PukysProtectionType", typeId.toLowerCase());
        protectionStone.setTag(nbt);

        if (!target.getInventory().add(protectionStone)) {
            target.drop(protectionStone, false);
        }

        source.sendSuccess(() -> Component.literal("§a[PukysCore] Bloque '" + typeId + "' entregado a " + target.getName().getString()), true);
        return 1;
    }

    private static int manageMember(CommandSourceStack source, ServerPlayer target, boolean isAdding) {
        ServerPlayer owner = source.getPlayer();
        if (owner == null) return 0;

        Region region = RegionManager.getRegionAt(owner.blockPosition(), owner.level().dimension().location().toString());
        if (region == null) {
            source.sendFailure(Component.literal("§cDebes estar dentro de tu región para gestionar miembros."));
            return 0;
        }

        if (!region.owner.equals(owner.getUUID()) && !source.hasPermission(2)) {
            source.sendFailure(Component.literal("§cSolo el dueño de esta zona puede modificar sus miembros."));
            return 0;
        }

        if (isAdding) {
            if (region.members.contains(target.getUUID())) {
                source.sendFailure(Component.literal("§c" + target.getName().getString() + " ya es miembro."));
            } else {
                region.members.add(target.getUUID());
                RegionManager.saveRegionsAsync();
                source.sendSuccess(() -> Component.literal("§a" + target.getName().getString() + " añadido a la protección."), false);
            }
        } else {
            if (region.members.remove(target.getUUID())) {
                RegionManager.saveRegionsAsync();
                source.sendSuccess(() -> Component.literal("§a" + target.getName().getString() + " removido de la protección."), false);
            } else {
                source.sendFailure(Component.literal("§cEl jugador no pertenece a esta zona."));
            }
        }
        return 1;
    }

    private static int showInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        Region region = RegionManager.getRegionAt(player.blockPosition(), player.level().dimension().location().toString());
        if (region == null) {
            source.sendFailure(Component.literal("§7Terreno libre sin protección."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§b=== Datos de la Protección ==="), false);

        String ownerName = player.getServer().getProfileCache().get(region.owner)
                .map(com.mojang.authlib.GameProfile::getName).orElse("Desconocido");

        source.sendSuccess(() -> Component.literal("§7Dueño: §a" + ownerName), false);
        source.sendSuccess(() -> Component.literal("§7Tipo: §f" + region.type.toUpperCase() + " §7(Radio: §f" + region.radius + "§7)"), false);

        if (region.members.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Miembros: §fNinguno"), false);
        } else {
            java.util.List<String> memberNames = new java.util.ArrayList<>();
            for (java.util.UUID uuid : region.members) {
                String name = player.getServer().getProfileCache().get(uuid)
                        .map(com.mojang.authlib.GameProfile::getName).orElse("Desconocido");
                memberNames.add(name);
            }
            source.sendSuccess(() -> Component.literal("§7Miembros: §f" + String.join(", ", memberNames)), false);
        }
        return 1;
    }
}