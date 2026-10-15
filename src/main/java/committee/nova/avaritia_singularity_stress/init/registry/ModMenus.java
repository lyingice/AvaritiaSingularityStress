package committee.nova.avaritia_singularity_stress.init.registry;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorMenu;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfigMenu;
import committee.nova.avaritia_singularity_stress.content.wireless.frequency_card.FrequencyCardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 机器 GUI 的菜单类型注册。 */
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AvaritiaSingularityStressMod.MODID);

    public static final Supplier<MenuType<KineticNeutronCollectorMenu>> KINETIC_NEUTRON_COLLECTOR =
            MENUS.register("kinetic_neutron_collector",
                    () -> IMenuTypeExtension.create(KineticNeutronCollectorMenu::new));

    public static final Supplier<MenuType<KineticNeutronCompressorMenu>> KINETIC_NEUTRON_COMPRESSOR =
            MENUS.register("kinetic_neutron_compressor",
                    () -> IMenuTypeExtension.create(KineticNeutronCompressorMenu::new));
    public static final Supplier<MenuType<FrequencyCardMenu>> WIRELESS_FREQUENCY_CARD =
            MENUS.register("wireless_frequency_card",
                    () -> IMenuTypeExtension.create(FrequencyCardMenu::new));

    /**
     * B9 / B10 / B11 三种无线方块共用的配置界面。
     * <p>
     * 工厂签名必须是 {@code (int id, Inventory inv, RegistryFriendlyByteBuf buf)}，
     * 这里用 lambda 显式构造，因为 {@link WirelessConfigMenu} 还要拿到菜单类型本身。
     */
    public static final Supplier<MenuType<WirelessConfigMenu>> WIRELESS_CONFIG =
            MENUS.register("wireless_config",
                    () -> IMenuTypeExtension.create(
                            (id, inventory, buf) -> new WirelessConfigMenu(
                                    ModMenus.WIRELESS_CONFIG.get(), id, inventory, buf)));

    public static void init() {
        // 仅用于触发类加载
    }
}
