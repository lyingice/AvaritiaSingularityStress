package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 把本模组的物品挂进 Create 自带的 tooltip 体系。
 *
 * <h2>为什么不用自己拼 Text</h2>
 * Create 的这套东西一次解决三件事，自己写一段 <code>appendHoverText</code> 全都拿不到：
 * <ul>
 *   <li>按 Shift 才展开正文，没按的时候只留一行「按住 Shift 可查看概要」；
 *   <li>文案里的 <code>_文字_</code> 会被渲染成强调色（{@link FontHelper} 负责）；
 *   <li>按物品面板宽度自动折行。</li>
 * </ul>
 *
 * <h2>调用链（三个类，一个都不能少）</h2>
 * <ol>
 *   <li>本类在客户端初始化时把自己塞进 {@code TooltipModifier.REGISTRY}
 *       —— 这正是 Create 官方注册的地方（{@code CreateRegistrate.java:120}）；</li>
 *   <li>{@link ItemDescription.Modifier} 第一次被问到文案时才去读语言键，
 *       键固定为 <b>{@code <物品 descriptionId>.tooltip}</b>（{@code ItemDescription.java:96-101}）；</li>
 *   <li>Create 自己的
 *       {@code ClientEvents.addToItemTooltip}（{@code ClientEvents.java:277-283}）
 *       在 {@code ItemTooltipEvent} 里查这张表并调用。</li>
 * </ol>
 * 所以<b>只写 lang 键是不够的</b>：这张表里没有这个物品，Create 根本不会来看。
 * 反过来，注册了但缺 {@code .summary} 键也不显示 ——
 * {@code ItemDescription.create} 第一行就要求 summary 存在（{@code ItemDescription.java:43-45}）。
 *
 * <h2>为什么放客户端</h2>
 * {@link ItemDescription} 依赖 {@code I18n} 与字体度量，是纯客户端类；
 * 放进 common 会让专用服务端在类加载阶段就炸。
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID, value = Dist.CLIENT)
public final class ModTooltips {

    private ModTooltips() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ModTooltips::register);
    }

    private static void register() {
        // I2 深空延拓之手：结构与 Create 的伸缩机械手（create:extendo_grip）一致
        attach(ModItems.NEUTRON_EXTENDO_GRIP.get());
    }

    /**
     * 给一个物品挂上 Create 的描述。
     * <p>
     * 重复注册会被拒：{@code SimpleRegistry.register} 对已存在的键直接抛
     * {@code IllegalArgumentException}（{@code SimpleRegistry.java:34}），
     * 所以先查一次，避免任何重入路径（资源重载、客户端二次 setup）把启动搞崩。
     */
    private static void attach(Item item) {
        if (TooltipModifier.REGISTRY.get(item) != null) {
            return;
        }
        TooltipModifier.REGISTRY.register(item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }
}
