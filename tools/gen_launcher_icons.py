# -*- coding: utf-8 -*-
"""
生成 HeartSync 启动图标（PNG 位图版本，供 API 24/25 等不支持自适应图标的设备使用）。

设计稿：青底 + 白色圆 + 青色心形（与 App 内启动页 Logo 完全一致）。
自适应图标（API 26+）由 res/drawable/ic_launcher_foreground.xml 负责，此脚本只产出 PNG。

用法： python gen_launcher_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

# ---------------------------------------------------------------- 心形路径
# 复用 res/drawable/ic_heart.xml 中的 24x24 视口心形轮廓
HEART_PATH = (
    "M12,21.4l-1.4,-1.3C5.4,15.4 2,12.3 2,8.5 2,5.4 4.4,3 7.5,3"
    "c1.7,0 3.4,0.8 4.5,2.1C13.1,3.8 14.8,3 16.5,3 19.6,3 22,5.4 22,8.5"
    "c0,3.8 -3.4,6.9 -8.6,11.5L12,21.4z"
)


def _tokenize(d):
    """把 SVG 路径串切成 [命令, 数值, 数值, ...] 的扁平列表。

    注意负号是数字的一部分（不能当分隔符丢掉），
    例如 'l-1.4,-1.3' 必须解析成 'l', -1.4, -1.3。
    """
    out, num = [], ""

    def flush():
        nonlocal num
        if num:
            out.append(float(num))
            num = ""

    for ch in d:
        if ch.isalpha():
            flush()
            out.append(ch)
        elif ch in "0123456789.":
            num += ch
        else:                      # 逗号、空格等分隔符
            flush()
        # '-' 交给下面的分支处理
        if ch == "-":
            # 负号：先结算前一个数字，再以负号开启新数字
            flush()
            num += "-"
    flush()
    return out


def _flatten_cubic(p0, p1, p2, p3, steps=24):
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        x = u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0]
        y = u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]
        pts.append((x, y))
    return pts


def heart_polygon():
    """把 SVG 路径解析成多边形点集（支持 M/L/l/C/c/Z）。"""
    toks = _tokenize(HEART_PATH)
    pts, cur, start, i = [], (0.0, 0.0), (0.0, 0.0), 0
    cmd = None
    while i < len(toks):
        if isinstance(toks[i], str):
            cmd = toks[i]
            i += 1
            if cmd in ("z", "Z"):
                pts.append(start)
                cur = start
                continue
        if cmd == "M":
            cur = (toks[i], toks[i + 1]); i += 2
            start = cur
            pts.append(cur)
            cmd = "L"          # 后续坐标对按 L 处理
        elif cmd == "L":
            cur = (toks[i], toks[i + 1]); i += 2
            pts.append(cur)
        elif cmd == "l":
            cur = (cur[0] + toks[i], cur[1] + toks[i + 1]); i += 2
            pts.append(cur)
        elif cmd == "C":
            p1 = (toks[i], toks[i + 1])
            p2 = (toks[i + 2], toks[i + 3])
            p3 = (toks[i + 4], toks[i + 5]); i += 6
            pts.extend(_flatten_cubic(cur, p1, p2, p3))
            cur = p3
        elif cmd == "c":
            p1 = (cur[0] + toks[i], cur[1] + toks[i + 1])
            p2 = (cur[0] + toks[i + 2], cur[1] + toks[i + 3])
            p3 = (cur[0] + toks[i + 4], cur[1] + toks[i + 5]); i += 6
            pts.extend(_flatten_cubic(cur, p1, p2, p3))
            cur = p3
        else:
            i += 1
    return pts


# ---------------------------------------------------------------- 绘制
TEAL = (47, 184, 166, 255)     # brand_primary  #2FB8A6
WHITE = (255, 255, 255, 255)


def render(size, round_bg=False, ss=4):
    """按目标像素尺寸绘制图标。ss 为超采样倍数（抗锯齿）。"""
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 背景：青色圆角方块（圆形图标则为正圆）
    if round_bg:
        d.ellipse([0, 0, S - 1, S - 1], fill=TEAL)
    else:
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=TEAL)

    # 白色圆
    r = S * 0.35
    cx = cy = S / 2
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)

    # 青色心形（24x24 视口 → 目标缩放）
    heart = heart_polygon()
    hx0 = min(p[0] for p in heart); hx1 = max(p[0] for p in heart)
    hy0 = min(p[1] for p in heart); hy1 = max(p[1] for p in heart)
    scale = (S * 0.30) / (hx1 - hx0)
    ox = cx - ((hx0 + hx1) / 2) * scale
    oy = cy - ((hy0 + hy1) / 2) * scale
    d.polygon([(x * scale + ox, y * scale + oy) for x, y in heart], fill=TEAL)

    return img.resize((size, size), Image.LANCZOS)


DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

if __name__ == "__main__":
    res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "app", "src", "main", "res")
    for folder, size in DENSITIES.items():
        out_dir = os.path.join(res, folder)
        os.makedirs(out_dir, exist_ok=True)
        render(size, round_bg=False).save(os.path.join(out_dir, "ic_launcher.png"))
        render(size, round_bg=True).save(os.path.join(out_dir, "ic_launcher_round.png"))
        print(f"{folder}: ic_launcher.png / ic_launcher_round.png  ({size}x{size})")
    print("done")
