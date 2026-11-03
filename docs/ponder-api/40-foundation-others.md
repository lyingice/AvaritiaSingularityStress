# 40 · foundation / enums / command / config / level / api 根包 / 入口类

> 签名来自 `javap -public`。这一批大多是**内部实现**，能不用就不用；
> 每个类开头都标了「外部可用」等级（✅ 可用 / ⚠️ 实现细节 / ❌ 内部）。

---

# 一、`net.createmod.ponder.foundation`

## PonderScene ⚠️

一个**编译后**的场景。你能从 `SceneBuilder.getScene()` 拿到它。
外部 mod 一般只用到 `getOutliner()` 和 `getWorld()`。

**常量**：`public static final String TITLE_KEY` —— 值为 `"header"`（`javap -v` ConstantValue 已核实）。

### 构造器
#### `PonderScene(PonderLevel, PonderLocalization, String, ResourceLocation, Collection<ResourceLocation>, Collection<SceneOrderingEntry>)`
- 6 个参数：(假世界, 本地化表, 命名空间, 组件 id, 标签集合, 排序项集合)。
- 坑：**不要自己 new**，让 `PonderSceneRegistry.compile` 去建。

### 生命周期
#### `begin()：void` —— 开始计时/执行指令。
#### `reset()：void` —— 重置到初始状态。
#### `tick()：void` —— 每 tick 推进。
#### `seekToTime(int)：void` —— 跳到指定 tick。
#### `addToSceneTime(int)：void` —— 场景时间 +N（推断）。
#### `stopCounting()：void` —— 停止计时。
#### `markKeyframe(int)：void` —— 在指定 tick 打关键帧。
#### `fadeOut()：void` —— 触发淡出。
#### `deselect()：void` —— 取消选中。

### 元素管理（泛型解析 ElementLink 的核心）
#### `addElement(PonderElement)：void`
#### `<E extends PonderElement> linkElement(E, ElementLink<E>)：void`
- 把元素和句柄绑定。
#### `<E extends PonderElement> E resolve(ElementLink<E>)：E`
- 解析句柄 → 元素实例。
#### `<E extends PonderElement> Optional<E> resolveOptional(ElementLink<E>)：Optional<E>`
- 同上，带 Optional。
#### `<E extends PonderElement> runWith(ElementLink<E>, Consumer<E>)：void`
#### `<E extends PonderElement, F> F applyTo(ElementLink<E>, Function<E, F>)：F`
#### `forEach(Consumer<? super PonderElement>)：void`
#### `<T extends PonderElement> forEach(Class<T>, Consumer<T>)：void`
#### `<T extends PonderElement> forEachVisible(Class<T>, Consumer<T>)：void`
- 只遍历可见元素。
#### `<T extends Entity> forEachWorldEntity(Class<T>, Consumer<T>)：void`
- 遍历假世界里的实体。

### 文本
#### `registerText(String)：Supplier<String>`
- 分配 `text_N` 键并登记内容；返回惰性求值的 supplier。
- 已核实：`textIndex` 构造器里初值 **1**，本方法用完再自增 → 首条是 `text_1`。
#### `registerText(String, Object...)：Supplier<String>`
- 带参数填充版本。
#### `registerText` 只处理 `.text(...)`；`.sharedText(...)` 不占编号。

### 渲染
#### `renderScene(SuperRenderTypeBuffer, GuiGraphics, float)：void`
#### `renderOverlay(PonderUI, GuiGraphics, float)：void`
#### `rayTraceScene(Vec3, Vec3)：Pair<ItemStack, BlockPos>`
- 场景内射线检测，返回 (命中物品, 命中坐标)。

### 视角 / 兴趣点
#### `setPointOfInterest(Vec3)：void`
#### `getPointOfInterest()：Vec3`
#### `getTransform()：PonderScene.SceneTransform`
- 内部类 `SceneCamera` / `SceneTransform` 在 `javap -public` 里只作为类型出现（无公开成员）。

### 构建工具
#### `builder()：SceneBuilder` —— 返回 `PonderSceneBuilder`。
#### `getSceneBuildingUtil()：SceneBuildingUtil`

### 元信息
#### `getTitle()：String`
#### `getString(String)：String` —— 取任意语言键的值（`missing_title` 兜底）。
#### `getWorld()：PonderLevel`
#### `getNamespace()：String`
#### `getLocation()：ResourceLocation`
#### `getId()：ResourceLocation`
#### `getTags()：List<PonderTag>`
#### `getOrderingEntries()：List<SceneOrderingEntry>`
#### `getElements()：Set<PonderElement>`
#### `getBounds()：BoundingBox`

### 时间 / 进度
#### `getSceneProgress()：float`
#### `getKeyframeCount()：int`
#### `getKeyframeTime(int)：int`
#### `getTotalTime()：int`
#### `getCurrentTime()：int`

### 底板 / 取景
#### `getBasePlateOffsetX()：int`
#### `getBasePlateOffsetZ()：int`
#### `getBasePlateSize()：int`
#### `shouldHidePlatformShadow()：boolean`
#### `getScaleFactor()：float`
#### `getYOffset()：float`

### 状态
#### `isFinished()：boolean`
#### `setFinished(boolean)：void`
#### `isNextUpEnabled()：boolean`
#### `setNextUpEnabled(boolean)：void`
#### `getOutliner()：Outliner` —— 拿 catnip 描边器（`Selection.makeOutline` 要用）。

---

## PonderSceneBuilder ❌

`implements SceneBuilder`，`javap -public` 的 21 个方法与 `SceneBuilder` 一一对应（多一个 public 构造器 `PonderSceneBuilder(PonderScene)`）。
**外部 mod 不要 new 它**——用 `scene.getScene().builder()` 或直接拿到传进来的 `SceneBuilder`。

## PonderSceneBuilder.Ponder*Instructions ❌

5 个内部实现类，只证明 `SceneBuilder` 的 5 个访问器各自对应一个实现：

| 内部类 | 实现接口 |
| --- | --- |
| `PonderSceneBuilder$PonderWorldInstructions` | `WorldInstructions` |
| `PonderSceneBuilder$PonderOverlayInstructions` | `OverlayInstructions` |
| `PonderSceneBuilder$PonderSpecialInstructions` | `SpecialInstructions` |
| `PonderSceneBuilder$PonderEffectInstructions` | `EffectInstructions` |
| `PonderSceneBuilder$PonderDebugInstructions` | `DebugInstructions` |

## PonderSceneBuildingUtil ❌

`implements SceneBuildingUtil`，3 个方法：`select()` / `vector()` / `grid()`。
内部类 `PonderSelectionUtil` / `PonderVectorUtil` / `PonderPositionUtil` 分别实现三个 Util 接口，方法签名与接口完全一致。

---

## PonderIndex ✅（外部唯一该碰的 foundation 类）

#### `static addPlugin(PonderPlugin)：void`
- 字节码：`Collections.binarySearch(plugins, plugin, comparator)` → `List.add(index, plugin)`。**插列表，不立即注册**。

#### `static forEachPlugin(Consumer<PonderPlugin>)：void`
#### `static streamPlugins()：Stream<PonderPlugin>`
#### `static reload()：void`
- 清空 shared / 场景 / 标签 → `registerAll()` → `gatherSharedText()`。

#### `static registerAll()：void`
- 先 `registerScenes` 后 `registerTags`。

#### `static gatherSharedText()：void`
- 收集共享文本。

#### `static getSceneAccess()：SceneRegistryAccess`
#### `static getTagAccess()：TagRegistryAccess`
#### `static getLangAccess()：LangRegistryAccess`
#### `static editingModeActive()：boolean`
- 返回配置文件里 `editingMode` 的值。

---

## PonderTag ⚠️

`implements catnip.gui.element.ScreenElement`。

#### `PonderTag(ResourceLocation, ResourceLocation, ItemStack, ItemStack)` —— 构造器（4 参含义**未验证**，推断为 id / 图标 id / 主图标 / 次图标）。
#### `getId()：ResourceLocation`
#### `getMainItem()：ItemStack`
#### `getTitle()：String`
#### `getDescription()：String`
#### `render(GuiGraphics, int, int)：void` —— ScreenElement 实现。
#### `equals(Object)：boolean` —— 重写过（按 id 比较，推断）。

## PonderTag.Highlight ❌
#### `public static final ResourceLocation ALL` —— 唯一的成员，就是个「全部高亮」哨兵值。

---

## PonderChapter ⚠️ / PonderChapterRegistry ❌

#### `PonderChapter.of(ResourceLocation)：PonderChapter`（静态工厂）
#### `getId()：ResourceLocation`
#### `getTitle()：String`
#### `render(GuiGraphics, int, int)：void`

`PonderChapterRegistry`：`addStoriesToChapter(PonderChapter, StoryBoardEntry...)` / `getAllChapters()` / `getStories(PonderChapter)`。

---

## PonderStoryBoardEntry ❌

`implements StoryBoardEntry`。

#### `PonderStoryBoardEntry(PonderStoryBoard, String, ResourceLocation, ResourceLocation)`
- (剧情, 命名空间, schematic id, 组件 id)。
#### `PonderStoryBoardEntry(PonderStoryBoard, String, String, ResourceLocation)`
- 第3参是纯 path，内部 `ResourceLocation.fromNamespaceAndPath`。
- 其余方法与 `StoryBoardEntry` 接口一致。

## SelectionImpl ❌
#### `static of(BoundingBox)：Selection` —— 从包围盒造 `Selection`（唯一的公开成员）。

---

## PonderTooltipHandler ⚠️

#### `static boolean enable` —— 全局开关。
#### `static final String HOLD_TO_PONDER` / `SUBJECT` —— 语言键常量。
#### `static tick()` / `static deferredTick()`
#### `static addToTooltip(List<Component>, ItemStack)：void` —— 往 tooltip 里加「按住 X 思索」。
#### `static handleTooltipColor(ItemStack)：Optional<Couple<Color>>` —— 给 tooltip 换色。
#### `static synchronized registerHoveredPonderStackCallback(Consumer<ItemStack>)`
#### `static synchronized removeHoveredPonderStackCallback(Consumer<ItemStack>)`
- 物品被悬停时回调（**唯一对第三方有实际价值的入口**）。

## PonderWorldParticles ❌
#### `PonderWorldParticles(PonderLevel)` / `addParticle(Particle)` / `tick()` / `renderParticles(...)` / `clearEffects()`

---

## foundation.registration 家族 ❌

| 类 | 作用 |
| --- | --- |
| `PonderSceneRegistry` | `SceneRegistryAccess` 实现；`loadSchematic` 系列（public static）在这里 |
| `PonderTagRegistry` | `TagRegistryAccess` 实现 |
| `PonderLocalization` | `LangRegistryAccess` 实现；`LANG_PREFIX="ponder."`、`UI_PREFIX="ui."` |
| `DefaultPonderSceneRegistrationHelper` | `PonderSceneRegistrationHelper<ResourceLocation>` 默认实现 |
| `DefaultPonderTagRegistrationHelper` | `PonderTagRegistrationHelper<ResourceLocation>` 默认实现 |
| `DefaultSharedTextRegistrationHelper` | `SharedTextRegistrationHelper` 实现 |
| `GenericPonderSceneRegistrationHelper` / `GenericPonderTagRegistrationHelper` | 带 `withKeyFunction` 的泛型包装 |
| `GenericMultiSceneBuilder` / `GenericMultiTagBuilder` | `MultiSceneBuilder` / `MultiTagBuilder.Tag`+`Component` 实现 |
| `PonderTagBuilder` | `TagBuilder` 实现 |
| `PonderIndexExclusionHelper` | `IndexExclusionHelper` 实现 |

`PonderSceneRegistry` 里 3 个 **public static** 方法值得知道：
#### `compileScene(PonderLocalization, StoryBoardEntry, PonderLevel)：PonderScene`
#### `loadSchematic(ResourceLocation)：StructureTemplate`
#### `loadSchematic(ResourceManager, ResourceLocation)：StructureTemplate`
#### `loadSchematic(InputStream)：StructureTemplate`
- 路径规则已核实：`<ns>:ponder/<path>.nbt`。

---

## foundation.instruction 家族 ❌

`PonderInstruction`（abstract）：
#### `isBlocking()：boolean`（默认 false，推断）
#### `reset(PonderScene)：void`
#### `isComplete()：boolean`（abstract）
#### `onScheduled(PonderScene)：void`
#### `tick(PonderScene)：void`（abstract）
#### `static simple(Consumer<PonderScene>)：PonderInstruction` —— **唯一建议第三方使用的静态方法**。

其余 24 个指令类（都在 `javap -public` 转储里，逐条列出）：
`AnimateElementInstruction`、`AnimateMinecartInstruction`、`AnimateParrotInstruction`、`AnimateWorldSectionInstruction`（含 `static move(...)`/`rotate(...)`）、`BlockEntityDataInstruction`、`ChaseAABBInstruction`、`CreateMinecartInstruction`、`CreateParrotInstruction`、`DelayInstruction`、`DisplayWorldSectionInstruction`、`EmitParticlesInstruction`、`FadeInOutInstruction`、`FadeIntoSceneInstruction`、`FadeOutOfSceneInstruction`、`HideAllInstruction`、`HighlightValueBoxInstruction`、`KeyframeInstruction`（`IMMEDIATE`/`DELAYED` 两个常量）、`LineInstruction`、`MarkAsFinishedInstruction`、`MovePoiInstruction`、`OutlineSelectionInstruction`、`ReplaceBlocksInstruction`、`RotateSceneInstruction`、`ShowInputInstruction`、`TextInstruction`、`TickingInstruction`、`WorldModifyInstruction`。

## foundation.element 家族 ❌

| 类 | 实现/对应 |
| --- | --- |
| `ElementLinkImpl` | `ElementLink` |
| `PonderElementBase` | `PonderElement` |
| `AnimatedSceneElementBase` / `AnimatedOverlayElementBase` | 两个 Animated 接口 |
| `TrackedElementBase` / `EntityElementImpl` | `TrackedElement` / `EntityElement` |
| `WorldSectionElementImpl` | `WorldSectionElement` |
| `MinecartElementImpl` / `ParrotElementImpl` | 矿车 / 鹦鹉 |
| `TextWindowElement` (+`Builder`) | 文字气泡 |
| `InputWindowElement` (+`Builder`) | 操作提示气泡 |
| `OutlinerElement` | 描边元素 |

## foundation.ui 家族 ⚠️

| 类 | 作用 |
| --- | --- |
| `PonderUI` | **Ponder 主界面**，有静态工厂 `of(ResourceLocation)` / `of(ItemStack)` / `of(ItemStack, PonderTag)` |
| `AbstractPonderScreen` | `PonderUI` 的父类 |
| `PonderIndexScreen` | 索引界面（`javap -public` 见转储） |
| `PonderTagIndexScreen` / `PonderTagScreen` | 标签界面 |
| `PonderButton` / `PonderProgressBar` / `ChapterLabel` | 控件 |

`PonderUI` 的 public 成员里有：
#### `static of(ResourceLocation)：PonderUI`
#### `static of(ItemStack)：PonderUI`
#### `static of(ItemStack, PonderTag)：PonderUI`
- **这是唯一「用代码打开 Ponder 界面」的公开入口**（推断：返回值是 Screen，交给 `Minecraft.setScreen`）。

## foundation.content 家族 ❌
`BasePonderPlugin`、`DebugPonderPlugin`、`DebugScenes`、`TemplateScenes` —— 官方示例，可当作**读源码时的模板**（doc 不给源码，只给类名）。

---

# 二、`net.createmod.ponder.enums` ⚠️（全部列全）

## PonderConfig
#### `static registerConfigs()：Set<Map.Entry<ModConfig.Type, ConfigBase>>`
- 注册配置（给 `ModContainer.registerConfig` 用）。
#### `static onLoad(ModConfig)：void` / `static onReload(ModConfig)：void`
#### `static client()：CClient`
- **唯一对第三方有用的方法**：读客户端配置。

## PonderGuiTextures（25 个常量，全部来自 `javap -public`）
`LOGO`、`SPEECH_TOOLTIP_BACKGROUND`、`SPEECH_TOOLTIP_COLOR`、`ICON_PONDER_LEFT`、`ICON_PONDER_CLOSE`、`ICON_PONDER_RIGHT`、`ICON_PONDER_IDENTIFY`、`ICON_PONDER_REPLAY`、`ICON_PONDER_USER_MODE`、`ICON_PONDER_SLOW_MODE`、`ICON_CONFIG_UNLOCKED`、`ICON_CONFIG_LOCKED`、`ICON_CONFIG_DISCARD`、`ICON_CONFIG_SAVE`、`ICON_CONFIG_RESET`、`ICON_CONFIG_BACK`、`ICON_CONFIG_PREV`、`ICON_CONFIG_NEXT`、`ICON_DISABLE`、`ICON_CONFIG_OPEN`、`ICON_CONFIRM`、`ICON_LMB`、`ICON_SCROLL`、`ICON_RMB`、`PLACEMENT_INDICATOR_SHEET`
- 字段 `public final ResourceLocation location`
- 方法：`render(GuiGraphics,int,int)` / `render(GuiGraphics,int,int,Color)` / `getLocation()` / `getStartX()/getStartY()/getWidth()/getHeight()` / `asStencil()：DelegatedStencilElement`
- 用途：自定义输入提示图标（`InputElementBuilder.showing(...)`）。

## PonderKeybinds
- 常量：`PONDER`（唯一按键）、`public static final String CATEGORY`
- `static register(Consumer<KeyMapping>)：void`
- `isDown()：boolean`
- `message()：Component`

## PonderSpecialTextures
- 常量：`BLANK`、`public static final String ASSET_PATH`（值 `"textures/special/"`，已核实）
- `bind()：void` / `getLocation()：ResourceLocation`

---

# 三、`net.createmod.ponder.command`

## PonderCommand ❌
空类，`javap -public` 只有默认构造器。

## PonderCommands ❌
#### `static register(CommandDispatcher<CommandSourceStack>)：void`
- 注册 `/ponder` 命令。**已核实**：命令后面跟的是 brigadier 的 `<组件id>` 参数（不是字面量子命令 `scene`/`targets`），用法即 `/ponder <组件id>`。

## SimplePonderActions ⚠️
#### `static openPonder(String)：void`
#### `static reloadPonder(String)：void`

---

# 四、`net.createmod.ponder.config` ⚠️

## CClient（`extends ConfigBase`）
字段（全是 catnip 的 Config 值对象，`javap -public` 全列）：

| 字段 | 类型 | 用途（推断自命名） |
| --- | --- | --- |
| `comfyReading` | `ConfigBase.ConfigBool` | 「Comfy Reading」慢速阅读 |
| `editingMode` | `ConfigBase.ConfigBool` | 编辑模式（`PonderIndex.editingModeActive()` 读它） |
| `placementAssist` | `ConfigBase.ConfigGroup` | 分组 |
| `placementIndicator` | `ConfigBase.ConfigEnum<PlacementIndicatorSetting>` | 放置指示器样式 |
| `indicatorScale` | `ConfigBase.ConfigFloat` | 指示器缩放 |

#### `getName()：String`

## CClient.PlacementIndicatorSetting（3 个常量，全列出）
`TEXTURE`、`TRIANGLE`、`NONE`

---

# 五、`net.createmod.ponder.api` 根 + `level`

## PonderPalette ✅
10 个常量的颜色值已从字节码静态初始化块核出。

| 常量 | `getColor()` int | 十六进制 |
| --- | --- | --- |
| `WHITE` | 15658734 | `#EEEEEE` |
| `BLACK` | 2232593 | `#221111` |
| `RED` | 16735596 | `#FF5D6C` |
| `GREEN` | 9222737 | `#8CBA51` |
| `BLUE` | 6253743 | `#5F6CAF` |
| `SLOW` | 2293538 | `#22FF22` |
| `MEDIUM` | 34047 | `#0084FF` |
| `FAST` | 16733695 | `#FF55FF` |
| `INPUT` | 8375776 | `#7FCDE0` |
| `OUTPUT` | 14532966 | `#DDC166` |

方法：
#### `static values()：PonderPalette[]`
#### `static valueOf(String)：PonderPalette`
#### `getColor()：int` —— 返回 RGB int（底层 `Color.getRGB()`）。
#### `getColorObject()：Color` —— 返回 catnip 的 `Color` 对象。

## ParticleEmitter ✅
#### `create(PonderLevel, double, double, double)：void`
- 唯一的抽象方法；@FunctionalInterface。三个 double 是粒子生成坐标。

## VirtualBlockEntity ⚠️
#### `markVirtual()：void`
#### `isVirtual()：boolean`
- 让你的方块实体在 Ponder 假世界里能被安全处理。

## PonderLevel ⚠️
继承自 `net.createmod.catnip.levelWrappers.SchematicLevel`。
- **public 实例字段**：`public PonderScene scene`（已核实，唯一一个公开字段）

#### `PonderLevel(BlockPos, Level)` —— 构造器
#### `createBackup()：void` / `restore()：void`
#### `restoreBlocks(Selection)：void`
#### `pushFakeLight(int)：void` / `popLight()：void`
#### `getBrightness(LightLayer, BlockPos)：int`
#### `setMask(Selection)：void` / `clearMask()：void`
#### `getBlockState(BlockPos)：BlockState`
#### `getChunkForCollisions(int,int)：BlockGetter`
#### `renderEntities(PoseStack, SuperRenderTypeBuffer, Camera, float)：void`
#### `renderParticles(PoseStack, MultiBufferSource, Camera, float)：void`
#### `tick()：void`
#### `addParticle(ParticleOptions, double,double,double,double,double,double)：void`
#### `addAlwaysVisibleParticle(...)：void`（同签名）
#### `setBlock(BlockPos, BlockState, int)：boolean`
#### `addParticle(Particle)：void`
#### `setBlockBreakingProgress(BlockPos, int)：void`
#### `getBlockBreakingProgressions()：Map<BlockPos, Integer>`
#### `addBlockDestroyEffects(BlockPos, BlockState)：void`
#### `hasChunkAt(BlockPos)：boolean` / `hasChunk(int,int)：boolean` / `isLoaded(BlockPos)：boolean`
#### `hasNearbyAlivePlayer(double,double,double,double)：boolean`

---

# 六、入口类（❌ 外部 mod 一律不要调用）

## Ponder 根类 ✅（常量可用）
- `public static final String MOD_ID`（值 `"ponder"`）
- `public static final String MOD_NAME`
- `public static final org.apache.logging.log4j.Logger LOGGER`
- `public static final java.util.Random RANDOM`
- `static lang()：LangBuilder`
- `static asResource(String)：ResourceLocation`
- `static init()：void`

## PonderClient ❌
- 常量：`RESOURCE_RELOAD_LISTENER`、`GHOST_BLOCKS`
- `static init()` / `modLoadCompleted()` / `onTick()` / `onRenderWorld(PoseStack)` / `invalidateRenderers()` / `isGameActive()`

## NeoForgePonder ❌（通用端 `@Mod` 入口）
- `NeoForgePonder(IEventBus, ModContainer)` —— mod 构造器
- `static init(FMLCommonSetupEvent)`
- 内部类 `Events`：`static registerCommands(RegisterCommandsEvent)`
- 内部类 `ModBusEvents`：`static onLoad(ModConfigEvent.Loading)` / `static onReload(ModConfigEvent.Reloading)`
- 它做的事：注册 `/ponder` 命令、读配置、初始化 `Ponder`。**外部 mod 无需调用**。

## NeoForgePonderClient ❌（客户端 `@Mod` 入口）
- `NeoForgePonderClient(IEventBus)` / `static init(FMLClientSetupEvent)`
- 内部类 `ClientEvents`：`onTickPre(ClientTickEvent.Pre)`、`onRenderWorld(RenderLevelStageEvent)`、`onLoadWorld(LevelEvent.Load)`、`onUnloadWorld(LevelEvent.Unload)`、`afterRenderOverlayLayer(RenderGuiLayerEvent.Post)`、`onRenderTooltipColor(RenderTooltipEvent.Color)`、`onItemTooltip(ItemTooltipEvent)`
- 内部类 `ModBusClientEvents`：`loadCompleted(FMLLoadCompleteEvent)`、`registerClientReloadListeners(RegisterClientReloadListenersEvent)`、`onTextureStitchPost(TextureAtlasStitchedEvent)`、`register(RegisterKeyMappingsEvent)`
- 它做的事：每 tick 驱动 Ponder、渲染世界/叠加层、处理 tooltip 与配色、注册按键与资源重载监听。**外部 mod 无需调用**。

---

# 七、Catnip 中「可选用」的类（确认存在，不展开）

| 类 | 关键成员 | 说明 |
| --- | --- | --- |
| `net.createmod.catnip.math.Pointing` | 枚举 `UP`/`LEFT`/`DOWN`/`RIGHT`；`getXRotation()`、`getCombinedDirection(Direction)` | **写 Ponder 必用**（`showControls` 第2参） |
| `net.createmod.catnip.animation.LerpedFloat` | `static linear()`、`static angular()`、`startWithValue(double)`、`chase(double,double,Chaser)`、`chaseTimed(double,int)`、`updateChaseTarget(float)`、`tickChaser()`、`getValue()`、`settled()` | 自定义元素动画时可选用 |
| `net.createmod.catnip.outliner.Outliner` | `static getInstance()`、`showOutline(Object, Outline)`、`showLine(Object, Vec3, Vec3)`、`chaseAABB(Object, AABB)`、`showAABB(Object, AABB, int)`、`showCluster(...)`、`showItem(...)`、`keep(Object)`、`remove(Object)`、`edit(Object)`、`renderOutlines(...)` | `Selection.makeOutline` 需要它 |

> 这三个类都打包在 **ponder 的同一个 jar** 里（`net/createmod/catnip/**`），不需要额外加依赖坐标。

---

# 八、本次未核实清单（与 00-索引.md 第 5 节一致）

- 所有方法的**参数名**：字节码无 `MethodParameters`，**读不到**。
- `PonderIndex.addPlugin` 的调用时机：**未验证**。
- `PonderScene.SceneCamera` / `SceneTransform` 的公开成员：`javap -public` 未输出（无公开成员或仅包内可见）。
- `PonderUI.of(...)` 之后如何上屏：**未验证**（只确认返回 `PonderUI`）。
- 各指令参数里 `boolean`/`float`/`int` 的语义：**未验证**（已在 10-scene.md 逐条标注）。
