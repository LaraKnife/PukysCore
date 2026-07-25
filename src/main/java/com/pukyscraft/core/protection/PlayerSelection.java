package com.pukyscraft.core.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PlayerSelection {
    public BlockPos pos1;
    public BlockPos pos2;

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    public void extendVert(Level level) {
        if (!isComplete()) return;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;

        this.pos1 = new BlockPos(pos1.getX(), minY, pos1.getZ());
        this.pos2 = new BlockPos(pos2.getX(), maxY, pos2.getZ());
    }
}