package committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityConfig;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityStress;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * B6 奇点压缩器的方块实体(转速控制器的重构版)。
 * <p>
 * 传动逻辑完全继承 Create 转速控制器(输入端接外部应力网络,输出端经上方大齿轮传动杆注入下游)。
 * 在此之上实现压缩行为:
 * <ul>
 *   <li>把<b>空闲应力</b>(网络总产能 − 全网实际消耗)中超过门槛
 *       {@link SingularityConfig#capacityGate()} 的部分,按压缩比
 *       {@link SingularityConfig#compressionRatio()} 压缩为本台产出:
 *       {@code 产出 = max(0, 空闲应力 − 门槛) / 压缩比}。
 *       SSU 只沿本机输出侧的<b>子链路</b>传递,不全网共享;</li>
 *   <li>压缩伴随区间浮动的品控损耗:
 *       {@code 损耗 = qualityLossMin + (qualityLossMax − qualityLossMin) × 空闲比例};
 *       上链路中存在 B14 时损耗归零;</li>
 *   <li>下游机器由 {@link SingularityStress#analyze} 算出各自的奇点浓度与状态。</li>
 * </ul>
 * 自身占用通过覆写 {@link #calculateStressApplied()} 实现,值只取决于本类缓存字段,
 * 绝不反向访问网络(避免在网络装配回调中递归);缓存值由 tick 经
 * {@code updateStressFor} 主动刷新。
 * <p>
 * 注意反馈滞后:占用率(含品控损耗)影响全网消耗 → 影响空闲比例 → 影响损耗,
 * 每 tick 迭代一轮,数值会快速收敛。
 */
public class SingularityRotationSpeedControllerBlockEntity extends SpeedControllerBlockEntity {

    private static final String NBT_SSU_OWNS_STRESS = "SsuOwnStress";
    private static final String NBT_SSU_NETWORK = "SsuNetwork";
    private static final String NBT_SSU_CAPACITY = "SsuCapacity";
    private static final String NBT_SSU_IDLE = "SsuIdle";
    private static final String NBT_SSU_QC_LOSS = "SsuQcLoss";

    /** 服务端每 tick 依据网络快照刷新;客户端由 NBT 同步,仅用于护目镜显示。 */
    private float qcLoss;
    /** 本台 B6 的 ssu 产出(同步给护目镜)。 */
    private float networkSsu;
    /** 本网络总产能 su(同步给护目镜)。 */
    private float networkCapacity;
    /** 本网络空闲应力 su = 产能 − 消耗(同步给护目镜)。 */
    private float networkIdle;

    public SingularityRotationSpeedControllerBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 方块实体工厂,供注册表使用。 */
    public static SingularityRotationSpeedControllerBlockEntity create(BlockPos pos, BlockState state) {
        return new SingularityRotationSpeedControllerBlockEntity(
                ModBlockEntities.SINGULARITY_ROTATION_SPEED_CONTROLLER.get(), pos, state);
    }

    /**
     * 本台 B6 自身的应力占用率(SU/RPM):基础占用 + 品控损耗。
     * <p>
     * 这是"每 RPM"的率值,实际应力 = 本值 × |转速|(随转速放大,由 Create 计算)。
     * 品控损耗取自缓存字段(由 tick 刷新)——本方法在 network.add/addSilently
     * 装配回调中会被调用,必须保持无副作用、无网络访问。
     */
    public float computeOwnOccupancy() {
        return SingularityConfig.b6StressPerRpm() + qcLoss;
    }

    @Override
    public float calculateStressApplied() {
        float occupancy = computeOwnOccupancy();
        this.lastStressApplied = occupancy;
        return occupancy;
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        SingularityStress.NetworkSnapshot snapshot = SingularityStress.snapshotOf(this);
        SingularityStress.CompressorInfo info = SingularityStress.compressorInfoOf(this);
        boolean dirty = false;

        if (!Mth.equal(info.qcLoss(), qcLoss)) {
            qcLoss = info.qcLoss();
            dirty = true;
        }
        if (!Mth.equal(info.production(), networkSsu)) {
            networkSsu = info.production();
            dirty = true;
        }
        if (!Mth.equal(snapshot.capacity(), networkCapacity)) {
            networkCapacity = snapshot.capacity();
            dirty = true;
        }
        if (!Mth.equal(snapshot.idleStress(), networkIdle)) {
            networkIdle = snapshot.idleStress();
            dirty = true;
        }

        // 占用率变化(品控损耗浮动/B14 抑制)时刷新网络缓存,使 SU_actual 与应力表保持一致。
        KineticNetwork network = getOrCreateNetwork();
        if (network != null) {
            float desired = computeOwnOccupancy();
            Float cached = network.members.get(this);
            if (cached == null || !Mth.equal(cached, desired)) {
                network.updateStressFor(this, desired);
                dirty = true;
            }
        }

        if (dirty) {
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat(NBT_SSU_NETWORK, networkSsu);
        compound.putFloat(NBT_SSU_CAPACITY, networkCapacity);
        compound.putFloat(NBT_SSU_IDLE, networkIdle);
        compound.putFloat(NBT_SSU_QC_LOSS, qcLoss);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        networkSsu = compound.getFloat(NBT_SSU_NETWORK);
        networkCapacity = compound.getFloat(NBT_SSU_CAPACITY);
        networkIdle = compound.getFloat(NBT_SSU_IDLE);
        // 旧存档无此键:回退品控损耗下限,首个 tick 即会被快照纠正
        qcLoss = compound.contains(NBT_SSU_QC_LOSS)
                ? compound.getFloat(NBT_SSU_QC_LOSS)
                : SingularityConfig.qualityLossMin();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        if (Math.abs(getTheoreticalSpeed()) < SingularityStress.OPERATING_RPM) {
            SingularityStress.lang()
                    .translate("singularity.goggles.idle")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            return true;
        }

        // 本台压缩量由空闲应力驱动:产出 = max(0, 空闲应力 − 门槛) / 压缩比。
        SingularityStress.lang()
                .translate("singularity.goggles.compressing",
                        LangNumberFormat.format(networkSsu),
                        LangNumberFormat.format(networkIdle))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        if (networkSsu <= 0.0F) {
            // 空闲应力未过门槛:明确告诉玩家"还差多少空闲应力",而不是含糊其辞。
            SingularityStress.lang()
                    .translate("singularity.goggles.below_gate",
                            LangNumberFormat.format(SingularityConfig.capacityGate()))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
        if (qcLoss <= 0.0F) {
            SingularityStress.lang()
                    .translate("singularity.goggles.qc_suppressed")
                    .style(ChatFormatting.GREEN)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();

        // 阶段 1 验证钩子:确认 B6 输出侧方块的来源链能被回溯到本装置。
        if (!SingularityStress.DEBUG_SOURCE_CHAIN || level == null || level.isClientSide)
            return;

        for (Direction side : Direction.values()) {
            BlockPos neighbourPos = worldPosition.relative(side);
            if (level.getBlockEntity(neighbourPos) instanceof KineticBlockEntity neighbour) {
                AvaritiaSingularityStressMod.LOGGER.info(
                        "[ssu-debug] {} 由 SSU 驱动 = {}",
                        neighbourPos, SingularityStress.isSingularityPowered(neighbour));
            }
        }
    }
}