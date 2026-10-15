package committee.nova.avaritia_singularity_stress.content.wireless.item_funnel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;

import committee.nova.avaritia_singularity_stress.content.wireless.WirelessBlockInteractions;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B9 无线物品传输漏斗的方块。
 * <p>
 * 基类选 {@link FunnelBlock}（已批准的方案）：朝向、扳手循环 EXTRACTING、红石 POWERED
 * 全部直接从 Create 黄铜漏斗继承，模型与 blockstate 也复用黄铜漏斗的几何。
 * <p>
 * <b>必须覆写 {@link #newBlockEntity}</b>：{@code AbstractFunnelBlock} 实现的是
 * {@code IBE<FunnelBlockEntity>}，其 {@code getBlockEntityType()} 返回 Create 自己的漏斗类型，
 * 默认的 {@code IBE.newBlockEntity} 会因此造出 Create 的 {@code FunnelBlockEntity}。
 * 由于我们的 BE 不继承 {@code FunnelBlockEntity}，{@code getBlockEntityClass()}
 * 也无法覆写成我们的类（泛型被基类钉死），所以：
 * <ul>
 *   <li>{@code newBlockEntity} 必须自写；</li>
 *   <li>{@code withBlockEntityDo()} / {@code AbstractFunnelBlock.tryInsert()} 一律不可用，
 *       交互插入路径自写在 {@link #useItemOn}。</li>
 * </ul>
 * {@code getTicker} 不用覆写：它按 {@code getBlockEntityClass()}（= Create 的漏斗 BE，也是
 * SmartBlockEntity）判定后返回 {@code SmartBlockEntityTicker}，我们的 BE 同样是 SmartBlockEntity，能正常 tick。
 */
public class WirelessItemFunnelBlock extends FunnelBlock {

    public WirelessItemFunnelBlock(Properties properties) {
        super(properties);
    }

    /**
     * {@code FunnelBlock} 是抽象类，唯一的抽象方法。
     * <p>
     * <b>本类不再使用它的返回值</b>，见 {@link #updateShape}。之所以仍然照抄
     * {@code BrassFunnelBlock} 的实现，只是因为抽象方法必须给出一个合法返回值；
     * 另有一个隐患要记住：若将来把本方块做成 {@code FunnelItem}（Create 的漏斗物品），
     * 放置路径 {@code FunnelItem.getPlacementState} 会用这个返回值直接放到传送带上，
     * 那时"放置即变黄铜带式漏斗"会绕开 {@link #updateShape}，必须一并处理。
     */
    @Override
    public BlockState getEquivalentBeltFunnel(BlockGetter world, BlockPos pos, BlockState state) {
        Direction facing = getFacing(state);
        return AllBlocks.BRASS_BELT_FUNNEL.getDefaultState()
                .setValue(BeltFunnelBlock.HORIZONTAL_FACING, facing)
                .setValue(POWERED, state.getValue(POWERED));
    }

    /**
     * 与 Create 黄铜漏斗的<b>一处有意差异</b>：不做"传送带退化"。
     * <p>
     * {@code FunnelBlock.updateShape} 原本会在"朝向水平 + 下方是传送带"时把整个方块换成
     * {@code create:brass_belt_funnel}。对这个方块来说是双重损失：
     * <ul>
     *   <li>贴图变成 Create 的黄铜带式漏斗（本模组的无线贴图全丢）；</li>
     *   <li>方块实体被换成 Create 自己的 {@code FunnelBlockEntity}，
     *       {@link WirelessItemFunnelBlockEntity} 连同已登记的无线频率/频道一起消失。</li>
     * </ul>
     * 所以这里只保留水logged状态更新，其余原样返回。
     * <p>
     * 代价（已确认接受）：在传送带上它不会获得带式漏斗"把物品推上/取下传送带"的专用行为。
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        updateWater(level, state, pos);
        return state;
    }

    /**
     * 造我们自己的方块实体。
     * <p>
     * 不覆写的话，默认实现会走 {@code getBlockEntityType()} → Create 的漏斗类型。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return WirelessItemFunnelBlockEntity.create(pos, state);
    }

    /** 放置时把放置者写进 BE 作为频道所有者（私有频道靠它判定能否配对）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof WirelessItemFunnelBlockEntity funnels) {
            funnels.setOwner(player.getUUID());
        }
    }

    /**
     * 交互：
     * <ul>
     *   <li><b>空手右键</b> → 打开无线配置界面（已批准）；</li>
     *   <li><b>手持扳手/频率卡</b> → 放行给物品（否则开口面上的插入路径会把扳手/卡片当物品塞进漏斗，
     *       而且 Create 的扳手切模式与频率卡写频率都轮不到执行，见 {@link WirelessBlockInteractions}）；</li>
     *   <li><b>手持物品点开口面</b> → 自写的插入路径；</li>
     *   <li>其余情况放行给默认处理。</li>
     * </ul>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (WirelessBlockInteractions.deferToHeldItem(player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof WirelessItemFunnelBlockEntity funnels)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            if (!level.isClientSide) {
                player.openMenu(
                        new SimpleMenuProvider(
                                (id, inventory, p) -> new WirelessConfigMenu(
                                        ModMenus.WIRELESS_CONFIG.get(), id, inventory, funnels),
                                Component.translatable("block.avaritia_singularity_stress.wireless_item_funnel")),
                        buffer -> buffer.writeBlockPos(pos));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 手持物品：只在"开口面"插入，且被红石锁定或处于抽取模式时不接收（与原生漏斗判定一致）
        if (hitResult.getDirection() != AbstractFunnelBlock.getFunnelFacing(state)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(POWERED) || state.getValue(EXTRACTING)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack remainder = funnels.insertHeld(stack.copy());
        if (!ItemStack.matches(remainder, stack) || remainder.getCount() != stack.getCount()) {
            player.setItemInHand(hand, remainder);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
