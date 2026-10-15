# Create 传动杆与动能连接调查

> 调查范围：Create 6.0.10 / Minecraft 1.21.1，以及本工程 `AvaritiaSingularityStress` 当前实现。
>
> 目的：在修改 8 台动能机器的模型面、传动杆连接面或输入方向之前，先固定 Create 的真实语义，避免把模型方向、shaft 连接和 `source` 上游链混为一谈。
>
> 本文是调查与设计基线，**不包含任何实现修改**。

## 1. 核心结论

### 1.1 Create 没有通用的“输入端/输出端”概念

Create 动能系统判断的是：

1. 两个方块是否相邻并处于同一条轴线上；
2. 起点方块是否允许朝向邻居的那个面连接 shaft；
3. 邻居方块是否允许朝向起点的相反面连接 shaft；
4. 连接后由速度传播逻辑决定谁成为谁的 `source`。

因此：

- `hasShaftTowards(...)` 表达“这个面有没有轴连接”；
- 它不表达“这个面是输入端还是输出端”；
- `source` 表达“当前方块采用哪个上游动能实体作为传播来源”；
- `source` 不是固定的南面、北面，也不是一个 input/output 端口字段。

### 1.2 模型 JSON 的 `north/south/east/west` 只是模型本地面

模型中的纹理键只决定渲染面。它不会自动改变：

- `getRotationAxis(BlockState)`；
- `hasShaftTowards(...)`；
- `RotationPropagator` 的连接判定；
- `KineticBlockEntity.source`。

模型旋转由 blockstate 的 `x/y` 变换完成，物理连接由 Java 方块状态和 `hasShaftTowards` 完成。两者必须通过同一套状态设计保持一致，不能只改其中一边。

### 1.3 当前 8 台 B2/B4 机器已经是单面 shaft 连接

收集器和压缩机都继承 `HorizontalKineticBlock`，并且当前实现为：

```java
@Override
public Axis getRotationAxis(BlockState state) {
    return state.getValue(HORIZONTAL_FACING).getAxis();
}

@Override
public boolean hasShaftTowards(LevelReader world, BlockPos pos,
        BlockState state, Direction face) {
    return face == state.getValue(HORIZONTAL_FACING).getOpposite();
}
```

所以它们不是同轴两端都能接的标准 Shaft，而是：

```text
真实 shaft 连接面 = HORIZONTAL_FACING.opposite()
旋转轴            = HORIZONTAL_FACING.axis
```

### 1.4 当前 B6 和 B14 不是同一种连接语义

- B6 继承 Create `SpeedControllerBlock`，使用 `HORIZONTAL_AXIS`，同轴两侧都能连接；上方大齿轮属于额外的速度控制器特殊连接。
- B14 继承 `AbstractSimpleShaftBlock`，使用 `AXIS`，同轴两侧都能连接；它是标准轴式方块。
- B6 的大齿轮识别依赖本工程的 `RotationPropagatorMixin`，不能假设仅靠继承就能获得原版控制器的所有硬编码行为。

---

## 2. 调查到的关键 Create 类

以下类均位于 `com.simibubi.create.content.*`，不属于 Create 的稳定公开 API；升级 Create 时必须重新核对。

| 类 | 作用 | 本工程相关性 |
|---|---|---|
| `IRotate` | 定义 `hasShaftTowards` 与 `getRotationAxis` | B2/B4/B6/B14 都依赖 |
| `KineticBlock` | 动能方块基类、生命周期入口 | 所有 kinetic block 的基础 |
| `HorizontalKineticBlock` | 提供 `HORIZONTAL_FACING` 和水平放置 | B2/B4 |
| `DirectionalKineticBlock` | 提供六向 `FACING` 放置语义 | Create 风扇/钻头类参考 |
| `RotatedPillarKineticBlock` | 提供 `AXIS` 轴状态 | B14 父类链 |
| `AbstractShaftBlock` | 标准双面轴连接 | B14 父类链 |
| `ShaftBlock` | 标准 shaft 方块、扳手、支架、延长 | 轴模型参考 |
| `RotationPropagator` | 相邻连接、速度传播、source 更新 | 物理连接核心 |
| `KineticNetwork` | members/sources/stress/capacity 网络 | ssu 引擎读取产能 |
| `SpeedControllerBlock` | 控制器轴、上方大齿轮放置辅助 | B6 父类 |
| `SpeedControllerBlockEntity` | target speed 和大齿轮特殊速度传播 | B6 父类 |

关键源码参考根目录：

```text
C:\Users\12501\MCreatorWorkspaces\AvaritiaSingularityStress\allapis\Create-mc1.21.1-dev\src\main\java\com\simibubi\create
```

---

## 3. `IRotate` 的真实契约

文件：

```text
allapis/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/base/IRotate.java
```

关键方法：

```java
public boolean hasShaftTowards(
        LevelReader world,
        BlockPos pos,
        BlockState state,
        Direction face);
```

```java
public Axis getRotationAxis(BlockState state);
```

语义：

- `getRotationAxis` 返回该状态下的物理旋转轴；
- `hasShaftTowards` 判断指定方块面是否存在可连接轴；
- 接口没有 `getInputFace`、`getOutputFace`、`sourceDirection` 或 `transmissionDirection`；
- 如果需要单面连接，必须在具体 Block 的 `hasShaftTowards` 中明确实现。

Create 的 `KineticBlock` 默认 `hasShaftTowards` 返回 `false`；继承类必须自己定义连接面。

---

## 4. RotationPropagator 的连接判定

文件：

```text
allapis/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/RotationPropagator.java
```

相邻方块沿 `direction` 连接时，核心条件等价于：

```java
alignedAxes
    && definitionFrom.hasShaftTowards(
            world, fromPos, stateFrom, direction)
    && definitionTo.hasShaftTowards(
            world, toPos, stateTo, direction.getOpposite());
```

也就是说，连接双方必须同时同意：

```text
from 允许朝向 to 的面
to   允许朝向 from 的面
```

如果只有一方返回 `true`，标准轴连接不会建立。

### 4.1 单面连接示例

假设当前方块在 `pos`，邻居在 `pos.relative(Direction.NORTH)`：

```text
当前方块必须允许 NORTH 面；
邻居方块必须允许 SOUTH 面。
```

### 4.2 普通轴的速度传播

标准轴连接通常把上游理论速度乘以连接类型的速度修正系数：

```java
return from.getTheoreticalSpeed() * rotationSpeedModifier;
```

普通 Shaft 的修正通常为 1。Create 不会因为某个方块“看起来像输入口”而自动赋予 input/output 语义。

### 4.3 自定义传播钩子

`KineticBlockEntity` 内部还有：

```java
public float propagateRotationTo(...);
public boolean isCustomConnection(...);
```

它们是 Create 内部实现扩展点，不是稳定公开 API。除非确实需要非标准连接，否则不应优先使用；B2/B4 的方向需求只需要正确的 `hasShaftTowards` 和 `getRotationAxis`。

---

## 5. `source` 的真实语义

文件：

```text
allapis/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/base/KineticBlockEntity.java
```

字段：

```java
public @Nullable BlockPos source;
```

`source` 保存当前 `KineticBlockEntity` 所采用的上游传播实体的世界坐标。它是传播树父节点指针，不是：

- 固定的输入方向；
- 固定的输出方向；
- 模型的 front 面；
- 某一根传动杆的永久角色。

速度传播可能根据速度源竞争改变 source。断开、替换方块或重新传播时，source 也会被清理和重新设置。

本工程的 `SingularityStress.isChainLinked(...)` 沿 source 链回溯 B6，因此它判断的是“上游传播链是否包含 B6”，不是“机器是否连接在世界南面”。

---

## 6. 标准双面轴与单面机器参考

### 6.1 标准双面轴：AbstractShaftBlock

文件：

```text
allapis/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/simpleRelays/AbstractShaftBlock.java
```

连接逻辑：

```java
return face.getAxis() == state.getValue(AXIS);
```

结果：

```text
AXIS=X -> EAST/WEST 都可连接
AXIS=Y -> UP/DOWN 都可连接
AXIS=Z -> NORTH/SOUTH 都可连接
```

B14 通过父类继承这一语义。

### 6.2 双面水平机器：MechanicalPressBlock

文件：

```text
allapis/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/press/MechanicalPressBlock.java
```

连接逻辑：

```java
return face.getAxis() == state.getValue(HORIZONTAL_FACING).getAxis();
```

它的 `HORIZONTAL_FACING` 主要决定水平轴和视觉朝向，不代表只有一端是输入口。

### 6.3 单面机器：EncasedFan / Drill / CuckooClock

这些 Create 机器使用类似逻辑：

```java
return face == state.getValue(FACING).getOpposite();
```

或：

```java
return face == state.getValue(HORIZONTAL_FACING).getOpposite();
```

常见语义是：

```text
工作面 = FACING
shaft 面 = FACING.opposite()
旋转轴 = FACING.axis
```

这也是当前 B2/B4 的连接语义。

---

## 7. 当前工程的 B2/B4 传动状态

### 7.1 Block 类

收集器：

```text
src/main/java/committee/nova/avaritia_singularity_stress/content/kinetic_neutron_collector/KineticNeutronCollectorBlock.java
```

压缩机：

```text
src/main/java/committee/nova/avaritia_singularity_stress/content/kinetic_neutron_compressor/KineticNeutronCompressorBlock.java
```

两者都：

```java
extends HorizontalKineticBlock
implements IBE<...BlockEntity>
```

两者都：

```java
@Override
public Axis getRotationAxis(BlockState state) {
    return state.getValue(HORIZONTAL_FACING).getAxis();
}

@Override
public boolean hasShaftTowards(LevelReader world, BlockPos pos,
        BlockState state, Direction face) {
    return face == state.getValue(HORIZONTAL_FACING).getOpposite();
}
```

### 7.2 8 个注册方块共用连接实现

```text
4 个收集器 -> KineticNeutronCollectorBlock
4 个压缩机 -> KineticNeutronCompressorBlock
```

每一族四档共用一个 BlockEntity 类型；档位通过注册方块反查 Tier。后续如果只修改这两个 Block 类，会同时影响 8 台机器。

### 7.3 放置方向

`HorizontalKineticBlock` 默认放置方向来自：

```java
context.getHorizontalDirection().getOpposite()
```

当前没有把机器固定到世界 `SOUTH` 的逻辑。对于单面连接：

```text
facing=NORTH -> shaft=SOUTH
facing=EAST  -> shaft=WEST
facing=SOUTH -> shaft=NORTH
facing=WEST  -> shaft=EAST
```

这里的 `facing` 是状态语义，不能直接等同于模型的某一个世界面，必须结合 blockstate 的 Y 旋转看。

---

## 8. 当前 8 台机器的模型生成状态

文件：

```text
src/main/java/committee/nova/avaritia_singularity_stress/init/provider/ModBlockStateProvider.java
```

当前 `generateMachine(...)` 使用：

```java
.withExistingParent(base + blockName(block), mcLoc("block/cube"))
.texture("particle", modLoc(base + prefix + "_front"))
.texture("up", modLoc(base + topFile))
.texture("down", modLoc(base + "frame"))
.texture("north", modLoc(base + prefix + "_front"))
.texture("south", modLoc(base + "frame_back"))
.texture("east", modLoc(base + prefix + "_side_right"))
.texture("west", modLoc(base + prefix + "_side_left"))
```

当前严格映射为：

```text
particle = front
up       = top
 down    = frame
north    = front
south    = frame_back
east     = side_right
west     = side_left
```

注意：用户之前提出的目标是 `south = frame`。当前调查到的工程代码是 `south = frame_back`，因此后续实现前必须确认 `frame` 与 `frame_back` 是否是两张不同的资源，以及 south 面到底应显示哪一张。本文不擅自改动这一点。

### 8.1 Blockstate 旋转

8 个 B2/B4 blockstate 都只有四个水平 facing 变体：

```text
facing=north -> y=0
facing=east  -> y=90
facing=south -> y=180
facing=west  -> y=270
```

没有 `pointing` 状态，也没有上下方向变体。

blockstate 的 `y` 只负责旋转模型，不改变 `hasShaftTowards` 的返回值。真实 shaft 面仍由 `HORIZONTAL_FACING.opposite()` 决定。

### 8.2 方向表（模型与物理必须分开验证）

当前模型基础状态中：

```text
本地 north 面 = front
本地 south 面 = frame_back
```

物理连接状态中：

```text
shaft 面 = HORIZONTAL_FACING.opposite()
旋转轴   = HORIZONTAL_FACING.axis
```

设计文档不得直接写成“模型 south 就是输入面”；必须明确：

1. 模型本地面；
2. blockstate 旋转；
3. 世界面；
4. `hasShaftTowards` 的实际返回面。

---

## 9. B6 奇点压缩器的传动杆语义

文件：

```text
src/main/java/committee/nova/avaritia_singularity_stress/content/singularity_rotation_speed_controller/SingularityRotationSpeedControllerBlock.java
```

B6 继承：

```text
SingularityRotationSpeedControllerBlock
 -> Create SpeedControllerBlock
 -> HorizontalAxisKineticBlock
 -> KineticBlock
```

B6 使用 `HORIZONTAL_AXIS`，不是 `HORIZONTAL_FACING`：

```text
axis=x -> 旋转轴 X，EAST/WEST 两侧普通 shaft 连接
axis=z -> 旋转轴 Z，NORTH/SOUTH 两侧普通 shaft 连接
```

### 9.1 上方大齿轮是特殊连接，不是普通传动杆输入面

Create 的 `isLargeCogToSpeedController(...)` 额外要求：

- 起点是水平大齿轮；
- 大齿轮在控制器正上方；
- 大齿轮轴是水平轴；
- 控制器 `HORIZONTAL_AXIS` 与大齿轮轴不同。

本工程的：

```text
src/main/java/committee/nova/avaritia_singularity_stress/mixin/RotationPropagatorMixin.java
```

只是在 Create 硬编码的原版控制器判断上扩展自定义 B6。它不改变 B6 普通 shaft 的双面轴连接语义。

### 9.2 继承不等于完整兼容

Create `SpeedControllerBlock` 内部仍存在对：

```java
AllBlocks.ROTATION_SPEED_CONTROLLER
```

的硬编码，尤其是 PlacementHelper 和特殊传播路径。后续如果修改 B6 放置辅助、大齿轮交互或传动杆渲染，必须单独验证：

- 大齿轮能否正确放置；
- B6 是否获得正确 `HORIZONTAL_AXIS`；
- Mixin 是否仍匹配目标方法；
- bracket/shaft 是否重复渲染；
- source 链是否按预期指向 B6。

---

## 10. B14 奇点稳定器的传动语义

文件：

```text
src/main/java/committee/nova/avaritia_singularity_stress/content/singularity_stabilizer/SingularityStabilizerBlock.java
```

B14 继承：

```text
SingularityStabilizerBlock
 -> AbstractSimpleShaftBlock
 -> AbstractShaftBlock
 -> RotatedPillarKineticBlock
 -> KineticBlock
```

它使用 `AXIS`，并继承标准双面 shaft 连接：

```text
AXIS=X -> EAST/WEST
AXIS=Y -> UP/DOWN
AXIS=Z -> NORTH/SOUTH
```

B14 当前动态渲染职责分为：

- Flywheel 可用时：`SingleAxisRotatingVisual` / rotor partial；
- Flywheel 不可用时：BER fallback；
- 静态 blockstate 不应再次绘制完整 rotor，否则会双重渲染。

当前调查还发现：B14 没有生成 blockstate，且已有生成方法引用的模型路径与实际目录模型不一致。后续恢复 B14 资源时必须单独设计，不能顺手套用 B2/B4 的 cube 生成逻辑。

---

## 11. 模型方向与输入面设计决策

### 11.1 如果只要求修改视觉纹理

只改 `generateMachine(...)` 的纹理键映射即可；物理连接不变。这个方案不会让 south 成为真实输入面。

### 11.2 如果要求“输入面随机器朝向旋转”

当前 B2/B4 已经采用单面连接：

```java
face == HORIZONTAL_FACING.opposite()
```

这通常是正确的 Create 单面语义。应保持：

```java
getRotationAxis = HORIZONTAL_FACING.axis
hasShaftTowards = HORIZONTAL_FACING.opposite
```

然后再根据实际模型本地面和 blockstate Y 旋转，验证视觉 front/后侧与物理 shaft 面是否一致。

### 11.3 如果要求“永远固定世界 SOUTH 物理输入”

不能只改模型 JSON 或默认放置方向。需要重新设计：

```java
hasShaftTowards(...) == face == Direction.SOUTH
getRotationAxis(...) == Direction.Axis.Z
```

同时必须处理：

- `HORIZONTAL_FACING` 是否仍然保留；
- EAST/WEST 状态是否还允许有效传动；
- 相邻方块的反向面是否也同意连接；
- 玩家旋转、镜像、放置辅助是否仍符合预期。

这会让机器只能连接世界南面，并可能破坏四向方块的自然串联。当前不建议在没有实机拓扑验证前采用。

### 11.4 不要把“输入口”写进 source 链

如果需求只是“哪一面接传动杆”，改 Block 的 `hasShaftTowards`；如果需求是“奇点只从某个上游传播”，改 `source` 链逻辑。两者不能互相替代。

---

## 12. 当前工程已知风险

1. `IRotate`、`KineticBlock`、`RotationPropagator`、`KineticNetwork`、`SpeedControllerBlock` 都是 Create 内部类，不是稳定公开 API。
2. B2/B4 的 `getRotationAxis` 是整条水平轴，但 `hasShaftTowards` 是单面；不能把它们混成双面轴。
3. 模型本地 `north/south` 与世界方向要经过 blockstate Y 旋转，不能直接凭 JSON 键判断世界面。
4. `source` 是传播树上游，不是输入端字段。
5. Create `RotationPropagator` 需要连接双方都返回 `true`，单边返回不会建立普通轴连接。
6. B6 上方大齿轮连接依赖自定义 Mixin，且 SpeedController 内部仍有原版方块硬编码。
7. B14 当前缺少生成的 blockstate；其静态模型、rotor partial、BER、Flywheel 的职责必须分离。
8. 模型包装或 CT 处理时，`ModifyBakingResult.getModels()` 的顶层 key 是方块注册名和状态变体，不是模型文件路径。
9. 状态变化导致旋转轴改变时，必须让 Create 清理旧 network/source 并重新传播，不能只替换视觉状态。
10. 当前 B14 注释中的“全网生效”与 source 链实现可能存在语义差异；后续应明确是全网络还是某个 B6 上游链路。

---

## 13. 后续实现前的验证矩阵

### A. B2/B4 连接面

- [ ] 对 `facing=north/east/south/west` 分别列出 `hasShaftTowards` 的唯一 `true` 面。
- [ ] 验证 `getRotationAxis` 与该面处于同一轴。
- [ ] 验证对方 Shaft 的相反面也返回 `true`。
- [ ] 测试机器接在 shaft 两侧时，只有设计中的一侧建立连接。
- [ ] 测试反向放置后输入面是否随 facing 正确旋转。
- [ ] 测试方块旋转、镜像和重新放置后的 network/source 清理。

### B. 模型纹理与 blockstate

- [ ] 确认 `frame` 与 `frame_back` 是否应该是同一张图。
- [ ] 确认基础模型的本地 north 是否确实代表 front。
- [ ] 逐个检查 `facing` 四个 Y 旋转后的世界 front 面。
- [ ] 用渲染截图核对 front、back、side_left、side_right 与实际贴图文件。
- [ ] 不用模型面名推断物理 shaft 面。
- [ ] 重新运行 datagen 后检查 8 个 blockstate 和 8 个模型 JSON。

### C. B6

- [ ] 普通横向 shaft 连接：X/Z 两轴两侧是否按 Create 规则工作。
- [ ] 上方水平大齿轮是否被 Mixin 识别。
- [ ] 大齿轮轴与 B6 `HORIZONTAL_AXIS` 垂直时才连接。
- [ ] 非大齿轮、竖直大齿轮、侧面大齿轮不误触发特殊连接。
- [ ] PlacementHelper 是否仍被原版 `AllBlocks.ROTATION_SPEED_CONTROLLER` 硬编码限制。
- [ ] B6 shaft fallback 与 Flywheel visual 不发生双重绘制。

### D. B14

- [ ] 补齐 blockstate 前先确定静态壳和 rotor partial 的职责。
- [ ] 修正生成器路径与实际 `models/block/singularity_stabilizer/` 目录。
- [ ] AXIS=X/Y/Z 三种状态分别验证视觉旋转与物理轴一致。
- [ ] Flywheel 开启/关闭各测一次，确认只渲染一个 rotor。
- [ ] B14 两端 shaft 连接和断开后 network/source 是否恢复。

### E. source 与奇点

- [ ] 明确 source 链只表示上游传播，不表示固定方向。
- [ ] B6 输入侧机器不应因模型 front 或邻接位置误判为输出侧。
- [ ] B6 输出侧机器 source 链能稳定回溯到 B6。
- [ ] 长链、跨区块、未加载区块、多个速度源分别验证。
- [ ] B14 的实际生效范围与注释一致：全网或对应 B6 上游链，二者必须统一。

### F. 最终验收

- [ ] `compileJava`。
- [ ] `runData`，确认出现 `All providers took`。
- [ ] 逐个打开 8 台机器，检查模型方向和 GUI。
- [ ] 使用 Create 传动杆/齿轮做正向、反向、断开测试。
- [ ] 重启世界后再次测试 network/source。
- [ ] 检查日志中无 Mixin injection error、missing model、missing blockstate。

---

## 14. 调查结论与当前建议

当前最安全的后续顺序是：

1. 先确认 `frame` / `frame_back` 的资源意图；
2. 固定 B2/B4 的模型本地面与 `facing` 旋转关系；
3. 保持当前 `hasShaftTowards = HORIZONTAL_FACING.opposite()`，不要把视觉 south 直接改成物理固定 SOUTH；
4. 单独设计 B6 的普通 shaft 与上方大齿轮两条连接路径；
5. 单独修复 B14 blockstate 和静态模型路径；
6. 通过上述验证矩阵后，再实施 Java/JSON 修改。

在这些问题没有通过实机拓扑验证前，不建议直接把 8 台机器改成固定世界 SOUTH 输入，也不建议把 `source` 链改造成方向判断。