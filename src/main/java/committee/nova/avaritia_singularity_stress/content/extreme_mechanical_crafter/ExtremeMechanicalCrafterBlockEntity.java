package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

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
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeConnectedInputHandler.ConnectedInput;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;

import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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

        if (phase == Phase.ACCEPTING) {
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
                ItemStack result = isVirtual() ? scriptedResult
                        : ExtremeRecipeGridHandler.tryToApplyRecipe(level, this);

                if (result != null) {
                    List<ItemStack> containers = new ArrayList<>();
                    groupedItems.grid.values().forEach(stack -> {
                        if (stack.hasCraftingRemainingItem()) {
                            containers.add(stack.getCraftingRemainingItem().copy());
                        }
                    });

                    if (isVirtual()) {
                        groupedItemsBeforeCraft = groupedItems;
                    }

                    groupedItems = new GroupedItems(result);
                    for (int i = 0; i < containers.size(); i++) {
                        ItemStack stack = containers.get(i);
                        GroupedItems container = new GroupedItems();
                        container.grid.put(Pair.of(i, 0), stack);
                        container.mergeOnto(groupedItems, Pointing.LEFT);
                    }

                    phase = Phase.CRAFTING;
                    countDown = 2000;
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
                if (targetingCrafter == null) {
                    ejectWholeGrid();
                    return;
                }

                boolean empty = groupedItems.onlyEmptyItems();
                Pointing pointing = getBlockState().getValue(ExtremeMechanicalCrafterBlock.POINTING);
                groupedItems.mergeOnto(targetingCrafter.groupedItems, pointing);
                groupedItems = new GroupedItems();

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

    protected DirectBeltInputBehaviour getTargetingBelt() {
        BlockPos targetPos = worldPosition.relative(getTargetDirection());
        return BlockEntityBehaviour.get(level, targetPos, DirectBeltInputBehaviour.TYPE);
    }

    public void tryInsert() {
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
        if (!inserted.isEmpty() || changedPhase) {
            sendData();
        }
    }

    public void ejectWholeGrid() {
        List<ExtremeMechanicalCrafterBlockEntity> chain = ExtremeRecipeGridHandler.getAllCraftersOfChain(this);
        if (chain == null) {
            return;
        }
        chain.forEach(ExtremeMechanicalCrafterBlockEntity::eject);
    }

    public void eject() {
        BlockState blockState = getBlockState();
        boolean present = ExtremeCrafterHelper.isCrafter(blockState);
        Vec3 vec = present ? Vec3.atLowerCornerOf(blockState.getValue(HORIZONTAL_FACING).getNormal())
                .scale(.75f) : Vec3.ZERO;
        Vec3 ejectPos = VecHelper.getCenterOf(worldPosition).add(vec);
        groupedItems.grid.forEach((pair, stack) -> dropItem(ejectPos, stack));
        if (!inventory.getItem(0).isEmpty()) {
            dropItem(ejectPos, inventory.getItem(0));
        }
        phase = Phase.IDLE;
        groupedItems = new GroupedItems();
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        sendData();
    }

    public void dropItem(Vec3 ejectPos, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(level, ejectPos.x, ejectPos.y, ejectPos.z, stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
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
        if (phase == Phase.IDLE && craftingItemPresent()) {
            checkCompletedRecipe(false);
        }
        if (phase == Phase.INSERTING) {
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
        chain.forEach(ExtremeMechanicalCrafterBlockEntity::begin);
    }

    protected void begin() {
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
