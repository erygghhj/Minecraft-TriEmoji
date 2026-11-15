# Changelog

## 2.0.0-beta — 2026-11-15

版本号进入 2.0 序列。功能逻辑与 1.0.0 相同，本版只改元数据与文档。

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
