package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter.BlazeCubeCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity.Phase;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * B29 终焉动能合成终端实体。
 * <p>
 * 经连接器网络统一管理合成器：选择配方后，从<b>相邻容器</b>（仓库）调集材料，
 * 按配方格→合成器网格坐标（{@link CraftingTerminalNetwork}）自动投入对应合成器单格槽，
 * 空格自动加盖板，随后由合成器网络自行合成。GUI 显示材料需求与应力需求。
 * <p>
 * 填料约束（与合成器一致）：每格 1 个、整链必须 IDLE、盖板格拒收。
 */
public class ExtremeCraftingTerminalBlockEntity extends SmartBlockEntity implements MenuProvider {

    /** B29 起工返回契约（UI 并行依赖的稳定枚举）。 */
    public enum CraftResult {
        STARTED,
        INVALID_RECIPE,
        INVALID_NETWORK,
        BUSY,
        NO_POWER,
        GRID_MISMATCH,
        MISSING_MATERIALS,
        TRANSFER_FAILED
    }

    private String selectedRecipeId = "";
    private int crafterCount;
    // 相邻 BFS 发现的连接器数：供 GUI 区分"无连接器网络"与"有连接器但链不完整"。
    private int connectorCount;
    private float stressNeed;
    private boolean networkReady;
    // 网络根品类（炽骨/终焉）：客户端列表按品类收窄准入。
    private boolean networkBlaze;
    // 真实网格形状（x,y,covered 三元组）：客户端列表按数量/摆放屏蔽做不到的配方。
    private int[] gridCells = new int[0];
    // 书签：钉住的配方作为默认选择，红石信号自动起工（自动化流水线）。
    private String pinnedRecipeId = "";
    private boolean lastPowered;
    // 退款兜底：原槽/同 handler 拒收且 ItemEntity 正式 spawn 返回 false 时暂存。
    // 暂存非空不接受新单，因此单次任务最多累计 81 个栈，不会无界增长。
    private final List<ItemStack> refundBuffer = new ArrayList<>();

    public ExtremeCraftingTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    public static ExtremeCraftingTerminalBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeCraftingTerminalBlockEntity(ModBlockEntities.EXTREME_CRAFTING_TERMINAL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 终端无附加行为：网络发现/填料按需调用，仓库抽取直接读相邻容器能力
    }

    // ------------------------------------------------------------------
    // 同步
    // ------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("SelectedRecipe", selectedRecipeId);
        tag.putInt("CrafterCount", crafterCount);
        tag.putInt("ConnectorCount", connectorCount);
        tag.putFloat("StressNeed", stressNeed);
        tag.putBoolean("NetworkReady", networkReady);
        tag.putBoolean("NetworkBlaze", networkBlaze);
        tag.putIntArray("GridCells", gridCells);
        tag.putString("PinnedRecipe", pinnedRecipeId);
        if (!clientPacket && !refundBuffer.isEmpty()) {
            ListTag refunds = new ListTag();
            for (ItemStack stack : refundBuffer) {
                if (!stack.isEmpty()) {
                    refunds.add(stack.saveOptional(registries));
                }
            }
            tag.put("RefundBuffer", refunds);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        selectedRecipeId = tag.getString("SelectedRecipe");
        crafterCount = tag.getInt("CrafterCount");
        connectorCount = tag.getInt("ConnectorCount");
        stressNeed = tag.getFloat("StressNeed");
        networkReady = tag.getBoolean("NetworkReady");
        networkBlaze = tag.getBoolean("NetworkBlaze");
        gridCells = tag.getIntArray("GridCells");
        pinnedRecipeId = tag.getString("PinnedRecipe");
        refundBuffer.clear();
        if (tag.contains("RefundBuffer")) {
            ListTag refunds = tag.getList("RefundBuffer", Tag.TAG_COMPOUND);
            for (int i = 0; i < refunds.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, refunds.getCompound(i));
                if (!stack.isEmpty()) {
                    refundBuffer.add(stack);
                }
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) {
            return;
        }
        // 低频重试暂存退款；只有 ItemEntity 正式 spawn 成功才释放原副本。
        tryDrainRefundBuffer();
        CraftingTerminalNetwork.CraftingGrid grid =
                CraftingTerminalNetwork.findCraftingGrid(level, worldPosition);
        // 无效拓扑直接按 0 台同步，避免再跑一遍同样会失败的 findCrafters。
        List<ExtremeMechanicalCrafterBlockEntity> crafters =
                grid != null ? grid.crafters() : List.of();
        int count = crafters.size();
        int connectors = CraftingTerminalNetwork.findConnectors(level, worldPosition).size();
        boolean blaze = grid != null
                && grid.root().getBlockState().getBlock() instanceof BlazeCubeCrafterBlock;
        int[] cells = grid == null ? new int[0] : TerminalCraftPlanner.cellsOf(grid).entrySet().stream()
                .flatMapToInt(e -> java.util.stream.IntStream.of(
                        e.getKey().getKey(), e.getKey().getValue(), e.getValue() ? 1 : 0))
                .toArray();
        float stress = 0;
        for (ExtremeMechanicalCrafterBlockEntity be : crafters) {
            stress += be.calculateStressApplied() * Math.abs(be.getSpeed());
        }
        // networkReady = 拓扑完整、整链 IDLE、无旧库存/聚合、无 active 任务、每台有有效速度。
        // UI 的“自动合成”按钮以此为准；具体失败原因由 tryStartCraft 的 CraftResult 提供。
        boolean ready = grid != null && refundBuffer.isEmpty();
        for (ExtremeMechanicalCrafterBlockEntity be : crafters) {
            if (be.getPhase() != Phase.IDLE
                    || be.hasTerminalJob()
                    || be.hasTerminalPendingDrops()
                    || !be.getInventory().getItem(0).isEmpty()
                    || !be.getGroupedItems().grid.isEmpty()
                    || be.getSpeed() == 0) {
                ready = false;
                break;
            }
        }
        if (count != crafterCount || connectors != connectorCount
                || stress != stressNeed || ready != networkReady
                || blaze != networkBlaze || !Arrays.equals(cells, gridCells)) {
            crafterCount = count;
            connectorCount = connectors;
            stressNeed = stress;
            networkReady = ready;
            networkBlaze = blaze;
            gridCells = cells;
            sendData();
        }

        // 红石自动化：持续供电期间低频重试钉住配方（BUSY 时跳过，空闲即起工）。
        if (lastPowered && !pinnedRecipeId.isEmpty()) {
            tryStartCraft(ResourceLocation.tryParse(pinnedRecipeId));
        }
    }

    /** 红石上升沿立即起工钉住配方（短脉冲也不丢）；lazyTick 负责持续供电重试。 */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !lastPowered && !pinnedRecipeId.isEmpty()) {
            tryStartCraft(ResourceLocation.tryParse(pinnedRecipeId));
        }
        lastPowered = powered;
    }

    /** 退款暂存是否非空；非空时拒绝新单，避免无界累积。 */
    public boolean hasPendingRefunds() {
        return !refundBuffer.isEmpty();
    }

    /**
     * 退款最终兜底：原 handler 拒收且墙外 ItemEntity 被其它模组否决时，
     * 先持久化在终端自身 NBT，等 lazyTick 低频重试，实物优先守恒。
     */
    public void stageRefund(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        refundBuffer.add(stack.copy());
        setChanged();
        AvaritiaSingularityStressMod.LOGGER.warn(
                "[B29] 终端 {} 退款掉落被拒绝，已暂存 {} x{} 等待重试",
                worldPosition, stack.getItem(), stack.getCount());
    }

    private void tryDrainRefundBuffer() {
        if (refundBuffer.isEmpty()) {
            return;
        }
        ItemStack stack = refundBuffer.get(0);
        if (stack.isEmpty()) {
            refundBuffer.remove(0);
            setChanged();
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        ItemEntity entity = new ItemEntity(level, center.x, center.y + 0.25, center.z, stack.copy());
        entity.setDefaultPickUpDelay();
        // 只有正式加入世界成功才移除原副本；失败时保留暂存，绝不发两份。
        if (level.addFreshEntity(entity)) {
            refundBuffer.remove(0);
            setChanged();
        }
    }

    // ------------------------------------------------------------------
    // 菜单
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.avaritia_singularity_stress.extreme_crafting_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ExtremeCraftingTerminalMenu(
                committee.nova.avaritia_singularity_stress.init.registry.ModMenus.EXTREME_CRAFTING_TERMINAL.get(),
                id, inv, worldPosition);
    }

    // ------------------------------------------------------------------
    // 访问器（GUI 读）
    // ------------------------------------------------------------------

    public String getSelectedRecipeId() {
        return selectedRecipeId;
    }

    public int getCrafterCount() {
        return crafterCount;
    }

    public int getConnectorCount() {
        return connectorCount;
    }

    public boolean isNetworkBlaze() {
        return networkBlaze;
    }

    /** 同步的网格形状三元组（x,y,covered）。 */
    public int[] getGridCells() {
        return gridCells;
    }

    public Map<Pair<Integer, Integer>, Boolean> gridCellsMap() {
        Map<Pair<Integer, Integer>, Boolean> cells = new LinkedHashMap<>();
        for (int i = 0; i + 2 < gridCells.length; i += 3) {
            cells.put(Pair.of(gridCells[i], gridCells[i + 1]), gridCells[i + 2] != 0);
        }
        return cells;
    }

    public String getPinnedRecipeId() {
        return pinnedRecipeId;
    }

    /** 书签钉住/取消（null=取消）；准入校验在网络包侧完成。 */
    public void pinRecipe(ResourceLocation id) {
        pinnedRecipeId = id == null ? "" : id.toString();
        setChanged();
        sendData();
    }

    /** 打开菜单的默认选择：钉住优先，其次上次选择。 */
    public String getDefaultRecipeId() {
        return pinnedRecipeId.isEmpty() ? selectedRecipeId : pinnedRecipeId;
    }


    public float getStressNeed() {
        return stressNeed;
    }

    public boolean isNetworkReady() {
        return networkReady;
    }

    public void selectRecipe(ResourceLocation id) {
        selectedRecipeId = id == null ? "" : id.toString();
        setChanged();
        sendData();
    }

    // ------------------------------------------------------------------
    // 自动合成
    // ------------------------------------------------------------------

    /**
     * 开始自动合成（保持旧 boolean 契约，内部委托 tryStartCraft）。
     * 只负责 B29 闭环：整链验证、无副作用模拟、事务抽取、独立入口起工。
     */
    public boolean startCraft(ResourceLocation recipeId) {
        return tryStartCraft(recipeId) == CraftResult.STARTED;
    }

    /** B29 起工契约：所有失败分支都在任何副作用之前返回。 */
    public CraftResult tryStartCraft(ResourceLocation recipeId) {
        if (level == null || level.isClientSide) {
            return CraftResult.INVALID_NETWORK;
        }
        if (!refundBuffer.isEmpty()) {
            // 暂存退款未清空前不接受新单：既避免无界累积，也避免新旧材料混账。
            return CraftResult.TRANSFER_FAILED;
        }
        if (recipeId == null) {
            return CraftResult.INVALID_RECIPE;
        }
        CraftingTerminalNetwork.CraftingGrid grid =
                CraftingTerminalNetwork.findCraftingGrid(level, worldPosition);
        if (grid == null) {
            return CraftResult.INVALID_NETWORK;
        }

        for (ExtremeMechanicalCrafterBlockEntity be : grid.crafters()) {
            if (be.getPhase() != Phase.IDLE || be.hasTerminalJob()
                    || be.hasTerminalPendingDrops()
                    || !be.getInventory().getItem(0).isEmpty()
                    || !be.getGroupedItems().grid.isEmpty()) {
                return CraftResult.BUSY;
            }
        }
        for (ExtremeMechanicalCrafterBlockEntity be : grid.crafters()) {
            // getSpeed() 在 overstressed/暂停时为 0；整链任何一台没速度都不起工。
            if (be.getSpeed() == 0) {
                return CraftResult.NO_POWER;
            }
        }

        RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
        boolean blaze = grid.root().getBlockState().getBlock() instanceof BlazeCubeCrafterBlock;
        if (holder == null || !ExtremeRecipeGridHandler.supportsAutoCraftFor(holder.value(), blaze)) {
            return CraftResult.INVALID_RECIPE;
        }

        TerminalCraftPlanner.Plan plan = TerminalCraftPlanner.plan(level, worldPosition, grid, holder);
        switch (plan.status) {
            case RECIPE_UNSUPPORTED:
                return CraftResult.INVALID_RECIPE;
            case GRID_MISMATCH:
                return CraftResult.GRID_MISMATCH;
            case MISSING_MATERIALS:
                return CraftResult.MISSING_MATERIALS;
            case OK:
                break;
        }

        if (!TerminalCraftPlanner.commit(level, worldPosition, grid, plan)) {
            return CraftResult.TRANSFER_FAILED;
        }
        selectRecipe(recipeId);
        return CraftResult.STARTED;
    }

    // ------------------------------------------------------------------
    // 配方布局
    // ------------------------------------------------------------------

    /** 配方网格布局（row-major cells，空格为 Ingredient.EMPTY）。 */
    public record RecipeLayout(int width, int height, List<Ingredient> cells, ItemStack result) {
    }

    /**
     * 提取配方布局（UI 签名保持不变）：有序按图案，无序仍给出虚拟方阵预览。
     * 不可稳定推导的特殊配方返回 null；尺寸按网格真实支持上限 9×9/81。
     */
    public static RecipeLayout layoutOf(Recipe<?> recipe) {
        if (!ExtremeRecipeGridHandler.supportsAutoCraft(recipe)) {
            return null;
        }
        if (recipe instanceof ShapedTableCraftingRecipe shaped) {
            return fromPattern(shaped.pattern, shaped.getResultItem(null));
        }
        if (recipe instanceof ExtremeKineticCraftingRecipe kinetic) {
            return fromPattern(kinetic.pattern, kinetic.getResultItem(null));
        }
        if (recipe instanceof committee.nova.avaritia_singularity_stress.content.recipes.BlazeCubeCraftingRecipe blazeCube) {
            return fromPattern(blazeCube.pattern, blazeCube.getResultItem(null));
        }
        if (recipe instanceof ShapelessTableCraftingRecipe shapeless) {
            List<Ingredient> ing = shapeless.getIngredients();
            int n = ing.size();
            if (n <= 0 || n > 81) {
                return null;
            }
            int w = (int) Math.ceil(Math.sqrt(n));
            int h = (int) Math.ceil(n / (double) w);
            if (w > 9 || h > 9 || w * h > 81) {
                return null;
            }
            List<Ingredient> cells = new ArrayList<>(ing);
            while (cells.size() < w * h) {
                cells.add(Ingredient.EMPTY);
            }
            return new RecipeLayout(w, h, cells, shapeless.getResultItem(null));
        }
        return null;
    }

    private static RecipeLayout fromPattern(ShapedRecipePattern pattern, ItemStack result) {
        int w = pattern.width();
        int h = pattern.height();
        if (w <= 0 || h <= 0 || w > 9 || h > 9 || w * h > 81) {
            return null;
        }
        List<Ingredient> cells = new ArrayList<>(pattern.ingredients());
        while (cells.size() < w * h) {
            cells.add(Ingredient.EMPTY);
        }
        return new RecipeLayout(w, h, cells, result);
    }
}
