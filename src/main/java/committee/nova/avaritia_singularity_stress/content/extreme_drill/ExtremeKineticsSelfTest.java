package committee.nova.avaritia_singularity_stress.content.extreme_drill;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * B27 终焉钻头 / B28 终焉水车 服务端自检（专用服务端，默认关闭）。
 * <p>
 * 触发：{@code -Davaritia.extreme_kinetics.selftest=true}，等 20 tick 后跑一次并 halt。
 * 验证：钻头 256SU/RPM 耗能、基础时运 floor(|speed|/16)∈[0,16] × 配置乘区(默认 5)、近瞬挖；
 * 水车恒 256RPM、基础容量=Create 水车×5(默认 160)、总应力=容量×转速、有流体翻倍。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public final class ExtremeKineticsSelfTest {

    private static final String ENABLE_PROPERTY = "avaritia.extreme_kinetics.selftest";
    private static boolean finished;

    private ExtremeKineticsSelfTest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || finished) {
            return;
        }
        if (event.getServer().getTickCount() < 20) {
            return;
        }
        finished = true;
        runAll(event.getServer());
    }

    private static void runAll(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> result = new ArrayList<>();
        int pass = 0;
        int total = 19;
        try {
            // 清理测试区：世界在多次运行间持久化，残留的水/石头/掉落物会干扰断言（尤其水车"无流体"用例）。
            for (BlockPos p : BlockPos.betweenClosed(-2, 98, -3, 2, 102, 18)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.getEntitiesOfClass(ItemEntity.class, new AABB(-2, 98, -3, 3, 103, 19))
                    .forEach(ItemEntity::discard);

            // 临时关闭应力：钻头 256SU/RPM 仍大于单台水车 160SU/RPM 基准容量(32×5)，
            // 真实网络会过载停转(getSpeed 在 overStressed 时返回 0)。端到端破坏用例只需驱动转速，
            // 故关闭应力以便单台水车带动钻头；应力数值本身另有断言验证。
            com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.disableStress.set(true);

            // ---------- B27 钻头 ----------
            BlockPos dp = new BlockPos(0, 100, 0);
            ExtremeDrillBlockEntity drill = placeDrill(level, dp);
            pass += check(result, "钻头·放置得到终焉钻头实体", drill != null);

            pass += check(result, "钻头·耗能基准 256SU/RPM",
                    BlockStressValues.getImpact(ModBlocks.EXTREME_DRILL.get()) == 256.0);

            // 有效时运 = 基础等级 × 配置乘区(默认 5)
            pass += check(result, "钻头·16RPM→时运5(默认5倍)", fortuneAt(drill, 16) == 5);
            pass += check(result, "钻头·32RPM→时运10", fortuneAt(drill, 32) == 10);
            pass += check(result, "钻头·256RPM→时运80", fortuneAt(drill, 256) == 80);
            pass += check(result, "钻头·8RPM→时运0", fortuneAt(drill, 8) == 0);
            pass += check(result, "钻头·512RPM→基础16截断×5=80", fortuneAt(drill, 512) == 80);
            ExtremeDrillConfig.FORTUNE_MULTIPLIER.set(2);
            pass += check(result, "钻头·配置2倍→256RPM时运32", fortuneAt(drill, 256) == 32);
            ExtremeDrillConfig.FORTUNE_MULTIPLIER.set(5);
            pass += check(result, "钻头·恢复默认5倍→80", fortuneAt(drill, 256) == 80);

            drill.setSpeed(256);
            pass += check(result, "钻头·256RPM 破坏速度放大(近瞬挖)", drill.getBreakSpeed() == 25600f);

            // 端到端：用终焉水车(恒 256RPM)给钻头供电，面前放石头应几乎立即破坏并掉落。
            // 必须接真实动力源——否则 KineticBlockEntity.validateKinetics 会把无源非发电方块的手动转速清零
            // (KineticBlockEntity.java:149-152)，那是测试桩问题而非方块缺陷。
            BlockPos wheelSrc = dp.south();
            ExtremeWaterWheelBlockEntity src = placeWheel(level, wheelSrc);
            src.initialize();                 // 启动发电 256RPM
            drill.setSource(wheelSrc);        // 有效源使 validateKinetics 保留转速(KineticBlockEntity.java:128-146)
            BlockPos target = dp.relative(Direction.NORTH);
            level.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            drill.destroyNextTick();
            for (int i = 0; i < 10 && !level.getBlockState(target).isAir(); i++) {
                drill.setSpeed(256);
                drill.tick();
            }
            boolean broken = level.getBlockState(target).isAir();
            boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(2)).isEmpty();
            pass += check(result, "钻头·端到端近瞬破坏石头并掉落", broken && dropped);

            // ---------- B28 水车 ----------
            BlockPos wp = new BlockPos(0, 100, 16);
            ExtremeWaterWheelBlockEntity wheel = placeWheel(level, wp);
            pass += check(result, "水车·放置得到终焉水车实体", wheel != null);
            pass += check(result, "水车·恒 256RPM", wheel.getGeneratedSpeed() == 256f);

            float base = (float) BlockStressValues.getCapacity(AllBlocks.WATER_WHEEL.get());
            float expected = base * ExtremeWaterWheelBlockEntity.BASE_CAPACITY_FACTOR;
            pass += check(result, "水车·Create 水车基础容量默认32", base == 32f);
            pass += check(result, "水车·无流体容量=基础×5", wheel.calculateAddedStressCapacity() == expected);
            pass += check(result, "水车·无流体总应力=基础×5×256",
                    wheel.calculateAddedStressCapacity() * wheel.getGeneratedSpeed() == expected * 256f);

            // 有流体：在轮缘偏移放水源，刷新后容量翻倍
            for (Direction d : Direction.values()) {
                level.setBlock(wp.relative(d), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
            wheel.determineAndApplyFlowScore();
            pass += check(result, "水车·有流体容量翻倍", wheel.calculateAddedStressCapacity() == expected * 2f);
            pass += check(result, "水车·有流体总应力=基础×5×2×256",
                    wheel.calculateAddedStressCapacity() * wheel.getGeneratedSpeed() == expected * 2f * 256f);
            pass += check(result, "水车·有流体转速仍 256", wheel.getGeneratedSpeed() == 256f);

            AvaritiaSingularityStressMod.LOGGER.info("[extreme-kinetics-selftest] RESULT {}/{}", pass, total);
            for (String line : result) {
                AvaritiaSingularityStressMod.LOGGER.info("[extreme-kinetics-selftest] {}", line);
            }
            AvaritiaSingularityStressMod.LOGGER.info("[extreme-kinetics-selftest] DONE {}",
                    pass == total ? "ALL PASS" : "HAS FAILURE");
        } catch (Exception e) {
            AvaritiaSingularityStressMod.LOGGER.error("[extreme-kinetics-selftest] 异常", e);
        } finally {
            com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.disableStress.set(false);
            ExtremeDrillConfig.FORTUNE_MULTIPLIER.set(5);
            server.halt(false);
        }
    }

    private static int fortuneAt(ExtremeDrillBlockEntity drill, float speed) {
        drill.setSpeed(speed);
        return drill.getFortuneLevel();
    }

    private static ExtremeDrillBlockEntity placeDrill(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        BlockState state = ModBlocks.EXTREME_DRILL.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.NORTH);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return (ExtremeDrillBlockEntity) level.getBlockEntity(pos);
    }

    private static ExtremeWaterWheelBlockEntity placeWheel(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        BlockState state = ModBlocks.EXTREME_WATER_WHEEL.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.NORTH);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        return (ExtremeWaterWheelBlockEntity) level.getBlockEntity(pos);
    }

    private static int check(List<String> out, String name, boolean ok) {
        out.add(name + " -> " + (ok ? "PASS" : "FAIL"));
        return ok ? 1 : 0;
    }
}
