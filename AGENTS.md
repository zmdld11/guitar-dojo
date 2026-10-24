# guitar-dojo 项目规则

## 构建与测试

工程当前未初始化（M0 里程碑）。初始化后补全本节，预期命令：

```bash
./gradlew assembleDebug      # 构建 debug APK
./gradlew test               # 单元测试
./gradlew lint check         # lint + ktlint + detekt
./gradlew installDebug       # 装机（需连接设备或模拟器）
```

模块结构：`:app`（UI 与业务）、`:core:audio`（音频引擎，纯 Kotlin，不依赖 Android UI，必须可单测）。

## 硬性约束

- **`local_phrases/` 永不入库**。该目录存放从商业曲谱（Guitar Pro / Songsterr）转换的乐句 JSON，属版权内容，已在 `.gitignore` 排除。提交前务必确认 `git status` 里没有它的内容。
- 仓库内置乐句必须自编，不抄任何商业曲谱片段。
- 音频调度禁止使用 `Handler.postDelayed` / `Timer` / `System.currentTimeMillis()` 驱动节拍：必须基于 AAudio 音频回调时钟（见 `docs/ROADMAP.md` #6）。
- 音频引擎对外接口保持纯 Kotlin，不在 `:core:audio` 里引入 Compose 或 Android Context 依赖（采样播放的 Context 注入走接口）。

## 文档索引

- `docs/ROADMAP.md` — 里程碑与 issue 清单，开工前先读
- `docs/调研报告.md` — 技术选型依据
- `docs/画图提示词.md` — 插图清单（用户负责出图，agent 负责集成）

## 流程

- 个人项目简化主干开发：日常直接提交 main，大改动开 feature 分支。
- commit 用 Conventional Commits 并带 issue 号。
- 版本 SemVer，里程碑收尾打 tag + 更新 CHANGELOG。
