# 开发者指南

## 项目目的

截图识别工具（Screen Matcher）是一个原生 Android 应用，通过截屏与本地模板图片的匹配识别，帮助用户在迷宫类游戏中快速识别屏幕上出现的「门」图案。

**核心职责**:
- 提供预置模板图片列表的浏览与自定义图片的增删
- 以悬浮窗形式提供常驻识别入口与结果展示
- 通过 MediaProjection 截屏并对截屏执行多尺度 NCC 模板匹配

**相关系统**:
- GitHub Actions - 自动构建 APK 并发布到 GitHub Release
- scripts/monitor_build.py - 构建状态监控与自动修复脚本

## 环境搭建

### 前置条件

- JDK 17
- Android SDK（Platform 34）
- Android Studio（推荐，用于运行与调试）
- Gradle 8.5（可通过 Wrapper 自动下载）

### 安装

```bash
# 克隆仓库
git clone <repo-url>
cd HPC

# 通过 Wrapper 构建（无需本机安装 Gradle）
./gradlew assembleDebug
```

### 环境变量

本项目的 Android 应用本身不读取环境变量。仅 `scripts/monitor_build.py` 需要 GitHub Token（优先使用 `GITHUB_TOKEN` 或 `MONKEYCODE_GITHUB_ACCESS_TOKEN`，其次通过 `git credential fill` 自动获取）。

⚠️ **绝不提交密钥**。

### 运行

```bash
# 构建 debug APK
./gradlew assembleDebug

# 构建 release APK（使用 debug 签名，可直接安装）
./gradlew assembleRelease

# 清空并构建
./gradlew clean assembleDebug

# 安装到已连接的设备
./gradlew installDebug
```

## 开发工作流

### 代码质量工具

本项目未配置独立的 lint / test 脚本。构建时 Gradle 会执行内置 `lintVitalRelease` 等检查。

| 工具 | 命令 | 目的 |
|------|------|------|
| Gradle 构建 | `./gradlew assembleDebug` | 编译与打包验证 |
| Kotlin 编译 | `./gradlew :app:compileDebugKotlin` | 仅编译 Kotlin 源码 |
| 清理 | `./gradlew clean` | 清理构建产物 |

### 提交前检查

1. 运行 `./gradlew assembleDebug` 确认编译通过
2. 检查 `git status` 仅包含预期文件
3. 确认没有遗留调试日志

### 分支策略

- `master` - 主分支，push 即触发 GitHub Actions 构建

### Pull Request 流程

本项目为单人直推 `master` 工作流，未配置 PR 规范。若引入协作，建议：从 `master` 创建 `feature/*` 分支，构建通过后合并回 `master`。

## 常见任务

### 添加新的预置模板图片

**需修改的文件**:
1. `app/src/main/assets/preset_images/` - 放入图片文件（jpg/png/webp）
2. `app/src/main/assets/preset_images/manifest.json` - 在 `images` 数组追加文件名
3. `app/src/main/kotlin/com/example/screen_matcher/PresetImageManager.kt` - 在 `loadStaticPresets()` 的 `names` 列表追加同名条目

**步骤**:
1. 准备图片并复制到 assets 目录
2. 在 manifest.json 与 PresetImageManager 中同步登记文件名
3. 构建并安装验证图片能显示在列表中

**示例提交**: `feat: add preset image 北X门`

### 调整匹配灵敏度

**需修改的文件**:
1. `app/src/main/kotlin/com/example/screen_matcher/TemplateMatcher.kt`

**步骤**:
1. 修改 `NCC_THRESHOLD`（当前 0.45）控制最终匹配判定
2. 修改 `COARSE_THRESHOLD`（当前 0.35）控制粗扫描候选数
3. 修改 `SCALES` 数组增删缩放档位
4. 修改 `MAX_SOURCE_WIDTH`（当前 600）控制截图缩放基准

### 修复 Bug

**流程**:
1. 通过 GitHub Actions 日志定位失败的步骤
2. 本地复现（`./gradlew assembleDebug`）
3. 用最小改动修复
4. 重新构建验证
5. 推送到 `master` 触发 CI 复核

**示例提交**: `fix: 修复截图功能 Invalid intent data 错误`

### 查看 GitHub Actions 构建状态

```bash
# 运行构建监控脚本（每 15 分钟检查一次，成功则自动停止）
cd scripts && python3 monitor_build.py

# 手动触发一次工作流（需 workflow_dispatch 权限）
curl -X POST \
  -H "Authorization: Bearer <GITHUB_TOKEN>" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/lookmeplus-l/HPC/actions/workflows/build-apk.yml/dispatches \
  -d '{"ref":"master"}'
```

## 编码规范

### 文件组织
- 每个类一个文件，位于 `app/src/main/kotlin/com/example/screen_matcher/`
- 布局文件位于 `app/src/main/res/layout/`，以 `activity_`、`item_`、`floating_` 前缀区分用途
- 图标资源使用矢量图（XML），位于 `app/src/main/res/drawable/`

### 命名

| 类型 | 约定 | 示例 |
|------|------|------|
| 类 | PascalCase | `FloatingWindowService` |
| 函数 | camelCase | `startRecognition` |
| 常量 | SCREAMING_SNAKE | `NCC_THRESHOLD` |
| 视图 ID | 前缀 + 语义 | `rv_images`, `tv_empty`, `iv_thumb` |
| 布局资源 | 类型前缀 + 语义 | `activity_main`, `item_image` |

### 错误处理

- 文件 IO 使用 `try/catch` 并在失败时给出用户可读的提示（`Toast`）
- 权限缺失时先检查再引导授权
- 回调基于主线程 `runOnUiThread` 执行 UI 更新

### 日志

- 本项目未集成日志框架，UI 反馈使用 `Toast`，监控脚本使用文件日志（`fb_monitor.log`）

### 测试

- 项目当前未包含单元测试或 UI 测试
- 验证方式为构建通过 + 真机/模拟器安装验证
