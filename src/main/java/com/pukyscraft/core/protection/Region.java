package com.pukyscraft.core.protection;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Region {
    public UUID owner;
    public List<UUID> members = new CopyOnWriteArrayList<>();
    public BlockPos center;
    public int radius;
    public String dimension;
    public String type;

    public Region(UUID owner, BlockPos center, int radius, String dimension, String type) {
        this.owner = owner;
        this.center = center;
        this.radius = radius;
        this.dimension = dimension;
        this.type = type;
    }

    public boolean contains(BlockPos pos, String checkDimension) {
        if (!this.dimension.equals(checkDimension)) return false;

        return Math.abs(pos.getX() - center.getX()) <= radius &&
                Math.abs(pos.getY() - center.getY()) <= radius &&
                Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    public boolean isMemberOrOwner(UUID uuid) {
        return owner.equals(uuid) || members.contains(uuid);
    }
}