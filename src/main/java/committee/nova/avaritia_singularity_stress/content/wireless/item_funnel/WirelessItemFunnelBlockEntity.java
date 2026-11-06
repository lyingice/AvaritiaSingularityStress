package committee.nova.avaritia_singularity_stress.content.wireless.item_funnel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelFilterSlotPositioning;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessKey;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessService;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * B9 无线物品传输漏斗的方块实体。
 * <p>
 * <b>角色由 {@link FunnelBlock#EXTRACTING} 决定，不是由注册名决定</b>：
 * <ul>
 *   <li>{@code extracting=true}（抽取端）：从朝向的容器里抽 1 个物品放进<b>自己的缓存</b>；</li>
 *   <li>{@code extracting=false}（接收端）：找出唯一对端的抽取端，从它的缓存取 1 个注入自己朝向的容器。</li>
 * </ul>
 * 只注册一个方块名（{@code wireless_item_funnel}），扳手循环 EXTRACTING 即可切换角色——
 * 这一条是 Create 黄铜漏斗自带的行为，由 {@link FunnelBlock} 基类提供。
 * <p>
 * 关于基类的两点实现约束（已在开工前核实并批准）：
 * <ol>
 *   <li>基类 {@code AbstractFunnelBlock} 实现的是 {@code IBE<FunnelBlockEntity>}，
 *       {@code getBlockEntityClass()} 被钉死成 Create 的 {@code FunnelBlockEntity}。
 *       本 BE 不继承它，因此 {@code IBE.getBlockEntity(...)} 对我们恒返回 null——
 *       <b>绝不能用 {@code withBlockEntityDo()} / {@code tryInsert()}</b>，
 *       所以物品插入路径在这里自写（见 {@link #insertHeld(ItemStack)}）。</li>
 *   <li>{@code IBE.newBlockEntity(...)} 默认会用 Create 的漏斗类型造 BE，
 *       必须由 {@code WirelessItemFunnelBlock} 覆写并返回本类实例。</li>
 * </ol>
 * 搬运逻辑挂在 {@link #tickWireless()} 上，由共享底层 {@code WirelessLifecycle}
 * 每 5 tick 驱动一次（不是每 tick），与设计定稿一致。
 */
public class WirelessItemFunnelBlockEntity extends SmartBlockEntity
        implements IFrequencyBindable, WirelessDevice, IWirelessConfigHolder {

    /**
     * 每次操作搬运的物品数：一整组（64 个）/ 5 tick。
     * <p>
     * 原先是 1 个 / 5 tick（照抄黄铜漏斗的手感），实测太慢；改成整组后
     * 抽取端一次从容器里取满一组、接收端一次往容器里灌满一组。
     * 真正的上限仍由两端的容器/缓存各自把关（{@code extractItem} 与
     * {@code insertItemStacked} 都会自己截断），这里只是一个"请求量"。
     */
    private static final int ITEMS_PER_OPERATION = 64;

    private static final String KEY_CACHE = "Cache";

    /** 吐出后多少 tick 内不再吸收自己吐出的物品（防吐-吸死循环）。 */
    private static final int EJECT_REACQUIRE_DELAY = 10;

    // ===================== 频率（方块 NBT，按维度分区） =====================

    private int frequency = NO_FREQUENCY;

    private ChannelVisibility visibility = ChannelVisibility.PRIVATE;

    @Nullable
    private UUID owner;

    /** 抽取端缓存：抽取端把物品搬进这里，接收端从这里取走注入。 */
    private ItemStack cache = ItemStack.EMPTY;

    /** B9 接收端出口：默认直接插入朝向容器，开启后改为向漏斗口外吐出 ItemEntity。 */
    private boolean externalOutput;

    /**
     * 最近一次"向世界吐出物品"的 gameTime。
     * <p>
     * 用途只有一个：<b>防吐-吸死循环</b>。朝上的漏斗开启外部输出时，吐出的物品会正好落在
     * 自己开口上方，重力把它带回方块内，entityInside 又立刻吸收、再吐出……
     * 这个窗口内的掉落物不再被自己吸收，让物品有机会离开。
     */
    private long lastEjectTime = Long.MIN_VALUE / 2;

    public WirelessItemFunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** 注册用工厂，与 {@code ModBlockEntities} 的 {@code ::create} 写法一致。 */
    public static WirelessItemFunnelBlockEntity create(BlockPos pos, BlockState state) {
        return new WirelessItemFunnelBlockEntity(ModBlockEntities.WIRELESS_ITEM_FUNNEL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 过滤槽沿用 Create 黄铜漏斗的定位器（B9 就是黄铜漏斗外观）。
        // 刻意不加 onlyActiveWhen：Create 自己的漏斗把过滤槽限定在 BRASS_FUNNEL 上，
        // 我们换成自己的方块后那个判定会恒 false，过滤槽就永远点不开。
        behaviours.add(new FilteringBehaviour(this, new FunnelFilterSlotPositioning()));

        // Create 漏斗的传送带入口依赖 DirectBeltInputBehaviour；世界掉落物由方块的 entityInside 入口处理。
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(this::supportsDirectBeltInput)
                .setInsertionHandler(this::handleDirectBeltInput));
    }

    // ===================== 收集（带子/隧道 → 缓存） =====================

    /**
     * 从哪一面收：照抄 Create 的 {@code FunnelBlockEntity.supportsDirectBeltInput}
     * （{@code FunnelBlockEntity.java:288-296}）——不抽取、未通电、<b>口朝上</b>时才收。
     * 口朝上意味着带子在漏斗上方经过，物品落进漏斗；横向/朝下的漏斗有自己的推取语义。
     */
    private boolean supportsDirectBeltInput(Direction side) {
        BlockState state = getBlockState();
        if (state == null) {
            return false;
        }
        if (state.getValue(FunnelBlock.EXTRACTING) || state.getValue(FunnelBlock.POWERED)) {
            return false;
        }
        return AbstractFunnelBlock.getFunnelFacing(state) == Direction.UP;
    }

    /**
     * 端口统一入口（掉落物 / 手持插入 / 传送带都走这里）：把物品收进本漏斗缓存。
     * <p>
     * <b>为什么进缓存而不是直接插容器</b>：这台机器是<b>无线端口</b>，
     * 缓存就是它的传输缓冲——发送端（extracting=true）的缓存会被对端接收端取走，
     * 接收端的缓存由 {@link #flushReceiverCache} 每 tick 往本地容器/世界排。
     * 之前要求"必须先有容器才吸收"，导致玩家把物品丢在没有容器、纯无线发送的漏斗上时
     * <b>完全不吸收</b>（实机：铁锭一直躺在漏斗上）。
     * <p>
     * 不收过滤槽约束（"掉进来就收"），容量仍受一个整堆限制；收不下就把剩余退还给调用方，
     * 由它们决定留在世界里还是退回传送带——绝不静默扣物品。
     *
     * @return 没被收下的剩余部分（空 = 全部收下）
     */
    public ItemStack absorbExternal(ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty() || getBlockState().getValue(FunnelBlock.POWERED)) {
            return incoming;
        }
        // 刚吐出的物品别立刻又吸回来（朝上的漏斗会形成吐-吸死循环）
        if (level != null && level.getGameTime() - lastEjectTime < EJECT_REACQUIRE_DELAY) {
            return incoming;
        }
        // 收货前确认"有地方可去"，否则吸进缓存就是黑洞：
        //   ① 自己有本地去处（前方容器 / 外部输出）；或
        //   ② 频道里有一台有去处、且还有缓存空间的漏斗能接走。
        // 两者都不成立时把物品留在世界（可见、可捡），不静默吞掉。
        BlockState state = getBlockState();
        if (!hasLocalSink(state) && !hasSinkPeerWithRoom(incoming)) {
            return incoming;
        }
        int room = cacheRoom(incoming);
        int moved = Math.min(room, incoming.getCount());
        if (moved <= 0) {
            return incoming;
        }
        if (!simulate) {
            if (cache.isEmpty()) {
                cache = incoming.copyWithCount(moved);
            } else {
                cache.grow(moved);
            }
            setChanged();
            sendData();
        }
        return incoming.copyWithCount(incoming.getCount() - moved);
    }


    /**
     * 带子把物品推进来：同样走端口统一入口 {@link #absorbExternal}（FilteringBehaviour 仍然生效，
     * 这是玩家显式配置的白名单，与"掉落物无条件吸收"是两件事）。
     *
     * @return <b>没有被收下的部分</b>（缓存满/过滤不匹配时完整退回给 Create 传送带）
     */
    private ItemStack handleDirectBeltInput(TransportedItemStack stack, Direction side, boolean simulate) {
        ItemStack incoming = stack.stack;
        FilteringBehaviour filter = getBehaviour(FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(incoming)) {
            return incoming;
        }
        return absorbExternal(incoming, simulate);
    }

    /** 缓存还能装几个这个物品：只装一种，封顶该物品的最大堆叠（通常是 64）。 */
    private int cacheRoom(ItemStack incoming) {
        int cap = incoming.getMaxStackSize();
        if (cache.isEmpty()) {
            return cap;
        }
        if (!ItemStack.isSameItemSameComponents(cache, incoming)) {
            return 0;
        }
        return Math.max(0, cap - cache.getCount());
    }

    // ===================== 生命周期（注册幂等 / 注销跟随 invalidate） =====================

    /**
     * 首次 tick 触发；<b>区块卸载后重载会再调一次</b>，
     * 因此注册必须幂等 —— {@link WirelessNetwork#add} 内部先摘旧条目再放新条目。
     */
    @Override
    public void initialize() {
        super.initialize();
        WirelessNetwork.add(this);
    }

    /**
     * {@code setRemoved()} 是 final 且区块卸载时会跳过 {@code remove()}，
     * 所以注销只能挂在 {@code invalidate()} 上（否则会留下悬空引用）。
     */
    @Override
    public void invalidate() {
        WirelessNetwork.remove(this);
        super.invalidate();
    }

    // ===================== WirelessDevice =====================

    @Override
    public WirelessKey getKey() {
        return hasFrequency() ? WirelessKey.of(frequency, owner, visibility) : null;
    }

    @Override
    public boolean isAlive() {
        return !isRemoved() && level != null && !level.isClientSide;
    }

    @Override
    public boolean wantsWirelessTick() {
        return true;
    }

    @Override
    public void tickWireless() {
        transfer();
    }

    // ===================== IFrequencyBindable / IWirelessConfigHolder =====================

    @Override
    public int getFrequency() {
        return frequency;
    }

    @Override
    public void setFrequency(int frequency) {
        int clamped = IFrequencyBindable.clampFrequency(frequency);
        if (this.frequency == clamped) {
            return;
        }
        this.frequency = clamped;
        setChanged();
        sendData();
        // 频率变了 → 搬到新频道（无频率时 WirelessNetwork.add 内部直接返回）
        WirelessNetwork.move(this);
    }

    @Override
    public ChannelVisibility getVisibility() {
        return visibility;
    }

    @Override
    public void setVisibility(ChannelVisibility visibility) {
        if (this.visibility == visibility) {
            return;
        }
        this.visibility = visibility;
        setChanged();
        sendData();
        WirelessNetwork.move(this);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    @Override
    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        sendData();
        WirelessNetwork.move(this);
    }

    @Override
    public boolean isExternalOutput() {
        return externalOutput;
    }

    @Override
    public void setExternalOutput(boolean value) {
        if (externalOutput == value) {
            return;
        }
        externalOutput = value;
        setChanged();
        sendData();
    }

    // ===================== 搬运 =====================

    /**
     * 每 5 tick 的搬运调度（由 {@code WirelessLifecycle} 驱动）。
     * <p>
     * <b>B25：改成"有去处自己消化，没去处推给有去处的同频漏斗"。</b>
     * 旧模型要求一台必须当发送端（抽取端）、另一台当接收端才动；玩家把物品丢进一台
     * 默认（接收端）漏斗时，它既没有本地容器、也不会往外送，物品就卡在缓存里出不去
     * （实机：铁锭浮在漏斗上方反复弹跳）。现在去向只由"有没有本地去处"决定。
     */
    private void transfer() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getValue(FunnelBlock.POWERED)) {
            return;
        }
        // 抽取端：先把朝向容器里的货拉进缓存（它朝的容器是"源"，不是去处）
        if (state.getValue(FunnelBlock.EXTRACTING) && hasFrequency()) {
            extractIntoCache(state);
        }
        if (cache.isEmpty()) {
            return;
        }
        // 自己没有去处：优先把货交给有去处的同频漏斗，别自己吐出来
        if (!hasLocalSink(state) && pushCacheToPeer()) {
            return;
        }
        drainLocally(state);
    }

    /**
     * 本端有没有"本地去处"（能把这批货放下并离开无线网络的地方）。
     * <p>
     * 抽取端朝向的容器是<b>货源</b>不是去处——否则刚抽出来就塞回去，来回打转。
     * 所以只有"非抽取端 + 前方有容器"或"勾选了外部输出"才算去处。
     */
    private boolean hasLocalSink(BlockState state) {
        if (externalOutput) {
            return AbstractFunnelBlock.getFunnelFacing(state) != null;
        }
        return !state.getValue(FunnelBlock.EXTRACTING) && facingInventory(state) != null;
    }

    /** 把缓存推给一台有本地去处的同频漏斗；推出去了返回 true。 */
    private boolean pushCacheToPeer() {
        for (WirelessItemFunnelBlockEntity peer : peers()) {
            if (!peer.hasLocalSink(peer.getBlockState())) {
                continue;
            }
            FilteringBehaviour peerFilter = peer.getBehaviour(FilteringBehaviour.TYPE);
            if (peerFilter != null && !peerFilter.test(cache)) {
                continue;
            }
            int room = peer.cacheRoom(cache);
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, cache.getCount());
            if (peer.cache.isEmpty()) {
                peer.cache = cache.copyWithCount(moved);
            } else {
                peer.cache.grow(moved);
            }
            cache.shrink(moved);
            if (cache.isEmpty()) {
                cache = ItemStack.EMPTY;
            }
            setChanged();
            sendData();
            peer.setChanged();
            peer.sendData();
            return true;
        }
        return false;
    }

    /** 同频、范围内、仍然存活的其它漏斗（本机不在结果里）。 */
    private List<WirelessItemFunnelBlockEntity> peers() {
        List<WirelessItemFunnelBlockEntity> result = new ArrayList<>();
        if (!hasFrequency()) {
            return result;
        }
        for (WirelessDevice device : WirelessService.candidates(this, WirelessConfig.itemFunnelRange())) {
            if (device instanceof WirelessItemFunnelBlockEntity funnel && funnel != this && funnel.isAlive()) {
                result.add(funnel);
            }
        }
        return result;
    }

    /** 把缓存投到本地：外部输出→吐到世界；否则→塞前方容器。塞不下的留在缓存里重试。 */
    private void drainLocally(BlockState state) {
        if (cache.isEmpty()) {
            return;
        }
        if (externalOutput) {
            Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
            if (facing == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(worldPosition)
                    .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.65));
            ItemEntity entity = new ItemEntity(level, center.x, center.y, center.z, cache.copy());
            entity.setDefaultPickUpDelay();
            // 朝上的漏斗会把货吐在自己开口正上方，直接掉回去就变成"吐-吸"循环；
            // 给一点向上初速让它落到旁边。
            if (facing == Direction.UP) {
                entity.setDeltaMovement(0.0D, 0.18D, 0.0D);
            }
            if (level.addFreshEntity(entity)) {
                lastEjectTime = level.getGameTime();
                cache = ItemStack.EMPTY;
                setChanged();
                sendData();
            }
            return;
        }
        IItemHandler target = facingInventory(state);
        if (target == null) {
            return;
        }
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, cache.copy(), false);
        if (leftover.getCount() != cache.getCount()) {
            cache = leftover.isEmpty() ? ItemStack.EMPTY : leftover;
            setChanged();
            sendData();
        }
    }

    /**
     * 抽取前确认：频道里至少有一台漏斗能接下这件物品（有本地去处、过滤放行、缓存还有空间）。
     * <p>
     * 没有这样的对端就不扣源容器——否则货只会堆在发送端看不见的缓存里（T31）。
     */
    private boolean hasSinkPeerWithRoom(ItemStack stack) {
        for (WirelessItemFunnelBlockEntity peer : peers()) {
            if (!peer.hasLocalSink(peer.getBlockState())) {
                continue;
            }
            FilteringBehaviour peerFilter = peer.getBehaviour(FilteringBehaviour.TYPE);
            if (peerFilter != null && !peerFilter.test(stack)) {
                continue;
            }
            if (peer.cacheRoom(stack) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 抽取端：从朝向的容器抽 1 个（受过滤槽约束）放进自己的缓存。 */
    private void extractIntoCache(BlockState state) {
        if (!cache.isEmpty() && cache.getCount() >= cache.getMaxStackSize()) {
            return;
        }
        IItemHandler target = facingInventory(state);
        if (target == null) {
            return;
        }
        FilteringBehaviour filter = getBehaviour(FilteringBehaviour.TYPE);
        for (int slot = 0; slot < target.getSlots(); slot++) {
            ItemStack inSlot = target.getStackInSlot(slot);
            if (inSlot.isEmpty()) {
                continue;
            }
            if (filter != null && !filter.test(inSlot)) {
                continue;
            }
            // 缓存放不下第二种物品：等它被接收端取空再换品类
            if (!cache.isEmpty() && !ItemStack.isSameItemSameComponents(cache, inSlot)) {
                continue;
            }
            // 没有可接下的对端时不先扣源箱子，否则货只会堆在看不见的缓存里（T31）
            if (!hasSinkPeerWithRoom(inSlot)) {
                return;
            }
            ItemStack extracted = target.extractItem(slot, ITEMS_PER_OPERATION, false);
            if (extracted.isEmpty()) {
                continue;
            }
            if (cache.isEmpty()) {
                cache = extracted.copy();
            } else {
                cache.grow(extracted.getCount());
            }
            setChanged();
            return;
        }
    }

    /**
     * 取漏斗<b>背后</b>的容器能力（开口方向的反面）。
     * <p>
     * 推导（真实类型 {@code net.createmod.catnip.math.BlockFace}，javap 已核对）：
     * {@code getConnectedPos() = pos.relative(face)}；Create 的
     * {@code FunnelBlockEntity.addBehaviours} 把 provider 设为
     * {@code new BlockFace(p, funnelFacing.getOpposite())}（{@code FunnelBlockEntity.java:255-259}），
     * 能力基类再调 {@code getTarget().getOpposite()}（{@code CapManipulationBehaviourBase.java:134-147}），
     * 净结果就是：<b>容器在 {@code pos.relative(facing.getOpposite())}，能力面为 {@code facing}</b>。
     * <p>
     * 这正是"漏斗开口朝外、容器装在背面"的直觉模型。之前一版错误地改成 {@code relative(facing)}，
     * 结果贴着箱子放的漏斗查到了开口外侧的空气，抽取和插入全部落空。
     */
    @Nullable
    private IItemHandler facingInventory(BlockState state) {
        Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
        if (facing == null || level == null) {
            return null;
        }
        // 先按 Create 的语义找"背后"的容器
        BlockPos behind = worldPosition.relative(facing.getOpposite());
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, behind, facing);
        if (handler != null) {
            return handler;
        }
        // 容错：玩家常把箱子直接压在朝上的漏斗上面（开口那一侧）。
        // 背后没有容器时再找开口方向一次，免得"明显贴着箱子却不工作"。
        BlockPos inFront = worldPosition.relative(facing);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, inFront, facing.getOpposite());
    }

    /**
     * 玩家手持物品右击"开口面"时的插入路径。
     * <p>
     * 这是自写的：基类 {@code FunnelBlock.useItemOn} 走的是
     * {@code withBlockEntityDo(...)}，而它依赖的 {@code IBE.getBlockEntity} 对本类恒返回 null。
     *
     * @param stack 待插入的物品（调用方传副本）
     * @return 没插进去的剩余部分
     */
    public ItemStack insertHeld(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) {
            return stack;
        }
        FilteringBehaviour filter = getBehaviour(FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(stack)) {
            return stack;
        }
        // 与掉落物入口共用同一条路径：先进端口缓存，再由 tick 决定是无线发送还是写本地容器。
        // 这样"手持右键灌进纯无线漏斗"也能工作，不必一定要先贴一个容器。
        return absorbExternal(stack, false);
    }

    // ===================== 持久化 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFrequency(tag, registries, clientPacket);
        if (!cache.isEmpty()) {
            tag.put(KEY_CACHE, cache.save(registries));
        }
        tag.putBoolean("ExternalOutput", externalOutput);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readFrequency(tag, registries, clientPacket);
        cache = tag.contains(KEY_CACHE)
                ? ItemStack.parseOptional(registries, tag.getCompound(KEY_CACHE))
                : ItemStack.EMPTY;
        externalOutput = tag.getBoolean("ExternalOutput");
    }
}
