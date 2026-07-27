package com.pukyscraft.core.mixin;

import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LeavesBlock.class)
public class LeavesBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void pukyscore$onLeafDecay(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        String dimension = level.dimension().location().toString();

        Region pRegion = RegionManager.getRegionAt(pos, dimension);
        WorldRegion wRegion = RegionManager.getWorldRegionAt(pos, dimension);
        boolean allowDecay = pRegion != null ? true : wRegion.getFlag("leaf_decay");

        if (!allowDecay) {
            ci.cancel();
        }
    }
}