package committee.nova.avaritia_singularity_stress.content.wireless.item_funnel;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

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

    /** 每次操作搬运的物品数：1 个 / 5 tick，与漏斗的原生节流手感一致。 */
    private static final int ITEMS_PER_OPERATION = 1;

    private static final String KEY_CACHE = "Cache";

    // ===================== 频率（方块 NBT，按维度分区） =====================

    private int frequency = NO_FREQUENCY;

    private ChannelVisibility visibility = ChannelVisibility.PRIVATE;

    @Nullable
    private UUID owner;

    /** 抽取端缓存：抽取端把物品搬进这里，接收端从这里取走注入。 */
    private ItemStack cache = ItemStack.EMPTY;

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

    // ===================== 搬运 =====================

    private void transfer() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        // 红石锁定（C 方案）：通电即整机停，抽取与注入都不做
        if (state.getValue(FunnelBlock.POWERED)) {
            return;
        }
        if (!hasFrequency()) {
            return;
        }
        if (state.getValue(FunnelBlock.EXTRACTING)) {
            extractIntoCache(state);
        } else {
            injectFromPeer(state);
        }
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
     * 接收端：找到唯一对端（抽取端），从它的缓存取 1 个注入自己朝向的容器。
     * <p>
     * 一对一由 {@link WirelessService#findPeer} 收口：0 个或 ≥2 个对端都返回 null，
     * 宁可停机也不随机挑一个。
     */
    private void injectFromPeer(BlockState state) {
        IItemHandler target = facingInventory(state);
        if (target == null) {
            return;
        }
        WirelessDevice peer = WirelessService.findPeer(this, WirelessConfig.itemFunnelRange());
        if (!(peer instanceof WirelessItemFunnelBlockEntity sender)) {
            return;
        }
        // 只有抽取端的缓存才是货源；两个都是接收端（或都是抽取端）时什么也不做
        if (!sender.getBlockState().getValue(FunnelBlock.EXTRACTING)) {
            return;
        }
        ItemStack available = sender.cache;
        if (available.isEmpty()) {
            return;
        }
        FilteringBehaviour filter = getBehaviour(FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(available)) {
            return;
        }

        ItemStack toInsert = available.copyWithCount(ITEMS_PER_OPERATION);
        // 先模拟，确认能塞进去再从缓存里扣，保证物品不丢
        ItemStack simulated = ItemHandlerHelper.insertItemStacked(target, toInsert, true);
        int accepted = toInsert.getCount() - simulated.getCount();
        if (accepted <= 0) {
            return;
        }
        ItemStack moved = sender.cache.split(accepted);
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, moved, false);
        if (!leftover.isEmpty()) {
            // 模拟与执行之间容器被别的东西改了；把塞不进的退回缓存，绝不凭空消失
            if (sender.cache.isEmpty()) {
                sender.cache = leftover;
            } else {
                sender.cache.grow(leftover.getCount());
            }
        }
        sender.setChanged();
        sender.sendData();
        setChanged();
    }

    /**
     * 取"朝向的容器"的能力。
     * <p>
     * 与 Create 的 {@code InvManipulationBehaviour} 同约定：目标容器在
     * {@code worldPosition.relative(facing.getOpposite())}，而能力查询面是 {@code facing}。
     */
    @Nullable
    private IItemHandler facingInventory(BlockState state) {
        Direction facing = AbstractFunnelBlock.getFunnelFacing(state);
        if (facing == null) {
            return null;
        }
        BlockPos target = worldPosition.relative(facing.getOpposite());
        return level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing);
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
        IItemHandler target = facingInventory(getBlockState());
        if (target == null) {
            return stack;
        }
        FilteringBehaviour filter = getBehaviour(FilteringBehaviour.TYPE);
        if (filter != null && !filter.test(stack)) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(target, stack, false);
    }

    // ===================== 持久化 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFrequency(tag, registries, clientPacket);
        if (!cache.isEmpty()) {
            tag.put(KEY_CACHE, cache.save(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readFrequency(tag, registries, clientPacket);
        cache = tag.contains(KEY_CACHE)
                ? ItemStack.parseOptional(registries, tag.getCompound(KEY_CACHE))
                : ItemStack.EMPTY;
    }
}
