package com.pukyscraft.core.functions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryManager {
    public static final Map<UUID, OfflineSession> offlineSessions = new ConcurrentHashMap<>();

    public static class OfflineSession {
        public File dataFile;
        public CompoundTag nbt;
        public SimpleContainer container;
        public String targetName;

        public OfflineSession(File f, CompoundTag n, SimpleContainer c, String name) {
            dataFile = f; nbt = n; container = c; targetName = name;
        }
    }

    // INVSEE ONLINE
    public static void openOnlineInv(ServerPlayer admin, ServerPlayer target) {
        // Un cofre de 6 filas (54 slots) para alojar el inventario + armadura
        SimpleContainer container = new SimpleContainer(54) {
            @Override
            public void setChanged() {
                super.setChanged();
                // Al mover un ítem en el cofre, se sincroniza al instante con el jugador
                for (int i = 0; i < 36; i++) target.getInventory().setItem(i, this.getItem(i)); // Inventario principal
                for (int i = 0; i < 4; i++) target.getInventory().armor.set(i, this.getItem(36 + i)); // Armadura
                target.getInventory().offhand.set(0, this.getItem(40)); // Mano secundaria
            }
        };

        for (int i = 0; i < 36; i++) container.setItem(i, target.getInventory().getItem(i).copy());
        for (int i = 0; i < 4; i++) container.setItem(36 + i, target.getInventory().armor.get(i).copy());
        container.setItem(40, target.getInventory().offhand.get(0).copy());

        admin.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, playerInv, p) -> ChestMenu.sixRows(id, playerInv, container),
                net.minecraft.network.chat.Component.literal("§8Invsee: " + target.getName().getString())
        ));
    }

    // INVSEE OFFLINE
    public static boolean openOfflineInv(ServerPlayer admin, UUID targetUuid, String targetName) {
        try {
            File playerDataDir = admin.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
            File dataFile = new File(playerDataDir, targetUuid.toString() + ".dat");

            if (!dataFile.exists()) return false;

            CompoundTag nbt = NbtIo.readCompressed(dataFile);
            ListTag inventoryNbt = nbt.getList("Inventory", 10);
            SimpleContainer container = new SimpleContainer(54);

            for (int i = 0; i < inventoryNbt.size(); i++) {
                CompoundTag itemTag = inventoryNbt.getCompound(i);
                byte slot = itemTag.getByte("Slot");
                ItemStack stack = ItemStack.of(itemTag);

                int containerSlot = -1;
                if (slot >= 0 && slot < 36) containerSlot = slot;
                else if (slot >= 100 && slot < 104) containerSlot = 36 + (slot - 100);
                else if (slot == -106) containerSlot = 40;

                if (containerSlot != -1) container.setItem(containerSlot, stack);
            }

            offlineSessions.put(admin.getUUID(), new OfflineSession(dataFile, nbt, container, targetName));

            admin.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, playerInv, p) -> ChestMenu.sixRows(id, playerInv, container),
                    net.minecraft.network.chat.Component.literal("§8Offline: " + targetName)
            ));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void saveOfflineInv(UUID adminUuid) {
        OfflineSession session = offlineSessions.remove(adminUuid);
        if (session == null) return;

        try {
            ListTag newInvList = new ListTag();
            for (int i = 0; i < 54; i++) {
                ItemStack stack = session.container.getItem(i);
                if (stack.isEmpty()) continue;

                CompoundTag itemTag = new CompoundTag();
                byte slot = 0;

                if (i < 36) slot = (byte) i;
                else if (i >= 36 && i < 40) slot = (byte) (100 + (i - 36));
                else if (i == 40) slot = (byte) -106;
                else continue;

                itemTag.putByte("Slot", slot);
                stack.save(itemTag);
                newInvList.add(itemTag);
            }

            session.nbt.put("Inventory", newInvList);
            NbtIo.writeCompressed(session.nbt, session.dataFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}