package committee.nova.avaritia_singularity_stress.compat.jei;

import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * JEI 中旋转的终焉合成器 3D 展示。
 * <p>
 * 具体展示哪个方块由构造参数决定:终焉动力合成器(分级配方分类)
 * 或终焉锻造器(锻造配方分类)。齿轮部件两者共用(中子齿轮纹理相同)。
 */
public class AnimatedExtremeCrafter extends AnimatedKinetics {

    private final Supplier<BlockState> blockState;

    /** 默认展示终焉动力合成器。 */
    public AnimatedExtremeCrafter() {
        this(() -> ModBlocks.EXTREME_MECHANICAL_CRAFTER.get().defaultBlockState());
    }

    public AnimatedExtremeCrafter(Supplier<BlockState> blockState) {
        this.blockState = blockState;
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int x, int y) {
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        AllGuiTextures.JEI_SHADOW.render(guiGraphics, -16, 13);
        poseStack.translate(3, 16, 0);
        TransformStack.of(poseStack)
                .rotateXDegrees(-12.5F)
                .rotateYDegrees(-22.5F);

        // 旋转齿轮(本模组 datagen 生成的中子齿轮部件)
        GuiGameElement.of(ModPartialModels.EXTREME_MECHANICAL_CRAFTER_COGWHEEL)
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .rotateBlock(90, 0, getCurrentAngle())
                .scale(22)
                .render(guiGraphics);

        // 合成器方块本体
        GuiGameElement.of(blockState.get())
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .rotateBlock(0, 180, 0)
                .scale(22)
                .render(guiGraphics);

        poseStack.popPose();
    }
}
