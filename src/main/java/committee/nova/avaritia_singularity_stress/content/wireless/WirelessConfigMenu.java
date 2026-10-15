package committee.nova.avaritia_singularity_stress.content.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 三种无线方块（B9 / B10 / B11）共用的配置界面。
 * <p>
 * <b>本菜单没有槽位、也不用 ContainerData</b>：
 * <ul>
 *   <li><b>不加玩家背包</b>：面板贴图与 I3 频率卡同款（176×166）——y≥84 的区域画的是
 *       六组预设槽图案，加背包槽会把玩家物品图标糊在预设图案上，
 *       而且本界面不需要搬运物品（频率卡 GUI 同样一个槽都没有）；</li>
 *   <li><b>不走 ContainerData</b>：频率卡是<b>物品</b>，数值只能靠 ContainerData 传；
 *       本菜单背后是<b>方块实体</b>，BE 的 NBT 本来就同步到客户端
 *       （见 {@link IFrequencyBindable#writeFrequency} 的 clientPacket 分支），
 *       界面直接读客户端 BE 即可：
 *       <ol>
 *         <li>开界面首帧就是真值 —— 不会先画 0/空再跳成真实频率（"闪一下"的成因）；</li>
 *         <li>没有 ContainerData 被压成 SHORT 的截断问题
 *             （{@code ClientboundContainerSetDataPacket} 只写 short，
 *             大于 32767 的数量上限会被截成负数，见旧版 GUI 的数值乱码）。</li>
 *       </ol>
 *       玩家改值仍然走 {@link WirelessConfigPacket} 上行，服务端写回 BE 后
 *       由 BE 自己的 {@code sendData()} 推给所有正在看的客户端。</li>
 * </ul>
 */
public class WirelessConfigMenu extends AbstractContainerMenu {

    /** 目标方块坐标（服务端写包 / 客户端读包各一次）。 */
    private final BlockPos targetPos;

    /** 服务端构造时直接持有 BE；客户端为 null，由 {@link #holder(Level)} 按坐标查。 */
    @Nullable
    private final IWirelessConfigHolder serverHolder;

    /**
     * 客户端构造器：{@code IMenuTypeExtension.create(...)} 调用。
     * <p>
     * 包体里只有坐标 —— 由 {@code player.openMenu(provider, pos)} 的
     * {@code writeClientSideData} 自动带过来；其余字段客户端从坐标处的 BE 读。
     */
    public WirelessConfigMenu(MenuType<?> type, int id, Inventory inventory, FriendlyByteBuf buf) {
        this(type, id, inventory, buf.readBlockPos(), null);
    }

    /** 服务端构造器：直接用 BE 作为配置读写入口。 */
    public WirelessConfigMenu(MenuType<?> type, int id, Inventory inventory, IWirelessConfigHolder holder) {
        this(type, id, inventory, holder.getBlockPos(), holder);
    }

    private WirelessConfigMenu(MenuType<?> type, int id, Inventory inventory, BlockPos targetPos,
            @Nullable IWirelessConfigHolder serverHolder) {
        super(type, id);
        this.targetPos = targetPos;
        this.serverHolder = serverHolder;
    }

    /** @return 目标方块坐标。 */
    public BlockPos getTargetPos() {
        return targetPos;
    }

    /**
     * 取配置读写入口。
     * <p>
     * 服务端返回打开界面时那台 BE；客户端按坐标在客户端世界里查 —— 客户端 BE 的值由
     * block entity update 包同步（任何字段变化 BE 都会 {@code sendData()}），
     * 所以这里读到的一直是最新值。
     *
     * @param level 客户端世界（{@code minecraft.level}）；服务端可传 {@code null}
     * @return 取不到时返回 {@code null}（例如区块正在卸载），调用方应保持界面现状，
     *         <b>不要</b>用 0 覆盖已有输入
     */
    @Nullable
    public IWirelessConfigHolder holder(@Nullable Level level) {
        if (serverHolder != null) {
            return serverHolder;
        }
        if (level == null) {
            return null;
        }
        return IWirelessConfigHolder.of(level.getBlockEntity(targetPos));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 本菜单没有可搬运的机器槽
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // 距离与权限校验在 WirelessConfigPacket.handle 里做（写入时），这里不额外关界面
        return true;
    }
}
