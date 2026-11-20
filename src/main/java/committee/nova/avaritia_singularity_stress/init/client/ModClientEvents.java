package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.createmod.catnip.data.Couple;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.client.renderer.ModRenderers;
import committee.nova.avaritia_singularity_stress.init.client.screen.FrequencyCardScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.KineticNeutronCollectorScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.KineticNeutronCompressorScreen;
import committee.nova.avaritia_singularity_stress.init.client.screen.WirelessConfigScreen;
import committee.nova.avaritia_singularity_stress.init.client.visualizer.ModVisualizers;
import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
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
            // 包壳齿轮的 CT shift 必须在纹理拼接之前建好（晚了 sprite 取不到）
            initEncasedShifts();
            // B16:视觉注册挪到 loadComplete,原因见下面的 loadComplete 注释。

            // B15 空间折跃隧道必须走 cutoutMipped：Create 的黄铜隧道就是
            // addLayer(() -> RenderType::cutoutMipped) + noOcclusion（见 BuilderTransformers.beltTunnel:352-353）。
            // 少了这一行，贴图里的透明像素会被当作不透明绘制，隧道口就被"糊死"，
            // 挂在口子里的软门帘自然看不见 —— 这就是"外面的遮住了帘子"的真正原因。
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.WARP_TUNNEL.get(), RenderType.cutoutMipped());
        });
    }

    /**
     * B16 视觉注册的统一入口。
     * <p>
     * <b>为什么不在 clientSetup 里注册</b>：B16 要覆盖 Create 自己注册的
     * {@code BRACKETED_KINETIC}(传动杆/小齿轮/大齿轮) visualizer，而 Create 的注册发生在
     * {@code FMLClientSetupEvent} 的监听器里({@code CreateBlockEntityBuilder.registerVisualizer()}，
     * 6.0.10 字节码与 6.0.11 源码 {@code CreateBlockEntityBuilder.java:105-116} 一致)。
     * {@code FMLClientSetupEvent} 是并行派发事件，跨 mod 的 {@code enqueueWork} 顺序没有保证，
     * 所以把覆盖动作推到 {@code FMLLoadCompleteEvent}：它一定晚于所有 client setup。
     * <p>
     * 而 Flywheel 的 visualizer 是"懒读取"的——{@code BlockEntityStorage.createRaw} 在每次
     * {@code queueAdd} 时才去取 visualizer，所以只要在世界加载前注册完就生效，
     * 不存在"注册太晚"的问题。
     * <p>
     * 注册是幂等的（{@code setVisualizer} 无条件覆盖），重复调用不会出问题。
     */
    @SubscribeEvent
    public static void loadComplete(FMLLoadCompleteEvent event) {
        ModVisualizers.register();
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
                        AvaritiaSingularityStressMod.MODID, "extreme_smithing_crafter"),
                // B26 炽骨立方合成器:按注册 ID 包装,排除 inventory 变体(见下方判断)
                ResourceLocation.fromNamespaceAndPath(
                        AvaritiaSingularityStressMod.MODID, "blaze_cube_crafter"));
        EncasedCTBehaviour blazeCasingCT = new EncasedCTBehaviour(encasedCasingShift());
        Set<ResourceLocation> blazeCasingIds = Set.of(
                ResourceLocation.fromNamespaceAndPath(
                        AvaritiaSingularityStressMod.MODID, "blaze_cube_casing"));

        // B30 终焉连接器:全向 CT,连接器↔连接器/终端相连
        ExtremeConnectorCTBehaviour connectorCT = new ExtremeConnectorCTBehaviour();
        Set<ResourceLocation> connectorIds = Set.of(
                ResourceLocation.fromNamespaceAndPath(
                        AvaritiaSingularityStressMod.MODID, "extreme_connector"));

        // B19 炽骨包壳齿轮的连接纹理：壳侧面用 side/_side_connected，机壳本体用 casing/_connected。
        // Create 原版是靠 CreateRegistrate.connectedTextures(...) 注册的；本工程不用 registrate，
        // 所以手动做等价的三步：① CTSpriteShifter.getCT 建 shift；② CasingConnectivity.make 登记配对；
        // ③ 烘焙结果里把模型包一层 CTModel（EncasedCTBehaviour.connectsTo 依赖 CasingConnectivity，缺了恒 false）。
        EncasedCogCTBehaviour blazeCogCT = new EncasedCogCTBehaviour(encasedCasingShift(),
                Couple.create(encasedSideShift(), encasedOtherSideShift()));
        EncasedCogCTBehaviour blazeLargeCogCT = new EncasedCogCTBehaviour(encasedCasingShift());
        Set<ResourceLocation> blazeCogIds = Set.of(
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "blaze_cube_encased_cogwheel"),
                ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "blaze_cube_encased_large_cogwheel"));

        int[] wrapped = {0};
        event.getModels().replaceAll((location, model) -> {
            if (blazeCogIds.contains(location.id())
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
                wrapped[0]++;
                return new CTModel(model,
                        location.id().getPath().contains("large") ? blazeLargeCogCT : blazeCogCT);
            }

            if (crafterBlockIds.contains(location.id())
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
                wrapped[0]++;
                return new CTModel(model, behaviour);
            }
            if (blazeCasingIds.contains(location.id())
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
                wrapped[0]++;
                return new CTModel(model, blazeCasingCT);
            }
            if (connectorIds.contains(location.id())
                    && !ModelResourceLocation.INVENTORY_VARIANT.equals(location.getVariant())) {
                wrapped[0]++;
                return new CTModel(model, connectorCT);
            }
            return model;
        });

        AvaritiaSingularityStressMod.LOGGER.info(
                "[extreme_crafter] CTModel wrapped on {} blockstate models", wrapped[0]);
    }

    // ---- 炽骨包壳齿轮的 CT shift（懒加载 + 幂等；CTSpriteShifter 内部有缓存与拼接刷新）----
    private static CTSpriteShiftEntry encasedCasingShift;
    private static CTSpriteShiftEntry encasedSideShift;
    private static CTSpriteShiftEntry encasedOtherSideShift;

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, "block/" + path);
    }

    private static CTSpriteShiftEntry encasedCasingShift() {
        initEncasedShifts();
        return encasedCasingShift;
    }

    private static CTSpriteShiftEntry encasedSideShift() {
        initEncasedShifts();
        return encasedSideShift;
    }

    private static CTSpriteShiftEntry encasedOtherSideShift() {
        initEncasedShifts();
        return encasedOtherSideShift;
    }

    private static void initEncasedShifts() {
        if (encasedCasingShift != null) {
            return;
        }
        encasedCasingShift = CTSpriteShifter.getCT(AllCTTypes.OMNIDIRECTIONAL,
                tex("blaze_cube_casing"), tex("blaze_cube_casing_connected"));
        encasedSideShift = CTSpriteShifter.getCT(AllCTTypes.VERTICAL,
                tex("blaze_cube_encased_cogwheel_side"), tex("blaze_cube_encased_cogwheel_side_connected"));
        encasedOtherSideShift = CTSpriteShifter.getCT(AllCTTypes.HORIZONTAL,
                tex("blaze_cube_encased_cogwheel_side"), tex("blaze_cube_encased_cogwheel_side_connected"));
        // EncasedCTBehaviour.connectsTo 会查 CasingConnectivity；不登记就永远是 false（CT 不生效）
        CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get(), encasedCasingShift);
        CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLAZE_CUBE_ENCASED_LARGE_COGWHEEL.get(), encasedCasingShift);
        CreateClient.CASING_CONNECTIVITY.make(ModBlocks.BLAZE_CUBE_CASING.get(), encasedCasingShift);
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
        event.register(ModMenus.WARP_TUNNEL_CONFIG.get(),
                committee.nova.avaritia_singularity_stress.init.client.screen.WarpTunnelConfigScreen::new);
        // B29 终焉动能合成终端
        event.register(ModMenus.EXTREME_CRAFTING_TERMINAL.get(),
                committee.nova.avaritia_singularity_stress.init.client.screen.ExtremeCraftingTerminalScreen::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // Flywheel discovers PartialModel instances from PartialModel.ALL.
    }
}
