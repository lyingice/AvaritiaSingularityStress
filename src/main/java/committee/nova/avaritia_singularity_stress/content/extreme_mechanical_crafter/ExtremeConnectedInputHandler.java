package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity.Inventory;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * 终焉合成器的库存连接处理。
 * <p>
 * 完整复制原版 {@code ConnectedInputHandler} 的算法（扳手连接的判定、合并、拆分、
 * 组合库存排序），但类型判定全部走 {@link ExtremeCrafterHelper}，
 * 因此不再依赖 Create 机械合成器的硬编码类型检查。
 * <p>
 * 与 Create 一样：相邻并不自动连接，必须用扳手在背面点击建立连接；
 * 建立连接后整组共享同一个组合库存。
 */
public class ExtremeConnectedInputHandler {

    private ExtremeConnectedInputHandler() {
    }

    /** 扳手点击时是否允许在两个终焉合成器之间建立连接。 */
    public static boolean shouldConnect(Level world, BlockPos pos, Direction face, Direction direction) {
        BlockState refState = world.getBlockState(pos);
        if (!refState.hasProperty(HORIZONTAL_FACING)) {
            return false;
        }
        Direction refDirection = refState.getValue(HORIZONTAL_FACING);
        // 与朝向同轴的邻居（前/后）不连接
        if (direction.getAxis() == refDirection.getAxis()) {
            return false;
        }
        // 从正面点击不连接（正面用于切换 pointing）
        if (face == refDirection) {
            return false;
        }
        BlockState neighbour = world.getBlockState(pos.relative(direction));
        if (!ExtremeCrafterHelper.isCrafter(neighbour)) {
            return false;
        }
        return refDirection == neighbour.getValue(HORIZONTAL_FACING);
    }

    public static void toggleConnection(Level world, BlockPos pos, BlockPos pos2) {
        ExtremeMechanicalCrafterBlockEntity crafter1 = ExtremeCrafterHelper.getCrafter(world, pos);
        ExtremeMechanicalCrafterBlockEntity crafter2 = ExtremeCrafterHelper.getCrafter(world, pos2);

        if (crafter1 == null || crafter2 == null) {
            return;
        }

        BlockPos controllerPos1 = crafter1.getBlockPos()
                .offset(crafter1.getInput().getData().get(0));
        BlockPos controllerPos2 = crafter2.getBlockPos()
                .offset(crafter2.getInput().getData().get(0));

        // 情况一：两端已在同一组 → 断开，拆成两组
        if (controllerPos1.equals(controllerPos2)) {
            ExtremeMechanicalCrafterBlockEntity controller = ExtremeCrafterHelper.getCrafter(world, controllerPos1);

            Set<BlockPos> positions = controller.getInput().getData().stream()
                    .map(controllerPos1::offset)
                    .collect(Collectors.toSet());
            List<BlockPos> frontier = new LinkedList<>();
            List<BlockPos> splitGroup = new ArrayList<>();

            frontier.add(pos2);
            positions.remove(pos2);
            positions.remove(pos);
            while (!frontier.isEmpty()) {
                BlockPos current = frontier.remove(0);
                for (Direction direction : Iterate.directions) {
                    BlockPos next = current.relative(direction);
                    if (!positions.remove(next)) {
                        continue;
                    }
                    splitGroup.add(next);
                    frontier.add(next);
                }
            }

            initAndAddAll(world, crafter1, positions);
            initAndAddAll(world, crafter2, splitGroup);

            crafter1.setChanged();
            crafter1.connectivityChanged();
            crafter2.setChanged();
            crafter2.connectivityChanged();
            return;
        }

        // 情况二：两端分属不同组 → 合并
        if (!crafter1.getInput().isController()) {
            crafter1 = ExtremeCrafterHelper.getCrafter(world, controllerPos1);
        }
        if (!crafter2.getInput().isController()) {
            crafter2 = ExtremeCrafterHelper.getCrafter(world, controllerPos2);
        }
        if (crafter1 == null || crafter2 == null) {
            return;
        }

        connectControllers(world, crafter1, crafter2);

        world.setBlock(crafter1.getBlockPos(), crafter1.getBlockState(), Block.UPDATE_ALL);

        crafter1.setChanged();
        crafter1.connectivityChanged();
        crafter2.setChanged();
        crafter2.connectivityChanged();
    }

    public static void initAndAddAll(Level world, ExtremeMechanicalCrafterBlockEntity crafter,
                                     Collection<BlockPos> positions) {
        crafter.setInput(new ConnectedInput());
        positions.forEach(splitPos -> modifyAndUpdate(world, splitPos, input -> {
            input.attachTo(crafter.getBlockPos(), splitPos);
            crafter.getInput().getData().add(splitPos.subtract(crafter.getBlockPos()));
        }));
    }

    public static void connectControllers(Level world, ExtremeMechanicalCrafterBlockEntity crafter1,
                                          ExtremeMechanicalCrafterBlockEntity crafter2) {

        crafter1.getInput().getData().forEach(offset -> {
            BlockPos connectedPos = crafter1.getBlockPos().offset(offset);
            modifyAndUpdate(world, connectedPos, input -> {
            });
        });

        crafter2.getInput().getData().forEach(offset -> {
            if (offset.equals(BlockPos.ZERO)) {
                return;
            }
            BlockPos connectedPos = crafter2.getBlockPos().offset(offset);
            modifyAndUpdate(world, connectedPos, input -> {
                input.attachTo(crafter1.getBlockPos(), connectedPos);
                crafter1.getInput().getData().add(BlockPos.ZERO.subtract(input.getData().get(0)));
            });
        });

        crafter2.getInput().attachTo(crafter1.getBlockPos(), crafter2.getBlockPos());
        crafter1.getInput().getData().add(BlockPos.ZERO.subtract(crafter2.getInput().getData().get(0)));
    }

    private static void modifyAndUpdate(Level world, BlockPos pos, Consumer<ConnectedInput> callback) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof ExtremeMechanicalCrafterBlockEntity crafter)) {
            return;
        }
        callback.accept(crafter.getInput());
        crafter.setChanged();
        crafter.connectivityChanged();
    }

    /**
     * 一个连接组的连接数据。
     * <p>
     * 与 Create 相同：只保存「指向 controller 的相对坐标」，
     * controller 自身的 {@code data} 保存组内所有成员的相对坐标（含 {@link BlockPos#ZERO}）。
     */
    public static class ConnectedInput {

        boolean isController;
        List<BlockPos> data = Collections.synchronizedList(new ArrayList<>());

        public ConnectedInput() {
            isController = true;
            data.add(BlockPos.ZERO);
        }

        public boolean isController() {
            return isController;
        }

        public List<BlockPos> getData() {
            return data;
        }

        public void attachTo(BlockPos controllerPos, BlockPos myPos) {
            isController = false;
            data.clear();
            data.add(controllerPos.subtract(myPos));
        }

        public IItemHandler getItemHandler(Level world, BlockPos pos) {
            List<Inventory> inventories = this.getInventories(world, pos);
            return new CombinedInvWrapper(inventories.toArray(IItemHandlerModifiable[]::new));
        }

        /**
         * 取得按连接组顺序排序的库存列表。
         * <p>
         * 排序规则与原版一致：先按 Y 从高到低，再按「朝向顺时针方向的轴」排序。
         * 该顺序同时决定自动输入的槽位顺序与包装物流按配方分发的顺序。
         */
        public List<Inventory> getInventories(Level world, BlockPos pos) {
            if (!isController) {
                BlockPos controllerPos = pos.offset(data.get(0));
                ConnectedInput input = ExtremeCrafterHelper.getInput(world, controllerPos);
                if (input == this || input == null || !input.isController) {
                    return List.of();
                }
                return input.getInventories(world, controllerPos);
            }

            Direction facing = Direction.SOUTH;
            BlockState blockState = world.getBlockState(pos);
            if (blockState.hasProperty(HorizontalKineticBlock.HORIZONTAL_FACING)) {
                facing = blockState.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            }
            AxisDirection axisDirection = facing.getAxisDirection();
            Axis compareAxis = facing.getClockWise().getAxis();

            Comparator<BlockPos> invOrdering = (p1, p2) -> {
                int compareY = -Integer.compare(p1.getY(), p2.getY());
                int modifier = axisDirection.getStep() * (compareAxis == Axis.Z ? -1 : 1);
                int c1 = compareAxis.choose(p1.getX(), p1.getY(), p1.getZ());
                int c2 = compareAxis.choose(p2.getX(), p2.getY(), p2.getZ());
                return compareY != 0 ? compareY : modifier * Integer.compare(c1, c2);
            };

            return data.stream()
                    .sorted(invOrdering)
                    .map(l -> ExtremeCrafterHelper.getCrafter(world, pos.offset(l)))
                    .filter(Objects::nonNull)
                    .map(ExtremeMechanicalCrafterBlockEntity::getInventory)
                    .collect(Collectors.toList());
        }

        public void write(CompoundTag nbt) {
            nbt.putBoolean("Controller", isController);
            ListTag list = new ListTag();
            data.forEach(pos -> {
                CompoundTag data = new CompoundTag();
                data.putInt("X", pos.getX());
                data.putInt("Y", pos.getY());
                data.putInt("Z", pos.getZ());
                list.add(data);
            });
            nbt.put("Data", list);
        }

        public void read(CompoundTag nbt) {
            isController = nbt.getBoolean("Controller");
            data = Collections.synchronizedList(new ArrayList<>(NBTHelper.readCompoundList(nbt.getList("Data", Tag.TAG_COMPOUND),
                    c -> new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z")))));

            // nbt 被清空 → 重置为自己就是 controller
            if (data.isEmpty()) {
                isController = true;
                data.add(BlockPos.ZERO);
            }
        }
    }
}
