package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_compressor;

import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 动能中子态素压缩机的容器菜单。
 * <p>
 * 槽位沿用无尽贪婪压缩机的坐标:输入 (39,35)、输出 (120,35);
 * 进度/材料条由 {@link committee.nova.avaritia_singularity_stress.init.client.screen.KineticNeutronCompressorScreen} 绘制。
 */
public class KineticNeutronCompressorMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    private static final int INV_START = 2;
    private static final int INV_END = INV_START + 36;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final ContainerData data;
    private final BlockPos pos;

    public KineticNeutronCompressorMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new ItemStackHandler(2), new SimpleContainerData(4), buffer.readBlockPos());
    }

    public KineticNeutronCompressorMenu(int id, Inventory playerInventory, IItemHandler inventory,
                                        ContainerData data, BlockPos pos) {
        super(ModMenus.KINETIC_NEUTRON_COMPRESSOR.get(), id);
        this.data = data;
        this.pos = pos;

        addSlot(new SlotItemHandler(inventory, INPUT_SLOT, 39, 35));
        addSlot(new SlotItemHandler(inventory, OUTPUT_SLOT, 120, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        addDataSlots(data);
        addPlayerInventory(playerInventory);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getPeriod() {
        return data.get(1);
    }

    public int getMaterialCount() {
        return data.get(2);
    }

    public int getMaterialRequired() {
        return data.get(3);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index == OUTPUT_SLOT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index == INPUT_SLOT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.getCount() == 0) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}