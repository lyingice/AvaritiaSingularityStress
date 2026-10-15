package committee.nova.avaritia_singularity_stress.init.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.minecraft.resources.ResourceLocation;

public final class ModSpriteShifts {

    public static final SpriteShiftEntry EXTREME_MECHANICAL_CRAFTER_THINGIES =
            SpriteShifter.get(
                    texture("block/extreme_mechanical_crafter/crafter_thingies"),
                    texture("block/extreme_mechanical_crafter/crafter_thingies")
            );

    // ===== 终焉锻造器:皮带动画贴图(smithing 专属) =====
    public static final SpriteShiftEntry EXTREME_SMITHING_CRAFTER_THINGIES =
            SpriteShifter.get(
                    texture("block/extreme_smithing_crafter/crafter_thingies"),
                    texture("block/extreme_smithing_crafter/crafter_thingies")
            );

    // 连接纹理:正面(face==facing)连接平移,与主体模型键5的晶体矩阵外壳同源。
    // 外壳纹理由无尽集成提供,两种终焉方块共用,故正面条目只有一条。
    public static final CTSpriteShiftEntry EXTREME_CRAFTER_FRONT =
            CTSpriteShifter.getCT(
                    AllCTTypes.OMNIDIRECTIONAL,
                    ResourceLocation.fromNamespaceAndPath("avaritia_integration",
                            "block/create/crystal_matrix_casing"),
                    ResourceLocation.fromNamespaceAndPath("avaritia_integration",
                            "block/create/crystal_matrix_casing_connected"));

    // 连接纹理:竖直方向连接平移(侧壁)
    public static final CTSpriteShiftEntry EXTREME_CRAFTER_SIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.VERTICAL,
                    texture("block/extreme_mechanical_crafter/crafter_side"),
                    texture("block/extreme_mechanical_crafter/crafter_side_connected"));

    // 连接纹理:水平方向连接平移(上下端面)
    public static final CTSpriteShiftEntry EXTREME_CRAFTER_OTHERSIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.HORIZONTAL,
                    texture("block/extreme_mechanical_crafter/crafter_side"),
                    texture("block/extreme_mechanical_crafter/crafter_side_connected"));

    // ===== 终焉锻造器的侧壁连接纹理(smithing 专属) =====
    public static final CTSpriteShiftEntry EXTREME_SMITHING_CRAFTER_SIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.VERTICAL,
                    texture("block/extreme_smithing_crafter/crafter_side"),
                    texture("block/extreme_smithing_crafter/crafter_side_connected"));

    public static final CTSpriteShiftEntry EXTREME_SMITHING_CRAFTER_OTHERSIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.HORIZONTAL,
                    texture("block/extreme_smithing_crafter/crafter_side"),
                    texture("block/extreme_smithing_crafter/crafter_side_connected"));

    private ModSpriteShifts() {
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AvaritiaSingularityStressMod.MODID,
                path
        );
    }

    public static void init() {
        // Static initialization registers the shift entries with Create's shifter cache.
    }
}
