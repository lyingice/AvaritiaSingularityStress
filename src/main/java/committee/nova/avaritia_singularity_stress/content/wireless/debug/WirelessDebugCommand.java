package committee.nova.avaritia_singularity_stress.content.wireless.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.simibubi.create.content.fluids.FluidPropagator;

import committee.nova.avaritia_singularity_stress.AvaritiaSingularityStressMod;
import committee.nova.avaritia_singularity_stress.content.wireless.IFrequencyBindable;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessConfig;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessDevice;
import committee.nova.avaritia_singularity_stress.content.wireless.WirelessNetwork;
// [B11 雪藏] import committee.nova.avaritia_singularity_stress.content.wireless.fluid_transmitter.WirelessFluidTransmitterBlockEntity;
import committee.nova.avaritia_singularity_stress.content.wireless.stress_bridge.WirelessStressBridgeBlockEntity;
import committee.nova.avaritia_singularity_stress.init.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 无线系统调试指令：{@code /wireless_debug [半径]}（默认 32，最大 64，需要权限等级 2）。
 * <p>
 * 把附近这四类方块的<b>全部相关参数</b>逐行打进系统日志（前缀 {@code [WIRELESS-DBG]}）：
 * <ul>
 *   <li>B10 无线应力传输器 —— 输出端 / 接收端</li>
 *   <li>[已雪藏] B11 无线流体传输器 —— 输出端 / 接收端（代码整体注释，见 fluid_transmitter 包）</li>
 * </ul>
 * 内容包括：方块状态、频率/可见性/所有者/频道钥匙、各自配置字段、应力网络（capacity/stress/idle/
 * 成员数）、跨桥量与看门狗、动能状态、流体行为的每个方向与压强、六个邻居是什么、
 * 以及 B11 的<b>桥接门禁逐条判定</b>（哪一条不成立一目了然）。
 * <p>
 * 只读：不改任何状态、不写 NBT、不发包。
 */
public final class WirelessDebugCommand {

    private static final String PREFIX = "[WIRELESS-DBG] ";

    private static final int DEFAULT_RADIUS = 32;
    private static final int MAX_RADIUS = 64;

    private WirelessDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wireless_debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource(), DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> run(context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static int run(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());

        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (isRelevant(level.getBlockState(pos).getBlock())) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));

        log("==================== /wireless_debug ====================");
        log("维度=" + level.dimension().location() + " 原点=" + origin + " 半径=" + radius
                + " gameTime=" + level.getGameTime());
        log("配置: stressBridgeRange=" + WirelessConfig.stressBridgeRange()
                // [B11 雪藏] + " fluidTransmitterRange=" + WirelessConfig.fluidTransmitterRange()
                + " itemFunnelRange=" + WirelessConfig.itemFunnelRange()
                + " maxFrequency=" + WirelessConfig.maxFrequency()
                + " | create mechanicalPumpRange(getPumpRange())=" + FluidPropagator.getPumpRange());
        log("本维度无线网: 频道数=" + WirelessNetwork.channelCount(level)
                + " 登记设备数=" + WirelessNetwork.deviceCount(level)
                + "   (登记=已写频率并成功进网；没登记就不会被配对到)");

        int index = 0;
        for (WirelessDevice device : WirelessNetwork.devicesIn(level)) {
            log("  [登记" + index++ + "] " + describe(device));
        }

        log("扫描到相关方块 " + found.size() + " 个（B10 输出端/接收端）");
        index = 0;
        for (BlockPos pos : found) {
            log("---------- 方块[" + index++ + "] " + pos + " 距原点 "
                    + String.format("%.1f", Math.sqrt(pos.distSqr(origin))) + " ----------");
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof WirelessStressBridgeBlockEntity bridge) {
                bridge.debugDump().forEach(WirelessDebugCommand::log);
            // [B11 雪藏] B11 无线流体传输器已整体注释
            // } else if (blockEntity instanceof WirelessFluidTransmitterBlockEntity transmitter) {
            //     transmitter.debugDump().forEach(WirelessDebugCommand::log);
            } else {
                log("(该坐标没有本模组的方块实体)");
            }
        }
        log("==================== /wireless_debug 结束 ====================");

        int count = found.size();
        source.sendSuccess(() -> Component.literal("已把 " + count
                        + " 个无线方块的参数写入日志（latest.log / 控制台搜索 WIRELESS-DBG）")
                .withStyle(ChatFormatting.AQUA), false);
        return count;
    }

    private static boolean isRelevant(Block block) {
        // [B11 雪藏] 原判定还包含 B11 无线流体传输器的两个注册名。
        return block == ModBlocks.WIRELESS_STRESS_BRIDGE_SENDER.get()
                || block == ModBlocks.WIRELESS_STRESS_BRIDGE_RECEIVER.get();
        // || block == ModBlocks.WIRELESS_FLUID_TRANSMITTER_SENDER.get()
        // || block == ModBlocks.WIRELESS_FLUID_TRANSMITTER_RECEIVER.get();
    }

    private static String describe(WirelessDevice device) {
        String kind;
        if (device instanceof WirelessStressBridgeBlockEntity bridge) {
            kind = bridge.isOutputSide() ? "B10 输出端" : "B10 接收端";
        // [B11 雪藏] B11 无线流体传输器已整体注释
        // } else if (device instanceof WirelessFluidTransmitterBlockEntity transmitter) {
        //     kind = transmitter.isOutputSide() ? "B11 输出端" : "B11 接收端";
        } else {
            kind = device.getClass().getSimpleName();
        }
        String frequency = device instanceof IFrequencyBindable bindable
                ? (bindable.hasFrequency() ? String.valueOf(bindable.getFrequency()) : "未绑定")
                : "?";
        return kind + " " + device.getBlockPos()
                + " 频率=" + frequency
                + " 可见性=" + device.getVisibility()
                + " owner=" + device.getOwner()
                + " isAlive=" + device.isAlive()
                + " key=" + device.getKey();
    }

    private static void log(String line) {
        AvaritiaSingularityStressMod.LOGGER.info(PREFIX + line);
    }
}
