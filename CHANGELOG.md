# Changelog

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
