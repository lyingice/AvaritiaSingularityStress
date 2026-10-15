package committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter;

import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 终焉锻造器(B7)。
 * <p>
 * 行为与 {@link ExtremeMechanicalCrafterBlock} 完全一致(继承而来),唯一区别:
 * 它出现在合成链中时,该链会启用<b>终焉锻造配方</b>({@code avaritia:extreme_smithing})。
 * 典型用法:放在四个终焉动力合成器的中间,四向 4 格 + 中心 1 格 = 5 格,
 * 正好对应锻造配方的 5 个输入(模板 / 基座 / 3 个添加物)。
 * <p>
 * 外观使用本模组 {@code extreme_smithing_crafter} 目录下的三张纹理。
 * <p>
 * 只覆写方块实体类型;方块实体类由父类 IBE 通过 {@code isInstance} 判定,
 * 子类实例天然通过,无需覆写 {@code getBlockEntityClass()}。
 */
public class ExtremeSmithingCrafterBlock extends ExtremeMechanicalCrafterBlock {

    public ExtremeSmithingCrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends ExtremeMechanicalCrafterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_SMITHING_CRAFTER.get();
    }
}
