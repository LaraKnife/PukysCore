package com.pukyscraft.core.protection;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Region {
    public UUID owner;
    public List<UUID> members = new CopyOnWriteArrayList<>();
    public BlockPos center;
    public int radiusX;
    public int radiusY;
    public int radiusZ;
    public String dimension;
    public String type;

    public Region(UUID owner, BlockPos center, int radiusX, int radiusY, int radiusZ, String dimension, String type) {
        this.owner = owner;
        this.center = center;
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.dimension = dimension;
        this.type = type;
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