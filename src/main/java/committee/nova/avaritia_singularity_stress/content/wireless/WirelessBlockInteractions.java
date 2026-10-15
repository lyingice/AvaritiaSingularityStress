package committee.nova.avaritia_singularity_stress.content.wireless;

import com.simibubi.create.AllItems;

import committee.nova.avaritia_singularity_stress.init.registry.ModItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/**
 * 无线方块（带配置界面）的"交互让位"规则。
 * <p>
 * <b>为什么需要它</b>：1.21 的原版交互顺序是
 * {@code 方块 useItemOn → 方块 useWithoutItem → 物品 useOn}
 * （{@code ServerPlayerGameMode.useItemOn}：方块那两个分支一旦 consumesAction() 就直接 return，
 * 根本走不到物品的 {@code useOn}）。而 B10 / B11 是在 {@code useWithoutItem} 里开配置界面的，
 * 于是手持扳手或频率卡右键时，方块先把点击吃掉：
 * <ul>
 *   <li>Create 扳手 → {@code WrenchItem.useOn → IWrenchable.onWrenched} 永不执行 →<b>方块没法旋转朝向</b>（B10 的扳手就只做旋转，见 {@code WirelessStressBridgeBlock}）；</li>
 *   <li>频率卡 → {@code WirelessFrequencyCardItem.useOn} 永不执行 →<b>右键写不进频率、也没有提示</b>。</li>
 * </ul>
 * B9 漏斗没这个问题，因为它的插入路径写在 {@code useItemOn} 里、且空手才开界面，
 * 手持物品时会 PASS 出去交给物品。
 * <p>
 * 所以：<b>手里拿着"要对这个方块做事的工具"时，方块必须返回 PASS 让位</b>。
 * 顺带一提，玩家潜行时原版本来就会跳过方块交互（{@code flag1 = isSecondaryUseActive() && flag}），
 * 这也是为什么"潜行 + 右键"在修复前反而能用。
 */
public final class WirelessBlockInteractions {

    private WirelessBlockInteractions() {
    }

    /**
     * @param player 正在右键的玩家（{@code useWithoutItem} 只会因主手触发，故只看主手）
     * @return true = 手持物品要自己处理这次点击，方块应当返回 PASS 让位
     */
    public static boolean deferToHeldItem(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        // 本模组的频率卡
        if (held.is(ModItems.WIRELESS_FREQUENCY_CARD.get())) {
            return true;
        }
        // Create 自家扳手：WrenchEventHandler 明确跳过它（见该类第 35 行），
        // 只能靠 WrenchItem.useOn，所以必须在这里放行。
        if (AllItems.WRENCH.isIn(held)) {
            return true;
        }
        // 其他模组的扳手：带 c:tools/wrench 标签。这类物品会被 Create 的
        // WrenchEventHandler 提前拦下（它会 cancel 事件并把结果塞回去），这里放行是兜底。
        return held.is(Tags.Items.TOOLS_WRENCH);
    }
}
