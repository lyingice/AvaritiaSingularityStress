package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

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
 * <b>唯一的输入输出面（接网面）</b>：{@link #hasShaftTowards} 只在 {@code FACING} 的反面返回 true，
 * 也就是本方块接入动能网的唯一入口/出口；其余五面不连网。
 * 取反面而不是正面，是因为 Create 的放置推导（{@code DirectionalKineticBlock.getPreferredFacing}）
 * 约定 {@code FACING} 背对相邻的轴，于是"贴着轴放就自动接上"由 Create 自带逻辑保证，
 * 本类不需要覆写放置（{@code EncasedFanBlock} 是同款写法）。
 * 方块模型 {@code block/wireless_stress_bridge} 的 port 贴图也只在对应的那一面上。
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
     * 唯一的输入输出面：只有 {@code FACING} 的反面能接轴/齿轮，其余五面一律不连。
     * <p>
     * Create 的 {@code RotationPropagator.getRotationSpeedModifier} 要求相邻两侧的
     * {@code hasShaftTowards} 同时为真才会建立轴连接，所以本方法返回 true 的那一个面
     * 就是本机接入动能网的唯一入口/出口（输出端从这里扣源网，接收端从这里给本网加容量）。
     * <p>
     * 为什么是反面而不是正面：见类注释（Create 的放置推导把 {@code FACING} 背对相邻轴）。
     */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
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

    /** 空手右键 → 打开无线配置界面；手持扳手/频率卡时让位给物品（见 {@link WirelessBlockInteractions}）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (WirelessBlockInteractions.deferToHeldItem(player)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof WirelessStressBridgeBlockEntity bridge) {
            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new WirelessConfigMenu(
                                    ModMenus.WIRELESS_CONFIG.get(), id, inventory, bridge),
                            // 用方块自己的描述 id，中英文案由 lang 提供
                            net.minecraft.network.chat.Component.translatable(getDescriptionId())),
                    buffer -> buffer.writeBlockPos(pos));
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
