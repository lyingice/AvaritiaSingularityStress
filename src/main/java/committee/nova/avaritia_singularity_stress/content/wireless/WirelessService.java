package committee.nova.avaritia_singularity_stress.content.wireless;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 无线配对服务：在 {@link WirelessNetwork} 的频道成员之上施加
 * 「距离上限」与「一对一」两条最终规格约束。
 * <p>
 * 本类不持有任何状态，全部静态、无缓存 —— 缓存是跨区块卸载后悬空引用的主要来源。
 */
public final class WirelessService {

    private WirelessService() {
    }

    // ===================== 距离 =====================

    /**
     * 距离上限判定。照抄 Create 的 {@code RedstoneLinkNetworkHandler.withinRange}，
     * 但用方块坐标中心点的欧氏距离。
     * <p>
     * 注意这里的坐标比较<b>只在同一维度内有意义</b> —— 本模组按维度分区
     * （见 {@link WirelessNetwork}），所以不存在跨维度误判。
     *
     * @param from  自身坐标
     * @param to    对端坐标
     * @param range 距离上限（格）
     * @return 在范围内为 true
     */
    public static boolean withinRange(BlockPos from, BlockPos to, double range) {
        if (from.equals(to)) {
            return true;
        }
        return Vec3.atCenterOf(from).closerThan(Vec3.atCenterOf(to), range);
    }

    // ===================== 对端解析（一对一） =====================

    /**
     * 在频道内解析出唯一对端。
     * <p>
     * <b>一对一语义</b>：同频道 + 同维度 + 距离内的合格对端，
     * <ul>
     *   <li>恰好 1 个 → 返回它；</li>
     *   <li>0 个 → 返回 null（未配对）；</li>
     *   <li>≥2 个 → 返回 null（存在歧义，宁可停机也不随机挑一个）。</li>
     * </ul>
     *
     * @param self  查询者自身
     * @param range 距离上限（格）
     * @return 唯一对端，或 null
     */
    @Nullable
    public static WirelessDevice findPeer(WirelessDevice self, double range) {
        List<WirelessDevice> candidates = candidates(self, range);
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * 频道内的全部合格对端（已按距离过滤，已剔除死引用与自身）。
     *
     * @param self  查询者自身
     * @param range 距离上限（格）
     * @return 合格对端列表（可能为空，不会为 null）
     */
    public static List<WirelessDevice> candidates(WirelessDevice self, double range) {
        Level level = self.getLevel();
        WirelessKey key = self.getKey();
        if (level == null || level.isClientSide || key == null) {
            return List.of();
        }
        BlockPos selfPos = self.getBlockPos();
        List<WirelessDevice> result = new ArrayList<>();
        for (WirelessDevice peer : WirelessNetwork.peers(level, key, self)) {
            if (!withinRange(selfPos, peer.getBlockPos(), range)) {
                continue;
            }
            result.add(peer);
        }
        return result;
    }

    /**
     * 判断两台设备是否确实属于同一个 {@link Level} 对象。
     * <p>
     * 私有时所有者必须一致（{@link WirelessDevice#canPairWith} 已收口），
     * 这里只做维度同一性兜底 —— 频道表本就按维度分区，这是第二道保险。
     */
    public static boolean sameLevel(WirelessDevice a, WirelessDevice b) {
        return a.getLevel() == b.getLevel();
    }

    /**
     * 所有者 UUID 是否匹配（私有频道用）。
     *
     * @param owner 频道所有者
     * @param other 另一台设备的所有者
     * @return 双方均非 null 且相等时为 true
     */
    public static boolean ownerMatches(@Nullable UUID owner, @Nullable UUID other) {
        return owner != null && owner.equals(other);
    }
}
