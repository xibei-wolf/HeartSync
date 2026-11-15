#!/bin/sh
# 心晴校园 HeartSync 后端启动脚本（Linux / macOS）
# 零依赖，无需 npm install
cd "$(dirname "$0")" || exit 1

echo ""
echo "  正在启动 心晴校园 HeartSync 后端服务..."
echo "  (零依赖，无需 npm install；按 Ctrl+C 停止服务)"
echo ""

node server.js
