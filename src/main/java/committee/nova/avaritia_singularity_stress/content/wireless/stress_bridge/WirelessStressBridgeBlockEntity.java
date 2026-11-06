package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.IWirelessConfigHolder;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessGuiKind;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessKey;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessService;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessStatusView;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
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

    /** B23：分配算法里的"视为 0"阈值，避免浮点残渣导致无限补齐。 */
    private static final float FLOAT_EPS = 1.0e-4F;

    /** B21 档位转速的默认值与上限，取值与创造马达一致（{@code CreativeMotorBlockEntity.java:39-40}）。 */
    private static final int DEFAULT_GEAR_SPEED = 16;
    private static final int MAX_GEAR_SPEED = 256;

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

    /** B23：并流账本（每路来源的 SU），key 是来源坐标的 {@code asLong()} 字符串。 */
    private static final String KEY_INFLOW = "Inflow";
    private static final String KEY_STATUS = "BridgeStatus";
    private static final String KEY_NET_CAPACITY = "NetCapacity";
    private static final String KEY_NET_STRESS = "NetStress";
    private static final String KEY_NET_IDLE = "NetIdle";

    /**
     * B20：同频道设备台数（界面状态区用）。
     * <p>
     * 必须单独同步：客户端没有频道表，算不出这个数
     * （{@code WirelessService.java:77} 对客户端直接返回空列表）。
     */
    private static final String KEY_CANDIDATES = "PeerCandidates";

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

    /**
     * 输出端 = 本次最多送出多少 SU（还要与 B22 的百分比滑块取小）；
     * 接收端 = <b>所有来源合计</b>最多接收多少 SU（B23 并流时按"剩余额度"分配给各 sender）。
     */
    private int primaryAmount;

    /** 接收端 = 优先级。B23 的分流策略定为「均分+补齐」，本字段仍不参与计算，整体雪藏。 */
    private int secondaryAmount;

    /**
     * 当前跨桥流动的实际 SU：Sender 记为"已扣源网多少"，Receiver 记为"已给本网加多少"。
     * 不持久化 —— 每 5 tick 会重算。
     */
    private float bridgeStress;

    /** Receiver 记下最近一路来源的转速，仅供护目镜参考（B21 起本网转速由档位决定）。 */
    private float sourceSpeed;

    /**
     * B23：每路来源的流入量（接收端专用）。key = 来源 sender 的坐标，value = 它这一路给的实际 SU。
     * <p>
     * <b>为什么必须换成 Map</b>：单值 {@code bridgeStress} 撑不住「多对一并流」——
     * 两个 sender 会互相覆盖，最后只剩一个的值生效。
     * 对外仍然只暴露 {@link #bridgeStress}（= 本 Map 的求和），
     * 所以 {@code calculateAddedStressCapacity()} 与 KineticNetwork 的记账约定完全不用改。
     */
    private final Map<BlockPos, Float> inflow = new HashMap<>();

    /** B23：每路来源的最后推送时刻；看门狗按来源逐个清理，不再一刀切。 */
    private final Map<BlockPos, Long> inflowTime = new HashMap<>();

    // ===================== 护目镜调试数据（服务端每 5 tick 刷新，随 NBT 同步给客户端） =====================

    /** 当前状态（为什么在传 / 为什么不传）。 */
    private BridgeStatus debugStatus = BridgeStatus.UNBOUND;

    /** 本机所在网络的产能 / 消耗 / 空闲（su）；输出端 = 它扣的那张源网，接收端 = 它供的那张网。 */
    private float debugCapacity;
    private float debugStress;
    private float debugIdle;

    /**
     * B20：同频道设备台数，服务端每 5 tick 算一次随同步包下发。
     * <p>
     * {@code -1} = 还没算过（刚放置/刚改频率），界面据此显示「检测中」而不是「0 台」。
     */
    private int debugCandidates = -1;

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

    /**
     * B21：档位转速滚轮（只有接收端有，见 {@link #addBehaviours}）。
     * <p>
     * 用 Create 现成的 {@link com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour}
     * 而不是自绘 —— 玩家在世界里看到的、按住右键拖出来的，就是创造马达那一颗滚轮。
     */
    public WirelessStressBridgeGearBehaviour gearSpeed;

    /** B22：输出端的「占用源网空闲百分比」滚轮（0~100）。 */
    public WirelessStressBridgeSendRatioBehaviour sendRatio;

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // 两端各挂一颗，语义不同、互不干扰：
        //   输出端 = 愿意用掉源网空闲的百分之多少（B22）
        //   接收端 = 本网跑多快的档位（B21）
        if (isOutputSide()) {
            sendRatio = new WirelessStressBridgeSendRatioBehaviour(
                    Component.translatable("gui.avaritia_singularity_stress.wireless.send_ratio"),
                    this, new WirelessStressBridgeValueBox());
            sendRatio.between(0, 100);
            sendRatio.value = 100;
            behaviours.add(sendRatio);
            return;
        }
        gearSpeed = new WirelessStressBridgeGearBehaviour(
                Component.translatable("gui.avaritia_singularity_stress.wireless.gear_speed"),
                this, new WirelessStressBridgeValueBox());
        gearSpeed.between(-MAX_GEAR_SPEED, MAX_GEAR_SPEED);
        gearSpeed.value = DEFAULT_GEAR_SPEED;
        // 改档位要立刻让所在网络重新记账，否则本网容量对不上（见 getGeneratedSpeed / calculateAddedStressCapacity）
        gearSpeed.withCallback(i -> updateGeneratedRotation());
        behaviours.add(gearSpeed);
    }

    @Override
    public void initialize() {
        super.initialize();
        WirelessNetwork.add(this);
        // B21：照抄 CreativeMotorBlockEntity.initialize 的判断（CreativeMotorBlockEntity.java:61-65）——
        // 网络恢复/首次加载时要把自己的转速与容量重新推给网络，否则本网会按旧账本算。
        if (!isOutputSide() && (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed())) {
            updateGeneratedRotation();
        }
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

    /**
     * B21：打开配置面板。
     * <p>
     * <b>为什么抽成 BE 方法</b>：现在有两个入口 —— 方块的 {@code useWithoutItem}
     * （点在非滚轮区域）与滚轮的 {@code onShortInteract}（点在滚轮区域，短按）。
     * 两处各写一遍 {@code openMenu} 迟早会漂移（例如只改了一边的 title）。
     */
    public void openConfigMenu(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new WirelessConfigMenu(
                        ModMenus.WIRELESS_CONFIG.get(), id, inventory, this),
                Component.translatable(getBlockState().getBlock().getDescriptionId())),
                buffer -> buffer.writeBlockPos(getBlockPos()));
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

    /**
     * B20：界面状态区的数据来源。
     * <p>
     * 这些字段原本只喂给护目镜（{@link #addToGoggleTooltip}），界面一个都没读到 ——
     * 于是"改了频率还是不传"时，玩家开界面完全看不到原因。这里把它们一次性交出去。
     * <p>
     * {@code debugStatus == UNBOUND} 但已经有频率 = 还没跑过第一次无线 tick（刚放置 / 刚改频率），
     * 这时报「未绑定频率」会误导（频率明明就摆在界面上），所以单独报 pending。
     */
    @Nullable
    @Override
    public WirelessStatusView statusView() {
        String key = hasFrequency() && debugStatus == BridgeStatus.UNBOUND
                ? WirelessStatusView.PENDING
                : debugStatus.name().toLowerCase(Locale.ROOT);
        return new WirelessStatusView(key, bridgeStress, debugCapacity, debugStress, debugIdle,
                debugCandidates, WirelessConfig.stressBridgeRange());
    }

    // ===================== 动能：源网 / 接收端网 =====================

    /**
     * 接收端本网的「档位转速」（B21）。
     * <p>
     * <b>语义（已确认：档位，不是油门）</b>：滚轮设定的是<b>本网跑多快</b>，
     * 而不是「传输多少」。所以跨桥的实际 SU 依然是 X（见 {@link #bridgeStress}），
     * 改变的只是 X 被折算成「每 RPM 多少容量」的比例：
     * <ul>
     *   <li>容量（每 RPM）= X / |档位转速|，本网转速 = 档位转速；</li>
     *   <li>两者相乘 = X，<b>实际容量守恒</b>，不会因为调档而凭空多出或少掉应力；</li>
     *   <li>而本网每台设备的消耗 = 它的每RPM值 × |档位转速|，
     *       所以档位越低，同一批设备吃掉的 SU 越少 —— 同一份 X 能带动更多设备，代价是转速慢。</li>
     * </ul>
     * 这就是「用转速分流」的落点：同一个 X 可以按玩家需要切成「高速小力矩」或「低速大力矩」。
     * <p>
     * <b>与改动前（跟随源网转速）的区别</b>：原来这里返回 {@link #sourceSpeed}，
     * 玩家无法干预；现在 {@code sourceSpeed} 只作为护目镜上的参考值显示。
     * <p>
     * 输出端恒返回 0 —— 它是纯粹的应力消费者，不能成为源。
     */
    @Override
    public float getGeneratedSpeed() {
        if (isOutputSide() || bridgeStress <= 0F) {
            return 0F;
        }
        return gearSpeed == null ? 0F : gearSpeed.getValue();
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
        // B23：报「同频道里有几个输出端」。原来报的是全部同频道设备数，
        // 并流之后那个数字会把别的接收端也算进来，玩家看不出到底有几个源在喂自己。
        updateDebug(receiverStatus(), liveNetwork(), peerSenderCount());
    }

    /** B23：同频道里的输出端数量（接收端用来看「有几个源在喂我」）。 */
    private int peerSenderCount() {
        int count = 0;
        for (WirelessDevice device : WirelessService.candidates(this, WirelessConfig.stressBridgeRange())) {
            if (device instanceof WirelessStressBridgeBlockEntity bridge && bridge.isOutputSide()) {
                count++;
            }
        }
        return count;
    }

    /**
     * B20：同频道（含可见性/所有者过滤）且距离内的设备台数。
     * <p>
     * 输出端原来调 {@code WirelessService.findPeer}，它内部会调 {@code candidates} 再判
     * {@code size() == 1}（{@code WirelessService.java:62-65}）。这里让 tickSender 直接拿列表、
     * 自己判唯一性，**同一次遍历**既得到对端、又得到台数，不额外扫一遍频道表。
     * <p>
     * 注意：频道表与距离判定都只在服务端可用（{@code WirelessService.java:77}），
     * 所以这个值必须靠 {@code sendData()} 同步给客户端界面看。
     */
    private int peerCount() {
        return WirelessService.candidates(this, WirelessConfig.stressBridgeRange()).size();
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
     * @param status     当前状态
     * @param network    用来显示产能/消耗/空闲的网络；null 表示读不到（保持上次的数值）
     * @param candidates B20：同频道设备台数（界面用）；-1 表示本次不更新
     */
    private void updateDebug(BridgeStatus status, @Nullable KineticNetwork network, int candidates) {
        BridgeStatus oldStatus = debugStatus;
        float oldCapacity = debugCapacity;
        float oldStress = debugStress;
        float oldIdle = debugIdle;
        int oldCandidates = debugCandidates;

        debugStatus = status;
        if (network != null) {
            debugCapacity = network.calculateCapacity();
            debugStress = network.calculateStress();
            debugIdle = Math.max(0F, debugCapacity - debugStress);
        }
        if (candidates >= 0) {
            debugCandidates = candidates;
        }

        if (oldStatus != debugStatus || !Mth.equal(oldCapacity, debugCapacity)
                || !Mth.equal(oldStress, debugStress) || !Mth.equal(oldIdle, debugIdle)
                || oldCandidates != debugCandidates) {
            sendData();
        }
    }

    /**
     * B23：接收端看门狗。按<b>每路来源</b>独立计时，超时就把那一路从并流里摘掉。
     * <p>
     * 改动前只有一个 {@code lastPushGameTime}，多源时会互相刷新计时器 ——
     * 拆掉其中一个 sender，只要另一个还在推，残留的那份 X 就永远清不掉（凭空供电）。
     */
    private void tickReceiverWatchdog() {
        if (inflow.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        boolean anyExpired = inflowTime.values().removeIf(t -> now - t > RECEIVER_TIMEOUT_TICKS);
        if (!anyExpired) {
            return;
        }
        // 时刻表里没有的，就是已经超时的来源；把它们从流入表里一并摘掉
        inflow.keySet().removeIf(pos -> !inflowTime.containsKey(pos));
        recomputeInflow();
    }

    /** B23：把所有来源的流入量求和，写回 {@link #bridgeStress} 并推给本网。 */
    private void recomputeInflow() {
        float sum = sumInflow();
        if (sum == bridgeStress) {
            return;
        }
        bridgeStress = sum;
        updateGeneratedRotation();
        setChanged();
        sendData();
    }

    private float sumInflow() {
        float sum = 0F;
        for (float value : inflow.values()) {
            sum += value;
        }
        return sum;
    }

    /**
     * B23：除指定来源之外，当前从其它来源已收到的总量。
     * <p>
     * 给 sender 算「这个接收端还剩多少接收额度」用。并流时如果只看单路，
     * 三个 sender 各自都以为对方的上限是全部额度，加起来就会突破玩家设的「接收上限」。
     * 服务端是单线程、BE 又是同一个对象，所以先 tick 的 sender 写进 {@link #inflow} 后，
     * 后 tick 的 sender 立刻就能看见 —— 不需要额外的全局协调。
     *
     * @param source 调用方 sender 自己的坐标（要排除掉，因为里面可能还留着它上一 tick 的旧值）
     */
    public float inflowTotalExcept(BlockPos source) {
        float sum = 0F;
        for (Map.Entry<BlockPos, Float> entry : inflow.entrySet()) {
            if (!entry.getKey().equals(source)) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    /**
     * Sender：算本次可送出的 X → 分给频道内全部接收端（均分+补齐）→ 各自 {@code receiveFromPeer}。
     * <p>
     * <b>B23 改了什么</b>：原来「候选≠1 就停机」（{@code WirelessService.java:62-65} 的一对一语义），
     * 现在改成「频道内所有接收端都算对端」。于是同一个频道可以：
     * <ul>
     *   <li>N 个 sender → 1 个 receiver：接收端按来源累加（并流）；</li>
     *   <li>1 个 sender → N 个 receiver：这里的分配算法负责切分（分流）。</li>
     * </ul>
     * 频道（频率 + 可见性 + owner）仍然是边界：{@code WirelessService.candidates} 已经按
     * {@code WirelessKey} 过滤过，所以跨频道不会互相污染。
     */
    private void tickSender() {
        if (!hasFrequency()) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.UNBOUND, null, 0);
            return;
        }
        // 一次遍历同时做两件事：挑出接收端、给界面报台数
        List<WirelessDevice> candidates = WirelessService.candidates(this, WirelessConfig.stressBridgeRange());
        List<WirelessStressBridgeBlockEntity> receivers = new ArrayList<>();
        for (WirelessDevice device : candidates) {
            if (device instanceof WirelessStressBridgeBlockEntity bridge && !bridge.isOutputSide()) {
                receivers.add(bridge);
            }
        }
        int peerCount = receivers.size();

        // 设计定稿：getOrCreateNetwork() 可能为 null，必须先 hasNetwork()，拿到后仍要判空
        KineticNetwork network = liveNetwork();
        if (network == null) {
            // 没接轴 / 接错面：整台机器不在任何应力网络里，源网无从谈起
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_NETWORK, null, peerCount);
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
            updateDebug(BridgeStatus.NO_HEADROOM, network, peerCount);
            return;
        }

        // B22：两个来源取小 —— 百分比滑块决定"愿意用掉多少空闲"，GUI 数字框决定"绝对上限"
        float ratio = sendRatio == null ? 1F : Mth.clamp(sendRatio.getValue(), 0, 100) / 100F;
        float x = Math.min(headroom * ratio, limitOf(primaryAmount));
        if (x <= 0F) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_HEADROOM, network, peerCount);
            return;
        }

        if (receivers.isEmpty()) {
            setBridgeStress(0F);
            updateDebug(BridgeStatus.NO_PEER, network, 0);
            return;
        }

        float delivered = distributeToReceivers(receivers, x);
        // 全部被各端的「接收上限」挡回来时，实际送出是 0，状态要如实报 NO_PEER 而不是「传输中」
        setBridgeStress(delivered);
        updateDebug(delivered > 0F ? BridgeStatus.SENDING : BridgeStatus.NO_PEER, network, peerCount);
    }

    /**
     * B23 分流算法：<b>均分 + 补齐</b>（用户已确认的策略）。
     * <p>
     * 做法与"往几个不同容量的杯子里平倒水"一致：
     * <ol>
     *   <li>第一轮把所有还能接的接收端均分一份，每份不超过它自己的「接收上限」；</li>
     *   <li>有人吃不下的份额会剩下来，第二轮再均分给仍有余量的人；</li>
     *   <li>两轮后仍然剩的就留给自己（不浪费，也不外溢到别的频道）。</li>
     * </ol>
     * 两轮足够：均分后最多只有"部分人吃饱"一种情况需要补，补完要么分完、要么全都吃饱。
     * <p>
     * 关键：<b>每一路每个 tick 都要调用一次 {@code receiveFromPeer}</b>（哪怕是 0），
     * 因为接收端靠这次调用刷新"这一路还活着"的心跳；漏掉就会被看门狗判成断开。
     *
     * @return 实际送出去的 SU 总量（≤ total）
     */
    private float distributeToReceivers(List<WirelessStressBridgeBlockEntity> receivers, float total) {
        int n = receivers.size();
        float[] caps = new float[n];
        float[] got = new float[n];
        for (int i = 0; i < n; i++) {
            // 接收端自己的接收上限；0/负数 = 不限。
            // B23：并流时还要扣掉「别的 sender 已经喂进去的量」，让上限约束的是总和而不是单路。
            WirelessStressBridgeBlockEntity receiver = receivers.get(i);
            float cap = limitOf(receiver.getPrimaryAmount());
            if (cap != Float.MAX_VALUE) {
                cap = Math.max(0F, cap - receiver.inflowTotalExcept(getBlockPos()));
            }
            caps[i] = cap;
        }
        float remaining = total;
        for (int round = 0; round < 2 && remaining > FLOAT_EPS; round++) {
            int hungry = 0;
            for (int i = 0; i < n; i++) {
                if (got[i] < caps[i] - FLOAT_EPS) {
                    hungry++;
                }
            }
            if (hungry == 0) {
                break;
            }
            float share = remaining / hungry;
            for (int i = 0; i < n; i++) {
                if (got[i] >= caps[i] - FLOAT_EPS) {
                    continue;
                }
                float give = Math.min(share, caps[i] - got[i]);
                got[i] += give;
                remaining -= give;
            }
        }
        for (int i = 0; i < n; i++) {
            receivers.get(i).receiveFromPeer(getBlockPos(), got[i], getTheoreticalSpeed());
        }
        return Math.max(0F, total - remaining);
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
     * B23：接收端入口（多源并流）。记下「这一路来源给了多少 SU」，再按求和推给本网。
     * <p>
     * <b>为什么要带 {@code source}</b>：一对一时只有一路，单值就够；并流后必须按来源分别记账，
     * 否则第二个 sender 会把第一个覆盖掉，而且看门狗也没法单独清理某一路。
     * <p>
     * 设计定稿仍然有效：不要自己调 {@code notifyStressCapacityChange} / {@code updateStressFor} /
     * {@code network.updateStress()} —— {@code updateGeneratedRotation()} 内部已经做了这些。
     *
     * @param source 来源 sender 的坐标（作为并流账本的 key）
     * @param su     这一路本次给的实际 SU；0 表示该来源停止供能
     * @param speed  该来源的源网转速（B21 起只用于护目镜显示）
     */
    public void receiveFromPeer(BlockPos source, float su, float speed) {
        if (level == null || level.isClientSide || isOutputSide()) {
            return;
        }
        // 心跳：即使值没变也要刷新，否则看门狗会把正常的一路判成断开
        inflowTime.put(source, level.getGameTime());
        float clamped = Math.max(0F, su);
        Float previous = inflow.put(source, clamped);
        sourceSpeed = speed;
        boolean unchanged = previous != null && previous.floatValue() == clamped
                && bridgeStress == sumInflow();
        if (unchanged) {
            return;
        }
        recomputeInflow();
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
        // B23：并流账本也要落盘，理由同上 —— 容量来自 Σinflow，
        // 只存总和而不存明细的话，重载后看门狗没法按来源清理（会整块清零或整块残留）。
        CompoundTag inflowTag = new CompoundTag();
        for (Map.Entry<BlockPos, Float> entry : inflow.entrySet()) {
            inflowTag.putFloat(Long.toString(entry.getKey().asLong()), entry.getValue());
        }
        tag.put(KEY_INFLOW, inflowTag);
        // 其余只进客户端同步包：它们每 5 tick 就会被重算，落盘没有意义
        if (clientPacket) {
            tag.putInt(KEY_STATUS, debugStatus.ordinal());
            tag.putFloat(KEY_NET_CAPACITY, debugCapacity);
            tag.putFloat(KEY_NET_STRESS, debugStress);
            tag.putFloat(KEY_NET_IDLE, debugIdle);
            // B20：候选台数只在服务端算得出来，必须随同步包下发
            tag.putInt(KEY_CANDIDATES, debugCandidates);
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
        // B23：并流明细（key = 来源坐标的 asLong 字符串）。时刻表不落盘 ——
        // 重启后各路 sender 会在 RECEIVER_TIMEOUT_TICKS 内重新心跳，届时自动补齐。
        inflow.clear();
        inflowTime.clear();
        CompoundTag inflowTag = tag.getCompound(KEY_INFLOW);
        for (String key : inflowTag.getAllKeys()) {
            try {
                inflow.put(BlockPos.of(Long.parseLong(key)), inflowTag.getFloat(key));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，不让一个坏键把整个方块实体的加载搞崩
            }
        }
        if (!inflow.isEmpty()) {
            bridgeStress = sumInflow();
        }
        if (clientPacket) {
            debugStatus = statusByIndex(tag.getInt(KEY_STATUS));
            debugCapacity = tag.getFloat(KEY_NET_CAPACITY);
            debugStress = tag.getFloat(KEY_NET_STRESS);
            debugIdle = tag.getFloat(KEY_NET_IDLE);
            // 旧存档/旧包没有这个键：保持 -1（界面显示"检测中"），别误报 0 台
            debugCandidates = tag.contains(KEY_CANDIDATES) ? tag.getInt(KEY_CANDIDATES) : -1;
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
                            // 注意：这里必须用 Component.translatable（完整键），不能交给 lang().translate。
                            // lang() 是 new LangBuilder(MODID)（见本文件末尾 lang()），translate 会自动前缀
                            // "avaritia_singularity_stress."，而 visibilityLangKey() 本身就是完整键，
                            // 结果会拼成 …avaritia_singularity_stress.gui.avaritia_singularity_stress.visibility.private
                            // ——实机护目镜里显示成一长串原始键名，就是这个原因。
                            Component.translatable(visibilityLangKey()))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        }

        lang().translate("wireless.goggles.status", lang().translate(statusLangKey()))
                .style(statusColor())
                .forGoggles(tooltip, 1);

        // 唯一的接网面：轴/齿轮只能贴这一面，直接报出它朝哪边（= FACING，见方块类注释）
        lang().translate("wireless.goggles.io_face",
                        getBlockState().getValue(WirelessStressBridgeBlock.FACING).getName())
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
            // B21：档位转速（本网实际跑多快）。与上面那行「源网转速」并列，
            // 因为两者现在可以不同 —— 源网转速只是参考，档位才是本网真值。
            lang().translate("wireless.goggles.gear_speed",
                            String.valueOf(gearSpeed == null ? 0 : Math.abs(gearSpeed.getValue())))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            lang().translate("wireless.goggles.limit_receive", limitText(primaryAmount))
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            // [B20 雪藏] 优先级字段当前不参与任何计算（见 secondaryAmount 字段注释）。
            // 在护目镜里显示一个"设了也不生效"的数字，比不显示更误导，故整体注释保留。
            // 恢复优先级功能时，把下面这段原样放回即可（注意别再同时出现在界面里，
            // 除非真的接进 tickSender 的分配逻辑）。
            // if (secondaryAmount > 0) {
            //     lang().translate("wireless.goggles.priority", String.valueOf(secondaryAmount))
            //             .style(ChatFormatting.DARK_GRAY)
            //             .forGoggles(tooltip, 1);
            // }
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
                + getBlockState().getValue(WirelessStressBridgeBlock.FACING)
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
                // B23：并流后看门狗是按来源计时的，这里把每路的状态都摊开
                + " 并流来源=" + inflow.size() + " 路"
                + (inflow.isEmpty() ? ""
                        : " " + inflow.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue()
                                        + "@" + (level == null ? "?" :
                                                (level.getGameTime() - inflowTime.getOrDefault(e.getKey(), 0L)) + "t"))
                                .toList())
                + " (> " + RECEIVER_TIMEOUT_TICKS + " tick 无推送的那路会被摘掉)"
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
