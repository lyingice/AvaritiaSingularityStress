package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_common.KineticPeriods;
import committee.nova.avaritia_singularity_stress.content.kinetic_common.OutputChance;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityStress;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * B2 动能中子态素收集器的方块实体。
 * <p>
 * 接入旋转力后持续产出中子材料:RPM 越高越快,消耗的应力也越大。
 * 当所在应力网络满足奇点条件(SSU×64 > SU_actual,且本机位于 B6 输出侧)时
 * 进入 <b>奇点状态</b>:产出周期减半,并解锁超激发态产量加成;
 * 未触发时行为与普通 SU 模式完全一致。
 */
public class KineticNeutronCollectorBlockEntity extends KineticBlockEntity implements MenuProvider {

    /** 最低可运行转速(与共享周期公式一致)。 */
    public static final float MIN_RPM = KineticPeriods.MIN_RPM;
    /** 最高有效转速(与共享周期公式一致)。 */
    public static final float MAX_RPM = KineticPeriods.MAX_RPM;

    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_SSU_AMOUNT = "SsuAmount";
    private static final String NBT_SSU_CAPACITY = "SsuCapacity";
    private static final String NBT_SSU_NEED = "SsuNeed";
    private static final String NBT_SSU_CONCENTRATION = "SsuConcentration";
    private static final String NBT_SSU_CHAIN = "SsuChainLinked";
    private static final String NBT_SSU_QC = "SsuQcSuppressed";
    private static final String NBT_SSU_STATE = "SsuState";

    /** 单格输出槽。 */
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sendData();
        }
    };

    private CollectorTier tier;
    private float progress;
    /** 奇点判定结果:服务端每 tick 由量化引擎刷新,NBT 同步到客户端供护目镜/Jade 显示。 */
    private SingularityStress.SingularityInfo singularityInfo = SingularityStress.SingularityInfo.NONE;
    /** 派生出的奇点状态(四级);由浓度与转速决定,同样同步给客户端。 */
    private SingularityStress.SingularityState singularityState = SingularityStress.SingularityState.NONE;
    private int lastOutputCount;

    /** 同步给 GUI 的进度数据:槽 0 = 进度 tick,槽 1 = 当前周期 tick。 */
    private final SimpleContainerData data = new SimpleContainerData(2);

    public KineticNeutronCollectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = CollectorTier.fromBlock(state.getBlock());
    }

    public static KineticNeutronCollectorBlockEntity create(BlockPos pos, BlockState state) {
        return new KineticNeutronCollectorBlockEntity(
                ModBlockEntities.KINETIC_NEUTRON_COLLECTOR.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.avaritia_singularity_stress." + tier.registryName());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new KineticNeutronCollectorMenu(id, playerInventory, inventory, data);
    }

    public ContainerData getData() {
        return data;
    }

    public CollectorTier getTier() {
        return tier;
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    public float getProgress() {
        return progress;
    }

    public SingularityStress.SingularityInfo getSingularityInfo() {
        return singularityInfo;
    }

    /** 当前奇点状态(四级)。 */
    public SingularityStress.SingularityState getSingularityState() {
        return singularityState;
    }

    /** 当前是否处于奇点状态(浓度已满 100% 且位于 B6 输出侧)。 */
    public boolean isSingularityPowered() {
        return singularityState != SingularityStress.SingularityState.NONE;
    }

    /** 是否进入超激发态(或更高)。 */
    public boolean isOverexcited() {
        return singularityState.atLeast(SingularityStress.SingularityState.OVEREXCITED);
    }

    /** 是否进入终极激发态(仅极限档可解锁)。 */
    public boolean isUltimate() {
        return singularityState == SingularityStress.SingularityState.ULTIMATE;
    }

    /** 由浓度与档位/转速派生奇点状态。 */
    private SingularityStress.SingularityState computeState(SingularityStress.SingularityInfo info) {
        return SingularityStress.stateOf(info.concentration(), info.chainLinked(), getSpeed(),
                tier.overexciteBonus > 0, CollectorTier.OVEREXCITE_RPM,
                tier == CollectorTier.ULTIMATE);
    }

    /**
     * 当前实际产出周期(tick)。
     * <p>
     * 转速的唯一作用就是缩短周期:{@code 周期 = 基准秒 × 20 ÷ 周期缩短倍率}。
     * 奇点态先把基准秒减半,再套用同一条倍率。
     */
    public int currentPeriodTicks() {
        float baseSeconds = tier.baseSeconds;
        if (singularityState != SingularityStress.SingularityState.NONE) {
            baseSeconds /= CollectorTier.SSU_PERIOD_DIVISOR;
        }
        return Math.max(1, Mth.ceil(baseSeconds * 20.0F / KineticPeriods.periodDivisor(getSpeed())));
    }

    /** 当前一次产出的物品数量(基础 1 + 超激发态加成 + 终极激发态加成)。 */
    public int currentOutputCount() {
        int count = 1;
        if (singularityState.atLeast(SingularityStress.SingularityState.OVEREXCITED)) {
            count += tier.overexciteBonus;
        }
        if (singularityState == SingularityStress.SingularityState.ULTIMATE) {
            count += SingularityStress.ULTIMATE_BONUS;
        }
        return count;
    }

    /** 剩余 tick 数,供 Jade 显示。 */
    public int remainingTicks() {
        return Math.max(0, currentPeriodTicks() - Mth.floor(progress));
    }

    /** 可输出物品预览(未运行时也能显示)。 */
    public ItemStack previewOutput() {
        ItemStack base = tier.outputStack();
        return base.isEmpty() ? ItemStack.EMPTY : base.copyWithCount(currentOutputCount());
    }

    /** 供 Jade 展示的"可能产出"列表:收集器为确定性单项(概率 100%)。 */
    public List<OutputChance> outputChances() {
        ItemStack output = previewOutput();
        return output.isEmpty() ? List.of() : List.of(new OutputChance(output, 1.0F));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat(NBT_PROGRESS, progress);
        compound.putInt("OutputCount", lastOutputCount);
        compound.put("Inventory", inventory.serializeNBT(registries));
        // 奇点判定结果同步到客户端(护目镜在客户端侧读取)。
        compound.putFloat(NBT_SSU_AMOUNT, singularityInfo.ssuAmount());
        compound.putFloat(NBT_SSU_CAPACITY, singularityInfo.capacity());
        compound.putFloat(NBT_SSU_NEED, singularityInfo.needSsu());
        compound.putFloat(NBT_SSU_CONCENTRATION, singularityInfo.concentration());
        compound.putBoolean(NBT_SSU_CHAIN, singularityInfo.chainLinked());
        compound.putBoolean(NBT_SSU_QC, singularityInfo.qcSuppressed());
        compound.putInt(NBT_SSU_STATE, singularityState.ordinal());
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        progress = compound.getFloat(NBT_PROGRESS);
        lastOutputCount = compound.getInt("OutputCount");
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        singularityInfo = new SingularityStress.SingularityInfo(
                compound.getFloat(NBT_SSU_AMOUNT),
                compound.getFloat(NBT_SSU_CAPACITY),
                compound.getFloat(NBT_SSU_NEED),
                compound.getFloat(NBT_SSU_CONCENTRATION),
                compound.getBoolean(NBT_SSU_CHAIN),
                compound.getBoolean(NBT_SSU_QC));
        SingularityStress.SingularityState[] states = SingularityStress.SingularityState.values();
        int stateOrdinal = compound.getInt(NBT_SSU_STATE);
        singularityState = stateOrdinal >= 0 && stateOrdinal < states.length
                ? states[stateOrdinal] : SingularityStress.SingularityState.NONE;
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        SingularityStress.SingularityInfo info = SingularityStress.analyze(this);
        SingularityStress.SingularityState state = computeState(info);
        if (!info.equals(singularityInfo) || state != singularityState) {
            singularityInfo = info;
            singularityState = state;
            sendData();
        }

        // 同步 GUI 数据(进度 / 周期)。
        data.set(0, Mth.floor(progress));
        data.set(1, currentPeriodTicks());

        float speed = Math.abs(getSpeed());
        if (speed < MIN_RPM) {
            // 未达最低转速:不产出,进度缓慢回落。
            progress = Math.max(0, progress - 2);
            data.set(0, Mth.floor(progress));
            return;
        }

        // 转速的作用已经完全体现在被缩短的周期(currentPeriodTicks)里,
        // 因此进度按 1 tick/tick 正常推进即可;此处再乘周期倍率会变成二次加速。
        progress += 1.0F;
        int period = currentPeriodTicks();
        int count = currentOutputCount();

        if (progress >= period) {
            progress = 0;
            if (tryProduce(count)) {
                setChanged();
                sendData();
            } else {
                // 输出槽满:挂起进度,避免无限堆积。
                progress = period;
            }
        }
    }

    /** 尝试向输出槽放入产量;放不下则返回 false。 */
    private boolean tryProduce(int count) {
        ItemStack output = tier.outputStack();
        if (output.isEmpty()) {
            return false;
        }

        ItemStack existing = inventory.getStackInSlot(0);
        if (existing.isEmpty()) {
            inventory.setStackInSlot(0, output.copyWithCount(count));
            lastOutputCount = count;
            return true;
        }
        if (ItemStack.isSameItemSameComponents(existing, output)
                && existing.getCount() + count <= existing.getMaxStackSize()) {
            existing.grow(count);
            inventory.setStackInSlot(0, existing);
            lastOutputCount = count;
            return true;
        }
        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!IRotate.StressImpact.isEnabled()) {
            return false;
        }
        float stressAtBase = calculateStressApplied();
        if (Mth.equal(stressAtBase, 0)) {
            return false;
        }

        // 原版 SU 组:动力学状态: / 应力影响: / N su 当前速度值
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        // ssu 组:奇点应力: / 奇点应力影响: / N ssu 当前速度值 / 奇点浓度:xx.xx% / 状态:...
        SingularityStress.addSingularityStats(tooltip, stressAtBase, getSpeed(),
                singularityInfo, singularityState);
        return true;
    }
}
