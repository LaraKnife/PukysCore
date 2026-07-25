package com.pukyscraft.core.protection;

import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Region {
    public UUID owner;
    public String ownerName;
    public List<UUID> members = new CopyOnWriteArrayList<>();
    public BlockPos center;
    public int radiusX;
    public int radiusY;
    public int radiusZ;
    public String dimension;
    public String type;

    public static final List<String> ALLOWED_PLAYER_FLAGS = Arrays.asList(
            "block_break", "block_place", "pvp", "explosion_damage", "block_interact", "entity_interact"
    );

    public Map<String, Boolean> flags = new HashMap<>();

    public Region(UUID owner, String ownerName, BlockPos center, int radiusX, int radiusY, int radiusZ, String dimension, String type) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.center = center;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.dimension = dimension;
        this.type = type;

        // Flags por defecto para una nueva zona de jugador
        this.flags.put("block_break", false);
        this.flags.put("block_place", false);
        this.flags.put("pvp", false);
        this.flags.put("explosion_damage", false);
        this.flags.put("block_interact", false);
        this.flags.put("entity_interact", false);
    }

    public boolean getFlag(String flag) {
        return this.flags.getOrDefault(flag, false);
    }

    public void setFlag(String flag, boolean value) {
        this.flags.put(flag, value);
    }

    public boolean contains(BlockPos pos, String checkDimension) {
        if (!this.dimension.equals(checkDimension)) return false;

        boolean inX = Math.abs(pos.getX() - center.getX()) <= radiusX;
        boolean inZ = Math.abs(pos.getZ() - center.getZ()) <= radiusZ;
        // Si radiusY es -1, ignoramos la altura y protege infinito verticalmente
        boolean inY = (radiusY == -1) || (Math.abs(pos.getY() - center.getY()) <= radiusY);

        return inX && inY && inZ;
    }

    public boolean overlaps(Region other) {
        if (!this.dimension.equals(other.dimension)) return false;

        boolean overlapX = Math.abs(this.center.getX() - other.center.getX()) <= (this.radiusX + other.radiusX);
        boolean overlapZ = Math.abs(this.center.getZ() - other.center.getZ()) <= (this.radiusZ + other.radiusZ);

        boolean overlapY = true;
        if (this.radiusY != -1 && other.radiusY != -1) {
            overlapY = Math.abs(this.center.getY() - other.center.getY()) <= (this.radiusY + other.radiusY);
        }

        return overlapX && overlapY && overlapZ;
    }

    public boolean isMemberOrOwner(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }
}