package committee.nova.avaritia_singularity_stress.content.singularity;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.singularity_rotation_speed_controller.SingularityRotationSpeedControllerBlockEntity;
import committee.nova.avaritia_singularity_stress.content.singularity_stabilizer.SingularityStabilizerBlockEntity;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 奇点应力(ssu)的共用定义。
 * <p>
 * Create 的应力是网络中一个不区分单位的 float 池,无法新增第二种"应力类型"。
 * 因此 ssu 不是独立资源,而是<b>由每台奇点压缩器(B6)产出的量化属性</b>:
 * <ul>
 *   <li><b>产出由空闲应力驱动</b>:空闲应力 = 网络总产能 − 全网实际消耗。
 *       每台运转中的 B6 产出 {@code max(0, 空闲应力 − 产能门槛) / 压缩比};
 *       空闲越多压得越多,小电站连门槛都够不到。</li>
 *   <li><b>SSU 沿 B6 子链路传递</b>,不全网共享:机器沿自身 source 链向上回溯,
 *       链路上每台运转中的 B6 都把自己的产出贡献给它;不在任何 B6 子链路里的
 *       机器看不到 SSU。</li>
 *   <li><b>每台机器各自的奇点浓度</b>:{@code 浓度 = 可见SSU ÷ (本机应力消耗 ÷ 压缩比)}。
 *       浓度 ≥ 100% 且链路上存在 B6(即位于 B6 输出侧)时进入奇点状态,
 *       并按转速进一步分级为超激发态 / 终极激发态。</li>
 *   <li><b>品控损耗区间浮动</b>:{@code 损耗 = 下限 + (上限 − 下限) × 空闲比例},
 *       空闲越多损耗越高;B14 在该 B6 上链路时归零。多台 B14 各管各的下游。</li>
 * </ul>
 * 所有数值从 {@link SingularityConfig} 读取。
 * 原版 Create 机器只读取 SU 部分,ssu 对它们无效。
 * <p>
 * 注意:网络扫描仅覆盖已加载区块的成员;source 链回溯遇到未加载区块时,
 * 该机器视为不可见任何 SSU(与旧版 isChainLinked 的保守行为一致)。
 * {@link #analyze} 与缓存方法只应在服务端 tick 中调用;
 * 客户端显示(护目镜/Jade)使用机器 BE 同步过的字段。
 */
public final class SingularityStress {

    // ================= 固定常量(数值型参数见 SingularityConfig) =================

    /**
     * 终极激发态追加产量。
     * <p>
     * 仅极限档在 ≥{@link #ULTIMATE_RPM} 时解锁;设为 0 则终极激发态与超激发态产量相同
     * (只作为称号)。
     */
    public static final int ULTIMATE_BONUS = 1;

    /** 终极激发态的转速下限(≥)。 */
    public static final float ULTIMATE_RPM = 256.0F;

    /**
     * B6 护目镜"待机"显示阈值:低于该转速视为停转。
     * <p>
     * 注意这<b>不是产出限制</b>——ssu 产出取决于空闲应力,与 B6 转速无关;
     * 本常量仅用于让护目镜在停转时显示"待机"。
     */
    public static final float OPERATING_RPM = 1.0F;

    /** 来源链回溯深度上限,防止异常拓扑造成死循环。 */
    private static final int MAX_DEPTH = 64;

    /**
     * 阶段 1 调试开关:置 true 后,B6 每次 lazyTick 会在日志中报告周围方块是否被识别为 ssu。
     * 验证来源链判定通过后请改回 false(或删除调试输出)。
     */
    public static final boolean DEBUG_SOURCE_CHAIN = false;

    private SingularityStress() {
    }

    // ================= 状态 =================

    /**
     * 奇点状态的四个层级。
     * <p>
     * 由浓度(是否满 100%)与转速共同决定;{@link #key} 指向 lang 键(带 modid 前缀)。
     */
    public enum SingularityState {
        /** 未进入奇点态(浓度不足,或不在 B6 输出侧)。 */
        NONE("singularity.state.none"),
        /** 一般奇点态。 */
        ACTIVE("singularity.state.active"),
        /** 奇点态 · 超激发态(转速超过该机器档位的超激发阈值)。 */
        OVEREXCITED("singularity.state.overexcited"),
        /** 奇点态 · 终极激发态(极限档达到 256 RPM)。 */
        ULTIMATE("singularity.state.ultimate");

        private final String key;

        SingularityState(String key) {
            this.key = key;
        }

        /** lang 键(不含 modid 前缀)。 */
        public String key() {
            return key;
        }

        public boolean atLeast(SingularityState other) {
            return ordinal() >= other.ordinal();
        }
    }

    /**
     * 由浓度与转速派生状态。
     *
     * @param concentration     奇点浓度(0~1,1 表示已达 100%)
     * @param chainLinked       是否位于 B6 输出侧(source 链可回溯到至少一台 B6)
     * @param speed             当前转速
     * @param overexciteEligible 该档是否可进入超激发态(收集器需 overexciteBonus&gt;0)
     * @param overexciteRpm     超激发阈值(严格大于)
     * @param ultimateEligible  该档是否可进入终极激发态(仅极限档)
     */
    public static SingularityState stateOf(float concentration, boolean chainLinked, float speed,
                                           boolean overexciteEligible, float overexciteRpm,
                                           boolean ultimateEligible) {
        if (!chainLinked || concentration < 1.0F)
            return SingularityState.NONE;

        float abs = Math.abs(speed);
        if (ultimateEligible && abs >= ULTIMATE_RPM)
            return SingularityState.ULTIMATE;
        if (overexciteEligible && abs > overexciteRpm)
            return SingularityState.OVEREXCITED;
        return SingularityState.ACTIVE;
    }

    // ================= 数据结构 =================

    /**
     * 网络级快照:同网络内所有机器共用,每游戏刻刷新一次。
     *
     * @param capacity   网络总产能(su)
     * @param consumption 全网实际消耗(su)
     * @param idleStress 空闲应力 = capacity − consumption(下限 0)
     */
    public record NetworkSnapshot(float capacity, float consumption, float idleStress) {
        public static final NetworkSnapshot EMPTY = new NetworkSnapshot(0.0F, 0.0F, 0.0F);
    }

    /**
     * 单台 B6 的判定结果(每游戏刻每台只算一次)。
     *
     * @param running      是否运转中(理论转速 ≠ 0)
     * @param qcSuppressed 品控损耗是否被上游链路中的 B14 抑制
     * @param qcLoss       本台品控损耗(SU/RPM),被抑制时为 0
     * @param production   本台产出的 ssu(未运转时为 0)
     */
    public record CompressorInfo(boolean running, boolean qcSuppressed, float qcLoss, float production) {
        public static final CompressorInfo IDLE =
                new CompressorInfo(false, false, 0.0F, 0.0F);
    }

    /**
     * 单台机器的 source 链回溯结果(每游戏刻每台只算一次)。
     *
     * @param visibleSsu    链路上所有运转中 B6 的 ssu 产出之和
     * @param foundB6       链路上是否存在 B6(即位于 B6 输出侧)
     * @param allSuppressed 链路上所有 B6 的品控损耗均被 B14 抑制(无 B6 时为 false)
     */
    private record ChainResult(float visibleSsu, boolean foundB6, boolean allSuppressed) {
        public static final ChainResult EMPTY = new ChainResult(0.0F, false, false);
    }

    /**
     * 单台机器的奇点判定结果(服务端每 tick 由 {@link #analyze} 算出,再同步给客户端)。
     * <p>
     * 字段语义(v3):{@code ssuAmount} 为<b>本机可见 ssu</b>(沿链路可达的 B6 产出之和),
     * 不再是全网合计;{@code qcSuppressed} 为本机链路上所有 B6 的损耗均被抑制。
     *
     * @param ssuAmount     本机可见 ssu 合计
     * @param capacity      所在网络总产能(su)
     * @param needSsu       本机达到奇点态所需 ssu(= 本机应力消耗 / 压缩比)
     * @param concentration 奇点浓度(0~1)
     * @param chainLinked   是否位于 B6 输出侧
     * @param qcSuppressed  本机链路的品控损耗是否全部被 B14 抑制
     */
    public record SingularityInfo(float ssuAmount, float capacity, float needSsu, float concentration,
                                  boolean chainLinked, boolean qcSuppressed) {
        public static final SingularityInfo NONE =
                new SingularityInfo(0.0F, 0.0F, 0.0F, 0.0F, false, false);

        /** 浓度百分比(0~100)。 */
        public float concentrationPercent() {
            return concentration * 100.0F;
        }
    }

    /** 每 tick 每网络一份缓存,避免同网络多台机器重复扫描。 */
    private static final Map<CacheKey, NetworkSnapshot> SNAPSHOT_CACHE = new HashMap<>();
    private static final Map<CacheKey, Map<BlockPos, CompressorInfo>> B6_CACHE = new HashMap<>();
    private static final Map<CacheKey, Map<BlockPos, ChainResult>> CHAIN_CACHE = new HashMap<>();
    private static long cacheStamp = -1L;

    private record CacheKey(Level level, long networkId) {
    }

    private static void ensureTickCache(Level level) {
        long gameTime = level.getGameTime();
        if (cacheStamp != gameTime) {
            cacheStamp = gameTime;
            SNAPSHOT_CACHE.clear();
            B6_CACHE.clear();
            CHAIN_CACHE.clear();
        }
    }

    // ================= 量化引擎 =================

    /**
     * 对一台动能机器执行完整的奇点判定,算出它自己的奇点浓度。
     * <p>
     * 网络级部分与链路回溯每游戏刻只计算一次;浓度按机器独立计算。
     */
    public static SingularityInfo analyze(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null || be.isRemoved())
            return SingularityInfo.NONE;

        NetworkSnapshot snapshot = snapshotOf(be);
        ChainResult chain = chainOf(be);

        // 本机达到奇点态所需的 ssu = 本机应力消耗 / 压缩比(config)。
        float needSu = be.calculateStressApplied() * Math.abs(be.getSpeed());
        float needSsu = needSu / SingularityConfig.compressionRatio();

        float concentration;
        if (needSsu <= 0.0F) {
            // 未运转(应力需求为 0):有可见 ssu 即视为满浓度,否则 0。
            concentration = chain.visibleSsu() > 0.0F ? 1.0F : 0.0F;
        } else {
            concentration = Mth.clamp(chain.visibleSsu() / needSsu, 0.0F, 1.0F);
        }

        return new SingularityInfo(chain.visibleSsu(), snapshot.capacity(), needSsu,
                concentration, chain.foundB6(), chain.foundB6() && chain.allSuppressed());
    }

    /**
     * 枚举网络成员无关的网络级数据:总产能、实际消耗、空闲应力。
     * <p>
     * 公开给 B6 在自身 tick 中读取,注意只在服务端调用;
     * 本方法不触发任何应力重算,可安全地在网络装配过程中被间接调用。
     */
    public static NetworkSnapshot snapshotOf(KineticBlockEntity be) {
        Level level = be.getLevel();
        Long networkId = be.network;
        if (level == null || networkId == null)
            return NetworkSnapshot.EMPTY;

        ensureTickCache(level);
        CacheKey key = new CacheKey(level, networkId);
        NetworkSnapshot cached = SNAPSHOT_CACHE.get(key);
        if (cached != null)
            return cached;

        KineticNetwork network = be.getOrCreateNetwork();
        if (network == null)
            return NetworkSnapshot.EMPTY;

        float capacity = network.calculateCapacity();
        float consumption = network.calculateStress();
        float idle = Math.max(0.0F, capacity - consumption);
        NetworkSnapshot snapshot = new NetworkSnapshot(capacity, consumption, idle);
        SNAPSHOT_CACHE.put(key, snapshot);
        return snapshot;
    }

    /**
     * 单台 B6 的判定结果:运转状态、上链路 B14 抑制、品控损耗、ssu 产出。
     * <pre>
     *   空闲比例 = 空闲应力 / 总产能
     *   品控损耗 = qualityLossMin + (qualityLossMax − qualityLossMin) × 空闲比例   (被 B14 抑制时为 0)
     *   ssu 产出 = max(0, 空闲应力 − capacityGate) / compressionRatio             (未运转时为 0)
     * </pre>
     */
    public static CompressorInfo compressorInfoOf(SingularityRotationSpeedControllerBlockEntity b6) {
        Level level = b6.getLevel();
        Long networkId = b6.network;
        if (level == null || networkId == null || b6.isRemoved())
            return CompressorInfo.IDLE;

        ensureTickCache(level);
        CacheKey key = new CacheKey(level, networkId);
        Map<BlockPos, CompressorInfo> map = B6_CACHE.computeIfAbsent(key, k -> new HashMap<>());
        CompressorInfo cached = map.get(b6.getBlockPos());
        if (cached != null)
            return cached;

        NetworkSnapshot snapshot = snapshotOf(b6);
        boolean running = Math.abs(b6.getTheoreticalSpeed()) > 0.0F;
        boolean suppressed = isStabilizerUpstream(b6);

        float capacity = snapshot.capacity();
        float idleRatio = capacity > 0.0F ? Mth.clamp(snapshot.idleStress() / capacity, 0.0F, 1.0F) : 0.0F;
        float loss = 0.0F;
        if (!suppressed) {
            float min = SingularityConfig.qualityLossMin();
            float max = SingularityConfig.qualityLossMax();
            loss = min + Math.max(0.0F, max - min) * idleRatio;
        }

        float production = 0.0F;
        if (running) {
            production = Math.max(0.0F, snapshot.idleStress() - SingularityConfig.capacityGate())
                    / SingularityConfig.compressionRatio();
        }

        CompressorInfo info = new CompressorInfo(running, suppressed, loss, production);
        map.put(b6.getBlockPos(), info);
        return info;
    }

    // ================= 链路回溯(子链路可见性 + B14 上链路判定) =================

    /**
     * 沿 source 链向上回溯本机来源,汇总可见 ssu 与 B6/B14 分布。
     * <p>
     * 链路上每台<b>运转中</b>的 B6 都把自己的 ssu 产出贡献给本机;
     * 未运转的 B6 只贡献"存在 B6"这一事实(保持输出侧判定),不贡献产出。
     * 遇到未加载区块:视为链路不可达,返回空结果。
     */
    private static ChainResult chainOf(KineticBlockEntity be) {
        Level level = be.getLevel();
        Long networkId = be.network;
        if (level == null || networkId == null)
            return ChainResult.EMPTY;

        ensureTickCache(level);
        CacheKey key = new CacheKey(level, networkId);
        Map<BlockPos, ChainResult> map = CHAIN_CACHE.computeIfAbsent(key, k -> new HashMap<>());
        ChainResult cached = map.get(be.getBlockPos());
        if (cached != null)
            return cached;

        float sum = 0.0F;
        boolean foundB6 = false;
        boolean allSuppressed = true;

        BlockPos current = be.source;
        for (int depth = 0; depth < MAX_DEPTH && current != null; depth++) {
            if (!level.isLoaded(current)) {
                map.put(be.getBlockPos(), ChainResult.EMPTY);
                return ChainResult.EMPTY;
            }

            BlockEntity upstream = level.getBlockEntity(current);
            if (upstream == be)
                break;
            if (upstream instanceof SingularityRotationSpeedControllerBlockEntity b6) {
                foundB6 = true;
                CompressorInfo info = compressorInfoOf(b6);
                if (!info.qcSuppressed())
                    allSuppressed = false;
                if (info.running())
                    sum += info.production();
            }
            if (!(upstream instanceof KineticBlockEntity upstreamKinetic))
                break;

            current = upstreamKinetic.source;
        }

        ChainResult result = new ChainResult(sum, foundB6, allSuppressed);
        map.put(be.getBlockPos(), result);
        return result;
    }

    /**
     * 判断该方块实体是否位于奇点压缩器(B6)的输出侧(source 链可回溯到 B6)。
     */
    public static boolean isChainLinked(KineticBlockEntity be) {
        if (be == null || be.getLevel() == null)
            return false;
        return chainOf(be).foundB6();
    }

    /**
     * 判断 B6 的上链路(沿其 source 链向上回溯)是否存在 B14 奇点稳定器。
     * <p>
     * B14 只抑制<b>它下游</b>的 B6:多台 B14 各管各的下游,互不叠加。
     */
    private static boolean isStabilizerUpstream(SingularityRotationSpeedControllerBlockEntity b6) {
        Level level = b6.getLevel();
        if (level == null)
            return false;

        BlockPos current = b6.source;
        for (int depth = 0; depth < MAX_DEPTH && current != null; depth++) {
            if (!level.isLoaded(current))
                return false;

            BlockEntity upstream = level.getBlockEntity(current);
            if (upstream == b6)
                return false;
            if (upstream instanceof SingularityStabilizerBlockEntity)
                return true;
            if (!(upstream instanceof KineticBlockEntity upstreamKinetic))
                return false;

            current = upstreamKinetic.source;
        }
        return false;
    }

    /**
     * 该机器当前是否处于奇点状态(浓度已满且在输出侧)。
     */
    public static boolean isSingularityPowered(KineticBlockEntity be) {
        SingularityInfo info = analyze(be);
        return info.chainLinked() && info.concentration() >= 1.0F;
    }

    // ================= 护目镜辅助 =================

    /**
     * 护目镜文本构造器。
     * <p>
     * 注意不能用 {@code CreateLang.translate}:它会把键前缀成 {@code create.},
     * 而本模组的键前缀是 {@code avaritia_singularity_stress.}。
     */
    public static LangBuilder lang() {
        return new LangBuilder(AvaritiaSingularityStressMod.MODID);
    }

    /** 当前配置下该方块的 ssu 等效消耗(impact SU/RPM → ssu/RPM)。 */
    public static float ssuPerRpm(KineticBlockEntity be) {
        float equivalent = SingularityConfig.compressionRatio() * SingularityConfig.ssuToSuRatio();
        return be.calculateStressApplied() / equivalent;
    }

    /**
     * 复刻 Create 原生应力组的 <b>ssu 版本</b>,并追加浓度与状态。
     * <p>
     * 原版结构为:
     * <pre>
     *   动力学状态：            (create.gui.goggles.kinetic_stats)
     *   应力影响：              (create.tooltip.stressImpact)
     *     8192 su 当前速度值     (number + create.generic.unit.stress + at_current_speed)
     * </pre>
     * 这里给出完全对标的第二组:
     * <pre>
     *   奇点应力：              (singularity.goggles.stats)
     *   奇点应力影响：           (singularity.goggles.impact)
     *     128 ssu 当前速度值     (number + generic.unit.ssu + create at_current_speed)
     *   奇点浓度：88.88%        (singularity.goggles.concentration)
     *   状态：奇点状态(超激发态)  (singularity.goggles.state + state.key)
     * </pre>
     *
     * @param stressAtBase 该方块的基础应力消耗(SU/RPM)
     * @param speed        当前转速
     * @param info         奇点判定结果
     * @param state        派生出的奇点状态
     */
    public static void addSingularityStats(List<Component> tooltip, float stressAtBase, float speed,
                                           SingularityInfo info, SingularityState state) {
        float totalSu = stressAtBase * Math.abs(speed);
        float equivalent = SingularityConfig.compressionRatio() * SingularityConfig.ssuToSuRatio();

        // —— 奇点应力组(对标原版应力组) ——
        lang().translate("singularity.goggles.stats")
                .forGoggles(tooltip);
        lang().translate("singularity.goggles.impact")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        lang().text(LangNumberFormat.format(totalSu / equivalent))
                .space()
                .translate("generic.unit.ssu")
                .style(ChatFormatting.AQUA)
                .space()
                .add(CreateLang.translate("gui.goggles.at_current_speed")
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        // —— 奇点浓度 ——
        float percent = info.concentrationPercent();
        lang().translate("singularity.goggles.concentration",
                        String.format("%.2f%%", percent))
                .style(percent >= 100.0F ? ChatFormatting.AQUA : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        // —— 状态 ——
        ChatFormatting stateColor = switch (state) {
            case ULTIMATE -> ChatFormatting.LIGHT_PURPLE;
            case OVEREXCITED -> ChatFormatting.GOLD;
            case ACTIVE -> ChatFormatting.AQUA;
            case NONE -> ChatFormatting.DARK_GRAY;
        };
        lang().translate("singularity.goggles.state")
                .style(ChatFormatting.GRAY)
                .add(lang().translate(state.key()).style(stateColor))
                .forGoggles(tooltip, 1);
    }
}