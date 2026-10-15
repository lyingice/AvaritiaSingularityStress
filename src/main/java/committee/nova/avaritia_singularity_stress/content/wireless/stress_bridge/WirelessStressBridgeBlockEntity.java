package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessGuiKind;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessKey;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessService;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.lang.LangNumberFormat;

/**
 * B10 无线应力传输器的方块实体（方案 C：两个注册名、两个 BE 类型、共用一个 BE 类）。
 * <p>
 * 角色由方块状态 {@code output} 决定：
 * <ul>
 *   <li><b>Sender</b>（{@code output=true}，挂在<b>源网</b>上）：每 5 tick 取源网空闲容量
 *       {@code X = min(calculateCapacity() - calculateStress(), splitSuLimit, drainSuLimit)}，
 *       直接调对端接收端的 {@link #receiveFromPeer(float, float)}，
 *       并通过覆写 {@link #calculateStressApplied()} 把这 X 记成"源网被扣掉的容量"。</li>
 *   <li><b>Receiver</b>（{@code output=false}，挂在自己的<b>接收端网</b>上）：收到 (X, 转速) 后
 *       覆写 {@link #calculateAddedStressCapacity()} 给本网加容量、覆写 {@link #getGeneratedSpeed()}
 *       把源网转速当成本网的发电转速。X ≤ 0 时返回 0 → 接收端网络整体停转（设计预期）。</li>
 * </ul>
 * <p>
 * <b>基类为什么必须是 {@link GeneratingKineticBlockEntity}</b>：设计要求的
 * {@code updateGeneratedRotation()} 只声明在这个类上（{@code KineticBlockEntity} 没有），
 * {@code notifyStressCapacityChange(float)} 同理（protected）。
 * <p>
 * <b>单位归一化（已批准）</b>：{@code KineticNetwork} 里 {@code sources}/{@code members}
 * 存的是"每 RPM"值，实际值 = 存值 × |转速|
 * （{@code getActualCapacityOf = sources.get(be) * |getGeneratedSpeed()|}、
 * {@code getActualStressOf = members.get(be) * |getTheoreticalSpeed()|}）。
 * 而 X 按公式算出来是<b>实际 SU</b>，所以写回网络前一律除以 |转速|，
 * 保证"Sender 正好从源网扣 X、Receiver 正好给本网加 X"。
 * <p>
 * <b>Sender 为什么可以用 {@code updateStressFor}</b>：{@code updateGeneratedRotation()} 里推送应力
 * 的那段是 {@code if (hasNetwork() && speed != 0)}，而 Sender 的 {@code getGeneratedSpeed()} 恒为 0，
 * 那个分支根本不会执行。本工程 B6（{@code SingularityRotationSpeedControllerBlockEntity}）对
 * 动态应力用的就是"覆写 calculateStressApplied() + KineticNetwork.updateStressFor 主动刷新"，
 * 这里沿用同一套既有做法（不是直接改 sources/members 字段）。
 */
public class WirelessStressBridgeBlockEntity extends GeneratingKineticBlockEntity
        implements IFrequencyBindable, WirelessDevice, IWirelessConfigHolder {

    /** |转速| 小于此值视为静止，避免归一化除零。 */
    private static final float MIN_SPEED = 1.0e-4F;

    /**
     * 接收端的看门狗超时（tick）。
     * <p>
     * 输出端每 5 tick 推一次；超过这个时间没收到推送就认为桥断了（输出端被拆 / 区块卸载 /
     * 频率被改），把 {@link #bridgeStress} 清零。<b>不这样做的话，输出端一旦消失，
     * 接收端会永远保留最后一次的 X 继续给自己那张网供电。</b>
     */
    private static final long RECEIVER_TIMEOUT_TICKS = 20L;

    // 【已移除】KINETIC_STATE_KEYS：原扳手"换方块"时要清掉的动能状态键，
    // 随 WirelessStressBridgeBlock.onWrenched 一并删除（现在扳手只旋转，不换方块）。

    private static final String KEY_PRIMARY = "SplitSu";
    private static final String KEY_SECONDARY = "Priority";

    // 护目镜调试字段：只进客户端同步包，不落盘（每 5 tick 现算的瞬时值）
    private static final String KEY_BRIDGE_STRESS = "BridgeSu";
    private static final String KEY_SOURCE_SPEED = "SourceSpeed";
    private static final String KEY_STATUS = "BridgeStatus";
    private static final String KEY_NET_CAPACITY = "NetCapacity";
    private static final String KEY_NET_STRESS = "NetStress";
    private static final String KEY_NET_IDLE = "NetIdle";

    /**
     * 护目镜里的"现在到底在干什么/为什么没在传"。
     * <p>
     * 每个取值在 {@code lang} 里对应 {@code avaritia_singularity_stress.wireless.status.<小写名>}。
     * 这是给玩家（和调试）看的：原版动力学组在应力为 0 时整组不显示，
     * 而接收端的 {@code calculateStressApplied()} 恒为 0，所以必须自己补一套。
     */
    public enum BridgeStatus {
        /** 没写频率。 */
        UNBOUND,
        /** 频率有了，但这个方块不在任何应力网络里（唯一接网面没接轴/齿轮，或接到了别的面上）。 */
        NO_NETWORK,
        /** 在网里，但源网没有空闲应力（产能 ≤ 消耗）。 */
        NO_HEADROOM,
        /** 源网有富余，但范围内找不到同频率、同可见性的接收端。 */
        NO_PEER,
        /** 输出端正在传输。 */
        SENDING,
        /** 接收端正在给本站网络供电。 */
        RECEIVING,
        /** 接收端已绑定但还没收到推送（输出端没在工作 / 超出范围）。 */
        RECEIVE_IDLE
    }

    // ===================== 频率 =====================

    private int frequency = NO_FREQUENCY;

    private ChannelVisibility visibility = ChannelVisibility.PRIVATE;

    @Nullable
    private UUID owner;

    // ===================== 配置（GUI 两个数值框） =====================

    /** 输出端 = 每次最多分给对端多少 SU（splitSuLimit）；接收端 = 自己最多接收多少 SU（drainSuLimit）。 */
    private int primaryAmount;

    /** 接收端 = 优先级（本方案一对一，暂不参与计算）。 */
    private int secondaryAmount;

    /**
     * 当前跨桥流动的实际 SU：Sender 记为"已扣源网多少"，Receiver 记为"已给本网加多少"。
     * 不持久化 —— 每 5 tick 会重算。
     */
    private float bridgeStress;

    /** Receiver 记下源网转速，作为自己网络的发电转速。 */
    private float sourceSpeed;

    /** 接收端最后一次收到推送的游戏刻，用于看门狗（见 {@link #RECEIVER_TIMEOUT_TICKS}）。 */
    private long lastPushGameTime;

    // ===================== 护目镜调试数据（服务端每 5 tick 刷新，随 NBT 同步给客户端） =====================

    /** 当前状态（为什么在传 / 为什么不传）。 */
    private BridgeStatus debugStatus = BridgeStatus.UNBOUND;

    /** 本机所在网络的产能 / 消耗 / 空闲（su）；输出端 = 它扣的那张源网，接收端 = 它供的那张网。 */
    private float debugCapacity;
    private float debugStress;
    private float debugIdle;

    public WirelessStressBridgeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 注册用工厂。
     * <p>
     * 方案 C 要求两个注册名各配一个 BE 类型，而两个方块共用本类，
     * 所以这里按方块状态的 {@code output} 选出对应的 BE 类型：
     * 每个类型只注册在它自己的方块上，因此选出来的类型必然与创建它的类型一致。
     */
    public static WirelessStressBridgeBlockEntity create(BlockPos pos, BlockState state) {
        BlockEntityType<WirelessStressBridgeBlockEntity> type =
                state.getValue(WirelessStressBridgeBlock.OUTPUT)
                        ? ModBlockEntities.WIRELESS_STRESS_BRIDGE_SENDER.get()
                        : ModBlockEntities.WIRELESS_STRESS_BRIDGE_RECEIVER.get();
        return new WirelessStressBridgeBlockEntity(type, pos, state);
    }

    // ===================== 生命周期 =====================

    @Override
    public void initialize() {
        super.initialize();
        WirelessNetwork.add(this);
    }

    @Override
    public void invalidate() {
        WirelessNetwork.remove(this);
        super.invalidate();
    }

    /** @return true = 输出端（Sender，扣源网）；false = 接收端（Receiver，给本网加容量）。 */
    public boolean isOutputSide() {
        return getBlockState().getValue(WirelessStressBridgeBlock.OUTPUT);
    }

    // ===================== 界面布局提示（客户端 GUI 读，见 WirelessGuiKind） =====================

    /** B10 有"数量"字段：输出端 = 送出上限；接收端 = 接收上限 + 优先级。 */
    @Override
    public WirelessGuiKind guiKind() {
        return WirelessGuiKind.STRESS_BRIDGE;
    }

    @Override
    public boolean guiOutputSide() {
        return isOutputSide();
    }

    // ===================== 动能：源网 / 接收端网 =====================

    /**
     * 接收端用源网转速作为本网发电转速；X ≤ 0 时返回 0（本网停转）。
     * <p>
     * 输出端恒返回 0 —— 它是纯粹的应力消费者，不能成为源。
     */
    @Override
    public float getGeneratedSpeed() {
        if (isOutputSide() || bridgeStress <= 0F) {
            return 0F;
        }
        return sourceSpeed;
    }

    /**
     * 输出端从源网扣掉的应力。
     * <p>
     * 网络会把它乘上 |本机转速|，所以这里返回归一化后的 {@code X / |转速|}。
     */
    @Override
    public float calculateStressApplied() {
        if (!isOutputSide()) {
            return 0F;
        }
        float normalized = normalize(bridgeStress, getTheoreticalSpeed());
        lastStressApplied = normalized;
        return normalized;
    }

    /**
     * 接收端给本网新增的容量。
     * <p>
     * 网络会把它乘上 |getGeneratedSpeed()|（= 源网转速），所以这里同样返回归一化值。
     */
    @Override
    public float calculateAddedStressCapacity() {
        if (isOutputSide()) {
            return 0F;
        }
        float normalized = normalize(bridgeStress, getGeneratedSpeed());
        lastCapacityProvided = normalized;
        return normalized;
    }

    private static float normalize(float su, float speed) {
        if (Math.abs(speed) < MIN_SPEED) {
            return 0F;
        }
        return su / Math.abs(speed);
    }

    /** 0 或负数 = 不限（与 GUI 空框默认值一致）。 */
    private static float limitOf(int configured) {
        return configured <= 0 ? Float.MAX_VALUE : configured;
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

    /** 输出端推进传输；接收端跑看门狗（对端消失时必须停转，否则会凭空供电）。 */
    @Override
    public boolean wantsWirelessTick() {
        return true;
    }

    @Override
    public void tickWireless() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (isOutputSide()) {
            tickSender();
            return;
        }
        tickReceiverWatchdog();
        // 接收端：本网就是它供的那张网；桥流量 > 0 说明正在供电
        updateDebug(receiverStatus(), liveNetwork());
    }

    private BridgeStatus receiverStatus() {
        if (!hasFrequency()) {
            return BridgeStatus.UNBOUND;
        }
        return bridgeStress > 0F ? BridgeStatus.RECEIVING : BridgeStatus.RECEIVE_IDLE;
    }

    /** @return 本机所在网络；没网络或网里全是死成员时返回 null（免得 calculate* 抛异常）。 */
    @Nullable
    private KineticNetwork liveNetwork() {
        if (!hasNetwork()) {
            return null;
        }
        KineticNetwork network = getOrCreateNetwork();
        if (network == null || !hasLiveMembers(network)) {
            return null;
        }
        return network;
    }

    /**
     * 刷新护目镜数据；有变化才发包（每 5 tick 最多一次）。
     *
     * @param network 用来显示产能/消耗/空闲的网络；null 表示读不到（保持上次的数值）
     */
    private void updateDebug(BridgeStatus status, @Nullable KineticNetwork network) {
        BridgeStatus oldStatus = debugStatus;
        float oldCapacity = debugCapacity;
        float oldStress = debugStress;
        float oldIdle = debugIdle;

        debugStatus = status;
        if (network != null) {
            debugCapacity = network.calculateCapacity();
            debugStress = network.calculateStress();
            debugIdle = Math.max(0F, debugCapacity - debugStress);
        }

        if (oldStatus != debugStatus || !Mth.equal(oldCapacity, debugCapacity)
                || !Mth.equal(oldStress, debugStress) || !Mth.equal(oldIdle, debugIdle)) {
            sendData();
        }
    }

    /** 接收端看门狗：太久没收到推送就当作桥已断开，清零并停转本站网络。 */
    private void tickReceiverWatchdog() {
        if (bridgeStress <= 0F) {
            return;
        }
        if (level.getGameTime() - lastPushGameTime <= RECEIVER_TIMEOUT_TICKS) {
            return;
        }
        receiveFromPeer(0F, 0F);
    }

    /**
     * Sender：算 X → 找唯一对端 → 直接调它的 receiveFromPeer。
     */
    private void tickSender() {
        if (!hasFrequency()) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.UNBOUND, null);
            return;
        }
        // 设计定稿：getOrCreateNetwork() 可能为 null，必须先 hasNetwork()，拿到后仍要判空
        KineticNetwork network = liveNetwork();
        if (network == null) {
            // 没接轴 / 接错面：整台机器不在任何应力网络里，源网无从谈起
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_NETWORK, null);
            return;
        }

        // 源网的 stress 里已经包含本机上一 tick 记进去的那份 X（见 setBridgeStress），
        // 必须先把"自己占的那份"扣出来再算空闲，否则：
        //   t   : stress=0        -> idle=C      -> 推 X=C，stress 变 C
        //   t+5 : stress=C        -> idle=0      -> 判 NO_HEADROOM -> setBridgeStress(0)，stress 回 0
        //   t+10: 又推…… 于是护目镜在"传输中/没有空闲应力"之间以 10 tick 为周期频闪，
        //         源网也只有一半时间真的被扣着（另一半等于凭空供电）。
        float ownStress = Math.abs(calculateStressApplied()) * Math.abs(getTheoreticalSpeed());
        float headroom = network.calculateCapacity() - (network.calculateStress() - ownStress);
        if (headroom <= 0F) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_HEADROOM, network);
            return;
        }

        float x = Math.min(headroom, limitOf(primaryAmount));
        if (x <= 0F) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_HEADROOM, network);
            return;
        }

        WirelessDevice peer = WirelessService.findPeer(this, WirelessConfig.stressBridgeRange());
        if (!(peer instanceof WirelessStressBridgeBlockEntity receiver) || receiver.isOutputSide()) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_PEER, network);
            return;
        }

        // drainSuLimit = 接收端自己的接收上限
        x = Math.min(x, limitOf(receiver.getPrimaryAmount()));
        if (x <= 0F) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_PEER, network);
            return;
        }

        receiver.receiveFromPeer(x, getTheoreticalSpeed());
        setBridgeStress(x);
        updateDebug(BridgeStatus.SENDING, network);
    }

    /**
     * 遍历 {@code members} 做存活校验。
     * <p>
     * {@code members} 是强引用 BE 的 Map，区块卸载后条目可能还在，
     * 而 {@code calculateCapacity()/calculateStress()} 内部直接
     * {@code be.getLevel().getBlockEntity(...)}、没有 null 检查。
     * 这里只读地先数一遍：网里一个活的成员都没有，就放弃本次传输，不去碰那两个方法。
     * <p>
     * 注意不改动 {@code members} 本身（设计定稿禁止手改 sources/members）——
     * 清除失效条目是 Create 那两个方法自己的职责。
     */
    private static boolean hasLiveMembers(KineticNetwork network) {
        for (KineticBlockEntity member : network.members.keySet()) {
            Level memberLevel = member.getLevel();
            if (memberLevel == null || member.isRemoved()) {
                continue;
            }
            if (memberLevel.getBlockEntity(member.getBlockPos()) == member) {
                return true;
            }
        }
        return false;
    }

    /**
     * 接收端入口：记下 (X, 源网转速)，并用 {@code updateGeneratedRotation()} 把新容量/新转速推给自己那张网。
     * <p>
     * 设计定稿：不要自己调 {@code notifyStressCapacityChange} / {@code updateStressFor} /
     * {@code network.updateStress()} —— {@code updateGeneratedRotation()} 内部已经做了这些。
     */
    public void receiveFromPeer(float su, float speed) {
        if (level == null || level.isClientSide || isOutputSide()) {
            return;
        }
        // 心跳：即使值没变也要刷新，否则看门狗会把正常的桥判成断开
        lastPushGameTime = level.getGameTime();
        float clamped = Math.max(0F, su);
        if (bridgeStress == clamped && sourceSpeed == speed) {
            return;
        }
        bridgeStress = clamped;
        sourceSpeed = speed;
        updateGeneratedRotation();
        setChanged();
        // 让客户端护目镜拿到新的桥流量/源网转速
        sendData();
    }

    /** Sender 记下本次扣掉的 X，并把新的每-RPM 值推给源网。 */
    private void setBridgeStress(float su) {
        if (bridgeStress == su) {
            return;
        }
        bridgeStress = su;
        if (hasNetwork()) {
            KineticNetwork network = getOrCreateNetwork();
            if (network != null) {
                network.updateStressFor(this, calculateStressApplied());
            }
        }
        setChanged();
        // 让客户端护目镜拿到新的桥流量
        sendData();
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
    public int getPrimaryAmount() {
        return primaryAmount;
    }

    @Override
    public void setPrimaryAmount(int value) {
        int clamped = Math.max(0, value);
        if (primaryAmount == clamped) {
            return;
        }
        primaryAmount = clamped;
        setChanged();
        sendData();
    }

    @Override
    public int getSecondaryAmount() {
        return secondaryAmount;
    }

    @Override
    public void setSecondaryAmount(int value) {
        int clamped = Math.max(0, value);
        if (secondaryAmount == clamped) {
            return;
        }
        secondaryAmount = clamped;
        setChanged();
        sendData();
    }

    // ===================== 持久化 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeFrequency(tag, registries, clientPacket);
        tag.putInt(KEY_PRIMARY, primaryAmount);
        tag.putInt(KEY_SECONDARY, secondaryAmount);
        // 桥流量与源网转速是**真实状态**，必须落盘：
        // KineticNetwork 的记账约定是"卸载时把整张网容量存进 NBT，加载时 initFromTE() 把
        // unloadedCapacity 设成该值，随后每个回来的 BE 用 addSilently(be, lastCapacity, ...)
        // 以 lastCapacity × |getGeneratedSpeed()| 把自己那份减掉"。接收端的 getGeneratedSpeed()
        // 依赖 bridgeStress/sourceSpeed，若不落盘，加载时它返回 0 -> 减掉 0 -> 那份容量永远
        // 留在 unloadedCapacity 里（实测每重载一次凭空多一份 X）。
        // 不会因此"重启后凭空长期供电"：接收端看门狗在 20 tick 内收不到推送就会清零。
        tag.putFloat(KEY_BRIDGE_STRESS, bridgeStress);
        tag.putFloat(KEY_SOURCE_SPEED, sourceSpeed);
        // 其余只进客户端同步包：它们每 5 tick 就会被重算，落盘没有意义
        if (clientPacket) {
            tag.putInt(KEY_STATUS, debugStatus.ordinal());
            tag.putFloat(KEY_NET_CAPACITY, debugCapacity);
            tag.putFloat(KEY_NET_STRESS, debugStress);
            tag.putFloat(KEY_NET_IDLE, debugIdle);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readFrequency(tag, registries, clientPacket);
        primaryAmount = Math.max(0, tag.getInt(KEY_PRIMARY));
        secondaryAmount = Math.max(0, tag.getInt(KEY_SECONDARY));
        // 与 write 对称：桥流量/源网转速是真实状态，服务端也要读回来（否则加载时
        // getGeneratedSpeed() 为 0，KineticNetwork.addSilently 的减法会对不上账）
        bridgeStress = tag.getFloat(KEY_BRIDGE_STRESS);
        sourceSpeed = tag.getFloat(KEY_SOURCE_SPEED);
        if (clientPacket) {
            debugStatus = statusByIndex(tag.getInt(KEY_STATUS));
            debugCapacity = tag.getFloat(KEY_NET_CAPACITY);
            debugStress = tag.getFloat(KEY_NET_STRESS);
            debugIdle = tag.getFloat(KEY_NET_IDLE);
        }
    }

    private static BridgeStatus statusByIndex(int ordinal) {
        BridgeStatus[] values = BridgeStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BridgeStatus.UNBOUND;
    }

    // ===================== 护目镜 =====================

    /**
     * B10 的护目镜信息。
     * <p>
     * <b>为什么自己覆写、且<b>不</b>调 super</b>：Create 这条继承链上有两组会各自独立开关的信息：
     * <ul>
     *   <li>{@code KineticBlockEntity.addToGoggleTooltip}：{@code calculateStressApplied() == 0} 就整组不打（"动力学状态 / 应力影响"）；</li>
     *   <li>{@code GeneratingKineticBlockEntity.addToGoggleTooltip}：{@code calculateAddedStressCapacity() == 0} 就整组不打（"产能 / Capacity Provided"）。</li>
     * </ul>
     * 而本机的这两个值恰恰会在 0 与非 0 之间来回跳（输出端只在传的时候有应力、接收端只在收的时候有产能），
     * 于是护目镜里原版两组忽隐忽现、还和自己加的行交替出现 —— 看起来就是"互相争抢"。
     * 这里改成一次性输出一组稳定的调试信息（转速 + 桥自己的状态），不再依赖那两个开关。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean output = isOutputSide();
        // 方块名本身就带角色（…(输出端) / …(接收端)）
        lang().add(getBlockState().getBlock().getName()).style(ChatFormatting.GOLD).forGoggles(tooltip);

        lang().translate("wireless.goggles.speed", LangNumberFormat.format(Math.abs(getSpeed())))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (!hasFrequency()) {
            lang().translate("wireless.goggles.unbound")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        } else {
            lang().translate("wireless.goggles.frequency", String.valueOf(getFrequency()),
                            lang().translate(visibilityLangKey()))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        lang().translate("wireless.goggles.status", lang().translate(statusLangKey()))
                .style(statusColor())
                .forGoggles(tooltip, 1);

        // 唯一的接网面：轴/齿轮只能贴这一面，直接报出它朝哪边
        lang().translate("wireless.goggles.io_face",
                        getBlockState().getValue(WirelessStressBridgeBlock.FACING).getOpposite().getName())
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        lang().translate(output ? "wireless.goggles.transfer_out" : "wireless.goggles.transfer_in",
                        LangNumberFormat.format(bridgeStress))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        if (debugCapacity > 0F || debugStress > 0F) {
            lang().translate("wireless.goggles.net",
                            LangNumberFormat.format(debugCapacity),
                            LangNumberFormat.format(debugStress),
                            LangNumberFormat.format(debugIdle))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        if (output) {
            lang().translate("wireless.goggles.limit_send", limitText(primaryAmount))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else {
            lang().translate("wireless.goggles.source_speed",
                            LangNumberFormat.format(Math.abs(sourceSpeed)))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            lang().translate("wireless.goggles.limit_receive", limitText(primaryAmount))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            if (secondaryAmount > 0) {
                lang().translate("wireless.goggles.priority", String.valueOf(secondaryAmount))
                        .style(ChatFormatting.DARK_GRAY)
                        .forGoggles(tooltip, 1);
            }
        }
        return true;
    }

    /** 0 = 不限（与 GUI 空框一致，见 {@link #limitOf(int)}）。 */
    private static String limitText(int configured) {
        return configured <= 0 ? "∞" : String.valueOf(configured);
    }

    private String statusLangKey() {
        return "wireless.status." + debugStatus.name().toLowerCase(Locale.ROOT);
    }

    private String visibilityLangKey() {
        return "gui.avaritia_singularity_stress.visibility." + visibility.name().toLowerCase(Locale.ROOT);
    }

    private ChatFormatting statusColor() {
        return switch (debugStatus) {
            case SENDING, RECEIVING -> ChatFormatting.GREEN;
            case UNBOUND -> ChatFormatting.RED;
            default -> ChatFormatting.GOLD;
        };
    }

    // ===================== 调试指令 /wireless_debug 专用（只读，不改任何状态） =====================

    /**
     * 把本方块实体的全部相关参数整理成多行文本，供 {@code /wireless_debug} 打进系统日志。
     * <p>
     * <b>只读</b>：不写 NBT、不发包、不改应力网络。读取 {@code calculateCapacity()/calculateStress()}
     * 与 Create 自身行为一致（它们内部会顺带清掉失效成员）。
     */
    public List<String> debugDump() {
        List<String> out = new ArrayList<>();
        out.add("类型=B10 无线应力传输器 "
                + (isOutputSide() ? "输出端 SENDER(output=true，扣源网)" : "接收端 RECEIVER(output=false，给本网加容量)")
                + " state=" + getBlockState());
        out.add("接入面(唯一的输入输出面)="
                + getBlockState().getValue(WirelessStressBridgeBlock.FACING).getOpposite()
                + "  ← 只有这一面能接轴/齿轮；其余五面不连网");
        out.add("频率=" + (hasFrequency() ? String.valueOf(frequency) : "未绑定(-1)")
                + " 可见性=" + visibility
                + " owner=" + (owner == null ? "null" : owner)
                + " key=" + getKey());
        out.add("配置: primary(" + (isOutputSide() ? "splitSuLimit=每次最多分给对端" : "drainSuLimit=自身最多接收")
                + ")=" + limitText(primaryAmount)
                + " secondary(优先级，本方案未用)=" + secondaryAmount + "   [0 或负数 = 不限]");
        out.add("跨桥量 bridgeStress(X)=" + bridgeStress
                + " sourceSpeed=" + sourceSpeed
                + " 看门狗 lastPushGameTime=" + lastPushGameTime
                + (level == null ? "" : " age=" + (level.getGameTime() - lastPushGameTime)
                        + " tick (> " + RECEIVER_TIMEOUT_TICKS + " 会清零)")
                + " debugStatus=" + debugStatus.name());
        out.add("动能: hasNetwork=" + hasNetwork() + " networkId=" + network
                + " speed=" + speed + " theoreticalSpeed=" + getTheoreticalSpeed()
                + " generatedSpeed=" + getGeneratedSpeed()
                + " isSource=" + isSource() + " hasSource=" + hasSource()
                + " overStressed=" + isOverStressed()
                + " lastStressApplied(每RPM)=" + lastStressApplied
                + " lastCapacityProvided(每RPM)=" + lastCapacityProvided);

        KineticNetwork net = hasNetwork() ? getOrCreateNetwork() : null;
        if (net == null) {
            out.add("网络: 未接入 (hasNetwork=" + hasNetwork() + ", getOrCreateNetwork()=null)"
                    + "  ← 必须把轴/齿轮接到接网面（上面报的那一个面）才会工作");
        } else {
            float capacity = net.calculateCapacity();
            float stress = net.calculateStress();
            out.add("网络: id=" + net.id + " capacity=" + capacity + " stress=" + stress
                    + " idle=" + (capacity - stress)
                    + " members=" + net.members.size() + " sources=" + net.sources.size()
                    + " | 同步缓存 cap=" + debugCapacity + " stress=" + debugStress + " idle=" + debugIdle);

            // KineticNetwork.calculateCapacity() = Σ在网源 + unloadedCapacity（后者私有、无 getter），
            // 所以用差值把"未加载残留"反算出来 —— 它非 0 就说明有容量被凭空算进来了。
            float sourceSum = 0F;
            for (Map.Entry<KineticBlockEntity, Float> entry : net.sources.entrySet()) {
                sourceSum += net.getActualCapacityOf(entry.getKey());
            }
            float memberSum = 0F;
            for (KineticBlockEntity member : net.members.keySet()) {
                memberSum += net.getActualStressOf(member);
            }
            out.add("网络明细: Σ在网源=" + sourceSum + " -> 未加载残留容量=" + (capacity - sourceSum)
                    + " | Σ在网消耗=" + memberSum + " -> 未加载残留应力=" + (stress - memberSum)
                    + " | getSize()=" + net.getSize() + " 未加载成员数="
                    + (net.getSize() - net.members.size())
                    + "   (残留容量非 0 = 该网凭空多出容量)");

            if (isOutputSide()) {
                float headroom = capacity - stress;
                float split = primaryAmount <= 0 ? Float.MAX_VALUE : primaryAmount;
                out.add("Sender 本 tick 可传: X = min(idle=" + headroom + ", split=" + limitText(primaryAmount)
                        + ") = " + Math.min(headroom, split) + "   (随后还要被对端 drainSuLimit 再夹一次)");
            }
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = getBlockPos().relative(direction);
            out.add("   邻居 " + direction + " " + neighbourPos + " = "
                    + BuiltInRegistries.BLOCK.getKey(level.getBlockState(neighbourPos).getBlock())
                    + "   (输出端/接收端都要把轴/齿轮接到唯一接网面，见上面 hasNetwork)");
        }

        double range = WirelessConfig.stressBridgeRange();
        out.add("配对(range=" + range + "): " + candidatesText(range));
        return out;
    }

    /** 列出同频道、同维度、距离内的合格对端，并说明一对一要求。 */
    private String candidatesText(double range) {
        List<WirelessDevice> list = WirelessService.candidates(this, range);
        StringBuilder sb = new StringBuilder("candidates=").append(list.size()).append(" -> [");
        for (int i = 0; i < list.size(); i++) {
            WirelessDevice device = list.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(device.getBlockPos());
            if (device instanceof WirelessStressBridgeBlockEntity other) {
                sb.append(other.isOutputSide() ? "(输出端)" : "(接收端)");
            } else {
                sb.append('(').append(device.getClass().getSimpleName()).append(')');
            }
        }
        sb.append(']');
        if (list.size() != 1) {
            sb.append(list.isEmpty()
                    ? "  ← 无对端：查频率/可见性(私有要同 owner)/距离/是否被拆"
                    : "  ← 一对一要求恰好 1 个；≥2 会判为歧义而整体不工作");
        }
        return sb.toString();
    }

    /** 与 B6 一致：不能用 CreateLang.translate（它会前缀成 create.）。 */
    private static LangBuilder lang() {
        return new LangBuilder(AvaritiaSingularityStressMod.MODID);
    }
}
