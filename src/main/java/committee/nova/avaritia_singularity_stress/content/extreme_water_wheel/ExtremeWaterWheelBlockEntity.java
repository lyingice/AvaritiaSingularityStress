package committee.nova.avaritia_singularity_stress.content.extreme_water_wheel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B28 终焉水车方块实体。
 * <p>
 * Create 发电机<b>总应力 = 基础容量 × 转速</b>（GeneratingKineticBlockEntity.java:72-76 的
 * {@code stressTotal = stressBase * speed}；基础容量取 1RPM 基准，BlockStressValues.java:16-18）。
 * 因此：
 * <ul>
 *   <li>{@link #getGeneratedSpeed()} 恒为 256RPM，<b>无需流体</b>即转动（普通水车仅 ±8RPM）；</li>
 *   <li>{@link #calculateAddedStressCapacity()} 读取 Create 给水车注册的基础容量再乘
 *       {@link #BASE_CAPACITY_FACTOR}（5 倍），仍随 Create 配置联动；转速 256 使总应力远大于普通水车；</li>
 *   <li>检测到周围有流体时容量翻倍（"有流体应力翻倍"）。</li>
 * </ul>
 * 流体检测复用父类 {@code getOffsetsToCheck()} 的轮缘偏移；覆写 {@code determineAndApplyFlowScore}
 * 拦截父类 lazyTick 与方块 tick 的调用，改为刷新容量而非按流量调速。
 */
public class ExtremeWaterWheelBlockEntity extends WaterWheelBlockEntity {

    /** 基础容量倍率：Create 水车基础值(默认 32) × 5 = 160 SU/RPM；ModStress 静态查询共用此常量。 */
    public static final float BASE_CAPACITY_FACTOR = 5f;

    private boolean hasFluid;

    public ExtremeWaterWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static ExtremeWaterWheelBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeWaterWheelBlockEntity(ModBlockEntities.EXTREME_WATER_WHEEL.get(), pos, state);
    }

    /** B28 轮体改为固定模组贴图,不再支持 Create 水车的"右键木板换材质"。 */
    @Override
    public ItemInteractionResult applyMaterialIfValid(ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 恒速 256RPM，不依赖流体（普通水车为 clamp(flowScore)*8）。 */
    @Override
    public float getGeneratedSpeed() {
        return 256f;
    }

    @Override
    public float calculateAddedStressCapacity() {
        // 基础应力调整为 Create 水车基础值的 5 倍(默认 32→160)，有流体再翻倍
        float base = (float) BlockStressValues.getCapacity(AllBlocks.WATER_WHEEL.get()) * BASE_CAPACITY_FACTOR;
        float capacity = hasFluid ? base * 2f : base;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    /** 拦截父类的流量调速：只检测流体有无，变化时刷新发电容量。 */
    @Override
    public void determineAndApplyFlowScore() {
        boolean fluid = detectFluid();
        if (fluid != hasFluid) {
            hasFluid = fluid;
            updateGeneratedRotation();
            setChanged();
        }
    }

    private boolean detectFluid() {
        for (BlockPos offset : getOffsetsToCheck()) {
            if (!level.getFluidState(offset.offset(worldPosition)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 首 tick / 重载后启动发电（镜像 CreativeMotorBlockEntity.java:67-71 的启动时机）。 */
    @Override
    public void initialize() {
        super.initialize();
        if (!level.isClientSide) {
            hasFluid = detectFluid();
            updateGeneratedRotation();
        }
    }
}
