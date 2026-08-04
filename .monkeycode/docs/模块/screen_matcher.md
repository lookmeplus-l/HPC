# screen_matcher 模块

`screm_matcher` 目录存放应用的全部 Kotlin 源代码，包名 `com.example.screen_matcher`。

## 结构

```
screen_matcher/
├── MainActivity.kt          # 主界面：图片列表、悬浮窗开关、识别触发
├── FloatingWindowService.kt # 悬浮窗前台服务
├── ScreenshotService.kt     # 截屏前台服务
├── TemplateMatcher.kt       # NCC 模板匹配引擎
└── PresetImageManager.kt    # 预置图片管理
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `MainActivity.kt` | 应用入口，编排各组件协作，实现识别主流程 |
| `TemplateMatcher.kt` | 核心算法模块，决定匹配质量与速度 |
| `FloatingWindowService.kt` | 常驻悬浮窗交互界面 |
| `ScreenshotService.kt` | 屏幕捕获与图片保存 |
| `PresetImageManager.kt` | 模板图片资源管理 |

## 依赖

**本模块依赖**:
- `res/layout/` - 主界面、悬浮窗、列表项布局
- `res/drawable/` - 图标与背景矢量图
- `assets/preset_images/` - 静态预置图片

**依赖本模块的**:
- `AndroidManifest.xml` - 声明 Activity 与两个前台服务
- `app/build.gradle.kts` - 依赖声明与构建配置

## 规范

### 文件命名
- 类文件：PascalCase 单文件单类
- 布局资源：`activity_` / `item_` / `floating_` 前缀

### 代码模式

**伴生对象静态接口**（FloatingWindowService、ScreenshotService 使用）:
```kotlin
companion object {
    fun showImage(path: String) { mainHandler.post { ... } }
}
```

**回调传递**（ScreenshotService 使用）:
```kotlin
interface ScreenshotCallback {
    fun onSuccess(imagePath: String)
    fun onError(message: String)
}
```

### 错误处理
- 文件 IO 异常捕获后返回 `false` 或 `Toast` 提示，不向用户展示原始堆栈
- 权限缺失先引导授权，授权结果通过 `onActivityResult` 处理

### 测试
项目当前无单元测试，验证依赖构建通过 + 真机/模拟器手工验证。

## 添加新文件

### 添加新组件类型

1. 按命名约定创建 Kotlin 文件
2. 若是服务组件，在 `AndroidManifest.xml` 注册并声明前台服务类型
3. 实现所需接口或伴生对象静态方法
4. 从 MainActivity 接入调用链

**检查清单**:
- [ ] 文件命名符合 PascalCase
- [ ] 组件已在 Manifest 注册
- [ ] 错误路径有用户可读提示
