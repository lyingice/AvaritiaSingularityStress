package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 终焉合成器的类型判定与连接组查询入口。
 * <p>
 * 对应原版 {@code com.simibubi.create.content.kinetics.crafter.CrafterHelper}，
 * 但判定的是终焉合成器自身，而不是 Create 机械合成器。
 * 连接纹理、连接组、配方链三处共用这里的语义，保证三套判定结果一致。
 */
public final class ExtremeCrafterHelper {

    private ExtremeCrafterHelper() {
    }

    /** 该方块状态是否为终焉合成器。对应原版 {@code AllBlocks.MECHANICAL_CRAFTER.has(state)}。 */
    public static boolean isCrafter(BlockState state) {
        return state != null && state.getBlock() instanceof ExtremeMechanicalCrafterBlock;
    }

    public static ExtremeMechanicalCrafterBlockEntity getCrafter(BlockAndTintGetter reader, BlockPos pos) {
        if (reader == null || pos == null) {
            return null;
        }
        BlockEntity blockEntity = reader.getBlockEntity(pos);
        return blockEntity instanceof ExtremeMechanicalCrafterBlockEntity crafter ? crafter : null;
    }

    public static ExtremeConnectedInputHandler.ConnectedInput getInput(BlockAndTintGetter reader, BlockPos pos) {
        ExtremeMechanicalCrafterBlockEntity crafter = getCrafter(reader, pos);
        return crafter == null ? null : crafter.getInput();
    }

    /**
     * 两个终焉合成器是否属于同一个连接组。
     * <p>
     * 这是连接纹理是否显示的<b>唯一</b>判据：相邻并不足以让贴图连接，
     * 必须先用扳手把两者并入同一个 ConnectedInput 控制组。
     * 对应原版 {@code CrafterHelper.areCraftersConnected}。
     */
    public static boolean areCraftersConnected(BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos) {
        ExtremeConnectedInputHandler.ConnectedInput input1 = getInput(reader, pos);
        ExtremeConnectedInputHandler.ConnectedInput input2 = getInput(reader, otherPos);

        if (input1 == null || input2 == null) {
            return false;
        }
        if (input1.getData().isEmpty() || input2.getData().isEmpty()) {
            return false;
        }
        try {
            if (pos.offset(input1.getData().get(0))
                    .equals(otherPos.offset(input2.getData().get(0)))) {
                return true;
            }
        } catch (IndexOutOfBoundsException e) {
            // 渲染线程与逻辑线程竞争：data 可能在两次检查之间被清空
        }

        return false;
    }
}
