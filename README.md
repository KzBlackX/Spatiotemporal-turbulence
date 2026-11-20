# 时空乱流 / Spatiotemporal Turbulence

一个 Minecraft NeoForge 模组。

| 项 | 值 |
|---|---|
| Minecraft | 26.3 |
| NeoForge | 26.3.0.113 |
| Java | 25 |
| Gradle | 9.2.1（ModDevGradle 2.0.148） |
| modid | `spatiotemporal_turbulence` |
| 包名 | `com.db.spatiotemporal_turbulence` |
| 协议 | LGPL-3.0-or-later |

## 模组设定

时空乱流是时间空间的混乱：空间出现一个小黑洞，将人（物）吸进去，那么这个人（物）就穿越了时空，
来到其它时间、地点。

具体玩法：

- 玩家进入主世界后 10 秒，身边会出现黑洞，将玩家吸入并传送到中世纪维度；
- 该过程为一次性强制触发，不需要任何前置物品或机器；
- 模组不提供任何返回主世界的途径；玩家在中世纪维度死亡后会重生在同一个维度。

按照本模组设定，默认主世界为科技世界；进入黑洞穿越到中世纪魔法世界。

黑洞为传送门，中世纪为新维度，需要以较大概率生成村庄、小镇、城市、地牢。玩家扮演施术者，敌人为吸血鬼或恶魔，
地牢里可以有恶魔、吸血鬼或其他敌对生物。

恶魔或吸血鬼必须设定几个小、大 boss 基地：吸血鬼的基地可以是城堡，较分散；恶魔的基地只有一个，在魔法世界
z 轴 40 以下固定位置生成。魔法世界海平面为 z 轴 80 以上，只能通过祭坛传送到恶魔所在的地狱。

## 构建

```bash
# 编译并打包到 build/libs/
./gradlew build          # Windows: gradlew.bat build

# 启动开发客户端 / 服务端 / 数据生成
./gradlew runClient
./gradlew runServer
./gradlew runData
```

首次构建会下载 NeoForge、Minecraft 并做反编译，耗时较长。产物位于 `build/libs/spatiotemporal_turbulence-<version>.jar`。

## 目录结构

```
src/main/java/com/db/spatiotemporal_turbulence/   模组源码
src/main/resources/                               资源（assets、数据包、mixin 配置等）
src/main/templates/META-INF/neoforge.mods.toml    模组元数据模板，构建时展开变量
```

`neoforge.mods.toml` 里的 `${...}` 变量由 `gradle.properties` 提供，不要直接改展开后的产物。

## 当前代码状态

`src/main` 目前是官方脚手架：示例方块 `example_block`、示例物品 `example_item`、创造模式物品栏 `example_tab`、
示例配置 `Config`，以及客户端入口 `SpatiotemporalTurbulenceClient`。这些是占位代码，后续按上面的设定替换成
真正的传送机制、维度等内容。

黑洞吸入逻辑在 `BlackHoleHandler`，维度定义在 `src/main/resources/data/spatiotemporal_turbulence/`
（`dimension_type` 与 `dimension` 各一份 JSON）。

`spatiotemporal_turbulence` 是正式 modid，示例注册项也在该命名空间下，因此可以逐个替换而不用整体重写。

## 许可证

本项目采用 **GNU Lesser General Public License v3.0 或更高版本（LGPL-3.0-or-later）**。

- 完整条款见 [LICENSE](LICENSE)（LGPL-3.0）。LGPL-3.0 通过引用并入 GPL-3.0 的条款，
  因此同一份 [LICENSE.GPL-3.0](LICENSE.GPL-3.0) 也随仓库提供。
- 简而言之：你可以把本模组放进整合包、在闭源模组里依赖它；但如果你**修改了本项目自身的代码并分发**，
  修改后的这部分必须继续以 LGPL-3.0（或兼容许可）开源。
- 第三方材料与依赖的许可说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

历史说明：本项目在提交 `bc23f11` 及之前以 MIT 协议发布。该版本代码的 MIT 授权对已经获取它的人继续有效，
不可撤回；LGPL-3.0-or-later 适用于本提交之后的版本。
