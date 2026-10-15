package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_common.KineticPeriods;
import committee.nova.avaritia_singularity_stress.content.kinetic_common.OutputChance;
import committee.nova.avaritia_singularity_stress.content.singularity.SingularityStress;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.mods.avaritia.api.common.crafting.ICompressorRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * B4 动能中子态素压缩机的方块实体。
 * <p>
 * 使用应力把矿物压缩为对应的奇点:复用无尽贪婪的 {@code avaritia:compressor_recipe} 配方,
 * 因此产物与无尽贪婪原生压缩机一致。RPM 越高产出越快,并且达到 64/128/256 时获得
 * 原材料减免;奇点状态下(SSU×64 > SU_actual 且位于 B6 输出侧)额外解锁双倍/四倍产出。
 */
public class KineticNeutronCompressorBlockEntity extends KineticBlockEntity implements MenuProvider {

    public static final float MIN_RPM = KineticPeriods.MIN_RPM;
    public static final float MAX_RPM = KineticPeriods.MAX_RPM;

    private static final int SLOT_INPUT = 0;
    private static final int SLOT_OUTPUT = 1;
    private static final String NBT_MATERIAL_COUNT = "MaterialCount";
    private static final String NBT_MATERIAL_STACK = "MaterialStack";
    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_SSU_AMOUNT = "SsuAmount";
    private static final String NBT_SSU_CAPACITY = "SsuCapacity";
    private static final String NBT_SSU_NEED = "SsuNeed";
    private static final String NBT_SSU_CONCENTRATION = "SsuConcentration";
    private static final String NBT_SSU_CHAIN = "SsuChainLinked";
    private static final String NBT_SSU_QC = "SsuQcSuppressed";
    private static final String NBT_SSU_STATE = "SsuState";

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_INPUT && isCompressible(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sendData();
        }
    };

    private CompressorTier tier;
    private ICompressorRecipe activeRecipe;
    private ItemStack materialStack = ItemStack.EMPTY;
    private int materialCount;
    private float progress;
    /** 奇点判定结果:服务端每 tick 由量化引擎刷新,NBT 同步到客户端供护目镜/Jade 显示。 */
    private SingularityStress.SingularityInfo singularityInfo = SingularityStress.SingularityInfo.NONE;
    /** 派生出的奇点状态(四级);由浓度与转速决定,同样同步给客户端。 */
    private SingularityStress.SingularityState singularityState = SingularityStress.SingularityState.NONE;
    /** 上一轮产出数量,供 Jade 展示。 */
    private int lastOutputCount;

    private final SimpleContainerData data = new SimpleContainerData(4);

    public KineticNeutronCompressorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = CompressorTier.fromBlock(state.getBlock());
    }

    public static KineticNeutronCompressorBlockEntity create(BlockPos pos, BlockState state) {
        return new KineticNeutronCompressorBlockEntity(
                ModBlockEntities.KINETIC_NEUTRON_COMPRESSOR.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.avaritia_singularity_stress." + tier.registryName());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new KineticNeutronCompressorMenu(id, playerInventory, inventory, data, getBlockPos());
    }

    public ContainerData getData() {
        return data;
    }

    public CompressorTier getTier() {
        return tier;
    }

    public IItemHandler getInventory() {
        return inventory;
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

    public int getMaterialCount() {
        return materialCount;
    }

    public boolean hasRecipe() {
        return activeRecipe != null;
    }

    /** 是否进入超激发态(或更高):压缩机以 >=64 RPM 为超激发阈值。 */
    public boolean isOverexcited() {
        return singularityState.atLeast(SingularityStress.SingularityState.OVEREXCITED);
    }

    /** 是否进入终极激发态(仅极限档在 256 RPM 解锁)。 */
    public boolean isUltimate() {
        return singularityState == SingularityStress.SingularityState.ULTIMATE;
    }

    /** 由浓度与档位/转速派生奇点状态(压缩机无 overexciteBonus,四档均可超激发)。 */
    private SingularityStress.SingularityState computeState(SingularityStress.SingularityInfo info) {
        return SingularityStress.stateOf(info.concentration(), info.chainLinked(), getSpeed(),
                true, CompressorTier.RPM_THRESHOLDS[0],
                tier == CompressorTier.ULTIMATE);
    }

    // ------------------------------------------------------------------
    // 进度 / 周期 计算
    // ------------------------------------------------------------------

    /**
     * 当前产出周期(tick)。
     * <p>
     * 转速的唯一作用是缩短周期:{@code 周期 = 基准 tick ÷ 周期缩短倍率}。
     * 奇点态先把基准 tick 减半,再套用同一条倍率。
     */
    public int currentPeriodTicks() {
        float base = CompressorTier.BASE_TICKS;
        if (singularityState != SingularityStress.SingularityState.NONE) {
            base /= CompressorTier.SSU_PERIOD_DIVISOR;
        }
        return Math.max(1, Mth.ceil(base / KineticPeriods.periodDivisor(getSpeed())));
    }

    /** 当前配方所需原材料总数(含 RPM 效能减免)。 */
    public int requiredMaterial() {
        if (activeRecipe == null) {
            return 0;
        }
        float reduction = tier.materialReduction(getSpeed());
        return Math.max(1, Mth.ceil(activeRecipe.getInputCount() * (1.0F - reduction)));
    }

    public int remainingTicks() {
        return Math.max(0, currentPeriodTicks() - Mth.floor(progress));
    }

    /** 预览下一个产物(含双倍/四倍期望数量),供 Jade 第二行显示。 */
    public ItemStack previewOutput() {
        if (activeRecipe == null || level == null) {
            return ItemStack.EMPTY;
        }
        ItemStack base = activeRecipe.getResultItem(level.registryAccess());
        if (base.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int multiplier = expectedMultiplier();
        return base.copyWithCount(base.getCount() * Math.max(1, multiplier));
    }

    private int expectedMultiplier() {
        if (singularityState == SingularityStress.SingularityState.NONE) {
            return 1;
        }
        // Create 的速度是有符号的(齿轮啮合/反向目标转速都会翻转符号),
        // 这里必须取绝对值,与 rpmTierIndex / periodDivisor / stateOf 保持一致。
        float rpm = Math.abs(getSpeed());
        if (rpm < CompressorTier.RPM_THRESHOLDS[0]) {
            return 1;
        }
        // 期望倍率 = 1 + 双倍概率 + 2×四倍概率(概率为独立触发,四倍在双倍基础上再翻倍)。
        float d = tier.doubleChance(rpm);
        float q = tier.quadChance(rpm);
        return Math.max(1, Math.round(1.0F + d + 2.0F * q));
    }

    /**
     * 供 Jade 展示的"可能产出"列表:每项为一个(产物数量, 概率)。
     * <p>
     * 非奇点态或 |转速| 未达 64 RPM 时只有单倍 100%;奇点态下按 双倍/四倍 的独立掷骰展开:
     * {@code P(四倍) = q}、{@code P(双倍) = (1−q)·d}、{@code P(单倍) = (1−q)(1−d)}。
     * 四倍不可骰时(q=0)自动退化为两项。
     */
    public List<OutputChance> outputChances() {
        if (activeRecipe == null || level == null) {
            return List.of();
        }
        ItemStack base = activeRecipe.getResultItem(level.registryAccess());
        if (base.isEmpty()) {
            return List.of();
        }

        // 转速取绝对值:反转运行(负转速)同样算"达到 RPM 档位"。
        float rpm = Math.abs(getSpeed());
        float d = 0.0F;
        float q = 0.0F;
        if (singularityState != SingularityStress.SingularityState.NONE
                && rpm >= CompressorTier.RPM_THRESHOLDS[0]) {
            d = Mth.clamp(tier.doubleChance(rpm), 0.0F, 1.0F);
            q = Mth.clamp(tier.quadChance(rpm), 0.0F, 1.0F);
        }

        int unit = base.getCount();
        List<OutputChance> result = new java.util.ArrayList<>(3);
        if (q > 0.0F) {
            result.add(new OutputChance(base.copyWithCount(unit * 4), q));
        }
        float doubleChance = (1.0F - q) * d;
        if (doubleChance > 0.0F) {
            result.add(new OutputChance(base.copyWithCount(unit * 2), doubleChance));
        }
        float singleChance = (1.0F - q) * (1.0F - d);
        if (singleChance > 0.0F) {
            result.add(new OutputChance(base.copyWithCount(unit), singleChance));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            return;
        }

        SingularityStress.SingularityInfo info = SingularityStress.analyze(this);
        SingularityStress.SingularityState state = computeState(info);
        if (!info.equals(singularityInfo) || state != singularityState) {
            singularityInfo = info;
            singularityState = state;
            sendData();
        }

        // 1. 解析配方:输入槽里能压缩的物品。
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (materialStack.isEmpty() && !input.isEmpty() && isCompressible(input)) {
            materialStack = input.copyWithCount(1);
            setChanged();
        }

        if (materialStack.isEmpty()) {
            activeRecipe = null;
            progress = 0;
            syncData();
            return;
        }

        if (activeRecipe == null && !findRecipe()) {
            syncData();
            return;
        }

        int required = requiredMaterial();

        // 2. 累积材料:从输入槽搬运到内部材料计数。
        if (materialCount < required && !input.isEmpty()
                && ItemStack.isSameItemSameComponents(input, materialStack)) {
            int consume = Math.min(input.getCount(), required - materialCount);
            input.shrink(consume);
            materialCount += consume;
            inventory.setStackInSlot(SLOT_INPUT, input);
            setChanged();
        }

        syncData();

        float speed = Math.abs(getSpeed());
        if (speed < MIN_RPM || materialCount < required) {
            return;
        }

        // 3. 推进进度。转速的作用已在 currentPeriodTicks 里缩短周期,
        // 故进度按 1 tick/tick 推进,避免二次加速。
        progress += 1.0F;
        int period = currentPeriodTicks();
        if (progress < period) {
            return;
        }

        progress = 0;
        if (produce(required)) {
            setChanged();
            sendData();
        } else {
            // 输出槽放不下:挂起。
            progress = period;
        }
    }

    private void syncData() {
        data.set(0, Mth.floor(progress));
        data.set(1, currentPeriodTicks());
        // 材料条:当前已收集材料 / 配方所需材料(含 RPM 效能减免)。
        data.set(2, materialCount);
        data.set(3, requiredMaterial());
    }

    private boolean findRecipe() {
        if (level == null || materialStack.isEmpty()) {
            return false;
        }
        CraftingInput craftingInput = CraftingInput.of(1, 1, List.of(materialStack));
        Optional<RecipeHolder<ICompressorRecipe>> holder = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.COMPRESSOR_RECIPE.get(), craftingInput, level);
        activeRecipe = holder.map(RecipeHolder::value).orElse(null);
        return activeRecipe != null;
    }

    /** 消耗材料并产出;返回是否成功(输出槽放得下)。 */
    private boolean produce(int required) {
        ItemStack base = activeRecipe.getResultItem(level.registryAccess());
        if (base.isEmpty()) {
            return false;
        }

        int amount = base.getCount() * rollMultiplier();
        ItemStack output = base.copyWithCount(amount);

        ItemStack existing = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(existing, output)
                    || existing.getCount() + amount > existing.getMaxStackSize()) {
                return false;
            }
            existing.grow(amount);
            inventory.setStackInSlot(SLOT_OUTPUT, existing);
        } else {
            inventory.setStackInSlot(SLOT_OUTPUT, output);
        }

        lastOutputCount = amount;
        materialCount -= required;
        if (materialCount <= 0) {
            materialCount = 0;
            materialStack = ItemStack.EMPTY;
            activeRecipe = null;
        }
        return true;
    }

    /** 掷骰决定本次产出倍率:1 / 2 / 4。仅在奇点状态下生效;转速取绝对值(反转同样有效)。 */
    private int rollMultiplier() {
        if (singularityState == SingularityStress.SingularityState.NONE || level == null) {
            return 1;
        }
        float rpm = Math.abs(getSpeed());
        if (rpm < CompressorTier.RPM_THRESHOLDS[0]) {
            return 1;
        }
        if (level.random.nextFloat() < tier.quadChance(rpm)) {
            return 4;
        }
        if (level.random.nextFloat() < tier.doubleChance(rpm)) {
            return 2;
        }
        return 1;
    }

    private boolean isCompressible(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        CraftingInput craftingInput = CraftingInput.of(1, 1, List.of(stack));
        return level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.COMPRESSOR_RECIPE.get(), craftingInput, level)
                .isPresent();
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat(NBT_PROGRESS, progress);
        compound.putInt(NBT_MATERIAL_COUNT, materialCount);
        compound.put("MaterialStack", materialStack.saveOptional(registries));
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("LastOutput", lastOutputCount);
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
        materialCount = compound.getInt(NBT_MATERIAL_COUNT);
        materialStack = ItemStack.parseOptional(registries, compound.getCompound(NBT_MATERIAL_STACK));
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        lastOutputCount = compound.getInt("LastOutput");
        activeRecipe = null;
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!IRotate.StressImpact.isEnabled()) {
            return false;
        }
        float stressAtBase = calculateStressApplied();
        if (Mth.equal(stressAtBase, 0)) {
            return false;
        }

        // 原版 SU 组 + ssu 组(含奇点浓度与状态)。
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        SingularityStress.addSingularityStats(tooltip, stressAtBase, getSpeed(),
                singularityInfo, singularityState);

        if (isOverexcited()) {
            SingularityStress.lang().translate("kinetic_neutron_compressor.goggles.efficiency",
                            String.format("%.0f%%", tier.materialReduction(getSpeed()) * 100.0F))
                    .style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
    public ItemStack getMaterialStack() {
        return materialStack;
    }

    public boolean hasMaterialStack() {
        return !materialStack.isEmpty();
    }
}
