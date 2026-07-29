package com.pukyscraft.core.protection.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pukyscraft.core.PukysConfig;
import com.pukyscraft.core.auth.AuthDatabase;
import com.pukyscraft.core.permissions.PukysPermissions;
import com.pukyscraft.core.protection.PlayerSelection;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.Map;
import java.util.UUID;

public class ProtectionCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        java.util.function.Predicate<CommandSourceStack> requireAdmin = source -> {
            if (source.getEntity() instanceof ServerPlayer player) {
                return PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
            }
            return source.hasPermission(2);
        };

        dispatcher.register(Commands.literal("pos1")
                .requires(requireAdmin)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PlayerSelection sel = RegionManager.getSelection(player.getUUID());
                    sel.pos1 = player.blockPosition();
                    player.sendSystemMessage(Component.literal("§aPosición 1 establecida en tus pies."));
                    return 1;
                }));

        dispatcher.register(Commands.literal("pos2")
                .requires(requireAdmin)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PlayerSelection sel = RegionManager.getSelection(player.getUUID());
                    sel.pos2 = player.blockPosition();
                    player.sendSystemMessage(Component.literal("§dPosición 2 establecida en tus pies."));
                    return 1;
                }));

        dispatcher.register(Commands.literal("extend")
                .requires(requireAdmin)
                .then(Commands.literal("vert").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    PlayerSelection sel = RegionManager.getSelection(player.getUUID());
                    if (!sel.isComplete()) {
                        player.sendSystemMessage(Component.literal("§cDefine Pos1 y Pos2 primero."));
                        return 0;
                    }
                    sel.extendVert(player.level());
                    player.sendSystemMessage(Component.literal("§eSelección extendida verticalmente."));
                    return 1;
                }))
        );

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
                // --- MENÚ PRINCIPAL INTERACTIVO DE FLAGS ---
                .then(Commands.literal("flags")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String dim = player.level().dimension().location().toString();
                            boolean hasAdmin = PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS) || player.hasPermissions(2);

                            Region pRegion = RegionManager.getRegionAt(player.blockPosition(), dim);

                            // 1. Si está dentro de una región de jugador
                            if (pRegion != null) {
                                if (pRegion.owner.equals(player.getUUID()) || hasAdmin) {
                                    sendPlayerInteractiveMenu(player, pRegion);
                                    return 1;
                                } else {
                                    player.sendSystemMessage(Component.literal("§cNo estás dentro de una zona protegida por ti."));
                                    return 0;
                                }
                            }

                            // 2. Si no está en protección de jugador pero es Admin (WorldRegion / __global__)
                            if (hasAdmin) {
                                WorldRegion wRegion = RegionManager.getWorldRegionAt(player.blockPosition(), dim);
                                player.sendSystemMessage(Component.literal("§eModificando región de mundo: §6" + wRegion.getName()));
                                sendWorldInteractiveMenu(player, wRegion.getName());
                                return 1;
                            }

                            // 3. Jugador común fuera de su zona
                            player.sendSystemMessage(Component.literal("§cNo estás dentro de una zona protegida por ti."));
                            return 0;
                        })
                )
                // --- COMANDOS INTERNOS DE TOGGLE (CLIC EN EL CHAT) ---
                .then(Commands.literal("_toggleplayerflag")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("flag", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String flagName = StringArgumentType.getString(context, "flag");
                                    boolean newVal = BoolArgumentType.getBool(context, "value");
                                    String dim = player.level().dimension().location().toString();

                                    Region pRegion = RegionManager.getRegionAt(player.blockPosition(), dim);
                                    boolean hasAdmin = PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS) || player.hasPermissions(2);

                                    if (pRegion == null || (!pRegion.owner.equals(player.getUUID()) && !hasAdmin)) {
                                        player.sendSystemMessage(Component.literal("§cNo estás dentro de una zona protegida por ti."));
                                        return 0;
                                    }

                                    if (!Region.ALLOWED_PLAYER_FLAGS.contains(flagName)) {
                                        player.sendSystemMessage(Component.literal("§cFlag no permitida."));
                                        return 0;
                                    }

                                    pRegion.setFlag(flagName, newVal);
                                    RegionManager.saveRegionsAsync();
                                    sendPlayerInteractiveMenu(player, pRegion);
                                    return 1;
                                }))
                        )
                )
                .then(Commands.literal("_toggleflag")
                        .requires(requireAdmin)
                        .then(Commands.argument("region", StringArgumentType.string())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                                            String regName = StringArgumentType.getString(context, "region");
                                            String flagName = StringArgumentType.getString(context, "flag");
                                            boolean newVal = BoolArgumentType.getBool(context, "value");

                                            WorldRegion region = RegionManager.getWorldRegion(regName);
                                            if (region != null) {
                                                region.setFlag(flagName, newVal);
                                                RegionManager.saveWorldRegion(region);
                                                sendWorldInteractiveMenu(context.getSource().getPlayerOrException(), regName);
                                            }
                                            return 1;
                                        }))
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .requires(requireAdmin)
                        .executes(context -> reloadConfig(context.getSource()))
                )
                .then(Commands.literal("region")
                        .requires(requireAdmin)
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.word()).executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(context, "name");
                                    PlayerSelection sel = RegionManager.getSelection(player.getUUID());

                                    if (!sel.isComplete()) {
                                        player.sendSystemMessage(Component.literal("§cSelecciona 2 puntos primero."));
                                        return 0;
                                    }

                                    String dim = player.level().dimension().location().toString();
                                    WorldRegion newRegion = new WorldRegion(name, dim, sel.pos1, sel.pos2);
                                    RegionManager.saveWorldRegion(newRegion);
                                    player.sendSystemMessage(Component.literal("§aRegión '" + name + "' guardada."));
                                    return 1;
                                }))
                        )
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("flags").executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    sendWorldInteractiveMenu(context.getSource().getPlayerOrException(), name);
                                    return 1;
                                }))
                        )
                )
        );
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§e=== Comandos de PukysCore Protection ==="), false);
        source.sendSuccess(() -> Component.literal("§b/pc flags §7- Abrir menú interactivo de la zona."), false);
        source.sendSuccess(() -> Component.literal("§b/pc add <jugador> §7- Añadir miembro a tu zona."), false);
        source.sendSuccess(() -> Component.literal("§b/pc remove <jugador> §7- Remover miembro de tu zona."), false);
        source.sendSuccess(() -> Component.literal("§b/pc info §7- Ver datos de la zona actual."), false);
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("§c/pc give <jugador> <tipo> §7- Dar bloque de protección."), false);
            source.sendSuccess(() -> Component.literal("§c/pc reload §7- Recargar configuraciones y bloques."), false);
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

        ItemStack protectionBlock = new ItemStack(ForgeRegistries.ITEMS.getValue(resource));
        String formattedName = protType.displayName.replace("&", "§");
        protectionBlock.setHoverName(Component.literal(formattedName));

        CompoundTag nbt = protectionBlock.getOrCreateTag();
        nbt.putString("PukysProtectionType", typeId.toLowerCase());

        CompoundTag displayTag = nbt.contains("display") ? nbt.getCompound("display") : new CompoundTag();
        net.minecraft.nbt.ListTag loreList = new net.minecraft.nbt.ListTag();
        for (String line : protType.lore) {
            String jsonLore = Component.Serializer.toJson(Component.literal(line.replace("&", "§")));
            loreList.add(net.minecraft.nbt.StringTag.valueOf(jsonLore));
        }
        displayTag.put("Lore", loreList);
        nbt.put("display", displayTag);

        if (protType.enchanted) {
            net.minecraft.nbt.ListTag enchantments = new net.minecraft.nbt.ListTag();
            enchantments.add(new CompoundTag());
            nbt.put("Enchantments", enchantments);
            nbt.putInt("HideFlags", 1);
        }

        protectionBlock.setTag(nbt);

        if (!target.getInventory().add(protectionBlock)) {
            target.drop(protectionBlock, false);
        }

        source.sendSuccess(() -> Component.literal("§a[PukysCore] Bloque '" + typeId + "' entregado a " + target.getName().getString()), true);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        try {
            PukysConfig.loadProtections();
            source.sendSuccess(() -> Component.literal("§a[PukysCore] ¡Configuración y bloques de protección recargados con éxito!"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c[PukysCore] Ocurrió un error al recargar. Revisa la consola del servidor."));
            e.printStackTrace();
            return 0;
        }
    }

    private static int manageMember(CommandSourceStack source, ServerPlayer targetPlayer, boolean isAdding) {
        ServerPlayer executor;
        try {
            executor = source.getPlayerOrException(); // Quien ejecuta el comando
        } catch (CommandSyntaxException e) {
            return 0;
        }

        // Obtener la ubicación actual y dimensión del ejecutor
        BlockPos pos = executor.blockPosition();
        String dimension = executor.level().dimension().location().toString();

        // Buscar la región
        Region region = RegionManager.getRegionAt(pos, dimension);

        if (region == null) {
            source.sendFailure(Component.literal("§cDebes estar dentro de una protección para gestionar sus miembros."));
            return 0;
        }

        // Comprobar si el ejecutor tiene permisos de Admin o es OP (Nivel 2+)
        boolean hasAdmin = PermissionAPI.getPermission(executor, PukysPermissions.ADMIN_COMMANDS) || executor.hasPermissions(2);

        // Validar propiedad: Permitir si es el dueño O si es administrador
        if (!region.owner.equals(executor.getUUID()) && !hasAdmin) {
            source.sendFailure(Component.literal("§cNo eres el dueño de esta protección."));
            return 0;
        }

        // Aplicar lógica de añadir/remover al target
        UUID targetUUID = targetPlayer.getUUID();

        if (isAdding) {
            // Evitar que el dueño se añada a sí mismo (los admins podrían hacerlo por error)
            if (targetUUID.equals(region.owner)) {
                source.sendFailure(Component.literal("§cEl dueño de la región no necesita ser añadido como miembro."));
                return 0;
            }

            if (region.members.contains(targetUUID)) {
                source.sendFailure(Component.literal("§c" + targetPlayer.getName().getString() + " ya es miembro."));
            } else {
                region.members.add(targetUUID);
                RegionManager.saveRegionsAsync(); // Guardado de seguridad asíncrono
                source.sendSuccess(() -> Component.literal("§a" + targetPlayer.getName().getString() + " añadido a la protección."), false);
                targetPlayer.sendSystemMessage(Component.literal("§aHas sido añadido a la protección de §e" + region.ownerName + "§a."));
            }
        } else {
            if (region.members.remove(targetUUID)) {
                RegionManager.saveRegionsAsync(); // Guardado de seguridad asíncrono
                source.sendSuccess(() -> Component.literal("§a" + targetPlayer.getName().getString() + " removido de la protección."), false);
                targetPlayer.sendSystemMessage(Component.literal("§cHas sido removido de la protección de §e" + region.ownerName + "§c."));
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
        source.sendSuccess(() -> Component.literal("§7Dueño: §a" + region.ownerName), false);
        source.sendSuccess(() -> Component.literal("§7Tipo: §f" + region.type.toUpperCase()), false);

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

    private static void sendPlayerInteractiveMenu(ServerPlayer player, Region region) {
        player.sendSystemMessage(Component.literal("\n§8====== §bFlags de Tu Protección §8======"));

        for (String flag : Region.ALLOWED_PLAYER_FLAGS) {
            boolean value = region.getFlag(flag);
            boolean opposite = !value;

            MutableComponent line = Component.literal("§7- §f" + flag + ": ");
            String color = value ? "§a[ALLOW]" : "§c[DENY]";

            MutableComponent button = Component.literal(color).withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pc _toggleplayerflag " + flag + " " + opposite))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eHaz clic para cambiar a " + opposite)))
            );

            player.sendSystemMessage(line.append(button));
        }
        player.sendSystemMessage(Component.literal("§8====================================\n"));
    }

    private static void sendWorldInteractiveMenu(ServerPlayer player, String regionName) {
        WorldRegion region = RegionManager.getWorldRegion(regionName);
        if (region == null) {
            player.sendSystemMessage(Component.literal("§cLa región global no existe."));
            return;
        }

        player.sendSystemMessage(Component.literal("\n§8====== §bFlags Globales: §3" + region.getName() + " §8======"));

        for (Map.Entry<String, Boolean> entry : region.getFlags().entrySet()) {
            String flag = entry.getKey();
            boolean value = entry.getValue();
            boolean opposite = !value;

            MutableComponent line = Component.literal("§7- §f" + flag + ": ");
            String color = value ? "§a[ALLOW]" : "§c[DENY]";

            MutableComponent button = Component.literal(color).withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pc _toggleflag " + regionName + " " + flag + " " + opposite))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eHaz clic para cambiar a " + opposite)))
            );

            player.sendSystemMessage(line.append(button));
        }
        player.sendSystemMessage(Component.literal("§8=========================================\n"));
    }
}