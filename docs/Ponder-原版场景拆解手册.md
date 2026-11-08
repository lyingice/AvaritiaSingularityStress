# Ponder 原版场景拆解手册

> 《Ponder 保姆级使用手册》的扩展卷。
> 主手册讲「怎么写」，这一份讲「机械动力原版是怎么写的」，并把每个动作换成本模组的方块演示一遍。
> 目标：你看完就能照着自己的机器开写，不用再对着 Create 的源码猜。

适用环境与主手册一致：Minecraft 1.21.1 / NeoForge 21.1.232 / Create 6.0.10-280 / Ponder 1.0.82。

---

## 0. 怎么用这份文档

三个入口，按你现在的状态挑：

| 你现在的状态 | 从这看 |
| --- | --- |
| 一个场景都还没写 | 先回主手册第 1 章跑通，再回来 |
| 会写骨架，但不知道该演什么 | 第 2 章「十二个套路」，每个套路都有可抄代码 |
| 想找某个原版机器的分镜 | 第 1 章全量清单定位，再到第 3 章按族查 |

**关于「原版参考」的标注方式**：本文出现的原版场景 id 都是从 `AllCreatePonderScenes` 的字节码里抽出来的真实注册项，不是猜的。但「这个场景具体演了哪几步」需要逐个反编译才能确认，所以文中凡是我没有逐帧核对的地方，会写成「同类参考」，不写成定论。

---

## 1. 全量清单

Create 6.0.10 一共注册 **129 个** Ponder 场景（跨组件重复的算多次），去重后 **118 个唯一 id**，分属 **68 个族**。

清单由字节码自动抽取，命令在附录 A。

| 族 | 场景数 | 场景 id |
| --- | --- | --- |
| `high_logistics` | 16 | `high_logistics/chain_conveyor`<br>`high_logistics/package_frogport`<br>`high_logistics/package_postbox`<br>`high_logistics/packager`<br>`high_logistics/packager_address`<br>`high_logistics/stock_link`<br>`high_logistics/stock_ticker`<br>`high_logistics/stock_ticker_address`<br>`high_logistics/redstone_requester`<br>`high_logistics/repackager`<br>`high_logistics/table_cloth`<br>`high_logistics/factory_gauge_restocker`<br>`high_logistics/factory_gauge_recipe`<br>`high_logistics/factory_gauge_crafting`<br>`high_logistics/factory_gauge_links` |
| `funnels` | 6 | `funnels/brass`<br>`funnels/direction`<br>`funnels/compat`<br>`funnels/redstone`<br>`funnels/transposer` |
| `cog` | 4 | `cog/speedup`<br>`cog/encasing` |
| `shaft` | 3 | `shaft/relay`<br>`shaft/encasing` |
| `mechanical_press` | 3 | `mechanical_press/pressing`<br>`mechanical_press/compacting` |
| `weighted_ejector` | 3 | `weighted_ejector/eject`<br>`weighted_ejector/split`<br>`weighted_ejector/redstone` |
| `mechanical_crafter` | 3 | `mechanical_crafter/setup`<br>`mechanical_crafter/connect`<br>`mechanical_crafter/covers` |
| `tunnels` | 3 | `tunnels/andesite`<br>`tunnels/brass`<br>`tunnels/brass_modes` |
| `gantry` | 3 | `gantry/redstone`<br>`gantry/direction`<br>`gantry/subgantry` |
| `train_signal` | 3 | `train_signal/placement`<br>`train_signal/signaling`<br>`train_signal/redstone` |
| `valve_handle` | 2 | `valve_handle` |
| `chain_drive` | 2 | `chain_drive/relay`<br>`chain_drive/gearshift` |
| `gauges` | 2 | `gauges/gauge` |
| `mechanical_mixer` | 2 | `mechanical_mixer/mixing` |
| `chute` | 2 | `chute/downward`<br>`chute/smart`<br>`chute/upward` |
| `chassis` | 2 | `chassis/linear_group`<br>`chassis/radial`<br>`chassis/linear_attachment` |
| `mechanical_piston` | 2 | `mechanical_piston/piston_pole`<br>`mechanical_piston/modes` |
| `elevator_pulley` | 2 | `elevator_pulley/elevator`<br>`elevator_pulley/multi_rope` |
| `sail` | 2 | `sail`<br>`sail_frame` |
| `mechanical_roller` | 2 | `mechanical_roller/clear_and_pave`<br>`mechanical_roller/fill` |
| `fluid_pipe` | 2 | `fluid_pipe/flow`<br>`fluid_pipe/encasing`<br>`fluid_pipe/interaction` |
| `train_station` | 2 | `train_station/assembly`<br>`train_station/schedule` |
| `display_link` | 2 | `display_link`<br>`display_link_redstone` |
| 其余单场景族 | 1 | `gearbox` `clutch` `gearshift` `sequenced_gearshift` `fan/direction` `fan/processing` `creative_motor` `creative_motor_mojang` `water_wheel` `large_water_wheel` `hand_crank` `speed_controller` `millstone` `crushing_wheel` `basin` `empty_blaze_burner` `blaze_burner` `depot` `item_vault/storage` `super_glue` `sticker` `mechanical_arm/setup` `windmill_bearing/source` `cart_assembler/anchor` `cart_assembler/dual` `cart_assembler/modes` `cart_assembler/rails` `portable_interface/transfer` `redstone_contact` `mechanical_saw/processing` `mechanical_drill/breaker` `deployer/filter` `deployer/modes` `deployer/processing` `deployer/redstone` `deployer/contraption` `harvester` `plough` `contraption_controls` `smart_pipe` `fluid_tank/storage` `fluid_tank/sizes` `spout` `item_drain` `pulse_extender` `pulse_repeater` `powered_latch` `powered_toggle_latch` `analog_lever` `nixie_tube` `redstone_link` `rose_quartz_lamp` `pulse_timer` `smart_observer` `threshold_switch` `train_schedule` `train_controls` `train_observer` `display_board` `steam_whistle` `steam_engine` `chain_conveyor` |

**怎么读这张表**：`某某/某某` 这种带斜杠的是 Create 的资源路径风格，注册时就是字符串；不带斜杠的是一级 id。你自己的场景用不用斜杠都行，`addStoryBoard` 的第二个参数是纯 path。

---

## 2. 十二个套路

下面每个套路都按同一个结构写：**它解决什么问题 → 原版哪些场景在干这个 → 本模组可抄代码 → 容易踩的坑**。

代码里的 `ModBlocks` / `ModItems` 就是本模组的注册表，`CreateSceneBuilder` 是 Create 对 Ponder 的增强包装（`com.simibubi.create.foundation.ponder.CreateSceneBuilder`）。

### 2.1 最小可用场景

**解决什么**：把「一个方块 + 一句话」演出来。这是 90% 的思索场景的真身。

**同类参考**：`shaft/relay`、`clutch`、`gearbox`、`redstone_contact` 这类单方块说明基本都是这个骨架。

```java
private static void minimal(SceneBuilder builder, SceneBuildingUtil util) {
    CreateSceneBuilder scene = new CreateSceneBuilder(builder);

    scene.title("demo_minimal", "Demo");
    scene.configureBasePlate(0, 0, 5);   // 底板 5x5，原点 (0,0)
    scene.idle(5);

    // 让底板先出现
    scene.world().showSection(util.select().layer(0), Direction.UP);
    scene.idle(10);

    // 主体方块浮现
    Selection body = util.select().position(2, 1, 2);
    scene.world().showSection(body, Direction.DOWN);
    scene.idle(10);

    // 一句话，钉在方块上
    scene.overlay().showText(60)
            .text("A single block can already be worth explaining")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().centerOf(2, 1, 2));
    scene.idle(70);

    scene.markAsFinished();
}
```

**坑**：

- `configureBasePlate(0, 0, 5)` 的 5 是**底板边长**，不是坐标。底板的方块会铺在 y=-1，你的机器从 y=0 开始算。
- `showSection` 只显示 **`.nbt` 里已经存在**的方块。结构里没有的方块，`showSection` 什么都不会发生——这是本仓库踩过的坑，见 §2.3。

### 2.2 结构浮现的三个方向

**解决什么**：同一个 `showSection`，靠 `Direction` 参数做出「从下往上长」「从上方落下」「从侧面滑入」三种观感。

**同类参考**：`cog/encasing`、`shaft/encasing`、`mechanical_crafter/setup`。

```java
Selection machine = util.select().fromTo(1, 1, 2, 3, 3, 2);   // 立着的 3x3 墙

scene.world().showSection(machine, Direction.DOWN);    // 从上方落下
scene.idle(20);

scene.world().hideSection(machine, Direction.UP);      // 向上收走
scene.idle(20);

scene.world().showSection(machine, Direction.SOUTH);   // 从北面滑入
scene.idle(20);
```

**方向怎么选**：

| Direction | 观感 | 适合 |
| --- | --- | --- |
| `UP` | 从下方长出来 | 底板、地面上的机械 |
| `DOWN` | 从上方落下 | 悬空的、刚装好的部件 |
| `NORTH/SOUTH/EAST/WEST` | 从侧面滑进来 | 横向排列的流水线 |
| `Direction.Axis` 版本 | 沿轴抽动 | 轴、活塞杆这类有明确轴向的 |

### 2.3 替换方块：装壳的标准写法

**解决什么**：把结构里预放的 A 方块换成 B 方块，并且**保住转速**。

**同类参考**：`shaft/encasing`、`cog/encasing`、`belt/encasing`、`fluid_pipe/encasing` —— 原版所有「装壳」类场景都是这个模式。本仓库的 `blaze_cube_casing` / `crystal_matrix_casing` 也是照它抄的。

```java
BlockPos cogPos = util.grid().at(1, 1, 2);
Selection line = util.select().fromTo(0, 1, 2, 4, 1, 2);

// 1) 先让动力线转起来
scene.world().setKineticSpeed(line, 32);
scene.idle(10);

// 2) 换上包壳方块
BlockState encased = ModBlocks.BLAZE_CUBE_ENCASED_COGWHEEL.get()
        .defaultBlockState()
        .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X);
scene.world().setBlocks(util.select().position(cogPos), encased, true);

// 3) 必须重设转速——setBlocks 会把 BE 换成新的，转速丢了
scene.world().setKineticSpeed(line, 32);
scene.idle(20);
```

**三条硬规则**（都有字节码证据，Create 自己的 `shaftsCanBeEncased` 就这么写）：

1. **`setBlocks` 之后必须重新 `setKineticSpeed`**，否则新方块是静止的。
2. 动力线要连成 **≥3 格一条线**，孤立方块不会转。
3. 两个装壳点**隔开一格**，并排会视觉糊成一块。

### 2.4 改方块状态（不换方块）

**解决什么**：同一个方块换个朝向、换个档位，做「扳手拧一下」的效果。

**同类参考**：`gearbox`、`sequenced_gearshift`、`mechanical_piston/modes`、`tunnels/brass_modes`。

```java
BlockPos pos = util.grid().at(2, 1, 2);

scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40)
        .rightClick()
        .withItem(new ItemStack(AllItems.WRENCH.get()));
scene.idle(7);

scene.world().modifyBlock(pos, state -> state
        .setValue(ExtremeMechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH)
        .setValue(ExtremeMechanicalCrafterBlock.POINTING, Pointing.LEFT), false);
scene.idle(20);
```

**坑**：`modifyBlock` 第三个参数是「是否重新渲染」，一般给 `false`；给 `true` 会强制重建，视觉上会闪。

### 2.5 文字：最关键的表现手段

**解决什么**：一句话说清楚「这东西能干嘛」。Ponder 场景的节奏本质上是「一句台词 + 一段演示」。

**同类参考**：几乎所有场景都会用，`shaft/relay` 是教科书级的短小。

```java
scene.overlay().showText(70)
        .text("Blaze Cube Casing can be used to decorate cogwheels")
        .attachKeyFrame()                 // 这一帧可被拖拽定位
        .placeNearTarget()                // 贴着目标放，别飘在屏幕中间
        .pointAt(util.vector().centerOf(2, 1, 2));   // 指向具体方块
scene.idle(80);
```

**文案硬约束**（本仓库踩过两轮才定的）：

- 单条 ≤28 字，官方中位 18 字
- 标题 ≤14 字，且是**动作短语**（「给齿轮装壳」），不是方块名
- 没有感叹号、问号、破折号
- 不写「机制/系统/功能/实现/核心」，不写「你可以/让我们」
- 不替玩家下结论，不写空评价（非常/强大/轻松/完美）

**台词公式**：材料 + 可以用来 + 用途 + 目标。说了的必须演到，先演后讲。

### 2.6 手势提示：让玩家知道该点哪

**解决什么**：演「玩家会怎么操作」，而不是用文字说「右键它」。

**同类参考**：`deployer/filter`、`mechanical_arm/setup`、`cart_assembler/modes`、`display_link`。

```java
BlockPos pos = util.grid().at(2, 1, 2);

// 右键
scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 40)
        .rightClick()
        .withItem(new ItemStack(AllItems.WRENCH.get()));
scene.idle(7);

// 左键 / 滚动 / 拖拽
scene.overlay().showControls(util.vector().centerOf(pos), Pointing.LEFT, 30).leftClick();
scene.idle(30);
scene.overlay().showControls(util.vector().centerOf(pos), Pointing.RIGHT, 30).scroll();
scene.idle(30);
```

**坑**：`showControls` 的第二个参数是**指针方向**（`Pointing` 是 catnip 的枚举：UP/LEFT/DOWN/RIGHT），跟方块朝向的 `Pointing` 是同一个类，但语义完全不同——别混。

### 2.7 高亮、轮廓、连线

**解决什么**：让观众的眼睛跟着你的手指走。Ponder 里没有「鼠标」，靠这些临时图形指路。

**同类参考**：`display_link`、`redstone_link`、`stock_link` 这类「两个东西连起来」的场景。

```java
// 给整个选区画轮廓：第一个 Object 是"谁在指着"，随便给个新对象即可
scene.overlay().showOutline(PonderPalette.GREEN, new Object(),
        util.select().fromTo(1, 1, 2, 3, 1, 2), 60);
scene.idle(70);

// 两点连一条线
scene.overlay().showLine(PonderPalette.RED,
        util.vector().centerOf(0, 1, 2),
        util.vector().centerOf(4, 1, 2), 40);
scene.idle(50);

// 跟着方块包围盒走的轮廓
scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, new Object(),
        new AABB(util.grid().at(2, 1, 2)), 40);
scene.idle(50);

// 轮廓 + 文字一步到位（省一次 idle）
scene.overlay().showOutlineWithText(util.select().fromTo(1, 1, 2, 3, 1, 2), 60)
        .text("Line up the shafts along one axis")
        .pointAt(util.vector().centerOf(2, 1, 2));
scene.idle(70);
```

**`PonderPalette` 常用色**：`WHITE`（中性）、`RED`（禁止/错误）、`GREEN`（成功）、`BLUE`（信息）、`INPUT` / `OUTPUT`（配对输入输出）。

**坑**：`showOutline` 的第二个参数是个「归属对象」，同一个对象连续用两次，后一次会顶掉前一次。要同时显示多个轮廓，就每次 `new Object()`。

### 2.8 结构移动与旋转

**解决什么**：演「装好之后整个机构动起来」「旋转 90 度换个方向」这类整体位移。

**同类参考**：`gantry/*`、`mechanical_piston/*`、`elevator_pulley/*`。

```java
Selection arm = util.select().fromTo(1, 1, 1, 3, 3, 3);

// 整体平移：位移向量 + 耗时 tick
scene.world().moveSection(arm, util.vector().of(0, 2, 0), 20);
scene.idle(30);

// 整体旋转：绕 XYZ 的角度 + 耗时
scene.world().rotateSection(arm, 0, 90, 0, 30);
scene.idle(40);
```

**坑**：`moveSection` 只动**选区里的方块实体组**，不会重建；所以移动完再改方块状态是安全的，反过来（先改再移）可能拿到旧选区。

### 2.9 掉落物与实体

**解决什么**：演「东西掉下来」「产物被吐出来」。

**同类参考**：`depot`、`weighted_ejector/*`、`chute/*`。

```java
// 掉一个物品
scene.world().createItemEntity(
        util.vector().topOf(2, 1, 2),
        util.vector().of(0, 0.1, 0),
        new ItemStack(Items.DIAMOND));
scene.idle(30);
```

**坑**：物品实体参与物理，放太高的地方会弹开；`topOf` 再往上 0.2 左右比较稳。

### 2.10 特效与声音

**解决什么**：给「成功/失败/通上电」这类状态一个即时反馈。

**同类参考**：`redstone_contact`、`pulse_timer`、`threshold_switch`。

```java
scene.effects().indicateSuccess(util.grid().at(2, 1, 2));
scene.idle(20);

scene.effects().indicateRedstone(util.grid().at(2, 1, 2));
scene.idle(20);
```

### 2.11 镜头

**解决什么**：让观众看侧面、看背面、绕一圈。

**同类参考**：`crushing_wheel`、`windmill_bearing/source`、`mechanical_arm/setup`。

```java
// 绕 Y 轴转视角（Create 的 CreateSceneBuilder 提供）
scene.rotateCameraY(45);
scene.idle(20);
scene.rotateCameraY(-45);
scene.idle(20);
```

**坑**：镜头运动**不产生 idle 时间**，转完必须自己 `idle`，否则玩家看到的是一瞬间跳变。

### 2.12 收尾

```java
scene.markAsFinished();
```

没这句，场景最后会停在一个没有「结束」标记的状态，退出按钮行为会怪。

---

## 3. 重点族的逐个拆解

这一章只挑**结构最典型、复用率最高**的族。其余族看第 1 章清单定位，套路都在第 2 章里。

### 3.1 `shaft`（3 个场景）

| id | 演什么 | 本模组对照 |
| --- | --- | --- |
| `shaft/relay` | 单根传动杆接上动力开始转 | 你任何一个动能方块的最小演示 |
| `shaft/encasing` | 传动杆包上机壳，外观变化但照样转 | **`blaze_cube_casing` / `crystal_matrix_casing` 就是照这个写的** |
| `cog/large` | 大齿轮和小齿轮同台，直径不同 | 同上，齿轮箱场景 |

**从 `shaft/encasing` 学到的东西**（字节码级，Create 自己的顺序）：

1. `setKineticSpeed` 先把线转起来
2. `setBlocks` 换成包壳方块
3. **再 `setKineticSpeed` 一次**
4. 台词只讲「能用来装饰」，不讲原理

### 3.2 `cog`（4 个场景）

| id | 演什么 | 关键动作 |
| --- | --- | --- |
| `cog/small` | 小齿轮的转速换算（2:1） | 相邻齿轮反向、转速减半 |
| `cog/large` | 大齿轮的转速换算（4:1 面积） | 同上 |
| `cog/speedup` | 大带小加速 | 大小齿轮咬合 |
| `cog/encasing` | 齿轮装壳 | 同 `shaft/encasing` |

**给本模组的启示**：大齿轮和小齿轮必须**相邻咬合**才有转速变化；隔一格只演示外观。

### 3.3 `mechanical_crafter`（3 个场景）

| id | 演什么 |
| --- | --- |
| `mechanical_crafter/setup` | 阵列搭起来，摆成 3x3 |
| `mechanical_crafter/connect` | 用扳手把几台连成一个控制组 |
| `mechanical_crafter/covers` | 给合成格加盖子（只留部分输入） |

**这条正好是你导出的那场**（`extreme_mechanical_crafter`）。原版 `connect` 的动作顺序是：选中一台 → 右键 → 依次右键其余 → 整组朝向统一。本模组那场演的是单台朝向调整，比原版浅一层。

### 3.4 `funnels` / `tunnels` / `chute`（物流三兄弟，11 个场景）

共同点：**都是「输入端 → 传输 → 输出端」三点定位**，区别只在方向和过滤。

```java
// 物流场景的通用骨架
Selection input  = util.select().position(0, 1, 2);
Selection output = util.select().position(4, 1, 2);
scene.world().showSection(input, Direction.DOWN);
scene.idle(10);
scene.overlay().showLine(PonderPalette.OUTPUT,
        util.vector().centerOf(0, 1, 2), util.vector().centerOf(4, 1, 2), 40);
scene.idle(50);
scene.world().showSection(output, Direction.UP);
scene.idle(20);
```

### 3.5 `fluid_pipe`（3 个场景）

| id | 演什么 |
| --- | --- |
| `fluid_pipe/flow` | 流体在管里流动的方向 |
| `fluid_pipe/encasing` | 给管子包壳 |
| `fluid_pipe/interaction` | 用扳手改管道连接方式 |

**包壳类再次出现同一个套路**——`setBlocks` + 重设状态。这是 Create 里最高频的一个模式。

### 3.6 `high_logistics`（16 个场景，最大的一族）

这是 6.0 新增的「仓储物流」大包，占全部场景的 12%。它的共通结构是：

1. 先演一个**单点**（包、邮箱、股票链接）
2. 再演**两点之间**的连接（`stock_link`、`display_link`）
3. 最后演**规则**（requester 的数量、gauge 的配方）

**给本模组的启示**：如果你打算做物流类机制，照这个三段式走，别一上来就演网络。

---

## 附录 A：这份清单是怎么抽出来的

```bash
# 1) 数场景注册次数
javap -c -p -cp create-slim.jar \
  com.simibubi.create.infrastructure.ponder.AllCreatePonderScenes \
  | grep -c addStoryBoard        # → 129

# 2) 抽每个场景的 id（取每次 addStoryBoard 之前最近的 String 常量）
javap -c -p -cp create-slim.jar \
  com.simibubi.create.infrastructure.ponder.AllCreatePonderScenes \
  | grep -oE '// String [a-zA-Z0-9_/]+'
```

结果落在 `artifacts/create-scenes.txt`，本文第 1 章的表就是从它生成的。

## 附录 B：本手册没验证的部分

- **每个场景具体演了哪几步**：我只从 id 语义和 API 用法推断，没有逐个反编译逐帧核对。想看真身，用主手册第 11 章的反编译命令直接读 `*Scenes` 类。
- **原版场景的台词原文**：没抽。想看官方怎么说话，用 `node dsh-ponder-mode/ponder-lang.mjs --extract` 导出 1053 条官方 ponder 文本。
- **`moveSection` / `rotateSection` 的精确签名**：本文按主手册 4.3 的写法给，未在本轮重新 javap 核对。

