# GitHub Actions 构建监控

任务名称：**fb**

## 功能说明

自动监控 GitHub Actions 构建状态，每 15 分钟检查一次：
- ✅ **构建成功** → 自动暂停监控任务
- ❌ **构建失败** → 自动尝试修复并提交推送

## 配置说明

### 环境变量

脚本需要 `GITHUB_TOKEN` 环境变量，用于访问 GitHub API：

```bash
export GITHUB_TOKEN="your_github_token"
```

或者使用平台提供的环境变量（如 `MONKEYCODE_GITHUB_ACCESS_TOKEN`）。

### 监控的目标仓库

- 仓库：`lookmeplus-l/HPC`
- 工作流：`build-apk.yml`
- 分支：`master`

## 使用方法

### 1. 启动监控任务

```bash
# 启动监控（会立即执行一次检查）
/workspace/scripts/fb.sh start
```

### 2. 查看监控状态

```bash
# 查看当前状态和最近日志
/workspace/scripts/fb.sh status
```

### 3. 停止监控任务

```bash
# 手动停止监控
/workspace/scripts/fb.sh stop
```

### 4. 设置定时任务（crontab）

```bash
# 编辑 crontab
crontab -e

# 添加以下行（每 15 分钟执行一次）
*/15 * * * * /workspace/scripts/fb.sh run
```

## 自动修复功能

脚本会尝试自动修复以下类型的构建错误：

| 错误类型 | 修复策略 |
|---------|---------|
| 编译错误 | 添加缺失的依赖 |
| 资源错误 | 修正资源配置 |
| 符号未找到 | 检查依赖完整性 |
| 认证错误 | 记录日志，需要手动处理 |

## 状态文件

- **位置**：`/workspace/scripts/fb_monitor.state`
- **作用**：标记监控任务是否处于活动状态
- **文件存在** = 监控运行中
- **文件不存在** = 监控已停止

## 日志文件

- **位置**：`/workspace/scripts/fb_monitor.log`
- **格式**：`[YYYY-MM-DD HH:MM:SS] 日志内容`

## 工作流程

```
启动监控
    ↓
创建状态文件 fb_monitor.state
    ↓
每 15 分钟检查构建状态
    ↓
    ├─ 成功 → 删除状态文件，停止监控
    ├─ 失败 → 尝试修复 → 提交推送 → 继续监控
    └─ 构建中 → 等待下次检查
```

## 注意事项

1. **GitHub Token 权限**：需要 `actions:write` 和 `repo` 权限
2. **网络要求**：需要能够访问 GitHub API
3. **Python 环境**：需要 Python 3 环境
4. **Git 配置**：需要已配置 Git 用户信息才能自动提交

## 故障排查

### 监控未执行

```bash
# 检查状态文件是否存在
ls -la /workspace/scripts/fb_monitor.state

# 查看日志
cat /workspace/scripts/fb_monitor.log
```

### 无法访问 GitHub API

```bash
# 测试 Token 是否有效
curl -H "Authorization: Bearer $GITHUB_TOKEN" https://api.github.com/user
```

### 自动修复失败

检查日志文件了解具体错误，然后手动修复构建问题。
