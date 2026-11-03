# Ponder 示例工程（可直接拷进本模组）

这套文件是《[Ponder 保姆级使用手册](../Ponder-保姆级使用手册.md)》第 9 章的完整源码，
**已在 2026-11-02 用本仓库的 `./gradlew compileJava` 编译验证通过**（环境见手册第 11 章）。

## 文件

| 文件 | 作用 |
| --- | --- |
| `java/.../ponder_demo/MyPonderPlugin.java` | Ponder 插件入口（modid + 三项注册） |
| `java/.../ponder_demo/MyPonderClientSetup.java` | 客户端挂载（`FMLClientSetupEvent` → `PonderIndex.addPlugin`） |
| `java/.../ponder_demo/ExampleScenes.java` | 两个剧本 + 标签 + 共享文案注册 |

## 怎么用

1. 把 `java/` 下的 `committee/` 目录整体拷到 `src/main/java/`
   （或把包名 `committee.nova.avaritia_singularity_stress.ponder_demo` 改成你自己的包）。
2. 放结构文件（否则场景是空的，但不会崩）：
   `src/main/resources/assets/avaritia_singularity_stress/ponder/neutron_collector.nbt`
   与 `.../ponder/neutron_collector_kinetic.nbt`（制作方法见手册第 5 章）。
3. 补 lang（手册第 6 章）：
   ```json
   "avaritia_singularity_stress.ponder.neutron_collector.header": "收集中子物质",
   "avaritia_singularity_stress.ponder.neutron_collector.text_1": "动能中子收集器会在旋转时凝聚物质",
   "avaritia_singularity_stress.ponder.neutron_collector_kinetic.header": "给它接上旋转力",
   "avaritia_singularity_stress.ponder.neutron_collector_kinetic.text_1": "这里接入传动杆",
   "avaritia_singularity_stress.ponder.neutron_collector_kinetic.text_2": "这类机器只在有旋转力时工作",
   "avaritia_singularity_stress.ponder.shared.needs_rotation": "这类机器只在有旋转力时工作",
   "avaritia_singularity_stress.ponder.tag.neutron_machines": "中子机器",
   "avaritia_singularity_stress.ponder.tag.neutron_machines.description": "需要旋转力才能凝聚中子物质的机器"
   ```
4. 进游戏验证：`/ponder avaritia_singularity_stress:kinetic_neutron_collector`

## 编译验证（复现命令）

```bash
cp -r docs/ponder-example/java/committee src/main/java/
./gradlew compileJava --offline --console=plain
# → BUILD SUCCESSFUL
rm -rf src/main/java/committee/nova/avaritia_singularity_stress/ponder_demo
```

> 注：本仓库验证完成后已把 `src` 里的副本删除，保持仓库干净；示例只留在 `docs/ponder-example/`。
