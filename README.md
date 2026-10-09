# Auto Trade Cycling

Minecraft Fabric 客户端模组：自动重掷村民交易，直到刷出指定目标。

不同于逐次点击刷新，本模组把重掷放到服务端连续进行，客户端只在命中时收到结果，省掉每轮网络往返。

## 功能

- 服务端批量重掷，每 tick 的强度可选：保守 / 均衡 / 激进 / 极速 / 最快
- 目标支持物品 + 附魔等级 + 数量下限 + 价格上限，匹配模式支持「全部」与「任一」
- 借助 Visible Traders，可同时匹配 2-5 级的未解锁交易
- 目标与档位持久化在 `config/auto-trade-cycling.json`
- 按 `G` 打开配置界面；搜索中可用聊天栏或界面查看已刷新次数

## 依赖（均为必需）

| 依赖 | 版本 |
| --- | --- |
| Minecraft | 1.21.10 |
| Fabric Loader | >= 0.19.3 |
| Fabric API | 0.138.4+1.21.10 |
| [Trade Cycling](https://modrinth.com/mod/trade-cycling) | 任意 |
| [Visible Traders](https://modrinth.com/mod/visible-traders) | 任意 |
| [owo-lib](https://modrinth.com/mod/owo-lib) | 任意 |

## 使用

1. 添加目标：`/autoTradeCycling add item <数量> <价格上限> <物品名>`，附魔书用 `add enchantment <等级> <价格上限> <附魔名>`，给装备加附魔要求用 `add itemEnchantment <等级> <物品ID> <附魔名>`
2. 匹配模式：`/autoTradeCycling mode all|any`
3. 开始搜索：`/autoTradeCycling start`，然后右键打开一个**未交易过的 1 级村民**的交易界面
4. 停止：关闭交易界面，或按 `G` 打开配置界面后点停止

## 构建

```
./gradlew build
```

产物在 `build/libs/`。

## 协议

LGPL-3.0，见 [LICENSE.txt](LICENSE.txt)。
