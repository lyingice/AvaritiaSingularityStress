package committee.nova.avaritia_singularity_stress.init.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.CraftingTerminalPacket;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.CraftResult;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.RecipeLayout;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalMenu;
import committee.nova.avaritia_singularity_stress.init.registry.ModExtremeRecipeTypes;
import committee.nova.avaritia_singularity_stress.init.registry.ModPackets;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * B29 终端界面（第一阶段）：配方搜索 + 可滚动列表 + 9×9 布局预览 + 材料需求 + 应力/网络指标 + 合成按钮。
 * <p>
 * 布局固定 320×240（原版 GUI 坐标），因此在等效 320×240 窗口里也完整可用：
 * <ul>
 *   <li>左列 104px 配方列表，右侧 200px 结果面板；</li>
 *   <li>9×9 预览按可用空间自动缩放到 14px/格（16px 上限），格间留 1px；
 *       每格悬停有 tooltip，候选物品/材料需求不会压到右侧指标和底部按钮；</li>
 *   <li>材料需求固定在预览右侧的窄列，行高 12px，超出 9 行用滚轮滚动；tooltip 给出完整候选；</li>
 *   <li>应力/网络指标、结果提示、合成按钮各占独立行，9×9 非空格再多也不会遮挡。</li>
 * </ul>
 * 全部玩家可见文案走 lang 键；配方列表只来自客户端已同步的 {@link RecipeManager}，
 * 并每 20 tick 检查一次 holder 是否被数据包重载替换，重载后自动刷新列表。
 * 若当前选择在重载后不存在，界面明确显示 invalid_recipe 提示，CRAFT 按钮也会禁用。
 */
public class ExtremeCraftingTerminalScreen extends AbstractContainerScreen<ExtremeCraftingTerminalMenu> {

    private static final int GUI_W = 320;
    private static final int GUI_H = 240;

    // ---- 左列：搜索 + 配方列表 ----
    private static final int SEARCH_X = 6, SEARCH_Y = 18, SEARCH_W = 104, SEARCH_H = 12;
    private static final int LIST_X = 6, LIST_Y = 34, LIST_W = 104, LIST_H = 198, ROW_H = 18;

    // ---- 右侧面板 ----
    private static final int PANEL_X = 114, PANEL_W = 200;
    private static final int HEADER_X = 116, HEADER_Y = 18, HEADER_W = 196;

    // ---- 9×9 预览：可用空间 134px，9 格 + 8 个 1px 间隔 = 134px ----
    private static final int GRID = ExtremeCraftingTerminalMenu.MAX_PREVIEW;
    private static final int PREVIEW_X = 116, PREVIEW_Y = 40;
    private static final int PREVIEW_SIZE = 134, PREVIEW_GAP = 1;
    private static final int PREVIEW_CELL = (PREVIEW_SIZE - (GRID - 1) * PREVIEW_GAP) / GRID;

    // ---- 材料需求窄列：预览右侧 60px，9 行 × 12px ----
    private static final int MAT_X = 254, MAT_W = 60;
    private static final int MAT_HEADER_Y = 40, MAT_LIST_Y = 52;
    private static final int MAT_ROW_H = 12, MAT_VISIBLE = 9;

    // ---- 底部：指标 / 结果提示 / 按钮 ----
    private static final int METRIC_X = 116, METRIC_Y = 176, METRIC_LINE = 10;
    private static final int STATUS_X = 116, STATUS_Y = 206, STATUS_W = 198, STATUS_H = 10;
    private static final int CRAFT_X = 116, CRAFT_Y = 220, CRAFT_W = 150, CRAFT_H = 18;
    private static final int PIN_X = 268, PIN_Y = 220, PIN_W = 46, PIN_H = 18;

    private static final int SCROLLBAR_W = 2;
    private static final int RECIPE_REFRESH_TICKS = 20;
    private static final int TOOLTIP_CANDIDATES = 8;

    private EditBox searchBox;
    private Button craftButton;
    private Button pinButton;
    private long networkSignature;

    private final List<RecipeEntry> recipes = new ArrayList<>();
    private List<RecipeEntry> filtered = List.of();
    private int scroll;

    private int materialScroll;
    private List<MaterialGroup> materials = List.of();
    @Nullable
    private ResourceLocation materialRecipeId;
    private int materialRecipeIdentity;

    private String lastQuery = "";
    private long recipeSignature;
    private int recipeRefreshTicker;

    /** 客户端上次确认过的选择 holder 身份；重载换实例后用它判断预览选择已过期。 */
    private int selectedHolderIdentity;
    private boolean selectionExpired;

    /** 一次 CRAFT 在途：服务器响应序号变化或超时后解除，防双击重复提交。 */
    private boolean craftPending;
    private int pendingResultSeq;
    private int pendingTicks;

    private static final int PENDING_TIMEOUT_TICKS = 20;

    public ExtremeCraftingTerminalScreen(ExtremeCraftingTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        // 本菜单没有玩家槽位，不画物品栏标题
        this.inventoryLabelX = -1000;
        this.inventoryLabelY = -1000;
    }

    // ------------------------------------------------------------------
    // 初始化 / tick
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_W, SEARCH_H,
                Component.empty());
        searchBox.setMaxLength(80);
        searchBox.setHint(Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.search_hint"));
        searchBox.setResponder(text -> applyFilter());
        addRenderableWidget(searchBox);

        craftButton = Button.builder(
                        Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.craft"),
                        button -> doCraft())
                .bounds(leftPos + CRAFT_X, topPos + CRAFT_Y, CRAFT_W, CRAFT_H)
                .build(TexturedCraftButton::new);
        addRenderableWidget(craftButton);

        pinButton = Button.builder(
                        Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.pin"),
                        button -> doPin())
                .bounds(leftPos + PIN_X, topPos + PIN_Y, PIN_W, PIN_H)
                .build(TexturedCraftButton::new);
        addRenderableWidget(pinButton);

        loadRecipes();
        applyFilter();
        syncSelectedHolderIdentity();
        ensureSelectedVisible();
        // 首帧可能早于第一次 containerTick：这里先按同一套 canCraft 初始化，
        // 避免界面刚打开时按钮先亮一帧、下一 tick 才变灰。
        craftButton.active = canCraft();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshRecipesIfNeeded();
        if (craftPending) {
            if (menu.getResultSeq() != pendingResultSeq || --pendingTicks <= 0) {
                craftPending = false;
            }
        }
        if (craftButton != null) {
            craftButton.active = canCraft();
        }
        if (pinButton != null) {
            ExtremeCraftingTerminalBlockEntity be = menu.holder(minecraft == null ? null : minecraft.level);
            ResourceLocation selected = menu.getSelectedRecipeId();
            boolean pinned = be != null && selected != null
                    && selected.toString().equals(be.getPinnedRecipeId());
            pinButton.setMessage(Component.translatable(pinned
                    ? "gui.avaritia_singularity_stress.crafting_terminal.unpin"
                    : "gui.avaritia_singularity_stress.crafting_terminal.pin"));
            pinButton.active = selected != null;
        }
    }

    /** 数据包重载会替换 RecipeHolder 实例；每 20 tick 检查一次，变了就重建支持列表。 */
    private void refreshRecipesIfNeeded() {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        if (++recipeRefreshTicker < RECIPE_REFRESH_TICKS) {
            return;
        }
        recipeRefreshTicker = 0;
        long current = signatureOf(minecraft.level.getRecipeManager());
        long netCurrent = networkSignatureOf();
        if (current == recipeSignature && netCurrent == networkSignature) {
            return;
        }
        networkSignature = netCurrent;
        loadRecipes();
        applyFilter();
        validateSelectionAfterReload();
        ensureSelectedVisible();
    }

    /** 网络数量/品类/形状/盖板变化也要重建列表（只显示当前网络真能合成的配方）。 */
    private long networkSignatureOf() {
        ExtremeCraftingTerminalBlockEntity be = menu.holder(minecraft == null ? null : minecraft.level);
        if (be == null) {
            return 0L;
        }
        long sig = 1125899906842597L;
        sig = sig * 31 + be.getCrafterCount();
        sig = sig * 31 + (be.isNetworkBlaze() ? 1 : 0);
        sig = sig * 31 + java.util.Arrays.hashCode(be.getGridCells());
        return sig;
    }

    /** 重载后丢弃已过期的预览选择：同 id 换了 holder 实例也必须重新点一次。 */
    private void validateSelectionAfterReload() {
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null || minecraft == null || minecraft.level == null) {
            selectedHolderIdentity = 0;
            return;
        }
        RecipeHolder<?> holder = minecraft.level.getRecipeManager().byKey(selected).orElse(null);
        if (holder == null
                || !ExtremeCraftingTerminalMenu.isSupportedRecipeFor(holder.value(),
                        minecraft.level.registryAccess(), menu.holder(minecraft.level))) {
            expireSelection();
            return;
        }
        int identity = System.identityHashCode(holder.value());
        if (selectedHolderIdentity != 0 && selectedHolderIdentity != identity) {
            expireSelection();
            return;
        }
        selectedHolderIdentity = identity;
    }

    private void syncSelectedHolderIdentity() {
        selectionExpired = false;
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null || minecraft == null || minecraft.level == null) {
            selectedHolderIdentity = 0;
            return;
        }
        RecipeHolder<?> holder = minecraft.level.getRecipeManager().byKey(selected).orElse(null);
        selectedHolderIdentity = holder == null ? 0 : System.identityHashCode(holder.value());
    }

    private void expireSelection() {
        menu.setSelectedRecipeId(null);
        selectedHolderIdentity = 0;
        selectionExpired = true;
    }

    private static long signatureOf(RecipeManager recipeManager) {
        long signature = 1125899906842597L;
        for (RecipeHolder<?> holder : recipeManager.getOrderedRecipes()) {
            signature = signature * 31L + holder.id().hashCode();
            signature = signature * 31L + System.identityHashCode(holder.value());
        }
        return signature;
    }

    private void loadRecipes() {
        recipes.clear();
        if (minecraft == null || minecraft.level == null) {
            recipeSignature = 0L;
            return;
        }
        RecipeManager recipeManager = minecraft.level.getRecipeManager();
        recipeSignature = signatureOf(recipeManager);
        HolderLookup.Provider registries = minecraft.level.registryAccess();

        // 只列终端真实支持的配方类型；客户端 RecipeManager 由服务端同步，
        // 不在这里凭空构造配方，也不会出现"列表里有、服务端 layoutOf 不认"的条目。
        // 准入带网络上下文：品类收窄 + 数量/摆放/盖板形状过滤，只显示真能合成的。
        ExtremeCraftingTerminalBlockEntity be = menu.holder(minecraft.level);
        addSupported(recipeManager.getAllRecipesFor(ModRecipeTypes.CRAFTING_TABLE_RECIPE.get()), registries, be);
        addSupported(recipeManager.getAllRecipesFor(ModExtremeRecipeTypes.EXTREME_KINETIC_CRAFTING_TYPE.get()),
                registries, be);
        addSupported(recipeManager.getAllRecipesFor(ModExtremeRecipeTypes.BLAZE_CUBE_CRAFTING_TYPE.get()),
                registries, be);

        recipes.sort(Comparator
                .comparing((RecipeEntry entry) -> entry.result().getHoverName().getString(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.holder().id().toString()));
    }

    private <T extends Recipe<?>> void addSupported(List<RecipeHolder<T>> holders,
                                                    HolderLookup.Provider registries,
                                                    @Nullable ExtremeCraftingTerminalBlockEntity be) {
        for (RecipeHolder<T> holder : holders) {
            Recipe<?> recipe = holder.value();
            if (!ExtremeCraftingTerminalMenu.isSupportedRecipeFor(recipe, registries, be)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            result = result.copy();

            String name = result.getHoverName().getString();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            String searchText = (name + " " + holder.id() + " " + (itemId == null ? "" : itemId))
                    .toLowerCase(Locale.ROOT);
            recipes.add(new RecipeEntry(holder, result, searchText));
        }
    }

    private void applyFilter() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        boolean queryChanged = !query.equals(lastQuery);
        lastQuery = query;

        if (query.isEmpty()) {
            filtered = List.copyOf(recipes);
        } else {
            List<RecipeEntry> matches = new ArrayList<>();
            for (RecipeEntry entry : recipes) {
                if (entry.searchText().contains(query)) {
                    matches.add(entry);
                }
            }
            filtered = List.copyOf(matches);
        }

        if (queryChanged) {
            scroll = 0;
        }
        scroll = clamp(scroll, 0, maxListScroll());
    }

    private int visibleListRows() {
        return LIST_H / ROW_H;
    }

    private int maxListScroll() {
        return Math.max(0, filtered.size() - visibleListRows());
    }

    private void ensureSelectedVisible() {
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null) {
            return;
        }
        for (int i = 0; i < filtered.size(); i++) {
            if (!filtered.get(i).holder().id().equals(selected)) {
                continue;
            }
            if (i < scroll) {
                scroll = i;
            } else if (i >= scroll + visibleListRows()) {
                scroll = i - visibleListRows() + 1;
            }
            scroll = clamp(scroll, 0, maxListScroll());
            return;
        }
    }

    // ------------------------------------------------------------------
    // 操作
    // ------------------------------------------------------------------

    private void selectRecipe(RecipeEntry entry) {
        // 一次 CRAFT 在途时不放行新的 SELECT：否则延迟的 SELECT 回执（或本地清 pending）
        // 会让按钮提前解锁，违背"一次在途防双击"。等 CRAFT 回执/超时后再选。
        if (craftPending) {
            return;
        }
        ResourceLocation id = entry.holder().id();
        // 客户端先更新本会话选择，界面高亮/预览立刻跟手；服务端 SELECT 校验失败时
        // 会通过菜单结果槽回一个 INVALID_RECIPE，不会静默改掉别的会话。
        menu.setSelectedRecipe(entry.holder());
        selectedHolderIdentity = System.identityHashCode(entry.holder().value());
        selectionExpired = false;
        materialScroll = 0;
        ensureSelectedVisible();
        ModPackets.sendToServer(new CraftingTerminalPacket(menu.containerId, menu.getTargetPos(),
                CraftingTerminalPacket.KIND_SELECT, id));
    }

    private void doCraft() {
        if (craftPending) {
            return;
        }
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null) {
            return;
        }
        // 先记下当前响应序号再发包：服务端每个作业响应都会递增序号，
        // 屏内据此解除按钮 pending；超时只是网络异常兜底。
        pendingResultSeq = menu.getResultSeq();
        pendingTicks = PENDING_TIMEOUT_TICKS;
        craftPending = true;
        ModPackets.sendToServer(new CraftingTerminalPacket(menu.containerId, menu.getTargetPos(),
                CraftingTerminalPacket.KIND_CRAFT, selected));
    }

    /** 书签：钉住当前选择（再点取消）。钉住后作为默认选择并响应红石自动起工。 */
    private void doPin() {
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null) {
            return;
        }
        ModPackets.sendToServer(new CraftingTerminalPacket(menu.containerId, menu.getTargetPos(),
                CraftingTerminalPacket.KIND_PIN, selected));
    }


    private boolean canCraft() {
        if (craftPending || selectionExpired || menu.getSelectedRecipeId() == null) {
            return false;
        }
        // 只要配方本身有效就允许点一次：网络未就绪/零动力/网络占用要让服务端返回
        // INVALID_NETWORK / NO_POWER / BUSY 的具体原因，不能在前端用笼统的 not ready 挡掉。
        RecipeHolder<?> holder = selectedHolder();
        return holder != null && selectedLayout() != null;
    }

    @Nullable
    private RecipeHolder<?> selectedHolder() {
        ResourceLocation selected = menu.getSelectedRecipeId();
        if (selected == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getRecipeManager().byKey(selected).orElse(null);
    }

    @Nullable
    private RecipeLayout selectedLayout() {
        RecipeHolder<?> holder = selectedHolder();
        if (holder == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        return ExtremeCraftingTerminalMenu.supportedLayout(holder.value(), minecraft.level.registryAccess());
    }

    // ------------------------------------------------------------------
    // 输入：搜索框 E 不关屏、左键只命中画出的完整行
    // ------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 原版 AbstractContainerScreen 会在把按键交给焦点控件之前检查物品栏键（E），
        // 搜索框聚焦时按 E 会被当成"关闭界面"。
        // 只拦：ESC/Tab 交还父类；EditBox 自己消费的编辑键；以及物品栏关闭键。
        if (searchBox != null && searchBox.isFocused() && searchBox.canConsumeInput()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = listRowAt(mouseX, mouseY);
            if (row >= 0) {
                setFocused(null);
                selectRecipe(filtered.get(row));
                return true;
            }
        }
        if (searchBox != null && !searchBox.isMouseOver(mouseX, mouseY)) {
            setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 只命中实际画出的完整行：与 {@link #renderRecipeList} 使用同一套 LIST_H / ROW_H / scroll。
     * 列表底部的半行不会响应点击，避免"看起来没画出来却能选中"。
     */
    private int listRowAt(double mouseX, double mouseY) {
        double localX = mouseX - (leftPos + LIST_X);
        double localY = mouseY - (topPos + LIST_Y);
        if (localX < 0 || localX >= LIST_W || localY < 0 || localY >= LIST_H) {
            return -1;
        }
        int row = (int) (localY / ROW_H);
        if (row < 0 || row >= visibleListRows()) {
            return -1;
        }
        int index = scroll + row;
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int step = scrollY > 0 ? -1 : 1;
        if (materialRowAt(mouseX, mouseY) >= 0 || isOverMaterialColumn(mouseX, mouseY)) {
            materialScroll = clamp(materialScroll + step, 0, maxMaterialScroll());
            return true;
        }
        if (isOverList(mouseX, mouseY)) {
            scroll = clamp(scroll + step, 0, maxListScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverList(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X && mouseX < leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY < topPos + LIST_Y + LIST_H;
    }

    private boolean isOverMaterialColumn(double mouseX, double mouseY) {
        return mouseX >= leftPos + MAT_X && mouseX < leftPos + MAT_X + MAT_W
                && mouseY >= topPos + MAT_HEADER_Y
                && mouseY < topPos + MAT_LIST_Y + MAT_VISIBLE * MAT_ROW_H;
    }

    private int materialRowAt(double mouseX, double mouseY) {
        int x = leftPos + MAT_X;
        int y = topPos + MAT_LIST_Y;
        if (mouseX < x || mouseX >= x + MAT_W
                || mouseY < y || mouseY >= y + MAT_VISIBLE * MAT_ROW_H) {
            return -1;
        }
        int row = (int) ((mouseY - y) / MAT_ROW_H);
        if (row < 0 || row >= MAT_VISIBLE) {
            return -1;
        }
        int index = materialScroll + row;
        return index >= 0 && index < materials.size() ? index : -1;
    }

    // ------------------------------------------------------------------
    // 背景 / 主渲染
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 沿用项目现有的纯 fill 面板风格（山灰底 + 深灰凹槽），不新增 GUI 贴图。
        graphics.fill(leftPos, topPos, leftPos + GUI_W, topPos + GUI_H, 0xFFC6C6C6);

        graphics.fill(leftPos + LIST_X - 2, topPos + LIST_Y - 2,
                leftPos + LIST_X + LIST_W + 2, topPos + LIST_Y + LIST_H + 2, 0xFF8B8B8B);
        graphics.fill(leftPos + LIST_X, topPos + LIST_Y,
                leftPos + LIST_X + LIST_W, topPos + LIST_Y + LIST_H, 0xFFA0A0A0);

        graphics.fill(leftPos + PANEL_X - 2, topPos + 16,
                leftPos + PANEL_X + PANEL_W + 2, topPos + GUI_H - 2, 0xFF8B8B8B);
        graphics.fill(leftPos + PANEL_X, topPos + 18,
                leftPos + PANEL_X + PANEL_W, topPos + GUI_H - 4, 0xFFB0B0B0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, leftPos + 8, topPos + 6, 0x404040, false);

        renderRecipeList(graphics, mouseX, mouseY);
        renderSelectedPanel(graphics, mouseX, mouseY);

        // 只有鼠标所在区域的一个 tooltip 会真正出现；互斥判断按从具体到宽泛的顺序。
        renderRecipeTooltip(graphics, mouseX, mouseY);
        renderMaterialTooltip(graphics, mouseX, mouseY);
        renderPreviewTooltip(graphics, mouseX, mouseY);
        renderStatusTooltip(graphics, mouseX, mouseY);
        renderButtonTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题自己画（要控制位置），也不画不存在的玩家物品栏标签。
    }

    private void renderRecipeList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;
        int hovered = listRowAt(mouseX, mouseY);

        graphics.enableScissor(x, y, x + LIST_W, y + LIST_H);
        for (int i = 0; i < visibleListRows(); i++) {
            int index = scroll + i;
            if (index >= filtered.size()) {
                break;
            }
            RecipeEntry entry = filtered.get(index);
            int rowY = y + i * ROW_H;

            if (index == hovered) {
                graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFFBDBDBD);
            }
            if (entry.holder().id().equals(menu.getSelectedRecipeId())) {
                graphics.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0xFF7B7BB5);
            }

            renderResultIcon(graphics, entry.result(), x + 1, rowY + 1);
            String name = clip(entry.result().getHoverName().getString(), LIST_W - 22);
            graphics.drawString(font, name, x + 20, rowY + 5, 0xFFFFFF, false);
        }
        graphics.disableScissor();

        if (filtered.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.list_empty"),
                    x + 4, y + 4, 0x505050, false);
        }
        drawScrollbar(graphics, x + LIST_W - SCROLLBAR_W, y, LIST_H,
                scroll, filtered.size(), visibleListRows());
    }

    private void renderSelectedPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        ResourceLocation selected = menu.getSelectedRecipeId();
        RecipeHolder<?> holder = selectedHolder();
        RecipeLayout layout = holder == null ? null : selectedLayout();

        int headerX = leftPos + HEADER_X;
        int headerY = topPos + HEADER_Y;

        if (selected == null) {
            clearMaterials();
            drawCentered(graphics,
                    Component.translatable(selectionExpired
                            ? "gui.avaritia_singularity_stress.crafting_terminal.selected_missing"
                            : "gui.avaritia_singularity_stress.crafting_terminal.no_selection"),
                    leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SIZE, PREVIEW_SIZE,
                    selectionExpired ? 0xAA0000 : 0x505050);
        } else if (holder == null) {
            clearMaterials();
            graphics.drawString(font,
                    Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.selected_missing"),
                    headerX, headerY + 4, 0xAA0000, false);
            drawCentered(graphics,
                    Component.translatable(
                            "gui.avaritia_singularity_stress.crafting_terminal.status.invalid_recipe"),
                    leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SIZE, PREVIEW_SIZE, 0xAA0000);
        } else {
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            renderResultIcon(graphics, result, headerX, headerY);
            String name = clip(result.getHoverName().getString(), HEADER_W - 22);
            graphics.drawString(font, name, headerX + 20, headerY + 4, 0x404040, false);

            if (layout == null) {
                clearMaterials();
                drawCentered(graphics,
                        Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.unsupported"),
                        leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SIZE, PREVIEW_SIZE, 0xAA0000);
            } else {
                ensureMaterials(selected, holder, layout);
                renderPreview(graphics, layout);
                renderMaterials(graphics, mouseX, mouseY);
            }
        }

        renderMetrics(graphics);
        renderStatus(graphics);
    }

    private void renderPreview(GuiGraphics graphics, RecipeLayout layout) {
        int pitch = PREVIEW_CELL + PREVIEW_GAP;
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int x = leftPos + PREVIEW_X + col * pitch;
                int y = topPos + PREVIEW_Y + row * pitch;
                boolean inside = row < layout.height() && col < layout.width();
                Ingredient ingredient = inside
                        ? layout.cells().get(row * layout.width() + col)
                        : Ingredient.EMPTY;
                int color = !inside ? 0xFF9A9A9A : (ingredient.isEmpty() ? 0xFF8A8A8A : 0xFFA8A8A8);
                graphics.fill(x, y, x + PREVIEW_CELL, y + PREVIEW_CELL, color);

                if (!ingredient.isEmpty()) {
                    ItemStack[] candidates = ingredient.getItems();
                    if (candidates.length > 0) {
                        renderScaledItem(graphics, candidates[0], x, y, PREVIEW_CELL);
                    }
                    if (candidates.length > 1) {
                        graphics.drawString(font, "+", x + PREVIEW_CELL - 4, y + PREVIEW_CELL - 8,
                                0xFFFFFF, false);
                    }
                }
            }
        }
    }

    private void renderMaterials(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + MAT_X;
        int headerY = topPos + MAT_HEADER_Y;
        int listY = topPos + MAT_LIST_Y;

        graphics.drawString(font,
                Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.materials"),
                x, headerY, 0x404040, false);

        if (materials.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.materials.none"),
                    x, listY + 2, 0x606060, false);
            return;
        }

        int hovered = materialRowAt(mouseX, mouseY);
        graphics.enableScissor(x, listY, x + MAT_W, listY + MAT_VISIBLE * MAT_ROW_H);
        for (int i = 0; i < MAT_VISIBLE; i++) {
            int index = materialScroll + i;
            if (index >= materials.size()) {
                break;
            }
            MaterialGroup group = materials.get(index);
            int rowY = listY + i * MAT_ROW_H;
            if (index == hovered) {
                graphics.fill(x, rowY, x + MAT_W, rowY + MAT_ROW_H, 0xFFC8C8C8);
            }

            ItemStack display = group.display();
            if (!display.isEmpty()) {
                renderScaledItem(graphics, display, x + 1, rowY, 10);
            }
            graphics.drawString(font, "x" + group.count(), x + 13, rowY + 2, 0x303030, false);
            if (group.candidates().size() > 1) {
                graphics.drawString(font, "+" + (group.candidates().size() - 1),
                        x + 31, rowY + 2, 0x606060, false);
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, x + MAT_W - SCROLLBAR_W, listY, MAT_VISIBLE * MAT_ROW_H,
                materialScroll, materials.size(), MAT_VISIBLE);
    }

    private void renderMetrics(GuiGraphics graphics) {
        ExtremeCraftingTerminalBlockEntity be = menu.holder(minecraft.level);
        int x = leftPos + METRIC_X;
        int y = topPos + METRIC_Y;

        if (be == null) {
            graphics.drawString(font,
                    Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.network.missing"),
                    x, y, 0xAA0000, false);
            return;
        }

        graphics.drawString(font,
                Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.metric.crafters",
                        be.getCrafterCount()),
                x, y, 0x404040, false);
        graphics.drawString(font,
                Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.metric.stress",
                        formatSu(be.getStressNeed())),
                x, y + METRIC_LINE, 0x404040, false);
        String networkKey;
        int networkColor;
        if (be.getCrafterCount() <= 0 && be.getConnectorCount() > 0) {
            // 有连接器但整链被 fail-closed 拒绝：多为重放后背贴朝向不符/箭头断链，
            // 与"终端旁边根本没有连接器"区分开，避免误导（B29 诊断改进）。
            networkKey = "gui.avaritia_singularity_stress.crafting_terminal.network.incomplete";
            networkColor = 0xAA0000;
        } else if (be.getCrafterCount() <= 0) {
            networkKey = "gui.avaritia_singularity_stress.crafting_terminal.network.missing";
            networkColor = 0xAA0000;
        } else if (!be.isNetworkReady()) {
            // 有合成器但整链还没就绪：这里只报短状态，具体原因由下方 CraftResult 给出。
            networkKey = "gui.avaritia_singularity_stress.crafting_terminal.network.not_ready";
            networkColor = 0x806000;
        } else {
            networkKey = "gui.avaritia_singularity_stress.crafting_terminal.network.ready";
            networkColor = 0x008800;
        }
        graphics.drawString(font, clip(Component.translatable(networkKey).getString(), HEADER_W),
                x, y + METRIC_LINE * 2, networkColor, false);
    }

    private void renderStatus(GuiGraphics graphics) {
        Component full = statusComponent();
        String text = clip(full.getString(), STATUS_W);
        graphics.drawString(font, text, leftPos + STATUS_X, topPos + STATUS_Y, statusColor(), false);
    }

    private Component statusComponent() {
        if (selectionExpired) {
            return Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.selected_missing");
        }
        if (craftPending) {
            return Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.craft.pending");
        }
        CraftResult result = menu.getCraftResult();
        if (result != null) {
            return Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.status."
                    + result.name().toLowerCase(Locale.ROOT));
        }
        return Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.hint.select");
    }

    private int statusColor() {
        if (selectionExpired) {
            return 0xAA0000;
        }
        if (craftPending) {
            return 0x806000;
        }
        CraftResult result = menu.getCraftResult();
        if (result != null) {
            return result == CraftResult.STARTED ? 0x008800 : 0xAA0000;
        }
        return 0x606060;
    }

    // ------------------------------------------------------------------
    // 材料聚合（按 item + data components 比较，不拿 ItemStack 当 key）
    // ------------------------------------------------------------------

    private void ensureMaterials(ResourceLocation id, RecipeHolder<?> holder, RecipeLayout layout) {
        int identity = System.identityHashCode(holder.value());
        if (id.equals(materialRecipeId) && identity == materialRecipeIdentity) {
            return;
        }
        materialRecipeId = id;
        materialRecipeIdentity = identity;

        Map<List<StackKey>, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : layout.cells()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length == 0) {
                continue;
            }
            List<StackKey> keys = new ArrayList<>(candidates.length);
            for (ItemStack candidate : candidates) {
                if (!candidate.isEmpty()) {
                    keys.add(StackKey.of(candidate));
                }
            }
            if (keys.isEmpty()) {
                continue;
            }
            // 排序 + 去重：同一组候选即使顺序不同也合并成一个"材料组"；
            // StackKey 只持有 Item 与 DataComponentMap（都不可变），满足"带 components 的内容比较"。
            keys.sort(Comparator
                    .comparing((StackKey key) -> String.valueOf(BuiltInRegistries.ITEM.getKey(key.item())))
                    .thenComparing(key -> String.valueOf(key.components())));
            List<StackKey> unique = new ArrayList<>(keys.size());
            for (StackKey key : keys) {
                if (unique.isEmpty() || !unique.get(unique.size() - 1).equals(key)) {
                    unique.add(key);
                }
            }
            counts.merge(List.copyOf(unique), 1, Integer::sum);
        }

        List<MaterialGroup> groups = new ArrayList<>(counts.size());
        for (Map.Entry<List<StackKey>, Integer> entry : counts.entrySet()) {
            groups.add(new MaterialGroup(entry.getKey(), entry.getValue()));
        }
        materials = List.copyOf(groups);
        materialScroll = clamp(materialScroll, 0, maxMaterialScroll());
    }

    private void clearMaterials() {
        materials = List.of();
        materialRecipeId = null;
        materialRecipeIdentity = 0;
        materialScroll = 0;
    }

    private int maxMaterialScroll() {
        return Math.max(0, materials.size() - MAT_VISIBLE);
    }

    // ------------------------------------------------------------------
    // Tooltip：候选组、预览格、过期的选择、按钮禁用原因
    // ------------------------------------------------------------------

    private void renderRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = listRowAt(mouseX, mouseY);
        if (row < 0) {
            return;
        }
        RecipeEntry entry = filtered.get(row);
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, entry.result()));
        lines.add(Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.recipe_id",
                        entry.holder().id().toString())
                .withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, (int) mouseX, (int) mouseY);
    }

    private void renderMaterialTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int row = materialRowAt(mouseX, mouseY);
        if (row < 0) {
            return;
        }
        MaterialGroup group = materials.get(row);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.materials.need",
                group.count()));
        if (group.candidates().size() > 1) {
            lines.add(Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.materials.variants",
                    group.candidates().size()));
        }
        int shown = 0;
        for (StackKey key : group.candidates()) {
            if (shown >= TOOLTIP_CANDIDATES) {
                lines.add(Component.translatable(
                        "gui.avaritia_singularity_stress.crafting_terminal.materials.more",
                        group.candidates().size() - shown));
                break;
            }
            lines.add(key.toStack().getHoverName());
            shown++;
        }
        graphics.renderComponentTooltip(font, lines, (int) mouseX, (int) mouseY);
    }

    private void renderPreviewTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        RecipeHolder<?> holder = selectedHolder();
        if (holder == null || minecraft.level == null) {
            return;
        }
        RecipeLayout layout = ExtremeCraftingTerminalMenu.supportedLayout(holder.value(),
                minecraft.level.registryAccess());
        if (layout == null) {
            return;
        }

        int[] cell = previewCellAt(mouseX, mouseY);
        if (cell == null) {
            return;
        }
        int row = cell[0];
        int col = cell[1];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.avaritia_singularity_stress.crafting_terminal.preview.cell",
                row + 1, col + 1));

        if (row >= layout.height() || col >= layout.width()) {
            lines.add(Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.preview.empty"));
        } else {
            Ingredient ingredient = layout.cells().get(row * layout.width() + col);
            if (ingredient.isEmpty()) {
                lines.add(Component.translatable(
                        "gui.avaritia_singularity_stress.crafting_terminal.preview.empty"));
            } else {
                ItemStack[] candidates = ingredient.getItems();
                if (candidates.length == 0) {
                    lines.add(Component.translatable(
                            "gui.avaritia_singularity_stress.crafting_terminal.preview.no_candidates"));
                } else {
                    lines.add(Component.translatable(
                            "gui.avaritia_singularity_stress.crafting_terminal.materials.need", 1));
                    int shown = 0;
                    for (ItemStack candidate : candidates) {
                        if (shown >= TOOLTIP_CANDIDATES) {
                            lines.add(Component.translatable(
                                    "gui.avaritia_singularity_stress.crafting_terminal.materials.more",
                                    candidates.length - shown));
                            break;
                        }
                        lines.add(candidate.getHoverName());
                        shown++;
                    }
                }
            }
        }
        graphics.renderComponentTooltip(font, lines, (int) mouseX, (int) mouseY);
    }

    @Nullable
    private int[] previewCellAt(double mouseX, double mouseY) {
        int localX = (int) (mouseX - (leftPos + PREVIEW_X));
        int localY = (int) (mouseY - (topPos + PREVIEW_Y));
        int pitch = PREVIEW_CELL + PREVIEW_GAP;
        if (localX < 0 || localY < 0) {
            return null;
        }
        int col = localX / pitch;
        int row = localY / pitch;
        if (col >= GRID || row >= GRID) {
            return null;
        }
        if (localX % pitch >= PREVIEW_CELL || localY % pitch >= PREVIEW_CELL) {
            return null;
        }
        return new int[]{row, col};
    }

    private void renderStatusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < leftPos + STATUS_X || mouseX >= leftPos + STATUS_X + STATUS_W
                || mouseY < topPos + STATUS_Y || mouseY >= topPos + STATUS_Y + STATUS_H) {
            return;
        }
        Component full = statusComponent();
        if (font.width(full.getString()) <= STATUS_W) {
            return;
        }
        graphics.renderComponentTooltip(font, List.of(full), (int) mouseX, (int) mouseY);
    }

    private void renderButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // AbstractWidget.isMouseOver 隐含 active 判断；禁用按钮必须直接用几何 bounds。
        int x = leftPos + CRAFT_X;
        int y = topPos + CRAFT_Y;
        if (craftButton == null || craftButton.active || craftPending
                || mouseX < x || mouseX >= x + CRAFT_W
                || mouseY < y || mouseY >= y + CRAFT_H) {
            return;
        }
        Component reason;
        if (selectionExpired || menu.getSelectedRecipeId() != null) {
            reason = Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.craft.stale");
        } else {
            reason = Component.translatable(
                    "gui.avaritia_singularity_stress.crafting_terminal.craft.no_selection");
        }
        graphics.renderComponentTooltip(font, List.of(reason), (int) mouseX, (int) mouseY);
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /**
     * 结果物品图标 + 数量叠层。原版 renderItemDecorations 只在 count != 1 时画数字，
     * 这里显式加 >1 条件：产物 8 个（如 avaritia:ancient_debris_eight）能看见，单个产物不冗余。
     */
    private void renderResultIcon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
        if (stack.getCount() > 1) {
            graphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void renderScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty()) {
            return;
        }
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int height,
                               int offset, int total, int visible) {
        if (total <= visible || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + SCROLLBAR_W, y + height, 0xFF8B8B8B);
        int thumbHeight = Math.max(8, height * visible / total);
        int maxOffset = total - visible;
        int thumbY = y + (height - thumbHeight) * offset / maxOffset;
        graphics.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbHeight, 0xFF5A5A5A);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int x, int y, int width, int height,
                              int color) {
        String clipped = clip(text.getString(), width - 4);
        int textX = x + (width - font.width(clipped)) / 2;
        int textY = y + (height - font.lineHeight) / 2;
        graphics.drawString(font, clipped, textX, textY, color, false);
    }

    private String clip(String text, int width) {
        if (width <= 0) {
            return "";
        }
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatSu(float su) {
        if (su >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM SU", su / 1_000_000);
        }
        if (su >= 1000) {
            return String.format(Locale.ROOT, "%.1fk SU", su / 1000);
        }
        return String.format(Locale.ROOT, "%.0f SU", su);
    }

    /**
     * B29 自动合成按钮：只替换底图渲染，焦点、键盘、叙述和点击仍由原版 {@link Button} 处理。
     * <p>
     * 底图沿用项目统一按钮图 {@link SpriteButton#TEXTURE}（64×14，逐像素核对：顶部/左右 2px、
     * 底部 3px 描边，其中外圈 1px 深灰、内圈 1px 白/底部 y=12 的 132 灰）。这里按九宫格绘制：
     * 四角与上下左右描边保持 1:1，只有纯色中心被拉伸。不能像三段横拉那样纵向拉整张图，
     * 否则 18px 高按钮会把 1px 描边拉成 1.28px，像素会糊。
     * <p>
     * 同一张 PNG 用 shader 颜色做状态微调：正常略暗、悬停/焦点回到原亮度、禁用再压暗；
     * 绘制完立即把 RenderSystem 颜色复位，避免污染后续文字/图标。
     */
    private static final class TexturedCraftButton extends Button {
        private static final int TEX_W = 64;
        private static final int TEX_H = 14;
        /** 顶部/左右各 2px；底部 3px（y=11 白线、y=12 灰线、y=13 外框）。 */
        private static final int CAP = 2;
        private static final int BOTTOM_CAP = 3;
        private static final int MID_U = CAP;
        private static final int MID_V = CAP;
        private static final int MID_W = TEX_W - CAP * 2;
        private static final int MID_H = TEX_H - CAP - BOTTOM_CAP;

        /** 浅灰底上的深色字；禁用态也保持可读，不用原版浅灰字。 */
        private static final int TEXT_COLOR = 0x303030;
        private static final int TEXT_COLOR_DISABLED = 0x333333;
        private static final float BRIGHTNESS_NORMAL = 0.93F;
        private static final float BRIGHTNESS_HOVER = 1.0F;
        private static final float BRIGHTNESS_DISABLED = 0.78F;

        private TexturedCraftButton(Button.Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float brightness = !this.active
                    ? BRIGHTNESS_DISABLED
                    : (isHoveredOrFocused() ? BRIGHTNESS_HOVER : BRIGHTNESS_NORMAL);
            graphics.setColor(brightness, brightness, brightness, this.alpha);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            drawNineSlice(graphics, getX(), getY(), getWidth(), getHeight());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

            int color = this.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
            // 复用原版 renderString：居中/超宽滚动、2px 边距和 alpha 都与普通 Button 一致。
            renderString(graphics, Minecraft.getInstance().font,
                    color | (Mth.ceil(this.alpha * 255.0F) << 24));
        }

        private static void drawNineSlice(GuiGraphics graphics, int x, int y, int width, int height) {
            // 本屏按钮固定 198×18；极小尺寸退化时直接整图拉伸，避免负宽/越界。
            if (width < CAP * 2 || height < CAP + BOTTOM_CAP) {
                graphics.blit(SpriteButton.TEXTURE, x, y, width, height,
                        0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
                return;
            }

            int midW = width - CAP * 2;
            int midH = height - CAP - BOTTOM_CAP;

            // 四角固定，不参与任何缩放。
            blitPart(graphics, x, y, CAP, CAP, 0, 0, CAP, CAP);
            blitPart(graphics, x + width - CAP, y, CAP, CAP,
                    TEX_W - CAP, 0, CAP, CAP);
            blitPart(graphics, x, y + height - BOTTOM_CAP, CAP, BOTTOM_CAP,
                    0, TEX_H - BOTTOM_CAP, CAP, BOTTOM_CAP);
            blitPart(graphics, x + width - CAP, y + height - BOTTOM_CAP, CAP, BOTTOM_CAP,
                    TEX_W - CAP, TEX_H - BOTTOM_CAP, CAP, BOTTOM_CAP);

            // 上边 2px、下边 3px，均只横向拉伸。
            blitPart(graphics, x + CAP, y, midW, CAP, MID_U, 0, MID_W, CAP);
            blitPart(graphics, x + CAP, y + height - BOTTOM_CAP, midW, BOTTOM_CAP,
                    MID_U, TEX_H - BOTTOM_CAP, MID_W, BOTTOM_CAP);
            // 左右边只纵向拉伸，横向保持源图 2px 描边。
            blitPart(graphics, x, y + CAP, CAP, midH, 0, MID_V, CAP, MID_H);
            blitPart(graphics, x + width - CAP, y + CAP, CAP, midH,
                    TEX_W - CAP, MID_V, CAP, MID_H);
            // 中心是纯色 198 灰，拉伸不会产生可见的像素变形。
            blitPart(graphics, x + CAP, y + CAP, midW, midH,
                    MID_U, MID_V, MID_W, MID_H);
        }

        private static void blitPart(GuiGraphics graphics, int x, int y, int width, int height,
                                     int u, int v, int uWidth, int vHeight) {
            graphics.blit(SpriteButton.TEXTURE, x, y, width, height,
                    (float) u, (float) v, uWidth, vHeight, TEX_W, TEX_H);
        }
    }

    private record RecipeEntry(RecipeHolder<?> holder, ItemStack result, String searchText) {
    }

    /**
     * 材料候选的不可变键：Item + DataComponentPatch。
     * 不使用 ItemStack 引用/可变实例作 map key；同一物品只有 components patch 相同才视为同一候选。
     */
    private record StackKey(Item item, DataComponentPatch components) {

        private static StackKey of(ItemStack stack) {
            return new StackKey(stack.getItem(), stack.getComponentsPatch());
        }

        private ItemStack toStack() {
            ItemStack stack = new ItemStack(item, 1);
            stack.applyComponents(components);
            return stack;
        }
    }

    private record MaterialGroup(List<StackKey> candidates, int count) {

        private ItemStack display() {
            return candidates.isEmpty() ? ItemStack.EMPTY : candidates.get(0).toStack();
        }
    }
}
