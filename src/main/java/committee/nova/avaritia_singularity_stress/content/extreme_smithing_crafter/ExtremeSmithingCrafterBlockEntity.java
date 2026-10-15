package committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 终焉锻造器方块实体。
 * <p>
 * 状态机、库存、连接组、渲染数据全部继承自
 * {@link ExtremeMechanicalCrafterBlockEntity},不新增任何行为。
 * 它的存在意义是让「终焉锻造配方」的启用判定能通过方块类型区分出锻造器,
 * 同时拥有独立的方块实体类型与独立的连接组数据。
 */
public class ExtremeSmithingCrafterBlockEntity extends ExtremeMechanicalCrafterBlockEntity {

    public ExtremeSmithingCrafterBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

    /** 方块实体工厂,供注册表使用。 */
    public static ExtremeSmithingCrafterBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeSmithingCrafterBlockEntity(
                committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities
                        .EXTREME_SMITHING_CRAFTER.get(),
                pos,
                state);
    }
}
