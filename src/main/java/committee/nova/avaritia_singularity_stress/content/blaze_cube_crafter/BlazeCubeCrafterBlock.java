package committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * B26 炽骨立方合成器：沿用终焉动力合成器的交互与动能行为，仅开放前两档工作台配方。
 * 连接权限统一由 ExtremeCrafterHelper 判定，避免混入终焉合成链后借用高阶或锻造配方。
 * Create 的 IBE.java:63-85 通过实体类型创建实例、通过 isInstance 接受子类，因此只需覆写类型。
 */
public class BlazeCubeCrafterBlock extends ExtremeMechanicalCrafterBlock {

    public BlazeCubeCrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends ExtremeMechanicalCrafterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BLAZE_CUBE_CRAFTER.get();
    }
}
