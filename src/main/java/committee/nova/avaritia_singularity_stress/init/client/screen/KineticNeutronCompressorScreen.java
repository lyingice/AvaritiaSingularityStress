package committee.nova.avaritia_singularity_stress.init.client.screen;

import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorBlockEntity;
import committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor.KineticNeutronCompressorMenu;
import committee.nova.mods.avaritia.Res;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class KineticNeutronCompressorScreen extends AbstractContainerScreen<KineticNeutronCompressorMenu> {

    // 背景/槽位用你自己的贴图
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "avaritia_singularity_stress", "textures/gui/machine/kinetic_neutron_compressor.png");



    private static final int MATERIAL_BAR_X = 63;
    private static final int PROGRESS_BAR_X = 89;
    private static final int BAR_Y = 35;
    private static final int BAR_HEIGHT = 16;

    private static final int MATERIAL_BAR_U = 176;
    private static final int MATERIAL_BAR_V = 18;
    private static final int MATERIAL_BAR_MAX_W = 16;

    private static final int PROGRESS_BAR_U = 176;
    private static final int PROGRESS_BAR_V = 0;
    private static final int PROGRESS_BAR_MAX_W = 22;

    public KineticNeutronCompressorScreen(KineticNeutronCompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 背景
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // 材料条
        int required = menu.getMaterialRequired();
        if (required > 0) {
            int material = Mth.clamp(menu.getMaterialCount(), 0, required);
            int filledW = (int) ((long) material * MATERIAL_BAR_MAX_W / required);
            if (filledW > 0) {
                graphics.blit(TEXTURE,
                        leftPos + MATERIAL_BAR_X, topPos + BAR_Y,
                        MATERIAL_BAR_U, MATERIAL_BAR_V,
                        filledW + 1, BAR_HEIGHT);
            }
        }

        // 进度条
        int period = Math.max(1, menu.getPeriod());
        int progress = Mth.clamp(menu.getProgress(), 0, period);
        int filledP = (int) ((long) progress * PROGRESS_BAR_MAX_W / period);
        if (filledP > 0) {
            graphics.blit(TEXTURE,
                    leftPos + PROGRESS_BAR_X, topPos + BAR_Y,
                    PROGRESS_BAR_U, PROGRESS_BAR_V,
                    filledP + 1, BAR_HEIGHT);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);

        int barLeft = leftPos + MATERIAL_BAR_X;
        int barTop = topPos + BAR_Y;
        if (mouseX >= barLeft && mouseX < barLeft + MATERIAL_BAR_MAX_W
                && mouseY >= barTop && mouseY < barTop + BAR_HEIGHT) {

            List<Component> tooltip = new ArrayList<>();
            int material = menu.getMaterialCount();
            int required = menu.getMaterialRequired();

            if (material < 1) {
                tooltip.add(Component.translatable("gui.avaritia_singularity_stress.empty")
                        .withStyle(ChatFormatting.WHITE));
            } else {
                ItemStack materialStack = getMaterialStack();
                if (!materialStack.isEmpty()) {
                    tooltip.add(materialStack.getHoverName());
                }
                tooltip.add(Component.literal(material + " / " + required));
            }

            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    private ItemStack getMaterialStack() {
        if (minecraft == null || minecraft.level == null) return ItemStack.EMPTY;
        BlockEntity be = minecraft.level.getBlockEntity(menu.getBlockPos());
        if (be instanceof KineticNeutronCompressorBlockEntity compressor) {
            return compressor.getMaterialStack();
        }
        return ItemStack.EMPTY;
    }
}