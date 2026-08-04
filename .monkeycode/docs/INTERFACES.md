# 接口文档

本项目是 Android 应用，接口包括 Kotlin 组件间的公开调用契约、前台服务通信方式以及外部 CI 配置接口。

## 组件公开接口

### FloatingWindowService（伴生对象静态方法）

服务以伴生对象暴露静态方法供 MainActivity 直接调用，无需绑定服务。

| 方法 | 参数 | 说明 |
|------|------|------|
| `showImage(path: String)` | 图片绝对路径 | 在悬浮窗中显示匹配到的模板图片 |
| `hideImage()` | 无 | 隐藏悬浮窗中的匹配图片，并置状态为「未找到匹配」 |
| `setStatus(text: String)` | 状态码 | 更新悬浮窗状态文字（见下方状态码） |
| `onStartRecognition: (() -> Unit)?` | 无参回调 | 悬浮窗「开始识别」按钮的回调，由 MainActivity 注入 |

**`setStatus` 状态码映射**:

| 状态码 | 显示文字 |
|--------|---------|
| `scanning` | 正在识别中... |
| `matched` | 已匹配 |
| `no_match` | 未找到匹配 |
| `idle` | 点击开始识别 |
| 其他 | 原样显示 |

### ScreenshotService（伴生对象静态方法）

| 方法 | 参数 | 说明 |
|------|------|------|
| `start(context, resultCode, data, projectionManager, callback)` | 见下方 | 启动截屏前台服务 |

**参数说明**:

| 参数 | 类型 | 说明 |
|------|------|------|
| `context` | `Context` | 调用方上下文 |
| `resultCode` | `Int` | MediaProjection 授权结果码（`Activity.RESULT_OK`） |
| `data` | `Intent` | MediaProjection 授权返回的 Intent |
| `projectionManager` | `MediaProjectionManager` | 系统媒体投影管理器 |
| `callback` | `ScreenshotCallback` | 截屏结果回调 |

**`ScreenshotCallback` 接口**:

```kotlin
interface ScreenshotCallback {
    fun onSuccess(imagePath: String)  // 截屏成功，返回 PNG 文件绝对路径
    fun onError(message: String)     // 截屏失败，返回错误描述
}
```

### TemplateMatcher（单例对象）

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `match(screenshotPath: String, templatePath: String)` | 截图与模板文件路径 | `MatchResult` | 执行多尺度 NCC 匹配 |

**`MatchResult` 数据类**:

```kotlin
data class MatchResult(
    val matched: Boolean,        // 是否达到匹配阈值（NCC >= 0.45）
    val nccScore: Float,         // 归一化互相关分数
    val bestX: Int,              // 匹配位置 X（原始截图坐标）
    val bestY: Int,              // 匹配位置 Y（原始截图坐标）
    val bestScale: Float,        // 命中缩放档位
    val templateWidth: Int,      // 命中模板宽度（原始截图坐标）
    val templateHeight: Int      // 命中模板高度（原始截图坐标）
)
```

### PresetImageManager（实例类）

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `init()` | 无 | 无 | 初始化：加载静态与动态预置图片 |
| `addImage(sourceFile: File)` | 源文件 | `Boolean` | 添加自定义图片，自动处理重名 |
| `removeImage(name: String)` | 图片名 | 无 | 删除动态图片并更新清单 |
| `loadThumbnail(name: String)` | 图片名 | `Bitmap?` | 加载缩略图（采样率 4） |
| `cachePath(name: String)` | 图片名 | `String?` | 返回图片缓存路径 |
| `isDynamic(name: String)` | 图片名 | `Boolean` | 判断是否为用户动态添加 |
| `allNames` | - | `List<String>` | 全部图片名列表 |

## 前台服务契约

### FloatingWindowService

- **启动方式**: `startForegroundService`（O+）或 `startService`
- **前台服务类型**: `specialUse`（`FOREGROUND_SERVICE_SPECIAL_USE`）
- **通知**: 渠道 `screen_matcher_floating`，ID `2001`
- **悬浮窗类型**: `TYPE_APPLICATION_OVERLAY`（O+）
- **权限**: `SYSTEM_ALERT_WINDOW`（需运行时授权）

### ScreenshotService

- **启动方式**: `startForegroundService`（O+）或 `startService`
- **前台服务类型**: `mediaProjection`（`FOREGROUND_SERVICE_MEDIA_PROJECTION`）
- **通知**: 渠道 `screen_matcher_screenshot`，ID `2002`
- **生命周期**: `onStartCommand` 返回 `START_NOT_STICKY`，完成后 `stopSelf`
- **Intent 数据键**: `resultCode`（Int）、`data`（Parcelable Intent）

## 组件间事件

| 事件 | 触发方 | 消费方 | 载荷 |
|------|--------|--------|------|
| `scanning` | MainActivity | FloatingWindowService | 状态码 |
| `matched` | MainActivity | FloatingWindowService | 状态码 + `showImage(path)` |
| `no_match` | MainActivity | FloatingWindowService | 状态码 |
| `onSuccess(imagePath)` | ScreenshotService | MainActivity | 截图文件路径 |
| `onError(message)` | ScreenshotService | MainActivity | 错误消息 |

## 配置接口

### Gradle 构建

| 配置项 | 位置 | 当前值 |
|--------|------|--------|
| `namespace` / `applicationId` | `app/build.gradle.kts` | `com.example.screen_matcher` |
| `compileSdk` | `app/build.gradle.kts` | `34` |
| `minSdk` | `app/build.gradle.kts` | `21` |
| `targetSdk` | `app/build.gradle.kts` | `34` |
| AGP 版本 | `build.gradle.kts` | `8.2.0` |
| Kotlin 版本 | `build.gradle.kts` | `1.9.22` |
| Gradle 版本 | `gradle/wrapper/gradle-wrapper.properties` | `8.5` |

### Android 权限（AndroidManifest.xml）

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 截屏前台服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 悬浮窗前台服务 |
| `POST_NOTIFICATIONS` | 前台服务通知 |

### GitHub Actions 工作流

工作流文件: `.github/workflows/build-apk.yml`

| 配置项 | 值 |
|--------|-----|
| 触发 | push 到 `master` / `workflow_dispatch` |
| 运行环境 | `ubuntu-latest` |
| JDK | 17（Temurin） |
| Gradle | 8.5 |
| 构建任务 | `assembleDebug` + `assembleRelease` |
| 产物 | `app-debug.apk`、`app-release.apk` |
| 发布 | Artifacts + GitHub Release（tag `v<run_number>`） |

### 构建监控脚本（scripts/monitor_build.py）

| 配置项 | 位置 | 当前值 |
|--------|------|--------|
| 仓库 | `REPO_OWNER` / `REPO_NAME` | `lookmeplus-l` / `HPC` |
| 工作流 | `WORKFLOW_FILE` | `build-apk.yml` |
| 状态文件 | `STATE_FILE` | `scripts/fb_monitor.state` |
| 日志文件 | `LOG_FILE` | `scripts/fb_monitor.log` |
| Token 来源 | 环境变量或 `git credential fill` | 见开发者指南 |
