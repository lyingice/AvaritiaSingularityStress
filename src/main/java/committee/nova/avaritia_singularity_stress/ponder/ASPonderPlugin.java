package committee.nova.avaritia_singularity_stress.ponder;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder 插件的唯一入口。
 * <p>
 * 三条容易踩的规则：
 * <ul>
 *   <li>{@link #getModId()} 必须返回本模组 modid —— Ponder 用它拼 lang key，
 *       也用它给剧本找 {@code assets/<modid>/ponder/<剧本id>.nbt}。返回别家的 id 就全找不到。</li>
 *   <li>下面三个 register 方法会在 {@code PonderIndex.reload()} 时<b>被重复调用</b>，
 *       所以方法体里不许有副作用（别注册方块、别读世界、别发包）。</li>
 *   <li>本类只允许出现在客户端路径上：它 import 的全是 Ponder 客户端类。</li>
 * </ul>
 */
public final class ASPonderPlugin implements PonderPlugin {

    public static final ASPonderPlugin INSTANCE = new ASPonderPlugin();

    private ASPonderPlugin() {
    }

    @Override
    public String getModId() {
        return AvaritiaSingularityStressMod.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        AvaritiaPonderScenes.registerScenes(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        AvaritiaPonderScenes.registerTags(helper);
    }
}
