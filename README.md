# guitar-dojo · 吉他道场

[![CI](https://github.com/zmdld11/guitar-dojo/actions/workflows/ci.yml/badge.svg)](https://github.com/zmdld11/guitar-dojo/actions/workflows/ci.yml)

一个给「想坚持练琴但总断更」的电吉他初学者的安卓日常训练 App。

不是又一个调音器合集，而是把**调音 → 设备调试 → 今日乐句 → 考核 → 保养收尾**串成一条完整的每日训练流：走完一遍，才算完成今天的练习。

## 为什么做这个

买了琴、买了书、看了教程，还是练不下去——问题不在热情，在于没有一个能每天告诉我「现在该练什么、练到什么程度算过」的东西。市面上开源项目里调音器、节拍器、MIDI 播放器都是成熟单品，但几乎没有把训练计划和进度自适应串成闭环的。

所以这个 App 的核心是**每日训练计划引擎**：练不过就自动降速重练，练过了就提速进阶，难度曲线卡在「太浅的入门教程」和「第二周就劝退的 365 日」之间。

## 功能规划

| 模块 | 说明 |
| --- | --- |
| 节拍器 | 1–300 BPM、拍号、每拍细分、tap tempo、小节循环显示；基于音频时钟调度，长跑不漂移 |
| 调音器 / 拾音器 | 麦克风实时音高检测，吉他模式 + 半音阶模式；针对电吉他泛音误判做专门处理 |
| 乐句库 | Guitar Pro 转 JSON、选小节循环、0.25x–1.5x 变速播放、简化 TAB 小节条 |
| 每日训练流 | 训练向导：调音 → 设备调试 → 今日乐句 → 考核 → 保养收尾，支持断点续练 |
| 考核系统 | 音准 + 节奏双维度评分，结果驱动次日计划调整 |
| 无琴日模式 | 在校日只能做无琴内容：乐理微卡片 + 节奏拍手训练 |

## 技术栈

Kotlin + Jetpack Compose（Material 3），minSdk 26。音频引擎独立为 `:core:audio` 模块，便于单元测试。

## 项目文档

- [开发任务目标（ROADMAP）](docs/ROADMAP.md) — 里程碑、issue 拆分、验收标准
- [立项调研报告](docs/调研报告.md) — 数据源、音高检测方案、训练体系参考
- [手型与教学插图提示词](docs/画图提示词.md) — 交给文生图模型出图的完整清单

## 构建

### 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | compileSdk 35、build-tools 35.0.0 |
| Gradle | 8.14（仓库自带 wrapper，无需单独安装） |
| 最低系统 | Android 8.0（API 26） |

首次构建前确认 `local.properties` 里的 `sdk.dir` 指向本机 Android SDK；若设置了 `ANDROID_HOME` 可省略。

### 常用命令

```bash
./gradlew assembleDebug            # 构建 debug APK
./gradlew test                     # 单元测试
./gradlew lint ktlintCheck detekt  # 静态检查
./gradlew installDebug             # 安装到已连接的设备/模拟器
```

APK 输出在 `app/build/outputs/apk/debug/`。

### 模块结构

| 模块 | 职责 |
| --- | --- |
| `:app` | Compose UI、导航、业务逻辑 |
| `:core:audio` | 音频引擎（节拍调度、音高检测、播放），纯 Kotlin 接口，不依赖 Compose 与 Context |

## 版权声明

仓库内置的练习乐句均为自编内容。从商业曲谱（Guitar Pro / Songsterr 等）转换出的乐句数据仅作个人本地练习使用，存放在 `local_phrases/` 且已在 `.gitignore` 中排除，**不随仓库分发**。

## 开发流程

遵循 [dev-workflow](https://github.com/zmdld11/dev-workflow) 规范：issue 驱动、Conventional Commits、里程碑收尾打 tag 并更新 CHANGELOG。
