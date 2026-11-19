# 架构说明

## 1. 为什么是 PUA 而不是替换文本

Minecraft 的原生聊天渲染只认「字体里的字形」。要让一个 5 码点的 ZWJ 家庭表情在聊天里显示成一张图，唯一稳定的做法是：

**把所有 emoji 预先画进一张图集，再把每个格子绑定到一个私用区（PUA）字符。**

玩家输入的 `👨‍👩‍👧` 在服务端被替换成**一个** `U+E002`，客户端字体把它画成第 1 行第 3 列的图。

```
0xE000 + (row-1)*16 + (col-1)
```

## 2. 数据流

```
Twemoji 15.1 (3838 个 PNG)
        │  build_unified.py
        ▼
unified_sheet.png  512 × 7680，16 列 × 240 行，每格 32×32
        │
        ├──► Universal_Emoji.zip  (font/default.json + universal_emoji.png)
        │        ↑ providers 末尾追加，不覆盖原版
        │
        └──► ios.txt / android.txt / windows.txt
                 row.col=1f468-200d-1f469-200d-1f467
                        │  jar 内置，getResource() 读取
                        ▼
                 EmojiTable.load()  三方求交集
                        │
                        ▼
                 EmojiTextProcessor  手动坐标 → ZWJ 簇最长匹配
                        │
                        ▼
                 ChatListener  cancel + Server#sendMessage
                 ContentListener  告示牌 / 书页：原地改文本
```

## 3. 图集切分的硬约束

Minecraft 的 bitmap provider 用法：

```json
{
  "type": "bitmap",
  "file": "minecraft:font/universal_emoji.png",
  "ascent": 10,
  "height": 10,
  "chars": ["<16 个字符>", "<16 个字符>", ... 共 240 行]
}
```

- `chars` 的**字符串个数**决定纵向条数，**每串长度**决定横向列数。
- 所有字符串必须**等长**，否则纹理被拉伸（warp），表情会变形。
- `height` 是**屏幕显示高度**，不是像素格大小。32px 素材配 `height:10`，表情和聊天文字等比，不会把行高撑成 3 倍。
- 最后一行不满 16 个时，用透明占位字符补齐，保证等长。

## 4. 匹配算法

```
for each cluster in text:
    cluster = base + modifiers(肤色/U+20E3/tag) + (ZWJ + base + modifiers)*
    key = hex(cluster)            // 0000 补零，小写，'-' 分隔
    if key in supported:
        emit PUA glyph
    else if cluster 看起来像 emoji:
        emit "[不支持的表情]"      // 整簇降级，绝不拆
    else:
        emit cluster verbatim     // 普通文本原样通过
```

两个关键点：

1. **先剥 U+FE0F**，再匹配。`❤️` 和 `❤` 归一到同一个 key。
2. **簇不可拆**。`👨‍👩‍👦` 不在表里时，不会退化成「👨 + 👩 + 👦」三个字形，而是整体降级。这是多码点最容易炸的地方。

## 5. 三端校验

`ios.txt`、`android.txt`、`windows.txt` 分别只包含该平台能显示的行。插件加载时求交集：

- 三方都有 → 可用
- 缺一端 → 视为不可用，运行时替换成 `[不支持的表情]`

数据来源 `iamcal/emoji-data` 的 `has_img_apple` / `has_img_google` / `added_in`。
要人工修正某一条，编辑 `tools/platform_overrides.json`：

```json
{ "1fae0": { "windows": true } }
```

## 6. 版本矩阵

| 版本段 | 模块 | 聊天事件 | 资源包 | 说明 |
| --- | --- | --- | --- | --- |
| 1.13 – 1.15 | `emoji-plugin-legacy` | `AsyncPlayerChatEvent` | `Universal_Emoji_legacy.zip` | 无签名，`setMessage` 即可 |
| 1.16 – 1.18 | `emoji-plugin-legacy` | `AsyncPlayerChatEvent` | 同上（legacy 包） | `setResourcePack` 走反射回退 |
| 1.19 – 1.19.4（Paper） | `emoji-plugin-modern` | `AsyncChatEvent` | `Universal_Emoji_legacy.zip` | cancel + 重广播，绕开签名 |
| 1.20 – 26.3（Paper） | `emoji-plugin-modern` | `AsyncChatEvent` | `Universal_Emoji.zip` | 字体为 `reference` 结构 |
| 1.19 – 1.19.4（Spigot） | `emoji-plugin-spigot` | `AsyncPlayerChatEvent` | `Universal_Emoji_legacy.zip` | 纯 Bukkit，反射下发资源包 |
| 1.20 – 26.3（Spigot） | `emoji-plugin-spigot` | `AsyncPlayerChatEvent` | `Universal_Emoji.zip` | 字节码零 `io.papermc` / `net.kyori` 引用 |

> 1.16–1.18 也可以用 modern jar：`setResourcePack` 有 4 级签名回退，`AsyncChatEvent` 自 1.16.5 起存在。只是 `api-version: 1.19` 会被 1.18 服务端拒绝，需要把 `plugin.yml` 改成 `1.16` 重新打包。

## 7. 失败模式

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 所有文字变豆腐块 | `default.json` 被整体覆盖，丢了原版 provider | 用脚本生成的两份包，providers 末尾追加 |
| 表情被拉成条状 | `chars` 各行长度不等 | 构建时 assert + 占位补齐 |
| 家庭表情变三个人 | 按码点逐个匹配 | ZWJ 簇整体匹配 |
| 进服一直下资源包 | sha1 与实际 zip 不一致 | 重新跑脚本取新 SHA-1 |
| 1.19+ 红字「modified」 | 改过的消息签名失效 | cancel + 服务器系统消息 |
| 表情变成方框 | 资源包没下发/没启用 | 检查 `resourcepack.url` 是否公网可访问 |

## 8. 容量

- PUA `U+E000–U+F8FF` 共 6400 槽
- 当前占用 3840（3838 + 2 占位）
- 剩余 2560 槽，够未来 2–3 个 Unicode 版本的增量
