package committee.nova.avaritia_singularity_stress.init.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 星空延拓之手的第三人称/GUI/第一人称物品模型渲染器。
 *
 * <p>结构与 Create ExtendoGripItemRenderer 完全一致：基础握把、两组伸缩片段、
 * 末端假手和旋转齿轮均通过 PartialModel 绘制。第一人称状态读取 Neutron 自己的
 * RenderHand handler，避免引用 Create 原版物品的硬编码状态。</p>
 */
public class NeutronExtendoGripItemRenderer extends CustomRenderedItemModelRenderer {

    private static final PartialModel COG = ModPartialModels.NEUTRON_EXTENDO_GRIP_COG;
    private static final PartialModel THIN_SHORT = ModPartialModels.NEUTRON_EXTENDO_GRIP_THIN_SHORT;
    private static final PartialModel WIDE_SHORT = ModPartialModels.NEUTRON_EXTENDO_GRIP_WIDE_SHORT;
    private static final PartialModel THIN_LONG = ModPartialModels.NEUTRON_EXTENDO_GRIP_THIN_LONG;
    private static final PartialModel WIDE_LONG = ModPartialModels.NEUTRON_EXTENDO_GRIP_WIDE_LONG;

    private static final Vec3 ROTATION_OFFSET = new Vec3(0, 1 / 2f, 1 / 2f);
    private static final Vec3 COG_ROTATION_OFFSET = new Vec3(0, 1 / 16f, 0);

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        TransformStack stacker = TransformStack.of(ms);
        float animation = 0.25f;
        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean rightHand = transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (leftHand || rightHand) {
            animation = Mth.lerp(AnimationTickHolder.getPartialTicks(),
                    NeutronExtendoGripRenderHandler.lastMainHandAnimation,
                    NeutronExtendoGripRenderHandler.mainHandAnimation);
        }

        animation = animation * animation * animation;
        float extensionAngle = Mth.lerp(animation, 24f, 156f);
        float halfAngle = extensionAngle / 2;
        float oppositeAngle = 180 - extensionAngle;

        // 基础握把
        renderer.renderSolid(model.getOriginalModel(), light);

        // 第一组伸缩片段
        ms.pushPose();
        ms.translate(0, 1 / 16f, -7 / 16f);
        ms.scale(1, 1, 1 + animation);
        ms.pushPose();
        stacker.rotateXDegrees(-halfAngle);
        stacker.translate(ROTATION_OFFSET);
        renderer.renderSolid(THIN_SHORT.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        ms.translate(0, 5.5f / 16f, 0);
        stacker.rotateXDegrees(-oppositeAngle);
        stacker.translate(ROTATION_OFFSET);
        renderer.renderSolid(WIDE_LONG.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        ms.translate(0, 11 / 16f, 0);
        stacker.rotateXDegrees(oppositeAngle);
        stacker.translate(ROTATION_OFFSET);
        ms.translate(0, 0.5f / 16f, 0);
        renderer.renderSolid(THIN_SHORT.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        ms.popPose();
        ms.pushPose();

        // 第二组伸缩片段
        stacker.rotateXDegrees(-180 + halfAngle);
        stacker.translate(ROTATION_OFFSET);
        renderer.renderSolid(WIDE_SHORT.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        ms.translate(0, 5.5f / 16f, 0);
        stacker.rotateXDegrees(oppositeAngle);
        stacker.translate(ROTATION_OFFSET);
        renderer.renderSolid(THIN_LONG.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        ms.translate(0, 11 / 16f, 0);
        stacker.rotateXDegrees(-oppositeAngle);
        stacker.translate(ROTATION_OFFSET);
        ms.translate(0, 0.5f / 16f, 0);
        renderer.renderSolid(WIDE_SHORT.get(), light);
        stacker.translateBack(ROTATION_OFFSET);

        // 末端假手：第一人称使用随动画变化的姿态，其余视角使用固定指向姿态
        ms.translate(0, 5.5f / 16f, 0);
        stacker.rotateXDegrees(180 - halfAngle);
        stacker.rotateYDegrees(180);
        ms.translate(0, 0, -4 / 16f);
        ms.scale(1, 1, 1 / (1 + animation));
        renderer.renderSolid((leftHand || rightHand)
                ? NeutronExtendoGripRenderHandler.pose.get()
                : ModPartialModels.NEUTRON_EXTENDO_GRIP_HAND_POINTING.get(), light);
        ms.popPose();

        ms.popPose();

        // 旋转齿轮
        ms.pushPose();
        float angle = AnimationTickHolder.getRenderTime() * -2;
        if (leftHand || rightHand) {
            angle += 360 * animation;
        }
        angle %= 360;
        stacker.translate(COG_ROTATION_OFFSET);
        stacker.rotateZDegrees(angle);
        stacker.translateBack(COG_ROTATION_OFFSET);
        renderer.renderSolid(COG.get(), light);
        ms.popPose();
    }
}
