package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_connector.ExtremeConnectorBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.CraftingTerminalNetwork.CraftingGrid;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import committee.nova.avaritia_singularity_stress.content.recipes.BlazeCubeCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessService;
import committee.nova.avaritia_singularity_stress.content.wireless.item_funnel.WirelessItemFunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * B29 终端供料规划器（服务端、无副作用预演 + 单 tick 事务提交）。
 * <p>
 * 设计边界：
 * <ul>
 *   <li>只支持 {@link ExtremeRecipeGridHandler#supportsAutoCraft(Recipe)} 白名单内的配方；</li>
 *   <li>先完整模拟仓库可抽数量、用容量匹配分配 A|B 竞争候选，再用真实
 *       {@code recipe.matches} 验证最终虚拟输入；任何一步失败都不抽物品；</li>
 *   <li>提交时逐笔核对原来源 handler/slot 的数量与组件，失败按逆序只退还本单实际抽到的栈；</li>
 *   <li>起工通过 {@code startTerminalCraft} 直接写入 GroupedItems，不经过会自行起工的 insertItem。</li>
 * </ul>
 * 不在本类中处理 UI、区块加载或第三方 handler 的非契约行为。
 */
final class TerminalCraftPlanner {

    static final int MAX_CELLS = 81;
    static final int MAX_DIMENSION = 9;

    private TerminalCraftPlanner() {
    }

    enum Status {
        OK, RECIPE_UNSUPPORTED, GRID_MISMATCH, MISSING_MATERIALS
    }

    record Cell(ExtremeMechanicalCrafterBlockEntity crafter,
                Pair<Integer, Integer> coord,
                Ingredient ingredient,
                ItemStack input) {
    }

    record ExtractionUnit(IItemHandler handler, int slot, Ingredient ingredient,
                          ItemStack template, int cellIndex) {
    }

    static final class Plan {
        final Status status;
        @Nullable
        final RecipeHolder<?> holder;
        @Nullable
        final ExtremeRecipeGridHandler.TerminalCraftOutcome expected;
        final List<Cell> cells;
        final List<ExtractionUnit> extractions;

        private Plan(Status status, @Nullable RecipeHolder<?> holder,
                     @Nullable ExtremeRecipeGridHandler.TerminalCraftOutcome expected,
                     List<Cell> cells, List<ExtractionUnit> extractions) {
            this.status = status;
            this.holder = holder;
            this.expected = expected;
            this.cells = cells;
            this.extractions = extractions;
        }

        static Plan status(Status status) {
            return new Plan(status, null, null, List.of(), List.of());
        }

        static Plan ok(RecipeHolder<?> holder,
                       ExtremeRecipeGridHandler.TerminalCraftOutcome expected,
                       List<Cell> cells, List<ExtractionUnit> extractions) {
            return new Plan(Status.OK, holder, expected, List.copyOf(cells), List.copyOf(extractions));
        }
    }

    private record Demand(int row, int col, Ingredient ingredient) {
    }

    private record Placement(int anchorX, int anchorY, boolean mirror) {
    }

    private record Anchor(int x, int y) {
    }

    private record Taken(IItemHandler handler, int slot, ItemStack stack) {
    }

    private static final class OfferSource {
        final IItemHandler handler;
        final int slot;
        final int capacity;

        OfferSource(IItemHandler handler, int slot, int capacity) {
            this.handler = handler;
            this.slot = slot;
            this.capacity = capacity;
        }
    }

    private static final class OfferGroup {
        final ItemStack template;
        final List<OfferSource> sources = new ArrayList<>();
        int capacity;

        OfferGroup(ItemStack template) {
            this.template = template;
        }
    }

    private static final class MatchResult {
        final List<OfferGroup> groups;
        final int[] demandGroup;

        MatchResult(List<OfferGroup> groups, int[] demandGroup) {
            this.groups = groups;
            this.demandGroup = demandGroup;
        }
    }

    // ------------------------------------------------------------------
    // 规划
    // ------------------------------------------------------------------

    static Plan plan(Level level, BlockPos terminalPos, CraftingGrid grid, RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (!ExtremeRecipeGridHandler.supportsAutoCraft(recipe)) {
            return Plan.status(Status.RECIPE_UNSUPPORTED);
        }
        if (recipe instanceof ExtremeKineticCraftingRecipe kinetic) {
            return planShaped(level, terminalPos, grid, holder, kinetic.acceptsMirrored());
        }
        if (recipe instanceof ShapedTableCraftingRecipe) {
            // ShapedRecipePattern.matches 自身会尝试水平镜像（对称图案除外）。
            return planShaped(level, terminalPos, grid, holder, true);
        }
        if (recipe.getClass() == ShapelessTableCraftingRecipe.class) {
            return planShapeless(level, terminalPos, grid, holder);
        }
        return Plan.status(Status.RECIPE_UNSUPPORTED);
    }

    private static Plan planShaped(Level level, BlockPos terminalPos, CraftingGrid grid,
                                   RecipeHolder<?> holder, boolean mirrorAllowed) {
        ExtremeCraftingTerminalBlockEntity.RecipeLayout layout =
                ExtremeCraftingTerminalBlockEntity.layoutOf(holder.value());
        if (layout == null || layout.width() <= 0 || layout.height() <= 0
                || layout.width() > MAX_DIMENSION || layout.height() > MAX_DIMENSION
                || (long) layout.width() * layout.height() > MAX_CELLS) {
            return Plan.status(Status.RECIPE_UNSUPPORTED);
        }

        List<Demand> demands = new ArrayList<>();
        for (int row = 0; row < layout.height(); row++) {
            for (int col = 0; col < layout.width(); col++) {
                Ingredient ing = layout.cells().get(row * layout.width() + col);
                if (!ing.isEmpty()) {
                    demands.add(new Demand(row, col, ing));
                }
            }
        }
        if (demands.isEmpty()) {
            return Plan.status(Status.RECIPE_UNSUPPORTED);
        }

        List<Placement> placements = new ArrayList<>();
        Map<Pair<Integer, Integer>, Boolean> cells = cellsOf(grid);
        Placement normal = findPlacement(cells, layout, demands, false);
        if (normal != null) {
            placements.add(normal);
        }
        if (mirrorAllowed) {
            Placement mirrored = findPlacement(cells, layout, demands, true);
            if (mirrored != null) {
                placements.add(mirrored);
            }
        }
        if (placements.isEmpty()) {
            return Plan.status(Status.GRID_MISMATCH);
        }

        // 平移/镜像只改变格子坐标，不改变需求材料的多重集；容量匹配只需做一次。
        MatchResult match = match(level, terminalPos, demands);
        if (match == null) {
            return Plan.status(Status.MISSING_MATERIALS);
        }
        List<ItemStack> demandStacks = assignedStacks(demands, match);

        for (Placement placement : placements) {
            Map<Pair<Integer, Integer>, Integer> demandByCoord = new HashMap<>();
            for (int i = 0; i < demands.size(); i++) {
                demandByCoord.put(mappedCoord(layout, placement, demands.get(i)), i);
            }
            Plan plan = buildPlan(level, grid, holder, demands, demandByCoord, demandStacks, match);
            if (plan != null) {
                return plan;
            }
        }
        return Plan.status(Status.GRID_MISMATCH);
    }

    private static Plan planShapeless(Level level, BlockPos terminalPos, CraftingGrid grid,
                                      RecipeHolder<?> holder) {
        if (!(holder.value() instanceof ShapelessTableCraftingRecipe shapeless)) {
            return Plan.status(Status.RECIPE_UNSUPPORTED);
        }
        List<Ingredient> ingredients = shapeless.getIngredients();
        if (ingredients.isEmpty() || ingredients.size() > MAX_CELLS) {
            return Plan.status(Status.RECIPE_UNSUPPORTED);
        }
        List<Demand> demands = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                // 空 Ingredient 无法从仓库稳定匹配，也不应改变配方材料计数。
                return Plan.status(Status.RECIPE_UNSUPPORTED);
            }
            demands.add(new Demand(0, 0, ingredient));
        }

        Map<Pair<Integer, Integer>, Boolean> cells = cellsOf(grid);
        List<Pair<Integer, Integer>> available = new ArrayList<>(cells.keySet());
        available.sort(Comparator.comparingInt((Pair<Integer, Integer> p) -> p.getKey())
                .thenComparingInt(p -> p.getValue()));
        List<Pair<Integer, Integer>> chosen = new ArrayList<>(demands.size());
        for (Pair<Integer, Integer> coord : available) {
            if (!cells.get(coord)) {
                chosen.add(coord);
                if (chosen.size() == demands.size()) {
                    break;
                }
            }
        }
        if (chosen.size() < demands.size()) {
            return Plan.status(Status.GRID_MISMATCH);
        }

        MatchResult match = match(level, terminalPos, demands);
        if (match == null) {
            return Plan.status(Status.MISSING_MATERIALS);
        }
        List<ItemStack> demandStacks = assignedStacks(demands, match);
        Map<Pair<Integer, Integer>, Integer> demandByCoord = new HashMap<>();
        for (int i = 0; i < chosen.size(); i++) {
            demandByCoord.put(chosen.get(i), i);
        }
        Plan plan = buildPlan(level, grid, holder, demands, demandByCoord, demandStacks, match);
        return plan != null ? plan : Plan.status(Status.GRID_MISMATCH);
    }

    /** 真实网格 → (坐标 → 是否盖板) 形状表，供规划与客户端列表过滤共用。 */
    public static Map<Pair<Integer, Integer>, Boolean> cellsOf(CraftingGrid grid) {
        Map<Pair<Integer, Integer>, Boolean> cells = new LinkedHashMap<>();
        for (ExtremeMechanicalCrafterBlockEntity crafter : grid.crafters()) {
            cells.put(grid.coords().get(crafter.getBlockPos()), crafter.isCovered());
        }
        return cells;
    }

    /**
     * 只按数量/摆放/盖板判断配方是否放得进当前网格（不做材料匹配）。
     * 客户端列表用它屏蔽"做不到"的配方；服务端 SELECT/CRAFT 同口径复核。
     */
    public static boolean fitsShape(Map<Pair<Integer, Integer>, Boolean> cells, Recipe<?> recipe) {
        ExtremeCraftingTerminalBlockEntity.RecipeLayout layout =
                ExtremeCraftingTerminalBlockEntity.layoutOf(recipe);
        if (layout == null || cells.isEmpty()) {
            return false;
        }
        if (recipe instanceof ShapelessTableCraftingRecipe) {
            long free = 0;
            for (boolean covered : cells.values()) {
                if (!covered) {
                    free++;
                }
            }
            return free >= recipe.getIngredients().size();
        }
        List<Demand> demands = demandsOf(layout);
        if (demands.isEmpty()) {
            return false;
        }
        boolean mirrorAllowed = recipe instanceof ExtremeKineticCraftingRecipe kinetic
                ? kinetic.acceptsMirrored()
                : recipe instanceof BlazeCubeCraftingRecipe blazeCube && blazeCube.acceptsMirrored();
        if (findPlacement(cells, layout, demands, false) != null) {
            return true;
        }
        return mirrorAllowed && findPlacement(cells, layout, demands, true) != null;
    }

    private static List<Demand> demandsOf(ExtremeCraftingTerminalBlockEntity.RecipeLayout layout) {
        List<Demand> demands = new ArrayList<>();
        for (int row = 0; row < layout.height(); row++) {
            for (int col = 0; col < layout.width(); col++) {
                Ingredient ing = layout.cells().get(row * layout.width() + col);
                if (!ing.isEmpty()) {
                    demands.add(new Demand(row, col, ing));
                }
            }
        }
        return demands;
    }

    @Nullable
    private static Placement findPlacement(Map<Pair<Integer, Integer>, Boolean> byCoord,
                                           ExtremeCraftingTerminalBlockEntity.RecipeLayout layout,
                                           List<Demand> demands, boolean mirror) {
        int minX = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Pair<Integer, Integer> coord : byCoord.keySet()) {
            minX = Math.min(minX, coord.getKey());
            maxY = Math.max(maxY, coord.getValue());
        }
        if (byCoord.isEmpty() || minX == Integer.MAX_VALUE) {
            return null;
        }

        // 默认 minX,maxY（行 0 对应最高一行）；其余锚点由「任一非空配方格对准任一真实格」推导。
        Anchor preferred = new Anchor(minX, maxY);
        LinkedHashSet<Anchor> anchors = new LinkedHashSet<>();
        anchors.add(preferred);
        for (Demand demand : demands) {
            for (Pair<Integer, Integer> coord : byCoord.keySet()) {
                int col = mirror ? layout.width() - 1 - demand.col : demand.col;
                anchors.add(new Anchor(coord.getKey() - col, coord.getValue() + demand.row));
            }
        }
        List<Anchor> ordered = new ArrayList<>(anchors);
        ordered.sort(Comparator.comparingInt((Anchor a) -> distance(a, preferred))
                .thenComparingInt(a -> a.x)
                .thenComparingInt(a -> a.y));

        for (Anchor anchor : ordered) {
            Placement placement = new Placement(anchor.x, anchor.y, mirror);
            boolean valid = true;
            for (Demand demand : demands) {
                Pair<Integer, Integer> coord = mappedCoord(layout, placement, demand);
                Boolean covered = byCoord.get(coord);
                if (covered == null || covered) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return placement;
            }
        }
        return null;
    }

    private static int distance(Anchor a, Anchor b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private static Pair<Integer, Integer> mappedCoord(
            ExtremeCraftingTerminalBlockEntity.RecipeLayout layout,
            Placement placement, Demand demand) {
        int col = placement.mirror ? layout.width() - 1 - demand.col : demand.col;
        return Pair.of(placement.anchorX + col, placement.anchorY - demand.row);
    }

    @Nullable
    private static Plan buildPlan(Level level, CraftingGrid grid, RecipeHolder<?> holder,
                                  List<Demand> demands,
                                  Map<Pair<Integer, Integer>, Integer> demandByCoord,
                                  List<ItemStack> demandStacks, MatchResult match) {
        GroupedItems virtual = new GroupedItems();
        for (Pair<Integer, Integer> coord : grid.coords().values()) {
            virtual.grid.put(coord, ItemStack.EMPTY);
        }
        for (Map.Entry<Pair<Integer, Integer>, Integer> entry : demandByCoord.entrySet()) {
            virtual.grid.put(entry.getKey(), demandStacks.get(entry.getValue()).copyWithCount(1));
        }
        virtual.statsReady = false;
        virtual.calcStats();

        // 关键：用同一 exact recipe + 最终模拟输入执行真实 matches，确认无隐藏失配。
        ExtremeRecipeGridHandler.TerminalCraftOutcome expected =
                ExtremeRecipeGridHandler.tryToApplyTerminalRecipe(level, virtual, holder.id());
        if (expected == null) {
            return null;
        }

        Map<Pair<Integer, Integer>, Integer> cellIndexByCoord = new HashMap<>();
        List<Cell> cells = new ArrayList<>(grid.crafters().size());
        for (ExtremeMechanicalCrafterBlockEntity crafter : grid.crafters()) {
            Pair<Integer, Integer> coord = grid.coords().get(crafter.getBlockPos());
            Integer demandIndex = demandByCoord.get(coord);
            Ingredient ingredient = demandIndex == null
                    ? Ingredient.EMPTY : demands.get(demandIndex).ingredient();
            ItemStack input = demandIndex == null
                    ? ItemStack.EMPTY : demandStacks.get(demandIndex).copyWithCount(1);
            cellIndexByCoord.put(coord, cells.size());
            cells.add(new Cell(crafter, coord, ingredient, input));
        }

        int[] demandCell = new int[demands.size()];
        Arrays.fill(demandCell, -1);
        for (Map.Entry<Pair<Integer, Integer>, Integer> entry : demandByCoord.entrySet()) {
            Integer cellIndex = cellIndexByCoord.get(entry.getKey());
            if (cellIndex != null) {
                demandCell[entry.getValue()] = cellIndex;
            }
        }

        List<ExtractionUnit> extractions = buildExtractions(demands, match, demandCell);
        if (extractions == null) {
            return null;
        }
        return Plan.ok(holder, expected, cells, extractions);
    }

    @Nullable
    private static List<ExtractionUnit> buildExtractions(List<Demand> demands, MatchResult match,
                                                         int[] demandCell) {
        Map<Integer, OfferSource> demandSource = new HashMap<>();
        Map<Integer, List<Integer>> byGroup = new HashMap<>();
        for (int i = 0; i < demands.size(); i++) {
            int group = match.demandGroup[i];
            if (group < 0 || group >= match.groups.size()) {
                return null;
            }
            byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<Integer, List<Integer>> entry : byGroup.entrySet()) {
            OfferGroup group = match.groups.get(entry.getKey());
            List<Integer> demandIndices = entry.getValue();
            int next = 0;
            for (OfferSource source : group.sources) {
                int take = Math.min(source.capacity, demandIndices.size() - next);
                for (int i = 0; i < take; i++) {
                    demandSource.put(demandIndices.get(next++), source);
                }
                if (next >= demandIndices.size()) {
                    break;
                }
            }
            if (next != demandIndices.size()) {
                return null;
            }
        }

        List<ExtractionUnit> extractions = new ArrayList<>(demands.size());
        for (int i = 0; i < demands.size(); i++) {
            if (demandCell[i] < 0) {
                return null;
            }
            OfferSource source = demandSource.get(i);
            if (source == null) {
                return null;
            }
            OfferGroup group = match.groups.get(match.demandGroup[i]);
            extractions.add(new ExtractionUnit(source.handler, source.slot, demands.get(i).ingredient,
                    group.template.copyWithCount(1), demandCell[i]));
        }
        return extractions;
    }

    private static List<ItemStack> assignedStacks(List<Demand> demands, MatchResult match) {
        List<ItemStack> stacks = new ArrayList<>(demands.size());
        for (int i = 0; i < demands.size(); i++) {
            stacks.add(match.groups.get(match.demandGroup[i]).template.copyWithCount(1));
        }
        return stacks;
    }

    private static Map<Pair<Integer, Integer>, ExtremeMechanicalCrafterBlockEntity> byCoord(CraftingGrid grid) {
        Map<Pair<Integer, Integer>, ExtremeMechanicalCrafterBlockEntity> map = new HashMap<>();
        for (ExtremeMechanicalCrafterBlockEntity crafter : grid.crafters()) {
            map.put(grid.coords().get(crafter.getBlockPos()), crafter);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 仓库模拟与容量匹配
    // ------------------------------------------------------------------

    @Nullable
    private static MatchResult match(Level level, BlockPos terminalPos, List<Demand> demands) {
        List<OfferGroup> groups = collectOfferGroups(level, terminalPos);
        // 创造板条箱未过滤 = 无条件无限供料：为每个需求补合成来源（真实仓库优先）。
        if (hasUnfilteredCreativeCrate(level, terminalPos)) {
            for (Demand demand : demands) {
                ItemStack[] candidates = demand.ingredient().getItems();
                if (candidates.length == 0) {
                    continue;
                }
                ItemStack template = candidates[0].copyWithCount(1);
                OfferGroup group = null;
                for (OfferGroup candidate : groups) {
                    if (ItemStack.isSameItemSameComponents(candidate.template, template)) {
                        group = candidate;
                        break;
                    }
                }
                if (group == null) {
                    group = new OfferGroup(template);
                    groups.add(group);
                }
                group.sources.add(new OfferSource(null, -1, MAX_CELLS));
                group.capacity = Math.min(MAX_CELLS, group.capacity + MAX_CELLS);
            }
        }
        if (groups.isEmpty()) {
            return null;
        }
        int demandCount = demands.size();
        int groupCount = groups.size();
        int source = demandCount + groupCount;
        int sink = source + 1;
        Dinic dinic = new Dinic(sink + 1);
        for (int i = 0; i < demandCount; i++) {
            dinic.addEdge(source, i, 1);
        }
        for (int i = 0; i < demandCount; i++) {
            Ingredient ingredient = demands.get(i).ingredient();
            for (int j = 0; j < groupCount; j++) {
                if (ingredient.test(groups.get(j).template)) {
                    dinic.addEdge(i, demandCount + j, 1);
                }
            }
        }
        for (int j = 0; j < groupCount; j++) {
            dinic.addEdge(demandCount + j, sink, Math.min(groups.get(j).capacity, demandCount));
        }
        if (dinic.maxFlow(source, sink) != demandCount) {
            return null;
        }

        int[] assignment = new int[demandCount];
        Arrays.fill(assignment, -1);
        for (int i = 0; i < demandCount; i++) {
            for (Dinic.Edge edge : dinic.graph.get(i)) {
                if (edge.to >= demandCount && edge.to < source && edge.original == 1 && edge.cap == 0) {
                    assignment[i] = edge.to - demandCount;
                    break;
                }
            }
            if (assignment[i] < 0) {
                return null;
            }
        }
        return new MatchResult(groups, assignment);
    }

    /**
     * 只模拟、不抽取。容量取 min(模拟可抽数量, 实物数量, 81)。
     * 同一 slot 的多个需求会在提交时按 1 个/笔分别抽取并逐笔复核。
     */
    private static List<OfferGroup> collectOfferGroups(Level level, BlockPos terminalPos) {
        List<OfferGroup> groups = new ArrayList<>();
        for (IItemHandler handler : storageHandlers(level, terminalPos)) {
            int slots;
            try {
                slots = handler.getSlots();
            } catch (RuntimeException ignored) {
                continue;
            }
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack;
                try {
                    stack = handler.getStackInSlot(slot);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (stack.isEmpty()) {
                    continue;
                }
                int want = Math.min(stack.getCount(), MAX_CELLS);
                if (want <= 0) {
                    continue;
                }
                ItemStack simulated;
                try {
                    simulated = handler.extractItem(slot, want, true);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (simulated.isEmpty() || !ItemStack.isSameItemSameComponents(simulated, stack)) {
                    continue;
                }
                int capacity = Math.min(Math.min(simulated.getCount(), stack.getCount()), MAX_CELLS);
                if (capacity <= 0) {
                    continue;
                }
                ItemStack template = simulated.copyWithCount(1);
                OfferGroup group = null;
                for (OfferGroup candidate : groups) {
                    if (ItemStack.isSameItemSameComponents(candidate.template, template)) {
                        group = candidate;
                        break;
                    }
                }
                if (group == null) {
                    group = new OfferGroup(template);
                    groups.add(group);
                }
                group.sources.add(new OfferSource(handler, slot, capacity));
                group.capacity = Math.min(MAX_CELLS, group.capacity + capacity);
            }
        }
        return groups;
    }

    /** 终端相邻仓库：已加载块、使用 d.getOpposite、排除任务机器/终端/连接器并去重同一 handler 对象。 */
    private static List<IItemHandler> storageHandlers(Level level, BlockPos terminalPos) {
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Direction direction : Direction.values()) {
            BlockPos pos = terminalPos.relative(direction);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ExtremeMechanicalCrafterBlock
                    || state.getBlock() instanceof ExtremeCraftingTerminalBlock
                    || state.getBlock() instanceof ExtremeConnectorBlock) {
                continue;
            }
            // 原版双箱能力会读另一半；另一半未加载时先整体跳过，避免隐式拉区块/半箱账目。
            if (state.getBlock() instanceof ChestBlock
                    && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos otherHalf = pos.relative(ChestBlock.getConnectedDirection(state));
                if (!level.isLoaded(otherHalf)) {
                    continue;
                }
            }
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, pos, direction.getOpposite());
            if (handler != null && seen.add(handler)) {
                handlers.add(handler);
            }
        }
        addWirelessFunnelSources(level, terminalPos, handlers, seen);
        return handlers;
    }

    /**
     * B32 无线物品漏斗兼容（锚点模型，按用户纠正）：终端<b>自身不持有频率</b>，
     * 而是扫描六邻面的无线物品漏斗作为"频道锚点"，读锚点的 WirelessKey
     * （频率+所有者+可见性），再经 {@link WirelessNetwork#peers} 找同频道设备，
     * 把其中抽取模式漏斗的"货源容器"（背后优先、开口侧容错，与漏斗自身
     * facingInventory 同语义）作为远程仓库。锚点自身若也是抽取端，其货源一并纳入。
     * <p>
     * 隐私由频道键天然保证：peers 只返回同频率+同所有者+同可见性的设备，
     * 私有频道他人漏斗不会进入列表。距离口径沿用 itemFunnelRange（锚点→终端相邻，
     * 远程漏斗相对终端判定）。
     */
    private static void addWirelessFunnelSources(Level level, BlockPos terminalPos,
                                                 List<IItemHandler> handlers, Set<IItemHandler> seen) {
        double range = WirelessConfig.itemFunnelRange();
        for (Direction direction : Direction.values()) {
            BlockPos anchorPos = terminalPos.relative(direction);
            if (!level.isLoaded(anchorPos)) {
                continue;
            }
            if (!(level.getBlockEntity(anchorPos) instanceof WirelessItemFunnelBlockEntity anchor)
                    || !anchor.hasFrequency() || !anchor.isAlive()) {
                continue;
            }
            addFunnelSource(level, terminalPos, anchor, range, handlers, seen);
            for (WirelessDevice peer : WirelessNetwork.peers(level, anchor.getKey(), anchor)) {
                if (peer instanceof WirelessItemFunnelBlockEntity funnel) {
                    addFunnelSource(level, terminalPos, funnel, range, handlers, seen);
                }
            }
        }
    }

    /** 抽取模式漏斗的货源容器进仓库列表；非抽取端/无货源/超距/未加载跳过。 */
    private static void addFunnelSource(Level level, BlockPos terminalPos,
                                        WirelessItemFunnelBlockEntity funnel, double range,
                                        List<IItemHandler> handlers, Set<IItemHandler> seen) {
        BlockPos funnelPos = funnel.getBlockPos();
        if (!level.isLoaded(funnelPos)
                || !WirelessService.withinRange(terminalPos, funnelPos, range)) {
            return;
        }
        BlockState state = funnel.getBlockState();
        if (!state.getValue(FunnelBlock.EXTRACTING)) {
            return;
        }
        Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
        if (facing == null) {
            return;
        }
        // 与漏斗 facingInventory 同语义：背后是货源，背后没有再容错开口侧。
        BlockPos behind = funnelPos.relative(facing.getOpposite());
        IItemHandler handler = loadedHandler(level, behind, facing);
        if (handler == null) {
            handler = loadedHandler(level, funnelPos.relative(facing), facing.getOpposite());
        }
        if (handler != null && seen.add(handler)) {
            handlers.add(handler);
        }
    }

    @Nullable
    private static IItemHandler loadedHandler(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ExtremeMechanicalCrafterBlock
                || state.getBlock() instanceof ExtremeCraftingTerminalBlock
                || state.getBlock() instanceof ExtremeConnectorBlock) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
    }

    // ------------------------------------------------------------------
    // 事务提交
    // ------------------------------------------------------------------

    /**
     * 先无条件复核整链状态，再实际抽取；抽取后再次复核，最后才写入任何合成器。
     * 失败只按逆序退还本单实际抽到的栈：原槽优先，其次同 handler 其它槽，最后终端旁实体掉落。
     */
    static boolean commit(Level level, BlockPos terminalPos, CraftingGrid grid, Plan plan) {
        if (plan.status != Status.OK || plan.holder == null) {
            return false;
        }
        if (!revalidate(level, terminalPos, grid, plan)) {
            return false;
        }

        ItemStack[] cellInputs = new ItemStack[plan.cells.size()];
        Arrays.fill(cellInputs, ItemStack.EMPTY);
        List<Taken> taken = new ArrayList<>();
        for (ExtractionUnit unit : plan.extractions) {
            try {
                if (!extractOne(level, terminalPos, unit, cellInputs, taken)) {
                    refundAll(level, terminalPos, taken);
                    return false;
                }
            } catch (RuntimeException ignored) {
                refundAll(level, terminalPos, taken);
                return false;
            }
        }

        // handler 回调可能改变机器格/状态；写入任何机器前必须再次整链一致。
        if (!revalidate(level, terminalPos, grid, plan)) {
            refundAll(level, terminalPos, taken);
            return false;
        }
        for (int i = 0; i < plan.cells.size(); i++) {
            if (!plan.cells.get(i).input().isEmpty() && cellInputs[i].isEmpty()) {
                refundAll(level, terminalPos, taken);
                return false;
            }
        }

        UUID jobId = UUID.randomUUID();
        List<BlockPos> members = new ArrayList<>(plan.cells.size());
        for (Cell cell : plan.cells) {
            members.add(cell.crafter().getBlockPos());
        }

        List<ExtremeMechanicalCrafterBlockEntity> started = new ArrayList<>();
        for (int i = 0; i < plan.cells.size(); i++) {
            Cell cell = plan.cells.get(i);
            boolean root = cell.crafter() == grid.root();
            BlockPos targetPos = targetPos(cell.crafter());
            if (!cell.crafter().startTerminalCraft(jobId, grid.root().getBlockPos(),
                    plan.holder.id(), root, targetPos, cellInputs[i], members,
                    root ? plan.expected : null)) {
                for (ExtremeMechanicalCrafterBlockEntity be : started) {
                    be.revertTerminalCraftStart();
                }
                refundAll(level, terminalPos, taken);
                return false;
            }
            started.add(cell.crafter());
        }
        return true;
    }

    private static boolean revalidate(Level level, BlockPos terminalPos, CraftingGrid expected, Plan plan) {
        if (!(level.getBlockEntity(terminalPos) instanceof ExtremeCraftingTerminalBlockEntity terminal)
                || terminal.hasPendingRefunds()) {
            return false;
        }
        CraftingGrid current = CraftingTerminalNetwork.findCraftingGrid(level, terminalPos);
        if (current == null || current.root() != expected.root()) {
            return false;
        }
        if (current.crafters().size() != expected.crafters().size()
                || !current.coords().equals(expected.coords())) {
            return false;
        }
        Set<ExtremeMechanicalCrafterBlockEntity> expectedCrafters =
                Collections.newSetFromMap(new IdentityHashMap<>());
        expectedCrafters.addAll(expected.crafters());
        for (ExtremeMechanicalCrafterBlockEntity crafter : current.crafters()) {
            if (!expectedCrafters.contains(crafter)) {
                return false;
            }
        }
        for (Cell cell : plan.cells) {
            ExtremeMechanicalCrafterBlockEntity crafter = cell.crafter();
            if (crafter.getLevel() != level
                    || crafter.getPhase() != ExtremeMechanicalCrafterBlockEntity.Phase.IDLE
                    || crafter.hasTerminalJob()
                    || crafter.hasTerminalPendingDrops()
                    || !crafter.getInventory().getItem(0).isEmpty()
                    || !crafter.getGroupedItems().grid.isEmpty()
                    || crafter.getSpeed() == 0) {
                return false;
            }
            if (!cell.input().isEmpty() && crafter.isCovered()) {
                return false; // 真实盖板绝不自动摘除
            }
        }
        return true;
    }

    /** 相邻是否有未设过滤的创造板条箱（Create 语义：未过滤=供给任意物品）。 */
    private static boolean hasUnfilteredCreativeCrate(Level level, BlockPos terminalPos) {
        for (Direction direction : Direction.values()) {
            BlockPos pos = terminalPos.relative(direction);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof CreativeCrateBlockEntity crate) {
                FilteringBehaviour filtering = crate.getBehaviour(FilteringBehaviour.TYPE);
                if (filtering != null && filtering.getFilter().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean extractOne(Level level, BlockPos terminalPos, ExtractionUnit unit,
                                      ItemStack[] cellInputs, List<Taken> taken) {
        IItemHandler handler = unit.handler();
        if (handler == null) {
            // 创造板条箱合成来源：不抽实物、无需退款，直接按模板供 1 个。
            ItemStack used = unit.template().copyWithCount(1);
            cellInputs[unit.cellIndex()] = used;
            taken.add(new Taken(null, -1, used));
            return true;
        }
        int slot = unit.slot();
        if (slot < 0 || slot >= handler.getSlots()) {
            return false;
        }
        ItemStack current = handler.getStackInSlot(slot);
        if (current.isEmpty()
                || !ItemStack.isSameItemSameComponents(current, unit.template())
                || !unit.ingredient().test(current)) {
            return false;
        }
        ItemStack extracted = handler.extractItem(slot, 1, false);
        if (extracted.isEmpty()
                || !ItemStack.isSameItemSameComponents(extracted, unit.template())
                || !unit.ingredient().test(extracted)) {
            if (!extracted.isEmpty()) {
                refund(level, terminalPos, handler, slot, extracted);
            }
            return false;
        }
        ItemStack used = extracted.copyWithCount(1);
        if (extracted.getCount() > 1) {
            // 第三方 handler 违约返回多于此笔计划数量的栈：只取 1，余料立即退回/掉落，绝不吞掉。
            refund(level, terminalPos, handler, slot, extracted.copyWithCount(extracted.getCount() - 1));
        }
        cellInputs[unit.cellIndex()] = used;
        taken.add(new Taken(handler, slot, used));
        return true;
    }

    private static void refundAll(Level level, BlockPos terminalPos, List<Taken> taken) {
        for (int i = taken.size() - 1; i >= 0; i--) {
            Taken entry = taken.get(i);
            if (entry.handler() == null) {
                continue; // 创造板条箱合成来源没有实物可退
            }
            refund(level, terminalPos, entry.handler(), entry.slot(), entry.stack());
        }
    }

    private static void refund(Level level, BlockPos terminalPos, IItemHandler handler,
                               int preferredSlot, ItemStack stack) {
        ItemStack remainder = stack.copy();
        try {
            int slots = handler.getSlots();
            if (preferredSlot >= 0 && preferredSlot < slots) {
                remainder = handler.insertItem(preferredSlot, remainder, false);
            }
            for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
                if (slot == preferredSlot) {
                    continue;
                }
                remainder = handler.insertItem(slot, remainder, false);
            }
        } catch (RuntimeException ignored) {
            // 只出不进/接口异常时走可见掉落，绝不丢失余料。
        }
        if (!remainder.isEmpty()) {
            refundToWorldOrBuffer(level, terminalPos, remainder);
        }
    }

    /**
     * 显式 ItemEntity 是最后手段；若其它模组取消 EntityJoin 导致正式 spawn=false，
     * 转存同一终端 BE 的 NBT 缓冲，等 lazyTick 低频重试，避免原地吞材料。
     */
    private static void refundToWorldOrBuffer(Level level, BlockPos terminalPos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(terminalPos);
        ItemEntity entity = new ItemEntity(level, center.x, center.y + 0.25, center.z, stack.copy());
        entity.setDefaultPickUpDelay();
        // 只有真正加入世界成功才算交付；失败时保留原栈走持久化兜底。
        if (level.addFreshEntity(entity)) {
            return;
        }
        if (level.getBlockEntity(terminalPos) instanceof ExtremeCraftingTerminalBlockEntity terminal) {
            terminal.stageRefund(stack);
            return;
        }
        AvaritiaSingularityStressMod.LOGGER.error(
                "[B29] 终端 {} 已不存在，退款 {} x{} 无法暂存（拆除前请先取回）",
                terminalPos, stack.getItem(), stack.getCount());
    }

    @Nullable
    private static BlockPos targetPos(ExtremeMechanicalCrafterBlockEntity crafter) {
        ExtremeMechanicalCrafterBlockEntity target =
                ExtremeRecipeGridHandler.getTargetingCrafter(crafter);
        return target == null ? null : target.getBlockPos();
    }

    // ------------------------------------------------------------------
    // 容量匹配（Dinic，最多 81 个需求，避免指数回溯）
    // ------------------------------------------------------------------

    private static final class Dinic {
        static final class Edge {
            final int to;
            final int rev;
            int cap;
            final int original;

            Edge(int to, int rev, int cap) {
                this.to = to;
                this.rev = rev;
                this.cap = cap;
                this.original = cap;
            }
        }

        final List<List<Edge>> graph = new ArrayList<>();
        private final int[] level;
        private final int[] next;

        Dinic(int size) {
            for (int i = 0; i < size; i++) {
                graph.add(new ArrayList<>());
            }
            level = new int[size];
            next = new int[size];
        }

        void addEdge(int from, int to, int capacity) {
            Edge forward = new Edge(to, graph.get(to).size(), capacity);
            Edge backward = new Edge(from, graph.get(from).size(), 0);
            graph.get(from).add(forward);
            graph.get(to).add(backward);
        }

        int maxFlow(int source, int sink) {
            int flow = 0;
            while (bfs(source, sink)) {
                Arrays.fill(next, 0);
                int pushed;
                while ((pushed = dfs(source, sink, Integer.MAX_VALUE)) > 0) {
                    flow += pushed;
                }
            }
            return flow;
        }

        private boolean bfs(int source, int sink) {
            Arrays.fill(level, -1);
            level[source] = 0;
            int[] queue = new int[level.length];
            int head = 0;
            int tail = 0;
            queue[tail++] = source;
            while (head < tail) {
                int v = queue[head++];
                for (Edge edge : graph.get(v)) {
                    if (edge.cap > 0 && level[edge.to] < 0) {
                        level[edge.to] = level[v] + 1;
                        queue[tail++] = edge.to;
                    }
                }
            }
            return level[sink] >= 0;
        }

        private int dfs(int v, int sink, int limit) {
            if (v == sink) {
                return limit;
            }
            for (; next[v] < graph.get(v).size(); next[v]++) {
                Edge edge = graph.get(v).get(next[v]);
                if (edge.cap <= 0 || level[edge.to] != level[v] + 1) {
                    continue;
                }
                int pushed = dfs(edge.to, sink, Math.min(limit, edge.cap));
                if (pushed > 0) {
                    edge.cap -= pushed;
                    graph.get(edge.to).get(edge.rev).cap += pushed;
                    return pushed;
                }
            }
            return 0;
        }
    }
}
