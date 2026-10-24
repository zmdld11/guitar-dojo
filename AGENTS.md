# guitar-dojo 项目规则

## 构建与测试

```bash
./gradlew assembleDebug      # 构建 debug APK
./gradlew test               # 单元测试
./gradlew lint check         # lint + ktlint + detekt
./gradlew installDebug       # 装机（需连接设备或模拟器）

python tools/build_builtin_phrases.py --check   # 内置乐句产物是否最新
python tools/gp2json.py <song.gp>               # 商业谱转本地 JSON（只落 local_phrases/）
```

模块结构：`:app`（UI 与业务）、`:core:phrase`（乐句 Schema/解析/校验，纯 Kotlin）、`:core:audio`（音频引擎，纯 Kotlin，不依赖 Android UI，必须可单测）。

## 硬性约束

- **`local_phrases/` 与 `data/` 永不入库**。前者放从商业曲谱（Guitar Pro / Songsterr）转换的乐句 JSON，后者放本机参考书（含 365 日电子书）；都已在 `.gitignore` 排除。提交前务必确认 `git status` 里没有它们。
- 仓库内置乐句必须自编，不抄任何商业曲谱片段；新增/修改内置乐句只能改 `tools/build_builtin_phrases.py` 再重新生成，不要手改 assets。
- 音频调度禁止使用 `Handler.postDelayed` / `Timer` / `System.currentTimeMillis()` 驱动节拍：必须基于音频帧/播放头。节拍器与乐句播放共用「音频线程渲染 + 播放头轮询」模式。
- 乐句播放与节拍器必须混在同一条 AudioTrack 里，避免两路音频流的启动相位差。
- `:core:audio` 对外接口保持纯 Kotlin，不在模块里引入 Compose 或 Android Context；`AudioTrack` 等平台 API 只用于引擎内部实现。
- `PracticePlan` / `PhraseScheduler` 的行为改动必须补/改单测（帧号、变速、循环回绕）。

## 文档索引

- `docs/ROADMAP.md` — 里程碑与 issue 清单，开工前先读
- `docs/M3-乐句库设计.md` — JSON Schema、GP 转换、播放引擎设计
- `docs/调研报告.md` — 技术选型依据
- `docs/画图提示词.md` — 插图清单（用户负责出图，agent 负责集成）

## 流程

- 个人项目简化主干开发：日常直接提交 main，大改动开 feature 分支。
- commit 用 Conventional Commits 并带 issue 号。
- 版本 SemVer，里程碑收尾打 tag + 更新 CHANGELOG。
