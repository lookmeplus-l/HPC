# scripts 模块

`scripts` 目录存放构建监控脚本（任务名 fb），用于自动检查 GitHub Actions 构建状态。

## 结构

```
scripts/
├── monitor_build.py    # 主监控逻辑（Python 3）
├── fb.sh               # 管理脚本：start/status/stop/run
├── fb_daemon.sh        # 守护进程，每 15 分钟调用一次 monitor_build.py
└── README.md           # 脚本使用文档
```

运行时生成（已被 .gitignore 排除）：`fb_monitor.state`、`fb_monitor.log`、`fb_monitor.pid`、`__pycache__/`。

## 关键文件

| 文件 | 目的 |
|------|------|
| `monitor_build.py` | 查询构建状态、失败自动修复、成功自动停止 |
| `fb.sh` | 面向用户的启停与状态查询入口 |
| `fb_daemon.sh` | 后台循环执行监控检查 |

## 依赖

**本模块依赖**:
- GitHub REST API（查询工作流运行、触发 dispatch）
- `git credential fill` 或环境变量获取 Token

**依赖本模块的**:
- 无（独立运维工具）

## 行为逻辑

`monitor_build.py` 每次执行：
1. 检查状态文件 `fb_monitor.state` 是否存在（不存在则跳过）
2. 查询最新一次工作流运行结论
3. `success` -> 删除状态文件停止监控
4. `failure`/`cancelled`/`timed_out` -> 尝试本地构建，失败则尝试自动修复并提交推送
5. `in_progress` -> 等待下次检查
6. `no_runs` -> 调用 dispatch 触发一次构建

## 配置

| 配置项 | 当前值 |
|--------|--------|
| `REPO_OWNER` | `lookmeplus-l` |
| `REPO_NAME` | `HPC` |
| `WORKFLOW_FILE` | `build-apk.yml` |
| 监控间隔 | 900 秒（15 分钟） |

## 添加新文件

新增监控逻辑时在 `monitor_build.py` 中添加对应函数，并在 `main()` 的分支中调用。新增管理命令时在 `fb.sh` 的 case 分支中注册。
