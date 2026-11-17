# Universal Emoji 2.0.0-beta

《我的世界》Java 版 **1.10 → 26.3** 的 Emoji 聊天系统。服务端把原生 emoji 换成私用区字符，客户端靠资源包渲染成彩色图标，不需要任何客户端模组。

## 下载后请成对使用

**资源包是强制依赖，不是可选装饰。** 服务端把 😂 换成 `U+E8D5` 之后，没装资源包的客户端只会显示空白或方块。插件与资源包必须一起部署。

### 分组一：1.20 – 26.3（含 26.3）

| 文件 | 类型 |
| --- | --- |
| `UniversalEmoji-Paper-1.20+-2.0.0-beta.jar` | 插件（Paper 1.19 – 26.3） |
| `Universal_Emoji.zip` | **资源包（必需）** |

### 分组二：Spigot 1.19 – 26.3（S 版）

| 文件 | 类型 |
| --- | --- |
| `UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar` | 插件（Spigot 1.19 – 26.3，**纯 Bukkit API，不含任何 Paper 类**） |
| `Universal_Emoji.zip` | **资源包（必需）**（1.20 – 26.3） |
| `Universal_Emoji_legacy.zip` | **资源包（必需）**（1.19 – 1.19.4） |

### 分组三：1.10 – 1.18（老版本服）

| 文件 | 类型 |
| --- | --- |
| `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar` | 插件（Spigot/Paper 1.10 – 1.18） |
| `Universal_Emoji_legacy.zip` | **资源包（必需）** |

## 《我的世界》版本对照

| 游戏版本 | 资源包 | 插件 | 说明 |
| --- | --- | --- | --- |
| 1.10 – 1.15 | `Universal_Emoji_legacy.zip` | `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar` | `AsyncPlayerChatEvent`，无聊天签名 |
| 1.16 – 1.18 | `Universal_Emoji_legacy.zip` | `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar` | `api-version: 1.13` |
| 1.19 – 1.19.4 | `Universal_Emoji_legacy.zip` | **P 版** `UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`<br>**S 版** `UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar` | 1.19 起有聊天签名，取消事件后由服务端重新广播 |
| 1.20 – 26.3 | `Universal_Emoji.zip` | **P 版** `UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`<br>**S 版** `UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar` | P 版走 `AsyncChatEvent`，S 版走 `AsyncPlayerChatEvent` |

两份资源包的图集逐字节相同，只有字体 JSON 写法不同：`legacy` 用 1.16.5 结构（内联 `bitmap` + `legacy_unicode`），`modern` 用 1.21.4 结构（三个 `reference` + 自己的 `bitmap`）。

`pack.mcmeta` 同时写了 `pack_format: 1` 和 `supported_formats: 1..999`，任何版本都不会弹“不兼容”提示。

## 安装

1. 把对应的资源包放到任意 HTTP/HTTPS 静态服务器（GitHub Release、对象存储、Nginx 都行）。
2. jar 丢进 `plugins/`。
3. 首次启动生成 `plugins/UniversalEmoji/config.yml`，填两行：

```yaml
resourcepack:
  url: "https://你的域名/Universal_Emoji.zip"
  sha1: "ba8b4588060fe6bd45f51ec9bd5460be785bd75c"
  force: true
```

4. 重启，进服自动收包。

当前两个资源包的 SHA-1：

- `Universal_Emoji.zip` → `ba8b4588060fe6bd45f51ec9bd5460be785bd75c`
- `Universal_Emoji_legacy.zip` → `2d461f9a75873b82efe201e066b981f837e521c8`

## 覆盖范围

- 图集 512 × 7680，16 列 × 240 行，每格 32px，共 3,838 个 Twemoji 字形
- 私用区 `U+E000 .. U+EFFF`，实际用到 `U+E000 .. U+EEFD`
- 三端交叉校验：iOS 3,835 / Android 3,838 / Windows 3,585，**交集 3,582** 个可安全渲染
- 缺端直接降级为 `[不支持的表情]`，不会给玩家空白方块

## 实测

- Paper 1.21.11（build 132）：加载 3,582 sequences，PUA `U+E000..U+EEFD`
- Paper 26.3（build 252，Java 25）：同样加载成功，无 `NoSuchMethodError`
- `mvn package` 三模块全 SUCCESS，11 个单元测试通过

## 已知限制

- 资源包 PNG 约 6MB，客户端首次进服要下载一次。
- 三端表是厂商支持矩阵的**近似值**，不是 Mojang 官方数据；需要精确可改 `tools/platform_overrides.json`。
- 1.19 以下没有聊天签名问题，`legacy` 模块保留原版聊天格式（含玩家名）。
