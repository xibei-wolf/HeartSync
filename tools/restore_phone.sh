#!/bin/bash
# ============================================================
#  HeartSync 真机环境还原脚本
#  用途：录屏结束后，把临时修改过的手机系统设置恢复原状
#  用法：插好数据线、手机上允许 USB 调试后，执行 bash restore_phone.sh
# ============================================================

ADB="C:/Android/Sdk/platform-tools/adb.exe"
export MSYS_NO_PATHCONV=1

echo "============================================"
echo "  HeartSync 真机环境还原"
echo "============================================"
echo

# ---------- 0. 检查设备 ----------
DEV=$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')
if [ -z "$DEV" ]; then
  echo "[X] 未检测到设备。请依次检查："
  echo "    1) 数据线是否插紧（必须是数据线，部分线只供电）"
  echo "    2) 手机下拉通知栏 → USB 选项是否切到「传输文件 / MTP」"
  echo "       （OPPO 在「仅充电」模式下 ADB 不一定可用）"
  echo "    3) 「开发者选项 → USB 调试」是否开启"
  echo "    4) 弹出的「允许 USB 调试」是否点允许"
  echo "    5) 换个 USB 接口或换根线试试"
  echo
  echo "  提示：以下设置如果不还原也不影响正常使用，只是手机会"
  echo "        停留在错误时间(09:41)且动画被关掉，录完可手动改回："
  echo "        设置 → 系统 → 日期和时间 → 打开「自动设置时间」"
  echo "        设置 → 开发者选项 → 动画缩放三项改回 1.0"
  exit 1
fi
echo "[OK] 已连接设备: $DEV"
echo

echo "[1/5] 恢复自动时间与自动时区..."
"$ADB" -s "$DEV" shell settings put global auto_time 1
"$ADB" -s "$DEV" shell settings put global auto_time_zone 1
sleep 3
AT=$("$ADB" -s "$DEV" shell settings get global auto_time | tr -d '\r')
TZ=$("$ADB" -s "$DEV" shell settings get global auto_time_zone | tr -d '\r')
if [ "$AT" = "1" ] && [ "$TZ" = "1" ]; then
  echo "     [OK] auto_time=1, auto_time_zone=1"
else
  echo "     [!] 设置未生效，请手动检查"
fi
echo "     当前时间: $("$ADB" -s "$DEV" shell date | tr -d '\r')"
echo

echo "[2/5] 恢复动画缩放（0x -> 1.0 系统默认）..."
for a in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" -s "$DEV" shell settings put global $a 1
done
for a in window_animation_scale transition_animation_scale animator_duration_scale; do
  echo "     $a = $("$ADB" -s "$DEV" shell settings get global $a | tr -d '\r')"
done
echo

echo "[3/5] 移除 USB 反向端口转发..."
"$ADB" -s "$DEV" reverse --remove-all 2>/dev/null
LEFT=$("$ADB" -s "$DEV" reverse --list 2>/dev/null | tr -d '\r')
if [ -z "$LEFT" ]; then
  echo "     [OK] 隧道已全部清除"
else
  echo "     [!] 仍有残留: $LEFT"
fi
echo

echo "[4/5] 停止应用（释放数据库文件锁）..."
"$ADB" -s "$DEV" shell am force-stop com.heartsync.app 2>/dev/null
echo "     [OK] com.heartsync.app 已停止"
echo

echo "[5/5] 检查应用内是否残留调试用配置..."
PREFS=$("$ADB" -s "$DEV" shell "run-as com.heartsync.app cat shared_prefs/heartsync_prefs.xml" 2>/dev/null | tr -d '\r')
if echo "$PREFS" | grep -q "127.0.0.1:8080"; then
  echo "     [!] 检测到 server_base = http://127.0.0.1:8080"
  echo "         这是为真机联调临时写入的。正式演示前建议改回"
  echo "         http://10.0.2.2:8080（模拟器）或局域网 IP（真机）。"
  echo "         改法：App → 我的 → 设置 → 服务器地址"
else
  echo "     [OK] 未发现临时的 127.0.0.1 配置"
fi
echo

echo "============================================"
echo "  还原完成"
echo "============================================"
echo
echo "  已还原：自动时间 / 动画缩放 / 端口转发 / 应用运行状态"
echo
echo "  另外请自行确认："
echo "    - 服务端是否需要关闭（关闭 node server.js 窗口即可）"
echo "    - 模拟器是否需要关闭"
echo "    - 电脑 C 盘空间（此前仅剩约 14G）"
echo
