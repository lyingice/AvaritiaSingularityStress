package committee.nova.avaritia_singularity_stress.content.kinetics.link;

import com.simibubi.create.content.decoration.encasing.CasingBlock;

/**
 * B19 炽骨立方机壳：独立的机壳方块（不是拿别人的方块当外壳）。
 * <p>
 * Create 的封装体系里，机壳本身就是一个普通方块（{@code CasingBlock}，
 * {@code CasingBlock.java} 只有构造与扳手行为），真正把它和"被封装的方块"绑起来的是
 * {@code EncasingRegistry.addVariant(可封装方块, 包壳方块)} —— 见
 * {@code AvaritiaSingularityStressMod#commonSetup}。
 * <p>
 * 纹理用本包 {@code block/blaze_cube_casing}（{@code blaze_cube_casing_connected} 供连接纹理备用），
 * 后期直接替换这两张图即可，不用动代码或模型。
 * 命名修正：这两张图原先误命名为 {@code andesite_casing*}，与 Create 的安山机壳重名、极易混淆。
 */
public class BlazeCubeCasingBlock extends CasingBlock {

    public BlazeCubeCasingBlock(Properties properties) {
        super(properties);
    }
}
