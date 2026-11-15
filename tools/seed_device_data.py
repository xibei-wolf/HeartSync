# -*- coding: utf-8 -*-
"""
向真机/模拟器上的 HeartSync 本地库注入一周演示数据（睡眠/运动/情绪），用于录屏与验收截图。

数据设计目标（对照 util/HealthScore.java 的评分规则）：
  睡眠维度 40 分：平均时长落在 420~480 分钟 → 时长满分 20；平均质量 4.0 → 质量 16
  运动维度 30 分：近 7 条累计 150 分钟 → 满分 30
  情绪维度 30 分：平均 3.5 分 → 21
  合计 ≈ 87，落在首页环形图"状态很棒"区间

用法：
  python seed_device_data.py <拉取的heartsync.db>
"""
import sqlite3
import sys
import time
from datetime import date, timedelta

DB = sys.argv[1] if len(sys.argv) > 1 else 'heartsync.db'
con = sqlite3.connect(DB)
cur = con.cursor()

# 清理旧数据，保证可重复执行
for t in ('t_sleep_record', 't_exercise_record', 't_mood_record'):
    cur.execute('DELETE FROM %s' % t)

now = int(time.time() * 1000)
today = date.today()
# 最近 7 天（含今天），列表按日期升序
days = [(today - timedelta(days=i)).isoformat() for i in range(6, -1, -1)]

# ---------- 睡眠记录 ----------
# date, start, wake, minutes, deep, quality, note
sleep = [
    (days[0], '23:30', '07:00', 450, 72, 4, '宿舍有点吵'),
    (days[1], '00:20', '07:10', 410, 58, 3, '熬夜赶实验报告'),
    (days[2], '23:10', '06:50', 460, 85, 4, '睡前跑步，睡眠质量不错'),
    (days[3], '01:05', '08:00', 415, 46, 3, '室友打游戏到很晚'),
    (days[4], '23:40', '06:40', 430, 80, 4, '睡前不看手机了'),
    (days[5], '23:20', '07:00', 460, 90, 5, '状态最好的一天'),
    (days[6], '23:00', '07:00', 470, 96, 5, '规律的作息让精力更充沛'),
]
for i, (d, s, w, dur, deep, q, note) in enumerate(sleep):
    cur.execute(
        'INSERT INTO t_sleep_record (date, sleep_start, wake_time, duration_minutes,'
        ' deep_minutes, quality, note, create_time) VALUES (?,?,?,?,?,?,?,?)',
        (d, s, w, dur, deep, q, note, now - i * 86400000))

# ---------- 运动记录 ----------
# date, type, minutes, intensity(1轻 2中 3高), calories, note
# 注意：最后一条固定为「今天」，这样首页「今日运动」卡片才有值
exercise = [
    (days[1], '跑步', 35, 2, 320, '操场 5 公里，配速 6\'30"'),
    (days[2], '瑜伽', 30, 1, 150, '宿舍拉伸，缓解久坐腰酸'),
    (days[3], '快走', 30, 2, 210, '校园里快走放松心情'),
    (days[4], '跳绳', 25, 3, 260, '跳绳 1500 个，效率很高'),
    (days[6], '骑行', 30, 2, 300, '环城骑行，风景很好'),
]
for i, (d, t, m, inten, cal, note) in enumerate(exercise):
    cur.execute(
        'INSERT INTO t_exercise_record (date, type, duration_minutes, intensity,'
        ' calories, note, create_time) VALUES (?,?,?,?,?,?,?)',
        (d, t, m, inten, cal, note, now - i * 86400000))

# ---------- 情绪记录 ----------
# date, score(1-5), tags, diary
# 最后一条固定为「今天」，这样首页「今日心情」卡片才有值
mood = [
    (days[1], 3, '平静,略疲惫', '白天课排得很满，晚上想早点睡。'),
    (days[2], 2, '焦虑,压力', '实验数据反复不理想，心里有点慌，担心明天的答辩。'),
    (days[3], 4, '开心,充实', '跑了步之后心情好了很多，和朋友聊了聊未来规划。'),
    (days[4], 3, '平静', '还行，普通的一天，按部就班地完成计划。'),
    (days[5], 4, '开心,放松', '晨跑完出了一身汗，早餐吃了喜欢的豆浆油条，心情很棒。'),
    (days[6], 5, '开心,自信,充实', '今天状态特别好，把拖了很久的项目收尾了，很有成就感。'),
]
for i, (d, s, tags, diary) in enumerate(mood):
    cur.execute(
        'INSERT INTO t_mood_record (date, score, tags, diary, ai_analysis, create_time)'
        ' VALUES (?,?,?,?,?,?)',
        (d, s, tags, diary, '', now - i * 86400000))

con.commit()

# ---------- 打印结果并预估健康指数 ----------
print('日期范围:', days[0], '~', days[6])
for t in ('t_sleep_record', 't_exercise_record', 't_mood_record'):
    print('  %-18s %d 条' % (t, cur.execute('SELECT count(*) FROM %s' % t).fetchone()[0]))

n = min(len(sleep), 7)
avg_min = sum(x[3] for x in sleep[:n]) / n
avg_q = sum(x[5] for x in sleep[:n]) / n
sleep_score = 20 + round((avg_q / 5) * 20) if 420 <= avg_min <= 480 else None
ex_min = sum(x[2] for x in exercise[:7])
ex_score = round(min(30, ex_min / 150 * 30))
m_avg = sum(x[1] for x in mood[:7]) / min(len(mood), 7)
mood_score = round(min(30, m_avg / 5 * 30))
print('\n健康指数预估:')
print('  睡眠 均时长 %.0f分 均质量 %.1f → %d 分' % (avg_min, avg_q, sleep_score or 0))
print('  运动 累计 %d 分 → %d 分' % (ex_min, ex_score))
print('  情绪 均分 %.2f → %d 分' % (m_avg, mood_score))
print('  合计 ≈ %d 分' % ((sleep_score or 0) + ex_score + mood_score))
con.close()
