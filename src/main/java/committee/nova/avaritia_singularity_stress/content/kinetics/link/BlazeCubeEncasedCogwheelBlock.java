package committee.nova.avaritia_singularity_stress.content.kinetics.link;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * B19 炽骨立方机壳包壳齿轮（小 / 大共用，靠 large 区分）。
 * <p>
 * 与水晶矩阵那套同构（CrystalMatrixEncasedCogwheelBlock），差别只有两处：
 * 外壳物品换成本模组的 blaze_cube_casing、方块实体类型换成炽骨自己的。
 * <p>
 * 封装链路：CogWheelBlock.useItemOn -> tryEncase（CogWheelBlock.java:123）
 * -> EncasingRegistry.getVariants 找到本方块 -> handleEncasing 原地替换。
 * 没有这条登记，手持机壳右键齿轮就是"什么都不做"（不是坏了，是查表查不到）。
 * <p>
 * 必须覆盖 getBlockEntityType()：EncasedCogwheelBlock 默认返回 Create 的
 * ENCASED_COGWHEEL / ENCASED_LARGE_COGWHEEL，那两个类型只登记了 Create 自己的方块，
 * NeoForge 的 BlockEntity.validateBlockState 会判定"类型不承认该方块"并直接崩服。
 */
public class BlazeCubeEncasedCogwheelBlock extends EncasedCogwheelBlock {

    private final boolean large;

    public BlazeCubeEncasedCogwheelBlock(Properties properties, boolean large) {
        super(properties, large,
                () -> committee.nova.avaritia_singularity_stress.init.registry.ModBlocks.BLAZE_CUBE_CASING.get());
        this.large = large;
    }

    @Override
    public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType() {
        return large
                ? ModBlockEntities.BLAZE_ENCASED_LARGE_COGWHEEL.get()
                : ModBlockEntities.BLAZE_ENCASED_COGWHEEL.get();
    }
}
