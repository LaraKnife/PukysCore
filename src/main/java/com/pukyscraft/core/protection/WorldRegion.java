package com.pukyscraft.core.protection;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class WorldRegion {
    private final String name;
    private final String dimension;
    private final BlockPos min;
    private final BlockPos max;
    private final Map<String, Boolean> flags;

    public static final String[] AVAILABLE_FLAGS = {
            "block_break", "block_place", "pvp", "explosion_damage",
            "fall_damage", "mob_damage", "hunger_loss", "health_regen",
            "block_interact", "entity_interact"
    };

    public WorldRegion(String name, String dimension, BlockPos pos1, BlockPos pos2) {
        this.name = name;
        this.dimension = dimension;
        this.min = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.max = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));

        this.flags = new HashMap<>();
        for (String flag : AVAILABLE_FLAGS) {
            this.flags.put(flag, true);
        }
    }

    public boolean contains(BlockPos pos, String testDimension) {
        if (this.dimension.equals("all")) return true; // Global
        if (!this.dimension.equals(testDimension)) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() &&
                pos.getY() >= min.getY() && pos.getY() <= max.getY() &&
                pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public Map<String, Boolean> getFlags() { return flags; }
    public void setFlag(String flag, boolean value) { this.flags.put(flag, value); }
    public boolean getFlag(String flag) { return this.flags.getOrDefault(flag, true); }
}