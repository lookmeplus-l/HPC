#!/bin/bash
# 监控守护进程脚本
# 每 15 分钟执行一次监控检查，持续运行

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(dirname "$SCRIPT_DIR")"
STATE_FILE="$SCRIPT_DIR/fb_monitor.state"
LOG_FILE="$SCRIPT_DIR/fb_monitor.log"
PID_FILE="$SCRIPT_DIR/fb_monitor.pid"
INTERVAL=900  # 15 分钟（秒）

export GITHUB_TOKEN="${MONKEYCODE_GITHUB_ACCESS_TOKEN}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

check_and_run() {
    if [ ! -f "$STATE_FILE" ]; then
        log "监控任务已停止，退出守护进程"
        rm -f "$PID_FILE"
        exit 0
    fi
    
    log "执行定时检查..."
    python3 "$SCRIPT_DIR/monitor_build.py"
}

# 启动时检查
touch "$STATE_FILE"
echo $$ > "$PID_FILE"
log "监控守护进程启动 (PID: $$)"
log "监控间隔：${INTERVAL}秒"
log "按 Ctrl+C 或运行 'fb.sh stop' 停止监控"

while true; do
    check_and_run
    sleep $INTERVAL
done
