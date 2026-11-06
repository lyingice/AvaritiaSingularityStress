package committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import committee.nova.avaritia_singularity_stress.init.registry.ModItems;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;

/**
 * B21：接收端的「档位转速」滚轮。
 * <p>
 * 交互观感直接复用创造马达那一颗（{@link KineticScrollValueBehaviour}：↻/↺ 两行、
 * 按住右键拖出数值面板、松手生效、带 {@code playFeedbackSound}），
 * 只是语义换成「本网的输出转速档」。语义定义见
 * {@link WirelessStressBridgeBlockEntity#getGeneratedSpeed()}。
 *
 * <h2>为什么必须覆写 {@link #bypassesInput(ItemStack)}</h2>
 * {@code ValueSettingsInputHandler.onBlockActivated} 一旦 {@code testHit} 命中 value box，
 * 就会 {@code event.setCanceled(true)} + 返回 {@code InteractionResult.SUCCESS}
 * （{@code ValueSettingsInputHandler.java:32-80}），而 {@code bypassesInput} 的接口默认值是
 * {@code false}（{@code ValueSettingsBehaviour.java:88-90}）。也就是说：
 * <b>不写这个豁免，手持扳手右键会被滚轮抢走 —— 方块再也转不了朝向；
 * 手持频率卡右键也写不进频道。</b>这两件事恰好是
 * {@code WirelessBlockInteractions} 当初费力让位保下来的。
 * <p>
 * 这里沿用同一套判定（扳手 / 本模组频率卡 / 任意 {@code c:tools/wrench}），
 * 保证「拿着工具时方块和物品照旧，空手时滚轮才接管」。
 *
 * <h2>为什么还要覆写 {@link #onShortInteract}</h2>
 * 空手右键命中滚轮时，短按（松开右键）走的是 {@code ValueSettingsClient.tick()} 里的
 * {@code onShortInteract}（{@code ValueSettingsClient.java:101-108}），
 * 按住超过 5 tick 才打开数值面板。若 {@code onShortInteract} 留空，
 * 玩家会发现「空手点滚轮区域没反应、开不了配置面板」。
 * 所以把它转交给方块实体原本的开面板逻辑。
 */
public class WirelessStressBridgeGearBehaviour extends KineticScrollValueBehaviour {

    private final WirelessStressBridgeBlockEntity bridge;

    public WirelessStressBridgeGearBehaviour(Component label, WirelessStressBridgeBlockEntity be,
                                             ValueBoxTransform slot) {
        super(label, be, slot);
        this.bridge = be;
    }

    /**
     * 手持「要对这个方块做事的工具」时让位（判定与 {@code WirelessBlockInteractions#deferToHeldItem} 同源）。
     */
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
     * <b>只有空手时滚轮才接管右键</b>（实机痛点修复）。
     * <p>
     * {@code ValueSettingsInputHandler.onBlockActivated} 除了 clipboard 之外<b>不检查手持物品</b>
     * （{@code ValueSettingsInputHandler.java:34-80}），只要 {@code testHit} 命中 value box 就
     * cancel 掉右键并返回 SUCCESS。于是出现实测症状：
     * <b>手持传动杆右键接收端想接轴，结果事件被滚轮吃掉、轴没接上，短按还顺手打开了配置界面。</b>
     * <p>
     * {@code bypassesInput} 只能枚举已知工具（扳手 / 频率卡），挡不住「传动杆 / 齿轮 / 机壳 / 任何
     * 其它 mod 的方块物品」这类无穷无尽的组合；而 {@link #mayInteract(Player)} 是
     * {@code ValueSettingsInputHandler} 在 {@code bypassesInput} 之后、{@code testHit} 之前调用的总闸
     * （{@code ValueSettingsInputHandler.java:52}；{@code bypassesInput} 在 :50），
     * 在这里一次性收口最干净：
     * <ul>
     *   <li>空手 → 滚轮接管（短按开面板 / 按住拖数值）；</li>
     *   <li>手持任何物品 → 滚轮完全不参与，右键原样交给方块与物品
     *       （接轴、放方块、扳手旋转、频率卡写入都恢复正常）。</li>
     * </ul>
     * 代价：手持物品时改不了档位，得先空手。这是为了保住「建造优先」——
     * 毕竟这个方块的主要用法是把轴接上去。
     */
    @Override
    public boolean mayInteract(Player player) {
        return player.getMainHandItem().isEmpty();
    }

    /**
     * 短按 = 打开配置面板（与方块 useWithoutItem 同一个入口）。
     * <p>
     * 这里也要判一次空手：{@link #mayInteract(Player)} 只决定 Create 的输入处理器
     * 「要不要接管这次右键」，而 {@code ValueSettingsClient} 在松开右键时调
     * {@code onShortInteract} 之前<b>并不复查</b> {@code mayInteract}
     * （{@code ValueSettingsClient.java:101-108}）。少了这道判断，
     * 手持传动杆短按仍会弹出配置界面 —— 也就是 T19 复现的路径之一。
     */
    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return;
        }
        bridge.openConfigMenu(player);
    }
}
