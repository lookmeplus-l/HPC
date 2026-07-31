#!/bin/bash
# fb - GitHub Actions 构建监控启动脚本
# 每 15 分钟检查一次构建状态

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(dirname "$SCRIPT_DIR")"
STATE_FILE="$SCRIPT_DIR/fb_monitor.state"
LOG_FILE="$SCRIPT_DIR/fb_monitor.log"
MONITOR_SCRIPT="$SCRIPT_DIR/monitor_build.py"

# 设置 GitHub Token
export GITHUB_TOKEN="${MONKEYCODE_GITHUB_ACCESS_TOKEN}"

echo "GitHub Actions 构建监控 (任务名称：fb)"
echo "====================================="
echo "工作区：$WORKSPACE_DIR"
echo "日志文件：$LOG_FILE"
echo ""

start_monitor() {
    echo "启动监控任务..."
    touch "$STATE_FILE"
    echo "监控状态：活动"
    echo ""
    echo "监控频率：每 15 分钟"
    echo "监控内容：GitHub Actions 构建状态"
    echo "行为模式："
    echo "  - 构建失败：自动修复并推送"
    echo "  - 构建成功：自动停止监控"
    echo ""
    
    # 立即执行一次检查
    echo "执行首次检查..."
    python3 "$MONITOR_SCRIPT"
}

check_status() {
    if [ -f "$STATE_FILE" ]; then
        echo "监控状态：✓ 活动"
    else
        echo "监控状态：✗ 未运行"
    fi
    
    if [ -f "$LOG_FILE" ]; then
        echo ""
        echo "最近日志："
        tail -10 "$LOG_FILE"
    fi
}

stop_monitor() {
    echo "停止监控任务..."
    rm -f "$STATE_FILE"
    echo "监控已停止"
}

# 参数处理
case "${1:-start}" in
    start)
        if [ -f "$STATE_FILE" ]; then
            echo "警告：监控任务已在运行中"
            check_status
            exit 0
        fi
        start_monitor
        ;;
    status)
        check_status
        ;;
    stop)
        stop_monitor
        ;;
    run)
        # 手动执行一次（供 cron 调用）
        if [ ! -f "$STATE_FILE" ]; then
            echo "监控任务未激活，跳过执行"
            exit 0
        fi
        python3 "$MONITOR_SCRIPT"
        ;;
    *)
        echo "用法：$0 {start|status|stop|run}"
        echo ""
        echo "命令说明:"
        echo "  start  - 启动监控任务"
        echo "  status - 查看监控状态"
        echo "  stop   - 停止监控任务"
        echo "  run    - 手动执行一次检查（供 cron 使用）"
        exit 1
        ;;
esac
