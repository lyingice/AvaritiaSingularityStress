package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelFilterSlot;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.SidedFilteringBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * B15 空间折跃隧道·方块实体。
 *
 * <h2>一句话规则</h2>
 * 面与面用扳手连起来后：物品从<b>输入面</b>进，按绑定的<b>输出面</b>分流出去；
 * 每个面最多挂<b>一个过滤槽</b>（空槽 = 收所有物品），命中哪个输出面的过滤就送到哪个面。
 *
 * <h2>数据</h2>
 * <ul>
 *   <li>{@link #links}：面 → 对端（坐标 + 对端面 + 方向）。{@code incoming=true} 只是反向指针，
 *       表示"这个面是别人输出到我这里的终点"，本方块<b>不</b>拿它当出口用，避免物品来回弹。</li>
 *   <li>{@link #filters}：面 → 过滤物品（一个面一个槽）。</li>
 *   <li>{@link #queue}：缓冲队列（4 堆）。出口全忙时物品留在这里，满了就拒收，
 *       传送带会照常停在隧道前（Create 原生行为）。</li>
 * </ul>
 *
 * <h2>继承原因</h2>
 * Create 的传送带拦截写死了 {@code instanceof BrassTunnelBlockEntity}
 * （{@code BeltTunnelInteractionHandler:52}）与 {@code instanceof BrassTunnelBlock}（{@code :123}）。
 */
public class WarpTunnelBlockEntity extends BrassTunnelBlockEntity
        implements committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable {

    /** 缓冲队列容量（堆）。 */
    public static final int MAX_QUEUED_STACKS = 4;

    /** 四个水平面，顺序即分流优先级：北 → 东 → 南 → 西。 */
    public static final Direction[] FACES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** 单次投递上限。 */
    private static final int BATCH = 16;

    /** 投递节流（tick）。 */
    private static final int TRANSFER_INTERVAL = 5;

    /**
     * 每个面的频率（B15 新配对机制，取代旧的「扳手两点绑定」{@code Link}）。
     * <p>
     * 语义：两个面频率相同 = 它们配对；物品从 A 面进 → 折跃到 B 面对应的位置。
     * 未设置的面不在 Map 里（{@link #NO_FACE_FREQUENCY}）。
     */
    private final Map<Direction, Integer> faceFreqs = new EnumMap<>(Direction.class);
    /**
     * 每个出向面的轮询游标：同频多台终面都能收同一件货时，轮流从不同的终面开始试，
     * 避免"永远只喂索引里的第一台"（否则并流时另一条线永远闲着）。
     */
    private final Map<Direction, Integer> peerCursor = new EnumMap<>(Direction.class);
    /**
     * 出口过滤（GUI 复选框，默认勾上）。
     * <p>
     * 勾上：准入 = 本面过滤 ∪ 终面过滤，其中<b>终面过滤命中的折跃</b>、只是本面过滤命中的走本地。
     * 不勾：只按本面过滤，命中即折跃（旧行为）。
     */
    private boolean exportFilter = true;
    /** 有传送带支持的面（取自父类 updateTunnelConnections 算出的 sides）。 */
    private final Set<Direction> beltSides = EnumSet.noneOf(Direction.class);
    /** 四个面的过滤槽（Create 的 sided filtering；渲染与点击都交给它）。 */
    @Nullable
    private SidedFilteringBehaviour sidedFilter;
    /**
     * 队列条目：物品 + 入口面。
     * <p>
     * 只有「判定为折跃」的物品才进队列；不折跃的在 {@code absorbIncoming} 里直接本地投放。
     * （2026-11-05 曾试过「全部入队再按目标分发」，实机反而更差：本地物品占满 4 堆队列，
     *   {@code canTakeItems} 变 false，带子直接被堵死，故改回。）
     */
    private record Queued(ItemStack stack, Direction enteredFrom) {
    }

    private final List<Queued> queue = new ArrayList<>();

    /** 待吐出的一条：物品 + 从哪一面吐（= 配对时对端被绑定的那一面）。 */
    private record PendingOut(ItemStack stack, Direction face) {
    }

    /** 待吐出缓冲上限（堆数）。满了就让发送端把货留在自己队列里重试。 */
    private static final int MAX_PENDING_OUTPUT = 4;

    /**
     * 折跃过来的物品在这里等吐出。
     * <p>
     * <b>和入口缓冲 {@code stackToDistribute} 是两回事</b>：那个是带子塞进来的入口，
     * 放进那里会被重新判定一遍（可能折跃回去）。这条只出不进，纯粹等 {@code tickPendingOutput} 投放。
     */
    private final List<PendingOut> pendingOutput = new ArrayList<>();

    private String status = "idle";

    /**
     * B15 分流版：物品是从本机<b>哪个面</b>进来的（= 带子推进的方向）。
     * <p>
     * Create 的 {@code BeltTunnelInteractionHandler:57} 调用
     * {@code setStackToDistribute(stack, movementFacing.getOpposite())} 时已经带了这个信息，
     * 但父类 {@code getStackToDistribute()} 不会把它还给我们。所以这里自己记一份 ——
     * 整套「出向面过滤 / 终点面过滤 / 本地流出」的判定都以它为基准：
     * <b>出向面 = 入口面的对面</b>（物品进东边，就从西边出去）。
     */
    @Nullable
    private Direction enteredFrom;

    @Override
    public void setStackToDistribute(ItemStack stack, @Nullable Direction enteredFrom) {
        // 只在有值时才覆盖：本类内部把"剩余物品"塞回去时会传 null，
        // 那次不能把来源面清掉，否则后续判定会失去依据。
        if (enteredFrom != null) {
            this.enteredFrom = enteredFrom;
        }
        super.setStackToDistribute(stack, enteredFrom);
    }

    /** 来源面兜底：拿不到方向时，按隧道的水平轴推一个（东西轴取东、南北轴取北）。 */
    private Direction enteredFromOrDefault() {
        if (enteredFrom != null) {
            return enteredFrom;
        }
        return getBlockState().getValue(WarpTunnelBlock.HORIZONTAL_AXIS) == Direction.Axis.X
                ? Direction.EAST
                : Direction.NORTH;
    }

    public WarpTunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static WarpTunnelBlockEntity create(BlockPos pos, BlockState state) {
        return new WarpTunnelBlockEntity(ModBlockEntities.WARP_TUNNEL.get(), pos, state);
    }

    // ===================== 父类相关 =====================

    @Override
    public void initialize() {
        super.initialize();
        // 读 NBT 时 level 可能尚未挂到 BE，read() 中的 indexAdd 会因此跳过；首次初始化时重建，
        // 否则磁盘上虽有同频面，运行时 peerOf 仍会返回 null（依据 Create SmartBlockEntity.java:64-78）。
        if (level != null && !level.isClientSide) {
            reindexAll();
        }
    }

    /**
     * <b>【崩溃修复 2026-11-04】不能空实现了。</b>
     * <p>
     * 父类 {@code BrassTunnelBlockEntity.tick()} 在 {@code stackToDistribute} 非空且下方带子在转时，
     * 会走到 {@code if (selectionMode.get() == SelectionMode.SYNCHRONIZE)}
     * （{@code BrassTunnelBlockEntity.java:148}），而 {@code selectionMode} 只由父类自己的
     * {@code addBehaviours} 创建（{@code :110}）。
     * <p>
     * 原先这里是空实现（本意是"出口由面级绑定决定，不要那个多出口滚轮"），结果就是
     * <b>只要吸收没有完全清空 {@code stackToDistribute}（队列满 / 本地流出失败），下一 tick 必定 NPE 崩服</b>：
     * {@code crash-2026-11-04_20.29.31-server.txt} 的
     * {@code NullPointerException: ... "this.selectionMode" is null at gatherValidOutputs(:501)}。
     * <p>
     * 现在改为：调父类创建它（保证非 null），随后 {@code onlyActiveWhen(() -> false)} 把它<b>停用</b> ——
     * 既不会在世界里显示那个滚轮、也不会抢玩家点击，只是让父类 tick 拿到一个"永不被选中"的对象。
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        if (selectionMode != null) {
            selectionMode.onlyActiveWhen(() -> false);
        }
    }

    /** 只保留父类那个永不渲染的占位过滤（父类 read() 需要 filtering 非空）。 */
    @Override
    public void addBehavioursDeferred(List<BlockEntityBehaviour> behaviours) {
        super.addBehavioursDeferred(behaviours);
    }

    @Override
    protected SidedFilteringBehaviour createSidedFilter() {
        // 【B15 四个白点】父类用的是 BrassTunnelFilterSlot（Create 黄铜隧道的那套槽位定位），
        // 但父类的 filtering 字段是包内可见的，我们读不到，所以这里自己接一份引用。
        // 槽位定位器必须是 BrassTunnelFilterSlot 而不是"隐藏版"：白色描边（FilteringRenderer.tick 的
        // ValueBox）、悬停提示、点击放/清过滤物品，全靠它是否 shouldRender/testHit。
        // 证据：Create 的 BeltTunnelBlockEntity.addBehavioursDeferred → createSidedFilter → BrassTunnelFilterSlot。
        return sidedFilter = new SidedFilteringBehaviour(this, new BrassTunnelFilterSlot(), (side, filter) -> filter,
                side -> side.getAxis().isHorizontal());
    }

    /** 我们自己的 filtering 引用（父类字段包内可见，跨包拿不到）。 */
    @Nullable
    public SidedFilteringBehaviour sidedFilter() {
        return sidedFilter;
    }

    /**
     * 清空 {@code sides} 掐断父类分发；{@code flaps} 保留（软门帘渲染要用）。
     * <p>
     * 清之前先抄一份 {@link #beltSides}：父类算出来的 {@code sides} 就是"这一面有传送带进出"
     * （由 {@code SHAPE} + {@code HORIZONTAL_AXIS} 推出来，直隧道只有轴向两面、T/CROSS 再多两面），
     * 正好拿来判断某个面能不能绑定。
     */
    @Override
    public void updateTunnelConnections() {
        super.updateTunnelConnections();
        beltSides.clear();
        beltSides.addAll(sides);
        sides.clear();
    }

    /** 这一面是否有传送带支持（能挂带子 = 能绑定）。 */
    public boolean hasBeltOn(Direction face) {
        return beltSides.contains(face);
    }

    /**
     * Create 的 SmartBlockEntity.setRemoved() 在区块卸载时也会走 invalidate，不能只挂在 remove()
     * 上，否则全局频率索引会留下已失效的面（依据 Create SmartBlockEntity.java:134-146）。
     */
    @Override
    public void invalidate() {
        if (level != null && !level.isClientSide) {
            indexRemoveAll();
        }
        super.invalidate();
    }

    @Override
    public void destroy() {
        if (level != null) {
            for (Queued entry : queue) {
                if (!entry.stack().isEmpty()) {
                    Block.popResource(level, worldPosition, entry.stack());
                }
            }
            for (PendingOut entry : pendingOutput) {
                if (!entry.stack().isEmpty()) {
                    Block.popResource(level, worldPosition, entry.stack());
                }
            }
            queue.clear();
            pendingOutput.clear();
            // Create 的 BrassTunnelBlockEntity.destroy() 会返还 stackToDistribute（其源码:746-750），
            // 这里额外返还两套 B15 缓冲，避免替换方块时待吐出物品凭空消失。
        }
        super.destroy();
    }

    // ===================== 频率（按面） =====================

    /** 该面没设频率。 */
    public static final int NO_FACE_FREQUENCY = -1;

    /** 频率上限，与 {@code IFrequencyBindable.MAX_FREQUENCY} 一致。 */
    public static final int MAX_FACE_FREQUENCY = 9999;

    /** 「频率 → 所有持有该频率的面」的全局索引。只有服务端维护，客户端不需要。 */
    private static final Map<Integer, List<FaceRef>> FREQ_INDEX = new HashMap<>();

    /** 索引里的一条记录：哪个方块的哪个面。 */
    public record FaceRef(BlockPos pos, Direction face) {
    }

    public int getFaceFrequency(Direction face) {
        return faceFreqs.getOrDefault(face, NO_FACE_FREQUENCY);
    }

    public Map<Direction, Integer> getFaceFrequencies() {
        return faceFreqs;
    }

    /**
     * 写一个面的频率并维护同频索引。{@code freq < 0} 表示清除。
     * <p>
     * 清除时会顺手清空该面过滤槽的<b>内容</b>（保留槽位）。保留槽位这点是踩过坑的：
     * Create 的 {@code SidedFilteringBehaviour.updateFilterPresence()} 只在构造时跑一次，
     * 一旦 removeFilter 掉，之后 setFilter 会静默失败（见排错表 T23）。
     */
    public void setFaceFrequency(Direction face, int freq) {
        int old = getFaceFrequency(face);
        int clamped = freq < 0 ? NO_FACE_FREQUENCY : Math.max(0, Math.min(MAX_FACE_FREQUENCY, freq));
        if (old == clamped) {
            return;
        }
        indexRemove(face, old);
        if (clamped == NO_FACE_FREQUENCY) {
            faceFreqs.remove(face);
            if (sidedFilter != null && sidedFilter.get(face) != null) {
                sidedFilter.get(face).setFilter(ItemStack.EMPTY);
            }
        } else {
            faceFreqs.put(face, clamped);
            indexAdd(face, clamped);
        }
        if (sidedFilter != null) {
            sidedFilter.updateFilterPresence();
        }
        setChanged();
        sendData();
    }

    /**
     * 配对：让 B 的 faceB 与 A 的 faceA 同频。
     * <p>
     * A 没设过频率时自动分配一个未占用的编号（用户定的语义：复制第一面的频率）。
     */
    public static void bind(WarpTunnelBlockEntity a, Direction faceA, WarpTunnelBlockEntity b, Direction faceB) {
        int freq = a.getFaceFrequency(faceA);
        if (freq == NO_FACE_FREQUENCY) {
            freq = a.allocateFrequency();
            a.setFaceFrequency(faceA, freq);
        }
        b.setFaceFrequency(faceB, freq);
    }

    public boolean isExportFilter() {
        return exportFilter;
    }

    /** 切换出口过滤（GUI 复选框 / 封包调用）。 */
    public void setExportFilter(boolean value) {
        if (exportFilter == value) {
            return;
        }
        exportFilter = value;
        setChanged();
        sendData();
    }

    /** 蹲下 + 空手右键打开配置界面（服务端调用）。 */
    public void openConfigMenu(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = getBlockPos();
        // 【必须带 buf 写入器】客户端的 WarpTunnelConfigMenu 构造器要 buf.readBlockPos()；
        // 只给菜单工厂不给 writer 的话客户端收到空包，读坐标越界 → 直接
        // 「连接已丢失 / 网络协议错误」（实机 2026-11-05 就是这么崩的）。
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new WarpTunnelConfigMenu(
                                committee.nova.avaritia_singularity_stress.init.registry.ModMenus.WARP_TUNNEL_CONFIG
                                        .get(),
                                id, inventory, this),
                        Component.translatable("block.avaritia_singularity_stress.warp_tunnel.config")),
                buf -> buf.writeBlockPos(pos));
    }

    // ===================== IFrequencyBindable（接入频率卡） =====================

    /**
     * 整机视角的频率：返回第一个设过的面。
     * <p>
     * 隧道的频率是<b>按面</b>的；整机接口只是为了兼容频率卡那套「一台机器一个频率」的调用，
     * 真正读写请用 {@link #getFaceFrequency} / {@link #setFaceFrequency}。
     */
    @Override
    public int getFrequency() {
        for (Direction face : FACES) {
            int value = getFaceFrequency(face);
            if (value != NO_FACE_FREQUENCY) {
                return value;
            }
        }
        return committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable.NO_FREQUENCY;
    }

    /** 整机写入：只写到「有软帘门」的面（没有带子进出的面不该被配对）。 */
    @Override
    public void setFrequency(int frequency) {
        for (Direction face : FACES) {
            if (hasBeltOn(face)) {
                setFaceFrequency(face, frequency);
            }
        }
    }

    @Override
    public int getFrequency(Direction face) {
        return face == null ? getFrequency() : getFaceFrequency(face);
    }

    @Override
    public void setFrequency(Direction face, int frequency) {
        if (face == null) {
            setFrequency(frequency);
            return;
        }
        if (hasBeltOn(face)) {
            setFaceFrequency(face, frequency);
        }
    }

    /** 隧道没有「私有频道」概念：谁设同一个频率谁就能配对。 */
    @Override
    public ChannelVisibility getVisibility() {
        return ChannelVisibility.PUBLIC;
    }

    @Override
    public void setVisibility(ChannelVisibility visibility) {
        // 隧道不区分频道可见性
    }

    @Override
    public UUID getOwner() {
        return null;
    }

    @Override
    public void setOwner(UUID owner) {
        // 隧道没有所有者概念
    }

    /** 生成一个没被占用的频率（1..9999）。 */
    private int allocateFrequency() {
        RandomSource random = level == null ? RandomSource.create() : level.random;
        for (int i = 0; i < 200; i++) {
            int candidate = 1 + random.nextInt(MAX_FACE_FREQUENCY);
            if (!FREQ_INDEX.containsKey(candidate)) {
                return candidate;
            }
        }
        return 1;
    }

    /** 解除这一面的频率；同频的对端也一并解除（成对解绑，避免留下孤儿索引）。 */
    public void unbind(Direction face) {
        int freq = getFaceFrequency(face);
        if (freq == NO_FACE_FREQUENCY) {
            return;
        }
        setFaceFrequency(face, NO_FACE_FREQUENCY);
        List<FaceRef> list = FREQ_INDEX.get(freq);
        if (list == null) {
            return;
        }
        for (FaceRef ref : new ArrayList<>(list)) {
            if (ref.pos().equals(worldPosition)) {
                continue;
            }
            if (level != null && level.isLoaded(ref.pos())
                    && level.getBlockEntity(ref.pos()) instanceof WarpTunnelBlockEntity other) {
                other.setFaceFrequency(ref.face(), NO_FACE_FREQUENCY);
            }
        }
    }

    /**
     * 找这一面的对端：同频、非自己、已加载的第一个面。
     *
     * @return 找不到返回 null（= 这一面没配对，物品走本地）
     */
    @Nullable
    public FaceRef peerOf(Direction face) {
        List<FaceRef> peers = peersOf(face);
        return peers.isEmpty() ? null : peers.get(0);
    }

    /**
     * 这一面的<b>全部</b>同频终面（排除自己、排除未加载）。
     * <p>
     * <b>B15 分流</b>：原来 {@link #peerOf} 只取第一个，同频第二个终面永远收不到货——
     * 用户的场景是「1 号出向面同时喂 2 号（过滤铁锭）和 3 号（过滤下界合金锭）」，
     * 结果下界合金锭因为第一个终面过滤不匹配，直接被判成「不折跃」留在本地。
     * 现在按频率取全部候选，由 {@link #peerAccepts} 逐台判过滤。
     */
    List<FaceRef> peersOf(Direction face) {
        int freq = getFaceFrequency(face);
        if (freq == NO_FACE_FREQUENCY) {
            return List.of();
        }
        List<FaceRef> list = FREQ_INDEX.get(freq);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<FaceRef> result = new ArrayList<>(list.size());
        for (FaceRef ref : list) {
            if (ref.pos().equals(worldPosition) && ref.face() == face) {
                continue;
            }
            if (level != null && !level.isLoaded(ref.pos())) {
                continue;
            }
            result.add(ref);
        }
        return result;
    }

    private void indexAdd(Direction face, int freq) {
        // 只有服务端维护索引：客户端的 read 路径也会跑进来，但那边不需要也不该登记
        if (freq == NO_FACE_FREQUENCY || level == null || level.isClientSide) {
            return;
        }
        List<FaceRef> list = FREQ_INDEX.computeIfAbsent(freq, k -> new ArrayList<>());
        list.removeIf(r -> r.pos().equals(worldPosition) && r.face() == face);
        list.add(new FaceRef(worldPosition, face));
    }

    private void indexRemove(Direction face, int freq) {
        // 集成服务器客户端与服务端共用此静态 Map；客户端 read/sync 只能读面频率，不能删服务端索引。
        if (freq == NO_FACE_FREQUENCY || level == null || level.isClientSide) {
            return;
        }
        List<FaceRef> list = FREQ_INDEX.get(freq);
        if (list == null) {
            return;
        }
        list.removeIf(r -> r.pos().equals(worldPosition) && r.face() == face);
        if (list.isEmpty()) {
            FREQ_INDEX.remove(freq);
        }
    }

    /** 把当前所有面的频率重新登记进索引（读档 / 初始化后调用）。 */
    private void reindexAll() {
        indexRemoveAll();
        for (Map.Entry<Direction, Integer> entry : faceFreqs.entrySet()) {
            indexAdd(entry.getKey(), entry.getValue());
        }
    }

    private void indexRemoveAll() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Map.Entry<Direction, Integer> entry : faceFreqs.entrySet()) {
            indexRemove(entry.getKey(), entry.getValue());
        }
    }

    /** 该面的过滤物品（空 = 收所有物品）。数据存在 Create 的 sided filtering 里，不再另存一份。 */
    public ItemStack getFilter(Direction face) {
        return sidedFilter == null ? ItemStack.EMPTY : sidedFilter.getFilter(face);
    }

    public void setFilter(Direction face, ItemStack stack) {
        if (sidedFilter == null) {
            return;
        }
        sidedFilter.setFilter(face, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
        sendData();
    }

    // ===================== tick =====================

    @Override
    public void tick() {
        if (level == null) {
            return;
        }
        if (!level.isClientSide) {
            absorbIncoming();
        }
        super.tick();
        if (level.isClientSide) {
            clientStatusParticles();
            return;
        }
        if (getBlockState().getValue(WarpTunnelBlock.POWERED)) {
            setStatus("powered");
            return;
        }
        if (!queue.isEmpty() && level.getGameTime() % TRANSFER_INTERVAL == 0) {
            processHead();
        }
        // 待吐出缓冲：折跃过来的货从这里往对应面吐。和入口完全分开，吐不出去也不挡带子。
        if (!pendingOutput.isEmpty() && level.getGameTime() % TRANSFER_INTERVAL == 0) {
            tickPendingOutput();
        }
        updateStatusParticles();
    }

    /**
     * B15 分流版：能不能折跃。
     * <p>
     * <b>【实机修正 2026-11-04】不再死盯「入口面的对面」。</b>
     * 日志证据：物品从 {@code from=north} 进、{@code out=south}，但 {@code hasLink=false}——
     * 玩家的链路绑在了别的面（现场是物品进来那一面）。老逻辑只查 {@code links.get(outFace)}，
     * 于是「明明设了终点面过滤」却全程走本地。
     * <p>
     * 现在的顺序：
     * <ol>
     *   <li>先查出口面（标准情况，带子自然穿过的方向）；</li>
     *   <li>没命中就按 北→东→南→西 扫其余出向面，只要过滤允许就算数。</li>
     * </ol>
     * 这样玩家把链路绑在入口面、侧边面都能工作；真的绑了多条时按固定优先级取第一条。
     *
     * @param stack   待判定物品
     * @param outFace 出向面（= 入口面的对面），优先查它
     */
    /**
     * 能不能折跃（包内可见，供 {@code WarpSelfTest} 在专用服务端上直接断言）。
     */
    boolean shouldWarp(ItemStack stack, Direction entryFace) {
        // 【层次修正 v3】折跃只由「入口面」决定 —— 也就是传送带把货塞进来的那一面（玩家口中的"出向面"）。
        // 实机语义：X=(19,66,16) 的货从 west 进、west 未绑频率（只有 east 绑），那么它不该被折跃；
        // 之前判的是 outFace（入口的对面），正好反过来，于是"没绑的那面进来的货"被绑着的那面送走了。
        // 绑定的面同时仍然是对端在频道里能找到的端点（接收与释放用），两者不冲突。
        return linkAllows(entryFace, stack);
    }

    // ================= 已折跃标记 =================
    // 实机循环（X=(19,66,16) 过滤下界合金 → Z=(16,66,16) 过滤水晶矩阵）：
    // X 按"终面过滤命中"把水晶矩阵锭送给 Z；Z 沿带子吐出后，带子又把货送回 X，X 再送一次……
    // 过滤语义没错（Z 确实要水晶矩阵），错在"同一件货被同一条频率重复折跃"。
    // 修法：折跃时给那份副本打上频率标记，入口看到同频率的标记就判本地。
    // 与"时间窗口"的区别：无计时、不限制其它货、不影响吞吐，只约束这一件自身。
    private static final String KEY_WARP_FREQ = "WarpFreq";

    /** 这件货是不是已经在这条频率上折跃过（包内可见供自检断言）。 */
    static boolean alreadyWarpedOn(ItemStack stack, int freq) {
        if (freq == NO_FACE_FREQUENCY || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(KEY_WARP_FREQ) && tag.getInt(KEY_WARP_FREQ) == freq;
    }

    /** 给即将折跃出去的副本打标记（包内可见供自检断言）。 */
    static void markWarped(ItemStack stack, int freq) {
        if (freq == NO_FACE_FREQUENCY || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY_WARP_FREQ, freq));
    }

    /** 清标记：物品进容器＝这趟旅程结束，下次还能正常折跃。 */
    private static void clearWarpMark(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY_WARP_FREQ));
    }

    /**
     * 这一面是否接受这个物品进隧道（准入），以及它是否<b>折跃</b>而不是本地流出。
     * <p>
     * 新语义（B15 频率版，用户 2026-11-05 拍板）：
     * <ul>
     *   <li><b>出口过滤</b>勾上（默认）：准入 = 本面过滤 ∪ 终面过滤；
     *       其中<b>终面过滤命中的折跃</b>，只是本面过滤命中的走本地；</li>
     *   <li><b>出口过滤</b>不勾：只按本面过滤，命中即折跃（旧行为）。</li>
     * </ul>
     * 例：本面过滤金锭、终面过滤铁锭 → 金锭本地、铁锭折跃。
     */
    boolean linkAllows(Direction face, ItemStack stack) {
        // 同一件货在同一条频率上只折跃一次，避免"送达 → 沿带子回流 → 再送"的无限循环。
        // 注意：这不是时间窗口——没有计时、不影响别的货、不限制吞吐，只约束这一件自己。
        if (alreadyWarpedOn(stack, getFaceFrequency(face))) {
            return false;
        }
        // 有<b>任意一台</b>终面愿意收，就允许这件货入队/折跃（分流后不再要求唯一终面）
        for (FaceRef peer : peersOf(face)) {
            if (peerAccepts(face, peer, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单台终面是否收这件货 —— <b>整条链路上唯一的准入判定</b>。
     * <p>
     * {@link #linkAllows}（入队/准入）与 {@link #tryFace}（实际投递）都只调它，
     * 避免出现"判定说能折跃、投递时又被过滤挡住"的两套标准。
     */
    private boolean peerAccepts(Direction face, FaceRef peer, ItemStack stack) {
        if (exportFilter) {
            ItemStack peerFilter = peerFilterOf(peer);
            if (!peerFilter.isEmpty()) {
                // 终面设了过滤：以它为准（这是"分流"的依据）
                return ItemStack.isSameItem(peerFilter, stack);
            }
        }
        // 终面没设过滤（或关闭了出口过滤）→ 回落到本面过滤。
        // 这条回落不能省：漏了它会让"本面过滤铁锭"的隧道把泥土也判成可折跃（自检 case4 抓到的回归）。
        ItemStack outFilter = getFilter(face);
        return outFilter.isEmpty() || ItemStack.isSameItem(outFilter, stack);
    }

    /** 读对端那一面的过滤物品；对端未加载 / 不是隧道时按"空"处理（= 全收）。包内可见供自检用。 */
    ItemStack peerFilterOf(FaceRef peer) {
        if (level == null || !level.isLoaded(peer.pos())) {
            return ItemStack.EMPTY;
        }
        if (level.getBlockEntity(peer.pos()) instanceof WarpTunnelBlockEntity other) {
            return other.getFilter(peer.face());
        }
        return ItemStack.EMPTY;
    }

    /**
     * 吸收：<b>所有准入物品一律入队</b>，并记录它的目标类型（折跃 / 本地）。
     * <p>
     * 2026-11-05 起不再在吸收阶段区分「本地直接投放」——那样玩家会觉得
     * 「不折跃的物品根本没进隧道」。现在统一先入队，去向由 {@code processHead} 决定。
     * 放不下的（队列满）留在 {@code stackToDistribute}，交给 Create 的背压。
     */
    /** 复位：清队列 / 入口缓冲 / 待吐出缓冲（仅供自检在两个用例之间用）。 */
    void clearQueueForTest() {
        queue.clear();
        pendingOutput.clear();
        setStackToDistribute(ItemStack.EMPTY, Direction.NORTH);
        setChanged();
        sendData();
    }

    /** 吸收入口缓冲（包内可见，供 WarpSelfTest 端到端断言）。 */
    void absorbIncoming() {
        ItemStack incoming = getStackToDistribute();
        if (incoming.isEmpty()) {
            return;
        }
        Direction from = enteredFromOrDefault();
        Direction outFace = from.getOpposite();
        // v3：判定看入口面（货从哪进来）；outFace 只用于"不折跃时往哪边投放"
        boolean warp = shouldWarp(incoming, from);
        // [TEMP 定位日志 v4] 已注释。以后再出问题，把这段的 /* */ 去掉即可（搜 "[warp-debug]"）。
        // 它会打印：每台隧道四个面的频率与本面过滤、outFace 的全部对端及各自过滤、最终判定。
        /*
        {
            StringBuilder peers = new StringBuilder();
            for (FaceRef p : peersOf(outFace)) {
                ItemStack pf = peerFilterOf(p);
                peers.append(p.pos()).append('/').append(p.face())
                        .append("(filter=").append(pf.isEmpty() ? "air" : pf.getHoverName().getString()).append(") ");
            }
            StringBuilder allFaces = new StringBuilder();
            for (Direction f : FACES) {
                int freq = getFaceFrequency(f);
                ItemStack own = getFilter(f);
                allFaces.append(f).append('=').append(freq)
                        .append(own.isEmpty() ? "" : "(own=" + own.getHoverName().getString() + ")")
                        .append(' ');
            }
            AvaritiaSingularityStressMod.LOGGER.info(
                    "[warp-debug] DECIDE pos={} item={} from={} out={} faces=[{}] peersAtOut=[{}] export={} -> {}",
                    worldPosition, incoming.getItem(), from, outFace, allFaces.toString().trim(),
                    peers.length() == 0 ? "NONE" : peers.toString().trim(), exportFilter,
                    warp ? "WARP" : "LOCAL");
        }
        */

        if (!warp) {
            // ---------- 不折跃：直接本地投放，不进队列 ----------
            // 第三参数 false：禁止"塞回下方带子"兜底，否则物品会被塞回本机上游 → 无限循环
            int moved = placeAt(this, outFace, incoming.copyWithCount(Math.min(BATCH, incoming.getCount())), false);
            if (moved <= 0) {
                setStatus("exit_blocked");
                return;
            }
            int rest = incoming.getCount() - moved;
            setStackToDistribute(rest <= 0 ? ItemStack.EMPTY : incoming.copyWithCount(rest), from);
            setStatus("local");
            setChanged();
            sendData();
            return;
        }

        int moved = 0;
        while (moved < incoming.getCount() && queue.size() < MAX_QUEUED_STACKS) {
            int perStack = Math.min(incoming.getMaxStackSize(), incoming.getCount() - moved);
            ItemStack slice = incoming.copyWithCount(perStack);
            Queued tail = queue.isEmpty() ? null : queue.get(queue.size() - 1);
            // 同物品 + 同入口面才合并，否则该走不同去向的东西会被并成一堆
            if (tail != null && tail.enteredFrom() == from
                    && !tail.stack().isEmpty() && ItemStack.isSameItemSameComponents(tail.stack(), slice)
                    && tail.stack().getCount() < tail.stack().getMaxStackSize()) {
                int space = tail.stack().getMaxStackSize() - tail.stack().getCount();
                int add = Math.min(space, slice.getCount());
                tail.stack().grow(add);
                moved += add;
                if (add < slice.getCount()) {
                    break;
                }
            } else {
                queue.add(new Queued(slice, from));
                moved += perStack;
            }
        }
        if (moved <= 0) {
            return;
        }
        int rest = incoming.getCount() - moved;
        setStackToDistribute(rest <= 0 ? ItemStack.EMPTY : incoming.copyWithCount(rest), from);
        setChanged();
        sendData();
    }

    /**
     * 队头分流：<b>先走这件物品自己的出向面</b>（入口的对面），再按 北→东→南→西 顺位补。
     * <p>
     * 顺序很重要：一台机器同时绑了「北→A」「西→B」且两面过滤都为空时，
     * 老逻辑（纯 FACES 顺序）会把所有物品都塞给北面的 A，B 永远轮不到 —— 实机就是串线。
     * 先试 {@code entry.enteredFrom()}（玩家把链路绑在入口面是最常见的做法），
     * 再试入口对面，最后才按 北→东→南→西 顺位补。
     * 两条绑定各走各的。
     */
    /** 处理队头（包内可见，供 WarpSelfTest 端到端断言）。 */
    void processHead() {
        if (queue.isEmpty()) {
            return;
        }
        Queued entry = queue.get(0);
        ItemStack head = entry.stack();
        if (head.isEmpty()) {
            queue.remove(0);
            return;
        }

        // 队列里只有「判定为折跃」的物品（不折跃的在吸收阶段就本地投放了）

        // 【层次修正 v2】只投「出口面」＝入口的对面（与 shouldWarp 同一口径）。
        // 不再回退到入口面：那会让"刚被送来的货又从入口面被送回去"，
        // 两台互为对端时就是实机看到的两点闪烁。
        // v3：与 shouldWarp 同一口径——只投「入口面」，也就是货进来那一面所绑的频道
        Direction entryFace = entry.enteredFrom();
        boolean anyOutgoing = !peersOf(entryFace).isEmpty();

        if (tryFace(entryFace, head)) {
            return;
        }

        if (!anyOutgoing) {
            setStatus("unbound");
        }
        // 有绑定点但都不收/都堵：状态已由 trySend 写好，这里不再覆盖
    }

    /** 试一个面：在本面的全部同频终面里挑一台愿意收的投递。 */
    private boolean tryFace(Direction face, ItemStack head) {
        List<FaceRef> peers = peersOf(face);
        if (peers.isEmpty()) {
            return false;
        }
        int size = peers.size();
        int start = Math.floorMod(peerCursor.getOrDefault(face, 0), size);
        for (int i = 0; i < size; i++) {
            int index = (start + i) % size;
            FaceRef peer = peers.get(index);
            // 与 linkAllows 同一个判定：过滤不收的终面直接跳过，换下一台
            if (!peerAccepts(face, peer, head)) {
                continue;
            }
            if (trySend(face, peer, head)) {
                peerCursor.put(face, (index + 1) % size);
                return true;
            }
        }
        return false;
    }

    private boolean trySend(Direction face, FaceRef peer, ItemStack head) {
        if (!level.isLoaded(peer.pos())) {
            setStatus("peer_unloaded");
            return false;
        }
        if (!(level.getBlockEntity(peer.pos()) instanceof WarpTunnelBlockEntity other)) {
            setStatus("bad_peer");
            return false;
        }
        if (worldPosition.distSqr(peer.pos()) > WirelessConfig.warpTunnelRange() * WirelessConfig.warpTunnelRange()) {
            setStatus("out_of_range");
            return false;
        }
        // B 方案：不再由发送端直接往带子上投放，而是**先塞进对端的待吐出缓冲**，
        // 由对端在自己的 tick 里往「被绑定的那一面」吐。
        // 好处：发送端只要把货交出去就算成功（不依赖对端带子方向/容器/有没有被挡），
        // 对端吐不出来也只是它自己的缓冲在等，不会连累发送端的入口。
        int amount = Math.min(BATCH, head.getCount());
        // 打标记要在交给对端之前：对端之后会一路带着它走（吐出→带子→可能再回到某台隧道）
        ItemStack payload = head.copyWithCount(amount);
        markWarped(payload, getFaceFrequency(face));
        if (!other.receiveFromPeer(peer.face(), payload)) {
            return false;
        }
        // 对端收下了才算送出：扣发送端队列 + 播音效（原来这步在 releaseAt 的 finish 里，
        // 改成交给对端之后必须在这里补上，否则发送端队列永远清不掉 —— 自检里表现为 afterSend=1）
        finish(head, amount, other);
        return true;
    }

    /**
     * 接收折跃过来的物品，放进「待吐出」缓冲。
     * <p>
     * <b>刻意不碰 {@code stackToDistribute}</b>：那是带子把货塞进来的入口缓冲，
     * 一旦往里放，本机会把这堆货当成新来的重新判定一次（可能又折跃回去）。
     * 这条缓冲是纯出口语义：只往 {@code face} 那一面吐，不参与任何准入判定。
     *
     * @param face  对端被绑定的那一面（物品从这个方向出去）
     * @param stack 要收的物品
     * @return false = 缓冲满，发送端应当把物品留在自己队列里重试
     */
    boolean receiveFromPeer(Direction face, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        // 同面同物品且未满 → 并进已有那一堆，省缓冲位
        for (PendingOut out : pendingOutput) {
            if (out.face() == face && ItemStack.isSameItemSameComponents(out.stack(), stack)
                    && out.stack().getCount() + stack.getCount() <= out.stack().getMaxStackSize()) {
                out.stack().grow(stack.getCount());
                setChanged();
                sendData();
                return true;
            }
        }
        if (pendingOutput.size() >= MAX_PENDING_OUTPUT) {
            return false;
        }
        pendingOutput.add(new PendingOut(stack.copy(), face));
        setChanged();
        sendData();
        return true;
    }

    /**
     * 把待吐出缓冲里的物品往对应面投放（每个 tick 调一次，内部按 TRANSFER_INTERVAL 节流）。
     * <p>
     * 投放失败<b>不丢物品、也不堵入口</b>：留着下次再试，状态标 {@code output_blocked}。
     */
    /**
     * 折跃货该往哪一面吐。
     * <p>
     * <b>实机症状</b>：收货那台按自己“配置面”吐，结果吐到了自己脚底那条带子上，
     * 又被自己吸回来 → 物品卡在隧道口反复判定，永远出不去（截图里红圈就是它）。
     * <p>
     * 玩家语义：<b>货从隧道出来就该跟着带子走</b>。所以只要脚下有带子，就以带子的前进方向为准，
     * 把货插到<b>下游那一段</b>，它自然被带走；没有带子时才回退到配置面。
     */
    private Direction releaseFace(Direction configured) {
        BeltBlockEntity belt = BeltHelper.getSegmentBE(level, worldPosition.below());
        if (belt != null) {
            Direction movement = belt.getMovementFacing();
            if (movement != null) {
                return movement;
            }
        }
        return configured;
    }

    private void tickPendingOutput() {
        if (pendingOutput.isEmpty()) {
            return;
        }
        PendingOut out = pendingOutput.get(0);
        int moved = placeAt(this, releaseFace(out.face()),
                out.stack().copyWithCount(Math.min(BATCH, out.stack().getCount())), true);
        if (moved <= 0) {
            setStatus("output_blocked");
            return;
        }
        out.stack().shrink(moved);
        if (out.stack().isEmpty()) {
            pendingOutput.remove(0);
        }
        setStatus("receiving");
        setChanged();
        sendData();
    }

    /** 待吐出的条数（自检 / 状态显示用）。 */
    public int getPendingOutputCount() {
        return pendingOutput.size();
    }

    /**
     * 在对端的面释放物品：
     * <ol>
     *   <li>该面外侧传送带的输入口；</li>
     *   <li>该面对应的容器；</li>
     *   <li>该面正是带子前进方向且无实心阻挡 → 从带尾抛出；</li>
     *   <li>兜底：对端下方那条传送带。</li>
     * </ol>
     */
    private boolean releaseAt(WarpTunnelBlockEntity peer, Direction face, ItemStack head) {
        int moved = placeAt(peer, face, head.copyWithCount(Math.min(BATCH, head.getCount())), true);
        if (moved <= 0) {
            setStatus("exit_blocked");
            return false;
        }
        finish(head, moved, peer);
        return true;
    }

    /**
     * 把 {@code attempt} 投放到 {@code target} 的 {@code face} 外侧，返回实际搬走的数量。
     * <p>
     * <b>为什么从 {@code releaseAt} 里抽出来</b>：B15 分流版新增了「本地流出」——
     * 物品不折跃时要从<b>本机</b>的出向面带出去，投放的三级逻辑（带子输入口 → 容器 →
     * 带尾抛出）与折跃完全一致，只有"扣谁的账、播不播音效"不同。
     *
     * @param allowBeltFallback 是否允许最后一级"塞进下方传送带"。
     *                          <b>本地流出必须传 {@code false}</b>：那一级会把物品塞回带子库存，
     *                          而本机下方的带子正是物品<b>刚流过来的那条</b> —— 一旦塞回去，
     *                          物品会再次经过隧道、再次被判本地流出，形成无限循环。
     *                          折跃时对端是另一台机器，塞进它下方的带子不会绕回本机，所以允许。
     */
    private int placeAt(WarpTunnelBlockEntity target, Direction face, ItemStack attempt, boolean allowBeltFallback) {
        BlockPos beltPos = target.getBlockPos().below();
        BeltBlockEntity belt = BeltHelper.getSegmentBE(level, beltPos);

        // 【实机修正 2026-11-05】本地流出优先走「隧道脚下这条带子本身的输入口」：
        // face 就是物品的行进方向，插进去等于把它放到下游，视觉上就是「像普通隧道一样穿过去」。
        // 旧实现只找 beltPos.relative(face)（外侧那一格），逆向时会被 canInsertFromSide 拒绝，
        // 于是退化成 eject 把物品弹到带子旁边——玩家看到的就是「金锭根本进不去」。
        if (!allowBeltFallback && belt != null) {
            DirectBeltInputBehaviour ownInput = BlockEntityBehaviour.get(level, beltPos,
                    DirectBeltInputBehaviour.TYPE);
            if (ownInput != null && ownInput.canInsertFromSide(face)) {
                int moved = insertVia(ownInput, face, attempt);
                if (moved > 0) {
                    return moved;
                }
            }
        }

        if (belt != null) {
            DirectBeltInputBehaviour input = BlockEntityBehaviour.get(level, beltPos.relative(face),
                    DirectBeltInputBehaviour.TYPE);
            if (input != null && input.canInsertFromSide(face)) {
                int moved = insertVia(input, face, attempt);
                if (moved > 0) {
                    return moved;
                }
            }
        }

        IItemHandler container = level.getCapability(Capabilities.ItemHandler.BLOCK,
                target.getBlockPos().relative(face), face.getOpposite());
        if (container != null) {
            // 进容器＝这趟折跃结束，清标记，否则这件货以后永远不能再参与本频道的折跃
            ItemStack forContainer = attempt.copy();
            clearWarpMark(forContainer);
            int moved = insertInto(container, forContainer);
            if (moved > 0) {
                return moved;
            }
        }

        if (belt != null && face == belt.getMovementFacing()
                && !BlockHelper.hasBlockSolidSide(level.getBlockState(beltPos.relative(face)), level,
                        beltPos.relative(face), face.getOpposite())) {
            eject(belt, face, attempt.copy());
            return attempt.getCount();
        }

        if (allowBeltFallback && belt != null) {
            IItemHandler beltInv = level.getCapability(Capabilities.ItemHandler.BLOCK, beltPos, null);
            if (beltInv != null) {
                int moved = insertInto(beltInv, attempt);
                if (moved > 0) {
                    return moved;
                }
            }
        }

        if (!allowBeltFallback && belt != null) {
            // 【实机修正 2026-11-05】本地流出的最后兜底：三级投放全失败时，朝出向面把物品抛出，
            // 让它落在带子上/旁边继续自然流动。
            // 为什么必须兜底：本地流出失败会让物品一直留在 stackToDistribute 里 → canTakeItems()
            // 恒 false → 后面再来的物品一个都吸不进来（实机症状：出向过滤金锭、终面铁锭时，
            // 金锭把入口堵住，本该折跃的铁锭永远进不去）。
            // 用 eject 而不是塞回带子库存，是因为塞回可能落到上游形成「流出→再流进」的死循环。
            eject(belt, face, attempt.copy());
            return attempt.getCount();
        }
        return 0;
    }

    private void finish(ItemStack head, int moved, WarpTunnelBlockEntity peer) {
        dequeueHead(moved);
        setStatus("warping");
        peer.warpFx();
    }

    /** 塞进传送带输入口，返回实际搬走的数量。 */
    private int insertVia(DirectBeltInputBehaviour input, Direction side, ItemStack attempt) {
        ItemStack rejected = input.handleInsertion(attempt.copy(), side, true);
        if (rejected == null) {
            return 0;
        }
        int accepted = attempt.getCount() - rejected.getCount();
        if (accepted <= 0) {
            return 0;
        }
        ItemStack leftover = input.handleInsertion(attempt.copyWithCount(accepted), side, false);
        return accepted - (leftover == null ? 0 : leftover.getCount());
    }

    /** 塞进任意物品容器，返回实际搬走的数量（先模拟，再提交）。 */
    private int insertInto(IItemHandler target, ItemStack attempt) {
        ItemStack rejected = ItemHandlerHelper.insertItemStacked(target, attempt.copy(), true);
        int accepted = attempt.getCount() - rejected.getCount();
        if (accepted <= 0) {
            return 0;
        }
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, attempt.copyWithCount(accepted), false);
        return accepted - leftover.getCount();
    }

    private void warpFx() {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.35f, 1.4f);
        spawnDust(0x8A2BE2, 10);
    }

    private void spawnDust(int rgb, int amount) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(new DustParticleOptions(new org.joml.Vector3f(
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.0f),
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                amount, 0.25, 0.05, 0.25, 0.01);
    }

    private void eject(BeltBlockEntity belt, Direction face, ItemStack stack) {
        BeltBlockEntity controller = belt.getControllerBE();
        if (controller == null) {
            return;
        }
        float beltSpeed = belt.getDirectionAwareBeltMovementSpeed();
        float speed = Math.max(Math.abs(beltSpeed), 1 / 8f);
        int extra = beltSpeed > 0 ? 1 : 0;
        Vec3 outPos = BeltHelper.getVectorForOffset(controller, belt.index + extra);
        Vec3 outMotion = Vec3.atLowerCornerOf(face.getNormal()).scale(speed).add(0, 1 / 8f, 0);
        outPos = outPos.add(outMotion.normalize());
        ItemEntity entity = new ItemEntity(level, outPos.x, outPos.y + 6 / 16f, outPos.z, stack);
        entity.setDeltaMovement(outMotion);
        entity.setDefaultPickUpDelay();
        entity.hurtMarked = true;
        level.addFreshEntity(entity);
        flap(face, true);
    }

    // ===================== 队列 / 状态 =====================

    private void dequeueHead(int amount) {
        if (queue.isEmpty() || amount <= 0) {
            return;
        }
        ItemStack head = queue.get(0).stack();
        head.shrink(amount);
        if (head.isEmpty()) {
            queue.remove(0);
        }
        setChanged();
        sendData();
    }

    public List<ItemStack> getQueueView() {
        List<ItemStack> view = new ArrayList<>(queue.size());
        for (Queued entry : queue) {
            view.add(entry.stack());
        }
        return view;
    }

    public int getQueuedCount() {
        int total = 0;
        for (Queued entry : queue) {
            total += entry.stack().getCount();
        }
        return total;
    }

    public String getStatus() {
        return status;
    }

    private void setStatus(String key) {
        if (status.equals(key)) {
            return;
        }
        status = key;
        setChanged();
        sendData();
    }

    private int statusColor() {
        return switch (status) {
            case "warping" -> 0x8A2BE2;
            case "receiving" -> 0x7CD7FF;
            case "unbound", "no_peer", "peer_unloaded", "bad_peer", "out_of_range" -> 0xFFAA00;
            case "exit_blocked", "output_blocked" -> 0xFF5555;
            default -> -1;
        };
    }

    private void clientStatusParticles() {
        int rgb = statusColor();
        if (rgb < 0 || level.getGameTime() % 6 != 0) {
            return;
        }
        var random = level.random;
        level.addParticle(new DustParticleOptions(new org.joml.Vector3f(
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.8f),
                worldPosition.getX() + 0.5 + random.nextDouble() * 0.6 - 0.3,
                worldPosition.getY() + 1.05,
                worldPosition.getZ() + 0.5 + random.nextDouble() * 0.6 - 0.3,
                0, 0.02, 0);
    }

    private void updateStatusParticles() {
        int rgb = statusColor();
        if (rgb < 0 || level.getGameTime() % 10 != 0) {
            return;
        }
        spawnDust(rgb, 2);
    }

    // ===================== 父类钩子 =====================

    @Override
    public boolean hasDistributionBehaviour() {
        return true;
    }

    @Override
    public boolean canInsert(Direction side, ItemStack stack) {
        return canTakeItems();
    }

    @Override
    public boolean canTakeItems() {
        return queue.size() < MAX_QUEUED_STACKS && getStackToDistribute().isEmpty()
                && !getBlockState().getValue(WarpTunnelBlock.POWERED);
    }

    // ===================== 持久化 =====================

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("WarpStatus", status);

        // 每面频率（B15 新配对机制）
        ListTag freqList = new ListTag();
        faceFreqs.forEach((face, freq) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Face", face.get3DDataValue());
            entry.putInt("Freq", freq);
            freqList.add(entry);
        });
        tag.put("FaceFreqs", freqList);
        tag.putBoolean("ExportFilter", exportFilter);

        // 过滤槽不再单独存：SidedFilteringBehaviour 自己会写进方块实体 NBT（父类 read/write 里那份）。

        ListTag queueTag = new ListTag();
        for (Queued entry : queue) {
            if (entry.stack().isEmpty()) {
                continue;
            }
            Tag savedTag = entry.stack().saveOptional(registries);
            if (!(savedTag instanceof CompoundTag saved)) {
                continue;
            }
            // 入口面随物品落盘：不存的话重载后每一堆都退回 FACES 顺位，多出口又会串线
            saved.putInt("FromFace", entry.enteredFrom().get3DDataValue());
            queueTag.add(saved);
        }
        tag.put("Queue", queueTag);

        // 待吐出缓冲：存物品 + 该从哪一面吐，重载后继续往同一方向吐
        ListTag outTag = new ListTag();
        for (PendingOut out : pendingOutput) {
            if (out.stack().isEmpty()) {
                continue;
            }
            Tag savedTag = out.stack().saveOptional(registries);
            if (!(savedTag instanceof CompoundTag saved)) {
                continue;
            }
            saved.putInt("OutFace", out.face().get3DDataValue());
            outTag.add(saved);
        }
        tag.put("PendingOut", outTag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        status = tag.contains("WarpStatus") ? tag.getString("WarpStatus") : "idle";

        faceFreqs.clear();
        ListTag freqList = tag.getList("FaceFreqs", Tag.TAG_COMPOUND);
        for (int i = 0; i < freqList.size(); i++) {
            CompoundTag entry = freqList.getCompound(i);
            Direction face = Direction.from3DDataValue(entry.getInt("Face"));
            faceFreqs.put(face, entry.getInt("Freq"));
        }
        exportFilter = !tag.contains("ExportFilter") || tag.getBoolean("ExportFilter");
        reindexAll();

        // 过滤槽由 SidedFilteringBehaviour 自行读回（见父类 read），这里不再解析旧字段。

        pendingOutput.clear();
        ListTag outTag = tag.getList("PendingOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < outTag.size() && pendingOutput.size() < MAX_PENDING_OUTPUT; i++) {
            CompoundTag saved = outTag.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, saved);
            if (!stack.isEmpty()) {
                pendingOutput.add(new PendingOut(stack, Direction.from3DDataValue(saved.getInt("OutFace"))));
            }
        }

        queue.clear();
        ListTag queueTag = tag.getList("Queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < queueTag.size() && queue.size() < MAX_QUEUED_STACKS; i++) {
            CompoundTag saved = queueTag.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, saved);
            if (!stack.isEmpty()) {
                // 旧存档没有 FromFace：退回北面，行为与改动前一致（不崩、不丢物品）
                Direction face = saved.contains("FromFace")
                        ? Direction.from3DDataValue(saved.getInt("FromFace"))
                        : Direction.NORTH;
                queue.add(new Queued(stack, face));
            }
        }
    }
}
