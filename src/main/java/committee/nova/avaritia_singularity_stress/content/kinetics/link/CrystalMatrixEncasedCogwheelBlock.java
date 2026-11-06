package committee.nova.avaritia_singularity_stress.content.kinetics.link;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.mods.avaritia_integration.integrations.create.registry.CreateIntegrationBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * B18 水晶矩阵机壳包壳齿轮（小 / 大共用这个类，靠 {@code large} 区分）。
 * <p>
 * Create 的封装链路与传动杆一致（见 {@link CrystalMatrixEncasedShaftBlock}）：
 * 可封装方块（{@code CogWheelBlock}）在 {@code useItemOn} 里调 {@code tryEncase}（{@code CogWheelBlock.java:123}），
 * 从 {@code EncasingRegistry} 找到本方块，再 {@code handleEncasing} 原地替换。
 * <p>
 * <b>为什么必须覆盖 {@code getBlockEntityType()}</b>：{@code EncasedCogwheelBlock} 默认返回 Create 的
 * {@code ENCASED_COGWHEEL} / {@code ENCASED_LARGE_COGWHEEL}，而那两个类型只登记了 Create 自己的方块；
 * NeoForge 的 {@code BlockEntity.validateBlockState} 会判定"该类型不承认这个方块"并直接抛
 * {@code IllegalStateException}（实机崩过：{@code Invalid block entity create:encased_cogwheel ... got Block{
 * ...crystal_matrix_encased_cogwheel}}）。所以小 / 大各需要自己的类型。
 */
public class CrystalMatrixEncasedCogwheelBlock extends EncasedCogwheelBlock {

    private final boolean large;

    public CrystalMatrixEncasedCogwheelBlock(Properties properties, boolean large) {
        super(properties, large, () -> CreateIntegrationBlocks.CRYSTAL_MATRIX_CASING.get());
        this.large = large;
    }

    @Override
    public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType() {
        return large
                ? ModBlockEntities.CRYSTAL_ENCASED_LARGE_COGWHEEL.get()
                : ModBlockEntities.CRYSTAL_ENCASED_COGWHEEL.get();
    }
}
