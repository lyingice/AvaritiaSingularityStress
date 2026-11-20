package committee.nova.avaritia_singularity_stress.content.extreme_drill;

import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.foundation.utility.BlockHelper;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlockEntities;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * B27 终焉钻头方块实体。
 * <p>
 * 两处覆写（其余继承 Create 钻头）：
 * <ul>
 *   <li>{@link #getBreakSpeed()} 放大破坏速度，使进度单 tick 直达 10 —— 几乎立即挖掘；</li>
 *   <li>{@link #onBlockBroken(BlockState)} 用带时运的工具计算掉落，时运等级随转速线性增长。</li>
 * </ul>
 * 时运映射：基础等级 {@code floor(|speed| / 16)} 截断到 [0,16]，即 16RPM→1 级、256RPM→16 级，每 16RPM 一级；
 * 有效等级再乘 {@link ExtremeDrillConfig#fortuneMultiplier()} 额外乘区（SERVER 配置，默认 5）。
 * 掉落复用 Create {@code BlockHelper.destroyBlockAs}（BlockHelper.java:213-264），它把工具传给
 * {@code Block.getDrops}，因此工具上的时运附魔会按真实等级生效（可超过原版上限 3）。
 */
public class ExtremeDrillBlockEntity extends DrillBlockEntity {

    public ExtremeDrillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static ExtremeDrillBlockEntity create(BlockPos pos, BlockState state) {
        return new ExtremeDrillBlockEntity(ModBlockEntities.EXTREME_DRILL.get(), pos, state);
    }

    /** 近瞬挖：原版为 |speed|/100（BlockBreakingKineticBlockEntity.java:157-159），这里放大 100 倍。 */
    @Override
    public float getBreakSpeed() {
        return Math.abs(getSpeed()) * 100f;
    }

    /** 当前转速对应的基础时运等级（0~16），不含乘区。 */
    public int getBaseFortuneLevel() {
        return Mth.clamp((int) (Math.abs(getSpeed()) / 16f), 0, 16);
    }

    /** 有效时运 = 基础等级 × 配置额外乘区（默认 5）。 */
    public int getFortuneLevel() {
        return getBaseFortuneLevel() * ExtremeDrillConfig.fortuneMultiplier();
    }

    @Override
    public void onBlockBroken(BlockState stateToBreak) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        int fortune = getFortuneLevel();
        if (fortune > 0) {
            // Enchantments.FORTUNE 是 ResourceKey，需经注册表转 Holder（与 BlockHelper.java:250-251 一致）
            Holder<Enchantment> fortuneHolder = level.registryAccess()
                    .registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(Enchantments.FORTUNE);
            tool.enchant(fortuneHolder, fortune);
        }

        Vec3 vec = VecHelper.offsetRandomly(VecHelper.getCenterOf(breakingPos), level.random, .125f);
        BlockHelper.destroyBlockAs(level, breakingPos, null, tool, 1f, stack -> {
            if (stack.isEmpty()) {
                return;
            }
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                return;
            }
            if (level.restoringBlockSnapshots) {
                return;
            }
            ItemEntity itemEntity = new ItemEntity(level, vec.x, vec.y, vec.z, stack);
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(itemEntity);
        });
    }
}
