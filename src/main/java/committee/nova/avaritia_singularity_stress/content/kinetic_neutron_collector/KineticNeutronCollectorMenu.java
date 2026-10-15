package committee.nova.avaritia_singularity_stress.content.kinetic_neutron_collector;

import committee.nova.avaritia_singularity_stress.init.registry.ModMenus;
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
 * 动能中子态素收集器的容器菜单。
 * <p>
 * 只有 1 格产出槽(不可放入),外加玩家物品栏。按设计取消了进度条显示;
 * 进度数据仍通过 {@link ContainerData} 同步,便于需要时读取。
 */
public class KineticNeutronCollectorMenu extends AbstractContainerMenu {

    private static final int OUTPUT_SLOT = 0;
    private static final int INV_START = 1;
    private static final int INV_END = INV_START + 36;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final ContainerData data;

    /** 客户端由网络包构造:用一个空槽位占位,实际内容由服务端同步。 */
    public KineticNeutronCollectorMenu(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(id, playerInventory, new ItemStackHandler(1), new SimpleContainerData(2));
    }

    public KineticNeutronCollectorMenu(int id, Inventory playerInventory, IItemHandler inventory,
                                       ContainerData data) {
        super(ModMenus.KINETIC_NEUTRON_COLLECTOR.get(), id);
        this.data = data;

        addSlot(new SlotItemHandler(inventory, 0, 80, 32) {
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

    public int getProgress() {
        return data.get(0);
    }

    public int getPeriod() {
        return data.get(1);
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
        } else if (!moveItemStackTo(stack, OUTPUT_SLOT, OUTPUT_SLOT + 1, false)) {
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
