package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter.ExtremeSmithingCrafterBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 终焉合成器连接纹理行为:同方块 + 同朝向 + 已连接(扳手组)时,
 * 侧面 UV 平移到对应的 connected 半区。
 * <p>
 * 同时服务终焉动力合成器与终焉锻造器:两者的连接判定完全相同,
 * 只是纹理组不同,因此 {@link #getShift} 按方块类型分派。
 * <p>
 * 注意:Flywheel 烘焙 partial model 时会以 minecraft:air 作为占位
 * state 调用 getShift/getDataType,因此所有入口必须先做方块类型守卫,
 * 否则 air 无 HORIZONTAL_FACING 属性会直接抛异常。
 */
public class ExtremeCTBehaviour extends ConnectedTextureBehaviour.Base {

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face) {
        // 两种终焉方块都属于同一家族,可互相连接(共用连接组语义)
        if (!(state.getBlock() instanceof ExtremeMechanicalCrafterBlock)
                || !(other.getBlock() instanceof ExtremeMechanicalCrafterBlock)) {
            return false;
        }
        if (state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING)
                != other.getValue(HorizontalKineticBlock.HORIZONTAL_FACING)) {
            return false;
        }
        return ExtremeCrafterHelper.areCraftersConnected(reader, pos, otherPos);
    }

    @Override
    protected boolean reverseUVs(BlockState state, Direction direction) {
        if (!(state.getBlock() instanceof ExtremeMechanicalCrafterBlock)) {
            return false;
        }
        if (!direction.getAxis().isVertical()) {
            return false;
        }
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        if (facing.getAxis() == direction.getAxis()) {
            return false;
        }
        boolean isNegative = facing.getAxisDirection() == AxisDirection.NEGATIVE;
        if (direction == Direction.DOWN && facing.getAxis() == Axis.Z) {
            return !isNegative;
        }
        return isNegative;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction,
                                       @Nullable TextureAtlasSprite sprite) {
        // 类型守卫:air 等 non-crafter state 没有 facing 属性,直接返回 null
        if (!(state.getBlock() instanceof ExtremeMechanicalCrafterBlock)) {
            return null;
        }
        // 按方块类型选择纹理组;判定规则两者完全一致
        boolean smithing = state.getBlock() instanceof ExtremeSmithingCrafterBlock;

        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        boolean isFront = facing.getAxis() == direction.getAxis();
        boolean isVertical = direction.getAxis().isVertical();
        boolean facingX = facing.getAxis() == Axis.X;
        if (isFront) {
            // 正面朝向链路方向:连接后平移到晶体矩阵外壳(两种方块共用外壳纹理)
            return ModSpriteShifts.EXTREME_CRAFTER_FRONT;
        }
        if (isVertical && !facingX) {
            return smithing
                    ? ModSpriteShifts.EXTREME_SMITHING_CRAFTER_OTHERSIDE
                    : ModSpriteShifts.EXTREME_CRAFTER_OTHERSIDE;
        }
        return smithing
                ? ModSpriteShifts.EXTREME_SMITHING_CRAFTER_SIDE
                : ModSpriteShifts.EXTREME_CRAFTER_SIDE;
    }
}
