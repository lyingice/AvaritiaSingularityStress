# 10 · `net.createmod.ponder.api.scene` 逐条 API

> 全部签名来自 `javap -public`，语义来自实现类字节码（`PonderSceneBuilder$Ponder*Instructions`）。
> 参数名**未在字节码中保留**，括号内的名字仅是可读性提示；
> 「推断」= 由实现反推但字节码未直接写明；「未验证」= 完全没有证据。

## 包内类一览

| 类 | 类型 | 你怎么拿到它 |
| --- | --- | --- |
| `SceneBuilder` | interface | `PonderStoryBoard.program(scene, util)` 的第一个参数 |
| `SceneBuildingUtil` | interface | `PonderStoryBoard.program` 的第二个参数 |
| `WorldInstructions` | interface | `scene.world()` |
| `OverlayInstructions` | interface | `scene.overlay()` |
| `SpecialInstructions` | interface | `scene.special()` |
| `EffectInstructions` | interface | `scene.effects()` |
| `DebugInstructions` | interface | `scene.debug()` |
| `SelectionUtil` | interface | `util.select()` |
| `PositionUtil` | interface | `util.grid()` |
| `VectorUtil` | interface | `util.vector()` |
| `Selection` | interface | `util.select().position(...)` 等的返回值 |
| `PonderStoryBoard` | @FunctionalInterface | 你自己写 lambda |

---

## SceneBuilder

实现类：`net.createmod.ponder.foundation.PonderSceneBuilder`（❌ 内部，不要 new）。

### 取子指令集

#### `overlay()：OverlayInstructions`
拿到界面叠加指令集（文字、输入提示、描边、连线）。

#### `world()：WorldInstructions`
拿到世界操作指令集（区块显隐/位移/旋转、改方块、造实体）。

#### `debug()：DebugInstructions`
拿到调试指令集。

#### `effects()：EffectInstructions`
拿到特效指令集（粒子、红石/成功指示）。

#### `special()：SpecialInstructions`
拿到特殊元素指令集（鹦鹉、矿车、兴趣点）。

#### `getScene()：PonderScene`
返回当前正在构建的场景对象（`PonderScene`，⚠️ foundation 层实现类，不是 api 包类型）。
- 坑：外部 mod 一般不需要它；需要时多半只是为了 `getOutliner()`。

---

### 场景级设置（立即生效，不消耗时间轴）

#### `title(String, String)：void`
字节码：`scene.sceneId = ResourceLocation.fromNamespaceAndPath(scene.getNamespace(), 参数1)`，随后 `localization.registerSpecific(sceneId, "header", 参数2)`。
- **参数1**：场景 key（**纯 path**，不带命名空间；等于剧情注册时用的场景名）。
- **参数2**：标题显示文本。
- 效果：写入语言键 `<ns>.ponder.<参数1>.header`。
- 坑：参数1 必须与 `addStoryBoard` 的场景名一致，否则标题键与场景 id 对不上（`PonderScene` 常量池里有 `missing_title` 兜底）。

#### `configureBasePlate(int, int, int)：void`
字节码：依次写 `basePlateOffsetX` / `basePlateOffsetZ` / `basePlateSize`。
- **参数1**：底板 X 偏移（格）。
- **参数2**：底板 Z 偏移（格）。
- **参数3**：底板边长（格）。
- 效果：定义默认底板位置与大小，供 `showBasePlate()` 使用。

#### `scaleSceneView(float)：void`
写 `PonderScene.scaleFactor`。缩放整个场景取景。无范围校验。

#### `removeShadow()：void`
写 `hidePlatformShadow = true`，底板不再投影。

#### `setSceneOffsetY(float)：void`
写 `PonderScene.yOffset`，整体上下平移取景。

#### `showBasePlate()：void`
- 效果：把 `configureBasePlate` 定义的**单层（y=0）**底板从上方淡入，耗时 15 tick。
- 坑：**必须先 `configureBasePlate` 再调本方法**，否则用默认值。

---

### 时间轴控制

#### `idle(int)：void`
新增 `DelayInstruction(指定 tick)`：剧情暂停这么多 tick。1 秒 = 20 tick。

#### `idleSeconds(int)：void`
字节码 `idle(参数 * 20)`：暂停指定秒数。

#### `markAsFinished()：void`
新增 `MarkAsFinishedInstruction`：在时间轴此处把场景标记为「已结束」。

#### `setNextUpEnabled(boolean)：void`
新增一条回调指令，走到此处时执行 `scene.setNextUpEnabled(值)`。控制「Up Next」提示是否显示。

#### `rotateCameraY(float)：void`
新增 `RotateSceneInstruction(0f, 指定角度, true)`：相机绕 Y 轴转指定角度（度）。
- 推断：瞬时设定，不是缓动；第 3 个 `true` 的含义**未验证**。

#### `addKeyframe()：void`
新增 `KeyframeInstruction.IMMEDIATE`：立刻打关键帧。

#### `addLazyKeyframe()：void`
新增 `KeyframeInstruction.DELAYED`：延迟打关键帧。与上一个不是同义词。

---

### 底层逃生门

#### `addInstruction(PonderInstruction)：void`
直接塞进 `PonderScene.schedule` 列表。参数是 `foundation.instruction.PonderInstruction`（❌ 内部抽象类），一用就绑死内部实现。

#### `addInstruction(Consumer<PonderScene>)：void`
字节码 `addInstruction(PonderInstruction.simple(consumer))`。这是相对安全的自定义扩展点。

---

## WorldInstructions

获取：`scene.world()`。所有方法最终都 `addInstruction(...)`，即**按调用顺序排进时间轴**。
（时长一律 tick，20 tick = 1 秒。）

### 查询

#### `getHolderLookupProvider()：HolderLookup.Provider`
返回 1.21 的注册表查询器。你自己查 `Holder` 时用。

### 挖掘动画

#### `incrementBlockBreakingProgress(BlockPos)：void`
- 第1参：坐标。
- 效果：该方块「裂纹 +1 级」，连续调 10 次 ≈ 挖完动画。推断底层为 `PonderLevel.setBlockBreakingProgress`（该类确有此方法）。

### 区块显隐（Section = 一块被整体控制的建筑）

#### `showSection(Selection, Direction)：void`
字节码：`new DisplayWorldSectionInstruction(15, 方向, 选择, 基础区块supplier)`。
- 第1参：要显示的方块集合。
- 第2参：淡入方向。
- 效果：作为**基础区块**淡入，**固定 15 tick**。
- 坑：并入默认世界块；想单独控制请用 `showIndependentSection`。

#### `showSectionAndMerge(Selection, Direction, ElementLink<WorldSectionElement>)：void`
- 第1参：方块集合。第2参：淡入方向。第3参：要并入的**已有**独立区块句柄。
- 效果：新块并入该区块，之后随句柄一起动。

#### `glueBlockOnto(BlockPos, Direction, ElementLink<WorldSectionElement>)：void`
- 第1参：单格坐标。第2参：淡入方向。第3参：目标区块。
- 效果：把这一格「粘」到区块上。

#### `showIndependentSection(Selection, Direction)：ElementLink<WorldSectionElement>`
- 第1参：方块集合。第2参：淡入方向。
- 返回：新独立区块句柄（`moveSection`/`rotateSection`/`hideIndependentSection` 都用它）。
- 坑：返回值必须自己存变量。

#### `showIndependentSectionImmediately(Selection)：ElementLink<WorldSectionElement>`
- 第1参：方块集合。
- 效果：**无淡入动画**，立刻出现。注意只有 1 个参数。

#### `hideSection(Selection, Direction)：void`
- 第1参：方块集合。第2参：淡出方向。
- 字节码：新建临时 `WorldSectionElementImpl` → 在当前时间点加入场景 → `hideIndependentSection(临时句柄, 方向)`。

#### `hideIndependentSection(ElementLink<WorldSectionElement>, Direction)：void`
- 字节码：`FadeOutOfSceneInstruction(15, 方向, 句柄)`，**固定 15 tick**。

#### `restoreBlocks(Selection)：void`
- 结果：恢复成场景初始（schematic）状态。推断对应 `PonderLevel.restoreBlocks(Selection)`。

#### `makeSectionIndependent(Selection)：ElementLink<WorldSectionElement>`
- 效果：把**已在场景中**的方块提出成独立区块（不新增、不淡入）。
- 典型：先 `showSection` 整片显示，再对其中一部分单独位移。

#### `rotateSection(ElementLink<WorldSectionElement>, double, double, double, int)：void`
- 第1参：句柄。第2/3/4参：X/Y/Z 角度（度），字节码把它们装成 `new Vec3(x, y, z)`。第5参：时长。
- 效果：`AnimateWorldSectionInstruction.rotate(句柄, Vec3, 时长)`。
- 坑：旋转中心默认区块中心，要改配合 `configureCenterOfRotation`。

#### `configureCenterOfRotation(ElementLink<WorldSectionElement>, Vec3)：void`
- 第1参：句柄。第2参：旋转中心（世界坐标）。

#### `configureStabilization(ElementLink<WorldSectionElement>, Vec3)：void`
- 第1参：句柄。第2参：稳定参考点。对应 `WorldSectionElement.stabilizeRotation(Vec3)`。
- **未验证**：该 Vec3 的语义。

#### `moveSection(ElementLink<WorldSectionElement>, Vec3, int)：void`
- 第1参：句柄。第2参：位移向量（相对偏移，格）。第3参：时长。
- 效果：`AnimateWorldSectionInstruction.move(句柄, Vec3, 时长)`。
- 推断：相对偏移，连续调用会累加（未验证）。

### 方块修改

#### `setBlocks(Selection, BlockState, boolean)：void`
- 第1参：集合。第2参：目标状态。第3参：**未验证**（推断为「是否立刻生效」）。

#### `setBlock(BlockPos, BlockState, boolean)：void`
单坐标版本。

#### `destroyBlock(BlockPos)：void`
- 变成空气（推断等同 `setBlock(pos, AIR.defaultBlockState(), ...)`）。

#### `replaceBlocks(Selection, BlockState, boolean)：void`
- 走 `ReplaceBlocksInstruction`（该类确实存在）。
- 坑：与 `setBlocks` 的差异**未验证**（不同指令类说明行为确有区别）。

#### `modifyBlock(BlockPos, UnaryOperator<BlockState>, boolean)：void`
- 第2参：状态变换函数，如 `s -> s.cycle(BlockStateProperties.POWERED)`。第3参：未验证。

#### `modifyBlocks(Selection, UnaryOperator<BlockState>, boolean)：void`
批量版。

#### `cycleBlockProperty(BlockPos, Property<?>)：void`
- 第2参：要切到下一个值的属性。

#### `toggleRedstonePower(Selection)：void`
- 翻转这批方块的红石供电状态（推断：作用于 `POWERED`/`LIT` 这类属性）。

### 实体

#### `<T extends Entity> modifyEntities(Class<T>, Consumer<T>)：void`
- 对场景假世界里**所有**该类型实体执行一次回调。

#### `<T extends Entity> modifyEntitiesInside(Class<T>, Selection, Consumer<T>)：void`
同上，只处理坐标落在 `Selection` 内的实体。

#### `modifyEntity(ElementLink<EntityElement>, Consumer<Entity>)：void`
- 第1参：`createEntity` 得到的句柄。第2参：修改回调。

#### `createEntity(Function<Level, Entity>)：ElementLink<EntityElement>`
- 第1参：工厂函数，入参是假世界 `Level`（运行时是 `PonderLevel`）。
- 坑：坐标要你在工厂里自己设好。

#### `createItemEntity(Vec3, Vec3, ItemStack)：ElementLink<EntityElement>`
- 第1参：位置。第2参：初速度（`Vec3.ZERO` = 静止）。第3参：物品堆。

### 方块实体

#### `modifyBlockEntityNBT(Selection, Class<? extends BlockEntity>, Consumer<CompoundTag>)：void`
- 第1参：集合。第2参：只处理该类型。第3参：NBT 修改回调，如 `tag -> tag.putInt("Speed", 32)`。

#### `modifyBlockEntityNBT(Selection, Class<? extends BlockEntity>, Consumer<CompoundTag>, boolean)：void`
- 第4参 boolean：**未验证**含义。

#### `<T extends BlockEntity> modifyBlockEntity(BlockPos, Class<T>, Consumer<T>)：void`
- 第1参：坐标。第2参：类型（用于 cast）。第3参：**强类型**回调。
- 坑：类型不匹配时行为未验证（推断为 cast 异常）。

---

## OverlayInstructions

获取：`scene.overlay()`。

#### `showText(int)：TextElementBuilder`
- 第1参：气泡显示时长（tick）。
- 坑：只调 `showText` 不给 `.text(...)` 等于什么都不显示。

#### `showOutlineWithText(Selection, int)：TextElementBuilder`
- 第1参：要高亮的方块集合。第2参：显示时长。（自动附带描边）

#### `showControls(Vec3, Pointing, int)：InputElementBuilder`
- 第1参：指向的世界坐标。第2参：气泡朝向（`catnip.math.Pointing`：`UP/LEFT/DOWN/RIGHT`）。第3参：显示时长。

#### `chaseBoundingBoxOutline(PonderPalette, Object, AABB, int)：void`
- 颜色 / 描边槽位 key（同 key 复用同一根） / 包围盒 / 时长。

#### `showOutline(PonderPalette, Object, Selection, int)：void`
- 字节码：`OutlineSelectionInstruction(palette, key, selection, duration)`。
- 坑：同一 key 同时只能有一根描边；要同时亮两片请换 key。

#### `showLine(PonderPalette, Vec3, Vec3, int)：void`
- 颜色 / 起点 / 终点 / 时长。

#### `showBigLine(PonderPalette, Vec3, Vec3, int)：void`
- 同上但更粗。参数表与 `showLine` 完全一致，极易调错。

#### `showCenteredScrollInput(BlockPos, Direction, int)：void`
- 方块坐标 / 贴哪个面 / 时长。图标在方块**中心**。

#### `showScrollInput(Vec3, Direction, int)：void`
- 同上，但位置由 `Vec3` 精确定。

#### `showRepeaterScrollInput(BlockPos, int)：void`
- 中继器坐标 / 时长。位置已内置偏移。

#### `showFilterSlotInput(Vec3, int)：void`
- 滤槽中心坐标 / 时长。

#### `showFilterSlotInput(Vec3, Direction, int)：void`
- 同上，额外指定朝向。

---

## SpecialInstructions

获取：`scene.special()`。

#### `createBirb(Vec3, Supplier<? extends ParrotPose>)：ElementLink<ParrotElement>`
- 位置 / 姿态供应器，如 `() -> new ParrotPose.DancePose()`。
- 推断：用 `Supplier` 是为了重播时能新建姿态实例。

#### `changeBirbPose(ElementLink<ParrotElement>, Supplier<? extends ParrotPose>)：void`
- 切换已有鹦鹉的姿态。

#### `rotateParrot(ElementLink<ParrotElement>, double, double, double, int)：void`
- 句柄 / X 角 / Y 角 / Z 角 / 时长。

#### `moveParrot(ElementLink<ParrotElement>, Vec3, int)：void`
- 句柄 / 目标偏移 / 时长。

#### `movePointOfInterest(Vec3)：void`
- 新的兴趣点（世界坐标）。影响鹦鹉朝向与相机注视。

#### `movePointOfInterest(BlockPos)：void`
- `BlockPos` 重载。

#### `createCart(Vec3, float, MinecartElement.MinecartConstructor)：ElementLink<MinecartElement>`
- 位置 / 初始旋转角（度，绕 Y）/ 矿车工厂 `(w, x, y, z) -> new Minecart(w, x, y, z)`。

#### `rotateCart(ElementLink<MinecartElement>, float, int)：void`
- 句柄 / 目标角度（度）/ 时长。
- 坑：矿车只接受**一个** float（绕 Y），不像 `rotateSection` 是三轴。

#### `moveCart(ElementLink<MinecartElement>, Vec3, int)：void`
- 句柄 / 目标偏移 / 时长。

#### `<T extends AnimatedSceneElement> hideElement(ElementLink<T>, Direction)：void`
- 句柄 / 淡出方向。走 `FadeOutOfSceneInstruction(15, 方向, 句柄)`。
- 坑：类型上界是 `AnimatedSceneElement`，所以 `EntityElement`（`TrackedElement` 子接口）**不能用**它隐藏。

---

## EffectInstructions

获取：`scene.effects()`。

#### `emitParticles(Vec3, ParticleEmitter, float, int)：void`
- 字节码：`new EmitParticlesInstruction(Vec3, ParticleEmitter, float, int)`。
- 第1参：发射位置。第2参：发射器。第3参 float：**未验证**。第4参 int：**未验证**。
- 坑：两个数值参数顺序是常见踩坑点，务必实测。

#### `<T extends ParticleOptions> simpleParticleEmitter(T, Vec3)：ParticleEmitter`
- 粒子类型（如 `ParticleTypes.CRIT`）/ 速度向量。

#### `<T extends ParticleOptions> particleEmitterWithinBlockSpace(T, Vec3)：ParticleEmitter`
- 粒子类型 / 速度向量。把粒子约束在方块空间内（内部差异**未验证**）。

#### `indicateRedstone(BlockPos)：void`
- 红石指示。推断内部走 `createRedstoneParticles`。

#### `indicateSuccess(BlockPos)：void`
- 成功（绿色）指示。

#### `createRedstoneParticles(BlockPos, int, int)：void`
- 坐标 / int（**未验证**）/ int（**未验证**）。

---

## DebugInstructions

获取：`scene.debug()`。

#### `debugSchematic()：void`
以调试方式展示 schematic 原始内容，开发期核对坐标用。

#### `addInstructionInstance(PonderInstruction)：void`
同 `SceneBuilder.addInstruction(PonderInstruction)`，参数是 foundation 内部抽象类。

#### `enqueueCallback(Consumer<PonderScene>)：void`
同 `SceneBuilder.addInstruction(Consumer<PonderScene>)`。

---

## SelectionUtil

获取：`util.select()`。全部返回 `Selection`。

#### `everywhere()：Selection`
场景内所有方块。

#### `position(int, int, int)：Selection`
单方块 (x, y, z)。

#### `position(BlockPos)：Selection`
单方块。

#### `fromTo(int, int, int, int, int, int)：Selection`
(起x, 起y, 起z, 终x, 终y, 终z) 实心长方体。
- 坑：是「起点 3 个 + 终点 3 个」，不是 x1,x2,y1,y2,z1,z2。

#### `fromTo(BlockPos, BlockPos)：Selection`
两个角点。

#### `column(int, int)：Selection`
(x, z) 处的整列。

#### `layer(int)：Selection`
该 y 值的整层。

#### `layersFrom(int)：Selection`
从该层到顶。

#### `layers(int, int)：Selection`
两层区间（是否含两端**未验证**，推断含）。

#### `cuboid(BlockPos, Vec3i)：Selection`
起点 + 尺寸。

---

## PositionUtil

获取：`util.grid()`。返回 `BlockPos`（`util.grid().at(...)` 是 Ponder 里最高频的调用）。

#### `at(int, int, int)：BlockPos`
`new BlockPos(x, y, z)`。

#### `zero()：BlockPos`
原点。

---

## VectorUtil

获取：`util.vector()`。返回 `Vec3`。

#### `centerOf(int, int, int)：Vec3`
方块几何中心 `(x+0.5, y+0.5, z+0.5)`。

#### `centerOf(BlockPos)：Vec3`
重载。

#### `topOf(int, int, int)：Vec3`
方块顶面中心 `(x+0.5, y+1, z+0.5)`。

#### `topOf(BlockPos)：Vec3`
重载。

#### `blockSurface(BlockPos, Direction)：Vec3`
该面中心点。

#### `blockSurface(BlockPos, Direction, float)：Vec3`
同上，float 沿法线外推（避免描边/文字被方块盖住）。
- **未验证**：该 float 是「格」还是「比例」。

#### `of(double, double, double)：Vec3`
`new Vec3(x, y, z)`。

---

## Selection

不能直接 new（实现 `SelectionImpl` 在 foundation 层）。

#### `add(Selection)：Selection`
并集，返回新对象（推断）。

#### `substract(Selection)：Selection`
差集。**拼写就是 `substract`**（不是 `subtract`）。

#### `copy()：Selection`
复制（说明底层存在可变实现）。

#### `getCenter()：Vec3`
几何中心（推断取包围盒中心）。

#### `makeOutline(Outliner, Object)：OutlineParams`
- 第1参：`net.createmod.catnip.outliner.Outliner`（场景里用 `scene.getScene().getOutliner()`）。第2参：描边槽位 key。
- 坑：引入 catnip 类型，属深度 API。

#### `makeOutline(Outliner)：OutlineParams`（default）
单参数便捷重载。**未验证**内部用什么当 key。

#### 继承来的能力
`Selection extends Iterable<BlockPos>`（可 for-each）且 `extends Predicate<BlockPos>`（可 `sel.test(pos)`）。

---

## SceneBuildingUtil

获取：`PonderStoryBoard.program` 第二个参数。

#### `select()：SelectionUtil`
方块集合工具。

#### `vector()：VectorUtil`
向量工具。

#### `grid()：PositionUtil`
坐标工具。

> **重要**：本接口**没有任何常量字段**（没有 `SELECT`/`GRID`/`VECTOR`）。
> `javap -public` 对 `SceneBuildingUtil` 只输出 3 个方法，零字段。

---

## PonderStoryBoard

#### `program(SceneBuilder, SceneBuildingUtil)：void`
唯一抽象方法（函数式接口，可直接 lambda）。
- 第1参：场景构建器。第2参：构建工具。

**执行时机**：`PonderSceneRegistry.compileScene` 在场景**编译**时调用一次
（字节码：`new PonderScene(...)` → `scene.builder()` → `board.program(builder, scene.getSceneBuildingUtil())`）。
- 坑1：不是每帧执行，而是打开 Ponder 界面时编译。
- 坑2：体内**不要写副作用**（改真实世界、发网络包）——会被反复编译。
- 坑3：指令顺序 = 时间轴顺序，忘 `idle()` 会导致内容瞬间闪完。

---

## 交叉引用

- 元素句柄 `ElementLink`、文本/输入构建器 → [20-element.md](20-element.md)
- 插件注册、标签、语言键 → [30-registration.md](30-registration.md)
- `PonderScene` / `PonderLevel` / `PonderPalette` → [40-foundation-others.md](40-foundation-others.md)
