package committee.nova.avaritia_singularity_stress.content.kinetics.belt.warp_tunnel;

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
 * 空间折跃隧道的配置菜单（B15 频率版）。
 * <p>
 * 和 B10 的 {@code WirelessConfigMenu} 一样<b>没有槽位、不用 ContainerData</b>：
 * 四面频率与出口过滤都住在方块实体 NBT 里，BE 的 {@code sendData()} 会同步到客户端，
 * 界面直接读客户端 BE 即可（开界面首帧就是真值，也不会被 short 截断）。
 * 玩家改值走 {@link WarpTunnelConfigPacket} 上行。
 */
public class WarpTunnelConfigMenu extends AbstractContainerMenu {

    /** 交互距离上限，与 {@link WarpTunnelConfigPacket#MAX_RANGE} 同源。 */
    public static final double MAX_RANGE = WarpTunnelConfigPacket.MAX_RANGE;

    private final BlockPos targetPos;
    @Nullable
    private final WarpTunnelBlockEntity serverBe;

    /** 客户端构造器：只拿到坐标，之后用 {@link #holder(Level)} 查客户端 BE。 */
    public WarpTunnelConfigMenu(@Nullable MenuType<?> type, int id, Inventory inventory, FriendlyByteBuf buf) {
        super(type, id);
        this.targetPos = buf.readBlockPos();
        this.serverBe = null;
    }

    /** 服务端构造器：直接持有 BE。 */
    public WarpTunnelConfigMenu(@Nullable MenuType<?> type, int id, Inventory inventory, WarpTunnelBlockEntity be) {
        super(type, id);
        this.targetPos = be.getBlockPos();
        this.serverBe = be;
    }

    @Nullable
    public WarpTunnelBlockEntity holder(Level level) {
        if (serverBe != null) {
            return serverBe;
        }
        return level.getBlockEntity(targetPos) instanceof WarpTunnelBlockEntity be ? be : null;
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.canInteractWithBlock(targetPos, MAX_RANGE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
