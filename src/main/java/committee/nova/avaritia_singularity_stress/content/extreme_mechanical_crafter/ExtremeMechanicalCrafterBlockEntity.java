package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.SmartInventory;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeConnectedInputHandler.ConnectedInput;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.TerminalCraftOutcome;

import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 终焉动力合成器——方块实体。
 * <p>
 * 这是<b>完整复制</b> Create {@code MechanicalCrafterBlockEntity} 的独立实现，
 * 不继承 Create 合成器的方块实体，因此不受其父类对 {@code AllBlocks.MECHANICAL_CRAFTER}
 * 与静态 {@code getTargetDirection} 的硬编码影响。
 * <p>
 * 与普通机械合成器的唯一差异：{@link ExtremeRecipeGridHandler#tryToApplyRecipe}
 * 只匹配无尽贪婪分级配方。状态机、连接、库存、输出行为全部与原版一致。
 */
public class ExtremeMechanicalCrafterBlockEntity extends KineticBlockEntity
        implements TransformableBlockEntity {

    /** 合成阶段。与原版一一对应。 */
    public enum Phase {
        IDLE, ACCEPTING, ASSEMBLING, EXPORTING, WAITING, CRAFTING, INSERTING
    }

    /**
     * 单格库存。
     * <p>
     * {@code stackSize = 1} 是关键：每格最多只放 1 个物品，
     * 这样才能把多个合成器的格子拼成一张配方网格。
     */
    public static class Inventory extends SmartInventory {

        private final ExtremeMechanicalCrafterBlockEntity blockEntity;

        public Inventory(ExtremeMechanicalCrafterBlockEntity blockEntity) {
            super(1, blockEntity, 1, false);
            this.blockEntity = blockEntity;
            forbidExtraction();
            whenContentsChanged(slot -> {
                if (getItem(slot).isEmpty()) {
                    return;
                }
                if (blockEntity.phase == Phase.IDLE) {
                    blockEntity.checkCompletedRecipe(false);
                }
            });
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (blockEntity.phase != Phase.IDLE) {
                return stack;
            }
            if (blockEntity.covered) {
                return stack;
            }
            ItemStack insertItem = super.insertItem(slot, stack, simulate);
            if (insertItem.getCount() != stack.getCount() && !simulate) {
                blockEntity.getLevel()
                        .playSound(null, blockEntity.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                                SoundSource.BLOCKS, .25f, .5f);
            }
            return insertItem;
        }
    }

    protected Inventory inventory;
    protected GroupedItems groupedItems = new GroupedItems();
    protected ConnectedInput input = new ConnectedInput();
    @Nullable
    protected IItemHandler invCap;
    protected boolean reRender;
    protected Phase phase;
    protected int countDown;
    protected boolean covered;
    protected boolean wasPoweredBefore;

    /** 合成前的输入阵列，仅客户端渲染使用。 */
    protected GroupedItems groupedItemsBeforeCraft;

    private InvManipulationBehaviour inserting;
    private EdgeInteractionBehaviour connectivity;

    private ItemStack scriptedResult = ItemStack.EMPTY;

    // ------------------------------------------------------------------
    // B29 终端任务链冻结
    // ------------------------------------------------------------------
    // 终端起工时给整链每个节点打上同一 jobId、原 root、配方与预期流向。
    // 目的：箭头改线/拆机后，terminal 节点绝不能把本批物料 merge 进无关的手工链，
    // 也不能让非原 root 回退匹配其它配方。任务信息随存档持久化以获得崩溃/重载保护。

    @Nullable
    private UUID terminalJobId;
    private boolean terminalJobRoot;
    @Nullable
    private ResourceLocation terminalJobRecipe;
    @Nullable
    private BlockPos terminalJobRootPos;
    @Nullable
    private BlockPos terminalJobTargetPos;
    @Nullable
    private Direction terminalJobFacing;
    @Nullable
    private Direction terminalJobTargetDirection;
    private final List<BlockPos> terminalJobMembers = new ArrayList<>();
    // root 持久化预检时真实产物/返还物；重载后用同一 recipeId 复算并对比，
    // 防止数据包在同 ID 下变化导致输出漂移。
    private ItemStack terminalExpectedResult = ItemStack.EMPTY;
    private final List<ItemStack> terminalExpectedRemaining = new ArrayList<>();
    // 终端单取消/成品无输出时，ItemEntity 被其它模组否决后的本地兜底。
    // 普通手动 eject 永远不走这里，保持原有语义。
    private final List<ItemStack> terminalPendingDrops = new ArrayList<>();

    public ExtremeMechanicalCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
        phase = Phase.IDLE;
        groupedItemsBeforeCraft = new GroupedItems();
        inventory = new Inventory(this);

        // 不参与序列化：由 tick 中的变化检测维护
        wasPoweredBefore = true;
    }

    /** 方块实体工厂，供注册表使用。 */
    public static ExtremeMechanicalCrafterBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeMechanicalCrafterBlockEntity(
                committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities
                        .EXTREME_MECHANICAL_CRAFTER.get(),
                pos,
                state);
    }

    // ------------------------------------------------------------------
    // 行为
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        inserting = new InvManipulationBehaviour(this, this::getTargetFace);
        connectivity = new EdgeInteractionBehaviour(this, ExtremeConnectedInputHandler::toggleConnection)
                .connectivity(ExtremeConnectedInputHandler::shouldConnect)
                .require(item -> item.builtInRegistryHolder().is(Tags.Items.TOOLS_WRENCH));
        behaviours.add(inserting);
        behaviours.add(connectivity);
    }

    /** 朝向变化后重建插入行为，使插入目标面跟着更新。 */
    public void blockChanged() {
        // 物理箭头改变：B29 任务立即按本单取消，不能等下一次 tick 才制止中转。
        if (terminalJobId != null && level != null && !level.isClientSide) {
            cancelTerminalJob();
        }
        removeBehaviour(InvManipulationBehaviour.TYPE);
        inserting = new InvManipulationBehaviour(this, this::getTargetFace);
        attachBehaviourLate(inserting);
    }

    public BlockFace getTargetFace(Level world, BlockPos pos, BlockState state) {
        return new BlockFace(pos, ExtremeMechanicalCrafterBlock.getTargetDirection(state));
    }

    public Direction getTargetDirection() {
        return ExtremeMechanicalCrafterBlock.getTargetDirection(getBlockState());
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        if (input == null) {
            return;
        }
        CompoundTag inputNBT = new CompoundTag();
        input.write(inputNBT);
        tag.put("ConnectedInput", inputNBT);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("Inventory", inventory.serializeNBT(registries));

        CompoundTag inputNBT = new CompoundTag();
        input.write(inputNBT);
        compound.put("ConnectedInput", inputNBT);

        CompoundTag groupedItemsNBT = new CompoundTag();
        groupedItems.write(groupedItemsNBT, registries);
        compound.put("GroupedItems", groupedItemsNBT);

        compound.putString("Phase", phase.name());
        compound.putInt("CountDown", countDown);
        compound.putBoolean("Cover", covered);

        // 客户端不需要任务内部状态；只随存档保存，保证中途重载可继续/安全取消。
        if (!clientPacket && terminalJobId != null) {
            compound.putUUID("TerminalJobId", terminalJobId);
            compound.putBoolean("TerminalJobRoot", terminalJobRoot);
            if (terminalJobRecipe != null) {
                compound.putString("TerminalJobRecipe", terminalJobRecipe.toString());
            }
            if (terminalJobRootPos != null) {
                compound.putLong("TerminalJobRootPos", terminalJobRootPos.asLong());
            }
            if (terminalJobTargetPos != null) {
                compound.putLong("TerminalJobTarget", terminalJobTargetPos.asLong());
            }
            if (terminalJobFacing != null) {
                compound.putString("TerminalJobFacing", terminalJobFacing.getName());
            }
            if (terminalJobTargetDirection != null) {
                compound.putString("TerminalJobArrow", terminalJobTargetDirection.getName());
            }
            if (!terminalJobMembers.isEmpty()) {
                long[] members = new long[terminalJobMembers.size()];
                for (int i = 0; i < members.length; i++) {
                    members[i] = terminalJobMembers.get(i).asLong();
                }
                compound.putLongArray("TerminalJobMembers", members);
            }
            if (terminalJobRoot) {
                compound.put("TerminalExpectedResult", terminalExpectedResult.saveOptional(registries));
                ListTag remaining = new ListTag();
                for (ItemStack stack : terminalExpectedRemaining) {
                    if (!stack.isEmpty()) {
                        remaining.add(stack.saveOptional(registries));
                    }
                }
                compound.put("TerminalExpectedRemaining", remaining);
            }
        }
        if (!clientPacket && !terminalPendingDrops.isEmpty()) {
            ListTag pending = new ListTag();
            for (ItemStack stack : terminalPendingDrops) {
                if (!stack.isEmpty()) {
                    pending.add(stack.saveOptional(registries));
                }
            }
            compound.put("TerminalPendingDrops", pending);
        }

        super.write(compound, registries, clientPacket);

        if (clientPacket && reRender) {
            compound.putBoolean("Redraw", true);
            reRender = false;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        Phase phaseBefore = phase;
        GroupedItems before = this.groupedItems;

        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        input.read(compound.getCompound("ConnectedInput"));
        groupedItems = GroupedItems.read(compound.getCompound("GroupedItems"), registries);
        phase = Phase.IDLE;
        String name = compound.getString("Phase");
        for (Phase phase : Phase.values()) {
            if (phase.name().equals(name)) {
                this.phase = phase;
            }
        }
        countDown = compound.getInt("CountDown");
        covered = compound.getBoolean("Cover");

        terminalJobId = compound.hasUUID("TerminalJobId") ? compound.getUUID("TerminalJobId") : null;
        terminalJobRoot = terminalJobId != null && compound.getBoolean("TerminalJobRoot");
        terminalJobRecipe = terminalJobId != null && compound.contains("TerminalJobRecipe")
                ? ResourceLocation.tryParse(compound.getString("TerminalJobRecipe")) : null;
        terminalJobRootPos = terminalJobId != null && compound.contains("TerminalJobRootPos")
                ? BlockPos.of(compound.getLong("TerminalJobRootPos")) : null;
        terminalJobTargetPos = terminalJobId != null && compound.contains("TerminalJobTarget")
                ? BlockPos.of(compound.getLong("TerminalJobTarget")) : null;
        terminalJobFacing = terminalJobId != null && compound.contains("TerminalJobFacing")
                ? Direction.byName(compound.getString("TerminalJobFacing")) : null;
        terminalJobTargetDirection = terminalJobId != null && compound.contains("TerminalJobArrow")
                ? Direction.byName(compound.getString("TerminalJobArrow")) : null;
        terminalJobMembers.clear();
        if (terminalJobId != null) {
            for (long packed : compound.getLongArray("TerminalJobMembers")) {
                terminalJobMembers.add(BlockPos.of(packed));
            }
        }
        terminalExpectedResult = terminalJobRoot && compound.contains("TerminalExpectedResult")
                ? ItemStack.parseOptional(registries, compound.getCompound("TerminalExpectedResult"))
                : ItemStack.EMPTY;
        terminalExpectedRemaining.clear();
        if (terminalJobRoot && compound.contains("TerminalExpectedRemaining")) {
            ListTag remaining = compound.getList("TerminalExpectedRemaining", Tag.TAG_COMPOUND);
            for (int i = 0; i < remaining.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, remaining.getCompound(i));
                if (!stack.isEmpty()) {
                    terminalExpectedRemaining.add(stack);
                }
            }
        }
        terminalPendingDrops.clear();
        if (compound.contains("TerminalPendingDrops")) {
            ListTag pending = compound.getList("TerminalPendingDrops", Tag.TAG_COMPOUND);
            for (int i = 0; i < pending.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, pending.getCompound(i));
                if (!stack.isEmpty()) {
                    terminalPendingDrops.add(stack);
                }
            }
        }

        super.read(compound, registries, clientPacket);
        if (!clientPacket) {
            return;
        }
        if (compound.contains("Redraw")) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
        }
        if (phaseBefore != phase && phase == Phase.CRAFTING) {
            groupedItemsBeforeCraft = before;
        }
        if (phaseBefore == Phase.EXPORTING && phase == Phase.WAITING) {
            if (before.onlyEmptyItems()) {
                return;
            }
            Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
            Vec3 vec = Vec3.atLowerCornerOf(facing.getNormal())
                    .scale(.75)
                    .add(VecHelper.getCenterOf(worldPosition));
            Direction targetDirection = ExtremeMechanicalCrafterBlock.getTargetDirection(getBlockState());
            vec = vec.add(Vec3.atLowerCornerOf(targetDirection.getNormal()).scale(1));
            level.addParticle(ParticleTypes.CRIT, vec.x, vec.y, vec.z, 0, 0, 0);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // 速度与倒计时
    // ------------------------------------------------------------------

    public int getCountDownSpeed() {
        if (getSpeed() == 0) {
            return 0;
        }
        return Mth.clamp((int) Math.abs(getSpeed()), 4, 250);
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide && terminalJobId != null) {
            // IDLE 且没有任何本单物料：任务是残留状态，先清掉，避免影响玩家手动使用。
            if (phase == Phase.IDLE && inventory.getItem(0).isEmpty() && groupedItems.grid.isEmpty()) {
                clearTerminalJob();
            } else {
                TerminalJobStatus status = checkTerminalJob();
                if (status == TerminalJobStatus.PAUSE) {
                    return; // 原 root 所在区块未加载：暂停，不拉区块、不冒险中转
                }
                if (status == TerminalJobStatus.INVALID) {
                    cancelTerminalJob();
                    return;
                }
            }
        }

        if (terminalJobId != null && phase == Phase.INSERTING) {
            // 任务有效性已在上方检查；这里才允许重试本单实际出货。
            // 手动任务的 INSERTING 重试仍保留在 lazyTick。
            if (!level.isClientSide && (isTargetingBelt() || level.getGameTime() % 20 == 0)) {
                tryInsert();
            }
            return;
        }

        if (phase == Phase.ACCEPTING) {
            // 起工后链路若被拆断，ACCEPTING 节点可能永远等不到前置；及时按本单取消。
            if (terminalJobId != null && ExtremeRecipeGridHandler.getPrecedingCrafters(this).isEmpty()) {
                cancelTerminalJob();
            }
            return;
        }

        boolean onClient = level.isClientSide;
        boolean runLogic = !onClient || isVirtual();

        // 红石：仅在「无 → 有」的上升沿触发一次
        if (wasPoweredBefore != level.hasNeighborSignal(worldPosition)) {
            wasPoweredBefore = level.hasNeighborSignal(worldPosition);
            if (wasPoweredBefore) {
                if (!runLogic) {
                    return;
                }
                checkCompletedRecipe(true);
            }
        }

        if (phase == Phase.ASSEMBLING) {
            countDown -= getCountDownSpeed();
            if (countDown < 0) {
                countDown = 0;
                if (!runLogic) {
                    return;
                }
                // (a) 有下游目标 → 转为导出
                if (ExtremeRecipeGridHandler.getTargetingCrafter(this) != null) {
                    phase = Phase.EXPORTING;
                    countDown = groupedItems.onlyEmptyItems() ? 0 : 1000;
                    sendData();
                    return;
                }

                // (b) 无下游 → 尝试配方
                ItemStack result;
                List<ItemStack> remainders = new ArrayList<>();
                if (terminalJobId != null) {
                    // B29：只走本单记录的 recipeId 精确匹配。原 root 身份不符或失配，
                    // 一律按本单取消，绝不回退到其它配方/其它链。
                    if (!terminalJobRoot || terminalJobRecipe == null) {
                        cancelTerminalJob();
                        return;
                    }
                    ExtremeRecipeGridHandler.TerminalCraftOutcome outcome =
                            ExtremeRecipeGridHandler.tryToApplyTerminalRecipe(
                                    level, groupedItems, terminalJobRecipe);
                    if (outcome == null || !matchesExpectedOutcome(outcome)) {
                        // 同 ID 配方在重载/数据包变化后输出漂移：不回退、不产出，
                        // 按本单取消并把已投入材料弹出。
                        cancelTerminalJob();
                        return;
                    }
                    result = outcome.result();
                    // 原生 getRemainingItems 已同时覆盖默认容器返还；这里不能再用
                    // hasCraftingRemainingItem 追加一遍，否则容器会复制。
                    for (ItemStack stack : outcome.remaining()) {
                        if (!stack.isEmpty()) {
                            remainders.add(stack.copy());
                        }
                    }
                } else {
                    result = isVirtual() ? scriptedResult
                            : ExtremeRecipeGridHandler.tryToApplyRecipe(level, this);
                    if (result != null) {
                        groupedItems.grid.values().forEach(stack -> {
                            if (stack.hasCraftingRemainingItem()) {
                                remainders.add(stack.getCraftingRemainingItem().copy());
                            }
                        });
                    }
                }

                if (result != null) {
                    if (isVirtual()) {
                        groupedItemsBeforeCraft = groupedItems;
                    }

                    groupedItems = new GroupedItems(result);
                    for (int i = 0; i < remainders.size(); i++) {
                        ItemStack stack = remainders.get(i);
                        GroupedItems container = new GroupedItems();
                        container.grid.put(Pair.of(i, 0), stack);
                        container.mergeOnto(groupedItems, Pointing.LEFT);
                    }

                    phase = Phase.CRAFTING;
                    countDown = 2000;
                    if (terminalJobId != null) {
                        // 聚合原料已转换为产物：任务节点必须持久化这一相变。
                        setChanged();
                    }
                    sendData();
                    return;
                }
                ejectWholeGrid();
                return;
            }
        }

        if (phase == Phase.EXPORTING) {
            countDown -= getCountDownSpeed();

            if (countDown < 0) {
                countDown = 0;
                if (!runLogic) {
                    return;
                }

                ExtremeMechanicalCrafterBlockEntity targetingCrafter =
                        ExtremeRecipeGridHandler.getTargetingCrafter(this);
                if (terminalJobId != null) {
                    // 本单中转只允许写起工时记录的预期目标；箭头改线/目标被拆时取消整单，
                    // 防止把本批物料 merge 进 IDLE 的无关手工链（原逻辑会把对方推入 ASSEMBLING）。
                    if (targetingCrafter == null || terminalJobTargetPos == null
                            || !terminalJobTargetPos.equals(targetingCrafter.getBlockPos())
                            || !terminalJobId.equals(targetingCrafter.terminalJobId)) {
                        cancelTerminalJob();
                        return;
                    }
                } else if (targetingCrafter != null && targetingCrafter.terminalJobId != null) {
                    // 反向保护：普通手工合成改线指向 B29 任务节点时，禁止把手工物料写进任务链。
                    // 只清本节点，绝不调用动态 ejectWholeGrid（那会沿链清掉任务节点）。
                    ejectLocalContents(false);
                    setChanged();
                    sendData();
                    return;
                } else if (targetingCrafter == null) {
                    ejectWholeGrid();
                    return;
                }

                boolean empty = groupedItems.onlyEmptyItems();
                Pointing pointing = getBlockState().getValue(ExtremeMechanicalCrafterBlock.POINTING);
                groupedItems.mergeOnto(targetingCrafter.groupedItems, pointing);
                groupedItems = new GroupedItems();
                if (terminalJobId != null) {
                    // sendData 只广播；跨节点中转必须双方 dirty，避免区块/存档分裂状态。
                    setChanged();
                    targetingCrafter.setChanged();
                }

                float pitch = targetingCrafter.groupedItems.grid.size() * 1 / 16f + .5f;

                if (!empty) {
                    AllSoundEvents.CRAFTER_CLICK.playOnServer(level, worldPosition, 1, pitch);
                }

                phase = Phase.WAITING;
                countDown = 0;
                sendData();
                targetingCrafter.continueIfAllPrecedingFinished();
                targetingCrafter.sendData();
                return;
            }
        }

        if (phase == Phase.CRAFTING) {

            if (onClient) {
                Direction facing = getBlockState().getValue(HORIZONTAL_FACING);
                float progress = countDown / 2000f;
                Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
                Vec3 vec = facingVec.scale(.65).add(VecHelper.getCenterOf(worldPosition));
                Vec3 offset = VecHelper.offsetRandomly(Vec3.ZERO, level.random, .125f)
                        .multiply(VecHelper.axisAlingedPlaneOf(facingVec))
                        .normalize()
                        .scale(progress * .5f)
                        .add(vec);
                if (progress > .5f) {
                    level.addParticle(ParticleTypes.CRIT, offset.x, offset.y, offset.z, 0, 0, 0);
                }

                if (!groupedItemsBeforeCraft.grid.isEmpty() && progress < .5f) {
                    if (groupedItems.grid.containsKey(Pair.of(0, 0))) {
                        ItemStack stack = groupedItems.grid.get(Pair.of(0, 0));
                        groupedItemsBeforeCraft = new GroupedItems();

                        for (int i = 0; i < 10; i++) {
                            Vec3 randVec = VecHelper.offsetRandomly(Vec3.ZERO, level.random, .125f)
                                    .multiply(VecHelper.axisAlingedPlaneOf(facingVec))
                                    .normalize()
                                    .scale(.25f);
                            Vec3 offset2 = randVec.add(vec);
                            randVec = randVec.scale(.35f);
                            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), offset2.x,
                                    offset2.y, offset2.z, randVec.x, randVec.y, randVec.z);
                        }
                    }
                }
            }

            int prev = countDown;
            countDown -= getCountDownSpeed();

            if (countDown < 1000 && prev >= 1000) {
                AllSoundEvents.CRAFTER_CLICK.playOnServer(level, worldPosition, 1, 2);
                AllSoundEvents.CRAFTER_CRAFT.playOnServer(level, worldPosition);
            }

            if (countDown < 0) {
                countDown = 0;
                if (!runLogic) {
                    return;
                }
                tryInsert();
                return;
            }
        }

        if (phase == Phase.INSERTING) {
            if (runLogic && isTargetingBelt()) {
                tryInsert();
            }
            return;
        }
    }

    // ------------------------------------------------------------------
    // 输出
    // ------------------------------------------------------------------

    protected boolean isTargetingBelt() {
        DirectBeltInputBehaviour behaviour = getTargetingBelt();
        return behaviour != null && behaviour.canInsertFromSide(getTargetDirection());
    }

    /** B29 根出料的已知双箱边界：另一半未加载则先暂停，不读未加载方块。 */
    private boolean terminalOutputTargetPaused() {
        BlockPos targetPos = worldPosition.relative(getTargetDirection());
        if (!level.isInWorldBounds(targetPos)) {
            return false;
        }
        if (!level.isLoaded(targetPos)) {
            return true;
        }
        BlockState state = level.getBlockState(targetPos);
        if (state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherHalf = targetPos.relative(ChestBlock.getConnectedDirection(state));
            return level.isInWorldBounds(otherHalf) && !level.isLoaded(otherHalf);
        }
        return false;
    }

    protected DirectBeltInputBehaviour getTargetingBelt() {
        BlockPos targetPos = worldPosition.relative(getTargetDirection());
        return BlockEntityBehaviour.get(level, targetPos, DirectBeltInputBehaviour.TYPE);
    }

    public void tryInsert() {
        // B29 根出料：目标双箱另一半未加载时暂停，不触发隐式加载、也不 eject 本单。
        // 这只是已知原版双箱路径的守卫；CapManipulationBehaviourBase 的缓存刷新可能更早，
        // 不宣称覆盖所有旧 Create 缓存时序，普通手动输出语义不变。
        if (terminalJobId != null && terminalOutputTargetPaused()) {
            return;
        }
        if (!inserting.hasInventory() && !isTargetingBelt()) {
            ejectWholeGrid();
            return;
        }

        boolean changedPhase = phase != Phase.INSERTING;
        final List<Pair<Integer, Integer>> inserted = new LinkedList<>();

        DirectBeltInputBehaviour behaviour = getTargetingBelt();
        for (Entry<Pair<Integer, Integer>, ItemStack> entry : groupedItems.grid.entrySet()) {
            Pair<Integer, Integer> pair = entry.getKey();
            ItemStack stack = entry.getValue();
            BlockFace face = getTargetFace(level, worldPosition, getBlockState());

            ItemStack remainder = behaviour == null ? inserting.insert(stack.copy())
                    : behaviour.handleInsertion(stack, face.getFace(), false);
            if (!remainder.isEmpty()) {
                stack.setCount(remainder.getCount());
                continue;
            }

            inserted.add(pair);
        }

        inserted.forEach(groupedItems.grid::remove);
        if (groupedItems.grid.isEmpty()) {
            ejectWholeGrid();
        } else {
            phase = Phase.INSERTING;
        }
        if (terminalJobId != null) {
            // 部分插入/出货相变必须 dirty；ejectWholeGrid 内取消也会自身 setChanged。
            setChanged();
        }
        if (!inserted.isEmpty() || changedPhase) {
            sendData();
        }
    }

    public void ejectWholeGrid() {
        // 终端任务中绝不按当前动态链 eject：箭头改线后的“链”可能包含玩家手工物料。
        // 只按本单 jobId 回收本批节点，断链不会波及无关合成器。
        if (terminalJobId != null) {
            cancelTerminalJob();
            return;
        }
        List<ExtremeMechanicalCrafterBlockEntity> chain = ExtremeRecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null) {
            return;
        }
        // 手动链的动态取消可能恰好连到 B29 任务节点：只清非任务节点，
        // 任务节点由它自己的 job 生命周期回收，不能被外部 ejectWholeGrid 波及。
        boolean containsTerminalJob = false;
        for (ExtremeMechanicalCrafterBlockEntity be : chain) {
            if (be.terminalJobId != null) {
                containsTerminalJob = true;
                break;
            }
        }
        for (ExtremeMechanicalCrafterBlockEntity be : chain) {
            if (!containsTerminalJob || be.terminalJobId == null) {
                be.eject();
            }
        }
    }

    public void eject() {
        // 普通手动路径：完全保持既有掉落语义（不启用终端暂存兜底）。
        ejectLocalContents(false);
        clearTerminalJob();
        sendData();
    }

    /** 只清本节点库存/聚合；不沿动态链扩散。 */
    private void ejectLocalContents(boolean preserveRejectedDrops) {
        Vec3 ejectPos = ejectPosition();
        groupedItems.grid.forEach((pair, stack) -> {
            if (!stack.isEmpty()) {
                if (preserveRejectedDrops) {
                    dropTerminalItem(ejectPos, stack);
                } else {
                    dropItem(ejectPos, stack);
                }
            }
        });
        if (!inventory.getItem(0).isEmpty()) {
            if (preserveRejectedDrops) {
                dropTerminalItem(ejectPos, inventory.getItem(0));
            } else {
                dropItem(ejectPos, inventory.getItem(0));
            }
        }
        phase = Phase.IDLE;
        countDown = 0;
        groupedItems = new GroupedItems();
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();
    }

    private Vec3 ejectPosition() {
        BlockState blockState = getBlockState();
        boolean present = ExtremeCrafterHelper.isCrafter(blockState);
        Vec3 vec = present ? Vec3.atLowerCornerOf(blockState.getValue(HORIZONTAL_FACING).getNormal())
                .scale(.75f) : Vec3.ZERO;
        return VecHelper.getCenterOf(worldPosition).add(vec);
    }

    /** 保持原 public 签名；普通路径仍不检查 spawn 结果。 */
    public void dropItem(Vec3 ejectPos, ItemStack stack) {
        spawnItem(ejectPos, stack);
    }

    /** @return 是否真正加入世界；终端守恒路径必须检查返回值。 */
    private boolean spawnItem(Vec3 ejectPos, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(level, ejectPos.x, ejectPos.y, ejectPos.z, stack);
        itemEntity.setDefaultPickUpDelay();
        return level.addFreshEntity(itemEntity);
    }

    /**
     * 终端单专用掉落：ItemEntity 被其它模组否决时不丢，转存本 BE 的 NBT 缓冲，
     * lazyTick 低频重试；普通手动房间不传 preserve 标志，保持旧行为。
     */
    private void dropTerminalItem(Vec3 ejectPos, ItemStack stack) {
        if (spawnItem(ejectPos, stack)) {
            return;
        }
        if (!stack.isEmpty()) {
            terminalPendingDrops.add(stack.copy());
            setChanged();
            AvaritiaSingularityStressMod.LOGGER.warn(
                    "[B29] 合成器 {} 终端掉落被拒绝，已暂存 {} x{} 等待重试",
                    worldPosition, stack.getItem(), stack.getCount());
        }
    }

    // ------------------------------------------------------------------
    // 兜底检查与启动
    // ------------------------------------------------------------------

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level.isClientSide && !isVirtual()) {
            return;
        }
        // 终端单实物兜底：每次 lazyTick 只试一个栈，正式 spawn=true 才释放原副本。
        if (!terminalPendingDrops.isEmpty()) {
            ItemStack pending = terminalPendingDrops.get(0);
            if (pending.isEmpty() || spawnItem(ejectPosition(), pending.copy())) {
                terminalPendingDrops.remove(0);
                setChanged();
            }
        }
        if (phase == Phase.IDLE && craftingItemPresent()) {
            checkCompletedRecipe(false);
        }
        // B29 任务的 INSERTING 重试放到 tick 中、任务有效性检查之后，
        // 避免 super.tick 内先出货再发现箭头/root 已失效。
        if (phase == Phase.INSERTING && terminalJobId == null) {
            tryInsert();
        }
    }

    public boolean craftingItemPresent() {
        return !inventory.getItem(0).isEmpty();
    }

    public boolean craftingItemOrCoverPresent() {
        return !inventory.getItem(0).isEmpty() || covered;
    }

    public void checkCompletedRecipe(boolean poweredStart) {
        // 终端任务链由 B29 独立入口管理；红石/手动不得覆盖正在进行的本单。
        if (terminalJobId != null) {
            return;
        }
        if (getSpeed() == 0) {
            return;
        }
        if (level.isClientSide && !isVirtual()) {
            return;
        }
        List<ExtremeMechanicalCrafterBlockEntity> chain = ExtremeRecipeGridHandler.getAllCraftersOfChainIf(this,
                poweredStart ? ExtremeMechanicalCrafterBlockEntity::craftingItemPresent
                        : ExtremeMechanicalCrafterBlockEntity::craftingItemOrCoverPresent,
                poweredStart);
        if (chain == null) {
            return;
        }
        // 红石发起者可能只是链上的无关 C；只要整条真实链里存在 active terminalJob，
        // 本次手动/红石起工就必须无副作用地拒绝，不能让 chain.forEach(begin) 覆盖任务 GroupedItems。
        for (ExtremeMechanicalCrafterBlockEntity be : chain) {
            if (be.terminalJobId != null) {
                return;
            }
        }
        chain.forEach(ExtremeMechanicalCrafterBlockEntity::begin);
    }

    protected void begin() {
        // 第二道闸：即便调用方漏检，任务节点也绝不接受手动 begin 覆盖。
        if (terminalJobId != null) {
            return;
        }
        phase = Phase.ACCEPTING;
        groupedItems = new GroupedItems(inventory.getItem(0));
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        if (ExtremeRecipeGridHandler.getPrecedingCrafters(this).isEmpty()) {
            phase = Phase.ASSEMBLING;
            countDown = 1;
        }
        sendData();
    }

    protected void continueIfAllPrecedingFinished() {
        List<ExtremeMechanicalCrafterBlockEntity> preceding = ExtremeRecipeGridHandler.getPrecedingCrafters(this);
        if (preceding == null) {
            ejectWholeGrid();
            return;
        }

        if (terminalJobId != null) {
            // 只接受同一任务链的前置汇入；外部手工链中途接进来时整单取消，
            // 避免把非本单物料一起 assembly。
            for (ExtremeMechanicalCrafterBlockEntity blockEntity : preceding) {
                if (!terminalJobId.equals(blockEntity.terminalJobId)) {
                    cancelTerminalJob();
                    return;
                }
            }
        }

        for (ExtremeMechanicalCrafterBlockEntity blockEntity : preceding) {
            if (blockEntity.phase != Phase.WAITING) {
                return;
            }
        }

        phase = Phase.ASSEMBLING;
        countDown = 1;
    }

    /** 连接发生变化：要求客户端重绘，并让组合库存能力缓存失效。 */
    public void connectivityChanged() {
        reRender = true;
        sendData();
        invCap = null;
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // B29 终端任务生命周期
    // ------------------------------------------------------------------

    private enum TerminalJobStatus {
        VALID, PAUSE, INVALID
    }

    /**
     * 终端专用起工入口：不经过 Inventory.insertItem（那会触发 checkCompletedRecipe 自行起工），
     * 直接把已整链验证过的单格输入交给现有 phase/GroupedItems 流。
     * 真实 covered 状态完全不改；空格位传 ItemStack.EMPTY 以保留网格几何。
     */
    public boolean startTerminalCraft(UUID jobId, BlockPos rootPos, ResourceLocation recipeId,
                                      boolean root, @Nullable BlockPos expectedTarget,
                                      ItemStack input, List<BlockPos> members,
                                      @Nullable TerminalCraftOutcome expectedOutcome) {
        if (level == null || level.isClientSide || jobId == null || recipeId == null) {
            return false;
        }
        if (phase != Phase.IDLE || terminalJobId != null) {
            return false;
        }
        if (!terminalPendingDrops.isEmpty()) {
            return false; // 上一次终端单的实物兜底未清空，先禁止新单累积
        }
        if (!inventory.getItem(0).isEmpty() || !groupedItems.grid.isEmpty()) {
            return false;
        }

        terminalJobId = jobId;
        terminalJobRoot = root;
        terminalJobRecipe = recipeId;
        terminalJobRootPos = rootPos;
        terminalJobTargetPos = expectedTarget;
        // 冻结物理箭头：root 从一个箱子切到另一个箱子也属于改线，必须能检测。
        terminalJobFacing = getBlockState().getValue(HORIZONTAL_FACING);
        terminalJobTargetDirection = ExtremeMechanicalCrafterBlock.getTargetDirection(getBlockState());
        terminalJobMembers.clear();
        if (root) {
            terminalJobMembers.addAll(members);
        }
        terminalExpectedResult = ItemStack.EMPTY;
        terminalExpectedRemaining.clear();
        if (root && expectedOutcome != null) {
            terminalExpectedResult = expectedOutcome.result().copy();
            for (ItemStack stack : expectedOutcome.remaining()) {
                if (!stack.isEmpty()) {
                    terminalExpectedRemaining.add(stack.copy());
                }
            }
        }

        groupedItems = new GroupedItems(input.copy());
        phase = Phase.ACCEPTING;
        countDown = 0;
        if (ExtremeRecipeGridHandler.getPrecedingCrafters(this).isEmpty()) {
            phase = Phase.ASSEMBLING;
            countDown = 1;
        }
        setChanged();
        sendData();
        return true;
    }

    /**
     * 起工失败回滚：把刚交给本节点的输入原样取回，不弹出、不碰库存。
     * 只允许在同一 tick 内、节点尚未 tick 前调用。
     */
    public ItemStack revertTerminalCraftStart() {
        if (terminalJobId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack input = groupedItems.grid.get(Pair.of(0, 0));
        ItemStack reverted = input == null ? ItemStack.EMPTY : input.copy();
        phase = Phase.IDLE;
        countDown = 0;
        groupedItems = new GroupedItems();
        clearTerminalJob();
        sendData();
        return reverted;
    }

    public boolean hasTerminalJob() {
        return terminalJobId != null;
    }

    public boolean hasTerminalPendingDrops() {
        return !terminalPendingDrops.isEmpty();
    }

    /** 根复算必须与预检产物/返还物完全一致（数量与 components）。 */
    private boolean matchesExpectedOutcome(TerminalCraftOutcome outcome) {
        if (terminalExpectedResult.isEmpty()) {
            return false;
        }
        if (terminalExpectedResult.getCount() != outcome.result().getCount()
                || !ItemStack.isSameItemSameComponents(terminalExpectedResult, outcome.result())) {
            return false;
        }
        List<ItemStack> actual = new ArrayList<>();
        for (ItemStack stack : outcome.remaining()) {
            if (!stack.isEmpty()) {
                actual.add(stack);
            }
        }
        if (actual.size() != terminalExpectedRemaining.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            ItemStack expected = terminalExpectedRemaining.get(i);
            ItemStack got = actual.get(i);
            if (expected.getCount() != got.getCount()
                    || !ItemStack.isSameItemSameComponents(expected, got)) {
                return false;
            }
        }
        return true;
    }

    private TerminalJobStatus checkTerminalJob() {
        if (terminalJobId == null) {
            return TerminalJobStatus.VALID;
        }
        // 冻结物理箭头：pointing/facing 任一变化都视为改线，即使新目标仍是容器。
        // 先做纯状态比较，箭头已明显改线时尽快 cancel，不等区块检查。
        Direction currentFacing = getBlockState().getValue(HORIZONTAL_FACING);
        Direction currentArrow = ExtremeMechanicalCrafterBlock.getTargetDirection(getBlockState());
        if (!Objects.equals(terminalJobFacing, currentFacing)
                || !Objects.equals(terminalJobTargetDirection, currentArrow)) {
            return TerminalJobStatus.INVALID;
        }
        // 箭头平面/前置邻居若需要读取未加载区块，先暂停，绝不隐式加载。
        if (!jobNeighborhoodLoaded(currentFacing, currentArrow)) {
            return TerminalJobStatus.PAUSE;
        }

        ExtremeMechanicalCrafterBlockEntity root = resolveTerminalJobRoot();
        if (root == null) {
            if (terminalJobRoot) {
                return TerminalJobStatus.INVALID; // 根位置/身份被破坏
            }
            if (terminalJobRootPos == null) {
                return TerminalJobStatus.INVALID;
            }
            // 根区块确实未加载：暂停，不查询引发 load。
            return level.isLoaded(terminalJobRootPos)
                    ? TerminalJobStatus.INVALID : TerminalJobStatus.PAUSE;
        }
        if (root.terminalJobRecipe == null || !root.terminalJobMembers.contains(worldPosition)) {
            return TerminalJobStatus.INVALID;
        }
        // 恢复/继续时校验预期全部成员；成员区块未加载或不 tick 一律暂停。
        for (BlockPos pos : root.terminalJobMembers) {
            if (!level.isInWorldBounds(pos)) {
                return TerminalJobStatus.INVALID; // 世界外不可能存在本单成员
            }
            if (!isJobNodeTicking(pos)) {
                return TerminalJobStatus.PAUSE;
            }
            if (!(level.getBlockEntity(pos) instanceof ExtremeMechanicalCrafterBlockEntity member)
                    || !terminalJobId.equals(member.terminalJobId)) {
                return TerminalJobStatus.INVALID;
            }
        }

        // 预期流向必须保持不变：箭头改线后本节点不再允许向新目标中转。
        if (!Objects.equals(currentTargetPos(), terminalJobTargetPos)) {
            return TerminalJobStatus.INVALID;
        }
        // 只允许同一任务的节点作为前置；外部手工链接进来即取消本单。
        for (ExtremeMechanicalCrafterBlockEntity preceding
                : ExtremeRecipeGridHandler.getPrecedingCrafters(this)) {
            if (!terminalJobId.equals(preceding.terminalJobId)) {
                return TerminalJobStatus.INVALID;
            }
        }
        return TerminalJobStatus.VALID;
    }

    @Nullable
    private BlockPos currentTargetPos() {
        ExtremeMechanicalCrafterBlockEntity target = ExtremeRecipeGridHandler.getTargetingCrafter(this);
        return target == null ? null : target.getBlockPos();
    }

    /**
     * 活跃任务边界：箭头落点与可能被 getPrecedingCrafters 读取的非朝向邻居
     * 必须已加载。世界硬边界外视为已知空，绝不能因 buildHeight 外邻位永久暂停。
     * 只有确实属于本单成员的节点才在成员循环里要求 BE 真正 tick。
     */
    private boolean jobNeighborhoodLoaded(Direction facing, Direction arrow) {
        BlockPos arrowPos = worldPosition.relative(arrow);
        if (level.isInWorldBounds(arrowPos) && !level.isLoaded(arrowPos)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == facing.getAxis() || direction == arrow) {
                continue;
            }
            BlockPos neighbor = worldPosition.relative(direction);
            if (!level.isInWorldBounds(neighbor)) {
                continue; // 世界外必然是空气，不读取也不暂停
            }
            if (!level.isLoaded(neighbor)) {
                return false;
            }
        }
        return true;
    }

    private boolean isJobNodeTicking(BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        long chunkKey = ChunkPos.asLong(pos);
        return serverLevel.getChunkSource().isPositionTicking(chunkKey)
                && serverLevel.areEntitiesLoaded(chunkKey);
    }

    /**
     * 取消本单：优先找原 root，由它按持久化的成员坐标只回收同 jobId 节点。
     * root 未加载/已消失时只清理当前节点（不拉区块、不沿动态链扩散）。
     */
    public void cancelTerminalJob() {
        if (level == null || level.isClientSide || terminalJobId == null) {
            return;
        }
        ExtremeMechanicalCrafterBlockEntity root = resolveTerminalJobRoot();
        if (root != null) {
            root.ejectTerminalJobMembers();
        } else {
            ejectLocalTerminalJob();
        }
    }

    @Nullable
    private ExtremeMechanicalCrafterBlockEntity resolveTerminalJobRoot() {
        if (terminalJobRoot && worldPosition.equals(terminalJobRootPos)) {
            return this;
        }
        if (terminalJobRootPos == null || !level.isLoaded(terminalJobRootPos)) {
            return null;
        }
        return level.getBlockEntity(terminalJobRootPos) instanceof ExtremeMechanicalCrafterBlockEntity root
                && root.terminalJobRoot
                && terminalJobId.equals(root.terminalJobId) ? root : null;
    }

    private void ejectTerminalJobMembers() {
        UUID jobId = terminalJobId;
        if (jobId == null) {
            return;
        }
        List<BlockPos> members = new ArrayList<>(terminalJobMembers);
        if (!members.contains(worldPosition)) {
            members.add(worldPosition);
        }
        for (BlockPos pos : members) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof ExtremeMechanicalCrafterBlockEntity be
                    && jobId.equals(be.terminalJobId)) {
                be.ejectLocalTerminalJob();
            }
        }
    }

    private void ejectLocalTerminalJob() {
        // 终端单取消/成品无输出：掉落被否决时保留在本 BE，直到能真正 spawn。
        ejectLocalContents(true);
        clearTerminalJob();
        sendData();
    }

    private void clearTerminalJob() {
        terminalJobId = null;
        terminalJobRoot = false;
        terminalJobRecipe = null;
        terminalJobRootPos = null;
        terminalJobTargetPos = null;
        terminalJobFacing = null;
        terminalJobTargetDirection = null;
        terminalJobMembers.clear();
        terminalExpectedResult = ItemStack.EMPTY;
        terminalExpectedRemaining.clear();
        setChanged();
    }

    // ------------------------------------------------------------------
    // 访问器
    // ------------------------------------------------------------------

    protected IItemHandler getInvCapability() {
        if (invCap == null) {
            invCap = input.getItemHandler(getLevel(), getBlockPos());
        }
        return invCap;
    }

    /** 供模组主类的能力注册使用：暴露整组的组合库存。 */
    public IItemHandler exposeItemHandler() {
        return getInvCapability();
    }

    public Inventory getInventory() {
        return inventory;
    }

    public ConnectedInput getInput() {
        return input;
    }

    public void setInput(ConnectedInput input) {
        this.input = input;
    }

    public void setScriptedResult(ItemStack scriptedResult) {
        this.scriptedResult = scriptedResult;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getCountDown() {
        return countDown;
    }

    public boolean isCovered() {
        return covered;
    }

    public void setCovered(boolean covered) {
        this.covered = covered;
    }

    public GroupedItems getGroupedItems() {
        return groupedItems;
    }

    public GroupedItems getGroupedItemsBeforeCraft() {
        return groupedItemsBeforeCraft;
    }

    @Override
    public void transform(BlockEntity be, StructureTransform transform) {
        input.getData().replaceAll(transform::applyWithoutOffset);
        notifyUpdate();
    }
}
