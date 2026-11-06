package committee.nova.avaritia_singularity_stress.content.kinetics.link;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * B19 炽骨立方机壳包壳轴：用 Re-Avaritia 的 {@code blaze_cube_block}（炽骨立方）当封装外壳。
 * <p>
 * 封装链路与 B18 完全一致（{@code EncasableBlock.tryEncase} → {@code EncasingRegistry} →
 * {@code handleEncasing}），只是外壳物品换成炽骨立方。
 * <p>
 * 和 B18 一样必须覆盖 {@code getBlockEntityType()} 返回自己的类型：
 * 沿用 Create 的 {@code ENCASED_SHAFT} 会触发 {@code BlockEntity.validateBlockState} 崩溃
 * （Create 那个类型只登记了它自己的方块）。
 */
public class BlazeCubeEncasedShaftBlock extends EncasedShaftBlock {

    public BlazeCubeEncasedShaftBlock(Properties properties) {
        super(properties, () -> committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.BLAZE_CUBE_CASING.get());
    }

    @Override
    public BlockEntityType<? extends BracketedKineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BLAZE_CUBE_ENCASED_SHAFT.get();
    }
}

