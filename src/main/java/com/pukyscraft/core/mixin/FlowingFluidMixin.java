package com.pukyscraft.core.mixin;

import com.pukyscraft.core.protection.Region;
import com.pukyscraft.core.protection.RegionManager;
import com.pukyscraft.core.protection.WorldRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void pukyscore$onFluidSpread(LevelAccessor levelAccessor, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (levelAccessor instanceof Level level && !level.isClientSide()) {
            String dimension = level.dimension().location().toString();

            Region pRegion = RegionManager.getRegionAt(pos, dimension);
            WorldRegion wRegion = RegionManager.getWorldRegionAt(pos, dimension);

            boolean isWater = fluidState.is(FluidTags.WATER);
            boolean isLava = fluidState.is(FluidTags.LAVA);
            boolean allowFlow = true;

            if (pRegion != null) {
                allowFlow = true;
            } else {
                if (isWater) allowFlow = wRegion.getFlag("water_flow");
                else if (isLava) allowFlow = wRegion.getFlag("lava_flow");
            }

            if (!allowFlow) {
                ci.cancel();
            }
        }
    }
}