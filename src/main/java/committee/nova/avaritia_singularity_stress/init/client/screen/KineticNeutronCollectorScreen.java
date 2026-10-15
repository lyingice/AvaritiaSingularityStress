package committee.nova.avaritia_singularity_stress.init.client.screen;

import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector.KineticNeutronCollectorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * 动能中子态素收集器的 GUI。
 * <p>
 * 按设计取消了进度条,只显示背景、1 格产出槽与玩家物品栏。
 * 贴图与无尽贪婪的机器 GUI 同尺寸(176x166 可见区、256x256 画布),
 * 输出槽位于 (80,32),玩家物品栏起点 (8,84)。
 */
public class KineticNeutronCollectorScreen extends AbstractContainerScreen<KineticNeutronCollectorMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/machine/kinetic_neutron_collector.png");

    public KineticNeutronCollectorScreen(KineticNeutronCollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
