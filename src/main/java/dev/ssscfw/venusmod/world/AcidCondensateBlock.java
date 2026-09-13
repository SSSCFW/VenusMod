package dev.ssscfw.venusmod.world;

import dev.ssscfw.venusmod.environment.VenusEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** 地形を溶かさない酸性流体。防護服一式以外は接触中に2秒ごとのダメージ。 */
public final class AcidCondensateBlock extends LiquidBlock {
    public AcidCondensateBlock(FlowingFluid fluid, Properties properties) { super(fluid, properties); }
    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof LivingEntity living && living.tickCount % 40 == 0
                && VenusEnvironment.suitPieces(living) < 4) living.hurt(level.damageSources().magic(), 2);
    }
}
