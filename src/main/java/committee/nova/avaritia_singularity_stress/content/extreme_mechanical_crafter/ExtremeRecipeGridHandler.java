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
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Predicates;
import committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter.BlazeCubeCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_smithing_crafter.ExtremeSmithingCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.recipes.BlazeCubeCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingInput;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingInput;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticSmithingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.common.crafting.input.ExtremeSmithingRecipeInput;
import committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.NoConsumeCatalystShapedRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 终焉合成器的配方链与网格处理。
 * <p>
 * 算法完整复制原版 {@code RecipeGridHandler}（链路 BFS、目标/前置判定、网格聚合、
 * 坐标变换），配方权限由 {@link #tryToApplyRecipe} 区分：
 * 终焉系列支持专属动能、B31 炽骨子类型与 Avaritia 配方，B26 炽骨系列支持
 * B31 炽骨专属类型与 Avaritia 前两档工作台。
 * 两个系列独立组网，均不匹配普通工作台与 {@code create:mechanical_crafting}。
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
        if (!ExtremeCrafterHelper.canConnect(state, targetState)) {
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
            if (!world.isLoaded(neighbourPos)) {
                continue; // 不因链检查主动加载区块
            }
            BlockState neighbourState = world.getBlockState(neighbourPos);
            if (!ExtremeCrafterHelper.canConnect(blockState, neighbourState)) {
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
     * 匹配优先级(专属优先,无尽贪婪回退):
     * <ol>
     *   <li>链含终焉锻造器 → <b>本模组终焉动能锻造</b>(extreme_kinetic_smithing);</li>
     *   <li>链含终焉锻造器 → 回落无尽贪婪终焉锻造(avaritia:extreme_smithing);</li>
     *   <li><b>本模组终焉动能合成</b>(extreme_kinetic_crafting);</li>
     *   <li><b>B31 炽骨立方子类型</b>(blaze_cube_crafting,终焉动力兼容);</li>
     *   <li>回落无尽贪婪分级配方(crafting_table_recipe)。</li>
     * </ol>
     * 炽骨立方合成器：B31 专属类型优先，回落仅 Avaritia 前两档。
     * 终焉合成器<b>不</b>匹配工作台配方,也<b>不</b>匹配 {@code create:mechanical_crafting}。
     */
    public static ItemStack tryToApplyRecipe(Level level, ExtremeMechanicalCrafterBlockEntity crafter) {
        if (level == null || level.isClientSide) {
            return null;
        }
        GroupedItems items = crafter.getGroupedItems();
        items.calcStats();

        // B26：在所有专属合成/锻造通路之前返回，不能仅过滤最后的工作台回退。
        if (crafter.getBlockState().getBlock() instanceof BlazeCubeCrafterBlock) {
            // B31 炽骨专属子类型优先于 Avaritia 前两档回退。
            ItemStack blazeCube = tryBlazeCubeCrafting(level, items);
            if (blazeCube != null && !blazeCube.isEmpty()) {
                return blazeCube;
            }
            return tryAvaritiaRecipe(level, items, recipe -> recipe.getTier() == 1 || recipe.getTier() == 2);
        }

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

        // ③.5 B31 炽骨立方子类型(终焉动力合成器兼容)
        ItemStack blazeCubeCrafting = tryBlazeCubeCrafting(level, items);
        if (blazeCubeCrafting != null && !blazeCubeCrafting.isEmpty()) {
            return blazeCubeCrafting;
        }

        // ④ 回落无尽贪婪分级配方
        return tryAvaritiaRecipe(level, items, Predicates.alwaysTrue());
    }

    /**
     * B29 终端自动合成支持范围（服务端真源）。
     * <p>
     * 只接受两类可稳定由公开 {@link Recipe#getIngredients()} 推导的配方：
     * 终焉动能合成、Avaritia 分级工作台的有序/无序基础配方。
     * {@code NoConsumeCatalystShapedRecipe} 仅额外覆写 {@code getRemainingItems}，
     * 可沿原生返还路径支持。
     * <p>
     * 明确拒绝当前 Avaritia 中带隐藏匹配条件的特殊子类：
     * {@code FullMatterClusterRecipe}（要求物质团 contents ≥ 4096）、
     * {@code EternalSingularityCraftRecipe} / 默认组 {@code InfinityCatalystCraftRecipe}
     * （普通 Ingredient 不区分同一奇点物品的组件）。这些配方无法从公开 ingredients
     * 稳定推导候选，必须在列表/起工前过滤，不能先投料再发现不匹配。
     */
    public static boolean supportsAutoCraft(Recipe<?> recipe) {
        if (recipe instanceof ExtremeKineticCraftingRecipe || recipe instanceof BlazeCubeCraftingRecipe) {
            return true;
        }
        Class<?> type = recipe.getClass();
        return type == ShapedTableCraftingRecipe.class
                || type == NoConsumeCatalystShapedRecipe.class
                || type == ShapelessTableCraftingRecipe.class;
    }

    /**
     * 按网络品类收窄准入（B31 后续：终端支持炽骨网络）。
     * 炽骨网络只放行 B31 专属类型与 Avaritia 前两档；终焉网络放行全部基础白名单。
     * 合法单链必为单一品类（canConnect 跨系列断链），故按根品类一刀切即可。
     */
    public static boolean supportsAutoCraftFor(Recipe<?> recipe, boolean blazeNetwork) {
        if (!supportsAutoCraft(recipe)) {
            return false;
        }
        if (!blazeNetwork) {
            return true;
        }
        if (recipe instanceof BlazeCubeCraftingRecipe) {
            return true;
        }
        return recipe instanceof ITierCraftingRecipe tier
                && (tier.getTier() == 1 || tier.getTier() == 2);
    }

    /**
     * B29 根完成聚合时按终端任务记录的 recipeId 精确匹配，绝不回退其它配方。
     * 返回结果与 {@code getRemainingItems} 返还物；不匹配返回 null。
     */
    @Nullable
    public static TerminalCraftOutcome tryToApplyTerminalRecipe(
            Level level, GroupedItems items, @Nullable ResourceLocation recipeId) {
        if (level == null || level.isClientSide || recipeId == null) {
            return null;
        }
        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !supportsAutoCraft(holder.value())) {
            return null;
        }
        items.calcStats();
        Recipe<?> recipe = holder.value();

        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            TierInput input = createTierInput(items, shaped.getTier());
            return matchAndAssembleTier(level, shaped, input);
        }
        if (recipe instanceof ShapelessTableCraftingRecipe shapeless) {
            TierInput input = createTierInput(items, shapeless.getTier());
            return matchAndAssembleTier(level, shapeless, input);
        }
        if (recipe instanceof ExtremeKineticCraftingRecipe kinetic) {
            return matchKineticLike(level, items, kinetic);
        }
        if (recipe instanceof BlazeCubeCraftingRecipe blazeCube) {
            return matchKineticLike(level, items, blazeCube);
        }
        return null;
    }

    /** 动能合成与 B31 炽骨子类型共用紧凑网格匹配/装配/返还路径。 */
    @Nullable
    private static TerminalCraftOutcome matchKineticLike(Level level, GroupedItems items, ShapedRecipe recipe) {
        if (items.onlyEmptyItems()) {
            return null;
        }
        CraftingInput input = ExtremeKineticCraftingInput.of(items);
        if (!recipe.matches(input, level)) {
            return null;
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result == null || result.isEmpty()) {
            return null;
        }
        return new TerminalCraftOutcome(result, recipe.getRemainingItems(input));
    }

    private static TerminalCraftOutcome matchAndAssembleTier(
            Level level, ITierCraftingRecipe recipe, TierInput input) {
        if (!recipe.matches(input, level)) {
            return null;
        }
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result == null || result.isEmpty()) {
            return null;
        }
        return new TerminalCraftOutcome(result, recipe.getRemainingItems(input));
    }

    /** B29 精确匹配/根合成共用的 TierInput 构造：与聚合网格行序完全一致。 */
    public static TierInput createTierInput(GroupedItems items, int tier) {
        items.calcStats();
        int width = items.width;
        int height = items.height;
        List<ItemStack> stacks = new ArrayList<>(Math.max(0, width * height));
        if (width > 0 && height > 0) {
            for (int row = 0; row < height; row++) {
                int y = items.maxY - row;
                for (int col = 0; col < width; col++) {
                    ItemStack stack = items.grid.get(Pair.of(items.minX + col, y));
                    stacks.add(stack == null ? ItemStack.EMPTY : stack);
                }
            }
        }
        return TierInput.of(Math.max(0, width), Math.max(0, height), stacks, tier);
    }

    /** 终端精确配方的产物与返还物。 */
    public record TerminalCraftOutcome(ItemStack result, NonNullList<ItemStack> remaining) {
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
     * 匹配 B31 炽骨立方专属配方(blaze_cube_crafting)。
     * 与 tryKineticCrafting 同构:紧凑网格 + 手动遍历专属 RecipeType 分桶。
     * 炽骨立方合成器与终焉动力合成器共用此通路;原版工作台不在该分桶内,天然不可做。
     */
    private static ItemStack tryBlazeCubeCrafting(Level level, GroupedItems items) {
        CraftingInput input = ExtremeKineticCraftingInput.of(items);
        List<RecipeHolder<BlazeCubeCraftingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModExtremeRecipeTypes.BLAZE_CUBE_CRAFTING_TYPE.get());
        for (RecipeHolder<BlazeCubeCraftingRecipe> holder : recipes) {
            BlazeCubeCraftingRecipe recipe = holder.value();
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

    private static ItemStack tryAvaritiaRecipe(Level level, GroupedItems items,
                                                Predicate<ITierCraftingRecipe> allowedRecipe) {
        Map<Pair<Integer, Integer>, ItemStack> grid = items.grid;
        if (grid.isEmpty()) {
            return null;
        }

        int width = items.width;
        int height = items.height;
        if (width <= 0 || height <= 0) {
            return null;
        }

        List<RecipeHolder<ITierCraftingRecipe>> recipes = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get());

        for (RecipeHolder<ITierCraftingRecipe> holder : recipes) {
            ITierCraftingRecipe recipe = holder.value();
            // Avaritia 1.4.1 的 getTier 会为未指定等级的配方按尺寸/材料数推导等级，
            // 所以必须先过滤，再用配方等级构造输入，不能把所有 tier=0 的大配方直接放行。
            if (!allowedRecipe.test(recipe)) {
                continue;
            }
            // 世界网格的 y 越大越高；配方第一行是最上面一行，
            // 因此按 maxY 递减填充，使列表行 0 对应世界最高一行。
            TierInput input = createTierInput(items, recipe.getTier());
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
