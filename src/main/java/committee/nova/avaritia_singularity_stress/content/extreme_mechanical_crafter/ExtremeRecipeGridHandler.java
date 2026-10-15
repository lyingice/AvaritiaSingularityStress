package committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Predicates;
import committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter.ExtremeSmithingCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingInput;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingInput;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 终焉合成器的配方链与网格处理。
 * <p>
 * 算法完整复制原版 {@code RecipeGridHandler}（链路 BFS、目标/前置判定、网格聚合、
 * 坐标变换），唯一的差异在 {@link #tryToApplyRecipe}：
 * <b>终焉合成器只匹配无尽贪婪（Avaritia）分级配方</b>，
 * 不再匹配工作台配方，也不匹配 {@code create:mechanical_crafting}。
 */
public class ExtremeRecipeGridHandler {

    private ExtremeRecipeGridHandler() {
    }

    public static List<ExtremeMechanicalCrafterBlockEntity> getAllCraftersOfChain(
            ExtremeMechanicalCrafterBlockEntity root) {
        return getAllCraftersOfChainIf(root, Predicates.alwaysTrue());
    }

    public static List<ExtremeMechanicalCrafterBlockEntity> getAllCraftersOfChainIf(
            ExtremeMechanicalCrafterBlockEntity root, Predicate<ExtremeMechanicalCrafterBlockEntity> test) {
        return getAllCraftersOfChainIf(root, test, false);
    }

    /**
     * 双向遍历整条链（目标方向 + 前置方向）。
     * <p>
     * 与原版语义一致：成环、任一节点不满足条件、或全部为空，都返回 {@code null}
     * （红石启动时允许链路中部分槽位为空）。
     */
    public static List<ExtremeMechanicalCrafterBlockEntity> getAllCraftersOfChainIf(
            ExtremeMechanicalCrafterBlockEntity root, Predicate<ExtremeMechanicalCrafterBlockEntity> test,
            boolean poweredStart) {
        List<ExtremeMechanicalCrafterBlockEntity> crafters = new ArrayList<>();
        List<Pair<ExtremeMechanicalCrafterBlockEntity, ExtremeMechanicalCrafterBlockEntity>> frontier =
                new ArrayList<>();
        Set<ExtremeMechanicalCrafterBlockEntity> visited = new HashSet<>();
        frontier.add(Pair.of(root, null));

        boolean empty = false;
        boolean allEmpty = true;

        while (!frontier.isEmpty()) {
            Pair<ExtremeMechanicalCrafterBlockEntity, ExtremeMechanicalCrafterBlockEntity> pair =
                    frontier.remove(0);
            ExtremeMechanicalCrafterBlockEntity current = pair.getKey();
            ExtremeMechanicalCrafterBlockEntity last = pair.getValue();

            if (visited.contains(current)) {
                return null;
            }
            if (!test.test(current)) {
                empty = true;
            } else {
                allEmpty = false;
            }

            crafters.add(current);
            visited.add(current);

            ExtremeMechanicalCrafterBlockEntity target = getTargetingCrafter(current);
            if (target != last && target != null) {
                frontier.add(Pair.of(target, current));
            }
            for (ExtremeMechanicalCrafterBlockEntity preceding : getPrecedingCrafters(current)) {
                if (preceding != last) {
                    frontier.add(Pair.of(preceding, current));
                }
            }
        }

        return empty && !poweredStart || allEmpty ? null : crafters;
    }

    public static ExtremeMechanicalCrafterBlockEntity getTargetingCrafter(
            ExtremeMechanicalCrafterBlockEntity crafter) {
        BlockState state = crafter.getBlockState();
        if (!ExtremeCrafterHelper.isCrafter(state)) {
            return null;
        }

        BlockPos targetPos = crafter.getBlockPos()
                .relative(ExtremeMechanicalCrafterBlock.getTargetDirection(state));
        ExtremeMechanicalCrafterBlockEntity targetBE = ExtremeCrafterHelper.getCrafter(crafter.getLevel(), targetPos);
        if (targetBE == null) {
            return null;
        }

        BlockState targetState = targetBE.getBlockState();
        if (!ExtremeCrafterHelper.isCrafter(targetState)) {
            return null;
        }
        if (state.getValue(HORIZONTAL_FACING) != targetState.getValue(HORIZONTAL_FACING)) {
            return null;
        }
        return targetBE;
    }

    /**
     * 找出所有「指向当前合成器」的前置合成器。
     * <p>
     * 这是上下左右构成合成路径的核心规则：邻居必须朝向自己，才算自己的前置。
     */
    public static List<ExtremeMechanicalCrafterBlockEntity> getPrecedingCrafters(
            ExtremeMechanicalCrafterBlockEntity crafter) {
        BlockPos pos = crafter.getBlockPos();
        Level world = crafter.getLevel();
        List<ExtremeMechanicalCrafterBlockEntity> crafters = new ArrayList<>();
        BlockState blockState = crafter.getBlockState();
        if (!ExtremeCrafterHelper.isCrafter(blockState)) {
            return crafters;
        }

        Direction blockFacing = blockState.getValue(HORIZONTAL_FACING);
        Direction blockPointing = ExtremeMechanicalCrafterBlock.getTargetDirection(blockState);
        for (Direction facing : Iterate.directions) {
            if (blockFacing.getAxis() == facing.getAxis()) {
                continue;
            }
            if (blockPointing == facing) {
                continue;
            }

            BlockPos neighbourPos = pos.relative(facing);
            BlockState neighbourState = world.getBlockState(neighbourPos);
            if (!ExtremeCrafterHelper.isCrafter(neighbourState)) {
                continue;
            }
            if (ExtremeMechanicalCrafterBlock.getTargetDirection(neighbourState) != facing.getOpposite()) {
                continue;
            }
            if (blockFacing != neighbourState.getValue(HORIZONTAL_FACING)) {
                continue;
            }
            ExtremeMechanicalCrafterBlockEntity be = ExtremeCrafterHelper.getCrafter(world, neighbourPos);
            if (be == null) {
                continue;
            }

            crafters.add(be);
        }

        return crafters;
    }

    /**
     * 尝试在聚合网格上匹配配方。
     * <p>
     * 两条通路,按优先级:
     * <ol>
     *   <li><b>终焉锻造配方</b> —— 当整条合成链中出现终焉锻造器时启用。
     *       锻造配方需要 5 个输入,正好对应「中心锻造器 + 四向 4 个合成器」的十字布局。</li>
     *   <li><b>无尽贪婪分级配方</b> —— 原有通路,作为回落。</li>
     * </ol>
     * 注意终焉合成器<b>不</b>匹配工作台配方,也<b>不</b>匹配 {@code create:mechanical_crafting}。
     */
    /**
     * 尝试在聚合网格上匹配配方。
     * <p>
     * 匹配优先级(专属优先,无尽贪婪回退):
     * <ol>
     *   <li>链含终焉锻造器 → <b>本模组终焉动能锻造</b>(extreme_kinetic_smithing);</li>
     *   <li>链含终焉锻造器 → 回落无尽贪婪终焉锻造(avaritia:extreme_smithing);</li>
     *   <li><b>本模组终焉动能合成</b>(extreme_kinetic_crafting);</li>
     *   <li>回落无尽贪婪分级配方(crafting_table_recipe)。</li>
     * </ol>
     * 终焉合成器<b>不</b>匹配工作台配方,也<b>不</b>匹配 {@code create:mechanical_crafting}。
     */
    public static ItemStack tryToApplyRecipe(Level level, ExtremeMechanicalCrafterBlockEntity crafter) {
        if (level == null || level.isClientSide) {
            return null;
        }
        GroupedItems items = crafter.getGroupedItems();
        items.calcStats();

        if (chainHasSmithingCrafter(crafter)) {
            // ① 本模组专属终焉动能锻造
            ItemStack kineticSmithing = tryKineticSmithing(level, items);
            if (kineticSmithing != null && !kineticSmithing.isEmpty()) {
                return kineticSmithing;
            }
            // ② 回落无尽贪婪终焉锻造
            ItemStack smithing = tryExtremeSmithingRecipe(level, items);
            if (smithing != null && !smithing.isEmpty()) {
                return smithing;
            }
        }

        // ③ 本模组专属终焉动能合成
        ItemStack kineticCrafting = tryKineticCrafting(level, items);
        if (kineticCrafting != null && !kineticCrafting.isEmpty()) {
            return kineticCrafting;
        }

        // ④ 回落无尽贪婪分级配方
        return tryAvaritiaRecipe(level, items);
    }

    /**
     * 匹配本模组的终焉动能合成配方。
     * 先经 ExtremeKineticCraftingInput 裁剪 + 翻转为紧凑网格,
     * 再手动遍历专属 RecipeType 分桶内的配方。
     * (1.21.1 的 CraftingInput 是 final,自定义输入类型无法经过
     * getRecipeFor 的泛型,手动遍历与分桶查询语义等价。)
     */
    private static ItemStack tryKineticCrafting(Level level, GroupedItems items) {
        CraftingInput input = ExtremeKineticCraftingInput.of(items);
        List<RecipeHolder<ExtremeKineticCraftingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModExtremeRecipeTypes.EXTREME_KINETIC_CRAFTING_TYPE.get());
        for (RecipeHolder<ExtremeKineticCraftingRecipe> holder : recipes) {
            ExtremeKineticCraftingRecipe recipe = holder.value();
            if (!recipe.matches(input, level)) {
                continue;
            }
            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    /**
     * 匹配本模组的终焉动能锻造配方。
     * 沿用 5 格自动搜索骨架(数量≠5 直接否,模板/基座位置自动枚举),
     * 配方源为本模组专属 RecipeType。
     */
    private static ItemStack tryKineticSmithing(Level level, GroupedItems items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : items.grid.values()) {
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        if (stacks.size() != 5) {
            return null;
        }

        List<RecipeHolder<ExtremeKineticSmithingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModExtremeRecipeTypes.EXTREME_KINETIC_SMITHING_TYPE.get());

        for (RecipeHolder<ExtremeKineticSmithingRecipe> holder : recipes) {
            ExtremeKineticSmithingRecipe recipe = holder.value();

            for (int templateIndex = 0; templateIndex < 5; templateIndex++) {
                if (!recipe.isTemplateIngredient(stacks.get(templateIndex))) {
                    continue;
                }
                for (int baseIndex = 0; baseIndex < 5; baseIndex++) {
                    if (baseIndex == templateIndex) {
                        continue;
                    }
                    if (!recipe.isBaseIngredient(stacks.get(baseIndex))) {
                        continue;
                    }

                    List<ItemStack> additions = new ArrayList<>(3);
                    for (int i = 0; i < 5; i++) {
                        if (i != templateIndex && i != baseIndex) {
                            additions.add(stacks.get(i));
                        }
                    }
                    if (additions.size() != 3) {
                        continue;
                    }

                    ExtremeKineticSmithingInput input = new ExtremeKineticSmithingInput(
                            stacks.get(templateIndex),
                            stacks.get(baseIndex),
                            additions.get(0),
                            additions.get(1),
                            additions.get(2));

                    if (!recipe.matches(input, level)) {
                        continue;
                    }
                    ItemStack result = recipe.assemble(input, level.registryAccess());
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    /** 该合成器所在的整条链中是否存在终焉锻造器。 */
    public static boolean chainHasSmithingCrafter(ExtremeMechanicalCrafterBlockEntity root) {
        List<ExtremeMechanicalCrafterBlockEntity> chain = getAllCraftersOfChain(root);
        if (chain == null) {
            return false;
        }
        for (ExtremeMechanicalCrafterBlockEntity be : chain) {
            if (be.getBlockState().getBlock() instanceof ExtremeSmithingCrafterBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匹配终焉锻造配方({@code avaritia:extreme_smithing})。
     * <p>
     * 锻造配方严格要求 5 个输入(模板 / 基座 / 3 个添加物),且
     * {@code matches} 本身不校验位置,因此这里自动搜索「哪个格子是模板、哪个是基座」,
     * 其余 3 格必须全部匹配 {@code additions}。这样玩家无需记忆摆位顺序。
     */
    private static ItemStack tryExtremeSmithingRecipe(Level level, GroupedItems items) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : items.grid.values()) {
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        // 锻造配方是 5 输入;数量不符直接不匹配
        if (stacks.size() != 5) {
            return null;
        }

        List<RecipeHolder<ExtremeSmithingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.EXTREME_SMITHING_RECIPE.get());

        for (RecipeHolder<ExtremeSmithingRecipe> holder : recipes) {
            ExtremeSmithingRecipe recipe = holder.value();

            for (int templateIndex = 0; templateIndex < 5; templateIndex++) {
                if (!recipe.template.test(stacks.get(templateIndex))) {
                    continue;
                }
                for (int baseIndex = 0; baseIndex < 5; baseIndex++) {
                    if (baseIndex == templateIndex) {
                        continue;
                    }
                    if (!recipe.base.test(stacks.get(baseIndex))) {
                        continue;
                    }

                    List<ItemStack> additions = new ArrayList<>(3);
                    for (int i = 0; i < 5; i++) {
                        if (i != templateIndex && i != baseIndex) {
                            additions.add(stacks.get(i));
                        }
                    }
                    if (additions.size() != 3) {
                        continue;
                    }

                    ExtremeSmithingRecipeInput input = new ExtremeSmithingRecipeInput(
                            stacks.get(templateIndex),
                            stacks.get(baseIndex),
                            additions.get(0),
                            additions.get(1),
                            additions.get(2));

                    // 用官方语义复核(会再校验 3 个添加物都匹配 additions)
                    if (!recipe.matches(input, level)) {
                        continue;
                    }
                    ItemStack result = recipe.assemble(input, level.registryAccess());
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    private static ItemStack tryAvaritiaRecipe(Level level, GroupedItems items) {
        Map<Pair<Integer, Integer>, ItemStack> grid = items.grid;
        if (grid.isEmpty()) {
            return null;
        }

        int width = items.width;
        int height = items.height;
        if (width <= 0 || height <= 0) {
            return null;
        }

        // 世界网格的 y 越大越高；配方第一行是最上面一行，
        // 因此按 maxY 递减填充，使列表行 0 对应世界最高一行。
        List<ItemStack> stacks = new ArrayList<>(width * height);
        for (int row = 0; row < height; row++) {
            int y = items.maxY - row;
            for (int col = 0; col < width; col++) {
                ItemStack stack = grid.get(Pair.of(items.minX + col, y));
                stacks.add(stack == null ? ItemStack.EMPTY : stack);
            }
        }

        List<RecipeHolder<ITierCraftingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get());

        for (RecipeHolder<ITierCraftingRecipe> holder : recipes) {
            ITierCraftingRecipe recipe = holder.value();
            // 用配方自身的等级构造输入，使终焉合成器可以处理无尽贪婪的全部等级
            TierInput input = TierInput.of(width, height, stacks, recipe.getTier());
            if (!recipe.matches(input, level)) {
                continue;
            }
            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return null;
    }

    /** 合成网格：key 为 (x, y)，值与 Create 一致（y 越大越高）。 */
    public static class GroupedItems {
        public Map<Pair<Integer, Integer>, ItemStack> grid = new HashMap<>();
        public int minX, minY, maxX, maxY, width, height;
        public boolean statsReady;

        public GroupedItems() {
        }

        public GroupedItems(ItemStack stack) {
            grid.put(Pair.of(0, 0), stack);
        }

        public void mergeOnto(GroupedItems other, Pointing pointing) {
            int xOffset = pointing == Pointing.LEFT ? 1 : pointing == Pointing.RIGHT ? -1 : 0;
            int yOffset = pointing == Pointing.DOWN ? 1 : pointing == Pointing.UP ? -1 : 0;
            grid.forEach(
                    (pair, stack) -> other.grid.put(
                            Pair.of(pair.getKey() + xOffset, pair.getValue() + yOffset), stack));
            other.statsReady = false;
        }

        public void write(CompoundTag nbt, HolderLookup.Provider registries) {
            ListTag gridNBT = new ListTag();
            grid.forEach((pair, stack) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("x", pair.getKey());
                entry.putInt("y", pair.getValue());
                entry.put("item", stack.saveOptional(registries));
                gridNBT.add(entry);
            });
            nbt.put("Grid", gridNBT);
        }

        public static GroupedItems read(CompoundTag nbt, HolderLookup.Provider registries) {
            GroupedItems items = new GroupedItems();
            ListTag gridNBT = nbt.getList("Grid", Tag.TAG_COMPOUND);
            gridNBT.forEach(inbt -> {
                CompoundTag entry = (CompoundTag) inbt;
                int x = entry.getInt("x");
                int y = entry.getInt("y");
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
                items.grid.put(Pair.of(x, y), stack);
            });
            return items;
        }

        public void calcStats() {
            if (statsReady) {
                return;
            }
            statsReady = true;

            minX = 0;
            minY = 0;
            maxX = 0;
            maxY = 0;

            for (Pair<Integer, Integer> pair : grid.keySet()) {
                int x = pair.getKey();
                int y = pair.getValue();
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }

            width = maxX - minX + 1;
            height = maxY - minY + 1;
        }

        public boolean onlyEmptyItems() {
            for (ItemStack stack : grid.values()) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }
    }
}
