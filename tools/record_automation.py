# -*- coding: utf-8 -*-
"""
HeartSync 演示录屏自动操作脚本（配合手机自带录屏使用）。

用法：
  python record_automation.py          # 执行完整 30 秒脚本
  python record_automation.py --check  # 只打印各步骤坐标，不执行

设计：
  - 手机上用系统自带录屏（用户手动点「开始录制」）
  - 本脚本通过 adb 精确坐标驱动 UI 操作
  - 遇到需要输入中文的环节，脚本会暂停等待用户手动输入
"""
import subprocess
import sys
import time

ADB = r'C:\Android\Sdk\platform-tools\adb.exe'
SERIAL = 'IB49D6M7OBYXB6ZL'

# 关键控件坐标（已通过 uiautomator 实测确认）
COORD = {
    'ai_card':      (389, 1423),   # 首页「AI 心晴助手」卡片
    'et_input':     (400, 2290),   # AI 页输入框（无键盘时）
    'btn_send':     (984, 2290),   # AI 页「发送」按钮
    'btn_back_ai':  (84, 204),     # AI 页左上返回
    'nav_home':     (108, 2322),   # 底部「首页」
    'nav_sleep':    (324, 2322),   # 底部「作息」
    'card_item_1':  (950, 500),    # 作息列表第一条（cardItem 空白区）
}


def sh(args, timeout=60):
    """执行 adb shell 命令。"""
    r = subprocess.run([ADB, '-s', SERIAL, 'shell'] + args,
                       capture_output=True, text=True, timeout=timeout)
    return r.stdout.strip()


def tap(x, y):
    sh(['input', 'tap', str(x), str(y)])
    print('  [tap] %s,%s' % (x, y))


def key(code):
    sh(['input', 'keyevent', code])


def swipe(x1, y1, x2, y2, ms=300):
    sh(['input', 'swipe', str(x1), str(y1), str(x2), str(y2), str(ms)])
    print('  [swipe] %s,%s -> %s,%s' % (x1, y1, x2, y2))


def current_activity():
    """返回前台 Activity 的类名，如 com.heartsync.app.activity.MainActivity。"""
    out = sh(['dumpsys', 'activity', 'activities'])
    for line in out.splitlines():
        if 'topResumedActivity' in line:
            # 形如: topResumedActivity=ActivityRecord{xxx u0 com.heartsync.app/.activity.MainActivity t1}
            seg = line.split('u0')[-1]
            name = seg.split(' ')[0].strip()
            if '/' in name:
                name = name.split('/', 1)[1]
            return name
    return ''


def wait_for(fragment, timeout=15):
    """等待前台 Activity 包含指定片段。"""
    end = time.time() + timeout
    while time.time() < end:
        if fragment in current_activity():
            return True
        time.sleep(0.6)
    return False


def shot(name):
    """抓一帧用于校验（不参与成片）。"""
    out = r'C:\Users\西北孤傲的狼\Desktop\学校课程\Android移动开发\大作业\output\_rec_%s.png' % name
    with open(out, 'wb') as f:
        subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'],
                       stdout=f, stderr=subprocess.DEVNULL)
    return out


def prepare():
    """把 App 归位到首页。"""
    print('>> 归位到首页')
    sh(['am', 'force-stop', 'com.heartsync.app'])
    time.sleep(1.5)
    sh(['monkey', '-p', 'com.heartsync.app', '-c', 'android.intent.category.LAUNCHER', '1'])
    time.sleep(7)
    act = current_activity()
    print('   当前:', act)
    return 'MainActivity' in act


def run():
    print('=' * 60)
    print(' HeartSync 演示录屏自动操作 · 30 秒')
    print('=' * 60)

    # ---------- 0. 前置准备 ----------
    if not prepare():
        print('!! 未能进入首页，终止')
        return
    shot('00_home')

    print('\n>> [0-3s] 首页静止展示（健康指数环形图）')
    time.sleep(3)

    print('\n>> [3-6s] 点击「AI 心晴助手」卡片')
    tap(*COORD['ai_card'])
    time.sleep(3)
    if not wait_for('AiChatActivity', 10):
        print('!! 未进入 AI 页')
        return
    shot('01_ai_enter')

    print('\n>> [6-14s] 等待你在手机上手动输入问题并发送')
    print('   ⏳ 请现在在手机输入框输入问题，点击「发送」')
    # 等待用户发出消息（检测气泡数增加）
    sent = False
    end = time.time() + 60
    while time.time() < end:
        if has_user_bubble():
            sent = True
            break
        time.sleep(1)
    if not sent:
        print('!! 未检测到发送，继续后续步骤')
    else:
        print('   ✅ 已检测到发送')
    time.sleep(2)

    print('\n>> [14-22s] AI 逐字输出动画（等待回复完成）')
    time.sleep(8)
    shot('02_ai_reply')

    print('\n>> [22-24s] 返回首页')
    tap(*COORD['btn_back_ai'])
    time.sleep(2)

    print('\n>> [24-27s] 首页上滑，露出快捷功能区')
    swipe(540, 1800, 540, 900, 400)
    time.sleep(3)
    shot('03_home_scroll')

    print('\n>> [27-29s] 点底部「作息」')
    tap(*COORD['nav_sleep'])
    time.sleep(2)
    shot('04_sleep_list')

    print('\n>> [29-32s] 点列表第一条 → 睡眠详情页')
    tap(*COORD['card_item_1'])
    time.sleep(3)
    if wait_for('SleepDetailActivity', 8):
        print('   ✅ 已进入睡眠详情页')
    shot('05_sleep_detail')

    print('\n>> [32-36s] 停留展示 AI 作息分析')
    time.sleep(4)
    shot('06_ai_sleep')

    print('\n' + '=' * 60)
    print(' ✅ 脚本执行完毕')
    print('=' * 60)


def has_user_bubble():
    """检测聊天区是否出现了用户气泡（发送成功）。"""
    import re
    import io
    subprocess.run([ADB, '-s', SERIAL, 'shell', 'uiautomator', 'dump', '//sdcard/u.xml'],
                   capture_output=True)
    p = r'C:\Users\西北孤傲的狼\Desktop\学校课程\Android移动开发\大作业\output\_ui.xml'
    subprocess.run([ADB, '-s', SERIAL, 'pull', '//sdcard/u.xml', p],
                   capture_output=True)
    try:
        s = io.open(p, encoding='utf-8', errors='ignore').read()
    except Exception:
        return False
    # 统计 tvText 气泡；欢迎语是第 1 个，用户消息会带来第 2 个及以上
    n = len(re.findall(r'resource-id="[^"]*tvText"', s))
    return n >= 2


if __name__ == '__main__':
    if '--check' in sys.argv:
        for k, v in COORD.items():
            print('%-14s %s' % (k, v))
    else:
        run()
