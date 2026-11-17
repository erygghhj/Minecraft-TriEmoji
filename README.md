# Universal Emoji

> **内存要求**：插件本体实测约 4–10 MB 堆（三端码表 3,835 / 3,838 / 3,585 条全部载入、做完交集与排序后，GC 后占用 4 MB）；服务器整体最低 1 GB，建议 2 GB 以上，26.3 建议 4 GB。资源包 6 MB 由客户端下载，不占服务端内存。

Minecraft Java 版 **1.10 → 26.3** 的完整 Emoji 聊天系统。纯服务端 + 资源包方案，不依赖任何客户端模组。

聊天里打出原生 emoji（`😂`、`👨‍👩‍👧`、`❤️`），或者直接写坐标 `[1.3]`，都会在游戏内渲染成真正的彩色图标。

```
玩家: 今天好累 😂
游戏内: 今天好累 <彩色表情>
```

---

## 直接下载

文件都在仓库里，点开即下，不依赖 Release：

| 文件 | 适用版本 | 类型 |
| --- | --- | --- |
| [`Universal_Emoji.zip`](https://github.com/erygghhj/Minecraft-TriEmoji/raw/main/dist/Universal_Emoji.zip) | 1.20 – 26.3 | 资源包（**必装**） |
| [`Universal_Emoji_legacy.zip`](https://github.com/erygghhj/Minecraft-TriEmoji/raw/main/dist/Universal_Emoji_legacy.zip) | 1.10 – 1.19.4 | 资源包（**必装**） |
| [`UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`](https://github.com/erygghhj/Minecraft-TriEmoji/raw/main/release/UniversalEmoji-Paper-1.20+-2.0.0-beta.jar) | Paper 1.19 – 26.3 | 插件 |
| [`UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar`](https://github.com/erygghhj/Minecraft-TriEmoji/raw/main/release/UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar) | Spigot/Paper 1.10 – 1.18 | 插件 |
| [`UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar`](https://github.com/erygghhj/Minecraft-TriEmoji/raw/main/release/UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar) | Spigot **1.19 – 26.3** | 插件（S 版） |

资源包和插件必须成对使用，只装插件不装包，游戏里只会显示空白方块。

---

## 这套东西由两部分组成

| 部分 | 作用 | 在哪 |
| --- | --- | --- |
| `tools/build_unified.py` | 拉全量 Twemoji，拼成超长图集，生成资源包 + 三端码表 | `dist/` |
| `emoji-core` | 三端校验 + 最长匹配算法（纯 Java，不碰服务端 API） | Maven 模块 |
| `emoji-plugin-modern` | Paper 1.19 - 26.3 的插件（AsyncChatEvent + 绕开聊天签名） | Maven 模块 |
| `emoji-plugin-legacy` | Spigot/Paper 1.10 - 1.18 的插件（AsyncPlayerChatEvent） | Maven 模块 |
| `emoji-plugin-spigot` | Spigot 1.19 - 26.3 的插件（纯 Bukkit，反射下发资源包） | Maven 模块 |

当前构建：**3838 个 emoji**，16 列 × 240 行，PUA 占用 `U+E000 .. U+EFFF`。

---

## 适配版本（《我的世界》Java 版）

**资源包是强制依赖，不是可选装饰。** 服务端把 emoji 换成私用区字符（`U+E000` 起）之后，
没装资源包的客户端只会在聊天里看到空白或方块。所以插件和资源包必须成对部署，缺一个就不成立。

| 《我的世界》Java 版 | 资源包 | 插件 | 说明 |
| --- | --- | --- | --- |
| 1.10 – 1.15 | `Universal_Emoji_legacy.zip` | `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar` | `AsyncPlayerChatEvent`，无聊天签名 |
| 1.16 – 1.18 | `Universal_Emoji_legacy.zip` | `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar` | `api-version: 1.13`，事件仍是标准路径 |
| 1.19 – 1.19.4 | `Universal_Emoji_legacy.zip` | **P 版**：`UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`<br>**S 版**：`UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar` | 1.19 起有聊天签名，取消事件后由服务端重新广播 |
| 1.20 – 26.3 | `Universal_Emoji.zip` | **P 版**：`UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`<br>**S 版**：`UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar` | P 版走 `AsyncChatEvent`，S 版走 `AsyncPlayerChatEvent` |

两份资源包的差别只在字体 JSON 的写法，图集本身逐字节相同：

- `legacy`：1.16.5 的原版结构（内联 `bitmap` + `legacy_unicode`）
- `modern`：1.21.4 的原版结构（三个 `reference` + 自己的 `bitmap`）

`pack.mcmeta` 同时声明 `pack_format: 1` 和 `supported_formats: 1..999`，所以任何版本都不会弹“不兼容”。

**实测起服验证过的**：Paper 1.21.11（build 132）、Paper 26.3（build 252，Java 25）。
**Spigot 版（S 版）目前只有静态验证**（字节码零 `io.papermc` / `net.kyori` 引用、jar 条目齐全、下载链路 200），还没有在真实 Spigot 服起过服。
其余版本按上表的包结构构建，未逐版本起服验证。

---

## 一、生成资源包

```bash
python3 -m pip install Pillow requests
python3 tools/build_unified.py --out dist
```

产物：

```
dist/
├── Universal_Emoji.zip          # 1.20+ 用这个
├── Universal_Emoji_legacy.zip   # 1.10 - 1.19.4 用这个
├── unified_sheet.png            # 512 × 7680 超长图集
├── ios.txt / android.txt / windows.txt
├── manifest.json
└── sha1.txt
```

脚本会打印 zip 的 SHA-1，插件需要它。

### 为什么是两份包

Minecraft 的 `font/default.json` 是**整体替换**的：只写自己的 bitmap provider，就会把原版字母、汉字 provider 全部抹掉，所有人聊天变成豆腐块。

所以脚本做的是**追加**：

- `modern` 包从 1.21.4 的原版结构出发（`reference → include/space / include/default / include/unifont`）
- `legacy` 包从 1.16.5 的原版结构出发（完整内联的 `bitmap` + `legacy_unicode`，1.10 也认识这些类型）

然后把自己的 provider 追加到 `providers` 数组**末尾**，原版字形优先级不变。

### 关于 `pack_format`

按要求写成 `pack_format: 1`，同时补了 `supported_formats: 1 .. 999`。这样 1.21 客户端不会弹“不兼容”红字，1.12 客户端也能加载。

---

## 二、编译插件

```bash
mvn -B package
```

需要 **JDK 21**（1.21 API 的 class 版本）和 Maven。

产物：

```
emoji-plugin-modern/target/UniversalEmoji-Paper-1.20+-2.0.0-beta.jar          # Paper 1.19 - 26.3
emoji-plugin-spigot/target/UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar        # Spigot 1.19 - 26.3
emoji-plugin-legacy/target/UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar   # 1.10 - 1.18（Spigot/Paper 通用）
```

---

## 三、部署

1. 把 `Universal_Emoji.zip` 放到任意 **HTTP/HTTPS** 静态服务器（GitHub Release、对象存储、Nginx 都行）。
2. 把 jar 丢进 `plugins/`。
3. 启动一次，生成 `plugins/UniversalEmoji/config.yml`，改两行：

```yaml
resourcepack:
  url: "https://你的域名/Universal_Emoji.zip"
  sha1: "f2ce2a2617212aafd1ee19e4216f085d86ec3f40"
  force: true
```

4. 重启，进服会收到资源包。

> 插件**不会**读取本地 zip，也不会自己开 HTTP 服务。它只把 URL 和 SHA-1 发给客户端，客户端自己去下载。

---

## 四、怎么用

玩家端不需要装任何东西，资源包生效后：

| 输入 | 结果 |
| --- | --- |
| `😂` | 渲染成表情图 |
| `👨‍👩‍👧` | 整个家庭作为一个表情，不会拆成三个人 |
| `[1.3]` | 按坐标直接取第 1 行第 3 列的表情 |
| `[2.1]` | 手动坐标优先级最高，绕过三端校验 |

图片坐标可以对照 `dist/unified_sheet.png`：每行 16 个，`[行.列]` 从 1 开始数。

---

## 五、核心逻辑（为什么不会炸）

### 1. 剥离 U+FE0F，保留 U+200D

```
❤️  = 2764 FE0F      → 剥离后 2764
👨‍👩‍👧 = 1F468 200D 1F469 200D 1F467   → 一个 ZWJ 都不动
```

只删变体选择符，家庭、职业、肤色组合的完整性不受影响。

### 2. 按 ZWJ 簇做最长匹配，绝不拆簇

扫描顺序：`base → 修饰符（肤色/keycap/tag）→ ZWJ → 下一个 base …`。

整簇能命中就替换成一个 PUA 字符；整簇没命中，整簇降级成 `[不支持的表情]`。
**不会出现“家庭表情被拆成男人+女人+女孩”**，这是多码点最容易炸的地方。

### 3. 三端交叉校验

`ios.txt / android.txt / windows.txt` 三个表在 `EmojiTable.load()` 里求交集：

```java
// 三方都必须有，而且坐标必须一致
if (androidIndex != null && windowsIndex != null
        && androidIndex.equals(index) && windowsIndex.equals(index)) { ... }
```

缺任何一端 → 不进 `supportedGlyphs` → 运行时替换成 `[不支持的表情]`。

三端差异来自 [iamcal/emoji-data](https://github.com/iamcal/emoji-data)：
`has_img_apple` → iOS，`has_img_google` → Android，Windows 用 `added_in` 的 Emoji 版本阈值近似（微软 Segoe UI Emoji 更新滞后）。
可以用 `tools/platform_overrides.json` 手动改任意一条。

### 4. `[X.Y]` 优先级最高

手动坐标直接走 `(row-1)*16 + (col-1) + 0xE000`，不参与三端校验。

### 5. PUA 越界防御

- 构建阶段：`0xE000 + 格数 - 1 > 0xF8FF` 直接拒绝构建
- 运行阶段：`glyphChar(index)` 越界抛 `IllegalArgumentException`，`indexForCoordinate` 超出真实条目数返回 `-1` 并降级
- 读取 TXT 时：坐标超出网格、超过 6400 槽都会立刻报错并拒绝整个表

---

## 六、1.19+ 的红字 / 签名问题

1.19 起每条玩家聊天都带 Mojang 签名。插件改了文本，签名就对不上，客户端会标“已修改 / 未签名”。

本项目的解法：

```java
event.setCancelled(true);                       // 丢掉原消息
Bukkit.getServer().sendMessage(newLine);        // 服务器系统消息，不带签名
```

`Server` 实现了 `ForwardingAudience`，所以这条广播会发给所有玩家 + 控制台，**发送者本机也能看到**，而且不经过签名校验。

---

## 七、已知限制

- 表情是 **追加** 到原版字体的 PUA 区，`U+E000` 起 6400 个槽位，全量 Twemoji 用掉 3840 个，仍有余量。
- 资源包 PNG 约 6 MB，客户端首次进服要下载一次。
- 1.19.4 以下没有签名问题，`legacy` 模块直接 `setMessage`，保留原版聊天格式（含玩家名）。
- 三端表是**近似**的厂商支持矩阵，不是 Mojang 官方数据；要精确就改 `platform_overrides.json`。

---

## 八、遇到问题怎么改

这份代码刻意做成了「坏了好修」的形状：

- **先看日志**：启动时应有 `[UniversalEmoji] Universal Emoji ready: 3582 sequences …`。没有这行，就是三张码表没读到；有这行但游戏里还是方块，就是资源包 URL / SHA-1 没对上。
- **分层很浅**：`emoji-core` 只做纯逻辑（解析、交集、最长匹配，带单测），插件模块各只有三个类——入口 `*Plugin`、聊天监听 `*ChatListener`、资源包下发 `*PackPusher`。改算法只动 core，改服端适配只动对应模块。
- **注释全英文、每个类的职责写在类注释里**，报错和文件名一起贴出来就能直接定位。
- **遇到不会改的报错，可以直接丢给 AI**：把 `mvn` 报错、服务端堆栈、或「某个 emoji 显示不出来」这类现象贴上来，附上对应模块文件名，基本一轮能修。编译命令只有一条：`mvn -B package`（JDK 21）。

---

## 九、目录

```
Minecraft-TriEmoji/
├── tools/build_unified.py        # 图集 + 资源包 + 三端表
├── emoji-core/                   # 纯 Java 解析核心（有单测）
├── emoji-plugin-modern/          # Paper 1.19 - 26.3
├── emoji-plugin-spigot/          # Spigot 1.19 - 26.3（纯 Bukkit API）
├── emoji-plugin-legacy/          # Spigot/Paper 1.10 - 1.18
├── dist/                         # 构建产物
└── docs/                         # 架构与部署细节
```

## 作者

- GitHub：[@erygghhj](https://github.com/erygghhj)
- 邮箱：3870117206@qq.com

## License

MIT
