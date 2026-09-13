package dev.ssscfw.venusmod.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 部分形状の機械に共通の描画契約。衝突判定と隣接面の遮蔽判定を分離する。
 * モデルJSONのambientocclusion=falseだけでは隣接ブロックの面消去を防げない。
 * 将来の機械もこの基底クラスを使用すること（Gradle検査とGameTestで強制）。
 */
public abstract class VenusMachineBlock extends KineticBlock {
    protected VenusMachineBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected final VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected final boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected final int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
