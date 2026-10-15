package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * 无线频率管理器。
 * <p>
 * 结构照抄 Create 的 {@link RedstoneLinkNetworkHandler}：
 * <ul>
 *   <li><b>按维度分区</b>（{@link IdentityHashMap} 按对象身份，避免 equals 语义意外）——
 *       同一频率在不同维度天然隔离，不跨维度。</li>
 *   <li>维度加载建表、卸载删表，由 {@code LevelEvent.Load/Unload} 驱动。</li>
 *   <li>注册<b>幂等</b>：先 remove 再 add，且集合本身去重。
 *       因为 {@code SmartBlockEntity.initialize()} 在<b>每次区块重载后都会再调一次</b>，
 *       注册逻辑不能假设"只注册一次"。</li>
 *   <li>遍历时兜底剔除死引用：{@code setRemoved()} 是 final，区块卸载<b>不会</b>走
 *       {@code remove()}，只走 {@code invalidate()}，所以不能依赖注销一定发生。</li>
 * </ul>
 * <p>
 * 本类只管"谁在哪个频道上"；配对、距离判定、功能语义全部在 {@link WirelessService}。
 */
public final class WirelessNetwork {

    /** 维度 -> (频道 -> 设备集合)。 */
    private static final Map<LevelAccessor, Map<WirelessKey, Set<WirelessDevice>>> CONNECTIONS =
            new IdentityHashMap<>();

    private WirelessNetwork() {
    }

    // ===================== 维度生命周期（LevelEvent.Load / Unload） =====================

    public static void onLoadWorld(LevelAccessor level) {
        CONNECTIONS.put(level, new HashMap<>());
    }

    public static void onUnloadWorld(LevelAccessor level) {
        CONNECTIONS.remove(level);
    }

    private static Map<WirelessKey, Set<WirelessDevice>> table(LevelAccessor level) {
        return CONNECTIONS.computeIfAbsent(level, $ -> new HashMap<>());
    }

    // ===================== 注册 / 注销（必须幂等） =====================

    /**
     * 注册一台设备。幂等：同一实例重复注册不会产生重复条目；
     * 若它的频道钥匙变了，旧条目也会被清掉。
     */
    public static void add(WirelessDevice device) {
        WirelessKey key = device.getKey();
        if (key == null) {
            return;
        }
        Level level = device.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        Map<WirelessKey, Set<WirelessDevice>> table = table(level);

        // 先把它从所有频道里摘掉：防止频率被 I3 改写后残留旧频道条目
        removeFromAll(table, device);

        Set<WirelessDevice> set = table.computeIfAbsent(key, $ -> new LinkedHashSet<>());
        // 去掉身份相等的旧实例（区块重载后是同坐标的新对象）
        set.removeIf(existing -> existing == device
                || (existing.getBlockPos().equals(device.getBlockPos())
                        && existing.getLevel() == device.getLevel()));
        set.add(device);
    }

    /** 注销一台设备；不存在时静默返回。 */
    public static void remove(WirelessDevice device) {
        Level level = device.getLevel();
        if (level == null) {
            return;
        }
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        if (table == null) {
            return;
        }
        removeFromAll(table, device);
    }

    /**
     * 设备频率被改写后调用：把它搬到新频道去。
     * <p>
     * 等价于 remove + add，单独给个名字是为了调用点语义清楚。
     */
    public static void move(WirelessDevice device) {
        add(device);
    }

    private static void removeFromAll(Map<WirelessKey, Set<WirelessDevice>> table, WirelessDevice device) {
        List<WirelessKey> emptied = null;
        for (Map.Entry<WirelessKey, Set<WirelessDevice>> entry : table.entrySet()) {
            Set<WirelessDevice> set = entry.getValue();
            set.removeIf(existing -> existing == device
                    || (existing.getBlockPos().equals(device.getBlockPos())
                            && existing.getLevel() == device.getLevel()));
            if (set.isEmpty()) {
                if (emptied == null) {
                    emptied = new ArrayList<>();
                }
                emptied.add(entry.getKey());
            }
        }
        if (emptied != null) {
            emptied.forEach(table::remove);
        }
    }

    // ===================== 查询 =====================

    /**
     * 取出与给定钥匙同频道的存活设备（不含 {@code exclude} 自己）。
     * <p>
     * <b>返回值是拷贝后的快照</b>：调用方可以安全遍历，不会因剔除动作触发
     * {@code ConcurrentModificationException}，也不会持有管理器的内部集合。
     *
     * @param level   维度
     * @param key     查询钥匙（会按 {@link WirelessKey#lookupKeys()} 展开私有/公用）
     * @param exclude 排除的设备（通常是查询者自己），可为 null
     * @return 存活且通过 {@link WirelessDevice#canPairWith} 的对端列表
     */
    public static List<WirelessDevice> peers(Level level, WirelessKey key, @Nullable WirelessDevice exclude) {
        List<WirelessDevice> result = new ArrayList<>();
        if (level == null || level.isClientSide || key == null) {
            return result;
        }
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        if (table == null) {
            return result;
        }
        for (WirelessKey lookup : key.lookupKeys()) {
            Set<WirelessDevice> set = table.get(lookup);
            if (set == null) {
                continue;
            }
            // 顺手剔除死引用（管理器自清理，见类注释）
            set.removeIf(device -> !device.isAlive());
            for (WirelessDevice device : set) {
                if (device == exclude || result.contains(device)) {
                    continue;
                }
                if (exclude != null && !exclude.canPairWith(device)) {
                    continue;
                }
                result.add(device);
            }
        }
        return result;
    }

    /**
     * 取出某维度下<b>全部</b>登记设备的存活快照（跨频道去重）。
     * <p>
     * 这是每 tick 驱动的入口。注意<b>不能</b>用 {@link #peers} 来实现——
     * 那个方法是"找对端"语义，查询者自身会被排除，拿不到全量。
     *
     * @param level 维度
     * @return 存活设备列表（拷贝，可安全遍历）
     */
    public static List<WirelessDevice> devicesIn(Level level) {
        List<WirelessDevice> result = new ArrayList<>();
        if (level == null || level.isClientSide) {
            return result;
        }
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        if (table == null) {
            return result;
        }
        for (Set<WirelessDevice> set : table.values()) {
            // 顺手剔除死引用（管理器自清理，见类注释）
            set.removeIf(device -> !device.isAlive());
            for (WirelessDevice device : set) {
                if (!result.contains(device)) {
                    result.add(device);
                }
            }
        }
        return result;
    }

    /** 调试/校验用：某维度当前登记的频道数。 */
    public static int channelCount(LevelAccessor level) {
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        return table == null ? 0 : table.size();
    }

    /** 调试/校验用：某维度当前登记的设备总数（含未剔除的死引用）。 */
    public static int deviceCount(LevelAccessor level) {
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        if (table == null) {
            return 0;
        }
        int total = 0;
        for (Set<WirelessDevice> set : table.values()) {
            total += set.size();
        }
        return total;
    }

    /** 供调试输出用：某维度的原始频道表（只读语义，勿修改）。 */
    public static Collection<WirelessKey> channels(LevelAccessor level) {
        Map<WirelessKey, Set<WirelessDevice>> table = CONNECTIONS.get(level);
        return table == null ? List.of() : Set.copyOf(table.keySet());
    }
}
