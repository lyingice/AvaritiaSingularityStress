package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeConnectedInputHandler.ConnectedInput;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity.Phase;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 终焉动力合成器——方块。
 * <p>
 * 这是<b>完整复制</b> Create {@code MechanicalCrafterBlock} 的独立实现，
 * 不继承 Create 合成器的任何类，因此父类内部对 {@code AllBlocks.MECHANICAL_CRAFTER}
 * 的硬编码判断不会影响本方块。
 * <p>
 * 与普通机械合成器的区别只有一处：配方匹配只走无尽贪婪分级配方
 * （见 {@link ExtremeRecipeGridHandler#tryToApplyRecipe}）。
 * 方块自身的朝向、放置、扳手交互、连接建立等行为与原版一致。
 */
public class ExtremeMechanicalCrafterBlock extends HorizontalKineticBlock
        implements IBE<ExtremeMechanicalCrafterBlockEntity>, ICogWheel {

    public static final EnumProperty<Pointing> POINTING = EnumProperty.create("pointing", Pointing.class);

    public ExtremeMechanicalCrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POINTING, Pointing.UP));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(POINTING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    // ------------------------------------------------------------------
    // 放置：贴着已有终焉合成器时自动继承朝向，形成同平面同朝向的阵列
    // ------------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos placedOnPos = context.getClickedPos().relative(face.getOpposite());
        BlockState blockState = context.getLevel().getBlockState(placedOnPos);

        // 判断依据是「是否同属终焉合成器家族」而非严格同一方块:
        // 因为终焉合成器与终焉锻造器可以互相连接,放在一起时应自动继承朝向,
        // 否则十字布局(中心锻造器 + 四向合成器)无法自动对齐。
        if ((!(blockState.getBlock() instanceof ExtremeMechanicalCrafterBlock))
                || (context.getPlayer() != null && context.getPlayer().isShiftKeyDown())) {
            BlockState stateForPlacement = super.getStateForPlacement(context);
            Direction direction = stateForPlacement.getValue(HORIZONTAL_FACING);
            if (direction != face) {
                stateForPlacement = stateForPlacement.setValue(POINTING, pointingFromFacing(face, direction));
            }
            return stateForPlacement;
        }

        Direction otherFacing = blockState.getValue(HORIZONTAL_FACING);
        Pointing pointing = pointingFromFacing(face, otherFacing);
        return defaultBlockState().setValue(HORIZONTAL_FACING, otherFacing)
                .setValue(POINTING, pointing);
    }

    /**
     * 由「点击面」与「方块水平朝向」推导 pointing。
     * 映射表与原版逐格一致，包含正/负轴方向的差异。
     */
    public static Pointing pointingFromFacing(Direction pointingFace, Direction blockFacing) {
        boolean positive = blockFacing.getAxisDirection() == AxisDirection.POSITIVE;

        Pointing pointing = pointingFace == Direction.DOWN ? Pointing.UP : Pointing.DOWN;
        if (pointingFace == Direction.EAST) {
            pointing = positive ? Pointing.LEFT : Pointing.RIGHT;
        }
        if (pointingFace == Direction.WEST) {
            pointing = positive ? Pointing.RIGHT : Pointing.LEFT;
        }
        if (pointingFace == Direction.NORTH) {
            pointing = positive ? Pointing.LEFT : Pointing.RIGHT;
        }
        if (pointingFace == Direction.SOUTH) {
            pointing = positive ? Pointing.RIGHT : Pointing.LEFT;
        }
        return pointing;
    }

    // ------------------------------------------------------------------
    // 拆除：检测朝向变化、掉落盖子、断开指向本块的连接
    // ------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            if (getTargetDirection(state) != getTargetDirection(newState)) {
                ExtremeMechanicalCrafterBlockEntity crafter = ExtremeCrafterHelper.getCrafter(worldIn, pos);
                if (crafter != null) {
                    crafter.blockChanged();
                }
            }
        }

        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            ExtremeMechanicalCrafterBlockEntity crafter = ExtremeCrafterHelper.getCrafter(worldIn, pos);
            if (crafter != null) {
                if (crafter.isCovered()) {
                    Block.popResource(worldIn, pos, AllItems.CRAFTER_SLOT_COVER.asStack());
                }
                if (!isMoving) {
                    crafter.ejectWholeGrid();
                }
            }

            for (Direction direction : Iterate.directions) {
                if (direction.getAxis() == state.getValue(HORIZONTAL_FACING).getAxis()) {
                    continue;
                }

                BlockPos otherPos = pos.relative(direction);
                ConnectedInput thisInput = ExtremeCrafterHelper.getInput(worldIn, pos);
                ConnectedInput otherInput = ExtremeCrafterHelper.getInput(worldIn, otherPos);

                if (thisInput == null || otherInput == null) {
                    continue;
                }
                if (!pos.offset(thisInput.getData().get(0))
                        .equals(otherPos.offset(otherInput.getData().get(0)))) {
                    continue;
                }

                ExtremeConnectedInputHandler.toggleConnection(worldIn, pos, otherPos);
            }
        }

        super.onRemove(state, worldIn, pos, newState, isMoving);
    }

    // ------------------------------------------------------------------
    // 扳手：只有正面才切换 pointing；其他面交给 EdgeInteractionBehaviour 建立连接
    // ------------------------------------------------------------------

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getClickedFace() == state.getValue(HORIZONTAL_FACING)) {
            if (!context.getLevel().isClientSide) {
                KineticBlockEntity.switchToBlockState(context.getLevel(), context.getClickedPos(),
                        state.cycle(POINTING));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------
    // 交互：正面右键放/取物品、安装/取下盖子
    // ------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ExtremeMechanicalCrafterBlockEntity crafter)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (AllBlocks.MECHANICAL_ARM.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        boolean isHand = stack.isEmpty() && hand == InteractionHand.MAIN_HAND;
        boolean wrenched = AllItems.WRENCH.isIn(stack);

        if (hitResult.getDirection() == state.getValue(HORIZONTAL_FACING)) {

            if (crafter.getPhase() != Phase.IDLE && !wrenched) {
                crafter.ejectWholeGrid();
                return ItemInteractionResult.SUCCESS;
            }

            if (crafter.getPhase() == Phase.IDLE && !isHand && !wrenched) {
                if (level.isClientSide) {
                    return ItemInteractionResult.SUCCESS;
                }

                if (AllItems.CRAFTER_SLOT_COVER.isIn(stack)) {
                    if (crafter.isCovered()) {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                    if (!crafter.getInventory().isEmpty()) {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                    crafter.setCovered(true);
                    crafter.setChanged();
                    crafter.sendData();
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    return ItemInteractionResult.SUCCESS;
                }

                IItemHandler capability = level.getCapability(Capabilities.ItemHandler.BLOCK,
                        crafter.getBlockPos(), null);
                if (capability == null) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                ItemStack remainder = ItemHandlerHelper.insertItem(capability, stack.copy(), false);
                if (remainder.getCount() != stack.getCount()) {
                    player.setItemInHand(hand, remainder);
                }
                return ItemInteractionResult.SUCCESS;
            }

            ItemStack inSlot = crafter.getInventory().getItem(0);
            if (inSlot.isEmpty()) {
                if (crafter.isCovered() && !wrenched) {
                    if (level.isClientSide) {
                        return ItemInteractionResult.SUCCESS;
                    }
                    crafter.setCovered(false);
                    crafter.setChanged();
                    crafter.sendData();
                    if (!player.isCreative()) {
                        player.getInventory().placeItemBackInInventory(AllItems.CRAFTER_SLOT_COVER.asStack());
                    }
                    return ItemInteractionResult.SUCCESS;
                }
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!isHand && !ItemStack.isSameItemSameComponents(stack, inSlot)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            player.getInventory().placeItemBackInInventory(inSlot);
            crafter.getInventory().setStackInSlot(0, ItemStack.EMPTY);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
                                boolean isMoving) {
        InvManipulationBehaviour behaviour = BlockEntityBehaviour.get(worldIn, pos, InvManipulationBehaviour.TYPE);
        if (behaviour != null) {
            behaviour.onNeighborChanged(fromPos);
        }
    }

    @Override
    public float getParticleTargetRadius() {
        return .85f;
    }

    @Override
    public float getParticleInitialRadius() {
        return .75f;
    }

    // ------------------------------------------------------------------
    // 目标方向与合法性：完全由终焉合成器自身判定，不再有原版类型硬编码
    // ------------------------------------------------------------------

    /**
     * 由 state 计算输出目标方向。
     * <p>
     * 与原版算法完全一致：起点 UP，先绕 Z 轴旋转 pointing 的角度，再绕 Y 轴旋转朝向角度。
     * 结果只会是 UP/DOWN 或与水平朝向垂直的左右方向，永远不会指向朝向轴的前后。
     */
    public static Direction getTargetDirection(BlockState state) {
        if (!ExtremeCrafterHelper.isCrafter(state)) {
            return Direction.UP;
        }
        Direction facing = state.getValue(HORIZONTAL_FACING);
        Pointing point = state.getValue(POINTING);
        Vec3 targetVec = new Vec3(0, 1, 0);
        targetVec = VecHelper.rotate(targetVec, -point.getXRotation(), Axis.Z);
        targetVec = VecHelper.rotate(targetVec, AngleHelper.horizontalAngle(facing), Axis.Y);
        return Direction.getNearest(targetVec.x, targetVec.y, targetVec.z);
    }

    /** 目标位置是否为合法的下一个终焉合成器（供渲染器决定画皮带还是箭头）。 */
    public static boolean isValidTarget(Level world, BlockPos targetPos, BlockState crafterState) {
        BlockState targetState = world.getBlockState(targetPos);
        if (!world.isLoaded(targetPos)) {
            return false;
        }
        if (!ExtremeCrafterHelper.isCrafter(targetState)) {
            return false;
        }
        if (crafterState.getValue(HORIZONTAL_FACING) != targetState.getValue(HORIZONTAL_FACING)) {
            return false;
        }
        if (Math.abs(crafterState.getValue(POINTING).getXRotation()
                - targetState.getValue(POINTING).getXRotation()) == 180) {
            return false;
        }
        return true;
    }

    @Override
    public Class<ExtremeMechanicalCrafterBlockEntity> getBlockEntityClass() {
        return ExtremeMechanicalCrafterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExtremeMechanicalCrafterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.EXTREME_MECHANICAL_CRAFTER.get();
    }
}
