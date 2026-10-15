package committee.nova.avaritia_singularity_stress.init.client.renderer;

import committee.nova.avaritia_singularity_stress.init.client.ModSpriteShifts;
import committee.nova.avaritia_singularity_stress.init.registry.ModPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.SpriteShiftEntry;

/**
 * 一种终焉合成器的可视化资源组合。
 * <p>
 * 因为终焉合成器与终焉锻造器的渲染逻辑完全相同、只有模型与贴图不同,
 * 所以把「用哪些部件模型、用哪张动画贴图」抽成参数,
 * 由同一个渲染器与同一个 Flywheel Visual 复用于两种方块。
 * <p>
 * 若复制两份渲染器,那约 240 行动画数学就会出现多份真相;
 * 参数化后只有一份实现,编译器保证两套配置都不会漏改。
 */
public record ExtremeCrafterVisualSet(
        PartialModel lid,
        PartialModel arrow,
        PartialModel beltFrame,
        PartialModel belt,
        PartialModel cogwheel,
        SpriteShiftEntry thingies
) {

    /** 终焉动力合成器。 */
    public static final ExtremeCrafterVisualSet MECHANICAL = new ExtremeCrafterVisualSet(
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_LID,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_ARROW,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_BELT_FRAME,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_BELT,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_COGWHEEL,
            ModSpriteShifts.EXTREME_MECHANICAL_CRAFTER_THINGIES
    );

    /**
     * 终焉锻造器。
     * <p>
     * 只替换使用 smithing 专属纹理的三个部件;
     * belt 用 {@code #5} 外壳、cogwheel 用共享的 neutron_cogwheel,
     * 与合成器内容一致,因此直接复用其模型常量。
     */
    public static final ExtremeCrafterVisualSet SMITHING = new ExtremeCrafterVisualSet(
            ModPartialModels.EXTREME_SMITHING_CRAFTER_LID,
            ModPartialModels.EXTREME_SMITHING_CRAFTER_ARROW,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_BELT_FRAME,
            ModPartialModels.EXTREME_SMITHING_CRAFTER_BELT,
            ModPartialModels.EXTREME_MECHANICAL_CRAFTER_COGWHEEL,
            ModSpriteShifts.EXTREME_SMITHING_CRAFTER_THINGIES
    );
}
