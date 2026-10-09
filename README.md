# Auto Trade Cycling

Minecraft Fabric 客户端模组：自动重掷村民交易，直到刷出指定目标。


## 功能

- 服务端批量重掷，每 tick 的强度可选：保守 / 均衡 / 激进 / 极速 / 最快
- 目标支持物品 + 附魔等级 + 数量下限 + 价格上限，匹配模式支持「全部」与「任一」
- 借助 Visible Traders，可同时匹配 2-5 级的未解锁交易
- 目标与档位持久化在 `config/auto-trade-cycling.json`
- 按 `G` 打开配置界面；搜索中可用聊天栏或界面查看已刷新次数

## 依赖（均为必需）

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 26.1 / 26.1.1 / 26.1.2 / 26.2 |
| Fabric Loader | >= 0.19.3 |
| Fabric API | 0.145.4+26.1.2 / 0.152.1+26.2 |
| [Trade Cycling](https://modrinth.com/mod/trade-cycling) | 26.1 / 26.2 对应版本 |
| [Visible Traders](https://modrinth.com/mod/visible-traders) | 26.1（2.4.0）/ 26.2（2.5.1） |
| [owo-lib](https://modrinth.com/mod/owo-lib) | 0.13.1+26.1 / 0.13.1+26.2 |

## 使用

1. 添加目标：`/autoTradeCycling add item <数量> <价格上限> <物品名>`，附魔书用 `add enchantment <等级> <价格上限> <附魔名>`，给装备加附魔要求用 `add itemEnchantment <等级> <物品ID> <附魔名>`
2. 匹配模式：`/autoTradeCycling mode all|any`
3. 开始搜索：`/autoTradeCycling start`，然后右键打开一个**未交易过的 1 级村民**的交易界面
4. 停止：关闭交易界面，或按 `G` 打开配置界面后点停止

## 构建

多版本工程，各版本为独立子项目，源码在 `versions/<版本>`，按项目路径构建：

```
./gradlew buildAndGather     # 构建全部版本，并把各版本 jar 汇总到 mods/
./gradlew :26.1.2:build      # 仅构建 26.1 线
./gradlew :26.2:build        # 仅构建 26.2
```

同一条版本线共用一个 jar，产物名带适用版本范围：

| 构建目录 | 覆盖的 Minecraft 版本 | 产物 |
| --- | --- | --- |
| `versions/26.1.2` | 26.1 / 26.1.1 / 26.1.2 | `auto-trade-cycling-1.0+26.1-26.1.2.jar` |
| `versions/26.2` | 26.2 | `auto-trade-cycling-1.0+26.2.jar` |

产物位于 `versions/<版本>/build/libs/`，`mods/` 为汇总目录。26.3 暂不构建。

推送到仓库后，GitHub Actions（Dev Builds）会自动构建全部版本，并把 jar 汇总为 `mods` 构件上传，可在对应 run 的 Artifacts 中下载。

## 协议

LGPL-3.0，见 [LICENSE.txt](LICENSE.txt)。
