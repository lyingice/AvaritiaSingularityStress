package committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** B26 独立实体类型保留自己的库存与动画，合成状态机复用终焉动力合成器。 */
public class BlazeCubeCrafterBlockEntity extends ExtremeMechanicalCrafterBlockEntity {

    public BlazeCubeCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static BlazeCubeCrafterBlockEntity create(BlockPos pos, BlockState state) {
        return new BlazeCubeCrafterBlockEntity(ModBlockEntities.BLAZE_CUBE_CRAFTER.get(), pos, state);
    }
}
