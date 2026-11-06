package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import committee.nova.avaritia_singularity_stress.content.wireless.WirelessBlockInteractions;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;

import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B15 空间折跃隧道·方块（单端口 · 一面两槽）。
 *
 * <h2>继承链</h2>
 * {@code BeltTunnelBlock → BrassTunnelBlock → WarpTunnelBlock}。
 * Create 把类型判断写死了：{@code BeltTunnelInteractionHandler:52}（BE 是
 * {@code BrassTunnelBlockEntity} 才交物品）、{@code :123}（方块是
 * {@code BrassTunnelBlock} 才做"带子停在隧道前"）。不继承就只能往 Create 的传送带 tick 里塞 mixin。
 *
 * <h2>交互（当前真实映射）</h2>
 * <table>
 *   <tr><th>操作</th><th>效果</th></tr>
 *   <tr><td>手持物品 / 空手，点四个<b>白点</b></td><td>过滤槽：放物品 / 清空（Create 的 ValueBox，不经本类的 use 方法）</td></tr>
 *   <tr><td>扳手右键某个侧面</td><td>绑定 / 解绑（先点 A 面，再点 B 面）</td></tr>
 *   <tr><td>潜行 + 扳手右键</td><td>切换隧道视窗 CLOSED ⇄ WINDOW（交还 {@code BeltTunnelBlock.onWrenched}）</td></tr>
 *   <tr><td>潜行 + 空手右键</td><td>读这一面的绑定状态与过滤物品（聊天栏提示）</td></tr>
 *   <tr><td>上下两面</td><td>不接传送带：不参与绑定，也不给"未绑定"提示</td></tr>
 * </table>
 */
public class WarpTunnelBlock extends BrassTunnelBlock {

    /** 红石锁定：通电即整机停机（不接货、不投递）。用原版属性，自带同步与模型无关。 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public WarpTunnelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    // ===================== 放置 / 红石 =====================

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // 绑定不需要"所有者"：面与面直接互指坐标，所以这里不再写 owner。
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    // ===================== 方块实体 =====================

    @Override
    public Class<BeltTunnelBlockEntity> getBlockEntityClass() {
        return BeltTunnelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BeltTunnelBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WARP_TUNNEL.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WarpTunnelBlockEntity.create(pos, state);
    }

    // ===================== 交互 =====================

    /**
     * 潜行 + 空手右键 = 查看这个面的状态（绑定目标 + 过滤物品）。
     * <p>
     * 不带潜行时必须让位：过滤槽的四个白点由 Create 的 ValueBox 客户端拦截处理
     * （点白点放/清过滤物品），我们在服务端抢这一下就会把它吃掉。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (WirelessBlockInteractions.deferToHeldItem(player)) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof WarpTunnelBlockEntity tunnel)) {
            return InteractionResult.PASS;
        }

        // 蹲下 + 空手 = 打开配置界面（四面频率 + 出口过滤）。
        // 原来这里是在聊天栏/物品栏上方逐行播报「这一面绑了谁、过滤是什么」，
        // 改成界面后信息更全，也省得玩家对着 action bar 一行行看。
        if (!level.isClientSide) {
            tunnel.openConfigMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 手持物品右键：<b>不再</b>在这里设置过滤物品。
     * <p>
     * 过滤槽现在由 Create 的 sided filtering 负责（四个白点 + 悬停提示 + 点击放/清），
     * 它走的是 ValueBox 的客户端点击拦截，根本不经过这个方法。以前这里也设一遍，
     * 结果就是"点白点想放过滤物品，却被面级操作抢先"，所以整段移除，只保留让位给扳手/频率卡。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (WirelessBlockInteractions.deferToHeldItem(player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 待绑定的一步：玩家扳手点过、还没点第二个面的那个面。 */
    private record Pending(BlockPos pos, Direction face, long gameTime) {
    }

    /** 每个玩家一个"待绑定"记录；30 秒（600 tick）没点第二个面就自动过期。 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final long PENDING_TIMEOUT = 600L;

    /**
     * 扳手右键 = 绑定/解绑，但<b>不吞掉</b>扳手本身的功能：
     * <ol>
     *   <li>这个面已经有绑定 → <b>解绑</b>（对端那一面一起解掉）；</li>
     *   <li>没有绑定，且手里有"上一个面" → 两面<b>建立双向绑定</b>；</li>
     *   <li>没有绑定，也没有上一个面 → 记下这个面，提示去点另一个隧道的面。</li>
     * </ol>
     * <b>让位规则</b>（没得绑就让扳手干回本职，见 {@link #handBackToWrench}）：
     * <ul>
     *   <li>上下两面不接传送带 → 直接交还；</li>
     *   <li>附近 {@value #BIND_SCAN_RADIUS} 格内没有第二台隧道 → 直接交还。</li>
     * </ul>
     * 潜行 + 扳手同样交还 Create 原生行为（CLOSED ↔ WINDOW 视窗）。
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof WarpTunnelBlockEntity tunnel) || player == null) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            // 潜行 + 扳手交还 Create 原生行为：切换 CLOSED ↔ WINDOW（隧道视窗）。
            // 以前这里被"清空该面过滤"占用，导致视窗机制整条被覆盖；清过滤现在归过滤槽自己管
            // （Create 的 ValueBox：空手右键点白点 = 清空）。
            return super.onWrenched(state, context);
        }

        UUID id = player.getUUID();
        Pending pending = PENDING.get(id);
        long now = level.getGameTime();
        if (pending != null && now - pending.gameTime() > PENDING_TIMEOUT) {
            PENDING.remove(id);
            pending = null;
        }

        // 有"上一个面"在等第二次点击时，绑定流程优先；否则先判断这里到底有没有得绑。
        if (pending == null) {
            if (!bindableFace(face)) {
                // 上下两面不接传送带：这里没有"绑定"这回事，让扳手去切换模型/视窗。
                return handBackToWrench(state, context, player, Component.translatable(
                        "block.avaritia_singularity_stress.warp_tunnel.link.not_bindable", faceName(face)));
            }
            // 【实机修正 2026-11-04】这里原来还有一条 `!tunnel.hasBeltOn(face)` 的硬限制：
            // 那一面没有传送带进出就交还扳手、既不记 Pending 也不绑定。现场后果是：
            // 两台隧道在东西向、带子却是南北向，玩家扳手点"朝对端的那一面"（东/西）时，
            // hasBeltOn 为 false → 扳手被交还去切视窗 → 玩家以为绑上了，服务端 links 始终为空
            // （日志 outgoing=[NONE]）。
            // 这条限制本来就不成立：绑定面只是"逻辑出口"，真正的投放发生在**对端**那一面
            // （releaseAt(peer, link.face(), ...)），本机这一面有没有带子并不影响投递。
            // 所以只保留"必须是侧面"这一条，其余交给玩家。
        }

        if (pending == null) {
            if (tunnel.getFaceFrequency(face) != WarpTunnelBlockEntity.NO_FACE_FREQUENCY) {
                tunnel.unbind(face);
                // 带坐标：多台隧道并排时，只有坐标能说清是哪一台
                player.displayClientMessage(Component.translatable(
                        "block.avaritia_singularity_stress.warp_tunnel.link.unbound",
                        faceName(face), pos.toShortString()), true);
                return InteractionResult.SUCCESS;
            }
            PENDING.put(id, new Pending(pos, face, now));
            player.displayClientMessage(Component.translatable(
                    "block.avaritia_singularity_stress.warp_tunnel.link.selected",
                    faceName(face), pos.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        if (pending.pos().equals(pos) && pending.face() == face) {
            PENDING.remove(id);
            player.displayClientMessage(Component.translatable(
                    "block.avaritia_singularity_stress.warp_tunnel.link.cancel"), true);
            return InteractionResult.SUCCESS;
        }
        if (pending.pos().equals(pos)) {
            PENDING.put(id, new Pending(pos, face, now));
            player.displayClientMessage(Component.translatable(
                    "block.avaritia_singularity_stress.warp_tunnel.link.selected",
                    faceName(face), pos.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pending.pos()) instanceof WarpTunnelBlockEntity other)) {
            PENDING.remove(id);
            player.displayClientMessage(Component.translatable(
                    "block.avaritia_singularity_stress.warp_tunnel.link.gone"), true);
            return InteractionResult.SUCCESS;
        }
        // 配对＝复制第一面的频率（用户定的语义）；第二面原有的频率会被覆盖，不需要先解绑
        WarpTunnelBlockEntity.bind(other, pending.face(), tunnel, face);
        PENDING.remove(id);
        // 带坐标 + 箭头：箭头指向物品流向（左=发送方，右=接收方），
        // 方便玩家（和我）一眼看出这台是送还是收 —— 单向绑定最容易搞混的就是这一点。
        player.displayClientMessage(Component.translatable(
                "block.avaritia_singularity_stress.warp_tunnel.link.done",
                faceName(pending.face()),
                pending.pos().toShortString(),
                faceName(face),
                pos.toShortString()), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * 哪些面"可能"能绑定：只有四个侧面（上下两面不接传送带）。
     * <p>
     * 这只是快速排除；某个侧面到底能不能绑，还要看它是否真有传送带进出，
     * 由 {@link WarpTunnelBlockEntity#hasBeltOn(Direction)} 判定（父类按形状算出来的 sides）。
     */
    private static boolean bindableFace(Direction face) {
        return face.getAxis().isHorizontal();
    }

    /**
     * 没得绑时把扳手交还父类（{@code BeltTunnelBlock.onWrenched}：CLOSED ↔ WINDOW）。
     * 父类也没可切的状态（返回 PASS）时，至少给一句话，别让玩家以为扳手坏了。
     */
    private InteractionResult handBackToWrench(BlockState state, UseOnContext context, Player player,
            Component fallbackMessage) {
        InteractionResult result = super.onWrenched(state, context);
        if (!result.consumesAction()) {
            player.displayClientMessage(fallbackMessage, true);
        }
        return InteractionResult.SUCCESS;
    }

    /** 面的可读名（用于提示文本）：用现有 lang 的方位键。 */
    private static Component faceName(Direction face) {
        return switch (face) {
            case NORTH -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.north");
            case EAST -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.east");
            case SOUTH -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.south");
            case WEST -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.west");
            case UP -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.up");
            case DOWN -> Component.translatable("block.avaritia_singularity_stress.warp_tunnel.dir.down");
        };
    }

}
