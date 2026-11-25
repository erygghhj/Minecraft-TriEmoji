# Changelog

## 2.2.0 — 2026-11-25

### 新增：告示牌与书页转换
- 三个模块各加一个 `*ContentListener`：监听 `SignChangeEvent` 与 `PlayerEditBookEvent`，把告示牌四行、书与笔每一页里的 emoji 同样换成 PUA 字形。只用了跨版本稳定的 `String` API（`getLine`/`setLine`、`BookMeta#getPages`），1.13 – 26.3 通用。
- 局限：只在玩家编辑提交时处理，命令或其他插件直接写入的内容不会二次转换（见 README「已知限制」）。

### 修复：普通符号被误判成 emoji
- 原先按 Unicode 区间（2190–21FF、2600–27BF、2900–297F 等）判断“像 emoji”，导致聊天里的 `→ ★ ♪` 被替换成 `[不支持的表情]`。现在改为查三张平台表里真实出现过的码点，普通符号原样保留。

### 修复：旗帜被拆成两个字母
- `clusterEnd` 现在把一对区域指示符（`1F1E6–1F1FF`）当作一个簇，`🇨🇳` 命中 `1f1e8-1f1f3` 整旗字形。

### 修复：禁言绕过与异步线程
- 现代版与 Spigot 版监听改为 `ignoreCancelled = true`，被其他插件取消（禁言/过滤）的消息不再被重播。
- 现代版不再在 `AsyncChatEvent` 线程里读配置、发消息，统一 `runTask` 回主线程；Spigot 版同样把配置读取挪进主线程任务。

### 修复：reload 后不下发资源包
- 三个 `*PackPusher` 增加 `pushAll()`，`onEnable` 时对已在线玩家补推，`/reload` 后不用重连也能拿到包。

### 修复：签名阶段改写书页
- `PlayerEditBookEvent` 在 `isSigning = true` 时页面已经定稿，这时候调用 `setPages` 会被服务端拒绝。三个 `*ContentListener` 现在跳过签名阶段（草稿在“完成”那一步已经转换过），并且改为在 `BookMeta` 副本上修改，不再直接改事件里那个对象。

### 文档：legacy 支持范围更正为 1.13 – 1.18
- `1.10` 是遗留笔误。1.10 – 1.12 没有 `font/default.json` 字体提供者系统，资源包在机制上无法新增字形，不在支持范围。
- 同步更正父 `pom.xml` 描述、legacy 模块名、`plugin.yml` / `config.yml` 注释、README、RELEASE_NOTES、ARCHITECTURE；产物文件名保持不变（CI/Release 链接依赖它）。
- README 的 PUA 区间笔误 `U+EFFF` 改为 `U+EEFF`。

## 2.1.0 — 2026-11-17

版本号从 2.0 升到 2.1，同时把“每次有 bug 都要手动改包重传”这件事交给 CI。

### 新增：自动出包 + 自动发布
- `.github/workflows/build.yml` 现在有三个 job：`pack`（生成两份资源包和平台表）、`plugin`（Maven 编译三个模块）、`release`（仅在推 `v*` 标签时触发）。
- 推一个 `v*` 标签，CI 会自动把 6 个产物（4 个 jar + 2 个 zip）挂到对应的 GitHub Release 上，不需要再手工上传。
- CI 构建完成后会重新计算资源包 SHA-1，并**自动写回三个模块的默认 `config.yml`**，保证 jar 里的默认校验值永远和包一致。

### 修复：CI 会覆盖资源包修复
- `tools/build_unified.py` 原先写的是旧版 `pack.mcmeta`（`pack_format: 1` + `supported_formats 1..999`）和一张 512×7680 的长图集，只要 CI 跑一次就会把手工修复顶掉。
- 现在脚本自己产出 `pack_format: 15` + `min_format`/`max_format: 15..999`（modern）和 `pack_format: 4` + `supported_formats 4..9`（legacy），图集也按 15 张 512×512 分片输出。

### 变更：版本号
- `pom.xml`（父工程与三个模块）、三个 `plugin.yml`、`README`、`RELEASE_NOTES` 全部改为 `2.1.0`。
- 产物名同步为 `UniversalEmoji-{Paper,Spigot}-1.20+-2.1.0.jar`、`UniversalEmoji-Paper-Legacy-1.10-1.18-2.1.0.jar`、`UniversalEmoji-Spigot-Legacy-1.10-1.18-2.1.0.jar`。

## 2.0.0-beta.1 — 2026-11-17

修复资源包在 1.21.9 及以上完全无法加载的问题（用户实测 1.21.11 客户端提示“已损坏或不兼容”“重载资源包失败”）。

### 修复：pack.mcmeta 用错字段
- 1.21.9 起 Mojang 删除了 `supported_formats`，改由 `min_format` / `max_format` 描述兼容区间。
- `modern` 包改成 `pack_format: 15` + `min_format` / `max_format: 15..999`，覆盖 1.20 – 26.3。
- `legacy` 包改成 `pack_format: 4` + `supported_formats: 4..9`，覆盖 1.13 – 1.19，并**移除全部 `reference` 类型**（`reference` 是 1.20 才引入的，1.13 – 1.19 不认识，会被整包判失败）。

### 修复：图集过高导致加载失败
- 原来的单张 `universal_emoji.png` 是 512×7680，部分客户端加载不了。
- 现在按 16 行一片切成 **15 张 512×512** 分片，`default.json` 用 15 个 `bitmap` provider 分别指过去，字符总数不变（3840）。

### 调整：版本下限
- 资源包机制从 **1.13** 起才存在（`assets/minecraft/font/default.json` 是 1.13 引入的）。
- 1.10 – 1.12 客户端在机制层面无法通过资源包新增字形，文档已改为 **1.13 – 26.3**，并写明 1.10 – 1.12 的退化表现（emoji 显示为方块）。
- legacy 资源包覆盖范围扩到 1.19（原先只到 1.18）。

### 同步
- 两个资源包的 SHA-1 变了：modern `ba8b4588…`、legacy `2d461f9a…`。
- 四个 jar 内默认 `config.yml` 的 `sha1` 已同步，`dist/manifest.json`、README、RELEASE_NOTES 一并更新。
- 四个 jar 用 JDK 21 重新编译：P 版 class 65、S 版 class 61、Legacy class 52。

## 2.0.0-beta — 2026-11-15

版本号进入 2.0 序列。本版在原有 Paper / Legacy 两支之外新增 Spigot 分支。

### 新增：S 版（Spigot 1.19 – 26.3）
- 新模块 `emoji-plugin-spigot`，只用 `org.bukkit` API，字节码里零 `io.papermc` / `net.kyori` / `net.md_5` 引用。
- 聊天监听改用 `AsyncPlayerChatEvent`，异步线程只取字符串，广播回主线程执行，继续绕开 1.19+ 聊天签名。
- 资源包下发改成反射查找 `setResourcePack` 重载（4 参 → 3 参 → 2 参），一份 jar 覆盖 1.19 – 26.3。
- 产物 `UniversalEmoji-Spigot-2.0.0-beta.jar`，`api-version: 1.19`，class 版本 61（Java 17）。

### 文件命名统一
- 四个 jar 统一成 `<服端>-<版本范围>-<版本号>`：
  - `UniversalEmoji-Paper-1.20+-2.0.0-beta.jar`（Paper 1.19 – 26.3）
  - `UniversalEmoji-Spigot-1.20+-2.0.0-beta.jar`（Spigot 1.19 – 26.3）
  - `UniversalEmoji-Paper-Legacy-1.10-1.18-2.0.0-beta.jar`
  - `UniversalEmoji-Spigot-Legacy-1.10-1.18-2.0.0-beta.jar`
- 两个 Legacy 包字节相同（SHA-1 `fba4d11184424bb188c35f1fcaba73338643f498`），同一份代码同时兼容 Spigot 与 Paper 1.10 – 1.18，按服端挑名字即可。
- 三个模块 pom 的 `finalName` 同步改掉，源码重新 `mvn package` 会产出完全一样的文件名。

### 变更
- `plugin.yml` 描述更正为 `Paper 1.19 - 26.3` 与 `Spigot/Paper 1.10 - 1.18`，`website` 指向真实仓库。
- README 新增「适配版本（《我的世界》Java 版）」矩阵，明确资源包为强制依赖。
- 新增 `RELEASE_NOTES.md`，作为 Release 正文来源。
- 两个 jar 随版本号重命名。

### 不影响已部署服务器
- Java 代码、图集、三端码表均未改动。
- `dist/` 两个资源包 SHA-1 与 1.0.0 完全一致（modern `f2ce2a26…` / legacy `a04cc1e4…`），无需换包。

---

## 1.0.0 — 2026-11-15

首个可用版本，覆盖 Minecraft 1.10 – 26.3。

### 构建
- `tools/build_unified.py`：全量 Twemoji v15.1.0（3,838 个），32×32，16 列拼成 512×7,680 图集。
- 图集 `chars` 按行分条、等长补齐，避免纹理 warp。
- `pack_format: 1` + `supported_formats: 1..999`，26.3 不弹不兼容红字。
- 资源包分两支：`modern`（1.20+，reference/include 结构）与 `legacy`（1.10–1.19.4，内联 bitmap + legacy_unicode），均为**追加** provider，不抹掉原版字形。
- 输出 iOS / Android / Windows 三端码表，Windows 用 Emoji 版本阈值近似。

### 插件
- `emoji-core`：三端求交集、手动坐标、ZWJ 簇最长匹配、PUA 越界防御。11 个单测。
- `emoji-plugin-modern`（1.19.4–26.3）：`AsyncChatEvent` HIGHEST，`cancel` + `Server#sendMessage` 绕开聊天签名；`setResourcePack` 四级签名回退。
- `emoji-plugin-legacy`（1.10–1.15）：`AsyncPlayerChatEvent` + `setMessage`。
- 资源文件走 classpath（`getResource`），jar 内置，无本地路径依赖。

### 验证
- Paper 1.21.11（build 132）：插件加载，3,582 条可用，PUA U+E000..U+EEFD。
- Paper 26.3（build 252，Java 25）：同样加载成功，无 NoSuchMethodError。
- Maven 全模块 `package` 通过，单测 11/11。
