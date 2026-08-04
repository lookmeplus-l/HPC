# 截图识别工具（Screen Matcher）文档

本仓库是一个原生 Android 应用，通过截屏与本地模板图片的匹配识别，帮助用户在迷宫类游戏中快速识别屏幕上的「门」图案。文档涵盖系统架构、组件接口、开发者指南与核心概念。

**快速链接**: [架构](./ARCHITECTURE.md) | [接口](./INTERFACES.md) | [开发者指南](./DEVELOPER_GUIDE.md)

---

## 核心文档

### [架构](./ARCHITECTURE.md)
系统设计、技术栈、组件结构和数据流程。从这里开始了解系统如何运作。

### [接口](./INTERFACES.md)
组件公开方法、前台服务契约、配置接口。集成或修改系统的参考。

### [开发者指南](./DEVELOPER_GUIDE.md)
环境搭建、开发工作流、编码规范和常见任务。贡献者必读。

---

## 模块

| 模块 | 描述 | README |
|------|------|--------|
| `screen_matcher/` | 应用全部 Kotlin 源代码 | [README](./模块/screen_matcher.md) |
| `scripts/` | GitHub Actions 构建监控脚本 | [README](./模块/scripts.md) |
| `preset_images/` | 静态预置门图案资源 | [README](./模块/preset_images.md) |

---

## 核心概念

理解这些领域概念有助于导航代码库：

| 概念 | 描述 |
|------|------|
| [模板匹配](./专有概念/模板匹配.md) | 多尺度 NCC 相似度算法，决定识别命中 |
| [预置图片](./专有概念/预置图片.md) | 静态与动态模板图片的管理模型 |
| [悬浮窗服务](./专有概念/悬浮窗服务.md) | 常驻悬浮窗交互界面与状态展示 |
| [截屏服务](./专有概念/截屏服务.md) | MediaProjection 屏幕捕获与图片保存 |

---

## 入门指南

### 项目新人？

按此路径学习：
1. **[架构](./ARCHITECTURE.md)** - 了解全局
2. **[核心概念](#核心概念)** - 学习领域术语
3. **[开发者指南](./DEVELOPER_GUIDE.md)** - 搭建环境

### 首次贡献？

1. **[开发者指南](./DEVELOPER_GUIDE.md)** - 搭建和工作流
2. **[常见任务](./DEVELOPER_GUIDE.md#常见任务)** - 分步指南
3. **[模块 README](#模块)** - 各目录职责

---

## 快速参考

### 命令

```bash
./gradlew assembleDebug   # 构建 debug APK
./gradlew assembleRelease # 构建 release APK
./gradlew clean           # 清理构建产物
scripts/fb.sh status      # 查看构建监控状态
```

### 重要文件

| 文件 | 目的 |
|------|------|
| `app/src/main/kotlin/com/example/screen_matcher/MainActivity.kt` | 应用入口与识别主流程 |
| `app/src/main/assets/preset_images/manifest.json` | 预置图片清单 |
| `.github/workflows/build-apk.yml` | CI 构建与发布配置 |
| `app/build.gradle.kts` | 应用构建配置 |
