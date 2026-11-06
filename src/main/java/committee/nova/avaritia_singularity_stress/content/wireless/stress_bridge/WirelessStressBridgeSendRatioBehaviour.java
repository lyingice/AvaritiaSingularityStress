package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import committee.nova.avaritia_singularity_stress.init.registry.ModItems;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

/**
 * B22：输出端（sender）的「占用源网空闲百分比」滚轮，取值 0~100。
 * <p>
 * <b>为什么是百分比、不是 SU 也不是 RPM</b>（三者都试过的结论）：
 * <ul>
 *   <li><b>不可能是 RPM</b>：输出端挂在源网上，转速由 {@code getTheoreticalSpeed()} 决定，玩家改不了；</li>
 *   <li><b>不适合纯 SU</b>：源网空闲容量每 tick 都在变，写死一个绝对值会很快过时
 *       （原来 GUI 里的「送出上限」就是这个毛病，玩家得反复回来重调）；</li>
 *   <li><b>百分比</b>：跟随源网自动伸缩，"始终用掉当前空闲的 80%" 这种意图可以一次设定长期有效。</li>
 * </ul>
 * 最终送出量 = {@code min(源网空闲 × 百分比, GUI 里的绝对上限)}，两个来源取小，
 * 见 {@code WirelessStressBridgeBlockEntity#tickSender()}。
 * <p>
 * 交互侧的两个覆写与 {@link WirelessStressBridgeGearBehaviour} 同源（空手才接管、短按开面板），
 * 原因见那边的注释（含 T19 那个实机痛点的完整来龙去脉）。
 */
public class WirelessStressBridgeSendRatioBehaviour extends ScrollValueBehaviour {

    private final WirelessStressBridgeBlockEntity bridge;

    public WirelessStressBridgeSendRatioBehaviour(Component label, WirelessStressBridgeBlockEntity be,
                                                  ValueBoxTransform slot) {
        super(label, be, slot);
        this.bridge = be;
        withFormatter(value -> value + "%");
    }

    /** 只有空手时滚轮才接管右键；手持任何物品都要能正常接轴/放方块（T19）。 */
    @Override
    public boolean mayInteract(Player player) {
        return player.getMainHandItem().isEmpty();
    }

    /** 第二道保险：已知工具（扳手 / 频率卡 / 任意 c:tools/wrench）让位。 */
    @Override
    public boolean bypassesInput(ItemStack mainhandItem) {
        if (mainhandItem.isEmpty()) {
            return false;
        }
        if (mainhandItem.is(ModItems.WIRELESS_FREQUENCY_CARD.get())) {
            return true;
        }
        if (AllItems.WRENCH.isIn(mainhandItem)) {
            return true;
        }
        return mainhandItem.is(Tags.Items.TOOLS_WRENCH);
    }

    /**
     * 短按 = 打开配置面板（与方块 useWithoutItem 同一个入口）。
     * <p>
     * 同样先判空手：{@code ValueSettingsClient} 松手时调 {@code onShortInteract} 前不复查
     * {@code mayInteract}（{@code ValueSettingsClient.java:101-108}），不判就会复现 T19。
     */
    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        bridge.openConfigMenu(player);
    }
}
