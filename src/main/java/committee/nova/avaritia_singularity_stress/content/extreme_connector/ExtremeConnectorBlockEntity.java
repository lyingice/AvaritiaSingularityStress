package committee.nova.avaritia_singularity_stress.content.extreme_connector;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B30 终焉连接器实体：被动网络节点，无应力/无 tick。
 * 贴附的合成器由几何关系（前方一格）决定，组网与网格坐标推导见 {@code CraftingTerminalNetwork}。
 */
public class ExtremeConnectorBlockEntity extends BlockEntity {

    public ExtremeConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static ExtremeConnectorBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeConnectorBlockEntity(ModBlockEntities.EXTREME_CONNECTOR.get(), pos, state);
    }

    /** 背贴的合成器实体（位置语义）；不在任何合成器背面则返回 null。 */
    public ExtremeMechanicalCrafterBlockEntity attachedCrafter() {
        if (level == null) {
            return null;
        }
        BlockPos crafterPos = ExtremeConnectorBlock.attachedCrafterPos(level, worldPosition);
        return crafterPos == null ? null : ExtremeCrafterHelper.getCrafter(level, crafterPos);
    }
}
