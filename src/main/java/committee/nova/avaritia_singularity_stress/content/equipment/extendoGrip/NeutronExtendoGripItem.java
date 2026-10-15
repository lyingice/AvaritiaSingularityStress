package committee.nova.avaritia_singularity_stress.content.equipment.extendoGrip;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;
import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.init.client.renderer.NeutronExtendoGripItemRenderer;
import committee.nova.avaritia_singularity_stress.init.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.function.Consumer;

/**
 * I2 星空延拓之手。
 *
 * <p>这是独立于 Create {@code ExtendoGripItem} 的实现，避免 Create 原版类中
 * 对 {@code AllItems.EXTENDO_GRIP}、Create 网络包和 Create 配置的硬编码。</p>
 */
@EventBusSubscriber(modid = AvaritiaSingularityStressMod.MODID)
public class NeutronExtendoGripItem extends Item {

    public static final int MAX_DAMAGE = 2000;

    private static final String SINGLE_RANGE_MARKER = "neutronExtendoSingleRange";
    private static final String DUAL_RANGE_MARKER = "neutronExtendoDualRange";
    private static final String COMBAT_MARKER = "neutronExtendoCombat";
    private static final String AIR_REMAINDER = "neutronExtendoAirRemainder";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AvaritiaSingularityStressMod.MODID, path);
    }

    private static AttributeModifier addValueModifier(String path, double amount) {
        return new AttributeModifier(id(path), amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static AttributeModifier multipliedModifier(String path, double amount) {
        return new AttributeModifier(id(path), amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static final AttributeModifier SINGLE_BLOCK_RANGE = addValueModifier("neutron_single_block_range", 6.0D);
    private static final AttributeModifier SINGLE_ENTITY_RANGE = addValueModifier("neutron_single_entity_range", 6.0D);
    private static final AttributeModifier DUAL_BLOCK_RANGE = addValueModifier("neutron_dual_block_range", 10.0D);
    private static final AttributeModifier DUAL_ENTITY_RANGE = addValueModifier("neutron_dual_entity_range", 10.0D);
    private static final AttributeModifier ATTACK_DAMAGE = multipliedModifier("neutron_attack_damage", 1.0D);
    private static final AttributeModifier ATTACK_SPEED = multipliedModifier("neutron_attack_speed", 0.5D);
    private static final AttributeModifier BLOCK_BREAK_SPEED = multipliedModifier("neutron_block_break_speed", 1.0D);

    private static final Multimap<Holder<Attribute>, AttributeModifier> SINGLE_RANGE_MODIFIERS =
            ImmutableMultimap.<Holder<Attribute>, AttributeModifier>builder()
                    .put(Attributes.BLOCK_INTERACTION_RANGE, SINGLE_BLOCK_RANGE)
                    .put(Attributes.ENTITY_INTERACTION_RANGE, SINGLE_ENTITY_RANGE)
                    .build();
    private static final Multimap<Holder<Attribute>, AttributeModifier> DUAL_RANGE_MODIFIERS =
            ImmutableMultimap.<Holder<Attribute>, AttributeModifier>builder()
                    .put(Attributes.BLOCK_INTERACTION_RANGE, DUAL_BLOCK_RANGE)
                    .put(Attributes.ENTITY_INTERACTION_RANGE, DUAL_ENTITY_RANGE)
                    .build();
    private static final Multimap<Holder<Attribute>, AttributeModifier> COMBAT_MODIFIERS =
            ImmutableMultimap.<Holder<Attribute>, AttributeModifier>builder()
                    .put(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                    .put(Attributes.ATTACK_SPEED, ATTACK_SPEED)
                    .put(Attributes.BLOCK_BREAK_SPEED, BLOCK_BREAK_SPEED)
                    .build();

    public NeutronExtendoGripItem(Properties properties) {
        super(properties.durability(MAX_DAMAGE));
    }

    public static boolean isNeutronExtendoGrip(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.NEUTRON_EXTENDO_GRIP.get());
    }

    public static boolean isHoldingNeutronExtendoGrip(Player player) {
        return isNeutronExtendoGrip(player.getMainHandItem())
                || isNeutronExtendoGrip(player.getOffhandItem());
    }

    private static int maxUses() {
        return AllConfigs.server().equipment.maxExtendoGripActions.get();
    }

    /** 每 tick 根据主/副手状态添加或移除范围、战斗和挖掘属性。 */
    @SubscribeEvent
    public static void updateHeldModifiers(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        CompoundTag persistent = player.getPersistentData();
        boolean inMain = isNeutronExtendoGrip(player.getMainHandItem());
        boolean inOff = isNeutronExtendoGrip(player.getOffhandItem());
        boolean dual = inMain && inOff;
        boolean single = inMain ^ inOff;
        boolean combat = inMain || inOff;

        updateModifierState(player, persistent, SINGLE_RANGE_MARKER, single, SINGLE_RANGE_MODIFIERS);
        updateModifierState(player, persistent, DUAL_RANGE_MARKER, dual, DUAL_RANGE_MODIFIERS);
        updateModifierState(player, persistent, COMBAT_MARKER, combat, COMBAT_MODIFIERS);
    }

    private static void updateModifierState(Player player, CompoundTag persistent, String marker,
                                             boolean shouldHave,
                                             Multimap<Holder<Attribute>, AttributeModifier> modifiers) {
        boolean has = persistent.contains(marker);
        if (shouldHave == has) {
            return;
        }
        if (shouldHave) {
            player.getAttributes().addTransientAttributeModifiers(modifiers);
            persistent.putBoolean(marker, true);
        } else {
            player.getAttributes().removeAttributeModifiers(modifiers);
            persistent.remove(marker);
        }
    }

    @SubscribeEvent
    public static void restoreHeldModifiers(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(DUAL_RANGE_MARKER)) {
            player.getAttributes().addTransientAttributeModifiers(DUAL_RANGE_MODIFIERS);
        } else if (persistent.contains(SINGLE_RANGE_MARKER)) {
            player.getAttributes().addTransientAttributeModifiers(SINGLE_RANGE_MODIFIERS);
        }
        if (persistent.contains(COMBAT_MARKER)) {
            player.getAttributes().addTransientAttributeModifiers(COMBAT_MODIFIERS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void consumeOnBreak(BreakEvent event) {
        findAndDamage(event.getPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void consumeOnPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            findAndDamage(player);
        }
    }

    @SubscribeEvent
    public static void consumeOnAttack(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source != null && source.getEntity() instanceof Player player) {
            findAndDamage(player);
        }
    }

    private static void findAndDamage(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }

        EquipmentSlot slot = EquipmentSlot.MAINHAND;
        ItemStack held = player.getMainHandItem();
        if (!isNeutronExtendoGrip(held)) {
            slot = EquipmentSlot.OFFHAND;
            held = player.getOffhandItem();
        }
        if (!isNeutronExtendoGrip(held)) {
            return;
        }

        if (!consumeHalfAir(player)) {
            held.hurtAndBreak(1, player, slot);
        }
    }

    /**
     * 复用 Create 背罐来源，但绕过 canAbsorbDamage 的整数最小值 1，
     * 以固定点余数实现真正的半速空气消耗。
     */
    private static boolean consumeHalfAir(Player player) {
        if (player.isCreative() || maxUses() == 0) {
            return true;
        }

        var tanks = BacktankUtil.getAllWithAir(player);
        if (tanks.isEmpty()) {
            return false;
        }

        long denominator = Math.max(2L * maxUses(), 1L);
        long numerator = player.getPersistentData().getLong(AIR_REMAINDER)
                + BacktankUtil.maxAirWithoutEnchants();
        int wholeUnits = (int) (numerator / denominator);
        int remainder = (int) (numerator % denominator);

        if (wholeUnits > 0) {
            BacktankUtil.consumeAir(player, tanks.getFirst(), wholeUnits);
        }
        player.getPersistentData().putInt(AIR_REMAINDER, remainder);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, maxUses());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, maxUses());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BacktankUtil.getBarColor(stack, maxUses());
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new NeutronExtendoGripItemRenderer()));
    }
}
