package committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal;

import static com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.blaze_cube_crafter.BlazeCubeCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_connector.ExtremeConnectorBlock;
import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.item_funnel.WirelessItemFunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeCrafterHelper;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlock;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeMechanicalCrafterBlockEntity.Phase;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.CraftingTerminalNetwork.CraftingGrid;
import committee.nova.avaritia_singularity_stress.content.extreme_crafting_terminal.ExtremeCraftingTerminalBlockEntity.CraftResult;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler;
import committee.nova.avaritia_singularity_stress.content.extreme_mechanical_crafter.ExtremeRecipeGridHandler.GroupedItems;
import committee.nova.avaritia_singularity_stress.content.recipes.ExtremeKineticCraftingRecipe;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe;

import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * B29/B30 终焉动能合成终端服务端回归（默认关闭，-Davaritia.crafting_terminal.selftest=true）。
 * <p>
 * 这版不再用“静态塞一格材料”冒充成功：真正放置 Create 创造马达 + 小齿轮传动，
 * 让整条终焉合成器链在 world tick 中起工、组装、合成、输出，最后等全部回 IDLE。
 * <p>
 * 覆盖顺序：
 * <ol>
 *   <li>真实动力闭环：2×2 不对称空格 + Avaritia/kinetic 同料竞争、成品入箱、材料计数、
 *       无意外掉落、盖板状态、BUSY、最终 IDLE；随后一例 Avaritia 单配方成品；</li>
 *   <li>拒绝且零变更：无动力、缺料、部分槽已满且 IDLE；</li>
 *   <li>网络安全拒绝：多根、环、真实链缺连接器、错误侧贴、有效 L 网格缺必须格；</li>
 *   <li>手动配方优先级与真实 Avaritia 配方布局的静态回归。</li>
 * </ol>
 * 每个 fixture 使用独立基座并在 setup 时清空；超时/异常会记录 FAIL 并停止服务端。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public final class CraftingTerminalSelfTest {

    private static final String ENABLE_PROPERTY = "avaritia.crafting_terminal.selftest";
    private static final int START_TICK = 20;
    private static final int SETTLE_TICKS = 20;
    private static final int READY_TIMEOUT = 180;
    private static final int DEFAULT_TIMEOUT = 600;

    private static boolean finished;
    private static Runner runner;

    /** 仅测试开关打开时注册：给 jigsaw BE 一个“模拟可抽、实际必拒绝”的 handler。 */
    private static final ExtractFailingHandler FAILING_HANDLER = new ExtractFailingHandler();

    private CraftingTerminalSelfTest() {
    }

    @EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class TestCapabilities {
        private TestCapabilities() {
        }

        @SubscribeEvent
        public static void register(RegisterCapabilitiesEvent event) {
            if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
                return;
            }
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BlockEntityType.JIGSAW,
                    (be, side) -> FAILING_HANDLER);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || finished) {
            return;
        }
        if (event.getServer().getTickCount() < START_TICK) {
            return;
        }
        if (runner == null) {
            try {
                runner = new Runner(event.getServer());
            } catch (Throwable t) {
                AvaritiaSingularityStressMod.LOGGER.error("[crafting-terminal-selftest] 初始化异常", t);
                finished = true;
                event.getServer().halt(false);
                return;
            }
        }
        boolean done;
        try {
            done = runner.tick();
        } catch (Throwable t) {
            AvaritiaSingularityStressMod.LOGGER.error("[crafting-terminal-selftest] 未捕获异常", t);
            runner.failFatal("未捕获异常: " + t);
            done = true;
        }
        if (done) {
            finished = true;
            event.getServer().halt(false);
        }
    }

    // ------------------------------------------------------------------
    // 调度器
    // ------------------------------------------------------------------

    private static final class Runner {

        private final MinecraftServer server;
        private final ServerLevel level;
        private final Deque<Case> cases = new ArrayDeque<>();
        private final List<String> lines = new ArrayList<>();

        private Case current;
        private int phase;
        private int readyAge;
        private int waitAge;
        private int pass;
        private int total;
        private boolean fatal;
        private boolean finished;

        private Runner(MinecraftServer server) {
            this.server = server;
            this.level = server.overworld();

            // 手动优先级必须先跑：它会读取真实数据包配方，后续 case 会 replaceRecipes。
            cases.add(new ManualPriorityCase(new BlockPos(0, 100, 0)));
            // 竞争例：两配方都在，但终端必须按用户选中的 Avaritia 产出钻石，
            // 不能走旧手动 matcher 的 kinetic 优先。
            cases.add(new SuccessCase("动力·2×2 竞争·选中 Avaritia·不对称空格", new BlockPos(20, 100, 0),
                    true, true, "asym"));
            cases.add(new SuccessCase("动力·2×2 kinetic 单配方", new BlockPos(40, 100, 0),
                    false, false, "full_kinetic"));
            cases.add(new NoPowerCase(new BlockPos(60, 100, 0)));
            cases.add(new MissingMaterialCase(new BlockPos(80, 100, 0)));
            cases.add(new TransferFailureCase(new BlockPos(100, 100, 0)));
            cases.add(new PostExtractFailureCase(new BlockPos(120, 100, 0)));
            cases.add(new ArrowChangeCase(new BlockPos(140, 100, 0)));
            // 全部基座保持在出生点 simulation-distance 内，保证 canCraftersTick 成立；
            // 否则 findCraftingGrid 会按设计 fail closed，测试会误判为网络错误。
            cases.add(new MultiRootCase(new BlockPos(0, 100, 20)));
            cases.add(new CycleCase(new BlockPos(20, 100, 20)));
            cases.add(new MissingConnectorCase(new BlockPos(40, 100, 20)));
            cases.add(new WrongSideCase(new BlockPos(60, 100, 20)));
            cases.add(new LMissingCellCase(new BlockPos(80, 100, 20)));
            // 2026-11-20 扩展：炽骨网络纳管/品类准入、形状过滤、红石书签、创造板条箱免材料。
            cases.add(new BlazeTerminalCase(new BlockPos(100, 100, 20)));
            cases.add(new ShapeFilterCase(new BlockPos(120, 100, 20)));
            cases.add(new RedstonePinCase(new BlockPos(140, 100, 20)));
            cases.add(new CreativeCrateCase(new BlockPos(160, 100, 20)));
            cases.add(new WirelessFunnelCase(new BlockPos(180, 100, 20)));
        }

        private boolean tick() {
            if (fatal || finished) {
                return true;
            }
            if (current == null) {
                if (cases.isEmpty()) {
                    finish();
                    return true;
                }
                current = cases.poll();
                try {
                    current.setup(level);
                } catch (Throwable t) {
                    failFatal(current.name + " setup 异常: " + t);
                    return true;
                }
                phase = 0;
                readyAge = 0;
                waitAge = 0;
            }

            if (phase == 0) {
                readyAge++;
                current.age++;
                boolean ready;
                try {
                    ready = current.ready(level);
                } catch (Throwable t) {
                    failFatal(current.name + " ready 异常: " + t);
                    return true;
                }
                if (ready) {
                    try {
                        current.act(level);
                    } catch (Throwable t) {
                        failFatal(current.name + " act 异常: " + t);
                        return true;
                    }
                    phase = 1;
                    waitAge = 0;
                } else if (readyAge > current.readyTimeout()) {
                    failFatal(current.name + " fixture 超时（动力/网络未建立）");
                    return true;
                }
                return false;
            }

            waitAge++;
            current.age++;
            try {
                current.onTick(level);
                if (current.done(level)) {
                    current.verify(level, this);
                    current.cleanup(level);
                    current = null;
                    phase = 0;
                } else if (waitAge > current.timeout()) {
                    failFatal(current.name + " 等待超时（未出现预期成品/状态）");
                    return true;
                }
            } catch (Throwable t) {
                failFatal(current.name + " tick/verify 异常: " + t);
                return true;
            }

            if (current == null && cases.isEmpty()) {
                finish();
                return true;
            }
            return false;
        }

        private void check(String label, boolean ok) {
            lines.add(label + " -> " + (ok ? "PASS" : "FAIL"));
            total++;
            if (ok) {
                pass++;
            }
        }

        private void failFatal(String message) {
            if (fatal) {
                return;
            }
            check(message, false);
            fatal = true;
            if (current instanceof FixtureCase fixtureCase && fixtureCase.fixture != null) {
                logFixtureDiagnostics(level, fixtureCase.fixture);
            }
            if (current != null) {
                try {
                    current.cleanup(level);
                } catch (Throwable ignored) {
                    // 清理失败不应掩盖原始超时/异常
                }
                current = null;
            }
            finish();
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            AvaritiaSingularityStressMod.LOGGER.info("[crafting-terminal-selftest] RESULT {}/{}", pass, total);
            for (String line : lines) {
                AvaritiaSingularityStressMod.LOGGER.info("[crafting-terminal-selftest] {}", line);
            }
            AvaritiaSingularityStressMod.LOGGER.info("[crafting-terminal-selftest] DONE {}",
                    pass == total && total > 0 && !fatal ? "ALL PASS" : "HAS FAILURE");
        }
    }

    // ------------------------------------------------------------------
    // case 基类
    // ------------------------------------------------------------------

    private abstract static class Case {

        final String name;
        final BlockPos base;
        final Region region;
        int age;

        Case(String name, BlockPos base) {
            this.name = name;
            this.base = base;
            this.region = new Region(base);
        }

        void setup(ServerLevel level) {
            // 专用服无玩家时，出生点外的 chunk 只会 loaded 不会 entity-tick；
            // 终端网络会按设计 fail closed。测试区域显式 force，结束再取消。
            forceChunks(level, region, true);
            clearRegion(level, region);
            build(level);
            age = 0;
        }

        abstract void build(ServerLevel level);

        boolean ready(ServerLevel level) {
            return true;
        }

        int readyTimeout() {
            return READY_TIMEOUT;
        }

        void onTick(ServerLevel level) {
        }

        abstract void act(ServerLevel level);

        boolean done(ServerLevel level) {
            return true;
        }

        int timeout() {
            return DEFAULT_TIMEOUT;
        }

        abstract void verify(ServerLevel level, Runner runner);

        void cleanup(ServerLevel level) {
            clearRegion(level, region);
            forceChunks(level, region, false);
        }
    }

    private abstract static class FixtureCase extends Case {

        Fixture fixture;

        FixtureCase(String name, BlockPos base) {
            super(name, base);
        }

        @Override
        void build(ServerLevel level) {
            fixture = createFixture(level);
            buildFixture(level, fixture);
        }

        abstract Fixture createFixture(ServerLevel level);

        void buildFixture(ServerLevel level, Fixture fixture) {
        }

        abstract boolean powered();

        @Override
        boolean ready(ServerLevel level) {
            if (age < SETTLE_TICKS) {
                return false;
            }
            if (powered()) {
                return poweredReady(level, fixture);
            }
            return allSpeedsZero(level, fixture);
        }
    }

    // ------------------------------------------------------------------
    // 成功闭环
    // ------------------------------------------------------------------

    private static final class SuccessCase extends FixtureCase {

        private final boolean asymmetric;
        private final boolean bothRecipes;
        private final ResourceLocation avaritiaId;
        private final ResourceLocation kineticId;
        private final ResourceLocation recipeId;
        private final Item expectedProduct;
        private final Map<Item, Integer> initialStorage;
        private final Map<Item, Integer> expectedStorage;

        private Snapshot before;
        private CraftResult status;
        private boolean sawStart;
        private boolean busyChecked;
        private CraftResult busyStatus;
        private boolean busyStorageUnchanged = true;

        private SuccessCase(String name, BlockPos base, boolean asymmetric, boolean bothRecipes, String suffix) {
            super(name, base);
            this.asymmetric = asymmetric;
            this.bothRecipes = bothRecipes;
            this.expectedProduct = bothRecipes ? Items.DIAMOND : Items.EMERALD;
            this.avaritiaId = id("selftest_terminal_" + suffix + "_avaritia");
            this.kineticId = id("selftest_terminal_" + suffix + "_kinetic");
            this.recipeId = bothRecipes ? avaritiaId : kineticId;
            this.initialStorage = asymmetric
                    ? Map.of(Items.GOLD_INGOT, 2, Items.COPPER_INGOT, 2, Items.REDSTONE, 2)
                    : Map.of(Items.IRON_INGOT, 2, Items.GOLD_INGOT, 2,
                            Items.COPPER_INGOT, 2, Items.REDSTONE, 2);
            this.expectedStorage = subtractOne(initialStorage);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            // 带空格是“配方虚拟空格”，不是稀疏机器网格：四台必须都在，
            // 空格格由 planner 以 EMPTY 输入保留几何。
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, initialStorage);
            ShapedRecipePattern pattern = asymmetric ? patternAsymmetric() : patternFull();
            List<RecipeHolder<?>> holders = new ArrayList<>();
            if (bothRecipes) {
                holders.add(avaritiaHolder(avaritiaId, pattern));
            }
            holders.add(kineticHolder(kineticId, pattern));
            replaceRecipes(level, holders);
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            return grid != null && grid.crafters().size() == 4;
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        void onTick(ServerLevel level) {
            if (status != CraftResult.STARTED) {
                return;
            }
            boolean any = false;
            for (BlockPos pos : nonEmptyCrafterPositions()) {
                ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
                if (be != null && be.getPhase() != Phase.IDLE) {
                    any = true;
                    break;
                }
            }
            if (any) {
                sawStart = true;
            }
            if (sawStart && !busyChecked) {
                busyChecked = true;
                Map<Item, Integer> beforeBusy = storageCounts(level, fixture);
                busyStatus = terminal(level, fixture).tryStartCraft(recipeId);
                Map<Item, Integer> afterBusy = storageCounts(level, fixture);
                busyStorageUnchanged = beforeBusy.equals(afterBusy);
            }
        }

        @Override
        boolean done(ServerLevel level) {
            if (status != CraftResult.STARTED) {
                return true;
            }
            if (!allIdle(level, fixture)) {
                return false;
            }
            Map<Item, Integer> output = outputCounts(level, fixture);
            return output != null && output.equals(Map.of(expectedProduct, 1));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            boolean started = status == CraftResult.STARTED;
            r.check(name + " 返回 STARTED [" + status + "]", started);
            checkGrid(r, level, fixture, 4);

            if (!started) {
                r.check(name + " 失败时仓库未变", before != null && before.sameAs(snapshot(level, fixture)));
                r.check(name + " 失败时输出箱为空", outputCounts(level, fixture).isEmpty());
                checkNoDrops(r, level, fixture);
                return;
            }

            r.check(name + " 非空格真实起工", sawStart);
            r.check(name + " 最终全部 IDLE", allIdle(level, fixture));

            Map<Item, Integer> output = outputCounts(level, fixture);
            r.check(name + " 输出箱仅有 " + expectedProduct.getDescriptionId(),
                    output != null && output.equals(Map.of(expectedProduct, 1)));
            r.check(name + " 仓库材料恰扣一份", storageCounts(level, fixture).equals(expectedStorage));
            r.check(name + " 盖板状态未被终端改动", coversUnchanged(level, fixture, before));
            checkNoDrops(r, level, fixture);

            r.check(name + " 起工后再次开始返回 BUSY [" + busyStatus + "]",
                    busyChecked && busyStatus == CraftResult.BUSY);
            r.check(name + " 忙时未二次抽料", busyChecked && busyStorageUnchanged);
        }

        private List<BlockPos> nonEmptyCrafterPositions() {
            List<BlockPos> list = new ArrayList<>();
            for (BlockPos pos : fixture.crafters) {
                if (!(asymmetric && pos.equals(fixture.ne))) {
                    list.add(pos);
                }
            }
            return list;
        }

        @Override
        int timeout() {
            return 600;
        }

        @Override
        int readyTimeout() {
            return 180;
        }
    }

    private static final class NoPowerCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_terminal_nopower_avaritia");
        private Snapshot before;
        private CraftResult status;
        private boolean legacyStarted;

        private NoPowerCase(BlockPos base) {
            super("无动力·有效 2×2 网络必须 NO_POWER", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, false, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, Map.of(Items.IRON_INGOT, 1, Items.GOLD_INGOT, 1,
                    Items.COPPER_INGOT, 1, Items.REDSTONE, 1));
            replaceRecipes(level, List.of(avaritiaHolder(recipeId, patternFull())));
        }

        @Override
        boolean powered() {
            return false;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            return grid != null && grid.crafters().size() == 4;
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(recipeId);
            // 兼容旧 boolean API：无动力时也必须 false，不能靠新枚举“看起来拒绝”。
            legacyStarted = terminal(level, fixture).startCraft(recipeId);
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 返回 NO_POWER [" + status + "]", status == CraftResult.NO_POWER);
            r.check(name + " 兼容 boolean 返回 false", !legacyStarted);
            checkGrid(r, level, fixture, 4);
            r.check(name + " 全链速度仍为 0", allSpeedsZero(level, fixture));
            r.check(name + " 仓库/槽位/盖板/阶段全部未变", before != null && before.sameAs(snapshot(level, fixture)));
            r.check(name + " 输出箱为空", outputCounts(level, fixture).isEmpty());
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    private static final class MissingMaterialCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_terminal_missing_avaritia");
        private final Map<Item, Integer> storage = Map.of(Items.IRON_INGOT, 2,
                Items.GOLD_INGOT, 2, Items.COPPER_INGOT, 2);
        private Snapshot before;
        private CraftResult status;

        private MissingMaterialCase(BlockPos base) {
            super("缺料·有动力时 MISSING_MATERIALS 且原样归还", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, storage);
            replaceRecipes(level, List.of(avaritiaHolder(recipeId, patternFull())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            return grid != null && grid.crafters().size() == 4;
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 返回 MISSING_MATERIALS [" + status + "]", status == CraftResult.MISSING_MATERIALS);
            r.check(name + " 仓库/槽位/盖板/阶段全部未变", before != null && before.sameAs(snapshot(level, fixture)));
            r.check(name + " 输出箱为空", outputCounts(level, fixture).isEmpty());
            r.check(name + " 全链 IDLE", allIdle(level, fixture));
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    private static final class TransferFailureCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_terminal_transfer_avaritia");
        private final Map<Item, Integer> storage = Map.of(Items.IRON_INGOT, 2, Items.GOLD_INGOT, 2,
                Items.COPPER_INGOT, 2, Items.REDSTONE, 2);
        private Snapshot before;
        private CraftResult status;

        private TransferFailureCase(BlockPos base) {
            super("部分槽已满·IDLE 时 BUSY 且不弹出原物", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, storage);
            // 预置一个与配方无关的物品，制造“提交到该格必失败”的真实 fixture；
            // 不注册任何测试方块或产品 hook。
            ExtremeMechanicalCrafterBlockEntity blocked = crafter(level, fixture.east);
            if (blocked == null) {
                throw new IllegalStateException("缺少 east 合成器");
            }
            blocked.getInventory().setStackInSlot(0, new ItemStack(Items.DIRT));
            replaceRecipes(level, List.of(avaritiaHolder(recipeId, patternFull())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            if (grid == null || grid.crafters().size() != 4) {
                return false;
            }
            return allIdle(level, fixture);
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 返回 BUSY [" + status + "]", status == CraftResult.BUSY);
            r.check(name + " 预置物品/仓库/盖板/阶段全部未变",
                    before != null && before.sameAs(snapshot(level, fixture)));
            r.check(name + " 输出箱为空", outputCounts(level, fixture).isEmpty());
            r.check(name + " 全链 IDLE", allIdle(level, fixture));
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    /**
     * 真正越过 plan、进入 commit 抽取后才失败：自定义测试 handler 前两笔实际抽取成功，
     * 第三笔模拟通过但实际返回 EMPTY，验证 planner 逆序退款且不启动任何机器。
     */
    private static final class PostExtractFailureCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_terminal_post_extract_avaritia");
        private Snapshot before;
        private CraftResult status;

        private PostExtractFailureCase(BlockPos base) {
            super("提交抽取失败·TRANSFER_FAILED 且退款", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            Fixture fixture = build2x2(level, base, true, true, true);
            // 用 jigsaw BE + 仅测试开关注册的 handler 替换仓库；不注册生产测试方块。
            set(level, fixture.storage, Blocks.JIGSAW.defaultBlockState());
            FAILING_HANDLER.reset(List.of(Items.IRON_INGOT, Items.GOLD_INGOT,
                    Items.COPPER_INGOT, Items.REDSTONE));
            return fixture;
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            replaceRecipes(level, List.of(avaritiaHolder(recipeId, patternFull())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            return grid != null && grid.crafters().size() == 4;
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 返回 TRANSFER_FAILED [" + status + "]", status == CraftResult.TRANSFER_FAILED);
            r.check(name + " 已抽两笔后逆序退款、仓库原样",
                    before != null && before.sameAs(snapshot(level, fixture)));
            r.check(name + " 未启动任何合成器", allIdle(level, fixture));
            r.check(name + " 输出箱为空", outputCounts(level, fixture).isEmpty());
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    /**
     * 在途改箭头：叶子 A 已进入 EXPORTING 时，把 A 指向未纳管且 IDLE、聚合里有玩家物料的 C。
     * 任务必须按 jobId 取消，只回收 A/B，不碰 C，也不产生成品。
     */
    private static final class ArrowChangeCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_terminal_arrow_avaritia");
        private final BlockPos leafA;
        private final BlockPos rootB;
        private final BlockPos unmanagedC;

        private CrafterSnapshot cBefore;
        private CraftResult status;
        private boolean sawExporting;
        private boolean retargeted;

        private ArrowChangeCase(BlockPos base) {
            super("在途改箭头·任务取消且 C 不变", base);
            this.rootB = base;
            this.leafA = base.above();
            this.unmanagedC = base.east().above();
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            BlockPos terminal = rootB.south().south();
            BlockPos storage = terminal.east();
            BlockPos output = rootB.below();
            List<BlockPos> crafters = List.of(rootB, leafA);
            placeCrafter(level, rootB, Pointing.DOWN);
            placeCrafter(level, leafA, Pointing.DOWN);
            // C 与 A 同朝向但未贴连接器，不进终端网络；它只作为“改箭头后的无关目标”。
            placeCrafter(level, unmanagedC, Pointing.DOWN);
            placeConnector(level, rootB.south());
            placeConnector(level, leafA.south());
            placeTerminal(level, terminal);
            placeChest(level, output);
            placeChest(level, storage);
            placePower(level, base);
            return new Fixture(base, rootB, leafA, base.east(), null, terminal, storage, output,
                    crafters, Map.of(), new Region(base));
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, Map.of(Items.GOLD_INGOT, 1, Items.IRON_INGOT, 1));
            replaceRecipes(level, List.of(avaritiaHolder(recipeId, patternArrow())));

            ExtremeMechanicalCrafterBlockEntity c = crafter(level, unmanagedC);
            if (c == null) {
                throw new IllegalStateException("缺少未纳管 C");
            }
            c.getGroupedItems().grid.put(Pair.of(0, 0), new ItemStack(Items.DIRT));
            c.getGroupedItems().statsReady = false;
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            if (!super.ready(level)) {
                return false;
            }
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            if (grid == null || grid.crafters().size() != 2) {
                return false;
            }
            ExtremeMechanicalCrafterBlockEntity c = crafter(level, unmanagedC);
            return c != null && c.getPhase() == Phase.IDLE && !c.hasTerminalJob()
                    && c.getInventory().getItem(0).isEmpty()
                    && c.getGroupedItems().grid.size() == 1;
        }

        @Override
        void act(ServerLevel level) {
            cBefore = snapshotCrafter(level, unmanagedC);
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        void onTick(ServerLevel level) {
            if (status != CraftResult.STARTED || retargeted) {
                return;
            }
            ExtremeMechanicalCrafterBlockEntity a = crafter(level, leafA);
            if (a == null || a.getPhase() != Phase.EXPORTING) {
                return;
            }
            sawExporting = true;
            BlockState state = level.getBlockState(leafA);
            level.setBlock(leafA, state.setValue(ExtremeMechanicalCrafterBlock.POINTING, Pointing.LEFT),
                    Block.UPDATE_ALL);
            retargeted = true;
        }

        @Override
        boolean done(ServerLevel level) {
            if (status != CraftResult.STARTED) {
                return true;
            }
            if (!retargeted) {
                return false;
            }
            ExtremeMechanicalCrafterBlockEntity a = crafter(level, leafA);
            ExtremeMechanicalCrafterBlockEntity b = crafter(level, rootB);
            return a != null && b != null
                    && a.getPhase() == Phase.IDLE && b.getPhase() == Phase.IDLE
                    && !a.hasTerminalJob() && !b.hasTerminalJob()
                    && dropsIn(level, fixture.region).size() >= 2;
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 起工成功 [" + status + "]", status == CraftResult.STARTED);
            r.check(name + " A 确实经过 EXPORTING", sawExporting);
            r.check(name + " 箭头已改向 C", retargeted);

            CrafterSnapshot cAfter = snapshotCrafter(level, unmanagedC);
            r.check(name + " C 原槽/聚合/阶段不变", cBefore != null && cBefore.sameAs(cAfter));
            r.check(name + " C 仍 IDLE 且无任务", cAfter != null
                    && cAfter.phase == Phase.IDLE
                    && !crafter(level, unmanagedC).hasTerminalJob());

            ExtremeMechanicalCrafterBlockEntity a = crafter(level, leafA);
            ExtremeMechanicalCrafterBlockEntity b = crafter(level, rootB);
            r.check(name + " A/B 回 IDLE 且无残留任务",
                    a != null && b != null
                            && a.getPhase() == Phase.IDLE && b.getPhase() == Phase.IDLE
                            && !a.hasTerminalJob() && !b.hasTerminalJob());
            r.check(name + " B 原根未卡忙", terminal(level, fixture).tryStartCraft(recipeId)
                    != CraftResult.BUSY);
            r.check(name + " 输出箱没有其它成品", outputCounts(level, fixture).isEmpty());

            Map<Item, Integer> drops = dropCounts(level, fixture.region);
            r.check(name + " 掉落仅本单原料各一份",
                    drops.equals(Map.of(Items.GOLD_INGOT, 1, Items.IRON_INGOT, 1)));
            r.check(name + " A/B 槽位与聚合已清空",
                    a != null && b != null
                            && a.getInventory().getItem(0).isEmpty()
                            && b.getInventory().getItem(0).isEmpty()
                            && a.getGroupedItems().grid.isEmpty()
                            && b.getGroupedItems().grid.isEmpty());
            r.check(name + " 仓库已被本单抽空", storageCounts(level, fixture).isEmpty());
        }

        @Override
        int timeout() {
            return 300;
        }

        @Override
        int readyTimeout() {
            return 180;
        }
    }

    // ------------------------------------------------------------------
    // 非法网络 / 网格
    // ------------------------------------------------------------------

    private abstract static class InvalidCase extends FixtureCase {

        private final boolean expectGridNull;
        private final int expectedCrafters;
        private final Set<CraftResult> allowedStatuses;
        private Snapshot before;
        private CraftResult status;

        InvalidCase(String name, BlockPos base, boolean expectGridNull, int expectedCrafters,
                    Set<CraftResult> allowedStatuses) {
            super(name, base);
            this.expectGridNull = expectGridNull;
            this.expectedCrafters = expectedCrafters;
            this.allowedStatuses = allowedStatuses;
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, Map.of(Items.IRON_INGOT, 2, Items.GOLD_INGOT, 2,
                    Items.COPPER_INGOT, 2, Items.REDSTONE, 2));
            replaceRecipes(level, List.of(avaritiaHolder(id("selftest_terminal_invalid_avaritia"),
                    patternFull())));
        }

        @Override
        void act(ServerLevel level) {
            before = snapshot(level, fixture);
            status = terminal(level, fixture).tryStartCraft(id("selftest_terminal_invalid_avaritia"));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 安全拒绝，不返回 STARTED [" + status + "]", status != CraftResult.STARTED);
            r.check(name + " 拒绝状态在预期集合 " + allowedStatuses, allowedStatuses.contains(status));
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            if (expectGridNull) {
                r.check(name + " findCraftingGrid 为 null", grid == null);
            } else {
                r.check(name + " findCraftingGrid 有 " + expectedCrafters + " 台",
                        grid != null && grid.crafters().size() == expectedCrafters);
            }
            r.check(name + " 仓库/槽位/盖板/阶段全部未变",
                    before != null && before.sameAs(snapshot(level, fixture)));
            r.check(name + " 输出箱为空", outputCounts(level, fixture).isEmpty());
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    private static final class MultiRootCase extends InvalidCase {
        MultiRootCase(BlockPos base) {
            super("多根网络·安全拒绝", base, true, 0,
                    Set.of(CraftResult.INVALID_NETWORK, CraftResult.NO_POWER));
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return buildMultiRoot(level, base);
        }

        @Override
        boolean powered() {
            return false;
        }
    }

    private static final class CycleCase extends InvalidCase {
        CycleCase(BlockPos base) {
            super("环·安全拒绝", base, true, 0,
                    Set.of(CraftResult.INVALID_NETWORK, CraftResult.NO_POWER));
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return buildCycle(level, base);
        }

        @Override
        boolean powered() {
            return false;
        }
    }

    private static final class MissingConnectorCase extends InvalidCase {
        MissingConnectorCase(BlockPos base) {
            // 缺一台连接器会让真实链出现“实际入/出邻居未纳管”，findCraftingGrid 必须 fail closed；
            // 这里不把半条链当有效网格。
            super("真实链缺连接器·安全拒绝", base, true, 0,
                    Set.of(CraftResult.INVALID_NETWORK, CraftResult.GRID_MISMATCH));
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, false);
        }

        @Override
        boolean powered() {
            return true;
        }
    }

    private static final class WrongSideCase extends InvalidCase {
        WrongSideCase(BlockPos base) {
            super("错误侧贴连接器·安全拒绝", base, true, 0,
                    Set.of(CraftResult.INVALID_NETWORK, CraftResult.NO_POWER));
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return buildWrongSide(level, base);
        }

        @Override
        boolean powered() {
            return false;
        }
    }

    private static final class LMissingCellCase extends InvalidCase {
        LMissingCellCase(BlockPos base) {
            super("有效 L 网格缺必须格·GRID_MISMATCH", base, false, 3,
                    Set.of(CraftResult.GRID_MISMATCH, CraftResult.INVALID_NETWORK));
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, false, true);
        }

        @Override
        boolean powered() {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // 炽骨网络 / 形状过滤 / 红石书签 / 创造板条箱（2026-11-20 扩展）
    // ------------------------------------------------------------------

    /** 炽骨 2×2 网络：tier2 可起工出货；tier3 与列表准入拒绝；B31 专属类型放行。 */
    private static final class BlazeTerminalCase extends FixtureCase {

        private final ResourceLocation tier2Id = id("selftest_blaze_net_tier2");
        private final ResourceLocation tier3Id = id("selftest_blaze_net_tier3");
        private final ResourceLocation blazeId = id("selftest_blaze_net_b31");
        private CraftResult status;
        private boolean sawStart;

        private BlazeTerminalCase(BlockPos base) {
            super("炽骨网络·tier2 起工·tier3 拒绝·B31 放行", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true, ModBlocks.BLAZE_CUBE_CRAFTER.get());
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, Map.of(Items.IRON_INGOT, 4));
            ShapedRecipePattern iron = patternIron2x2();
            replaceRecipes(level, List.of(
                    new RecipeHolder<>(tier2Id, new ShapedTableCraftingRecipe(iron, new ItemStack(Items.DIAMOND), 2, false)),
                    new RecipeHolder<>(tier3Id, new ShapedTableCraftingRecipe(iron, new ItemStack(Items.EMERALD), 3, false)),
                    new RecipeHolder<>(blazeId, new committee.nova.avaritia_singularity_stress.content.recipes.BlazeCubeCraftingRecipe(
                            "", CraftingBookCategory.MISC, iron, new ItemStack(Items.GOLD_INGOT), true, true))));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            return super.ready(level) && terminal(level, fixture).getCrafterCount() == 4;
        }

        @Override
        void act(ServerLevel level) {
            status = terminal(level, fixture).tryStartCraft(tier2Id);
        }

        @Override
        void onTick(ServerLevel level) {
            if (status != CraftResult.STARTED) {
                return;
            }
            for (BlockPos pos : fixture.crafters) {
                ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
                if (be != null && be.getPhase() != Phase.IDLE) {
                    sawStart = true;
                    break;
                }
            }
        }

        @Override
        boolean done(ServerLevel level) {
            return status != CraftResult.STARTED
                    || (allIdle(level, fixture) && outputCounts(level, fixture).equals(Map.of(Items.DIAMOND, 1)));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " tier2 返回 STARTED [" + status + "]", status == CraftResult.STARTED);
            r.check(name + " 真实起工", sawStart);
            r.check(name + " 输出钻石×1", outputCounts(level, fixture).equals(Map.of(Items.DIAMOND, 1)));
            r.check(name + " 仓库铁恰扣 4", storageCounts(level, fixture).equals(Map.of()));
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            r.check(name + " 根品类为炽骨", grid != null
                    && grid.root().getBlockState().getBlock() instanceof BlazeCubeCrafterBlock);
            r.check(name + " tier3 起工拒绝 INVALID_RECIPE",
                    terminal(level, fixture).tryStartCraft(tier3Id) == CraftResult.INVALID_RECIPE);
            RecipeHolder<?> blazeHolder = level.getRecipeManager().byKey(blazeId).orElse(null);
            RecipeHolder<?> tier3Holder = level.getRecipeManager().byKey(tier3Id).orElse(null);
            r.check(name + " B31 品类准入放行", blazeHolder != null
                    && ExtremeRecipeGridHandler.supportsAutoCraftFor(blazeHolder.value(), true));
            r.check(name + " B31 列表准入放行", blazeHolder != null
                    && ExtremeCraftingTerminalMenu.isSupportedRecipeFor(
                            blazeHolder.value(), level.registryAccess(), terminal(level, fixture)));
            r.check(name + " tier3 列表准入拒绝", tier3Holder != null
                    && !ExtremeCraftingTerminalMenu.isSupportedRecipeFor(
                            tier3Holder.value(), level.registryAccess(), terminal(level, fixture)));
        }

        @Override
        int timeout() {
            return 600;
        }
    }

    /** 2×2 网络：3×3 配方按数量/摆放被列表与起工双重屏蔽。 */
    private static final class ShapeFilterCase extends FixtureCase {

        private final ResourceLocation bigId = id("selftest_shape_3x3");
        private final ResourceLocation smallId = id("selftest_shape_2x2");

        private ShapeFilterCase(BlockPos base) {
            super("形状过滤·2×2 网络屏蔽 3×3 配方", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            ShapedRecipePattern big = ShapedRecipePattern.of(
                    Map.of('A', Ingredient.of(Items.IRON_INGOT)), "AAA", "AAA", "AAA");
            replaceRecipes(level, List.of(
                    kineticHolder(bigId, big), kineticHolder(smallId, patternIron2x2())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            return super.ready(level) && terminal(level, fixture).getCrafterCount() == 4;
        }

        @Override
        void act(ServerLevel level) {
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
            r.check(name + " 网格非空", grid != null);
            if (grid == null) {
                return;
            }
            Map<Pair<Integer, Integer>, Boolean> cells = TerminalCraftPlanner.cellsOf(grid);
            RecipeHolder<?> big = level.getRecipeManager().byKey(bigId).orElse(null);
            RecipeHolder<?> small = level.getRecipeManager().byKey(smallId).orElse(null);
            r.check(name + " fitsShape 3×3=false", big != null && !TerminalCraftPlanner.fitsShape(cells, big.value()));
            r.check(name + " fitsShape 2×2=true", small != null && TerminalCraftPlanner.fitsShape(cells, small.value()));
            r.check(name + " 列表准入 3×3=false", big != null && !ExtremeCraftingTerminalMenu.isSupportedRecipeFor(
                    big.value(), level.registryAccess(), terminal(level, fixture)));
            r.check(name + " 列表准入 2×2=true", small != null && ExtremeCraftingTerminalMenu.isSupportedRecipeFor(
                    small.value(), level.registryAccess(), terminal(level, fixture)));
            r.check(name + " 起工 3×3=GRID_MISMATCH",
                    terminal(level, fixture).tryStartCraft(bigId) == CraftResult.GRID_MISMATCH);
        }
    }

    /** 钉住配方后不手动点合成：红石上升沿自动起工并出货。 */
    private static final class RedstonePinCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_redstone_pin");
        private boolean sawStart;

        private RedstonePinCase(BlockPos base) {
            super("红石书签·钉住后信号自动起工", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            fillStorage(level, fixture, Map.of(Items.IRON_INGOT, 4));
            replaceRecipes(level, List.of(kineticHolder(recipeId, patternIron2x2())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            return super.ready(level) && terminal(level, fixture).getCrafterCount() == 4;
        }

        @Override
        void act(ServerLevel level) {
            terminal(level, fixture).pinRecipe(recipeId);
            // 红石块贴终端上方：hasNeighborSignal 上升沿触发 tick() 自动起工。
            set(level, fixture.terminal.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        }

        @Override
        void onTick(ServerLevel level) {
            for (BlockPos pos : fixture.crafters) {
                ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
                if (be != null && be.getPhase() != Phase.IDLE) {
                    sawStart = true;
                    break;
                }
            }
        }

        @Override
        boolean done(ServerLevel level) {
            return allIdle(level, fixture) && sawStart
                    && outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 钉住持久化", recipeId.toString().equals(terminal(level, fixture).getPinnedRecipeId()));
            r.check(name + " 无手动点击真实起工", sawStart);
            r.check(name + " 输出绿宝石×1", outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1)));
            r.check(name + " 仓库铁恰扣 4", storageCounts(level, fixture).equals(Map.of()));
        }

        @Override
        int timeout() {
            return 600;
        }
    }

    /** 相邻未过滤创造板条箱：空仓库也能起工（无限供料），不消耗实物。 */
    private static final class CreativeCrateCase extends FixtureCase {

        private final ResourceLocation recipeId = id("selftest_creative_crate");
        private CraftResult status;

        private CreativeCrateCase(BlockPos base) {
            super("创造板条箱·未过滤免材料起工", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            // 仓库保持空；创造板条箱贴终端西侧，默认无过滤。
            set(level, fixture.terminal.west(), AllBlocks.CREATIVE_CRATE.get().defaultBlockState());
            replaceRecipes(level, List.of(kineticHolder(recipeId, patternIron2x2())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            return super.ready(level) && terminal(level, fixture).getCrafterCount() == 4;
        }

        @Override
        void act(ServerLevel level) {
            status = terminal(level, fixture).tryStartCraft(recipeId);
        }

        @Override
        boolean done(ServerLevel level) {
            return status != CraftResult.STARTED
                    || (allIdle(level, fixture) && outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1)));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 空仓库返回 STARTED [" + status + "]", status == CraftResult.STARTED);
            r.check(name + " 输出绿宝石×1", outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1)));
            r.check(name + " 仓库仍为空", storageCounts(level, fixture).equals(Map.of()));
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 600;
        }
    }

    private static ShapedRecipePattern patternIron2x2() {
        return ShapedRecipePattern.of(Map.of('A', Ingredient.of(Items.IRON_INGOT)), "AA", "AA");
    }

    /** B32 锚点模型：终端邻面漏斗读频道，同频道抽取端货源供料；未绑频/私有他人均拒绝。 */
    private static final class WirelessFunnelCase extends FixtureCase {

        private static final java.util.UUID OWNER_A = java.util.UUID.fromString("00000000-0000-0000-0000-00000000000a");
        private static final java.util.UUID OWNER_B = java.util.UUID.fromString("00000000-0000-0000-0000-00000000000b");
        private static final int FREQ = 4321;

        private final ResourceLocation recipeId = id("selftest_wireless_funnel");
        private BlockPos remoteChest;
        private BlockPos funnelPos;
        private BlockPos anchorPos;
        private CraftResult statusNoFreq;
        private CraftResult statusPrivate;
        private CraftResult status;

        private WirelessFunnelCase(BlockPos base) {
            super("无线漏斗·邻面锚点·同频道远程供料·私有鉴权", base);
        }

        @Override
        Fixture createFixture(ServerLevel level) {
            return build2x2(level, base, true, true, true);
        }

        @Override
        void buildFixture(ServerLevel level, Fixture fixture) {
            remoteChest = base.south(5);
            funnelPos = base.south(4);
            anchorPos = fixture.terminal.west();
            set(level, remoteChest, Blocks.CHEST.defaultBlockState());
            // 远程抽取端朝北：背后（南）是货源箱。POWERED=true 关掉自主搬运，避免测试期把箱抽进缓存。
            set(level, funnelPos, ModBlocks.WIRELESS_ITEM_FUNNEL.get().defaultBlockState()
                    .setValue(FunnelBlock.FACING, Direction.NORTH)
                    .setValue(FunnelBlock.EXTRACTING, true)
                    .setValue(FunnelBlock.POWERED, true));
            // 锚点贴终端西面：非抽取端、朝北对着空气（无本地去处），只供终端读频道。
            set(level, anchorPos, ModBlocks.WIRELESS_ITEM_FUNNEL.get().defaultBlockState()
                    .setValue(FunnelBlock.FACING, Direction.NORTH)
                    .setValue(FunnelBlock.POWERED, true));
            IItemHandler chest = itemHandler(level, remoteChest);
            for (int i = 0; i < 4; i++) {
                ItemHandlerHelper.insertItem(chest, new ItemStack(Items.IRON_INGOT), false);
            }
            if (level.getBlockEntity(funnelPos) instanceof WirelessItemFunnelBlockEntity funnel) {
                funnel.setFrequency(FREQ);
                funnel.setVisibility(IFrequencyBindable.ChannelVisibility.PRIVATE);
                funnel.setOwner(OWNER_B);
            }
            replaceRecipes(level, List.of(kineticHolder(recipeId, patternIron2x2())));
        }

        @Override
        boolean powered() {
            return true;
        }

        @Override
        boolean ready(ServerLevel level) {
            return super.ready(level) && terminal(level, fixture).getCrafterCount() == 4
                    && level.getBlockEntity(funnelPos) instanceof WirelessItemFunnelBlockEntity f
                    && f.getFrequency() == FREQ;
        }

        @Override
        void act(ServerLevel level) {
            ExtremeCraftingTerminalBlockEntity terminal = terminal(level, fixture);
            // 锚点未绑频：终端不搜索任何远程频道。
            statusNoFreq = terminal.tryStartCraft(recipeId);
            if (level.getBlockEntity(anchorPos) instanceof WirelessItemFunnelBlockEntity anchor) {
                anchor.setFrequency(FREQ);
                anchor.setVisibility(IFrequencyBindable.ChannelVisibility.PRIVATE);
                anchor.setOwner(OWNER_A);
            }
            // 锚点私有频道所有者 A，远程漏斗所有者 B：频道键不同，拒绝。
            statusPrivate = terminal.tryStartCraft(recipeId);
            if (level.getBlockEntity(funnelPos) instanceof WirelessItemFunnelBlockEntity funnel) {
                funnel.setOwner(OWNER_A);
            }
            status = terminal.tryStartCraft(recipeId);
        }

        @Override
        boolean done(ServerLevel level) {
            return status != CraftResult.STARTED
                    || (allIdle(level, fixture) && outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1)));
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 频率关闭拒绝 [" + statusNoFreq + "]", statusNoFreq == CraftResult.MISSING_MATERIALS);
            r.check(name + " 私有他人拒绝 [" + statusPrivate + "]", statusPrivate == CraftResult.MISSING_MATERIALS);
            r.check(name + " 同频同所有者 STARTED [" + status + "]", status == CraftResult.STARTED);
            r.check(name + " 输出绿宝石×1", outputCounts(level, fixture).equals(Map.of(Items.EMERALD, 1)));
            r.check(name + " 远程箱铁恰扣 4", counts(itemHandler(level, remoteChest)).equals(Map.of()));
            r.check(name + " 相邻仓库仍为空", storageCounts(level, fixture).equals(Map.of()));
            checkNoDrops(r, level, fixture);
        }

        @Override
        int timeout() {
            return 600;
        }
    }

    // ------------------------------------------------------------------
    // 手动配方优先级 / 真实配方布局
    // ------------------------------------------------------------------

    private static final class ManualPriorityCase extends Case {

        private ItemStack bothResult = ItemStack.EMPTY;
        private ItemStack avaritiaOnlyResult = ItemStack.EMPTY;
        private ExtremeCraftingTerminalBlockEntity.RecipeLayout crystalLayout;
        private ExtremeCraftingTerminalBlockEntity.RecipeLayout ancientLayout;

        private ManualPriorityCase(BlockPos base) {
            super("手动配方优先级 / 真实 Avaritia 布局", base);
        }

        @Override
        void build(ServerLevel level) {
            placeCrafter(level, base, Pointing.DOWN);
        }

        @Override
        void act(ServerLevel level) {
            // 必须先读取真实数据包配方；后面的 replaceRecipes 会替换整张 recipe manager。
            crystalLayout = realLayout(level, "avaritia", "crystal_matrix_ingot");
            ancientLayout = realLayout(level, "avaritia", "ancient_debris_eight");

            ExtremeMechanicalCrafterBlockEntity crafter = crafter(level, base);
            if (crafter == null) {
                throw new IllegalStateException("缺少手动优先级 fixture 合成器");
            }

            ShapedRecipePattern pattern = patternFull();
            replaceRecipes(level, List.of(
                    avaritiaHolder(id("selftest_terminal_manual_avaritia"), pattern),
                    kineticHolder(id("selftest_terminal_manual_kinetic"), pattern)));
            seedGrid(crafter);
            bothResult = ExtremeRecipeGridHandler.tryToApplyRecipe(level, crafter);

            replaceRecipes(level, List.of(avaritiaHolder(id("selftest_terminal_manual_avaritia_only"), pattern)));
            seedGrid(crafter);
            avaritiaOnlyResult = ExtremeRecipeGridHandler.tryToApplyRecipe(level, crafter);
        }

        @Override
        void verify(ServerLevel level, Runner r) {
            r.check(name + " 同料 kinetic 优先于 Avaritia",
                    produces(bothResult, Items.EMERALD));
            r.check(name + " 无 kinetic 时回落 Avaritia",
                    produces(avaritiaOnlyResult, Items.DIAMOND));

            if (crystalLayout == null) {
                r.check(name + " 真实 avaritia:crystal_matrix_ingot 布局存在", false);
            } else {
                r.check(name + " 真实 crystal_matrix_ingot 为 3×2",
                        crystalLayout.width() == 3 && crystalLayout.height() == 2);
                r.check(name + " 真实 crystal_matrix_ingot 产物正确",
                        produces(crystalLayout.result(), item("avaritia", "crystal_matrix_ingot")));
            }

            if (ancientLayout == null) {
                r.check(name + " 真实 avaritia:ancient_debris_eight 布局存在", false);
            } else {
                r.check(name + " 真实 ancient_debris_eight 为 3×3",
                        ancientLayout.width() == 3 && ancientLayout.height() == 3);
                r.check(name + " 真实 ancient_debris_eight 产物数量 8",
                        produces(ancientLayout.result(), item("minecraft", "ancient_debris"))
                                && ancientLayout.result().getCount() == 8);
            }
        }

        @Override
        int timeout() {
            return 30;
        }
    }

    // ------------------------------------------------------------------
    // fixture 构建
    // ------------------------------------------------------------------

    private static Fixture build2x2(ServerLevel level, BlockPos base, boolean powered,
                                    boolean withNe, boolean withNeConnector) {
        return build2x2(level, base, powered, withNe, withNeConnector,
                ModBlocks.EXTREME_MECHANICAL_CRAFTER.get());
    }

    private static Fixture build2x2(ServerLevel level, BlockPos base, boolean powered,
                                    boolean withNe, boolean withNeConnector, Block crafterBlock) {
        BlockPos root = base;
        BlockPos east = base.east();
        BlockPos above = base.above();
        BlockPos ne = base.east().above();
        BlockPos terminal = base.south().south();
        BlockPos storage = terminal.east();
        BlockPos output = base.below();

        List<BlockPos> crafters = new ArrayList<>();
        Map<BlockPos, Pointing> pointings = new LinkedHashMap<>();
        addCrafter(level, crafters, pointings, root, Pointing.DOWN, crafterBlock);
        addCrafter(level, crafters, pointings, east, Pointing.RIGHT, crafterBlock);
        addCrafter(level, crafters, pointings, above, Pointing.DOWN, crafterBlock);
        if (withNe) {
            addCrafter(level, crafters, pointings, ne, Pointing.RIGHT, crafterBlock);
        }

        for (BlockPos pos : crafters) {
            if (!withNeConnector && pos.equals(ne)) {
                continue;
            }
            placeConnector(level, pos.south());
        }
        // 位置语义回归：模拟用户对着相邻连接器侧边点击放置（FACING 不指向合成器）。
        // 根连接器故意朝 EAST，只要位置在背面仍应纳管，整链照常起工。
        set(level, root.south(), ModBlocks.EXTREME_CONNECTOR.get().defaultBlockState()
                .setValue(ExtremeConnectorBlock.FACING, Direction.EAST));
        placeTerminal(level, terminal);
        placeChest(level, output);
        placeChest(level, storage);
        if (powered) {
            placePower(level, base);
        }
        return new Fixture(base, root, above, east, withNe ? ne : null, terminal, storage, output,
                crafters, pointings, new Region(base));
    }

    private static Fixture buildMultiRoot(ServerLevel level, BlockPos base) {
        BlockPos root1 = base;
        BlockPos root2 = base.east(2);
        BlockPos bridge = base.east().south();
        BlockPos terminal = bridge.south();
        BlockPos storage = terminal.east();
        BlockPos output = root1.below();

        List<BlockPos> crafters = new ArrayList<>();
        Map<BlockPos, Pointing> pointings = new LinkedHashMap<>();
        addCrafter(level, crafters, pointings, root1, Pointing.DOWN);
        addCrafter(level, crafters, pointings, root2, Pointing.DOWN);
        placeConnector(level, root1.south());
        placeConnector(level, root2.south());
        // 桥接连接器不贴任何合成器，只把两个 root 的网络连成一个多根网络。
        placeConnector(level, bridge);
        placeTerminal(level, terminal);
        placeChest(level, output);
        placeChest(level, storage);
        return new Fixture(base, root1, base.above(), base.east(), null, terminal, storage, output,
                crafters, pointings, new Region(base));
    }

    private static Fixture buildCycle(ServerLevel level, BlockPos base) {
        BlockPos root = base;
        BlockPos above = base.above();
        BlockPos terminal = base.south().south();
        BlockPos storage = terminal.east();
        BlockPos output = base.below();

        List<BlockPos> crafters = new ArrayList<>();
        Map<BlockPos, Pointing> pointings = new LinkedHashMap<>();
        addCrafter(level, crafters, pointings, root, Pointing.UP);
        addCrafter(level, crafters, pointings, above, Pointing.DOWN);
        placeConnector(level, root.south());
        placeConnector(level, above.south());
        placeTerminal(level, terminal);
        placeChest(level, output);
        placeChest(level, storage);
        return new Fixture(base, root, above, base.east(), null, terminal, storage, output,
                crafters, pointings, new Region(base));
    }

    private static Fixture buildWrongSide(ServerLevel level, BlockPos base) {
        BlockPos root = base;
        BlockPos connector = base.east();
        BlockPos terminal = connector.east();
        BlockPos storage = terminal.south();
        BlockPos output = base.below();

        List<BlockPos> crafters = new ArrayList<>();
        Map<BlockPos, Pointing> pointings = new LinkedHashMap<>();
        addCrafter(level, crafters, pointings, root, Pointing.DOWN);
        placeConnector(level, connector);
        placeTerminal(level, terminal);
        placeChest(level, output);
        placeChest(level, storage);
        return new Fixture(base, root, base.above(), base.east(), null, terminal, storage, output,
                crafters, pointings, new Region(base));
    }

    private static void addCrafter(ServerLevel level, List<BlockPos> crafters,
                                   Map<BlockPos, Pointing> pointings, BlockPos pos, Pointing pointing) {
        addCrafter(level, crafters, pointings, pos, pointing, ModBlocks.EXTREME_MECHANICAL_CRAFTER.get());
    }

    private static void addCrafter(ServerLevel level, List<BlockPos> crafters,
                                   Map<BlockPos, Pointing> pointings, BlockPos pos, Pointing pointing,
                                   Block crafterBlock) {
        placeCrafter(level, pos, pointing, crafterBlock);
        crafters.add(pos);
        pointings.put(pos, pointing);
    }

    private static void placePower(ServerLevel level, BlockPos base) {
        BlockState cog = AllBlocks.COGWHEEL.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        // 2×2 合成器本身就是同轴小齿轮阵列：相邻格会以 -1 互相啮合。
        // 只在根西侧放一台小齿轮，再由它啮合整片网格，避免两个动力源相位冲突。
        set(level, base.west(), cog);

        BlockState motor = AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH);
        // 马达朝南，轴端顶在西侧小齿轮背面；唯一动力源。
        set(level, base.west().north(), motor);
    }

    private static void placeCrafter(ServerLevel level, BlockPos pos, Pointing pointing) {
        placeCrafter(level, pos, pointing, ModBlocks.EXTREME_MECHANICAL_CRAFTER.get());
    }

    private static void placeCrafter(ServerLevel level, BlockPos pos, Pointing pointing, Block crafterBlock) {
        BlockState state = crafterBlock.defaultBlockState()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(ExtremeMechanicalCrafterBlock.POINTING, pointing);
        set(level, pos, state);
    }

    private static void placeConnector(ServerLevel level, BlockPos pos) {
        BlockState state = ModBlocks.EXTREME_CONNECTOR.get().defaultBlockState()
                .setValue(ExtremeConnectorBlock.FACING, Direction.NORTH);
        set(level, pos, state);
    }

    private static void placeTerminal(ServerLevel level, BlockPos pos) {
        set(level, pos, ModBlocks.EXTREME_CRAFTING_TERMINAL.get().defaultBlockState());
    }

    private static void placeChest(ServerLevel level, BlockPos pos) {
        set(level, pos, Blocks.CHEST.defaultBlockState());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.getChunk(pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    // ------------------------------------------------------------------
    // 快照 / 断言工具
    // ------------------------------------------------------------------

    private static Snapshot snapshot(ServerLevel level, Fixture fixture) {
        Map<BlockPos, CrafterSnapshot> crafters = new HashMap<>();
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null) {
                throw new IllegalStateException("快照时缺少合成器: " + pos);
            }
            crafters.put(pos, new CrafterSnapshot(
                    be.getPhase(), be.isCovered(), be.getInventory().getItem(0).copy(),
                    copyGrid(be.getGroupedItems())));
        }
        IItemHandler storage = itemHandler(level, fixture.storage);
        Map<Item, Integer> stored = storage == null ? Map.of() : counts(storage);
        return new Snapshot(crafters, stored, dropsIn(level, fixture.region).size());
    }

    private static CrafterSnapshot snapshotCrafter(ServerLevel level, BlockPos pos) {
        ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
        if (be == null) {
            throw new IllegalStateException("快照时缺少合成器: " + pos);
        }
        return new CrafterSnapshot(be.getPhase(), be.isCovered(),
                be.getInventory().getItem(0).copy(), copyGrid(be.getGroupedItems()));
    }

    private static Map<Pair<Integer, Integer>, ItemStack> copyGrid(GroupedItems items) {
        Map<Pair<Integer, Integer>, ItemStack> copy = new HashMap<>();
        if (items != null) {
            items.grid.forEach((key, stack) -> copy.put(key, stack.copy()));
        }
        return copy;
    }

    private static Map<Item, Integer> storageCounts(ServerLevel level, Fixture fixture) {
        IItemHandler handler = itemHandler(level, fixture.storage);
        return handler == null ? Map.of() : counts(handler);
    }

    private static Map<Item, Integer> outputCounts(ServerLevel level, Fixture fixture) {
        IItemHandler handler = itemHandler(level, fixture.output);
        return handler == null ? Map.of() : counts(handler);
    }

    private static Map<Item, Integer> counts(IItemHandler handler) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static void fillStorage(ServerLevel level, Fixture fixture, Map<Item, Integer> items) {
        IItemHandler handler = itemHandler(level, fixture.storage);
        if (handler == null) {
            throw new IllegalStateException("仓库箱子没有 ItemHandler 能力");
        }
        for (Map.Entry<Item, Integer> entry : items.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                ItemStack remainder = ItemHandlerHelper.insertItem(handler, new ItemStack(entry.getKey()), false);
                if (!remainder.isEmpty()) {
                    throw new IllegalStateException("仓库装不下测试材料: " + entry.getKey());
                }
            }
        }
    }

    private static void checkGrid(Runner r, ServerLevel level, Fixture fixture, int expectedCrafters) {
        CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
        r.check("findCraftingGrid 非空", grid != null);
        if (grid == null) {
            return;
        }
        r.check("findCraftingGrid 根为输出根", grid.root().getBlockPos().equals(fixture.root));
        r.check("findCraftingGrid 台数 " + expectedCrafters, grid.crafters().size() == expectedCrafters);
        r.check("根坐标(0,0)", gridCoord(grid, fixture.root, 0, 0));
        r.check("东侧坐标(-1,0)", gridCoord(grid, fixture.east, -1, 0));
        r.check("上方坐标(0,1)", gridCoord(grid, fixture.above, 0, 1));
        if (fixture.ne != null) {
            r.check("东北坐标(-1,1)", gridCoord(grid, fixture.ne, -1, 1));
        }
    }

    private static void checkNoDrops(Runner r, ServerLevel level, Fixture fixture) {
        List<ItemEntity> drops = dropsIn(level, fixture.region);
        if (!drops.isEmpty()) {
            for (ItemEntity drop : drops) {
                AvaritiaSingularityStressMod.LOGGER.info(
                        "[crafting-terminal-selftest] 意外掉落 {} x{} @ {}",
                        drop.getItem().getDescriptionId(), drop.getItem().getCount(), drop.blockPosition());
            }
        }
        r.check("无意外掉落", drops.isEmpty());
    }

    private static List<ItemEntity> dropsIn(ServerLevel level, Region region) {
        return level.getEntitiesOfClass(ItemEntity.class, region.aabb());
    }

    private static Map<Item, Integer> dropCounts(ServerLevel level, Region region) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (ItemEntity entity : dropsIn(level, region)) {
            counts.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        }
        return counts;
    }

    private static boolean allIdle(ServerLevel level, Fixture fixture) {
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null || be.getPhase() != Phase.IDLE) {
                return false;
            }
        }
        return true;
    }

    private static boolean allSpeedsZero(ServerLevel level, Fixture fixture) {
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null || Math.abs(be.getSpeed()) > 1e-4f) {
                return false;
            }
        }
        return true;
    }

    private static boolean poweredReady(ServerLevel level, Fixture fixture) {
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null || Math.abs(be.getSpeed()) <= 1e-4f) {
                return false;
            }
        }
        for (BlockPos pos : motorPositions(fixture.base)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof CreativeMotorBlockEntity motor)
                    || motor.generatedSpeed == null
                    || Math.abs(motor.getGeneratedSpeed()) <= 1e-4f) {
                return false;
            }
        }
        return true;
    }

    /** 超时/异常时只读记录真实动力与拓扑，供核对 fixture 而不是猜。 */
    private static void logFixtureDiagnostics(ServerLevel level, Fixture fixture) {
        CraftingGrid grid = CraftingTerminalNetwork.findCraftingGrid(level, fixture.terminal);
        AvaritiaSingularityStressMod.LOGGER.info(
                "[crafting-terminal-selftest] DIAG base={} grid={} connectors={} crafters={}",
                fixture.base, grid == null ? "null" : grid.crafters().size() + "/" + grid.coords(),
                CraftingTerminalNetwork.findConnectors(level, fixture.terminal).size(),
                fixture.crafters.size());
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            if (be == null) {
                AvaritiaSingularityStressMod.LOGGER.info(
                        "[crafting-terminal-selftest] DIAG crafter {} BE=null state={}",
                        pos, level.getBlockState(pos));
                continue;
            }
            long chunk = ChunkPos.asLong(pos);
            AvaritiaSingularityStressMod.LOGGER.info(
                    "[crafting-terminal-selftest] DIAG crafter {} phase={} speed={} theoretical={} "
                            + "overstressed={} stressApplied={} covered={} job={} tick={} entities={} state={}",
                    pos, be.getPhase(), be.getSpeed(), be.getTheoreticalSpeed(), be.isOverStressed(),
                    be.calculateStressApplied(), be.isCovered(), be.hasTerminalJob(),
                    level.getChunkSource().isPositionTicking(chunk), level.areEntitiesLoaded(chunk),
                    be.getBlockState());
        }
        for (BlockPos pos : motorPositions(fixture.base)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CreativeMotorBlockEntity motor) {
                AvaritiaSingularityStressMod.LOGGER.info(
                        "[crafting-terminal-selftest] DIAG motor {} generated={} speed={} theoretical={} "
                                + "overstressed={} state={}",
                        pos, motor.getGeneratedSpeed(), motor.getSpeed(), motor.getTheoreticalSpeed(),
                        motor.isOverStressed(), motor.getBlockState());
            } else {
                AvaritiaSingularityStressMod.LOGGER.info(
                        "[crafting-terminal-selftest] DIAG motor {} BE={} state={}",
                        pos, be == null ? "null" : be.getClass().getName(), level.getBlockState(pos));
            }
        }
        AvaritiaSingularityStressMod.LOGGER.info(
                "[crafting-terminal-selftest] DIAG cog {} state={}",
                fixture.base.west(), level.getBlockState(fixture.base.west()));
    }

    private static List<BlockPos> motorPositions(BlockPos base) {
        return List.of(base.west().north());
    }

    private static boolean coversUnchanged(ServerLevel level, Fixture fixture, Snapshot before) {
        for (BlockPos pos : fixture.crafters) {
            ExtremeMechanicalCrafterBlockEntity be = crafter(level, pos);
            CrafterSnapshot old = before == null ? null : before.crafters.get(pos);
            if (be == null || old == null || be.isCovered() != old.covered) {
                return false;
            }
        }
        return true;
    }

    private static ExtremeMechanicalCrafterBlockEntity crafter(ServerLevel level, BlockPos pos) {
        return ExtremeCrafterHelper.getCrafter(level, pos);
    }

    private static ExtremeCraftingTerminalBlockEntity terminal(ServerLevel level, Fixture fixture) {
        BlockEntity be = level.getBlockEntity(fixture.terminal);
        if (!(be instanceof ExtremeCraftingTerminalBlockEntity terminal)) {
            throw new IllegalStateException("终端方块实体缺失: " + fixture.terminal);
        }
        return terminal;
    }

    private static IItemHandler itemHandler(ServerLevel level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    private static boolean gridCoord(CraftingGrid grid, BlockPos pos, int x, int y) {
        Pair<Integer, Integer> coord = grid.coords().get(pos);
        return coord != null && coord.getKey() == x && coord.getValue() == y;
    }

    private static void forceChunks(ServerLevel level, Region region, boolean forced) {
        int minChunkX = region.min.getX() >> 4;
        int maxChunkX = region.max.getX() >> 4;
        int minChunkZ = region.min.getZ() >> 4;
        int maxChunkZ = region.max.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.setChunkForced(chunkX, chunkZ, forced);
            }
        }
    }

    private static void clearRegion(ServerLevel level, Region region) {
        level.getChunk(region.min);
        level.getChunk(region.max);
        for (ItemEntity entity : dropsIn(level, region)) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(region.min, region.max)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (ItemEntity entity : dropsIn(level, region)) {
            entity.discard();
        }
    }

    private static boolean produces(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && item != null && stack.is(item);
    }

    private static Item item(String namespace, String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, path);
    }

    private static RecipeHolder<ShapedTableCraftingRecipe> avaritiaHolder(
            ResourceLocation id, ShapedRecipePattern pattern) {
        return new RecipeHolder<>(id,
                new ShapedTableCraftingRecipe(pattern, new ItemStack(Items.DIAMOND), 1, false));
    }

    private static RecipeHolder<ExtremeKineticCraftingRecipe> kineticHolder(
            ResourceLocation id, ShapedRecipePattern pattern) {
        return new RecipeHolder<>(id,
                new ExtremeKineticCraftingRecipe("", CraftingBookCategory.MISC, pattern,
                        new ItemStack(Items.EMERALD), true, true));
    }

    private static void replaceRecipes(ServerLevel level, List<? extends RecipeHolder<?>> holders) {
        level.getRecipeManager().replaceRecipes(new ArrayList<RecipeHolder<?>>(holders));
    }

    private static ExtremeCraftingTerminalBlockEntity.RecipeLayout realLayout(
            ServerLevel level, String namespace, String path) {
        RecipeHolder<?> holder = level.getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(namespace, path))
                .orElse(null);
        return holder == null ? null : ExtremeCraftingTerminalBlockEntity.layoutOf(holder.value());
    }

    private static void seedGrid(ExtremeMechanicalCrafterBlockEntity crafter) {
        GroupedItems items = crafter.getGroupedItems();
        items.grid.clear();
        // 与 patternFull()/2×2 网格坐标一致：A=东北、B=正上、C=正东、D=根。
        items.grid.put(Pair.of(-1, 1), new ItemStack(Items.IRON_INGOT));
        items.grid.put(Pair.of(0, 1), new ItemStack(Items.GOLD_INGOT));
        items.grid.put(Pair.of(-1, 0), new ItemStack(Items.COPPER_INGOT));
        items.grid.put(Pair.of(0, 0), new ItemStack(Items.REDSTONE));
        items.statsReady = false;
    }

    private static ShapedRecipePattern patternFull() {
        return ShapedRecipePattern.of(Map.of(
                'A', Ingredient.of(Items.IRON_INGOT),
                'B', Ingredient.of(Items.GOLD_INGOT),
                'C', Ingredient.of(Items.COPPER_INGOT),
                'D', Ingredient.of(Items.REDSTONE)), "AB", "CD");
    }

    private static ShapedRecipePattern patternAsymmetric() {
        return ShapedRecipePattern.of(Map.of(
                'B', Ingredient.of(Items.GOLD_INGOT),
                'C', Ingredient.of(Items.COPPER_INGOT),
                'D', Ingredient.of(Items.REDSTONE)), " B", "CD");
    }

    private static ShapedRecipePattern patternArrow() {
        return ShapedRecipePattern.of(Map.of(
                'A', Ingredient.of(Items.GOLD_INGOT),
                'B', Ingredient.of(Items.IRON_INGOT)), "A", "B");
    }

    private static Map<Item, Integer> subtractOne(Map<Item, Integer> source) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        source.forEach((item, count) -> result.put(item, count - 1));
        return result;
    }

    /**
     * 仅测试用：模拟抽取成功、实际抽取前两笔后第三笔拒绝。
     * insertItem 负责 refundAll 的逆序退回，保证物品计数不丢。
     */
    private static final class ExtractFailingHandler implements IItemHandler {

        private final List<ItemStack> stacks = new ArrayList<>();
        private int actualExtracts;

        private void reset(List<Item> items) {
            stacks.clear();
            for (Item item : items) {
                stacks.add(new ItemStack(item));
            }
            actualExtracts = 0;
        }

        @Override
        public int getSlots() {
            return stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < stacks.size() ? stacks.get(slot).copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= stacks.size()) {
                return stack;
            }
            ItemStack current = stacks.get(slot);
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack)) {
                int space = getSlotLimit(slot) - current.getCount();
                if (space <= 0) {
                    return stack;
                }
                int moved = Math.min(space, stack.getCount());
                if (!simulate) {
                    if (current.isEmpty()) {
                        stacks.set(slot, stack.copyWithCount(moved));
                    } else {
                        current.grow(moved);
                    }
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(moved);
                return remainder;
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= stacks.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = stacks.get(slot);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int count = Math.min(amount, current.getCount());
            if (simulate) {
                return current.copyWithCount(count);
            }
            if (actualExtracts >= 2) {
                return ItemStack.EMPTY;
            }
            actualExtracts++;
            ItemStack extracted = current.copyWithCount(1);
            current.shrink(1);
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < stacks.size();
        }
    }

    // ------------------------------------------------------------------
    // 数据结构
    // ------------------------------------------------------------------

    private static final class Fixture {
        final BlockPos base;
        final BlockPos root;
        final BlockPos above;
        final BlockPos east;
        final BlockPos ne;
        final BlockPos terminal;
        final BlockPos storage;
        final BlockPos output;
        final List<BlockPos> crafters;
        final Map<BlockPos, Pointing> pointings;
        final Region region;

        Fixture(BlockPos base, BlockPos root, BlockPos above, BlockPos east, BlockPos ne,
                BlockPos terminal, BlockPos storage, BlockPos output,
                List<BlockPos> crafters, Map<BlockPos, Pointing> pointings, Region region) {
            this.base = base;
            this.root = root;
            this.above = above;
            this.east = east;
            this.ne = ne;
            this.terminal = terminal;
            this.storage = storage;
            this.output = output;
            this.crafters = crafters;
            this.pointings = pointings;
            this.region = region;
        }
    }

    private static final class Region {
        final BlockPos min;
        final BlockPos max;

        Region(BlockPos base) {
            this.min = base.offset(-5, -4, -5);
            this.max = base.offset(7, 5, 7);
        }

        AABB aabb() {
            return new AABB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
    }

    private static final class Snapshot {
        final Map<BlockPos, CrafterSnapshot> crafters;
        final Map<Item, Integer> storage;
        final int drops;

        Snapshot(Map<BlockPos, CrafterSnapshot> crafters, Map<Item, Integer> storage, int drops) {
            this.crafters = crafters;
            this.storage = storage;
            this.drops = drops;
        }

        boolean sameAs(Snapshot other) {
            if (other == null || drops != other.drops || !storage.equals(other.storage)) {
                return false;
            }
            if (!crafters.keySet().equals(other.crafters.keySet())) {
                return false;
            }
            for (Map.Entry<BlockPos, CrafterSnapshot> entry : crafters.entrySet()) {
                if (!entry.getValue().sameAs(other.crafters.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class CrafterSnapshot {
        final Phase phase;
        final boolean covered;
        final ItemStack slot;
        final Map<Pair<Integer, Integer>, ItemStack> grid;

        CrafterSnapshot(Phase phase, boolean covered, ItemStack slot,
                        Map<Pair<Integer, Integer>, ItemStack> grid) {
            this.phase = phase;
            this.covered = covered;
            this.slot = slot;
            this.grid = grid;
        }

        boolean sameAs(CrafterSnapshot other) {
            if (other == null || phase != other.phase || covered != other.covered) {
                return false;
            }
            if (!sameStack(slot, other.slot)) {
                return false;
            }
            if (!grid.keySet().equals(other.grid.keySet())) {
                return false;
            }
            for (Map.Entry<Pair<Integer, Integer>, ItemStack> entry : grid.entrySet()) {
                if (!sameStack(entry.getValue(), other.grid.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameComponents(first, second);
    }
}
