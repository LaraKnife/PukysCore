package com.pukyscraft.core.auth.events;

import com.pukyscraft.core.auth.*;
import com.pukyscraft.core.auth.SecurityManager;
import com.pukyscraft.core.PukysConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "pukyscore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AuthEventHandler {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LocationManager.init();
        AuthDatabase.init();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {

            // LIMITADOR ANTI-ATAQUES (Bots masivos)
            if (!SecurityManager.allowConnectionThrottled()) {
                player.connection.disconnect(Component.literal("§cEl servidor está recibiendo muchas conexiones. Espera 5 segundos y reintenta."));
                return;
            }

            String ip = SecurityManager.normalizeIp(player.getIpAddress());

            if (SecurityManager.isIpBanned(ip)) {
                player.connection.disconnect(Component.literal("§cTu IP está baneada temporalmente por intentos fallidos."));
                return;
            }
            if (!AuthDatabase.isRegistered(player.getUUID()) && AuthDatabase.getAccountsCountForIp(ip) >= PukysConfig.auth_maxAccountsPerIp.get()) {
                player.connection.disconnect(Component.literal("§cLímite de cuentas por IP superado en esta red."));
                return;
            }

            AuthSessionManager.onPlayerJoin(player);

            // EFECTOS (Inmovilización estricta y Ceguera Opcional)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 999999, 255, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 999999, 255, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 999999, 250, false, false));

            if (PukysConfig.enableBlindness.get()) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 999999, 0, false, false));
            }

            sendAuthMessage(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        AuthSessionManager.removePlayer(event.getEntity().getUUID());
    }

    // Intento de deshabilitar el salto del jugador (puede fallar, pero ya está parcheado mediante efectos)
    @SubscribeEvent
    public static void onPlayerJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!AuthSessionManager.isAuthenticated(player.getUUID())) {
                player.setDeltaMovement(0, 0, 0);
            }
        }
    }

    // Cancelar atacar
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!AuthSessionManager.isAuthenticated(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // Capa de protección extra por si los efectos fallan para que el jugador no pueda moverse
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            if (!AuthSessionManager.isAuthenticated(player.getUUID())) {

                // MENSAJE PERIÓDICO: 200 ticks = exactamente 10 segundos.
                if (player.tickCount % 200 == 0) {
                    sendAuthMessage(player);
                }

                long joinTime = AuthSessionManager.getJoinTime(player.getUUID());
                long timeoutMs = PukysConfig.auth_sessionTimeoutMinutes.get() * 1000L;
                if (joinTime > 0 && System.currentTimeMillis() - joinTime > timeoutMs) {
                    player.connection.disconnect(Component.literal("§cHas tardado demasiado en iniciar sesión."));
                    return;
                }

                /*
                LocationManager.Location join = LocationManager.getJoin();
                if (join != null && player.position().distanceToSqr(join.x, join.y, join.z) > 0.05) {
                    LocationManager.teleportTo(player, join);
                    // Capa de protección extra para que el jugador no pueda moverse
                    player.setDeltaMovement(0, 0, 0);
                }
                 */
            }
        }
    }

    // Cancelar romper bloques
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!AuthSessionManager.isAuthenticated(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    // Cancelar poner bloques
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!AuthSessionManager.isAuthenticated(player.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    // Cancelar interactuar
    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (!AuthSessionManager.isAuthenticated(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    // Cancelar tirar items (dropear)
    @SubscribeEvent
    public static void onItemDrop(ItemTossEvent event) {
        if (!AuthSessionManager.isAuthenticated(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    // Cancelar recoger items (pickup)
    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (!AuthSessionManager.isAuthenticated(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        String message = event.getRawText();
        if (!message.startsWith("/") && !AuthSessionManager.isAuthenticated(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal("§cDebes iniciar sesión para usar el chat."));
        }
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        if (!AuthSessionManager.isAuthenticated(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal("§cDebes iniciar sesión para hablar en el chat."));
        }
    }

    @SubscribeEvent
    public static void onCommandExecution(net.minecraftforge.event.CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            if (!AuthSessionManager.isAuthenticated(player.getUUID())) {
                String cmd = event.getParseResults().getReader().getString();
                // Permitir solo /login y /register
                if (!cmd.startsWith("login") && !cmd.startsWith("register") &&
                        !cmd.startsWith("/login") && !cmd.startsWith("/register")) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cNo puedes usar comandos sin iniciar sesión."));
                }
            }
        }
    }

    // Prevenir exploit de respawn sin login/register
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!AuthSessionManager.isAuthenticated(player.getUUID())) {

                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 999999, 255, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 999999, 255, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 999999, 250, false, false));

                if (PukysConfig.enableBlindness.get()) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 999999, 0, false, false));
                }

                LocationManager.Location join = LocationManager.getJoin();
                if (join != null) {
                    LocationManager.teleportTo(player, join);
                }
            }
        }
    }

    private static void sendAuthMessage(ServerPlayer player) {
        if (AuthDatabase.isRegistered(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c§l[PukysCore] §fBienvenido de nuevo. Usa §e/login <contraseña>"));
        } else {
            player.sendSystemMessage(Component.literal("§c§l[PukysCore] §fPor favor, regístrate usando §e/register <contraseña> <contraseña>"));
        }
    }
}