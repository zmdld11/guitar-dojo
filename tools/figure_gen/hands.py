"""可复用的手部线稿部件。

手指一律由「圆头胶囊 + 固定数量」拼出，从根上避免六指/粘连。
视角约定：
- hand_top：俯视，琴颈水平、nut 在左，四指从下方向上包住指板。
- hand_side_neck：侧视，琴颈朝左，手掌在右，拇指扣在琴颈背面。
- hand_pick：右手持拨片，手臂在上，拨片尖朝下。
"""

from __future__ import annotations

import math

from .lineart import Canvas, INK, MID, SOFT, WHITE


def _bend(a, b, amount: float):
    mx, my = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
    dx, dy = b[0] - a[0], b[1] - a[1]
    L = math.hypot(dx, dy) or 1
    return (mx - dy / L * amount, my + dx / L * amount)


def hand_top(c: Canvas, xs, base_y: float, tips, palm_bottom: float | None = None,
             ghost=frozenset(), lifted=frozenset(), width_scale: float = 1.0,
             wrist: bool = True, creases: bool = True):
    """俯视手：xs 为食指→小指的 x，tips 为四指尖端 y。

    ghost：浅色残影（保留指）；lifted：灰线、指尖离开琴弦。
    """
    widths = [33, 35, 33, 28]
    lean = [-2, 0, 2, 5]
    palm_bottom = palm_bottom if palm_bottom is not None else base_y + 132
    midx = (xs[0] + xs[3]) / 2

    # 前臂：先画，掌部盖住接缝；比手腕宽，避免看起来像一根手指
    if wrist:
        c.capsule([(midx - 2, palm_bottom - 24), (midx - 10, palm_bottom + 150)],
                  118 * width_scale, 6.2, WHITE, INK)

    for i in range(4):
        x = xs[i]
        tip = (x + lean[i] * width_scale, tips[i])
        base = (x + lean[i] * 2.4 * width_scale, base_y + 8)
        mid = _bend(base, tip, 7 if i % 2 == 0 else -6)
        w = widths[i] * width_scale
        if i in ghost:
            c.ghost_capsule([base, mid, tip], w, 4.5, SOFT)
        elif i in lifted:
            c.capsule([base, mid, tip], w, 5.4, WHITE, MID)
        else:
            c.capsule([base, mid, tip], w, 6.2 * width_scale, WHITE, INK)
        if creases and i not in ghost:
            d = math.hypot(tip[0] - mid[0], tip[1] - mid[1]) or 1
            ux, uy = (tip[0] - mid[0]) / d, (tip[1] - mid[1]) / d
            px, py = -uy, ux
            kx, ky = tip[0] - ux * w * 0.40, tip[1] - uy * w * 0.40
            c.stroke([(kx + px * w * 0.28, ky + py * w * 0.28),
                      (kx - px * w * 0.28, ky - py * w * 0.28)],
                     3.2, SOFT if i in lifted else MID)

    left, right = xs[0] - 30 * width_scale, xs[3] + 30 * width_scale
    top = base_y + 10
    span = palm_bottom - top
    palm = [
        (left, top + 8),
        (xs[0] - 4, top - 4),
        ((xs[0] + xs[1]) / 2, top - 6),
        (xs[2], top - 4),
        (right, top + 8),
        (right + 6, top + span * 0.42),
        (right - 16, palm_bottom - 14),
        (midx + 22, palm_bottom + 4),
        (midx - 24, palm_bottom + 4),
        (left + 12, palm_bottom - 16),
        (left - 6, top + span * 0.44),
    ]
    c.blob(palm, outline=6.2 * width_scale, fill=WHITE, color=INK)

    if creases:
        c.stroke([(midx - 24 * width_scale, base_y + 42),
                  (midx + 6 * width_scale, base_y + 52),
                  (midx + 30 * width_scale, base_y + 38)], 3.2, SOFT)


def hand_cross_section(c: Canvas, center, neck_w: float = 300, neck_h: float = 118,
                       scale: float = 1.0):
    """从琴头方向看的横截面：指板朝上，四指从上方压弦，拇指从下方扣住琴颈。

    手用一个 C 形包住琴颈右端：手掌在右，四指在指板上方，拇指在背面。
    """
    cx, cy = center
    s = scale
    top = cy - neck_h / 2
    bottom = cy + neck_h / 2
    palm_x = cx + neck_w * 0.56
    palm_top = cy - neck_h * 1.05
    palm_bottom = cy + neck_h * 1.05

    # 琴颈横截面
    c.rounded_rect((cx - neck_w / 2, top, cx + neck_w / 2, bottom),
                   radius=neck_h * 0.42, width=6, fill=WHITE, color=INK)
    c.line((cx - neck_w / 2, top), (cx + neck_w / 2, top), 7, INK)
    for k in range(6):
        c.solid_circle((cx - neck_w * 0.38 + k * neck_w * 0.152, top - 2), 3.6, MID)

    # 四指：从手掌上端扇出，指尖压在指板上
    tip_xs = [cx - neck_w * 0.36 + i * neck_w * 0.235 for i in range(4)]
    for i, tx in enumerate(tip_xs):
        base = (palm_x - 26 * s, palm_top + 22 * s + i * 8 * s)
        mid = (tx + 92 * s, top - 96 * s - i * 6 * s)
        c.capsule([base, mid, (tx, top - 10 * s)], 31 * s, 5.6 * s, WHITE, INK)
        c.circle((tx, top - 10 * s), 18 * s, outline=4.8 * s, fill=WHITE)

    # 拇指：从手掌下端绕到琴颈背面
    thumb_tip = (cx + neck_w * 0.16, bottom - 6 * s)
    c.capsule([(palm_x - 24 * s, palm_bottom - 34 * s),
               (cx + neck_w * 0.44, bottom + 14 * s), thumb_tip],
              42 * s, 5.6 * s, WHITE, INK)
    c.circle(thumb_tip, 21 * s, outline=4.8 * s, fill=WHITE)

    # 前臂先画，掌部压住接缝
    c.capsule([(palm_x - 10 * s, cy + 6 * s), (palm_x + 140 * s, cy + 128 * s)],
              112 * s, 6.2 * s, WHITE, INK)

    # 掌部
    c.blob([(palm_x - 60 * s, palm_top + 16 * s),
            (palm_x + 34 * s, palm_top - 10 * s),
            (palm_x + 74 * s, cy),
            (palm_x + 40 * s, palm_bottom - 4 * s),
            (palm_x - 56 * s, palm_bottom - 22 * s),
            (palm_x - 70 * s, cy + 24 * s)],
           outline=6.4 * s, fill=WHITE, color=INK)

    # 虎口空隙
    c.dashed([(cx + neck_w * 0.40, cy - 26 * s), (palm_x - 66 * s, cy - 18 * s)],
             3, SOFT, 8, 6)


def hand_side_neck(c: Canvas, neck_box, style: str = "correct", scale: float = 0.9,
                   index_tip_x: float | None = None):
    """侧视手型：neck_box 为横向琴颈（指板在上），琴颈朝左、手掌在右。

    style: correct | flat | wrist | thumb_over | straight
    """
    x0, y0, x1, y1 = neck_box
    h = y1 - y0
    w = x1 - x0
    s = scale

    # 琴颈
    c.rounded_rect((x0, y0, x1, y1), radius=h * 0.44, width=6, fill=WHITE, color=INK)
    c.line((x0, y0), (x1, y0), 8, INK)
    for i in range(1, 7):
        fx = x0 + w * (0.12 + i * 0.12)
        c.line((fx, y0 + 2), (fx, y0 + h * 0.24), 2.4, MID)
    for k in range(3):
        c.line((x0, y0 - 9 - k * 5), (x1, y0 - 9 - k * 5), 2.2, INK)

    px = x1 - 0.17 * w
    py = y1 + 1.05 * h
    if style == "wrist":
        py = y1 + 0.30 * h
    elif style == "flat":
        py = y1 + 0.70 * h
    tip = (index_tip_x if index_tip_x is not None else x0 + 0.36 * w, y0 - 0.10 * h)
    base = (px - 40 * s, py - 78 * s)

    # 后三指：浅色残影，指尖落在指板不同位置
    if style not in ("thumb_over", "flat"):
        for k in (3, 2, 1):
            gtip = (tip[0] + 34 * k * s, tip[1])
            gbase = (base[0] - 8 * k * s, base[1] + 14 * k * s)
            gmid = _bend(gbase, gtip, 34 * s)
            c.ghost_capsule([gbase, gmid, gtip], 30 * s, 4.5 * s, SOFT)

    # 前臂
    if style == "wrist":
        c.capsule([(px + 10 * s, py + 50 * s), (px + 120 * s, py + 150 * s)],
                  112 * s, 6.2 * s, WHITE, INK)
    else:
        c.capsule([(px + 20 * s, py + 50 * s), (px + 120 * s, py + 132 * s)],
                  112 * s, 6.2 * s, WHITE, INK)

    # 掌部
    palm = [(px - 66 * s, py - 82 * s), (px + 54 * s, py - 66 * s),
            (px + 76 * s, py + 20 * s), (px + 22 * s, py + 92 * s),
            (px - 64 * s, py + 74 * s), (px - 80 * s, py - 10 * s)]
    c.blob(palm, outline=6.4 * s, fill=WHITE, color=INK)

    # 食指
    if style == "flat":
        fbase = (px - 40 * s, py - 48 * s)
        ftip = (x0 + 0.30 * w, y0 - 0.05 * h)
        fmid = (px - 0.26 * w, y0 - 0.02 * h)
        c.capsule([fbase, fmid, ftip], 32 * s, 5.6 * s, WHITE, INK)
        c.circle(ftip, 17 * s, outline=5 * s, fill=WHITE)
        c.arrow((ftip[0] + 30 * s, y0 - 60 * s), (ftip[0] + 30 * s, y0 - 18 * s),
                3.5 * s, MID, 12 * s)
    elif style == "straight":
        fbase = (px - 60 * s, py - 40 * s)
        ftip = (x0 + 0.30 * w, y0 - 0.04 * h)
        c.capsule([fbase, ftip], 32 * s, 5.6 * s, WHITE, INK)
        c.circle(ftip, 17 * s, outline=5 * s, fill=WHITE)
        c.arrow((ftip[0] + 18 * s, y0 - 64 * s), (ftip[0] + 18 * s, y0 - 22 * s),
                3.5 * s, MID, 12 * s)
    else:
        fmid = _bend(base, tip, (30 if style != "thumb_over" else 16) * s)
        c.capsule([base, fmid, tip], 33 * s, 5.8 * s, WHITE, INK)
        c.circle(tip, 18 * s, outline=5 * s, fill=WHITE)

    # 拇指
    if style == "thumb_over":
        ttip = (x0 + 0.52 * w, y0 - 0.52 * h)
        c.capsule([(px - 30 * s, py - 40 * s), (px - 100 * s, y0 - 30 * s), ttip],
                  42 * s, 5.8 * s, WHITE, INK)
    else:
        ttip = (x0 + 0.52 * w, y1 + 0.18 * h)
        c.capsule([(px - 46 * s, py + 16 * s), (px - 110 * s, y1 + 0.52 * h), ttip],
                  44 * s, 5.8 * s, WHITE, INK)

    # 姿态提示
    if style == "wrist":
        c.stroke([(px - 78 * s, py + 34 * s), (px - 104 * s, py + 78 * s),
                  (px - 74 * s, py + 116 * s)], 3.4, MID)
        c.arrow((px - 74 * s, py + 116 * s), (px - 50 * s, py + 130 * s), 3, MID, 10 * s)
    elif style == "correct":
        c.dashed([(px - 118 * s, y1 + 40 * s), (px - 88 * s, y1 + 64 * s),
                  (px - 58 * s, y1 + 66 * s)], 3.2, SOFT, 9, 7)


def hand_pick(c: Canvas, wrist_c, tilt: float = 0.0, scale: float = 1.0,
              show_pick: bool = True, pick_y: float = 190.0):
    """右手持拨片（手背侧图示）：短腕、握拳、拇指与食指捏住拨片，尖端朝下。"""
    wx, wy = wrist_c
    ca, sa = math.cos(tilt), math.sin(tilt)

    def T(p):
        return (wx + (p[0] * ca - p[1] * sa) * scale,
                wy + (p[0] * sa + p[1] * ca) * scale)

    # 手腕短袖口
    c.capsule([T((0, -146)), T((0, -82))], 138 * scale, 6.2 * scale, WHITE, INK)
    # 手掌
    palm = [T(p) for p in [
        (-78, -92), (78, -92), (96, 0), (64, 78), (-64, 78), (-96, 0),
    ]]
    c.blob(palm, outline=6.4 * scale, fill=WHITE, color=INK)
    # 蜷起的中指、无名指、小指
    for x, y, r in [(34, 66, 24), (66, 44, 21)]:
        c.circle(T((x, y)), r * scale, outline=5, fill=WHITE)
    c.circle(T((0, 62)), 24 * scale, outline=5, fill=WHITE)
    # 食指
    c.capsule([T((54, 26)), T((34, 104)), T((8, 148))], 31 * scale, 5.8 * scale, WHITE, INK)
    c.circle(T((6, 154)), 17 * scale, outline=4.6, fill=WHITE)
    # 拇指
    c.capsule([T((-54, 30)), T((-36, 104)), T((-9, 148))], 36 * scale, 5.8 * scale, WHITE, INK)
    c.circle(T((-10, 154)), 19 * scale, outline=4.6, fill=WHITE)

    if show_pick:
        px, py = T((0, pick_y))
        sz = 76 * scale
        pts = [(px - sz * 0.54, py - sz * 0.28),
               (px + sz * 0.54, py - sz * 0.28),
               (px, py + sz * 0.50)]
        c.d.polygon(c._p(pts), fill=INK)
        c.stroke(pts + [pts[0]], sz * 0.24, INK)
