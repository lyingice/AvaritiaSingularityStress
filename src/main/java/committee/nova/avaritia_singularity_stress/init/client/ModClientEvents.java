package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.foundation.block.connected.CTModel;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.client.renderer.ModRenderers;
import committee.nova.avaritia_singularity_stress.init.client.screen.FrequencyCardScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.KineticNeutronCollectorScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.KineticNeutronCompressorScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.WirelessConfigScreen;
import committee.nova.avaritia_singularity_stress.init.client.visualizer.ModVisualizers;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.Set;

@EventBusSubscriber(
        modid = AvaritiaSingularityStressMod.MODID,
        value = Dist.CLIENT
)
public final class ModClientEvents {

    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModPartialModels.init();
            ModSpriteShifts.init();
            ModVisualizers.register();
        });
    }

    /**
     * 把终焉合成器家族的 blockstate 变体模型包上 CTModel,让连接纹理生效。
     * 等效于原版 Registrate 的 onRegister(connectedTextures(...))。
     * <p>
     * 关键:烘焙结果表的 key 是 {@code ModelResourceLocation(方块注册名, 状态属性串)},
     * 例如 {@code (avaritia_singularity_stress:extreme_mechanical_crafter, "facing=north,pointing=down")}。
     * 模型文件路径(block/xxx/block)只是被 blockstate 引用的依赖,
     * 不会作为顶层 key 出现 —— 按它匹配会恒不命中,CT 就永远装不上。
     * 16 个 facing/pointing 变体共用同一个方块注册名,因此每个方块一条判断即覆盖全部。
     * <p>
     * 物品模型与方块同名,其 key 的 variant 是 "inventory",需要排除。
     * lid / arrow / belt / belt_animated / cogwheel 这些 partial model 不在方块状态表中,自然不受影响。
     * <p>
     * 两种终焉方块共用同一个 behaviour(内部按方块类型分派纹理组)。
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ExtremeCTBehaviour behaviour = new ExtremeCTBehaviour();
        Set<ResourceLocation> crafterBlockIds = Set.of(
                ResourceLocation.fromNamespaceAndPath(
                        AvaritiaSingularityStressMod.MODID, "extreme_mechanical_crafter"),
                ResourceLocation.fromNamespaceAndPath(
                        AvaritiaSingularityStressMod.MODID, "extreme_smithing_crafter"));

        int[] wrapped = {0};
        event.getModels().replaceAll((location, model) -> {
            if (crafterBlockIds.contains(location.id())
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
                wrapped[0]++;
                return new CTModel(model, behaviour);
            }
            return model;
        });

        AvaritiaSingularityStressMod.LOGGER.info(
                "[extreme_crafter] CTModel wrapped on {} blockstate models", wrapped[0]);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ModRenderers.register(event);
    }

    /** 注册机器 GUI 的屏幕。 */
    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.KINETIC_NEUTRON_COLLECTOR.get(), KineticNeutronCollectorScreen::new);
        event.register(ModMenus.KINETIC_NEUTRON_COMPRESSOR.get(), KineticNeutronCompressorScreen::new);
        event.register(ModMenus.WIRELESS_FREQUENCY_CARD.get(), FrequencyCardScreen::new);
        event.register(ModMenus.WIRELESS_CONFIG.get(), WirelessConfigScreen::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // Flywheel discovers PartialModel instances from PartialModel.ALL.
    }
}
