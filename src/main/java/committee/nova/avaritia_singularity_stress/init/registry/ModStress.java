package committee.nova.avaritia_singularity_stress.init.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;

import committee.nova.avaritia_singularity_stress.content.extreme_water_wheel.ExtremeWaterWheelBlockEntity;

public final class ModStress {

    private ModStress() {
    }

    public static void register() {
        // B28 终焉水车:基础容量(1RPM 基准)= Create 水车基础值 × 5(默认 32→160),随其配置联动;
        // 实际发电容量由 BE 覆写 calculateAddedStressCapacity 给出(有流体再翻倍),此处供静态查询/配置显示。
        BlockStressValues.CAPACITIES.registerProvider(block -> {
            if (block == ModBlocks.EXTREME_WATER_WHEEL.get()) {
                return () -> BlockStressValues.getCapacity(AllBlocks.WATER_WHEEL.get())
                        * ExtremeWaterWheelBlockEntity.BASE_CAPACITY_FACTOR;
            }
            return null;
        });
        // B28 终焉水车恒 256RPM(仅用于护目镜/提示,实际转速由 BE getGeneratedSpeed 决定)。
        // 用惰性 provider,避免构造期急切调用 .get() 触发"未绑定值"(注册表此时尚未绑定)。
        BlockStressValues.RPM.registerProvider(block -> {
            if (block == ModBlocks.EXTREME_WATER_WHEEL.get()) {
                return new BlockStressValues.GeneratedRpm(256, false);
            }
            return null;
        });
        // 惰性注册:Create 在实际计算应力时才回调 provider,
        // 那时方块注册表已绑定,这里才能安全地调用 get()。
        BlockStressValues.IMPACTS.registerProvider(block -> {
            if (block == ModBlocks.EXTREME_MECHANICAL_CRAFTER.get()) {
                return () -> 8.0D;
            }
            // 终焉锻造器同样消耗应力
            if (block == ModBlocks.EXTREME_SMITHING_CRAFTER.get()) {
                return () -> 8.0D;
            }
            // B26 炽骨立方合成器:沿用现有合成器的应力占用
            if (block == ModBlocks.BLAZE_CUBE_CRAFTER.get()) {
                return () -> 8.0D;
            }
            // B27 终焉钻头:耗能 256 SU/RPM(总耗能 = 256 × 转速)
            if (block == ModBlocks.EXTREME_DRILL.get()) {
                return () -> 256.0D;
            }
            // B6 奇点压缩器:压缩占用基准 32 SU/RPM(压缩量随转速:实际应力 = 32 × |转速|)。
            // 实际占用由 BlockEntity 覆写 calculateStressApplied 动态给出
            // (32 + 品控损耗 8,B14 稳定器在网时为纯 32);
            // 此注册值仅供应力表与第三方查询显示基准。
            if (block == ModBlocks.SINGULARITY_ROTATION_SPEED_CONTROLLER.get()) {
                return () -> 32.0D;
            }
            // B14 奇点稳定器:自身不消耗应力。
            if (block == ModBlocks.SINGULARITY_STABILIZER.get()) {
                return () -> 0.0D;
            }

            // ===== B2 收集器:各档位能耗(SU/RPM) =====
            if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR.get()) {
                return () -> 32.0D;
            }
            if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get()) {
                return () -> 64.0D;
            }
            if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_ELITE.get()) {
                return () -> 128.0D;
            }
            if (block == ModBlocks.KINETIC_NEUTRON_COLLECTOR_ULTIMATE.get()) {
                return () -> 256.0D;
            }

            // ===== B4 压缩机:全档位统一 32 SU/RPM =====
            if (block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR.get()
                    || block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_DENSE.get()
                    || block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ELITE.get()
                    || block == ModBlocks.KINETIC_NEUTRON_COMPRESSOR_ULTIMATE.get()) {
                return () -> 32.0D;
            }
            return null;
        });
    }
}
