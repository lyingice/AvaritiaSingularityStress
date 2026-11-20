package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import committee.nova.avaritia_singularity_stress.content.extreme_connector.ExtremeConnectorBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B29 终端的只读合成网络发现与严格单链验证（服务端调用）。
 * <p>
 * 网络 = 终端相邻连接器出发、经相邻连接器 BFS 扩展的连通连接器集合；
 * 每个背贴连接器纳管其正前方那台合成器。这里只做只读快照，
 * <b>仓库库存检查与事务回滚由核心合成事务负责</b>，不在本类实现。
 * <p>
 * 网格坐标与游戏实际合成完全一致：合成时前置合成器按自身 pointing 经
 * {@code GroupedItems.mergeOnto}（LEFT→x+1 / RIGHT→x-1 / DOWN→y+1 / UP→y-1）汇入下游，
 * 故 {@code coord(c) = coord(target(c)) + offset(c.pointing)}，输出根 coord=(0,0)。
 * 这里从唯一根沿 target 反向边迭代推坐标，供终端把配方格投到对应合成器。
 */
public final class CraftingTerminalNetwork {

    /**
     * 9×9 配方输入上限对应 81 台（docs/教程底稿-模组机制总集.md:223）。
     * <p>
     * 该文档只证明“配方输入”上限，并不证明物理冗余不能超过 81；
     * 本期按约定接受 81 作为 managed 节点硬上限，换取有界扫描。
     * 若以后要支持更大的物理冗余网络，需要另开编号调整上限。
     */
    private static final int MAX_MANAGED_CRAFTERS = 81;

    /**
     * 单次连接器 BFS 的访问预算。9×9 网格最多 81 个背贴连接器；
     * 4096 约为其 50 倍，给中继/绕线留出余量，同时防止损坏存档或
     * 恶意网络把 BFS 拖成无界扫描。
     */
    private static final int MAX_CONNECTOR_VISITS = 4096;

    private static final Comparator<ExtremeMechanicalCrafterBlockEntity> BY_POS =
            Comparator.comparingInt((ExtremeMechanicalCrafterBlockEntity be) -> be.getBlockPos().getX())
                    .thenComparingInt(be -> be.getBlockPos().getY())
                    .thenComparingInt(be -> be.getBlockPos().getZ());

    private CraftingTerminalNetwork() {
    }

    /**
     * B29 只读网络快照。{@code root} 是唯一输出根；{@code crafters} 按方块位置稳定排序；
     * {@code coords} 与 {@code crafters} 一一对应。无有效单链时 {@link #findCraftingGrid}
     * 返回 {@code null}，不会返回半条链。
     */
    public record CraftingGrid(ExtremeMechanicalCrafterBlockEntity root,
                               List<ExtremeMechanicalCrafterBlockEntity> crafters,
                               Map<BlockPos, Pair<Integer, Integer>> coords) {
        public CraftingGrid {
            crafters = List.copyOf(crafters);
            coords = Collections.unmodifiableMap(new LinkedHashMap<>(coords));
        }
    }

    private static final class ConnectorScan {
        private final Set<BlockPos> connectors = new LinkedHashSet<>();
        private boolean failed;
    }

    /**
     * 从终端相邻连接器出发 BFS，收集同一网络的所有连接器位置。
     * <p>
     * 只读取已加载区段：任一待检查邻居未加载（且仍在世界硬坐标范围内）时，
     * 无法确定网络边界，直接 fail closed 返回空集。所有 {@code getBlockState}
     * 前都先做 {@code isLoaded}，绝不通过读取 API 隐式拉取区块。
     */
    public static Set<BlockPos> findConnectors(Level level, BlockPos terminalPos) {
        if (level == null || terminalPos == null) {
            return Set.of();
        }
        ConnectorScan scan = scanConnectors(level, terminalPos);
        if (scan.failed) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(scan.connectors));
    }

    private static ConnectorScan scanConnectors(Level level, BlockPos terminalPos) {
        ConnectorScan result = new ConnectorScan();
        if (isKnownEmpty(level, terminalPos) || !level.isLoaded(terminalPos)) {
            result.failed = true;
            return result;
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = terminalPos.relative(direction);
            if (isKnownEmpty(level, neighbor)) {
                continue;
            }
            if (!level.isLoaded(neighbor)) {
                result.failed = true;
                return result;
            }
            if (isConnector(level, neighbor) && result.connectors.add(neighbor)) {
                queue.add(neighbor);
            }
        }

        int visits = 0;
        while (!queue.isEmpty()) {
            if (++visits > MAX_CONNECTOR_VISITS) {
                result.failed = true;
                return result;
            }
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (isKnownEmpty(level, neighbor)) {
                    continue;
                }
                if (!level.isLoaded(neighbor)) {
                    result.failed = true;
                    return result;
                }
                if (isConnector(level, neighbor) && result.connectors.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    /**
     * 只把世界硬坐标范围（高度 + 水平 ±30,000,000）外视为“已知空”。
     * <p>
     * 动态 world border 可以收缩，合法已存在的连接器/合成器可能留在边界外；
     * 原版链处理仍会查到它们。这里必须在已加载时照常读拓扑，不能把真实出邻居
     * 误判成输出根。边界外的合成器由 {@link #canCraftersTick} 明确拒绝起工。
     */
    private static boolean isKnownEmpty(Level level, BlockPos pos) {
        return !level.isInWorldBounds(pos);
    }

    private static boolean isConnector(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ExtremeConnectorBlock;
    }

    /**
     * B29 网络入口：发现连接器、筛出被管理的合成器，并验证它们构成一条
     * 可安全起工的单根连通链。失败一律返回 {@code null}。
     * <p>
     * “managed”按<b>位置语义</b>认背贴：连接器恰在合成器背面（合成器 HORIZONTAL_FACING
     * 的反方向一格）即纳管，与连接器自身 FACING 无关（对着相邻连接器侧边放置也算）。
     * 侧贴/顶贴连接器只作为中继，不纳管，也不要求中继连接器前方必须有合成器。
     * <p>
     * B26 炽骨合成器可被纳管（2026-11-20 放宽）：品类准入由
     * {@code ExtremeRecipeGridHandler.supportsAutoCraftFor} 按根品类收窄；
     * 连接判定继续复用 {@link ExtremeCrafterHelper#canConnect}，跨系列混链天然拒绝，
     * 终焉锻造器只要真实链规则允许混入就照常参与。
     * <p>
     * 发现阶段允许读取已加载区块；但起工前要求每台 managed 合成器所在区块
     * 真的会 tick（见 {@link #canCraftersTick}），避免“区块已加载但 BE 不 tick”
     * 时终端仍把材料投进去。
     */
    @Nullable
    public static CraftingGrid findCraftingGrid(Level level, BlockPos terminalPos) {
        if (level == null || terminalPos == null) {
            return null;
        }
        ConnectorScan scan = scanConnectors(level, terminalPos);
        if (scan.failed) {
            return null;
        }

        Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos = new LinkedHashMap<>();
        for (BlockPos connectorPos : scan.connectors) {
            if (!level.isLoaded(connectorPos)) {
                return null; // BFS 已检查过，防御性 fail closed
            }
            BlockState connectorState = level.getBlockState(connectorPos);
            if (!(connectorState.getBlock() instanceof ExtremeConnectorBlock)) {
                continue; // BFS 后同 tick 内理论上不会变，防御性处理
            }
            // 位置语义：水平四邻中，凡背面正好是本连接器的合成器都纳管，
            // 不读连接器自身 FACING（重放/侧边点击放置不影响判定）。
            for (Direction horizontal : Direction.Plane.HORIZONTAL) {
                BlockPos crafterPos = connectorPos.relative(horizontal);
                if (isKnownEmpty(level, crafterPos)) {
                    continue;
                }
                if (!level.isLoaded(crafterPos)) {
                    return null; // BFS 已检查过，防御性 fail closed
                }
                BlockState crafterState = level.getBlockState(crafterPos);
                if (!ExtremeCrafterHelper.isCrafter(crafterState)) {
                    continue; // 普通中继连接器
                }
                // B26 炽骨系列自 2026-11-20 起也可被终端纳管；品类准入在
                // ExtremeRecipeGridHandler.supportsAutoCraftFor 收窄为前两档+B31。
                // 跨系列混链仍由 canConnect 天然拒绝，合法单链必为单一品类。
                // 背贴 = 连接器在合成器背面；连接器→合成器方向与合成器朝向相同。
                if (crafterState.getValue(HORIZONTAL_FACING) != horizontal) {
                    continue;
                }
                if (!canCraftersTick(level, crafterPos)) {
                    return null;
                }
                ExtremeMechanicalCrafterBlockEntity crafter = ExtremeCrafterHelper.getCrafter(level, crafterPos);
                if (crafter == null) {
                    return null; // 方块是合成器但 BE 缺失：状态不一致，fail closed
                }
                if (managedByPos.putIfAbsent(crafterPos, crafter) == null
                        && managedByPos.size() > MAX_MANAGED_CRAFTERS) {
                    return null;
                }
            }
        }
        if (managedByPos.isEmpty()) {
            return null;
        }
        return buildGrid(level, managedByPos);
    }

    /** 收集网络内所有被管理的合成器；无有效单链时为空表（fail closed）。 */
    public static List<ExtremeMechanicalCrafterBlockEntity> findCrafters(Level level, BlockPos terminalPos) {
        CraftingGrid grid = findCraftingGrid(level, terminalPos);
        return grid == null ? List.of() : grid.crafters();
    }

    /**
     * 推算每个合成器的网格坐标（与合成时 mergeOnto 累积一致）。
     * <p>
     * 传入列表必须是完整 managed 链；缺节点、环、多根、坐标碰撞或存在未纳管的
     * 实际入/出邻居时返回空 map。坐标 offset 与 Create
     * {@code RecipeGridHandler.GroupedItems.mergeOnto}（RecipeGridHandler.java:188-194；
     * 本模组副本 ExtremeRecipeGridHandler.java:463-469）一致：
     * LEFT→x+1 / RIGHT→x-1 / DOWN→y+1 / UP→y-1。
     */
    public static Map<BlockPos, Pair<Integer, Integer>> gridCoords(
            List<ExtremeMechanicalCrafterBlockEntity> crafters) {
        if (crafters == null || crafters.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos = new LinkedHashMap<>();
        Level level = null;
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            if (crafter == null) {
                return Map.of();
            }
            Level crafterLevel = crafter.getLevel();
            if (crafterLevel == null) {
                return Map.of(); // 没有世界就无法核对实际邻居，fail closed
            }
            if (level == null) {
                level = crafterLevel;
            } else if (level != crafterLevel) {
                return Map.of();
            }
            if (managedByPos.putIfAbsent(crafter.getBlockPos(), crafter) != null) {
                return Map.of();
            }
            if (managedByPos.size() > MAX_MANAGED_CRAFTERS) {
                return Map.of();
            }
        }
        CraftingGrid grid = buildGrid(level, managedByPos);
        return grid == null ? Map.of() : grid.coords();
    }

    private static CraftingGrid buildGrid(Level level,
                                          Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos) {
        if (managedByPos.isEmpty() || managedByPos.size() > MAX_MANAGED_CRAFTERS) {
            return null;
        }
        List<ExtremeMechanicalCrafterBlockEntity> crafters = new ArrayList<>(managedByPos.values());
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            if (crafter == null || !ExtremeCrafterHelper.isCrafter(crafter.getBlockState())) {
                return null;
            }
            if (!level.isLoaded(crafter.getBlockPos()) || isKnownEmpty(level, crafter.getBlockPos())) {
                return null;
            }
        }
        crafters.sort(BY_POS);

        Map<BlockPos, BlockPos> targetOf = findTargets(level, crafters, managedByPos);
        if (targetOf == null) {
            return null;
        }
        if (!validatePreceding(level, crafters, managedByPos, targetOf)) {
            return null;
        }
        BlockPos rootPos = findSingleRoot(crafters, targetOf);
        if (rootPos == null) {
            return null;
        }
        Map<BlockPos, Pair<Integer, Integer>> coords = computeCoords(crafters, managedByPos, targetOf, rootPos);
        if (coords == null) {
            return null;
        }
        ExtremeMechanicalCrafterBlockEntity root = managedByPos.get(rootPos);
        if (root == null) {
            return null;
        }
        Map<BlockPos, Pair<Integer, Integer>> ordered = new LinkedHashMap<>();
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            Pair<Integer, Integer> coord = coords.get(crafter.getBlockPos());
            if (coord == null) {
                return null;
            }
            ordered.put(crafter.getBlockPos(), coord);
        }
        return new CraftingGrid(root, crafters, ordered);
    }

    /**
     * 按真实 {@code ExtremeRecipeGridHandler.getTargetingCrafter}
     * （ExtremeRecipeGridHandler.java:116-138）构建出边。
     * 额外要求目标位置已加载、目标 BE 存在且已纳管；否则返回 {@code null}。
     * 未加载的潜在下游绝不能当作“输出根”。
     */
    private static Map<BlockPos, BlockPos> findTargets(
            Level level, List<ExtremeMechanicalCrafterBlockEntity> crafters,
            Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos) {
        Map<BlockPos, BlockPos> targetOf = new LinkedHashMap<>();
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            BlockState state = crafter.getBlockState();
            BlockPos pos = crafter.getBlockPos();
            BlockPos targetPos = pos.relative(ExtremeMechanicalCrafterBlock.getTargetDirection(state));
            if (isKnownEmpty(level, targetPos)) {
                continue; // 世界硬坐标范围外不可能有合成器，按无目标处理
            }
            if (!level.isLoaded(targetPos)) {
                return null; // 未加载的潜在下游不能当输出根
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (!ExtremeCrafterHelper.isCrafter(targetState)) {
                continue;
            }
            if (!ExtremeCrafterHelper.canConnect(state, targetState)) {
                continue; // B26 与终焉系列按真实 canConnect 隔离
            }
            if (state.getValue(HORIZONTAL_FACING) != targetState.getValue(HORIZONTAL_FACING)) {
                continue;
            }
            if (!managedByPos.containsKey(targetPos)) {
                return null; // 实际出邻居没有被连接器纳管 = 少贴
            }
            if (ExtremeCrafterHelper.getCrafter(level, targetPos) == null) {
                return null; // 状态是合成器但没有 BE，不能假装它是根
            }
            targetOf.put(pos, targetPos);
        }
        return targetOf;
    }

    /**
     * 按真实 {@code ExtremeRecipeGridHandler.getPrecedingCrafters}
     * （ExtremeRecipeGridHandler.java:145-185）核对入边：
     * 所有实际前置必须在 managed 内，且其真实 target 必须就是当前节点；
     * 反向 target 边也必须能被前置规则识别。任何不一致都 fail closed。
     */
    private static boolean validatePreceding(
            Level level, List<ExtremeMechanicalCrafterBlockEntity> crafters,
            Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos,
            Map<BlockPos, BlockPos> targetOf) {
        Map<BlockPos, Set<BlockPos>> precedingByNode = new HashMap<>();
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            BlockPos pos = crafter.getBlockPos();
            BlockState state = crafter.getBlockState();
            Direction blockFacing = state.getValue(HORIZONTAL_FACING);
            Direction blockPointing = ExtremeMechanicalCrafterBlock.getTargetDirection(state);
            Set<BlockPos> actualPreceding = new HashSet<>();
            for (Direction facing : Iterate.directions) {
                if (blockFacing.getAxis() == facing.getAxis()) {
                    continue;
                }
                if (blockPointing == facing) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(facing);
                if (isKnownEmpty(level, neighborPos)) {
                    continue;
                }
                if (!level.isLoaded(neighborPos)) {
                    return false;
                }
                BlockState neighborState = level.getBlockState(neighborPos);
                if (!ExtremeCrafterHelper.canConnect(state, neighborState)) {
                    continue;
                }
                if (ExtremeMechanicalCrafterBlock.getTargetDirection(neighborState) != facing.getOpposite()) {
                    continue;
                }
                if (blockFacing != neighborState.getValue(HORIZONTAL_FACING)) {
                    continue;
                }
                if (!managedByPos.containsKey(neighborPos)) {
                    return false; // 实际入邻居没有连接器纳管 = 少贴
                }
                if (ExtremeCrafterHelper.getCrafter(level, neighborPos) == null) {
                    return false;
                }
                if (!pos.equals(targetOf.get(neighborPos))) {
                    return false; // 前置的真实 target 不是当前节点，流向不一致
                }
                actualPreceding.add(neighborPos);
            }
            precedingByNode.put(pos, actualPreceding);
        }
        // target 边必须与前置规则一致；2-cycle 会因为前置扫描跳过 blockPointing 方向而在这里被拒。
        for (Map.Entry<BlockPos, BlockPos> edge : targetOf.entrySet()) {
            Set<BlockPos> preceding = precedingByNode.get(edge.getValue());
            if (preceding == null || !preceding.contains(edge.getKey())) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos findSingleRoot(List<ExtremeMechanicalCrafterBlockEntity> crafters,
                                           Map<BlockPos, BlockPos> targetOf) {
        BlockPos root = null;
        for (ExtremeMechanicalCrafterBlockEntity crafter : crafters) {
            BlockPos pos = crafter.getBlockPos();
            if (targetOf.containsKey(pos)) {
                continue;
            }
            if (root != null) {
                return null; // 多链 / 多根
            }
            root = pos;
        }
        return root; // null 表示全都有 target：环
    }

    /**
     * 从唯一根沿 target 反向边迭代推坐标；不递归，避免长链栈深。
     * 同时拒绝环、未到达节点和坐标碰撞。
     */
    private static Map<BlockPos, Pair<Integer, Integer>> computeCoords(
            List<ExtremeMechanicalCrafterBlockEntity> crafters,
            Map<BlockPos, ExtremeMechanicalCrafterBlockEntity> managedByPos,
            Map<BlockPos, BlockPos> targetOf, BlockPos rootPos) {
        Map<BlockPos, List<BlockPos>> incoming = new HashMap<>();
        for (Map.Entry<BlockPos, BlockPos> edge : targetOf.entrySet()) {
            incoming.computeIfAbsent(edge.getValue(), key -> new ArrayList<>()).add(edge.getKey());
        }
        Map<BlockPos, Pair<Integer, Integer>> coords = new HashMap<>();
        Map<Pair<Integer, Integer>, BlockPos> occupied = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Pair<Integer, Integer> rootCoord = Pair.of(0, 0);
        coords.put(rootPos, rootCoord);
        occupied.put(rootCoord, rootPos);
        queue.add(rootPos);
        while (!queue.isEmpty()) {
            BlockPos parentPos = queue.poll();
            Pair<Integer, Integer> parentCoord = coords.get(parentPos);
            for (BlockPos childPos : incoming.getOrDefault(parentPos, List.of())) {
                if (coords.containsKey(childPos)) {
                    return null; // 同一节点被多条路径到达/环
                }
                ExtremeMechanicalCrafterBlockEntity child = managedByPos.get(childPos);
                if (child == null) {
                    return null;
                }
                Pair<Integer, Integer> childCoord = add(parentCoord, offset(child));
                if (occupied.putIfAbsent(childCoord, childPos) != null) {
                    return null; // 坐标一一对应失败
                }
                coords.put(childPos, childCoord);
                queue.add(childPos);
            }
        }
        if (coords.size() != crafters.size()) {
            return null; // 有节点未沿链到达根（环/孤立链）
        }
        return coords;
    }

    private static Pair<Integer, Integer> add(Pair<Integer, Integer> coord, int[] offset) {
        return Pair.of(coord.getKey() + offset[0], coord.getValue() + offset[1]);
    }

    /** pointing 对应的 mergeOnto 偏移（与 GroupedItems.mergeOnto 逐行一致）。 */
    private static int[] offset(ExtremeMechanicalCrafterBlockEntity crafter) {
        Pointing pointing = crafter.getBlockState().getValue(ExtremeMechanicalCrafterBlock.POINTING);
        int dx = pointing == Pointing.LEFT ? 1 : pointing == Pointing.RIGHT ? -1 : 0;
        int dy = pointing == Pointing.DOWN ? 1 : pointing == Pointing.UP ? -1 : 0;
        return new int[]{dx, dy};
    }

    /**
     * 判定合成器所在区块是否真的会执行 BE tick。
     * <p>
     * {@code LevelChunk.BoundTickingBlockEntity.tick}（LevelChunk.java:704）先检查
     * {@code LevelChunk.isTicking}（LevelChunk.java:373-380）：BLOCK_TICKING + 实体已加载；
     * {@code ServerLevel.tickBlockEntities} 还要求 {@code shouldTickBlocksAt}
     * （ServerLevel.java:437-439）。{@code ServerChunkCache.isPositionTicking}
     * （ServerChunkCache.java:290-297）覆盖 ticking future + 距离，
     * {@code areEntitiesLoaded} 补齐实体加载条件。这些查询只读现存
     * chunk/entity 状态，不触发加载。
     */
    private static boolean canCraftersTick(Level level, BlockPos pos) {
        if (isKnownEmpty(level, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            // 客户端 LevelChunk.isTicking 只检查世界边界。
            return true;
        }
        long chunkKey = ChunkPos.asLong(pos);
        return serverLevel.getChunkSource().isPositionTicking(chunkKey)
                && serverLevel.areEntitiesLoaded(chunkKey);
    }
}
