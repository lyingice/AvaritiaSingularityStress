package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import committee.nova.avaritia_singularity_stress.content.wireless.WirelessBlockInteractions;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * B10 无线应力传输器的方块（方案 C：两个注册名、一个方块类）。
 * <p>
 * 两个注册名（{@code wireless_stress_bridge_sender} / {@code _receiver}）共用本类，
 * 差异只落在 {@link #OUTPUT} 属性上；行为差异在 BE 里由
 * {@link WirelessStressBridgeBlockEntity#isOutputSide()} 分派。
 * <p>
 * 基类选 {@link DirectionalKineticBlock}：blockstate 需要 6 向 {@code facing}，
 * 而它正好提供 {@code FACING}，并自带放置朝向 / rotate / mirror。
 * <p>
 * <b>唯一的输入输出面（接网面）就是 {@code FACING} 那一面</b>：{@link #hasShaftTowards} 只在
 * {@code FACING} 返回 true，其余五面不连网。
 * <p>
 * <b>为什么是 FACING 而不是它的反面</b>（这条是踩过坑的，改之前务必看完）：
 * <ul>
 *   <li>本方块的两个方块模型 {@code block/wireless_stress_bridge/block.json} 与
 *       {@code block_vertical.json} 是创造马达模型 {@code create:block/creative_motor/block.json} 的
 *       <b>逐元素复制</b>（{@code from}/{@code to}/{@code faces}/{@code uv} 全部相同，只换了贴图），
 *       因此模型的「端口面」位置与创造马达完全一致。</li>
 *   <li>创造马达把端口面放在 {@code FACING} 那一面，三处证据互相印证：
 *       {@code CreativeMotorBlock.java:38-41}（{@code hasShaftTowards} 返回 {@code face == FACING}）、
 *       {@code CreativeMotorRenderer.java:18-20}（BER 用 {@code CachedBuffers.partialFacing(SHAFT_HALF, state)}，
 *       按 {@code FACING} 摆放半轴）、
 *       {@code AllBlockEntityTypes.java:263}（Flywheel 用
 *       {@code OrientedRotatingVisual.of(SHAFT_HALF)}，而 {@code OrientedRotatingVisual.java:43-49}
 *       写死 {@code to = FACING}）。</li>
 *   <li>所以本方块也必须让 {@code FACING} = 端口面 = 接网面，三者才对得上；
 *       若把接网面写成 {@code FACING} 的反面，轴会被画到模型背板上，端口面反而空着
 *       （实机症状：正对着的那个"看起来是端口"的面没有轴）。</li>
 * </ul>
 * <b>代价：必须覆写放置</b>。{@link DirectionalKineticBlock} 的默认放置是
 * {@code FACING = getPreferredFacing(context).getOpposite()}（{@code DirectionalKineticBlock.java:57}），
 * 即 FACING 背对相邻的轴 —— 与本方块需要的方向正好相反。所以这里照抄创造马达的放置
 * （{@code CreativeMotorBlock.java:31-38}）：贴着轴放时 {@code FACING = preferred}（朝向那根轴）。
 * <p>
 * <b>扳手做的是旋转</b>：本类<b>不覆写</b> {@link IWrenchable#onWrenched}，右键扳手因此走 Create 默认实现
 * （绕点击面旋转 {@code FACING}），角色不会改变 —— 输出端还是输出端。
 * 角色由<b>放置的是哪一个注册名</b>决定，运行时不可变；要换角色请拆掉重放。
 * <p>
 * <b>{@link #POWERED} 说明</b>：已定稿的 4 个 blockstate JSON 的变体键里含
 * {@code powered=false}，所以本方块<b>必须</b>声明这个属性，否则 12 个状态全都匹配不上、
 * 方块会渲染成缺失模型。而 JSON 里只有 {@code powered=false} 的变体，
 * 因此这里默认 false 并且<b>永不置 true</b>。
 */
public class WirelessStressBridgeBlock extends DirectionalKineticBlock
        implements IBE<WirelessStressBridgeBlockEntity>, IWrenchable {

    /** 输出端（Sender）标记：true = 扣源网并把应力送去对端，false = 接收端。 */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    /** 仅为匹配 blockstate JSON 而存在的红石属性，恒为 false（见类注释）。 */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** 该注册名的固定角色；与状态里的 {@link #OUTPUT} 始终一致（不再有运行时切换，见类注释）。 */
    private final boolean outputSide;

    public WirelessStressBridgeBlock(Properties properties, boolean outputSide) {
        super(properties);
        this.outputSide = outputSide;
        registerDefaultState(defaultBlockState()
                .setValue(OUTPUT, outputSide)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(OUTPUT, POWERED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /**
     * 放置朝向：照抄创造马达（{@code CreativeMotorBlock.java:31-38}）。
     * <p>
     * 与基类默认实现的唯一区别是"贴着轴放"那一支：基类写
     * {@code FACING = getPreferredFacing(context).getOpposite()}（{@code DirectionalKineticBlock.java:57}），
     * 让 FACING 背对相邻的轴；这里写 {@code FACING = preferred}，让 FACING 正对那根轴。
     * 原因是本方块的端口面（模型）与接网面（{@link #hasShaftTowards}）都在 FACING 上，
     * 只有朝向那根轴，玩家看到的端口面才是真正能接网的面。
     * <p>
     * 没有相邻轴 / 玩家潜行时退回 {@code super}，此时 FACING = 看向方向的反面
     * （{@code DirectionalKineticBlock.java:53-55}），与创造马达完全一致。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        if ((context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) || preferred == null) {
            return super.getStateForPlacement(context);
        }
        return defaultBlockState().setValue(FACING, preferred);
    }

    /**
     * 碰撞/遮挡形状跟着模型走，不用基类默认的满块。
     * <p>
     * 模型 {@code block/wireless_stress_bridge/block} 是创造马达模型的复制件（最外层只到 1~15，
     * 主体 3~13，中间是空的），而 Create 的 {@code CreativeMotorBlock} 也正好覆写了形状
     * 返回 {@link AllShapes#MOTOR_BLOCK}（{@code CreativeMotorBlock.java:26-29}），
     * 该形状是 {@code shape(3,0,3,13,14,13).forDirectional()}（{@code AllShapes.java:35}）。
     * 本类原先没覆写，走的是 {@code Shapes.block()} 满块，于是：
     * <ul>
     *   <li>玩家能站在空心的模型顶上悬空（碰撞比外观大一圈）；</li>
     *   <li>{@code isCollisionShapeFullBlock} 为真，相邻方块按"被满块遮挡"处理，
     *       贴地摆放时下方那格地面顶面会被判为可剔除 —— 视觉上就是"地面被挖掉一块"。</li>
     * </ul>
     * 这里对齐创造马达，两处问题一起消掉。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.MOTOR_BLOCK.get(state.getValue(FACING));
    }

    /**
     * 唯一的输入输出面：只有 {@code FACING} 那一面能接轴/齿轮，其余五面一律不连。
     * <p>
     * Create 的 {@code RotationPropagator.getRotationSpeedModifier} 要求相邻两侧的
     * {@code hasShaftTowards} 同时为真才会建立轴连接，所以本方法返回 true 的那一个面
     * 就是本机接入动能网的唯一入口/出口（输出端从这里扣源网，接收端从这里给本网加容量）。
     * <p>
     * 为什么是 FACING（而不是反面）：见类注释——模型的端口面与创造马达同款、在 FACING 上，
     * 而创造马达的 {@code hasShaftTowards} 也返回 {@code face == FACING}（{@code CreativeMotorBlock.java:38-41}）。
     * 两者必须一致，否则 Flywheel 的 {@code OrientedRotatingVisual.of()}（写死 {@code to = FACING}，
     * {@code OrientedRotatingVisual.java:43-49}）会把轴画到没有接网的那一面。
     */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    /**
     * 自写：{@link IBE} 的默认实现会用无参的 {@code getBlockEntityType()}，
     * 而两个注册名的 BE 类型不同，必须按状态挑（见 BE 的 {@code create}）。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WirelessStressBridgeBlockEntity.create(pos, state);
    }

    @Override
    public Class<WirelessStressBridgeBlockEntity> getBlockEntityClass() {
        return WirelessStressBridgeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WirelessStressBridgeBlockEntity> getBlockEntityType() {
        return outputSide
                ? ModBlockEntities.WIRELESS_STRESS_BRIDGE_SENDER.get()
                : ModBlockEntities.WIRELESS_STRESS_BRIDGE_RECEIVER.get();
    }

    /** 放置时把放置者记为频道所有者（私有频道靠它判定配对）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof WirelessStressBridgeBlockEntity bridge) {
            bridge.setOwner(player.getUUID());
        }
    }

    /**
     * 空手右键 → 打开无线配置界面；手持物品时一律让位。
     * <p>
     * 原版流程里 {@code useWithoutItem} 理论上只在空手时被调用，但这里是<b>兜底</b>：
     * T19 实机表现为「手持传动杆右键想去接轴，结果弹出配置界面」，
     * 而这条路径可能在物品 {@code useOn} 返回 PASS 之后被某些调用方接上。
     * 与其依赖调用顺序，不如在入口直接判一次空手 —— 代价只有一次 {@code isEmpty()}，
     * 收益是「建造优先」这条规则不再取决于别人的实现细节。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            // 手持任何物品都不开界面：接轴 / 放方块 / 扳手 / 频率卡 各走各的
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof WirelessStressBridgeBlockEntity bridge) {
            bridge.openConfigMenu(player);
        }
        return InteractionResult.SUCCESS;
    }

    // ===================== 扳手：只旋转，不切角色 =====================
    //
    // 【已移除】原 onWrenched 覆写会把方块在"输出端 / 接收端"两个注册名之间整体替换，
    // 抢走了扳手的旋转功能（与"切换方块朝向"直接冲突），而且换方块会重建 BE、必须手工搬 NBT。
    // 现在本类不再覆写 onWrenched，右键扳手走 IWrenchable 的默认实现：
    //   getRotatedBlockState(...) -> KineticBlockEntity.switchToBlockState(...)
    // 即绕点击面旋转 FACING，角色（输出端/接收端）保持不变；BE 不被换掉，
    // 频率 / 可见性 / owner / 两个数量上限原地保留。
    // 对应的 KINETIC_STATE_KEYS 也已在 BE 里一并删除。
}
