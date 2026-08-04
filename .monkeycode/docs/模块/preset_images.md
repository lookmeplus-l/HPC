# preset_images 资源

`preset_images` 目录存放应用内置的全部静态模板图片，用于模板匹配识别。

## 结构

```
preset_images/
├── manifest.json   # 图片清单（images 数组）
├── 北1门.webp / 北1门.jpg / 北1沙发门.png / ...  # 29 张门图案
└── ...
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `manifest.json` | 声明全部预置图片文件名，与代码中 `loadStaticPresets()` 的 names 列表一致 |
| 各图片文件 | 门图案模板，jpg/png/webp 混合格式 |

## 依赖

**本模块依赖**:
- 无

**依赖本模块的**:
- `PresetImageManager.loadStaticPresets()` - 首次启动复制到应用私有目录

## 预置图片命名

图片按「方位 + 图案特征」命名，用于表示不同形式的门，例如：
- `北1门`、`北4安全门`、`北T门`、`北凹门`、`北红对角门`
- `左对角门`、`左Y青蛙房`、`左音叉门`、`左锤灯笼门`
- `右三L门`、`右骑士门`、`右双L门`
- `南L门`、`南十字门`、`南orz门`、`南三缺一门`

## 添加新图片

1. 将图片文件放入本目录（支持 jpg/png/webp）
2. 在 `manifest.json` 的 `images` 数组追加文件名
3. 在 `PresetImageManager.loadStaticPresets()` 的 `names` 列表追加同名条目
4. 三处保持一致，缺一处会导致图片不显示或清单不一致
