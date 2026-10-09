#!/usr/bin/env bash
# 静默启动后端 (Spring Boot, :8088) 和前端 (Vite, :5173) 到后台
# 日志: logs/backend.log, logs/frontend.log
# PID:  logs/backend.pid, logs/frontend.pid
# 停止: kill $(cat logs/backend.pid logs/frontend.pid)

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

is_alive() {
  local pidfile=$1
  [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile" 2>/dev/null)" 2>/dev/null
}

start_in_bg() {
  local name=$1 workdir=$2
  shift 2
  local pidfile="$LOG_DIR/$name.pid"
  local logfile="$LOG_DIR/$name.log"
  if is_alive "$pidfile"; then
    echo "[$name] 已在运行 (pid $(cat "$pidfile"))"
    return
  fi
  (
    cd "$workdir"
    nohup "$@" >"$logfile" 2>&1 &
    echo $! > "$pidfile"
  )
  echo "[$name] 已启动 pid=$(cat "$pidfile"), 日志: $logfile"
}

start_in_bg backend "$ROOT" mvn -pl agent-server spring-boot:run

if [[ -d "$ROOT/web/node_modules" ]]; then
  start_in_bg frontend "$ROOT/web" npm run dev
else
  echo "[frontend] 跳过：web/node_modules 不存在，请先 cd web && npm install"
fi

echo
echo "查看日志：tail -f logs/{backend,frontend}.log"
