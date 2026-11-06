package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.BeltPart;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * B15 分流规则自检（专用服务端）。
 * <p>
 * <b>为什么需要它</b>：这条链路（链路 + 两面过滤 → 折跃/本地）没法靠编译验证，
 * 而人肉测试要摆带子、设过滤、放物品、盯结果，容易因为「物品走的是另一条链路」误判。
 * 服务端自检直接把 BE 造出来、把链路和过滤设好，然后逐条断言 {@code shouldWarp}，
 * 输出进日志——同一条命令可重复、可回归。
 * <p>
 * <b>为什么不摆传送带物流</b>：本次要验的是「过滤读没读到、判定对不对」，
 * 这两件事都只依赖 BE 数据（links / sidedFilter），与带子是否转动无关。
 * 只放一格 {@code CASING=true} 的带子，是为了让隧道满足 {@code canSurvive}。
 * <p>
 * 触发：启动专用服务端后自动在 spawn 附近跑一次，打印 4 个用例的结果，然后 {@code halt}。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public final class WarpSelfTest {

    /** 测试场地：spawn 附近的空中（已加载区块）。 */
    private static final BlockPos BASE_A = new BlockPos(0, 100, 0);
    private static final BlockPos BASE_B = new BlockPos(0, 100, 4);

    private static final ItemStack IRON = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack GOLD = new ItemStack(Items.GOLD_INGOT);
    private static final ItemStack DIRT = new ItemStack(Items.DIRT);

    private WarpSelfTest() {
    }

    /**
     * 开关：只有显式传 {@code -Davaritia.warp.selftest=true} 时才跑。
     * <p>
     * <b>必须默认关闭</b>：它会 {@code halt} 服务器，若不加开关，任何人开服都会被立刻关掉。
     * 验证用的 {@code run-server-verify.init.gradle} 会注入这个属性。
     */
    private static final String ENABLE_PROPERTY = "avaritia.warp.selftest";

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        List<String> result = new ArrayList<>();
        int pass = 0;

        try {
            WarpTunnelBlockEntity a = placeTunnel(level, BASE_A);
            WarpTunnelBlockEntity b = placeTunnel(level, BASE_B);
            if (a == null || b == null) {
                AvaritiaSingularityStressMod.LOGGER.error("[selftest] 摆方块失败：a={} b={}", a, b);
                event.getServer().halt(false);
                return;
            }

            // ---- case②：无链路 + 两槽空 → 全本地 ----
            a.setFaceFrequency(Direction.WEST, WarpTunnelBlockEntity.NO_FACE_FREQUENCY);
            a.setFilter(Direction.WEST, ItemStack.EMPTY);
            b.setFilter(Direction.WEST, ItemStack.EMPTY);
            pass += check(result, "case2 未绑定", a, Direction.WEST, IRON, false);

            // ---- case③：只有终点面过滤（对端 WEST=铁锭）→ 铁锭折跃、泥土本地 ----
            WarpTunnelBlockEntity.bind(a, Direction.WEST, b, Direction.WEST);
            a.setFilter(Direction.WEST, ItemStack.EMPTY);
            b.setFilter(Direction.WEST, IRON.copy());
            pass += check(result, "case3 终点过滤·铁锭", a, Direction.WEST, IRON, true);
            pass += check(result, "case3 终点过滤·泥土", a, Direction.WEST, DIRT, false);

            // ---- case④：只有出向面过滤（本端 WEST=铁锭）→ 铁锭折跃、泥土本地 ----
            b.setFilter(Direction.WEST, ItemStack.EMPTY);
            a.setFilter(Direction.WEST, IRON.copy());
            pass += check(result, "case4 出向过滤·铁锭", a, Direction.WEST, IRON, true);
            pass += check(result, "case4 出向过滤·泥土", a, Direction.WEST, DIRT, false);

            // ---- case⑤：两面都有（本端铁锭 / 对端金锭）→ 金锭折跃、铁锭本地 ----
            b.setFilter(Direction.WEST, GOLD.copy());
            pass += check(result, "case5 双面·金锭", a, Direction.WEST, GOLD, true);
            pass += check(result, "case5 双面·铁锭", a, Direction.WEST, IRON, false);
            pass += check(result, "case5 双面·泥土", a, Direction.WEST, DIRT, false);

            // ---- case5b：反向布局（出向=金锭 / 终面=铁锭）→ 铁锭折跃、金锭本地 ----
            // 这是用户实测的布局，单独列出来防止只测一个方向
            a.setFilter(Direction.WEST, GOLD.copy());
            b.setFilter(Direction.WEST, IRON.copy());
            pass += check(result, "case5b 反向·铁锭", a, Direction.WEST, IRON, true);
            pass += check(result, "case5b 反向·金锭", a, Direction.WEST, GOLD, false);

            // ================= 端到端：物品真的过去了吗 =================
            // 前面的用例验证判定；下面直接驱动吸收与处理，确认同一过滤规则能走完整个队列。
            AvaritiaSingularityStressMod.LOGGER.info("[selftest] ---- 端到端 ----");

            // 用户指定场景：本地出向面只收金锭、对端终面只收铁锭；金锭本地、铁锭折跃。
            a.setFilter(Direction.WEST, GOLD.copy());
            b.setFilter(Direction.WEST, IRON.copy());
            pass += runEndToEnd(level, result, "e2e 终面过滤·铁锭折跃", a, b, IRON, true);
            pass += runEndToEnd(level, result, "e2e 本面过滤·金锭本地", a, b, GOLD, false);

            // 出口过滤关闭时终面过滤不参与；本面过滤命中仍按旧行为折跃。
            a.setExportFilter(false);
            pass += runEndToEnd(level, result, "e2e 关闭出口过滤·金锭折跃", a, b, GOLD, true);
            a.setExportFilter(true);

            int total = 13;
            AvaritiaSingularityStressMod.LOGGER.info("[selftest] RESULT {}/{}", pass, total);
            for (String line : result) {
                AvaritiaSingularityStressMod.LOGGER.info("[selftest] {}", line);
            }
            AvaritiaSingularityStressMod.LOGGER.info("[selftest] DONE {}", pass == total ? "ALL PASS" : "HAS FAILURE");
        } catch (Exception e) {
            AvaritiaSingularityStressMod.LOGGER.error("[selftest] 异常", e);
        } finally {
            event.getServer().halt(false);
        }
    }

    /**
     * 端到端：把物品塞进入口缓冲 → 吸收 → 处理队头 → 看它落到哪。
     * <p>
     * 判定标准：
     * <ul>
     *   <li>折跃期望：物品被消耗（本机缓冲空、队列空），且对端附近出现该物品实体；</li>
     *   <li>本地期望：物品被消耗，且本机附近出现该物品实体（eject / 掉落）。</li>
     * </ul>
     * 一格带子跑不出完整物流，所以这里用「世界里有没有出现这个物品实体」作为到达证据。
     */
    private static int runEndToEnd(ServerLevel level, List<String> out, String name,
                                   WarpTunnelBlockEntity from, WarpTunnelBlockEntity to,
                                   ItemStack item, boolean expectWarp) {
        // 复位：清掉上一轮残留（队列 / 入口缓冲 / 待吐出缓冲），否则断言会读到别人的货
        from.clearQueueForTest();
        to.clearQueueForTest();
        level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(
                from.getBlockPos()).inflate(24)).forEach(ItemEntity::discard);

        from.setStackToDistribute(item.copy(), Direction.WEST);
        from.absorbIncoming();
        int queued = from.getQueuedCount();
        from.processHead();
        int queuedAfter = from.getQueuedCount();
        String status = from.getStatus();
        // B 方案的核心证据：折跃成功 = 对端的待吐出缓冲收下了
        int peerPending = to.getPendingOutputCount();

        boolean routedCorrectly = expectWarp
                ? (queued > 0 && queuedAfter == 0 && peerPending > 0)
                : (queued == 0);

        out.add(String.format(
                "%s | queued=%d afterSend=%d peerPending=%d status=%s | 路由=%s",
                name, queued, queuedAfter, peerPending, status,
                routedCorrectly ? "OK" : "WRONG"));
        return routedCorrectly ? 1 : 0;
    }

    /** 放一格 casing 带子 + 一台隧道，返回 BE（放不下返回 null）。 */
    private static WarpTunnelBlockEntity placeTunnel(ServerLevel level, BlockPos pos) {
        BlockState belt = AllBlocks.BELT.getDefaultState()
                .setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL)
                .setValue(BeltBlock.PART, BeltPart.MIDDLE)
                .setValue(BeltBlock.CASING, true);
        level.setBlock(pos.below(), belt, Block.UPDATE_CLIENTS);
        BlockState tunnel = ModBlocks.WARP_TUNNEL.get().defaultBlockState();
        level.setBlock(pos, tunnel, Block.UPDATE_CLIENTS);
        if (!(level.getBlockEntity(pos) instanceof WarpTunnelBlockEntity be)) {
            return null;
        }
        // 关键：setBlock 之后 initialize() 还没跑（要等第一次 tick 才会由 SmartBlockEntity.tick 触发），
        // 而 sidedFilter 正是 BrassTunnelBlockEntity.initialize() 里 createSidedFilter() 创建的。
        // 不手动初始化，下面的 setFilter() 会因为 sidedFilter==null 直接 return，过滤永远是 air ——
        // 这正是第一版自检 4/8 失败的原因（不是方块逻辑的错）。
        be.initialize();
        return be;
    }

    /** 断言一次：实际折跃与否 vs 期望；返回 1 表示通过。 */
    private static int check(List<String> out, String name, WarpTunnelBlockEntity be,
                             Direction outFace, ItemStack stack, boolean expectWarp) {
        boolean actual = be.shouldWarp(stack, outFace);
        boolean ok = actual == expectWarp;
        // 把读到的真实过滤值一并打出来 —— 这是判断「过滤到底存没存进去」的关键证据
        WarpTunnelBlockEntity.FaceRef peer = be.peerOf(outFace);
        String detail = String.format(
                "%s | freq=%s outFilter=%s peerFilter=%s | actual=%s expect=%s -> %s",
                name, be.getFaceFrequency(outFace), be.getFilter(outFace),
                peer == null ? "NO_PEER" : String.valueOf(be.peerFilterOf(peer)),
                actual ? "WARP" : "LOCAL", expectWarp ? "WARP" : "LOCAL", ok ? "PASS" : "FAIL");
        out.add(detail);
        return ok ? 1 : 0;
    }
}
