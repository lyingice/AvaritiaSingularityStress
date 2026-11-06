package committee.nova.avaritia_singularity_stress.content.kinetics.link;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.mods.avaritia_integration.integrations.create.registry.CreateIntegrationBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * B18 水晶矩阵机壳包壳轴：用无尽集成（AvaritiaIntegration）的
 * {@code crystal_matrix_casing} 把传动杆封起来。
 * <p>
 * <b>Create 的封装机制</b>（allapis 6.0.11 源码）：
 * <ol>
 *   <li>可被封装的原方块实现 {@code EncasableBlock}（传动杆就是），玩家右键时走
 *       {@code EncasableBlock.tryEncase}：它去 {@code EncasingRegistry.getVariants(本方块)} 里找
 *       "外壳物品 == 手持物品" 的 encased 变体（{@code EncasableBlock.java:27-39}）。</li>
 *   <li>找到后调 {@code EncasedBlock.handleEncasing}，由 {@code EncasedShaftBlock} 把方块原地替换成
 *       自己的 defaultBlockState（保留 AXIS），整条动力链不断（{@code EncasedShaftBlock.java:82-86}）。</li>
 *   <li>外壳方块本身只是 {@code CasingBlock}（{@code CasingBlock.java}），用扳手潜行右键可以拆回去
 *       （{@code EncasedShaftBlock.onSneakWrenched}，{@code :42-51}）。</li>
 * </ol>
 * <b>所以"新增一种封装"= 注册一个 {@code EncasedShaftBlock} 子类 + 在 {@code EncasingRegistry}
 * 里把原方块和它配对</b>，不需要 mixin，也不需要改 Create 的任何渲染。
 * <p>
 * <b>为什么覆盖 {@code getBlockEntityType()}</b>：Create 的包壳轴共用 {@code ENCASED_SHAFT} 这个方块实体
 * 类型，视觉固定用 {@code AllPartialModels.SHAFT}（原版木轴）。换成自己的类型后，视觉注册可以独立写，
 * 不必跟 Create 的其他包壳轴抢同一个类型；当前轴心仍用原版模型（包壳轴 = 原轴 + 外壳），
 * 外壳由方块模型（{@code block/crystal_matrix_encased_shaft}）负责。
 */
public class CrystalMatrixEncasedShaftBlock extends EncasedShaftBlock {

    public CrystalMatrixEncasedShaftBlock(Properties properties) {
        super(properties, () -> CreateIntegrationBlocks.CRYSTAL_MATRIX_CASING.get());
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CRYSTAL_ENCASED_SHAFT.get();
    }
}
