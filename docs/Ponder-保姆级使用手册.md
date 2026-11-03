# Ponder「思索」保姆级使用手册

> 适用环境（就是本仓库的真实环境，逐条核对过）
>
> | 项目 | 版本 / 坐标 | 证据 |
> | --- | --- | --- |
> | Minecraft | 1.21.1 | `gradle.properties` |
> | NeoForge | 21.1.232 | `build.gradle` |
> | Create | 6.0.10-280 | `gradle.properties` |
> | Ponder | `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1` | `gradle.properties` + gradle 缓存里的 jar |
> | Create 源码参照 | `allapis/Create-mc1.21.1-dev`（同代 1.21.1，ponder 1.0.85） | 仓库内 |
>
> 本手册里所有「API 名字 / 参数 / lang key / 资源路径」都不是猜的：
> 是对 `ponder-neoforge-1.0.82+mc1.21.1.jar` 做 `javap` + 反编译、再和 Create 的实际调用对照后写下的。
> 第 11 章列了每一条结论的出处，怀疑时按出处自查。

---

## 0. 先花 5 分钟搞清楚要用的是什么

### 0.1 Ponder 是干什么的

Ponder（中文名「思索」）是 Create 团队做的**可交互动画教程播放器**，不是文档系统：

- 玩家在物品栏里**按住 `W` 键**（默认键位，代码里是 keyCode 87）悬停在某个物品上，游戏就把这个物品对应的「思索场景」播放出来；
- 场景本身是一段**脚本化的动画**：底板上逐块冒出机器、旋转、文字提示、粒子、玩家操作手势；
- 播放器带进度条，可以拖回去看任意关键帧（keyframe）。

你要做的是：**写一段 Java 剧本 + 提供一张结构文件 + 一句语言文本**，剩下的渲染、播放、索引界面由 Ponder 负责。

### 0.2 Ponder 不是什么（避免走错路）

- 不是 GUI 框架：它是**渲染层自绘**的，无法在里面放 Button/Label；要交互 UI 走原版 Screen。
- 不是服务端系统：整个库是**客户端专用**的（`@Mod(dist = Dist.CLIENT)`），随便 import 到 common 代码里就会让专有服务器崩。
- 不是「实时跑机器」：场景里的方块实体是**虚拟**的（`PonderLevel`），不会真的产出物品。机器的表现只能是「你手写出来的样子」。

### 0.3 你最终会产出 4 个东西

```
你的模组
├─ src/main/java/<包>/ponder/MyPonderPlugin.java        ← 入口（告诉 Ponder 你有场景）
├─ src/main/java/<包>/ponder/MyScenes.java              ← 剧本（真正的动画）
├─ src/main/resources/assets/<modid>/ponder/<id>.nbt    ← 结构（场景里摆好的一堆方块）
└─ src/main/resources/assets/<modid>/lang/en_us.json    ← 文本（标题和每一句台词）
```

> 本仓库已经写好一份**能直接编译**的示例，第 9 章整份贴出，源文件在 `docs/ponder-example/java/...`。
> 编译验证记录见第 11 章「证据清单」。

---

## 1. 30 分钟跑通第一个场景

下面按顺序做，每一步都给了「怎么确认这一步做对了」。

### 1.1 第一步：加依赖

`build.gradle` 里（本仓库已经加好了，见文件对应行）：

```gradle
repositories {
    maven { url = "https://maven.createmod.net" }        // Create / Ponder / Flywheel
    maven { url = "https://maven.ithundxr.dev/snapshots" } // Registrate
}

dependencies {
    // Ponder 本体：neoForge 版本要写 +mc<MC版本>
    implementation("net.createmod.ponder:ponder-neoforge:${ponder_version}+mc${minecraft_version}")
}
```

`gradle.properties`：

```properties
minecraft_version = 1.21.1
ponder_version = 1.0.82
```

**怎么确认**：`./gradlew dependencies --configuration compileClasspath | grep ponder` 能看到 `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1`。

> 坑：只写 `1.0.82` 不写 `+mc1.21.1`，坐标解析不到（该 artifact 的版本号就是这个完整字符串）。

### 1.2 第二步：写入口类（PonderPlugin）

Ponder 只有一个扩展点：`net.createmod.ponder.api.registration.PonderPlugin`。它一共 **6 个方法**，
只有 `getModId()` 是抽象方法（必须实现），其余 5 个都是 `default`（要哪个写哪个）：

| 方法 | 用途 |
| --- | --- |
| `getModId()` | **必须**。返回你的 modid |
| `registerScenes(helper)` | 注册场景（见 1.2/7.2） |
| `registerTags(helper)` | 注册索引标签（见第 7 章） |
| `registerSharedText(helper)` | 注册可复用文案（见 4.6） |
| `onPonderLevelRestore(PonderLevel)` | 结构落位后的一次性修正钩子（多部件方块实体才需要，Create 用它修动能控制器） |
| `indexExclusions(helper)` | 把某些物品从索引里排除（见 7.5） |

```java
package com.example.mymod.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class MyPonderPlugin implements PonderPlugin {

    public static final MyPonderPlugin INSTANCE = new MyPonderPlugin();

    @Override
    public String getModId() {
        return "mymod";          // ← 必须是你的 modid，lang key 前缀、schematic 命名空间都靠它
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        MyScenes.register(helper);      // ← 场景注册在这里集中
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        MyScenes.registerTags(helper);  // ← 索引页分类（可以不要）
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("needs_rotation", "This machine needs rotational force");
    }
}
```

**三个方法的区别**（很多人混）：

| 方法 | 什么时候被调用 | 里面能写什么 |
| --- | --- | --- |
| `registerScenes` | 注册阶段（首次打开思索界面 / `/ponder reload`） | 只用 `helper.addStoryBoard(...)`，**不要**碰世界、不要 new ItemStack 以外的东西 |
| `registerTags` | 同上，紧跟 scenes 之后 | 只注册标签，标签要能被 scenes 引用，所以必须在 scenes 之后 |
| `registerSharedText` | 同上，最后 | 只登记「可复用台词」，供 `.sharedText("key")` 用 |

**重要**：这三个方法会被**反复调用**（每次 `/ponder reload`、每次切换世界都可能触发）。所以：

- ❌ 不要在里面对自己的静态 List `add`（会越加越多 → 场景重复 N 次）
- ❌ 不要在里面读注册表之外的东西
- ✅ 全部写成幂等的「声明式登记」

### 1.3 第三步：把插件挂上去（客户端、FMLClientSetupEvent）

```java
package com.example.mymod.ponder;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "mymod", value = Dist.CLIENT)   // ← value = Dist.CLIENT 是硬要求
public final class MyPonderClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(MyPonderPlugin.INSTANCE));
    }
}
```

Create 自己也是这么做的（`CreateClient` 里 `PonderIndex.addPlugin(new CreatePonderPlugin());`，见第 11 章出处）。

**怎么确认**：启动客户端后在 `logs/latest.log` 里搜 `Registering Ponder Scenes took`——出现这行说明注册跑过了。
（首次打开思索界面/执行 `/ponder reload` 时才会打印。）

> 坑 1：放在 mod 构造器或 `FMLCommonSetupEvent` 里 → 专有服务器启动时加载 `net.minecraft.client.*` → `NoClassDefFoundError` 直接崩。必须 `Dist.CLIENT`。
> 坑 2：放在 `FMLClientSetupEvent` 之外（比如第一次按键才注册）→ 玩家第一次按住 W 时索引还没建好，悬停没反应。

### 1.4 第四步：写第一个剧本

剧本就是一个静态方法，签名**必须**是 `(SceneBuilder, SceneBuildingUtil)`：

```java
package com.example.mymod.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class MyScenes {

    public static void firstScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("my_first", "My first ponder scene");   // ① 标题（同时决定 lang key 前缀）
        scene.configureBasePlate(0, 0, 5);                  // ② 底板：5x5，从 (0,0,0) 起
        scene.idle(5);

        scene.world().showSection(util.select().layer(0), Direction.UP);   // ③ 显现第 0 层
        scene.idle(5);

        BlockPos machine = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)                        // ④ 显示 60 tick（=3 秒）的文字
             .text("This is a machine")
             .attachKeyFrame()                              //    并在这一句挂一个关键帧
             .placeNearTarget()
             .pointAt(util.vector().blockSurface(machine, Direction.NORTH));
        scene.idle(60);

        scene.effects().indicateSuccess(machine);
        scene.idle(20);

        scene.markAsFinished();                              // ⑤ 收尾
    }
}
```

**逐行说明**（这几行是 90% 场景的开头模板）：

| 行 | 作用 | 不写会怎样 |
| --- | --- | --- |
| `title(id, text)` | 设置 lang key 的 scene id 与默认标题 | 不写 → scene id 变成 `<ns>:missing_title`，标题显示成 key |
| `configureBasePlate(x,z,size)` | 阴影底板的位置与边长（世界坐标） | 不写 → 底板按结构边界自动算，通常偏 |
| `idle(n)` | 让**指令队列**停 n tick（20 tick = 1 秒） | 所有动作会挤在同一 tick 内 |
| `world().showSection(sel, dir)` | 让选区内方块带方向动画「浮现」 | 不调用 → 场景里空空如也 |
| `overlay().showText(ticks)` | 文字气泡 | — |
| `markAsFinished()` | 宣布结束：停止计时 + 标记 finished（源码 `stopCounting()` + `setFinished(true)`） | 场景停在最后一帧，标题栏不出现结束态 |

### 1.5 第五步：准备结构文件（.nbt）

这是**唯一必须动手做的美术活**。最小可行做法（详细版见第 5 章）：

1. 开创造超平坦世界，按你的剧本把机器搭在**地面 y=0 之上**，让它的**最下一层方块贴 y=0**；
2. 拿**结构方块**（`/give @s structure_block`）框住它，模式 `SAVE`，名称填 `my_first`，保存；
3. 到存档目录 `saves/<世界名>/generated/minecraft/structures/my_first.nbt` 把文件拷出来；
4. 放到 `src/main/resources/assets/mymod/ponder/my_first.nbt`（**目录名是 `ponder`，这是 Ponder 硬编码的**）。

**怎么确认路径对了**：进游戏 `/ponder <你的物品id>`，如果日志里出现
`Ponder schematic missing: mymod:ponder/my_first.nbt` → 路径或名字写错了。
（这行日志是 Ponder 自己的报错，见第 11 章出处。）

### 1.6 第六步：进游戏验证

| 操作 | 结果 |
| --- | --- |
| `/ponder index` | 打开思索总索引（所有注册过的条目） |
| `/ponder tags` | 按标签分类的索引 |
| `/ponder <物品或方块 id>` | 直接播放该组件的场景，例如 `/ponder mymod:my_machine` |
| `/ponder reload` | 热重载所有插件（改完剧本不用重启游戏） |
| 物品栏里按住 `W` 悬停物品 | 正常的玩家入口（需要该物品已被注册为组件） |

`/ponder <id>` 里的 `<id>` 是**组件 id**（你注册 `addStoryBoard(组件, ...)` 时的那个 key），不是剧本 id。这一条搞错的人非常多，见第 2.1 节。

> 两个关于命令本身的事实（对开服/联机有用）：
> 1. `/ponder` 是**服务端命令**（根权限等级 0，任何玩家可用；带 targets 的形态要权限 2）。
>    它靠「服务端发自定义包 → 客户端跳 UI」工作，所以联机时也能用，你的 mod 不需要也不应该再注册 `/ponder`。
> 2. `<id>` 找不到时只打一行 `Logger.error`（`Could not find ponder scenes for item: ...`），**不弹任何提示**，
>    所以「敲了没反应」= id 拼错或没注册。

### 1.7 第一个场景的自检清单

- [ ] 日志有 `Registering Ponder Scenes took`，没有 `schematic missing`
- [ ] `/ponder <组件id>` 能打开界面，底板和机器都在
- [ ] 标题不是 `mymod.ponder.my_first.header` 这种原始 key（若出现 → 第 6 章 lang）
- [ ] 文字第二句不是 `...text_2` 原文（同上）
- [ ] 进度条能拖回关键帧（`attachKeyFrame` 生效）
- [ ] 专有服务器启动不崩（客户端类没有泄漏）

---
## 2. 心智模型：Ponder 到底怎么跑（不懂这段，后面全是玄学）

### 2.1 三段式：注册 → 编译 → 播放

```
[1] 注册（客户端启动 / /ponder reload 时）
    PonderIndex.addPlugin(你的插件)
      └─ registerScenes(helper)   ← 你写：addStoryBoard(组件id, 剧本id, 你的剧本方法, 标签...)
      └─ registerTags(helper)     ← 你写：registerTag(...)
      └─ registerSharedText(...)  ← 你写：registerSharedText("key", "默认文本")

[2] 编译（玩家打开某组件的思索界面时，一次性）
    PonderSceneRegistry.compile(组件id)
      ├─ loadSchematic(剧本id) → assets/<ns>/ponder/<剧本id>.nbt  ← 结构文件在这里被读
      ├─ StructureTemplate.placeInWorld(PonderLevel)              ← 结构被摆进虚拟世界
      └─ 你的剧本方法被调用一次 → 把动作录成指令队列(schedule)
          （此时 showText 的文本被登记：text_1, text_2 ...）

[3] 播放（每客户端 tick 20 次/秒）
    PonderScene.tick()
      ├─ 推进当前指令（DelayInstruction 会卡住队列）
      ├─ 渲染 over/outlines/particles
      └─ 进度条 = currentTime / totalTime
```

**这里有三条极其重要的推论**，新手 90% 的「为什么不生效」都出在这三条：

1. **你的剧本方法只在打开界面时跑一次，不是每帧跑**。
   → 里面写 `Math.random()`、`System.currentTimeMillis()`、读玩家位置，都只采样一次；
   → 想让某个东西「一直转」，不能用循环 + idle 拼（那是逐帧手摆动画），要用「一次性指令 + 让虚拟世界自己 tick」。
   （`PonderLevel.tick()` 每 tick 都在跑，所以结构里的方块实体/粒子会自然动。）

2. **结构文件是「一次性快照」**。你在剧本里 `showSection`/`setBlock` 操作的都是这份拷贝；
   `restoreBlocks`/`restore` 会把备份还原回去（备份点在结构摆好之后建立）。

3. **指令队列是严格串行的**。`idle(n)` 就是一个「阻塞 n tick 的延时指令」，
   它后面的所有指令都得等它；你没写 `idle` 的两个动作会挤在同一 tick 发生。

### 2.2 时间轴：tick、idle、keyframe

| 概念 | 含义 | 实操建议 |
| --- | --- | --- |
| 1 tick | 1/20 秒 | 文字停留的最小单位 |
| `idle(20)` | 停 1 秒 | 动作之间的呼吸：`idle(5)`～`idle(10)` |
| `idleSeconds(2)` | = `idle(40)` | 只在长停顿用 |
| `showText(60)` | 文字**存活** 60 tick，**不阻塞** | 文字显示时长与后面 `idle(60)` 要配对 |
| `attachKeyFrame()` | 在这一句上打**关键帧**：玩家能把进度条拖回这里 | 每个「新知识点」一句，一个场景 3～6 个 |
| `addKeyframe()` | 手动打关键帧（不依附文字） | 用于「结构出现」的节点 |
| `addLazyKeyframe()` | 延迟型关键帧（源码：记录时刻 = 当前 + **6 tick**） | `attachKeyFrame()` 内部用的就是它；想精确控制落点就手写 `addKeyframe()` |

> **为什么需要关键帧**：玩家可以点「Think Back」往回跳。跳转依赖 `keyframeTimes`。
> 反编译源码里 `PonderScene.markKeyframe` 只有在 `!stoppedCounting` 时才记录，
> 而 `seekToTime` 不允许往回跳（`Cannot seek backwards`）——所以播放器内部是靠「重放到关键帧」实现的。
> 实操经验：**文字密度 = 关键帧密度**，一个场景 3～6 个关键帧最舒服。

**节奏模板**（可直接套）：

```text
idle(5)              开场定格
呈现结构           idle(10)
第一句文字(60)      idle(60)
新动作             idle(20)
第二句文字(60)      idle(60)
...
markAsFinished()
```

### 2.3 坐标系与底板（99% 的「机器歪了」都在这）

Ponder 场景用的就是**普通的方块坐标系**，但有三件事要同时对齐：

1. **结构文件的原点**：结构 NBT 的最小角被摆在场景的 `(0,0,0)`。所以存档里搭的东西，
   最下一层要贴地（y=0 是场景地面）。
2. **底板**：`configureBasePlate(xOffset, zOffset, size)` 画一块 `size × size` 的阴影平台，
   从 `(xOffset, 0, zOffset)` 开始。`showBasePlate()` 会把它当作一个 section「浮现」出来
   （源码里就是 `select().cuboid(new BlockPos(xOffset,0,zOffset), new Vec3i(size-1,0,size-1))`）。
3. **相机默认视角**：`←35° 俯角 / 145° 水平`，看向 `(0,4,0)` 附近（`PointOfInterest` 初值）。

对齐口诀：

```text
结构里机器放在 (2,1,2) 附近（size=5 时是正中心）
底板参数写 (0, 0, 5)
相机不用调，默认就好看
要挪视角：scaleSceneView(放大) / setSceneOffsetY(上下) / rotateCameraY(左右转)
```

| 方法 | 作用 | 常用值 |
| --- | --- | --- |
| `scaleSceneView(float)` | 整体缩放 | `1`（默认）、`1.5`（近景机器）、`0.8`（大结构） |
| `setSceneOffsetY(float)` | 场景上下平移（相对屏幕） | `-1`（结构偏高时往下压） |
| `removeShadow()` | 去掉底板阴影 | 极少数「悬浮场景」才用 |
| `rotateCameraY(float)` | 水平旋转视角（指令，可中途调用） | `-30`、`90` |
| `configureBasePlate` / `showBasePlate` | 见上 | — |

### 2.4 元素（Element）与 ElementLink

场景里「能动的东西」都是元素：

| 元素 | 是什么 | 你怎么拿到 |
| --- | --- | --- |
| `WorldSectionElement` | 一组方块（可以整体移动/旋转/淡入淡出） | `world().showIndependentSection(sel, dir)` 返回 `ElementLink<WorldSectionElement>` |
| `TextWindowElement` | 文字气泡 | `overlay().showText(n)` 返回 `TextElementBuilder`（一般不用留引用） |
| `InputWindowElement` | 鼠标/键盘操作提示 | `overlay().showControls(...)` |
| `EntityElement` | 实体 | `world().createEntity(...)` / `createItemEntity(...)` |
| `AnimatedSceneElement` | 动画元素基类 | `special().createBirb(...)`、`createCart(...)` |
| `BeltItemElement`（Create） | 传送带上的物品 | `CreateSceneBuilder.world().createItemOnBelt(...)` |

**`ElementLink<T>` 是句柄**，不是对象本身。用法：

```java
ElementLink<WorldSectionElement> link =
        scene.world().showIndependentSection(util.select().fromTo(2,1,2, 2,4,2), Direction.DOWN);
scene.idle(20);
scene.world().moveSection(link, util.vector().of(0, 1, 0), 20);    // 20 tick 内上移 1 格
scene.world().rotateSection(link, 0, 45, 0, 40);                   // 40 tick 内水平转 45°
scene.world().configureCenterOfRotation(link, util.vector().centerOf(2, 1, 2)); // 转轴（不设会绕奇怪的地方转）
scene.world().configureStabilization(link, util.vector().of(0, 1, 0));         // 反向补偿，让结构看起来只转不飘
```

**把句柄还原成元素**（进阶，80% 的场景用不到）：

```java
PonderScene s = scene.getScene();                 // SceneBuilder -> PonderScene
WorldSectionElement el = s.resolve(link);          // 拿到元素实例
s.runWith(link, e -> { /* ... */ });               // 直接对元素操作
```

> 坑：`moveSection` / `rotateSection` 的时长参数是 **tick**，不是秒。写 `2` 不是「2 秒」而是「2 tick = 0.1 秒」，看起来像瞬移。
> 坑 2：`rotateSection` 不配 `configureCenterOfRotation`，结构会绕底板原点画大圈。

### 2.5 场景里没有「玩家」

- 没有真实玩家实体；想表现「玩家右键」，只能用 `overlay().showControls(...)` + `.rightClick().withItem(stack)` 画手势。
- 实体可以用 `world().createEntity(...)` 造，但它们是**假实体**（`PonderLevel` 里的 spawn），不会跟你交互。
- `scene.getWorld()` 拿到 `PonderLevel`：可以 `getBlockState`/`setBlock`/`addParticle`/`setMask`，但**别在里面做逻辑分支等 tick 结果**——剧本是编译期跑的。

### 2.6 客户端限定与两端分离

| 事实 | 影响 |
| --- | --- |
| Ponder 的所有类都是客户端类 | 你的 `PonderPlugin` 实现类**只能**在 `Dist.CLIENT` 的代码路径里被 new |
| `PonderPlugin.registerScenes` 在客户端跑 | 里面不要访问服务端状态 |
| `@EventBusSubscriber(value = Dist.CLIENT)` | 挂载入口的正确姿势 |
| 专有服务器没有 Ponder | 你的 surplus 调用必须 `DistExecutor`/`@EventBusSubscriber(Dist.CLIENT)` 隔离，否则崩服 |

**稳妥写法**：所有 Ponder 相关代码放在 `<包>.ponder` 子包，并且**只**被一个 `Dist.CLIENT` 的事件类引用。
这样即使专有服务器加载你的 mod，也不会碰到这些类。

---

## 3. API 地图（按「我要做什么」查）

> 完整逐方法说明见附录 `docs/ponder-api/`（scene / element / registration / foundation 四份）。
> 这里只给「你要的能力 → 对应方法」索引。

### 3.1 顶层：`SceneBuilder`

| 我想… | 方法 |
| --- | --- |
| 设标题 | `title(String sceneId, String title)` |
| 设底板 | `configureBasePlate(int x, int z, int size)` / `showBasePlate()` |
| 缩放/平移/去阴影 | `scaleSceneView(float)` / `setSceneOffsetY(float)` / `removeShadow()` |
| 等待 | `idle(int ticks)` / `idleSeconds(int s)` |
| 打关键帧 | `addKeyframe()` / `addLazyKeyframe()` |
| 转视角 | `rotateCameraY(float deg)` |
| 结束 | `markAsFinished()` / `setNextUpEnabled(boolean)` |
| 塞任意指令 | `addInstruction(Consumer<PonderScene>)` |

### 3.2 `world()`：`WorldInstructions`

**结构**（区块）

| 我要 | 方法 |
| --- | --- |
| 显示一块结构（融入底板） | `showSection(Selection, Direction)` |
| 显示并合并到已有大区块 | `showSectionAndMerge(Selection, Direction, ElementLink<WorldSectionElement>)` |
| 独立区块（能整体移动/旋转） | `showIndependentSection(Selection, Direction)` → `ElementLink` |
| 立刻显示（无动画） | `showIndependentSectionImmediately(Selection)` |
| 藏起来 | `hideSection(Selection, Direction)` / `hideIndependentSection(link, Direction)` |
| 把一个选区「拆」成独立区块 | `makeSectionIndependent(Selection)` → `ElementLink` |
| 粘到某个区块上一起动 | `glueBlockOnto(BlockPos, Direction, ElementLink)` |
| 平移 | `moveSection(link, Vec3 offset, int ticks)` |
| 旋转 | `rotateSection(link, double x, double y, double z, int ticks)` |
| 转轴 / 反向补偿 | `configureCenterOfRotation(link, Vec3)` / `configureStabilization(link, Vec3)` |

**方块**

| 我要 | 方法 |
| --- | --- |
| 放一个方块 | `setBlock(BlockPos, BlockState, boolean spawnParticles)` |
| 批量放 | `setBlocks(Selection, BlockState, boolean)` |
| 替换（保留方块实体数据） | `replaceBlocks(Selection, BlockState, boolean)` |
| 改状态 | `modifyBlock(BlockPos, UnaryOperator<BlockState>, boolean)` / `modifyBlocks(Selection, ...)` |
| 循环切属性（比如 facing） | `cycleBlockProperty(BlockPos, Property<?>)` |
| 敲掉 | `destroyBlock(BlockPos)` |
| 挖坏进度条 | `incrementBlockBreakingProgress(BlockPos)` |
| 红石开关 | `toggleRedstonePower(Selection)` |
| 还原 | `restoreBlocks(Selection)` |

**方块实体 / 实体**

| 我要 | 方法 |
| --- | --- |
| 改 BE 的字段 | `modifyBlockEntity(BlockPos, Class<T>, Consumer<T>)` |
| 改 BE 的 NBT | `modifyBlockEntityNBT(Selection, Class, Consumer<CompoundTag>[, boolean])` |
| 造实体 | `createEntity(Function<Level, Entity>)` → `ElementLink<EntityElement>` |
| 造掉落物 | `createItemEntity(Vec3 pos, Vec3 motion, ItemStack)` |
| 批量改实体 | `modifyEntities(Class<T>, Consumer<T>)` / `modifyEntitiesInside(Class<T>, Selection, Consumer<T>)` |
| 改一个实体 | `modifyEntity(ElementLink<EntityElement>, Consumer<Entity>)` |

### 3.3 `overlay()`：`OverlayInstructions`

| 我要 | 方法 |
| --- | --- |
| 一句话 | `showText(int ticks)` → `TextElementBuilder` |
| 文字 + 高亮框 | `showOutlineWithText(Selection, int ticks)` |
| 鼠标/键盘手势 | `showControls(Vec3, Pointing, int ticks)` → `InputElementBuilder` |
| 描一个选区 | `showOutline(PonderPalette, Object key, Selection, int)` |
| 追着一个 AABB 描边 | `chaseBoundingBoxOutline(PonderPalette, Object key, AABB, int)` |
| 两点画线 | `showLine(PonderPalette, Vec3 a, Vec3 b, int)` / `showBigLine(...)` |
| 滚动条 UI | `showScrollInput` / `showCenteredScrollInput` / `showRepeaterScrollInput` |
| 过滤槽 UI | `showFilterSlotInput(Vec3, int)` / `(Vec3, Direction, int)` |

`TextElementBuilder` 链式：

| 调用 | 作用 |
| --- | --- |
| `.text(String)` / `.text(String, Object... args)` | 设文案（会被登记成 `text_N`） |
| `.sharedText(String)` / `.sharedText(ResourceLocation)` | 用共享文案（见 1.2 的 registerSharedText） |
| `.colored(PonderPalette)` | 气泡颜色（枚举见下） |
| `.placeNearTarget()` | 贴着 `pointAt` 的目标放（不写就在上方） |
| `.pointAt(Vec3)` | 指定指向的位置 |
| `.attachKeyFrame()` | 在这句打关键帧 |
| `.independent(int)` / `.independent()` | 独立文字（不跟随视角/不自动隐藏） |

`PonderPalette` 全部取值：
`WHITE` `BLACK` `RED` `GREEN` `BLUE` `SLOW` `MEDIUM` `FAST` `INPUT` `OUTPUT`
（`SLOW/MEDIUM/FAST` 用于转速语义，`INPUT/OUTPUT` 用于输入输出，其余是纯色）。

`InputElementBuilder`：

| 调用 | 作用 |
| --- | --- |
| `.leftClick()` / `.rightClick()` / `.scroll()` | 画哪种手势 |
| `.withItem(ItemStack)` | 手上拿什么物品 |
| `.whileSneaking()` / `.whileCTRL()` | 附加按键 |
| `.showing(ScreenElement)` | 显示一个自定义图标 |

### 3.4 `effects()`：`EffectInstructions`

| 我要 | 方法 |
| --- | --- |
| 成功提示（绿勾粒子） | `indicateSuccess(BlockPos)` |
| 红石提示 | `indicateRedstone(BlockPos)` |
| 红石粒子 | `createRedstoneParticles(BlockPos, int color, int amount)` |
| 自定义粒子 | `emitParticles(Vec3, ParticleEmitter, float velocity, int amount)` |
| 造发射器 | `simpleParticleEmitter(ParticleOptions, Vec3)` / `particleEmitterWithinBlockSpace(ParticleOptions, Vec3)` |

### 3.5 `special()`：`SpecialInstructions`

| 我要 | 方法 |
| --- | --- |
| 让鹦鹉出场（彩蛋/教学梗） | `createBirb(Vec3, Supplier<? extends ParrotPose>)` / `changeBirbPose(...)` / `moveParrot(...)` / `rotateParrot(...)` |
| 矿车 | `createCart(Vec3, float, MinecartElement.MinecartConstructor)` / `moveCart(...)` / `rotateCart(...)` |
| 移动注意力点（相机 POI） | `movePointOfInterest(Vec3)` / `movePointOfInterest(BlockPos)` |
| 隐藏动画元素 | `hideElement(ElementLink<T>, Direction)` |

### 3.6 `debug()`：`DebugInstructions`

| 方法 | 作用 |
| --- | --- |
| `debugSchematic()` | **一键把整张结构显示出来**（排查「我的方块到底在不在」时最好用） |
| `enqueueCallback(Consumer<PonderScene>)` | 队列空闲时执行回调（可以在这里读虚拟世界做断言/打印） |
| `addInstructionInstance(PonderInstruction)` | 塞原生指令实例（进阶） |

### 3.7 选址三件套：`SceneBuildingUtil`

```java
util.select()   // SelectionUtil：选方块集合
util.grid()     // PositionUtil：拿 BlockPos
util.vector()   // VectorUtil：拿 Vec3（画文字/粒子用）
```

| `select()` | 说明 |
| --- | --- |
| `.position(int x,y,z)` / `.position(BlockPos)` | 单格 |
| `.fromTo(int..., int...)` / `.fromTo(BlockPos, BlockPos)` | 两个角之间的长方体（**含两端**） |
| `.cuboid(BlockPos origin, Vec3i size)` | 原点 + 尺寸（`size` 是**跨度-1**） |
| `.layer(int y)` / `.layers(int from, int to)` / `.layersFrom(int y)` | 整层/多层 |
| `.column(int x, int z)` | 整列（从底到顶） |
| `.everywhere()` | 全场景 |

`Selection` 支持 `.add(Selection)`、`.substract(Selection)`、`.copy()`、`.getCenter()`，也可以直接 `for (BlockPos p : sel)` 迭代。

| `vector()` | 说明 |
| --- | --- |
| `.centerOf(int x,y,z)` / `.centerOf(BlockPos)` | 方块中心 |
| `.topOf(...)` | 方块顶面中心 |
| `.blockSurface(BlockPos, Direction)` / `(BlockPos, Direction, float)` | 某一面中心（文字指向神器） |
| `.of(double, double, double)` | 任意偏移 |

| `grid()` | 说明 |
| --- | --- |
| `.at(int x,y,z)` / `.zero()` | BlockPos |

---
## 4. 分镜实操：每个动作怎么做（可抄代码 + 坑）

> 用法：按你要表达的意思找到对应小节，把代码贴进剧本，改坐标即可。
> 所有片段都来自 Create 1.21.1 的真实场景或本手册已编译验证的示例。

### 4.1 开场三件套

```java
scene.title("machine_id", "Doing the thing");   // ① lang key 前缀 + 默认标题
scene.configureBasePlate(0, 0, 5);              // ② 5x5 底板，左下角在 (0,0,0)
scene.idle(5);                                  // ③ 定格一下再开始
scene.world().showSection(util.select().layer(0), Direction.UP); // 显示地板
scene.idle(5);
```

### 4.2 显示结构：三种「浮现」方式

```java
// (a) 逐块浮现，并「焊」进底板（最常见的机器出场）
scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 2, 2), Direction.DOWN);

// (b) 独立区块：之后能整体移动/旋转
ElementLink<WorldSectionElement> contraption =
        scene.world().showIndependentSection(util.select().fromTo(2, 1, 2, 2, 3, 2), Direction.DOWN);

// (c) 立刻出现（没有生长动画，调试用）
scene.world().showIndependentSectionImmediately(util.select().everywhere());
```

**`Direction` 到底是什么意思**（源码级确认，别再猜）：
Ponder 会把元素先**偏移 0.5 格**放在这个方向上，再一边淡入一边滑回原位
（`setFadeVec(Vec3.atLowerCornerOf(direction.getNormal()).scale(0.5))`）。所以它表示的是「**从哪边滑进来**」：

| 参数 | 效果 |
| --- | --- |
| `Direction.UP` | 从**上方**落下（元素先出现在上方 0.5 格） |
| `Direction.DOWN` | 从**下方**升起 |
| `Direction.NORTH` | 从北面推进来 |
| `Direction.SOUTH/EAST/WEST` | 同上，各侧面 |

> Create 的惯例：地板（第 0 层）用 `Direction.UP`（从上方盖下来），机器用 `Direction.DOWN`（从地里升起）。
> 这两个刚好和中文直觉相反，是本手册里最常被搞反的一处。

### 4.3 移动 / 旋转结构

```java
// 平移 1 格，20 tick
scene.world().moveSection(contraption, util.vector().of(0, 1, 0), 20);
scene.idle(20);

// 绕自身中心转 45°（先设转轴，否则绕底板原点飞出去）
scene.world().configureCenterOfRotation(contraption, util.vector().centerOf(2, 1, 2));
scene.world().rotateSection(contraption, 0, 45, 0, 40);
scene.idle(40);

// 让旋转中的结构「原地转」而不是画圈：加反向补偿
scene.world().configureStabilization(contraption, util.vector().centerOf(2, 1, 2));
```

### 4.4 改方块

```java
// 换一个方块
scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.STONE.defaultBlockState(), true);

// 只改某个属性（保留其它状态）—— 例如让朝向变 Y 轴
scene.world().modifyBlock(pos, s -> s.setValue(CustomBlock.AXIS, Direction.Axis.Y), true);

// 循环切属性（每次「右键」切一格）
scene.world().cycleBlockProperty(pos, CustomBlock.POWERED);

// 红石开关（拉杆/按钮）
scene.world().toggleRedstonePower(util.select().fromTo(3, 1, 0, 3, 1, 2));

// 敲掉（带破坏动画）
scene.world().destroyBlock(pos);

// 撤销所有方块改动
scene.world().restoreBlocks(util.select().everywhere());
```

> 布尔参数 `spawnParticles` 传 `true` 会有「放置/破坏」粒子，视觉上更像真的在动手。

### 4.5 方块实体、实体、掉落物

```java
// 直接改方块实体的字段（编译期执行！）
scene.world().modifyBlockEntity(pos, MyBlockEntity.class, be -> be.setSomething(42));

// 改 NBT（适合跨版本/复杂数据）
scene.world().modifyBlockEntityNBT(util.select().position(pos), MyBlockEntity.class,
        tag -> tag.putInt("Something", 42));

// 造一个掉落物（pos 是中心点，motion 是初速度）
scene.world().createItemEntity(util.vector().centerOf(2, 4, 2), util.vector().of(0, 0, 0),
        new ItemStack(Items.IRON_INGOT));

// 造任意实体
ElementLink<EntityElement> zombie = scene.world().createEntity(world -> {
    Zombie z = new Zombie(world);
    z.setPos(2.5, 1, 2.5);
    return z;
});
scene.world().modifyEntity(zombie, e -> ((Zombie) e).setBaby(true));
```

> 坑：`createEntity` 里返回的实体必须自己 `setPos`，否则出现在 (0,0,0)。
> 坑：`modifyEntity` 拿到的是 `PonderLevel` 里的假实体，别指望它 tick 出 AI 行为。

### 4.6 文字（最重要的表现手段）

```java
// 基础
scene.overlay().showText(60)
     .text("This machine collects neutron matter")
     .attachKeyFrame()                 // 打关键帧
     .placeNearTarget()
     .pointAt(util.vector().blockSurface(pos, Direction.NORTH));
scene.idle(60);

// 带参数（%s 之类）：注意原文里要有占位符
scene.overlay().showText(60).text("Output: %s items", 16);

// 共享文案（在 registerSharedText 里登记过）
scene.overlay().showText(50).sharedText("needs_rotation");

// 带颜色 + 不贴身
scene.overlay().showText(60)
     .text("Power flows this way")
     .colored(PonderPalette.INPUT)
     .independent(80)                 // 独立显示 80 tick
     .pointAt(util.vector().of(2.5, 2, 2.5));
```

**文字排版的三个经验**：

1. `showText(n)` 的 `n` 是**存活时长**，不是「弹出生效的延时」。你还要自己 `idle(n)` 才看得完。
2. 想要文字**先后出现**就 `showText(...)` 后立刻 `idle(...)`；想**同时存在**就别 idle，连着写。
3. `.placeNearTarget()` 会让气泡贴着 `pointAt` 目标，读起来比浮在正中清楚得多。

### 4.7 高亮 / 轮廓 / 线

```java
// 给一个选区描边（key 用任意稳定对象，例如 BlockPos 或字符串）
scene.overlay().showOutline(PonderPalette.OUTPUT, pos, util.select().fromTo(2,1,2, 2,2,2), 60);

// 大线：从一个点到另一个点画一条粗线（指导流向）
scene.overlay().showBigLine(PonderPalette.INPUT, util.vector().centerOf(2, 2, 4),
        util.vector().centerOf(2, 2, 2), 60);

// 追着一个 AABB 描边（会跟着动，用于动态结构）
scene.overlay().chaseBoundingBoxOutline(PonderPalette.MEDIUM, pos, new AABB(pos).move(0, 0, 0), 60);

// 文字 + 高亮一起
scene.overlay().showOutlineWithText(util.select().position(pos), 60)
     .text("This slot takes the input");
```

### 4.8 玩家操作提示（手势）

```java
// 右键 + 手持扳手
scene.overlay().showControls(util.vector().topOf(pos), Pointing.DOWN, 30)
     .rightClick()
     .withItem(AllItems.WRENCH.asStack());
scene.idle(30);

// 滚动 + 潜行
scene.overlay().showControls(util.vector().centerOf(pos), Pointing.LEFT, 40)
     .scroll()
     .whileSneaking();
scene.idle(40);

// 过滤槽 UI（Create 风格）
scene.overlay().showFilterSlotInput(util.vector().centerOf(pos), Direction.NORTH, 80);
```

> `Pointing` 来自 `net.createmod.catnip.math.Pointing`，取值 `UP/DOWN/LEFT/RIGHT`（箭头指向哪边）。

### 4.9 特效

```java
scene.effects().indicateSuccess(pos);          // 绿勾
scene.effects().indicateRedstone(pos);         // 红石闪
scene.effects().createRedstoneParticles(pos, 0xFFFFFF, 10);
// 自定义粒子：发射器 + 速度 + 数量
scene.effects().emitParticles(util.vector().centerOf(2, 2, 2),
        scene.effects().simpleParticleEmitter(ParticleTypes.END_ROD, util.vector().of(0, 0.2, 0)), 1, 20);
```

### 4.10 镜头

```java
scene.rotateCameraY(-30);            // 视角水平转 -30°（指令，插值执行）
scene.scaleSceneView(1.6f);          // 拉近（配合小机器）
scene.setSceneOffsetY(-1);           // 整体下移
scene.special().movePointOfInterest(util.vector().centerOf(2, 2, 2)); // 视线焦点挪到机器
```

### 4.11 收尾

```java
scene.idle(20);
scene.markAsFinished();          // 宣布结束（进度条走满、出现 "Up Next"/结束）
// 可选：最后一个场景关掉 "Up Next"
scene.setNextUpEnabled(false);
```

---
## 5. 结构文件 `.nbt` 的制作（最容易翻车的一章）

### 5.1 硬规则（源码级）

Ponder 读结构的代码就这三行（反编译自 `PonderSceneRegistry`）：

```java
String path = "ponder/" + location.getPath() + ".nbt";
ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path);
// 找不到就 log.error("Ponder schematic missing: " + loc) 并返回空结构
StructureTemplate t = PonderSceneRegistry.loadSchematic(stream);   // GZIP + NBT
activeTemplate.placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, new StructurePlaceSettings(), level.random, 2);
```

翻译成人话：

| 规则 | 内容 |
| --- | --- |
| 路径 | `assets/<modid>/ponder/<剧本id>.nbt`（**变量是「剧本id」，不是物品 id**） |
| 剧本id | `addStoryBoard(组件, "my/id", ...)` 的第二个参数；可以带子目录，如 `"machines/collector"` → `ponder/machines/collector.nbt` |
| 格式 | **GZIP 压缩的 NBT**，即 `StructureTemplate` 格式（结构方块存出来的就是它） |
| 落点 | 结构的**最小角**被摆在场景 `(0,0,0)` |
| 缺失时 | 只打一行 error 日志，场景仍然能播（世界里什么都没有） |

### 5.2 用结构方块导出（推荐流程，照着做）

1. 开一个**创造超平坦**世界（`/gamemode creative`，平坦预设即可）。
2. 按剧本坐标搭机器。**关键**：让机器的**最下一层方块贴在 y=0 的地面上**。
   例：剧本里机器在 `util.grid().at(2, 1, 2)` → 结构里它就应该在局部坐标 `(2, 1, 2)`。
   同时让所有方块都在 `x,z ∈ [0, size-1]` 范围内（底板 `size` 通常 5 或 7）。
3. `/give @s structure_block`，把它放在结构的一个角，打开 GUI：
   - 模式 = `SAVE`
   - 结构名称 = `my_first`
   - 相对位置/尺寸 = 框住整个结构
   - **勾掉「包含实体」**（除非你确实要结构里带实体）
   - 点 `DETECT` 再 `SAVE`
4. 到存档里拿文件：
   `saves/<世界名>/generated/minecraft/structures/my_first.nbt`
5. 复制到 `src/main/resources/assets/<modid>/ponder/my_first.nbt`。
6. Dev 环境特别注意：**运行时真正读的是 `build/resources/main`**（`processResources` 的产物），
   见本仓库 `build/resources/main/assets/...`。所以改完源资源要跑一次
   `./gradlew processResources`（或 `./gradlew build`），再 `F3+T` 重载资源包。

### 5.3 坐标系对齐表（照着填就不会歪）

| 你想表达 | 结构里怎么摆 | 剧本里怎么写 |
| --- | --- | --- |
| 机器在底板正中心（5x5） | 机器底部落在 `(2,1,2)` | `configureBasePlate(0,0,5)` + `grid().at(2,1,2)` |
| 7x7 底板 | 中心是 `(3,1,3)` | `configureBasePlate(0,0,7)` |
| 地面层 | y=0 的那一层方块 | `select().layer(0)` |
| 机器上方留 3 格 | 结构高度至少到 y=4 | `grid().at(2,4,2)` 能用 |
| 侧边传动力（传动杆在南侧） | 杆在 `(2,1,4)` | `grid().at(2,1,4)` |

> 记法：**结构文件里的局部坐标 = 剧本里的 `util.grid()` 坐标**。两边对不上的唯一原因就是你搭的时候没让最小角落在 (0,0)。

### 5.4 方块实体的数据

- 结构方块保存时会把方块实体 NBT 一起存（容器内容、朝向、速度……），Ponder 落位时按 `placeInWorld(..., flags=2)` 放置，
  **方块实体数据会跟着来**。
- 想改 BE 字段：在剧本里用 `modifyBlockEntity(...)` / `modifyBlockEntityNBT(...)`（见 4.5）。
- Create 自己还需要一次性修正：
  `CreatePonderPlugin.onPonderLevelRestore` 里调 `PonderWorldBlockEntityFix.fixControllerBlockEntities(ponderLevel)`。
  这是 Create 内部处理（动能控制器 BE 的引用重建），**第三方 addon 通常不需要**，除非你也用了「主控方块实体」模式。

### 5.5 失败症状对照表

| 症状 | 原因 | 修法 |
| --- | --- | --- |
| 场景全空，日志 `Ponder schematic missing: ns:ponder/x.nbt` | 路径/文件名/命名空间错 | 核对 `assets/<modid>/ponder/<剧本id>.nbt` |
| 日志 `Failed to read ponder schematic` | 不是 GZIP+NBT（比如存成了 .schem / 未压缩） | 用结构方块重存；或 `gzip` 压一遍 |
| 场景里有方块但位置全歪 | 结构最小角不在 (0,0,0) | 重搭时把方块挪到 0 起步 |
| 机器缺零件（只有一部分） | 结构方块尺寸框小了 | 加大尺寸重新 SAVE |
| 方块变成「空气/未知方块」 | 结构里含其它模组方块，但测试环境没装该模组 | 测试环境装齐依赖 |
| 改完 .nbt 游戏里没变化 | 没同步到 `build/resources/main`，或没重载资源 | `./gradlew processResources` + `F3+T` |

### 5.6 没有结构文件时的降级方案（纯代码搭）

调试期（或做「程序生成教学场景」）可以完全不用 .nbt：

```java
scene.debug().debugSchematic();            // 先让整张结构可见（这里其实是空的）
scene.world().setBlocks(util.select().fromTo(0,0,0, 4,0,4), Blocks.STONE.defaultBlockState(), false);
scene.world().setBlock(util.grid().at(2,1,2), ModBlocks.KINETIC_NEUTRON_COLLECTOR.get().defaultBlockState(), true);
```

> 这样能验证剧本逻辑、文字、节奏，但**先别指望方块实体有数据**。
> 优点：改一行看一眼，不用反复结构方块导出；缺点：结构一复杂就该上 .nbt 了。

---

## 6. 本地化：中文标题与台词（第二大翻车点）

### 6.1 真相：你写在代码里的文字**平时根本不会显示**

这是所有新手第一个「为什么显示成一串奇怪 key」的原因。看源码：

```java
// PonderLocalization: 正常模式下，文本一律走 I18n
public String getSpecific(ResourceLocation sceneId, String k) {
    if (PonderIndex.editingModeActive()) {             // 编辑模式：用你代码里的默认文本
        return specific.get(sceneId).get(k);
    } else {
        return I18n.get(langKeyForSpecific(sceneId, k));   // 正常模式：查 lang！
    }
}
```

也就是说：`.text("This is a machine")` 里的英文只是**默认值/占位**，
玩家真正看到的是 lang 文件里对应 key 的内容；**lang 里没有这个 key，就显示 key 本身**。

### 6.2 key 规则表（照抄即可）

| 内容 | lang key | 例子 |
| --- | --- | --- |
| 场景标题 | `<modid>.ponder.<sceneId>.header` | `mymod.ponder.my_first.header` |
| 第 N 句文字 | `<modid>.ponder.<sceneId>.text_<N>` | `mymod.ponder.my_first.text_1` |
| 共享文字 | `<modid>.ponder.shared.<key>` | `mymod.ponder.shared.needs_rotation` |
| 标签名 | `<modid>.ponder.tag.<tagId>` | `mymod.ponder.tag.neutron_machines` |
| 标签描述 | `<modid>.ponder.tag.<tagId>.description` | 同上 + `.description` |

其中：

- **`<sceneId>` 来自 `scene.title("my_first", "...") 的第一个参数**——不是物品 id，也不是 .nbt 文件名。
  不调用 `title()` 时它默认是 `missing_title`（源码里就是这么写的）。
- **`<N>` 从 1 开始**，按剧本里 `.text()` 的**调用顺序**自增（`textIndex` 初值 1）。
- **`.sharedText()` 不占号**（源码：它走 `getShared`，不调用 `registerText`）。
  所以混用 sharedText 不会打乱 `.text_1/.text_2` 的编号，但插一句 `.text()` 会让后面所有编号顺移 —— **改剧本后一定要回头核对 lang 编号**。

### 6.3 落地方式 A：手写 lang（最简单）

`src/main/resources/assets/<modid>/lang/en_us.json`：

```json
{
  "mymod.ponder.my_first.header": "My First Machine",
  "mymod.ponder.my_first.text_1": "This machine does the thing",
  "mymod.ponder.my_first.text_2": "And this is how you power it",
  "mymod.ponder.shared.needs_rotation": "It needs rotational force",
  "mymod.ponder.tag.neutron_machines": "Neutron Machines",
  "mymod.ponder.tag.neutron_machines.description": "Machines that condense neutron matter"
}
```

`src/main/resources/assets/<modid>/lang/zh_cn.json` 写中文即可。

> 本模组已有 `ModLanguageProvider` / `ModLanguageProviderZhCn`（datagen），可以在这两个类里加同样的 key。

### 6.4 落地方式 B：datagen 自动生成（推荐给要维护的项目）

Ponder 提供了把「代码里写的默认文案」导出成 lang 的 API：

```java
// 在 GatherDataEvent 的 LANGUAGE provider 里
PonderIndex.addPlugin(MyPonderPlugin.INSTANCE);   // datagen 期间不会跑 FMLClientSetupEvent，必须手动加
PonderIndex.getLangAccess().provideLang("mymod", (key, value) -> {
    langProvider.add(key, value);                  // 由你的 LanguageProvider 收集
});
```

Create 就是这么干的（`CreateDatagen.providePonderLang`，注释里明确写了「FMLClientSetupEvent does not run during datagen」）。

优点：`.text("English")` 改一句，en_us.json 自动跟着改，key 永远对得上。
缺点：`.text()` 的默认文案必须写成**英文**（它会被当成 en_us 的正式文案）；中文仍需手写 zh_cn。

### 6.5 编辑模式（开发期神器）

Ponder 有个客户端配置开关：

```toml
# run/config/ponder-client.toml
[client]
    editingMode = true
```

打开后（`PonderConfig.client().editingMode`）：

- 文案**直接显示你在代码里写的默认值**，不用写 lang 就能看见文字；
- 场景脚本重载更频繁，方便调整；
- 索引界面会多出调试信息。

**开发期强烈建议打开**：先看效果，最后再补 lang。

### 6.6 语言问题速查

| 症状 | 原因 | 修法 |
| --- | --- | --- |
| 标题显示成 `mymod.ponder.xxx.header` | lang 缺 key | 补 `header` 条目 |
| 第 2 句显示成 `...text_2` | 编号对不上（你插了一句 `.text()`） | 数一遍调用顺序重排编号 |
| 英文正常，中文也是英文 | zh_cn.json 没写 / 没打包进去 | 检查 `assets/<modid>/lang/zh_cn.json` |
| 全部显示正常但共享文字是 key | `sharedText` 的 key 规则是 `shared.` 不是 `text_` | 用 6.2 表的写法 |
| 改了 lang 没生效 | 没 `F3+T` 重载资源（lang 属于资源包） | 重载资源包 |

---

## 7. 标签、索引、排序、排除

### 7.1 注册一个标签

```java
public static final ResourceLocation TAG_MACHINES =
        ResourceLocation.fromNamespaceAndPath("mymod", "machines");

@Override
public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
    helper.registerTag(TAG_MACHINES)
          .addToIndex()                                            // 出现在 /ponder index 首页
          .item(ModBlocks.MY_MACHINE.get(), true, false)            // 标签图标（物品）, 后两个参数 = 大图标/带动画
          .title("My Machines")                                     // 默认文案 → <ns>.ponder.tag.machines
          .description("Machines added by my mod")                  // → <ns>.ponder.tag.machines.description
          .register();                                              // ← 别忘了，忘了等于没注册
}
```

`TagBuilder` 全部可选项：`title` `description` `addToIndex` `icon(ResourceLocation|String)` `idAsIcon()` `item(ItemLike[,boolean,boolean])` `register()`。

> 标签 key 用的是 `registerTag(id)` 里 id 的 **path**：`mymod:machines` → key `mymod.ponder.tag.machines`。

### 7.2 一个剧本给多个组件共用

同一段动画给一堆同类方块用（比如你的 4 个等级的收集器）：

```java
helper.forComponents(
            ModBlocks.KINETIC_NEUTRON_COLLECTOR.get(),
            ModBlocks.KINETIC_NEUTRON_COLLECTOR_DENSE.get())
      .addStoryBoard("neutron_collector", ExampleScenes::collectorBasics, TAG_MACHINES);
```

- `forComponents(T...)` / `forComponents(Iterable<? extends T>)` 返回 `MultiSceneBuilder`，
  可以接着链多个 `addStoryBoard`。
- 注意每组组件会**各自生成一份场景**，但共用同一个 .nbt 与同一套 lang key（sceneId 相同）。
- 如果组件类型不是 `ResourceLocation`（比如 Registrate 的 `ItemProviderEntry`），先转换一次：
  `PonderSceneRegistrationHelper<ItemProviderEntry<?,?>> h = helper.withKeyFunction(RegistryEntry::getId);`

### 7.3 场景顺序

```java
helper.addStoryBoard(component, "a", Scenes::a)
      .orderBefore("mymod:b")     // 排在 b 之前
      .orderAfter("create:shaft/relay");  // 排在 Create 的某场景之后
```

字符串写 `"namespace:path"` 或只写 `"path"`（默认用当前 modid）。

### 7.4 高亮标签

注册场景时传标签，或用 `StoryBoardEntry` 的链式：

| 方法 | 作用 |
| --- | --- |
| `addStoryBoard(comp, id, board, tag1, tag2...)` | 直接带标签 |
| `.highlightTag(tag)` / `.highlightTags(a, b)` / `.highlightAllTags()` | 后续追加 |

标签决定索引页的分组，也决定「相关条目」列表。

### 7.5 排除索引项（`indexExclusions`）

有些组件你不希望出现在索引里（比如同一方块的所有染色变体）：

```java
@Override
public void indexExclusions(IndexExclusionHelper helper) {
    helper.exclude(ModItems.MY_DEBUG_ITEM.get());                                  // 单个
    helper.excludeBlockVariants(MyColoredBlock.class, ModBlocks.RED_MACHINE.get()); // 整族变体
    helper.exclude(item -> item.asItem() == Items.STICK);                          // 谓词
}
```

### 7.6 热重载

| 你改了什么 | 怎么生效 |
| --- | --- |
| 剧本 Java 代码 | 重启客户端（`/ponder reload` 只重跑注册，不会热替换已编译的类） |
| .nbt / lang | `F3+T` 重载资源包（dev 环境先 `processResources`） |
| 注册逻辑（addStoryBoard 等） | `/ponder reload` |

`/ponder reload` 内部就是 `PonderIndex.reload()`：清空 localization/shared/scenes/tags，
重新跑一遍所有插件的 `registerScenes`/`registerTags`/`registerSharedText`
——所以第 1.2 节强调「必须幂等」。

---
## 8. 调试与排错速查表

### 8.1 三分钟定位法

```text
1. 悬停物品/按 W 没反应
   → /ponder <组件id> 直接开：能开 = 入口问题（物品未注册/tooltip 被屏蔽）；也开不了 = 注册没跑
2. 界面开了但空
   → 看日志有没有 "Ponder schematic missing"（第 5.5 节）
   → 有结构但看不见：临时加 scene.debug().debugSchematic() 看方块在不在
3. 有方块但没文字
   → 编辑模式打开了吗？没有 lang 就只会显示 key
4. 场景卡住不动
   → 是不是忘了 idle？或者最后忘了 markAsFinished()
5. 动作全挤在一起
   → 每个动作之间补 idle(5~10)
```

### 8.2 症状 → 原因 → 修法 总表

| 症状 | 最可能的原因 | 修法 |
| --- | --- | --- |
| 日志完全没有 `Registering Ponder Scenes` | `PonderIndex.addPlugin` 没被调用 | 检查 `@EventBusSubscriber(value = Dist.CLIENT)` 与 `FMLClientSetupEvent` |
| 有 Registering 日志但没有你的场景 | `registerScenes` 里抛异常被吞 / addStoryBoard 参数 key 不是 `ResourceLocation` | 看日志里 PonderIndex 的报错；确认 key 类型与 `withKeyFunction` |
| `/ponder xxx` 提示 `Could not find ponder scenes for item` | 组件 id 不对（你传的是剧本 id） | 用 `addStoryBoard` 的第一个参数（方块/物品注册名） |
| 标题/正文显示原始 key | lang 缺条目 / `title()` 的第一个参数变了 | 第 6 章 |
| schematic missing | 路径错（`ponder/` 目录、命名空间、文件名） | 第 5.5 节 |
| 方块位置整体偏移 | 结构最小角不是 (0,0,0) | 第 5.3 节 |
| 文字出现了但立刻消失 | `showText(n)` 的 n 太小 | 至少 40～60 tick |
| 关键帧拖不回去 | 没 `attachKeyFrame()` / 或挂在 `.sharedText()` 后面仍有效（挂法一样） | 加 `.attachKeyFrame()` |
| 结构移动后飞出去 | 没设转轴 | `configureCenterOfRotation` |
| 专有服务器崩 `NoClassDefFoundError: net/createmod/ponder/...` | 客户端类泄漏到服务端 | 全部塞进 `Dist.CLIENT` 事件或 `DistExecutor` |
| 切换世界后场景重复/变多 | 注册方法不幂等（往里 add 静态列表） | 改成纯声明式 |
| 改代码后不生效 | `/ponder reload` 不能热替换已编译的剧本类 | 重启客户端 |

### 8.3 有用的日志/命令

| 手段 | 用途 |
| --- | --- |
| `/ponder reload` | 重跑注册（改 `addStoryBoard` 后不用重启） |
| `/ponder index` / `/ponder tags` | 看索引里到底有什么 |
| `/ponder <id>` | 绕过物品入口直接播放 |
| 日志关键字 `Ponder schematic missing` / `Failed to read ponder schematic` / `Registering Ponder Scenes took` | 三个关键断点 |
| `scene.debug().debugSchematic()` | 一次性显示整张结构（排查性最强的一行） |
| `scene.debug().enqueueCallback(s -> System.out.println(...))` | 在队列空闲时读虚拟世界 |
| 客户端配置 `editingMode = true` | 显示代码里的默认文案 |

---

## 9. 完整实战：给本模组的「动能中子收集器」写一个思索场景

以下代码**已在本仓库用 `./gradlew compileJava` 编译验证通过**（记录见第 11 章），
源文件在 `docs/ponder-example/java/.../ponder_demo/`，拷进 `src/main/java` 即可用。

### 9.1 先设计分镜（写代码前必做）

好的思索场景 = 一个观点一句话，一句一个关键帧：

| 步 | 画面 | 台词 | 时长 |
| --- | --- | --- | --- |
| 1 | 底板浮现 | （无） | 10 |
| 2 | 收集器从地里升起 | "动能中子收集器会在旋转时凝聚物质" | 60 |
| 3 | 传动杆接入、出现转向指示 | "这里接入旋转力" | 60 |
| 4 | 转速翻倍、成功提示 | "转速越高，收集越快" | 50 |

### 9.2 注册三件套（Plugin + 客户端挂载）

见 1.2 / 1.3 节。本仓库示例文件：

- `MyPonderPlugin.java` —— 实现 `PonderPlugin`
- `MyPonderClientSetup.java` —— `FMLClientSetupEvent` 里 `PonderIndex.addPlugin(...)`

### 9.3 剧本全文（示例 1：只用 Ponder 原生 API）

```java
public static void collectorBasics(SceneBuilder builder, SceneBuildingUtil util) {
    builder.title("neutron_collector", "Collecting neutron matter");
    builder.configureBasePlate(0, 0, 5);
    builder.scaleSceneView(1);
    builder.idle(5);

    builder.world().showSection(util.select().layer(0), Direction.UP);
    builder.idle(5);

    BlockPos collector = util.grid().at(2, 1, 2);
    builder.world().showSection(util.select().position(collector), Direction.DOWN);
    builder.idle(10);

    builder.overlay().showText(60)
            .text("Kinetic Neutron Collectors condense matter while rotating")
            .attachKeyFrame()
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(collector, Direction.NORTH));
    builder.idle(60);

    builder.effects().indicateSuccess(collector);
    builder.idle(20);

    builder.markAsFinished();
}
```

### 9.4 剧本全文（示例 2：Create 增强版）

```java
public static void collectorKinetic(SceneBuilder builder, SceneBuildingUtil util) {
    // 关键：CreateSceneBuilder 包一层，才能用 setKineticSpeed / rotationDirectionIndicator
    CreateSceneBuilder scene = new CreateSceneBuilder(builder);

    scene.title("neutron_collector_kinetic", "Feeding it rotational force");
    scene.configureBasePlate(0, 0, 5);
    scene.setSceneOffsetY(-1);
    scene.world().showSection(util.select().layer(0), Direction.UP);
    scene.idle(5);

    BlockPos collector = util.grid().at(2, 1, 2);
    BlockPos shaft = util.grid().at(2, 1, 4);
    Selection drive = util.select().fromTo(shaft, shaft.west().east());   // 传动杆所在的一格

    scene.world().showSection(util.select().position(collector), Direction.DOWN);
    scene.idle(5);
    scene.world().showSection(drive, Direction.SOUTH);
    scene.idle(10);

    scene.world().setKineticSpeed(drive, 32);            // Create 专用：设定转速
    scene.effects().rotationDirectionIndicator(shaft);   // Create 专用：转向粒子
    scene.idle(20);

    scene.overlay().showText(60)
            .text("A shaft here supplies the collector with rotational force")
            .attachKeyFrame()
            .placeNearTarget()
            .colored(PonderPalette.MEDIUM)
            .pointAt(util.vector().blockSurface(shaft, Direction.NORTH));
    scene.idle(60);

    scene.overlay().showControls(util.vector().topOf(collector), Pointing.DOWN, 30)
            .rightClick()
            .withItem(new ItemStack(AllItems.WRENCH.get()));
    scene.idle(30);

    scene.overlay().showText(50)
            .sharedText("needs_rotation")
            .colored(PonderPalette.INPUT)
            .placeNearTarget()
            .pointAt(util.vector().centerOf(collector));
    scene.idle(50);

    scene.world().modifyKineticSpeed(drive, f -> f * 2);  // 转速翻倍
    scene.idle(20);
    scene.effects().indicateRedstone(collector);
    scene.markAsFinished();
}
```

> 完整可编译版本（含 import、注册、标签）见 `docs/ponder-example/java/.../ExampleScenes.java`。
> 注意 `CreateSceneBuilder` 的 `world()` 返回的是 `CreateSceneBuilder.WorldInstructions`，
> 它继承自 Ponder 的 `PonderWorldInstructions`，所以原生方法（`showSection`/`setBlock`…）照样能用。

### 9.5 Create 专属能力速查（`CreateSceneBuilder`）

| 分类 | 方法 |
| --- | --- |
| 动能 | `setKineticSpeed(Selection, float)` / `multiplyKineticSpeed(Selection, float)` / `modifyKineticSpeed(Selection, UnaryOperator<Float>)` |
| 转向/转速粒子 | `effects().rotationDirectionIndicator(BlockPos)` / `rotationSpeedIndicator(BlockPos)` |
| 传动构件动画 | `rotateBearing(pos, angle, ticks)` / `movePulley(pos, distance, ticks)` / `moveDeployer(pos, distance, ticks)` / `animateBogey(...)` |
| 传送带 | `createItemOnBeltLike(pos, side, stack)` / `createItemOnBelt(pos, side, stack)` → `ElementLink<BeltItemElement>` / `removeItemsFromBelt(pos)` / `stallBeltItem(link, bool)` / `changeBeltItemTo(link, stack)` |
| 漏斗/机械手 | `flapFunnel(pos, outward)` / `instructArm(pos, phase, heldItem, ...)` |
| 合成器 | `setCraftingResult(crafter, output)` / `connectCrafterInvs(pos1, pos2)` |
| 胶水/显示板/列车 | `effects().superGlue(...)` / `setDisplayBoardText(...)` / `animateTrainStation(...)` / `conductorBlaze(...)` / `changeSignalState(...)` |
| 彩蛋 | `special().birbOnSpinnyShaft(pos)` / `birbOnTurntable(pos)` / `conductorBirb(link, bool)` |

### 9.6 场景需要补的资产清单

```text
src/main/resources/assets/avaritia_singularity_stress/ponder/neutron_collector.nbt          ← 结构（5x5 底板，机器在 (2,1,2)）
src/main/resources/assets/avaritia_singularity_stress/ponder/neutron_collector_kinetic.nbt  ← 第二个场景的结构
lang: avaritia_singularity_stress.ponder.neutron_collector.header / .text_1
      avaritia_singularity_stress.ponder.neutron_collector_kinetic.header / .text_1 / .text_2
      avaritia_singularity_stress.ponder.shared.needs_rotation
      avaritia_singularity_stress.ponder.tag.neutron_machines(.description)
```

---

## 10. 版本与移植

### 10.1 本仓库事实（已验证）

| 项 | 1.21.1 环境（本仓库） | 备注 |
| --- | --- | --- |
| artifact | `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1` | Create 1.21.1 源码用的是 1.0.85 |
| 包名 | `net.createmod.ponder.*` | 见 jar 内包结构 |
| 入口 | `PonderIndex.addPlugin`（客户端） | 1.20.1 的 Forge 版 jar（1.0.91）**包名同样是** `net.createmod.ponder.*`（已核对两个 jar 的包结构一致） |
| 文本 | 代码里提供默认文案，实际显示走 lang（第 6 章） | 两版一致 |
| 结构路径 | `assets/<ns>/ponder/<id>.nbt` | 两版一致 |

### 10.2 1.20.1 ↔ 1.21.1 逐项差异（对两个真实 jar 做了 `javap` 逐个 diff）

| 项 | 结论 |
| --- | --- |
| 包名 | **两版都是** `net.createmod.ponder.*`：两个 jar 里 `grep -c 'com/simibubi/ponder'` 都是 0 → 移植**不用改 import** |
| 核心 API | 15 个核心接口逐个 diff：**14 个完全相同** |
| 唯一 API 差异 | 1.21.1 的 `WorldInstructions` **多一个** `getHolderLookupProvider()` |
| 加载器入口类 | 全量改名：`ForgePonder`/`ForgePonderClient`（1.20.1）→ `NeoForgePonder`/`NeoForgePonderClient`（1.21.1）。那是 Ponder 自己的入口，**与你无关** |

`getHolderLookupProvider()` 为什么存在：1.21 把 `ItemStack.save(tag)` 换成了需要 `HolderLookup.Provider` 的 `saveOptional(provider)`。
所以「把物品写进方块实体 NBT」要这样写：

```java
scene.world().modifyBlockEntityNBT(selection, MyBlockEntity.class, nbt -> {
    nbt.put("Filter", stack.saveOptional(scene.world().getHolderLookupProvider()));
});
```

（Create 的 `CreateSceneBuilder.setFilterData` / `instructArm` 就是这么写的，见
`allapis/Create-mc1.21.1-dev/.../foundation/ponder/CreateSceneBuilder.java:242`。）
拿 1.21.1 的代码往 1.20.1 搬，这个方法是编译不过的。

> 完整比对命令与结果见 `docs/ponder-research/04-移植注意.md`。

### 10.3 从 Create 0.5.x 老教程移植时

- 那时候 Ponder 还在 Create 内部，包名 `com.simibubi.ponder`。具体在哪一版改名**未验证**（本地没有更早的 jar）；
  能确定的是：**1.0.91 起就已经是 `net.createmod.ponder`**，所以 1.20.1(1.0.91) → 1.21.1(1.0.82) 不用改 import。
- `net.createmod.catnip`（Catnip）是 Ponder 的配套工具库：`Pointing`、`Color`、`Outliner` 都在里面，Create 的 `CreateSceneBuilder` 也依赖它。
- 1.21.1 的 NeoForge 事件与 1.20.1 Forge 不同（`FMLClientSetupEvent` 还在，但注册表/网络 API 变了），移植时优先改「注册部分」，剧本 API 基本不动。

### 10.4 依赖写法（各平台）

```gradle
// NeoForge 1.21.1
implementation("net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1")
// Forge 1.20.1（若维护老分支）
implementation("net.createmod.ponder:Ponder-Forge-1.20.1:1.0.91")
```

> 注意：Create 6.x 已把 Ponder **jarJar 内嵌**（`build.gradle` 里 `jarJar(api("net.createmod.ponder:ponder-neoforge..."))`）。
> 如果你只是 Create 的 addon，依赖 Create 就已经带上 Ponder 运行时了；
> 但**编译期仍要显式声明 ponder 依赖**（本仓库就是这么做的）。

---

## 11. 证据清单（每条结论的出处）

### 11.1 本手册验证过的命令

```bash
# ① 反编译 API 真身
java -jar vineflower-1.11.2.jar -dgs=1 -asc=1 -rsy=1 \
     ~/.gradle/caches/modules-2/files-2.1/net.createmod.ponder/ponder-neoforge/1.0.82+mc1.21.1/<hash>/ponder-neoforge-1.0.82+mc1.21.1.jar \
     /tmp/psrc

# ② 看签名（不反编译也能拿）
cd /tmp/ponderx && javap -public -cp . net.createmod.ponder.api.scene.WorldInstructions

# ③ 编译验证本手册第 9 章的示例代码（把示例拷进 src 后）
cp -r docs/ponder-example/java/committee src/main/java/
./gradlew compileJava --offline --no-build-cache --console=plain
# → BUILD SUCCESSFUL in 6s，产出 3 个 .class：
#   ExampleScenes.class / MyPonderClientSetup.class / MyPonderPlugin.class
# 唯一警告来自本模组既有代码（NeutronExtendoGripItem.initializeClient 已被 NeoForge 标记过期），与本示例无关。
# 完整日志：docs/_verify/compile-demo.log
rm -rf src/main/java/committee/nova/avaritia_singularity_stress/ponder_demo   # 验证后已清理
```

### 11.2 逐条结论 → 源码位置（反编译后的类）

| 结论 | 出处 |
| --- | --- |
| 结构路径是 `assets/<ns>/ponder/<id>.nbt` | `PonderSceneRegistry.loadSchematic`：`String path = "ponder/" + location.getPath() + ".nbt"` |
| 结构是 GZIP+NBT，用 `StructureTemplate` 落位 | `PonderSceneRegistry.loadSchematic(InputStream)` + `compile(...)` 里 `placeInWorld(level, ZERO, ZERO, ...)` |
| 找不到结构只报错不崩 | 同上，`Ponder.LOGGER.error("Ponder schematic missing: " + ...)` 后返回空 `StructureTemplate` |
| lang key：`<ns>.ponder.<scene>.<key>` | `PonderLocalization.langKeyForSpecific` |
| lang key：`<ns>.ponder.shared.<key>` / `<ns>.ponder.tag.<id>[.description]` | `PonderLocalization.langKeyForShared` / `langKeyForTag` / `langKeyForTagDescription` |
| 正常模式一律走 `I18n.get(langKey)`，代码里的文本只是默认值 | `PonderLocalization.getSpecific` / `getShared` / `getTagName` |
| 场景 id 由 `title()` 第一参数决定，默认 `missing_title` | `PonderSceneBuilder.title` + `PonderScene` 构造器 `this.sceneId = ...("missing_title")` |
| 标题 key 是 `header` | `PonderScene.TITLE_KEY = "header"` |
| 文字编号从 1 开始、`.sharedText` 不占号 | `PonderScene` 构造器 `textIndex = 1`；`registerText` 里 `"text_" + textIndex`；`TextWindowElement.Builder.sharedText` 不调 registerText |
| `attachKeyFrame()` = 延迟关键帧 | `TextWindowElement.Builder.attachKeyFrame` → `addLazyKeyframe()` |
| `idle(n)` = 插入 `DelayInstruction(n)`；`idleSeconds(s)=idle(s*20)` | `PonderSceneBuilder.idle/idleSeconds` |
| 场景每 tick 推进一次、指令串行、Delay 阻塞 | `PonderScene.tick` |
| 不能倒退 seek | `PonderScene.seekToTime` 抛 `"Cannot seek backwards. Rewind first."` |
| `showSection` 的方向 = 起始 0.5 格偏移方向 | `FadeIntoSceneInstruction.firstTick`：`setFadeVec(Vec3.atLowerCornerOf(dir.getNormal()).scale(0.5))` |
| 插件方法会被反复调用（reload 时清空重跑） | `PonderIndex.reload` → `clearShared/clearRegistry/registerAll/gatherSharedText` |
| `/ponder` 命令族 | `PonderCommand.register`：`reload` / `index` / `tags` / `<scene id> [targets]` |
| `/ponder <id>` 的 id 是**组件** id | `SimplePonderActions.openPonder`：`doScenesExistForId(id)` → `PonderUI.of(id)` |
| 编辑模式开关 | `CClient.editingMode`（`ponder-client.toml`，默认 false） |
| 默认键位是 W（keyCode 87） | `PonderKeybinds.PONDER("ponder", 87)` |
| Create 的挂载点 | `CreateClient` 第 112 行 `PonderIndex.addPlugin(new CreatePonderPlugin());` |
| datagen 期间必须手动 addPlugin | `CreateDatagen.providePonderLang`（注释原文：`FMLClientSetupEvent does not run during datagen`） |
| 场景注册的真实调用形态 | `allapis/Create-mc1.21.1-dev/.../infrastructure/ponder/AllCreatePonderScenes.java` |
| 标签注册形态 | 同上目录 `AllCreatePonderTags.java` |
| Create 增强 API | `.../foundation/ponder/CreateSceneBuilder.java` |

### 11.3 本手册没能验证的部分（老实交代）

- **渲染效果**：文字气泡位置、粒子观感、缩放比例这些只能进游戏肉眼看，本手册给的是参数含义与经验值，不保证「好看」。
- **真实播放**：本文档的示例代码只做了**编译验证**（BUILD SUCCESSFUL），没有在实机里播放过；
  实机播放需要 `assets/<modid>/ponder/*.nbt` 结构文件（本仓库尚未放），缺失时场景会是空的但不崩。
- **「`com.simibubi.ponder` 具体在哪一版改名」**：未知（本地无早于 1.0.91 的 jar）。
  已实证的是 1.20.1(1.0.91) 与 1.21.1(1.0.82) 包名相同、15 个核心接口里 14 个完全一致（10.2）。
- **结构方块导出的具体 GUI 文案**：随游戏版本/语言变化，第 5.2 节给的是流程而非逐字文案。

---

## 附录 A：文件清单

| 文件 | 内容 |
| --- | --- |
| `docs/ponder-api/00-索引.md` | 全部 public API 的类索引（作用/是否外部可用） |
| `docs/ponder-api/10-scene.md` | `api.scene` 包逐方法说明 |
| `docs/ponder-api/20-element.md` | 元素与 builder |
| `docs/ponder-api/30-registration.md` | 注册相关 |
| `docs/ponder-api/40-foundation-others.md` | foundation / enums / command / config |
| `docs/ponder-api/99-签名原始转储.txt` | `javap -public` 原始输出（核对用） |
| `docs/ponder-research/01-入口与注册.md` | Create 真实注册代码与时机 |
| `docs/ponder-research/02-场景范式.md` | Create 真实场景范式库（含文件:行号） |
| `docs/ponder-example/java/...` | 本手册第 9 章示例的完整可编译源码（+ README：怎么接进自己的模组） |
| `docs/_verify/compile-demo.log` | 示例代码的编译验证日志（BUILD SUCCESSFUL） |
| `docs/_shots/*.png` | 本手册 HTML 版的渲染截图（自查用） |

## 附录 B：一页速查卡

```text
【依赖】  net.createmod.ponder:ponder-neoforge:<ver>+mc1.21.1

【四件套】
  PonderPlugin          getModId + registerScenes/Tags/SharedText
  PonderIndex.addPlugin 在 FMLClientSetupEvent（Dist.CLIENT）
  剧本                  (SceneBuilder scene, SceneBuildingUtil util) -> 指令流
  资源                  assets/<ns>/ponder/<剧本id>.nbt
                        assets/<ns>/lang/<lang>.json

【剧本骨架】
  title(id, 标题) → configureBasePlate(x,z,size) → idle(5)
  → world().showSection(...) → idle(10)
  → overlay().showText(60).text("...").attachKeyFrame().pointAt(...) → idle(60)
  → effects().indicateSuccess(pos) → idle(20)
  → markAsFinished()

【时间】  1 tick = 1/20 秒    idle(20) = 1 秒

【lang】  <ns>.ponder.<title第一个参数>.header
          <ns>.ponder.<title第一个参数>.text_1 / text_2 ...
          <ns>.ponder.shared.<key>
          <ns>.ponder.tag.<id> / .description

【方向】  showSection 的 Direction = 从哪个方向滑进来
          UP=从上方落下   DOWN=从下方升起（和直觉相反！）

【调试】  /ponder <组件id> | /ponder reload | editingMode=true
          日志搜 "Ponder schematic missing"
```
