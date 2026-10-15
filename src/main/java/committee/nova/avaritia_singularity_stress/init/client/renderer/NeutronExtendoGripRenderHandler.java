package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.equipment.extendoGrip.NeutronExtendoGripItem;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 星空延拓之手的第一人称手臂渲染。
 * <p>
 * 复制 Create ExtendoGripRenderHandler 的调用顺序与 PoseStack 数学，
 * 但所有物品/手部 partial 判断都改为 Neutron 自身资源。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID, value = Dist.CLIENT)
public final class NeutronExtendoGripRenderHandler {

    public static float mainHandAnimation;
    public static float lastMainHandAnimation;
    public static PartialModel pose = ModPartialModels.NEUTRON_EXTENDO_GRIP_HAND_PUNCHING;

    private NeutronExtendoGripRenderHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }

    public static void tick() {
        lastMainHandAnimation = mainHandAnimation;
        mainHandAnimation *= Mth.clamp(mainHandAnimation, 0.8f, 0.99f);

        pose = ModPartialModels.NEUTRON_EXTENDO_GRIP_HAND_PUNCHING;
        if (!NeutronExtendoGripItem.isNeutronExtendoGrip(getRenderedOffHandStack())) {
            return;
        }

        ItemStack main = getRenderedMainHandStack();
        if (main.isEmpty() || !(main.getItem() instanceof BlockItem)) {
            return;
        }
        if (!Minecraft.getInstance().getItemRenderer().getModel(main, null, null, 0).isGui3d()) {
            return;
        }
        pose = ModPartialModels.NEUTRON_EXTENDO_GRIP_HAND_HOLDING;
    }

    @SubscribeEvent
    public static void onRenderPlayerHand(RenderHandEvent event) {
        ItemStack heldItem = event.getItemStack();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        boolean rightHand = event.getHand() == InteractionHand.MAIN_HAND
                ^ player.getMainArm() == HumanoidArm.LEFT;
        ItemStack offhandItem = getRenderedOffHandStack();
        boolean notInOffhand = !NeutronExtendoGripItem.isNeutronExtendoGrip(offhandItem);
        if (notInOffhand && !NeutronExtendoGripItem.isNeutronExtendoGrip(heldItem)) {
            return;
        }

        PoseStack ms = event.getPoseStack();
        TransformStack msr = TransformStack.of(ms);
        AbstractClientPlayer clientPlayer = mc.player;
        RenderSystem.setShaderTexture(0, clientPlayer.getSkin().texture());

        float flip = rightHand ? 1.0F : -1.0F;
        float swingProgress = event.getSwingProgress();
        boolean blockItem = heldItem.getItem() instanceof BlockItem;
        float equipProgress = blockItem ? 0 : event.getEquipProgress() / 4;

        ms.pushPose();
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            if (1 - swingProgress > mainHandAnimation && swingProgress > 0) {
                mainHandAnimation = 0.95f;
            }
            float animation = Mth.lerp(AnimationTickHolder.getPartialTicks(),
                    lastMainHandAnimation, mainHandAnimation);
            animation = animation * animation * animation;

            ms.translate(flip * (0.64000005F - .1f), -0.4F + equipProgress * -0.6F,
                    -0.71999997F + .3f);

            ms.pushPose();
            msr.rotateYDegrees(flip * 75.0F);
            ms.translate(flip * -1.0F, 3.6F, 3.5F);
            msr.rotateZDegrees(flip * 120)
                    .rotateXDegrees(200)
                    .rotateYDegrees(flip * -135.0F);
            ms.translate(flip * 5.6F, 0.0F, 0.0F);
            msr.rotateYDegrees(flip * 40.0F);
            ms.translate(flip * 0.05f, -0.3f, -0.3f);

            PlayerRenderer playerRenderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            if (rightHand) {
                playerRenderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(),
                        event.getPackedLight(), player);
            } else {
                playerRenderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(),
                        event.getPackedLight(), player);
            }
            ms.popPose();

            ms.pushPose();
            ms.translate(flip * -0.1f, 0, -0.3f);
            ItemInHandRenderer firstPersonRenderer = mc.getEntityRenderDispatcher().getItemInHandRenderer();
            ItemDisplayContext transform = rightHand
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            firstPersonRenderer.renderItem(mc.player, notInOffhand ? heldItem : offhandItem, transform, !rightHand,
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());

            if (!notInOffhand) {
                ClientHooks.handleCameraTransforms(ms,
                        mc.getItemRenderer().getModel(offhandItem, null, null, 0), transform, !rightHand);
                ms.translate(flip * -.05f, .15f, -1.2f);
                ms.translate(0, 0, -animation * 2.25f);
                if (blockItem && mc.getItemRenderer().getModel(heldItem, null, null, 0).isGui3d()) {
                    msr.rotateYDegrees(flip * 45);
                    ms.translate(flip * 0.15f, -0.15f, -.05f);
                    ms.scale(1.25f, 1.25f, 1.25f);
                }
                firstPersonRenderer.renderItem(mc.player, heldItem, transform, !rightHand,
                        event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
            }
            ms.popPose();
        }
        ms.popPose();
        event.setCanceled(true);
    }

    private static ItemStack getRenderedMainHandStack() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
    }

    private static ItemStack getRenderedOffHandStack() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getOffhandItem();
    }
}
