package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.CraftResult;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.RecipeLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jetbrains.annotations.Nullable;

/**
 * B29 终端菜单：无槽位，但用一条 {@link SimpleContainerData} 把「本次合成尝试的结果」同步给界面。
 * <p>
 * 这个菜单现在承担三件事：
 * <ol>
 *   <li><b>会话选择</b>：{@link #selectedRecipeId} 是本次打开菜单的选择，不从客户端 BE 临时读。
 *       打开时以 BE 的 {@code getSelectedRecipeId()} 为初值（BE 只是"上次选择"的记忆），
 *       之后选择只改本会话；这样另一个玩家在同一终端上选别的配方不会把此会话的 CRAFT 目标改掉。</li>
 *   <li><b>服务端绑定</b>：{@link #serverTerminal} 是打开菜单时解析到的同一个 BE 实例。
 *       网络包会校验菜单 id、坐标、BE 实例三者同时匹配，客户端不能凭坐标去操作没打开的菜单。</li>
 *   <li><b>结果反馈</b>：{@link #setCraftResult} 写 ContainerData，客户端在同一帧/下一 tick
 *       就能在屏内看到本地化结果（原版 ContainerData 通道，不新增网络包）。</li>
 * </ol>
 * 配方准入统一走 {@link #supportedLayout(Recipe, HolderLookup.Provider)}：
 * <ul>
 *   <li>只接受终端真实支持的配方类型（无尽贪婪工作台配方 + 本模组终焉动能配方）；</li>
 *   <li>必须有非空产物，且 {@link ExtremeCraftingTerminalBlockEntity#layoutOf(Recipe)} 能给出布局；</li>
 *   <li>布局不超过界面 9×9 的预览范围；</li>
 *   <li>每个非空格都必须能枚举出至少一个候选物品 —— 这样屏幕上的"真实材料需求"不会出现
 *       只显示了一个候选、实际却无法投料的配方（NBT/components 候选由 {@code Ingredient.getItems()} 给出）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public class ExtremeCraftingTerminalMenu extends AbstractContainerMenu {

    /**
     * 数据包重载会在同一个 id 上换掉 RecipeHolder。NeoForge 的 OnDatapackSyncEvent
     * 在 PlayerList.reloadResources() 里、发送新的 ClientboundUpdateRecipesPacket 之前触发
     * （Event.getPlayer() == null 即 reload）。此时先关掉所有打开的终端菜单：
     * 客户端不会再用旧列表发 SELECT，服务端的 containerMenu 也不再是终端菜单。
     * 这比"菜单打开时记 recipe generation"更小，且不需要新协议。
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers()
                .filter(player -> player.containerMenu instanceof ExtremeCraftingTerminalMenu)
                .forEach(ServerPlayer::closeContainer);
    }

    /**
     * 原版容器菜单的有效距离口径：{@code AbstractContainerMenu.stillValid(ContainerLevelAccess, Player, Block)}
     * 用 {@code player.canInteractWithBlock(pos, 4.0)}。该方法的参数是"额外容差"，
     * 实际还要加玩家 {@code blockInteractionRange()}（默认 4.5），所以中心距离约 8.5 格。
     * 不再像旧版那样传 20（那会实际放到约 25 格）。
     */
    public static final double CONTAINER_INTERACTION_RANGE = 4.0D;

    /** 界面预览格数：9×9。超过这个尺寸的配方当前阶段不接入自动投料界面。 */
    public static final int MAX_PREVIEW = 9;

    private static final int DATA_RESULT = 0;

    private final BlockPos targetPos;

    /** 服务端打开菜单时的 BE 实例；客户端为 null，按坐标查客户端 BE。 */
    @Nullable
    private final ExtremeCraftingTerminalBlockEntity serverTerminal;

    /** 本菜单会话的选择（服务端由校验后的 SELECT 写入，客户端由打开数据初始化）。 */
    @Nullable
    private ResourceLocation selectedRecipeId;

    /**
     * 服务端 SELECT 时保存的 RecipeHolder 实例。
     * <p>
     * 数据包重载可能用同一个 id 换掉配方内容；CRAFT 必须按这个实例身份复核，
     * 重载后要求玩家重新 SELECT，不能拿旧选择直接合成"换了内容"的同 id 配方。
     */
    @Nullable
    private RecipeHolder<?> selectedRecipeHolder;

    /** 0 = 无结果；低 8 位是结果编码，高 8 位是响应序号（都按无符号处理）。 */
    private final SimpleContainerData resultData = new SimpleContainerData(1);

    /** 服务端：每个作业响应递增；与结果编码一起压进一个 short 宽的 ContainerData。 */
    private int resultSeq;

    /**
     * 客户端构造器：{@code IMenuTypeExtension.create(...)} 调用。
     * <p>
     * 包体由 {@code ExtremeCraftingTerminalBlock} 的 {@code openMenu(..., extraDataWriter)} 写入：
     * 坐标 + 是否有初始选择 + 初始选择。这样客户端首帧就能高亮"上次选择"，
     * 不依赖打开屏幕那一刻客户端 BE 是否已经收到方块实体同步。
     */
    public ExtremeCraftingTerminalMenu(MenuType<?> type, int id, Inventory inventory, FriendlyByteBuf buf) {
        this(type, id, inventory, buf.readBlockPos(), null);
        boolean hasSelection = buf.readBoolean();
        this.selectedRecipeId = hasSelection ? buf.readResourceLocation() : null;
    }

    /**
     * 服务端构造器（{@code BE.createMenu} 调用）。
     * <p>
     * 注意这里仍然只收 {@link BlockPos}，是为了不改动核心代理的 BE.createMenu 调用；
     * BE 实例在构造时从 level 解析并保存，之后网络包校验的就是这个实例。
     */
    public ExtremeCraftingTerminalMenu(MenuType<?> type, int id, Inventory inventory, BlockPos targetPos) {
        this(type, id, inventory, targetPos, resolveTerminal(inventory, targetPos));
        this.selectedRecipeId = parseSelection(
                this.serverTerminal == null ? null : this.serverTerminal.getDefaultRecipeId());
        // 服务端一打开就把"上次选择"解析成当前 RecipeManager 的 holder；
        // 之后若发生重载，byKey 会返回新实例，CRAFT 的身份校验自然失败，需要重新 SELECT。
        this.selectedRecipeHolder = resolveHolder(inventory, this.selectedRecipeId);
    }

    private ExtremeCraftingTerminalMenu(MenuType<?> type, int id, Inventory inventory, BlockPos targetPos,
            @Nullable ExtremeCraftingTerminalBlockEntity serverTerminal) {
        super(type, id);
        this.targetPos = targetPos;
        this.serverTerminal = serverTerminal;
        addDataSlots(resultData);
    }

    @Nullable
    private static ExtremeCraftingTerminalBlockEntity resolveTerminal(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof ExtremeCraftingTerminalBlockEntity terminal
                ? terminal
                : null;
    }

    @Nullable
    private static ResourceLocation parseSelection(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(raw);
    }

    @Nullable
    private static RecipeHolder<?> resolveHolder(Inventory inventory, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return inventory.player.level().getRecipeManager().byKey(id).orElse(null);
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    /** 服务端绑定到的 BE；客户端为 null。网络包用它做"同一实例"校验。 */
    @Nullable
    public ExtremeCraftingTerminalBlockEntity getServerTerminal() {
        return serverTerminal;
    }

    /**
     * 取展示/读取用 BE。
     * <p>
     * 服务端返回打开菜单时那台；客户端按坐标查客户端 BE（应力/网络状态由 BE sendData 同步）。
     */
    @Nullable
    public ExtremeCraftingTerminalBlockEntity holder(@Nullable Level level) {
        if (serverTerminal != null) {
            return serverTerminal;
        }
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(targetPos) instanceof ExtremeCraftingTerminalBlockEntity be ? be : null;
    }

    @Nullable
    public ResourceLocation getSelectedRecipeId() {
        return selectedRecipeId;
    }

    /**
     * 客户端点击列表时只记 id；服务端的 CRAFT 必须再通过 {@link #isSelectedHolder} 的身份校验。
     */
    public void setSelectedRecipeId(@Nullable ResourceLocation selectedRecipeId) {
        this.selectedRecipeId = selectedRecipeId;
        this.selectedRecipeHolder = null;
    }

    /** 服务端 SELECT 通过后记录当前 RecipeManager 中的 holder 实例（id 与实例一起保存）。 */
    public void setSelectedRecipe(@Nullable RecipeHolder<?> holder) {
        this.selectedRecipeHolder = holder;
        this.selectedRecipeId = holder == null ? null : holder.id();
    }

    /** CRAFT 时用来确认配方没有被数据包重载换成同 id 的新实例。 */
    public boolean isSelectedHolder(@Nullable RecipeHolder<?> holder) {
        return holder != null && selectedRecipeHolder == holder;
    }

    /**
     * 写入结果编码，<b>不推进</b>响应序号。
     * <p>
     * SELECT 的清空/失败也走这里：延迟到达的 SELECT 回执不能把屏幕上的 CRAFT pending 解除。
     */
    public void setCraftResult(@Nullable CraftResult result) {
        int code = result == null ? 0 : result.ordinal() + 1;
        resultData.set(DATA_RESULT, ((resultSeq & 0xFF) << 8) | (code & 0xFF));
    }

    /**
     * C2S CRAFT 的响应：推进序号 + 写结果。
     * 屏幕只有看到序号变化（或有限超时）才解除按钮 pending，防双击重复提交。
     */
    public void setCraftResponse(@Nullable CraftResult result) {
        resultSeq = (resultSeq + 1) & 0xFF;
        setCraftResult(result);
    }

    /** 客户端：当前结果的响应序号；变化即代表服务端处理完了一次操作。 */
    public int getResultSeq() {
        return (resultData.get(DATA_RESULT) & 0xFFFF) >>> 8;
    }

    /** 客户端：读本次 CRAFT 的结果；null = 还没有尝试或已被 SELECT 清空。 */
    @Nullable
    public CraftResult getCraftResult() {
        int code = resultData.get(DATA_RESULT) & 0xFF;
        if (code <= 0) {
            return null;
        }
        CraftResult[] values = CraftResult.values();
        return code <= values.length ? values[code - 1] : null;
    }

    /**
     * 终端真实的配方准入。CRAFT/SELECT 与客户端列表共用这一套判断，避免"界面能点、服务端不认"。
     * <p>
     * 返回的 {@link RecipeLayout} 可直接用于预览；返回 null 表示不支持。
     */
    @Nullable
    public static RecipeLayout supportedLayout(Recipe<?> recipe, HolderLookup.Provider registries) {
        // 类型白名单在 layoutOf -> ExtremeRecipeGridHandler.supportsAutoCraft 里，
        // 这里与核心保持同一入口，不再另加一套类型判断。
        if (recipe.getResultItem(registries).isEmpty()) {
            return null;
        }
        RecipeLayout layout = ExtremeCraftingTerminalBlockEntity.layoutOf(recipe);
        if (layout == null || layout.width() <= 0 || layout.height() <= 0
                || layout.width() > MAX_PREVIEW || layout.height() > MAX_PREVIEW) {
            return null;
        }
        // 每个非空格必须有可枚举候选：NBT/components 配方只有能准确列出候选时才进入界面，
        // 否则屏幕会显示一个看起来能投、实际抽不到的材料。
        for (Ingredient ingredient : layout.cells()) {
            if (!ingredient.isEmpty() && ingredient.getItems().length == 0) {
                return null;
            }
        }
        return layout;
    }

    public static boolean isSupportedRecipe(Recipe<?> recipe, HolderLookup.Provider registries) {
        return supportedLayout(recipe, registries) != null;
    }

    /**
     * 带网络上下文的准入（客户端列表 / SELECT / CRAFT 共用）：
     * 基础白名单 + 根品类收窄（炽骨网络仅前两档+B31）+ 数量/摆放/盖板形状过滤。
     * 无网络（0 台）时不展示任何配方——只显示当前网络真能合成的。
     */
    @Nullable
    public static RecipeLayout supportedLayoutFor(Recipe<?> recipe, HolderLookup.Provider registries,
                                                  @Nullable ExtremeCraftingTerminalBlockEntity terminal) {
        RecipeLayout layout = supportedLayout(recipe, registries);
        if (layout == null || terminal == null) {
            return layout;
        }
        if (terminal.getCrafterCount() <= 0) {
            return null;
        }
        if (!ExtremeRecipeGridHandler.supportsAutoCraftFor(recipe, terminal.isNetworkBlaze())) {
            return null;
        }
        return TerminalCraftPlanner.fitsShape(terminal.gridCellsMap(), recipe) ? layout : null;
    }

    public static boolean isSupportedRecipeFor(Recipe<?> recipe, HolderLookup.Provider registries,
                                               @Nullable ExtremeCraftingTerminalBlockEntity terminal) {
        return supportedLayoutFor(recipe, registries, terminal) != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 服务端统一校验：当前打开的必须是这个菜单、包里的菜单 id/坐标必须与菜单一致，
     * 且菜单本身仍满足原版容器存活条件（距离/方块/level.mayInteract/同一 BE 实例）。
     * <p>
     * 网络包只调用这一处，不再自己手写一份距离/方块/mayInteract 条件，
     * 避免两个地方口径漂移（尤其 {@code canInteractWithBlock} 的参数是额外容差）。
     */
    public boolean isOpenFor(Player player, int packetMenuId, BlockPos packetPos) {
        if (player.containerMenu != this) {
            return false;
        }
        if (this.containerId != packetMenuId) {
            return false;
        }
        if (!this.targetPos.equals(packetPos)) {
            return false;
        }
        return stillValid(player);
    }

    /**
     * 存活判定 = 原版容器距离口径 + 方块仍在 + {@code level.mayInteract} + 服务端 BE 实例未被替换。
     * 这样超距/拆方块/换 BE 后菜单会自动关闭，而不是留一个能点但服务端丢弃的界面。
     */
    @Override
    public boolean stillValid(Player player) {
        Level level = player.level();
        if (!level.isLoaded(targetPos)) {
            return false;
        }
        if (!player.canInteractWithBlock(targetPos, CONTAINER_INTERACTION_RANGE)) {
            return false;
        }
        if (!(level.getBlockState(targetPos).getBlock() instanceof ExtremeCraftingTerminalBlock)) {
            return false;
        }
        if (!level.mayInteract(player, targetPos)) {
            return false;
        }
        if (!level.isClientSide) {
            return serverTerminal != null && !serverTerminal.isRemoved()
                    && level.getBlockEntity(targetPos) == serverTerminal;
        }
        return true;
    }
}
