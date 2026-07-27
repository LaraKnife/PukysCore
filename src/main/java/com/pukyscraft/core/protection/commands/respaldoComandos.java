package com.pukyscraft.core.protection.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.pukyscraft.core.PukysConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pukyscraft.core.permissions.PukysPermissions;
import com.pukyscraft.core.protection.PlayerSelection;
import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
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

public class respaldoComandos {
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
                .then(Commands.literal("flag")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.argument("flagName", StringArgumentType.word())
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            String flagName = StringArgumentType.getString(context, "flagName");
                                            boolean newVal = BoolArgumentType.getBool(context, "value");

                                            Region region = RegionManager.getRegionAt(player.blockPosition(), player.level().dimension().location().toString());

                                            if (region == null) {
                                                player.sendSystemMessage(Component.literal("§cNo estás dentro de una zona de protección."));
                                                return 0;
                                            }

                                            boolean hasAdmin = PermissionAPI.getPermission(player, PukysPermissions.ADMIN_COMMANDS);
                                            if (!region.owner.equals(player.getUUID()) && !hasAdmin) {
                                                player.sendSystemMessage(Component.literal("§cSolo el dueño puede modificar las flags de esta zona."));
                                                return 0;
                                            }

                                            if (!Region.ALLOWED_PLAYER_FLAGS.contains(flagName)) {
                                                player.sendSystemMessage(Component.literal("§cFlag no válida o restringida para jugadores."));
                                                player.sendSystemMessage(Component.literal("§7Opciones: " + String.join(", ", Region.ALLOWED_PLAYER_FLAGS)));
                                                return 0;
                                            }

                                            region.setFlag(flagName, newVal);
                                            RegionManager.saveRegionsAsync();
                                            player.sendSystemMessage(Component.literal("§aLa flag '" + flagName + "' se ha cambiado a " + newVal + " en tu zona."));
                                            return 1;
                                        })
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
                                    sendInteractiveMenu(context.getSource().getPlayerOrException(), name);
                                    return 1;
                                }))
                        )
                )
                .then(Commands.literal("flags")
                        .requires(requireAdmin)
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String dim = player.level().dimension().location().toString();
                            WorldRegion region = RegionManager.getWorldRegionAt(player.blockPosition(), dim);
                            player.sendSystemMessage(Component.literal("§eEstás en la región: §6" + region.getName()));
                            sendInteractiveMenu(player, region.getName());
                            return 1;
                        }))
                .then(Commands.literal("_toggleflag")
                        .requires(requireAdmin)
                        .then(Commands.argument("region", StringArgumentType.word())
                                .then(Commands.argument("flag", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(context -> {
                                            String regName = StringArgumentType.getString(context, "region");
                                            String flagName = StringArgumentType.getString(context, "flag");
                                            boolean newVal = BoolArgumentType.getBool(context, "value");

                                            WorldRegion region = RegionManager.getWorldRegion(regName);
                                            if (region != null) {
                                                region.setFlag(flagName, newVal);
                                                RegionManager.saveWorldRegion(region);
                                                sendInteractiveMenu(context.getSource().getPlayerOrException(), regName);
                                            }
                                            return 1;
                                        }))
                                )
                        )
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

    private static int manageMember(CommandSourceStack source, ServerPlayer target, boolean isAdding) {
        ServerPlayer owner = source.getPlayer();
        if (owner == null) return 0;

        Region region = RegionManager.getRegionAt(owner.blockPosition(), owner.level().dimension().location().toString());
        if (region == null) {
            source.sendFailure(Component.literal("§cDebes estar dentro de tu región para gestionar miembros."));
            return 0;
        }

        boolean hasAdmin = PermissionAPI.getPermission(owner, PukysPermissions.ADMIN_COMMANDS);
        if (!region.owner.equals(owner.getUUID()) && !hasAdmin) {
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

    private static void sendInteractiveMenu(ServerPlayer player, String regionName) {
        WorldRegion region = RegionManager.getWorldRegion(regionName);
        if (region == null) {
            player.sendSystemMessage(Component.literal("§cLa región global no existe."));
            return;
        }

        player.sendSystemMessage(Component.literal("\n§8====== §bFlags: §3" + region.getName() + " §8======"));

        for (Map.Entry<String, Boolean> entry : region.getFlags().entrySet()) {
            String flag = entry.getKey();
            boolean value = entry.getValue();

            MutableComponent line = Component.literal("§7- §f" + flag + ": ");
            boolean opposite = !value;
            String color = value ? "§aALLOW" : "§cDENY";

            MutableComponent button = Component.literal(color).withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pc _toggleflag " + regionName + " " + flag + " " + opposite))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§eClick para cambiar a " + opposite)))
            );

            player.sendSystemMessage(line.append(button));
        }
        player.sendSystemMessage(Component.literal("§8=============================\n"));
    }
}
