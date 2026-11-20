package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.CraftResult;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * 客户端 → 服务端：终端操作。kind=0 选择配方，kind=1 开始自动合成。
 * <p>
 * <b>绑定规则</b>（客户端不能凭坐标调没开的菜单）：
 * <ol>
 *   <li>{@code menuId} 必须等于玩家当前打开菜单的 {@link net.minecraft.world.inventory.AbstractContainerMenu#containerId}；</li>
 *   <li>包内坐标必须等于菜单创建时记录的坐标；</li>
 *   <li>菜单的 {@link ExtremeCraftingTerminalMenu#isOpenFor} 统一复核原版容器存活条件：
 *       方块仍在、原版容器距离、{@code level.mayInteract}、坐标处仍是打开时那台 BE。
 *       网络包不重复手写这些条件，避免与 stillValid 口径漂移；</li>
 *   <li>以上任一不满足都静默丢弃 —— 伪造包不会影响任何终端会话。</li>
 * </ol>
 * <p>
 * <b>会话隔离</b>：SELECT 通过真实 {@code RecipeManager} + {@code layoutOf} 校验后只写本菜单会话，
 * 并保存当次 {@link RecipeHolder} 实例；CRAFT 只接受本菜单当前选择的、仍是同一 holder 实例的配方 id。
 * 数据包重载用同 id 换了配方实例时必须重新 SELECT。另一个玩家在同一 BE 上改选择不会影响本会话。
 * <p>
 * <b>反馈</b>：CRAFT 调用 BE 的 {@code tryStartCraft} 拿到 {@link CraftResult}，
 * 写入菜单的 ContainerData（8 位响应序号 + 8 位结果编码）；界面只有看到序号变化
 * 或有限超时才解除按钮 pending，避免一次作业被双击重复提交。
 * SELECT 的回执只写结果编码、不推进序号，避免延迟的 SELECT 误解除 CRAFT pending。
 * 不需要新增 S2C 网络包，也不会只在关掉菜单后才能看到结果。
 */
public record CraftingTerminalPacket(int menuId, BlockPos pos, int kind, ResourceLocation recipeId)
        implements CustomPacketPayload {

    public static final int KIND_SELECT = 0;
    public static final int KIND_CRAFT = 1;
    /** 书签：钉住该配方（再发同 id=取消）。钉住后作为默认选择并响应红石自动起工。 */
    public static final int KIND_PIN = 2;

    public static final CustomPacketPayload.Type<CraftingTerminalPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    AvaritiaSingularityStressMod.MODID, "crafting_terminal_action"));

    public static final StreamCodec<FriendlyByteBuf, CraftingTerminalPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CraftingTerminalPacket::menuId,
                    BlockPos.STREAM_CODEC, CraftingTerminalPacket::pos,
                    ByteBufCodecs.VAR_INT, CraftingTerminalPacket::kind,
                    ResourceLocation.STREAM_CODEC, CraftingTerminalPacket::recipeId,
                    CraftingTerminalPacket::new);

    public static final IPayloadHandler<CraftingTerminalPacket> HANDLER = CraftingTerminalPacket::handle;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void handle(CraftingTerminalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.isSpectator() || player.isDeadOrDying()) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            // 未知动作直接拒绝，避免未来新增 kind 时旧逻辑误走 SELECT 分支。
            if (packet.kind() != KIND_SELECT && packet.kind() != KIND_CRAFT && packet.kind() != KIND_PIN) {
                return;
            }
            if (!(player.containerMenu instanceof ExtremeCraftingTerminalMenu menu)) {
                return;
            }
            // menuId / 坐标 / 原版容器存活条件（距离、方块、mayInteract、同一 BE）全部集中在菜单里。
            if (!menu.isOpenFor(player, packet.menuId(), packet.pos())) {
                return;
            }
            ExtremeCraftingTerminalBlockEntity terminal = menu.getServerTerminal();
            if (terminal == null) {
                return;
            }

            ResourceLocation recipeId = packet.recipeId();
            if (recipeId == null) {
                return;
            }

            if (packet.kind() == KIND_PIN) {
                // 取消钉住不校验准入；钉住必须通过当前网络上下文的真实准入。
                if (recipeId.toString().equals(terminal.getPinnedRecipeId())) {
                    terminal.pinRecipe(null);
                } else {
                    RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
                    if (holder == null || !isSupported(holder, level, terminal)) {
                        menu.setCraftResult(CraftResult.INVALID_RECIPE);
                        menu.broadcastChanges();
                        return;
                    }
                    terminal.pinRecipe(recipeId);
                }
                menu.setCraftResult(null);
                menu.broadcastChanges();
                return;
            }

            if (packet.kind() == KIND_SELECT) {
                RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
                if (holder == null || !isSupported(holder, level, terminal)) {
                    // 客户端列表只展示已同步的配方，但数据包可以伪造；这里以服务端 RecipeManager 为准。
                    menu.setCraftResult(CraftResult.INVALID_RECIPE);
                    menu.broadcastChanges();
                    return;
                }
                // 只写本菜单会话；同时把 BE 的"上次选择"更新给之后打开的菜单做初值。
                menu.setSelectedRecipe(holder);
                menu.setCraftResult(null);
                terminal.selectRecipe(recipeId);
                menu.broadcastChanges();
                return;
            }

            // CRAFT：目标必须是本菜单当前选择，且必须是 SELECT 时那一个 holder 实例。
            // 数据包重载可能用同一个 id 替换配方内容；身份不一致时要求重新 SELECT。
            RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null || !menu.isSelectedHolder(holder) || !isSupported(holder, level, terminal)) {
                // 这是 CRAFT 的响应，推进 seq 让屏内 pending 立即解除。
                menu.setCraftResponse(CraftResult.INVALID_RECIPE);
                menu.broadcastChanges();
                return;
            }

            // 重复提交由 BE 的核心状态机（BUSY 等）防护，这里只转发一次结果。
            CraftResult result = terminal.tryStartCraft(recipeId);
            menu.setCraftResponse(result == null ? CraftResult.TRANSFER_FAILED : result);
            // 结果只是本菜单的一次性显示状态，不写回 BE、不做额外 sendData；
            // broadcastChanges 只负责把结果槽在当前 tick 送到客户端屏内。
            menu.broadcastChanges();
        });
    }

    /**
     * 与菜单/客户端列表同源的准入：真实 RecipeManager → 支持的类型/产物 → layoutOf → 非空候选。
     * 不在这里另造一套"能合成"的判断。
     */
    private static boolean isSupported(RecipeHolder<?> holder, ServerLevel level,
                                       ExtremeCraftingTerminalBlockEntity terminal) {
        return ExtremeCraftingTerminalMenu.isSupportedRecipeFor(
                holder.value(), level.registryAccess(), terminal);
    }
}
