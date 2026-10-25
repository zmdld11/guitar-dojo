"""插图清单实现。每个函数返回生成的 PNG 路径。

编号与 docs/画图提示词.md 一致。部分编号（c1–c5 / d1 / e1 / e3 / b3 / g1）
由 `sourced.py` 用 Openclipart 公有领域素材覆盖，见 `generate.py` 的 REGISTRY 合并。
"""

from __future__ import annotations

import math
from pathlib import Path

from .lineart import (
    Canvas, INK, MID, SOFT, HAIR, PALE, WHITE,
    neck_geometry, draw_neck_h, fret_center, chord_chart, pick_shape,
    star, eighth_note, sparkle, check, cross, staff, sweat_drop,
    catmull_rom,
)
from .hands import hand_top, hand_side_neck, hand_pick, hand_cross_section

PORTRAIT = (1024, 1365)
LANDSCAPE = (1365, 1024)


def _out(out: Path, name: str) -> Path:
    return out / name


CHORDS = {
    "C": {"mute": [6], "open": [3, 1], "dots": [(5, 3, 3), (4, 2, 2), (2, 1, 1)]},
    "G": {"open": [4, 3, 2], "dots": [(6, 3, 2), (5, 2, 1), (1, 3, 3)]},
    "D": {"mute": [6, 5], "open": [4], "dots": [(3, 2, 1), (2, 3, 3), (1, 2, 2)]},
    "Am": {"mute": [6], "open": [5, 1], "dots": [(4, 2, 2), (3, 2, 3), (2, 1, 1)]},
    "Em": {"open": [6, 3, 2, 1], "dots": [(5, 2, 2), (4, 2, 3)]},
    "E": {"open": [6, 2, 1], "dots": [(5, 2, 2), (4, 2, 3), (3, 1, 1)]},
    "A": {"mute": [6], "open": [5, 1], "barres": [(4, 2, 2, 1)]},
    "Dm": {"mute": [6, 5], "open": [4], "dots": [(3, 2, 2), (2, 3, 3), (1, 1, 1)]},
    "Fsimple": {"mute": [6, 5], "barres": [(2, 1, 1, 1)], "dots": [(4, 3, 3), (3, 2, 2)]},
    "Fbarre": {"barres": [(6, 1, 1, 1)], "dots": [(5, 3, 3), (4, 3, 4), (3, 2, 2)]},
}


# ────────────────────────────────────────────────────────────
# A 组：左手
# ────────────────────────────────────────────────────────────

def a1(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 658, 984))
    c.panel((707, 40, 1325, 984))

    # 左：俯视，四指立在指板上
    geo = neck_geometry((120, 400, 610, 528), nfrets=8)
    draw_neck_h(c, geo, width=2.8, inlays=False)
    xs = [fret_center(geo, i) for i in (1, 2, 3, 4)]
    string4 = geo["string_ys"][3]
    hand_top(c, xs, base_y=615, tips=[string4] * 4, palm_bottom=775, width_scale=0.94)
    for x in xs:
        c.solid_circle((x, string4), 8, INK)

    # 右：琴头方向横截面
    hand_cross_section(c, (1000, 540), neck_w=300, neck_h=118)
    return c.save(_out(out, "a1_left_hand_posture.png"))


def a2(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    cells = [
        (40, 40, 492, 650, "flat"),
        (532, 40, 984, 650, "wrist"),
        (40, 700, 492, 1310, "thumb_over"),
        (532, 700, 984, 1310, "straight"),
    ]
    for x0, y0, x1, y1, style in cells:
        c.panel((x0, y0, x1, y1), radius=30)
        neck = (x0 + 30, y0 + 210, x0 + 410, y0 + 280)
        hand_side_neck(c, neck, style=style, scale=0.80)
        cross(c, (x1 - 46, y0 + 46), 13, SOFT)
    return c.save(_out(out, "a2_left_hand_mistakes.png"))


def a3(out: Path) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    w, gap = 300, 19
    top, bottom = 70, 954
    for i in range(4):
        px = 48 + i * (w + gap)
        c.panel((px, top, px + w, bottom), radius=26)
        geo = neck_geometry((px + 18, 280, px + w - 18, 404), nfrets=6)
        draw_neck_h(c, geo, width=2.3, inlays=False)
        string = geo["string_ys"][2]
        xs = [fret_center(geo, f) for f in (1, 2, 3, 4)]
        tips = [string if f <= i else string + 24 for f in range(4)]
        lifted = frozenset(f for f in range(4) if f > i)
        hand_top(c, xs, base_y=485, tips=tips, palm_bottom=635,
                 lifted=lifted, width_scale=0.82, wrist=False)
        for f in range(i + 1):
            c.solid_circle((xs[f], string), 10, INK)
        # 进度点
        for k in range(4):
            cx = px + w / 2 + (k - 1.5) * 34
            if k <= i:
                c.solid_circle((cx, 800), 8, INK)
            else:
                c.circle((cx, 800), 8, outline=3, fill=WHITE, color=SOFT)
    for i in range(3):
        x = 48 + (i + 1) * (w + gap) - gap / 2
        c.arrow((x - 3, 760), (x + 3, 760), 2.5, MID, 11)
    return c.save(_out(out, "a3_chromatic_frames.png"))


def a4(out: Path) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    w, gap = 404, 30
    for i in range(3):
        px = 44 + i * (w + gap)
        c.panel((px, 70, px + w, 954), radius=26)
        geo = neck_geometry((px + 30, 350, px + w - 30, 490), nfrets=5)
        draw_neck_h(c, geo, width=2.5, inlays=False)
        string = geo["string_ys"][3]
        xs = [fret_center(geo, f) for f in (1, 2, 3, 4)]
        active = i
        tips = [string if f <= active else string + 30 for f in range(4)]
        ghost = frozenset(range(active))
        lifted = frozenset(f for f in range(4) if f > active)
        hand_top(c, xs, base_y=600, tips=tips, palm_bottom=760,
                 ghost=ghost, lifted=lifted)
        for f in range(active + 1):
            x = xs[f]
            if f < active:
                c.circle((x, string), 15, outline=4, fill=WHITE)
                c.digit(f + 1, (x, string), 16, INK)
                c.dashed([(x, string - 26), (x, string - 14)], 3, MID, 6, 5)
            else:
                c.solid_circle((x, string), 15, INK)
                c.digit(f + 1, (x, string), 16, WHITE)
    return c.save(_out(out, "a4_anchor_finger.png"))


def a5(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    c.panel((60, 60, 964, 940), radius=34)
    geo = neck_geometry((90, 300, 934, 660), nfrets=3)
    draw_neck_h(c, geo, width=4, inlays=False)
    string = geo["string_ys"][2]
    fret2 = geo["fret_xs"][1]
    fx = fret2 - 80
    # 从下方伸入的按弦指（只画到指根，避免巨大手指）
    c.capsule([(fx - 18, 930), (fx - 8, 720), (fx, string + 24)], 72, 6.5, WHITE, INK)
    c.circle((fx, string + 8), 36, outline=6.5, fill=WHITE)
    c.solid_circle((fx, string), 7, INK)
    # 角度：指尖与指板垂直
    c.dashed([(fx, 220), (fx, 760)], 4, MID, 14, 9)
    c.dashed([(fx - 200, string), (fx + 220, string)], 4, MID, 14, 9)
    c.stroke([(fx + 24, string - 28), (fx + 54, string - 28), (fx + 54, string)], 3, SOFT)
    # 力度：三支向下箭头
    for k in (-1, 0, 1):
        x = fx + k * 34
        c.arrow((x, 210), (x, 280), 4.5, MID, 15)
    # 落点：靠近品丝
    c.stroke([(fx + 52, string + 86), (fret2 - 30, string + 86)], 4, MID)
    c.arrow((fret2 - 30, string + 86), (fret2 - 6, string + 52), 4, MID, 15)
    # 小图：侧视指尖压弦
    c.panel((600, 730, 934, 916), radius=22)
    c.line((622, 850), (912, 850), 5, INK)
    c.capsule([(800, 900), (772, 790), (730, 850)], 40, 5, WHITE, INK)
    c.circle((730, 850), 20, outline=4, fill=WHITE)
    c.dashed([(730, 760), (730, 880)], 3, MID, 8, 6)
    c.stroke([(750, 880), (750, 850), (730, 850)], 2.5, SOFT)
    return c.save(_out(out, "a5_fretting_basics.png"))

# ────────────────────────────────────────────────────────────
# B 组：右手
# ────────────────────────────────────────────────────────────

def b1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    c.panel((60, 60, 964, 1160), radius=34)
    hand_pick(c, (500, 570), tilt=-0.05, scale=1.55)
    # 正面视角小图
    c.panel((600, 1150, 964, 1320), radius=22)
    hx, hy = 790, 1240
    c.circle((hx - 34, hy - 26), 24, outline=4.5, fill=WHITE)
    c.circle((hx + 34, hy - 26), 24, outline=4.5, fill=WHITE)
    c.capsule([(hx - 66, hy - 70), (hx - 34, hy - 30)], 22, 4.5)
    c.capsule([(hx + 66, hy - 70), (hx + 34, hy - 30)], 22, 4.5)
    pick_shape(c, (hx, hy + 20), 58)
    return c.save(_out(out, "b1_pick_grip.png"))


def b2(out: Path) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    w, gap = 404, 30
    for i in range(3):
        px = 44 + i * (w + gap)
        c.panel((px, 70, px + w, 954), radius=26)
        for k in range(3):
            y = 620 + k * 18
            c.line((px + 46, y), (px + w - 46, y), 4, INK)
        cx = px + w / 2
        # 手腕摆动的 S 形双向箭头
        c.stroke([(cx + 104, 380), (cx + 122, 440), (cx + 104, 500)], 4, SOFT)
        c.arrow((cx + 104, 500), (cx + 98, 540), 4, MID, 12)
        c.arrow((cx + 104, 380), (cx + 98, 340), 4, MID, 12)

        if i == 0:
            hand_pick(c, (cx + 10, 430), tilt=-0.04, scale=0.62)
            c.arrow((cx - 4, 566), (cx - 4, 604), 5, INK, 18)
        elif i == 1:
            hand_pick(c, (cx + 10, 560), tilt=-0.04, scale=0.62)
            c.stroke([(cx - 96, 590), (cx - 70, 660), (cx, 690),
                      (cx + 70, 660), (cx + 92, 592)], 4.5, SOFT)
            c.arrow((cx + 94, 600), (cx + 84, 660), 4, MID, 13)
        else:
            hand_pick(c, (cx + 10, 430), tilt=-0.04, scale=0.62)
            c.arrow((cx - 4, 604), (cx - 4, 560), 5, INK, 18)
    return c.save(_out(out, "b2_alternate_picking.png"))


def b3(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((60, 60, 1305, 964), radius=34)
    # 琴身局部
    c.blob([(520, 190), (980, 150), (1230, 330), (1290, 700),
            (1080, 920), (620, 940), (470, 720), (450, 430)],
           outline=7, fill=WHITE, color=INK)
    # 琴弦与琴桥
    for k in range(6):
        y = 400 + k * 18
        c.line((110, y), (1170, y), 2.6, INK)
    c.rounded_rect((1040, 372, 1066, 490), radius=8, width=5, fill=WHITE, color=INK)
    # 两个拾音器
    for x in (760, 880):
        c.rounded_rect((x, 372, x + 26, 490), radius=10, width=5, fill=WHITE, color=INK)
    # 手：手臂在上，手掌压在琴弦上
    hand_pick(c, (1160, 300), tilt=math.radians(8), scale=0.95)
    # 接触面：掌心下的排线
    c.hatch([(1080, 470), (1215, 470), (1215, 560), (1080, 560)],
            spacing=11, angle=-45, width=2.4, color=SOFT)
    c.dashed([(1070, 566), (1220, 566)], 4, SOFT, 12, 8)
    for k in range(2):
        c.arrow((1100 + k * 60, 640), (1112 + k * 60, 588), 4, MID, 13)
    return c.save(_out(out, "b3_right_hand_rest.png"))


# ────────────────────────────────────────────────────────────
# C 组：和弦
# ────────────────────────────────────────────────────────────

def _chord_page(out_name: str, out: Path, left: str, right: str) -> Path:
    c = Canvas(*PORTRAIT)
    c.panel((40, 40, 492, 1325), radius=30)
    c.panel((532, 40, 984, 1325), radius=30)
    chord_chart(c, (150, 400, 390, 960), frets=5, spec=CHORDS[left], dot_r=17)
    chord_chart(c, (642, 400, 882, 960), frets=5, spec=CHORDS[right], dot_r=17)
    return c.save(_out(out, out_name))


def c1(out: Path) -> Path:
    return _chord_page("c1_c_g.png", out, "C", "G")


def c2(out: Path) -> Path:
    return _chord_page("c2_d_am.png", out, "D", "Am")


def c3(out: Path) -> Path:
    return _chord_page("c3_em_e.png", out, "Em", "E")


def c4(out: Path) -> Path:
    return _chord_page("c4_a_dm.png", out, "A", "Dm")


def c5(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    c.panel((40, 40, 492, 1325), radius=30)
    c.panel((532, 40, 984, 1325), radius=30)
    chord_chart(c, (150, 400, 390, 960), frets=5, spec=CHORDS["Fbarre"], dot_r=17)
    chord_chart(c, (642, 400, 882, 960), frets=5, spec=CHORDS["Fsimple"], dot_r=17)
    sweat_drop(c, (408, 326), 20)
    # 右侧：被避开的低音弦用淡色短竖线标出
    for s in (6, 5):
        x = 672 + (6 - s) * 36
        c.dashed([(x, 352), (x, 388)], 3, PALE, 8, 6)
    return c.save(_out(out, "c5_f_simplified.png"))


def _spec_points(geo, spec):
    """把和弦 spec 展开成 (x, y, finger) 点，含横按。"""
    x0 = geo["xs"][0]
    gap = geo["xs"][1] - geo["xs"][0]
    pts = []
    for s, f, finger in spec.get("dots", []):
        pts.append((geo["xs"][6 - s], geo["y0"] + (f - 0.5) * geo["fret_gap"], finger))
    for s_from, s_to, f, finger in spec.get("barres", []):
        lo, hi = min(s_from, s_to), max(s_from, s_to)
        for s in range(lo, hi + 1):
            pts.append((geo["xs"][6 - s], geo["y0"] + (f - 0.5) * geo["fret_gap"], finger))
    return pts


def _switch_page(out_name: str, out: Path, a: str, b: str) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    w, gap = 404, 30
    for i in range(3):
        px = 44 + i * (w + gap)
        c.panel((px, 70, px + w, 954), radius=26)
        box = (px + 70, 300, px + w - 70, 780)
        if i == 0:
            chord_chart(c, box, frets=4, spec=CHORDS[a], dot_r=15)
        elif i == 2:
            chord_chart(c, box, frets=4, spec=CHORDS[b], dot_r=15)
        else:
            geo = chord_chart(c, box, frets=4, spec=None, dot_r=15)
            src = _spec_points(geo, CHORDS[a])
            dst = _spec_points(geo, CHORDS[b])
            for x, y, finger in src:
                c.circle((x, y), 15, outline=4, fill=WHITE, color=MID)
                c.digit(finger, (x, y), 14, MID)
            for x, y, finger in dst:
                for k in range(8):
                    a0 = math.radians(k * 45 + 12)
                    a1 = math.radians(k * 45 + 33)
                    c.stroke([(x + math.cos(a0) * 13, y + math.sin(a0) * 13),
                              (x + math.cos(a1) * 13, y + math.sin(a1) * 13)], 3, SOFT)
                c.solid_circle((x, y), 3, SOFT)
            # 移动路径：同一手指配对，连接最近的三个
            used = set()
            for sx, sy, sf in src:
                candidates = [(j, (dx - sx) ** 2 + (dy - sy) ** 2)
                              for j, (dx, dy, df) in enumerate(dst) if j not in used]
                if not candidates:
                    continue
                j, _ = min(candidates, key=lambda t: t[1])
                used.add(j)
                dx, dy, _ = dst[j]
                mx, my = (sx + dx) / 2, min(sy, dy) - 42
                c.dashed([(sx, sy), (mx, my), (dx, dy)], 4, MID, 10, 7)
                c.arrow((mx + (dx - mx) * 0.72, my + (dy - my) * 0.72), (dx, dy),
                        3.4, MID, 11)
    return c.save(_out(out, out_name))


def c6(out: Path) -> Path:
    return _switch_page("c6_switch_c_to_g.png", out, "C", "G")


def c7(out: Path) -> Path:
    return _switch_page("c7_switch_em_to_am.png", out, "Em", "Am")


def c8(out: Path) -> Path:
    return _switch_page("c8_switch_d_to_a.png", out, "D", "A")


# ────────────────────────────────────────────────────────────
# D 组：设备
# ────────────────────────────────────────────────────────────

def _guitar(c: Canvas, cx: float, body_top: float, scale: float = 1.0,
            blank_knobs: bool = False, switch: str = "mid",
            highlight: str | None = None, callouts=None, strings: bool = True):
    s = scale

    def P(x, y):
        return (cx + x * s, body_top + y * s)

    body_pts = [
        (0, 4),
        (54, 8), (82, 20), (94, 46), (86, 78), (72, 94),
        (104, 120), (128, 158), (136, 196),
        (130, 224), (114, 244),
        (124, 268), (146, 312), (158, 356),
        (150, 396), (122, 424), (78, 442), (0, 448),
        (-78, 442), (-122, 424), (-150, 396),
        (-158, 356), (-146, 312), (-124, 268),
        (-114, 244), (-130, 224),
        (-136, 196), (-128, 158), (-104, 120),
        (-72, 94), (-86, 78), (-94, 46), (-82, 20), (-54, 8),
    ]
    c.blob([P(*p) for p in body_pts], outline=7, fill=WHITE, color=INK)

    # 琴颈
    nw = 28
    c.rounded_rect((cx - nw * s, body_top - 250 * s, cx + nw * s, body_top - 2 * s),
                   radius=4, width=5, fill=WHITE, color=INK)
    for i in range(1, 8):
        y = body_top - 250 * s + i * 30 * s
        if y < body_top - 8 * s:
            c.line((cx - nw * s, y), (cx + nw * s, y), 2.4, INK)
    c.line((cx - nw * s, body_top - 250 * s), (cx + nw * s, body_top - 250 * s), 9, INK)

    # 琴头
    head = [P(*p) for p in [
        (0, -404), (46, -396), (58, -356), (54, -300), (40, -262),
        (0, -252), (-40, -262), (-54, -300), (-58, -356), (-46, -396),
    ]]
    c.blob(head, outline=6, fill=WHITE, color=INK)
    for side in (-1, 1):
        for k in range(3):
            c.circle((cx + side * 52 * s, body_top - (370 - k * 34) * s),
                     8.5 * s, outline=4, fill=WHITE)

    # 拾音器
    for y0 in (196, 268):
        c.rounded_rect((cx - 66 * s, body_top + y0 * s,
                        cx + 66 * s, body_top + (y0 + 36) * s),
                       radius=13 * s, width=5, fill=WHITE, color=INK)
        lit = (highlight == "neck" and y0 == 196) or \
              (highlight == "bridge" and y0 == 268) or highlight == "both"
        if lit:
            c.rounded_rect((cx - 58 * s, body_top + (y0 + 6) * s,
                            cx + 58 * s, body_top + (y0 + 30) * s),
                           radius=9 * s, width=0, fill=INK, color=INK)
        else:
            for k in range(6):
                c.solid_circle((cx + (-44 + k * 17.6) * s,
                                body_top + (y0 + 18) * s), 2.4 * s, MID)

    # 琴桥 / 拉弦板
    c.rounded_rect((cx - 58 * s, body_top + 306 * s, cx + 58 * s, body_top + 322 * s),
                   radius=6 * s, width=5, fill=WHITE, color=INK)
    c.rounded_rect((cx - 44 * s, body_top + 336 * s, cx + 44 * s, body_top + 352 * s),
                   radius=5 * s, width=5, fill=WHITE, color=INK)

    if strings:
        for k in range(6):
            x = cx + (-44 + k * 17.6) * s
            c.line((x, body_top - 246 * s), (x, body_top + 306 * s), 2.2, MID)

    # 拨杆
    sx, sy = cx + 82 * s, body_top + 138 * s
    c.circle((sx, sy), 12 * s, outline=4.5, fill=WHITE)
    if switch == "neck":
        c.line((sx, sy - 26 * s), (sx, sy + 4 * s), 8 * s, INK)
    elif switch == "bridge":
        c.line((sx, sy - 4 * s), (sx, sy + 26 * s), 8 * s, INK)
    else:
        c.line((sx - 24 * s, sy), (sx + 24 * s, sy), 8 * s, INK)
    c.solid_circle((sx, sy), 4.5 * s, INK)

    # 四个旋钮（音量实心点，音色空心）
    knob_pos = [(88, 286), (118, 320), (58, 326), (88, 360)]
    for k, (kx, ky) in enumerate(knob_pos):
        kx, ky = cx + kx * s, body_top + ky * s
        c.circle((kx, ky), 14 * s, outline=4.5, fill=WHITE)
        for t in range(4):
            a = math.radians(t * 90 + 45)
            c.line((kx + math.cos(a) * 18 * s, ky + math.sin(a) * 18 * s),
                   (kx + math.cos(a) * 22 * s, ky + math.sin(a) * 22 * s), 2, SOFT)
        if blank_knobs:
            c.line((kx, ky), (kx, ky - 18 * s), 4, MID)
        else:
            c.line((kx, ky), (kx + math.cos(math.radians(-60)) * 12 * s,
                              ky + math.sin(math.radians(-60)) * 12 * s), 4, INK)
        if k in (0, 3):
            c.solid_circle((kx, ky), 3.2 * s, INK)
        else:
            c.circle((kx, ky), 4.2 * s, outline=2.2, fill=WHITE)

    if callouts:
        for start, end in callouts:
            c.line(start, end, 3.2, MID)
            c.solid_circle(start, 5, INK)
            ex, ey = end
            if ex < start[0]:
                c.rounded_rect((ex - 100, ey - 18, ex, ey + 18), radius=9, width=3, color=HAIR)
            else:
                c.rounded_rect((ex, ey - 18, ex + 100, ey + 18), radius=9, width=3, color=HAIR)


def d1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    cx, bt = 430, 690
    _guitar(c, cx=cx, body_top=bt, scale=1.05)
    targets = [
        ((cx - 50 * 1.05, bt - 330 * 1.05), (150, 240)),
        ((cx - 28 * 1.05, bt - 130 * 1.05), (150, 470)),
        ((cx + 94 * 1.05, bt + 138 * 1.05), (770, 600)),
        ((cx - 58 * 1.05, bt + 314 * 1.05), (150, 900)),
        ((cx + 88 * 1.05, bt + 286 * 1.05), (770, 900)),
        ((cx + 88 * 1.05, bt + 360 * 1.05), (770, 1120)),
    ]
    for start, end in targets:
        c.line(start, end, 3.2, MID)
        c.solid_circle(start, 5, INK)
        ex, ey = end
        if ex < start[0]:
            c.rounded_rect((ex - 100, ey - 18, ex, ey + 18), radius=9, width=3, color=HAIR)
        else:
            c.rounded_rect((ex, ey - 18, ex + 100, ey + 18), radius=9, width=3, color=HAIR)
    return c.save(_out(out, "d1_guitar_overview.png"))


def _body_closeup(c: Canvas, cx: float, body_top: float, scale: float,
                  switch: str = "mid", highlight: str | None = None):
    s = scale

    def P(x, y):
        return (cx + x * s, body_top + y * s)

    body_pts = [
        (0, 4), (54, 8), (82, 20), (94, 46), (86, 78), (72, 94),
        (104, 120), (128, 158), (136, 196), (130, 224), (114, 244),
        (124, 268), (146, 312), (158, 356), (150, 396),
        (122, 424), (78, 442), (0, 448),
        (-78, 442), (-122, 424), (-150, 396),
        (-158, 356), (-146, 312), (-124, 268),
        (-114, 244), (-130, 224), (-136, 196),
        (-128, 158), (-104, 120), (-72, 94), (-86, 78), (-94, 46), (-82, 20), (-54, 8),
    ]
    c.blob([P(*p) for p in body_pts], outline=7, fill=WHITE, color=INK)
    # 琴颈残段
    c.rounded_rect((cx - 30 * s, body_top - 110 * s, cx + 30 * s, body_top + 4 * s),
                   radius=4, width=5, fill=WHITE, color=INK)
    for i in range(3):
        c.line((cx - 30 * s, body_top - (96 - i * 32) * s),
               (cx + 30 * s, body_top - (96 - i * 32) * s), 2.4, INK)
    # 弦
    for k in range(6):
        x = cx + (-44 + k * 17.6) * s
        c.line((x, body_top - 110 * s), (x, body_top + 306 * s), 2, MID)
    # 拾音器
    for y0 in (196, 268):
        c.rounded_rect((cx - 70 * s, body_top + y0 * s,
                        cx + 70 * s, body_top + (y0 + 40) * s),
                       radius=14 * s, width=5.5, fill=WHITE, color=INK)
        lit = (highlight == "neck" and y0 == 196) or \
              (highlight == "bridge" and y0 == 268) or highlight == "both"
        if lit:
            c.rounded_rect((cx - 60 * s, body_top + (y0 + 7) * s,
                            cx + 60 * s, body_top + (y0 + 33) * s),
                           radius=10 * s, width=0, fill=INK, color=INK)
        else:
            for k in range(6):
                c.solid_circle((cx + (-48 + k * 19.2) * s,
                                body_top + (y0 + 20) * s), 2.6 * s, MID)
    # 琴桥
    c.rounded_rect((cx - 64 * s, body_top + 306 * s, cx + 64 * s, body_top + 324 * s),
                   radius=6 * s, width=5, fill=WHITE, color=INK)
    # 拨杆
    sx, sy = cx + 100 * s, body_top + 138 * s
    c.circle((sx, sy), 15 * s, outline=5, fill=WHITE)
    if switch == "neck":
        c.line((sx, sy - 36 * s), (sx, sy + 4 * s), 9 * s, INK)
    elif switch == "bridge":
        c.line((sx, sy - 4 * s), (sx, sy + 36 * s), 9 * s, INK)
    else:
        c.line((sx - 32 * s, sy), (sx + 32 * s, sy), 9 * s, INK)
    c.solid_circle((sx, sy), 5 * s, INK)
    # 弦钮区提示：四个旋钮
    for kx, ky in [(96, 330), (130, 366), (62, 370), (96, 404)]:
        c.circle((cx + kx * s, body_top + ky * s), 13 * s, outline=4, fill=WHITE)
        c.solid_circle((cx + kx * s, body_top + ky * s), 4 * s, MID)


def d2(out: Path) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    w, gap = 404, 30
    for i, (sw, hl) in enumerate((("neck", "neck"), ("mid", "both"), ("bridge", "bridge"))):
        px = 44 + i * (w + gap)
        c.panel((px, 70, px + w, 954), radius=26)
        _body_closeup(c, cx=px + w / 2, body_top=330, scale=1.05,
                      switch=sw, highlight=hl)
    return c.save(_out(out, "d2_pickup_switch.png"))


def d3(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    knobs = [(330, 440, True), (694, 440, True), (330, 850, False), (694, 850, False)]
    for kx, ky, vol in knobs:
        r = 92
        c.circle((kx, ky), r, outline=8, fill=WHITE)
        for t in range(11):
            a = math.radians(-210 + t * 24)
            c.line((kx + math.cos(a) * (r + 16), ky + math.sin(a) * (r + 16)),
                   (kx + math.cos(a) * (r + 32), ky + math.sin(a) * (r + 32)), 3.2, SOFT)
        c.line((kx, ky), (kx, ky - r + 20), 6, MID)
        if vol:
            c.solid_circle((kx, ky), 18, INK)
        else:
            c.circle((kx, ky), 18, outline=5, fill=WHITE)
    return c.save(_out(out, "d3_knobs_layout.png"))

def d4(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((60, 150, 1305, 870), radius=30)
    c.rounded_rect((110, 230, 1255, 790), radius=26, width=8, fill=WHITE, color=INK)
    # 输入孔
    c.circle((230, 510), 24, outline=6, fill=WHITE)
    c.solid_circle((230, 510), 9, INK)
    # 三个旋钮
    for kx, ang in ((430, 145), (690, 90), (950, 55)):
        c.circle((kx, 510), 68, outline=7, fill=WHITE)
        for t in range(11):
            a = math.radians(-210 + t * 24)
            c.line((kx + math.cos(a) * 84, 510 + math.sin(a) * 84),
                   (kx + math.cos(a) * 98, 510 + math.sin(a) * 98), 3, SOFT)
        c.line((kx, 510), (kx + math.cos(math.radians(ang)) * 52,
                           510 + math.sin(math.radians(ang)) * 52), 6, INK)
    # 电源灯
    c.circle((1190, 510), 15, outline=5, fill=INK)
    # 音量旋钮：敏感区间的弧形箭头
    c.stroke([(950 + math.cos(math.radians(a)) * 112,
               510 + math.sin(math.radians(a)) * 112) for a in range(205, 336, 10)],
             5, MID)
    c.arrow((950 + math.cos(math.radians(331)) * 112,
             510 + math.sin(math.radians(331)) * 112),
            (950 + math.cos(math.radians(338)) * 112,
             510 + math.sin(math.radians(338)) * 112), 4.5, MID, 14)
    # 小刻度强调“行程很短”
    for a in range(240, 301, 15):
        c.line((950 + math.cos(math.radians(a)) * 84, 510 + math.sin(math.radians(a)) * 84),
               (950 + math.cos(math.radians(a)) * 104, 510 + math.sin(math.radians(a)) * 104),
               4, INK)
    # 声波
    for k in range(3):
        r = 30 + k * 22
        pts = [(1110 + math.cos(math.radians(a)) * r,
                690 + math.sin(math.radians(a)) * r) for a in range(-60, 61, 10)]
        c.stroke(pts, 4.5, MID)
    return c.save(_out(out, "d4_amp_panel.png"))


def _amp(c: Canvas, center, scale: float = 1.0, blank_knobs: bool = False):
    x, y = center
    s = scale
    c.rounded_rect((x - 205 * s, y - 130 * s, x + 205 * s, y + 240 * s),
                   radius=22 * s, width=7, fill=WHITE, color=INK)
    c.rounded_rect((x - 186 * s, y - 108 * s, x + 186 * s, y + 6 * s),
                   radius=12 * s, width=4.5, fill=WHITE, color=INK)
    c.rounded_rect((x - 168 * s, y + 40 * s, x + 168 * s, y + 214 * s),
                   radius=10 * s, width=4, fill=WHITE, color=INK)
    for k in range(7):
        yy = y + 60 * s + k * 23 * s
        c.line((x - 148 * s, yy), (x + 148 * s, yy), 3, PALE)

    c.circle((x - 148 * s, y - 50 * s), 11 * s, outline=4.5, fill=WHITE)
    c.solid_circle((x - 148 * s, y - 50 * s), 4 * s, INK)
    for kx, ang in ((x - 66 * s, 130), (x + 12 * s, 90), (x + 108 * s, 60)):
        c.circle((kx, y - 50 * s), 20 * s, outline=4.5, fill=WHITE)
        for t in range(9):
            a = math.radians(-180 + t * 22.5)
            c.line((kx + math.cos(a) * 25 * s, y - 50 * s + math.sin(a) * 25 * s),
                   (kx + math.cos(a) * 29 * s, y - 50 * s + math.sin(a) * 29 * s), 2, SOFT)
        if blank_knobs:
            c.line((kx, y - 50 * s), (kx, y - 68 * s), 4, MID)
        else:
            c.line((kx, y - 50 * s),
                   (kx + math.cos(math.radians(ang)) * 16 * s,
                    y - 50 * s + math.sin(math.radians(ang)) * 16 * s), 4, INK)
    c.circle((x + 164 * s, y - 50 * s), 8 * s, outline=4, fill=INK)


def d5(out: Path) -> Path:
    c = Canvas(*LANDSCAPE, margin=40)
    c.panel((40, 40, 800, 984), radius=30)
    c.panel((830, 40, 1325, 984), radius=30)
    _guitar(c, cx=420, body_top=480, scale=0.92, blank_knobs=True, strings=False)
    _amp(c, (1078, 512), scale=1.20, blank_knobs=True)
    return c.save(_out(out, "d5_today_setting_template.png"))


# ────────────────────────────────────────────────────────────
# E 组：流程
# ────────────────────────────────────────────────────────────

def _guitar_simple(c: Canvas, center, angle: float, scale: float = 1.0,
                   body_w: float = 300, body_h: float = 380):
    cx, cy = center
    ca, sa = math.cos(angle), math.sin(angle)

    def T(x, y):
        return (cx + (x * ca - y * sa) * scale, cy + (x * sa + y * ca) * scale)

    # 琴体
    body = []
    for i in range(48):
        a = 2 * math.pi * i / 48
        rx = body_w / 2 * (1 - 0.10 * math.cos(2 * a))
        ry = body_h / 2
        body.append(T(math.cos(a) * rx, math.sin(a) * ry))
    c.blob(body, outline=7, fill=WHITE, color=INK)
    # 琴颈
    neck = [T(-22, -body_h * 0.42), T(22, -body_h * 0.42),
            T(22, -body_h * 0.42 - 300), T(-22, -body_h * 0.42 - 300)]
    c.d.polygon(c._p(neck), fill=WHITE)
    c.stroke(neck + [neck[0]], 6, INK)
    # 琴头
    head = [T(-34, -body_h * 0.42 - 300), T(34, -body_h * 0.42 - 300),
            T(40, -body_h * 0.42 - 380), T(-40, -body_h * 0.42 - 380)]
    c.d.polygon(c._p(head), fill=WHITE)
    c.stroke(head + [head[0]], 6, INK)
    # 拾音器
    for off in (-30, 20):
        c.capsule([T(-52, off), T(52, off)], 18, 4.5, WHITE, INK)
    # 弦
    for k in range(6):
        x = -36 + k * 14.4
        c.stroke([T(x, -body_h * 0.42 - 280), T(x, 60)], 1.6, MID)


def e1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    # 地面
    c.line((70, 1180), (954, 1180), 4, HAIR)
    # 凳子
    c.line((280, 830), (700, 830), 9, INK)
    for x in (310, 670):
        c.line((x, 832), (x - 40, 1180), 7, INK)
    c.line((300, 1010), (660, 1010), 5, INK)
    # 右腿
    c.capsule([(470, 790), (650, 800), (690, 1010)], 78, 6.5, WHITE, INK)
    c.capsule([(690, 1010), (742, 1160)], 44, 5.5, WHITE, INK)
    c.capsule([(742, 1170), (820, 1174)], 34, 5, WHITE, INK)
    # 躯干
    torso = [(430, 500), (540, 470), (600, 560), (590, 700), (540, 800),
             (450, 800), (405, 700), (408, 590)]
    c.blob(torso, outline=7, fill=WHITE, color=INK)
    # 头与脖子
    c.capsule([(500, 470), (505, 410)], 62, 6, WHITE, INK)
    c.circle((505, 360), 62, outline=7, fill=WHITE)
    # 大臂
    c.capsule([(560, 540), (640, 620), (700, 680)], 46, 6, WHITE, INK)
    # 吉他（斜放）
    _guitar_simple(c, (720, 780), angle=math.radians(-57), scale=0.82)
    # 左腿
    c.capsule([(430, 800), (400, 1030)], 44, 5.5, WHITE, INK)
    c.capsule([(400, 1040), (392, 1160)], 40, 5.5, WHITE, INK)
    c.capsule([(392, 1170), (320, 1176)], 34, 5, WHITE, INK)
    # 按弦手
    c.blob([(410, 590), (470, 570), (492, 612), (462, 650), (414, 636)],
           outline=5, fill=WHITE, color=INK)
    for k in range(4):
        c.circle((430 + k * 16, 574 + k * 6), 10, outline=3.5, fill=WHITE)
    # 拨弦手 + 拨片
    c.blob([(720, 700), (790, 690), (812, 740), (778, 786), (720, 772)],
           outline=5, fill=WHITE, color=INK)
    pick_shape(c, (752, 724), 30)
    # 背部提示线
    c.dashed([(420, 520), (420, 780)], 3.5, SOFT, 10, 8)
    return c.save(_out(out, "e1_sitting_posture.png"))


def _cloth(c: Canvas, center, size: float = 120, angle: float = 0.0):
    x, y = center
    c.rounded_rect((x - size / 2, y - size / 2, x + size / 2, y + size / 2),
                   radius=size * 0.16, width=5, fill=WHITE, color=INK)
    c.stroke([(x - size * 0.32, y - size * 0.1), (x + size * 0.28, y - size * 0.2)], 3.5, SOFT)
    c.stroke([(x - size * 0.3, y + size * 0.18), (x + size * 0.3, y + size * 0.08)], 3.5, SOFT)
    c.hatch([(x - size * 0.42, y + size * 0.12), (x + size * 0.42, y + size * 0.02),
             (x + size * 0.42, y + size * 0.3), (x - size * 0.42, y + size * 0.4)],
            spacing=9, angle=45, width=2, color=PALE)


def e2(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 658, 984), radius=30)
    c.panel((707, 40, 1325, 984), radius=30)

    # 左：擦弦
    for k in range(3):
        y = 430 + k * 70
        c.line((110, y), (600, y), 6, INK)
    _cloth(c, (340, 430), 130)
    hand_pick(c, (410, 290), tilt=-0.10, scale=0.72, show_pick=False)
    c.arrow((250, 560), (250, 640), 5, MID, 16)
    c.arrow((250, 640), (250, 560), 5, MID, 16)

    # 右：擦指板
    geo = neck_geometry((760, 360, 1270, 620), nfrets=6)
    draw_neck_h(c, geo, width=2.6, inlays=False)
    _cloth(c, (980, 470), 150)
    hand_pick(c, (1050, 360), tilt=-0.10, scale=0.74, show_pick=False)
    c.arrow((860, 660), (1100, 660), 5, MID, 16)
    c.arrow((1100, 660), (860, 660), 5, MID, 16)
    return c.save(_out(out, "e2_maintenance.png"))


def e3(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 658, 984), radius=30)
    c.panel((707, 40, 1325, 984), radius=30)

    # 左：吉他架（架子先画，琴体压在上面）
    c.line((150, 860), (590, 860), 4, HAIR)  # 地面
    c.line((250, 840), (330, 600), 8, INK)
    c.line((490, 840), (410, 600), 8, INK)
    c.line((285, 730), (455, 730), 6, INK)
    c.line((300, 840), (300, 860), 6, INK)
    c.line((440, 840), (440, 860), 6, INK)
    _guitar(c, cx=370, body_top=430, scale=0.62, strings=False)
    # 接触点
    for x in (330, 412):
        c.solid_circle((x, 707), 6, INK)
    check(c, (560, 300), 26, INK)

    # 右：靠墙
    c.line((760, 200), (760, 880), 8, INK)  # 墙
    c.line((760, 880), (1240, 880), 4, HAIR)  # 地面
    _guitar_simple(c, (940, 720), angle=math.radians(-14), scale=0.86)
    c.dashed([(940, 380), (940, 880)], 4, SOFT, 12, 9)
    c.arrow((1060, 600), (1010, 760), 4.5, MID, 16)
    cross(c, (1150, 300), 26, INK)
    return c.save(_out(out, "e3_storage.png"))


# ────────────────────────────────────────────────────────────
# F 组：无琴日
# ────────────────────────────────────────────────────────────

def _clap_hand(c: Canvas, center, scale: float = 1.0):
    x, y = center
    s = scale
    c.blob([(x - 46 * s, y - 10 * s), (x + 46 * s, y - 14 * s),
            (x + 52 * s, y + 46 * s), (x + 8 * s, y + 74 * s),
            (x - 48 * s, y + 56 * s), (x - 54 * s, y + 10 * s)],
           outline=5.5 * s, fill=WHITE, color=INK)
    for k in range(4):
        fx = x - 42 * s + k * 28 * s
        c.capsule([(fx, y - 6 * s), (fx, y - 62 * s)], 22 * s, 4.5 * s, WHITE, INK)
    c.capsule([(x - 48 * s, y + 24 * s), (x - 86 * s, y - 4 * s)], 24 * s, 4.5 * s, WHITE, INK)


def f1(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.line((160, 620), (1200, 620), 5, INK)
    for i in range(4):
        x = 260 + i * 280
        c.circle((x, 540), 36, outline=6, fill=WHITE)
        c.line((x, 620), (x, 652), 5, INK)
        if i % 2 == 0:
            _clap_hand(c, (x, 776), 1.12)
            for k in (-1, 1):
                c.line((x + k * 104, 708), (x + k * 138, 676), 4, MID)
        else:
            c.line((x - 34, 776), (x + 34, 776), 4, SOFT)
    return c.save(_out(out, "f1_clap_rhythm.png"))


def f2(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    staff(c, (500, 1040, 944, 1180), notes=3, width=2, color=SOFT, note_r=8)
    return c.save(_out(out, "f2_theory_card_bg.png"))


# ────────────────────────────────────────────────────────────
# G 组：状态图
# ────────────────────────────────────────────────────────────

def g1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    _guitar(c, cx=512, body_top=700, scale=0.72, strings=False)
    for x, y, r in [(250, 330, 26), (760, 300, 20), (820, 520, 24), (210, 600, 18)]:
        star(c, (x, y), r)
    for x, y in [(300, 460), (720, 700), (840, 800)]:
        eighth_note(c, (x, y), 44)
    for x, y in [(420, 260), (600, 280), (880, 420)]:
        sparkle(c, (x, y), 16)
    return c.save(_out(out, "g1_complete_celebration.png"))


def g2(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    # 谱纸
    paper = [(230, 330), (800, 300), (840, 980), (270, 1010)]
    c.d.polygon(c._p(paper), fill=WHITE)
    c.stroke(paper + [paper[0]], 6, INK)
    for k in range(3):
        y = 600 + k * 26
        c.line((300, y), (770, y - 4), 3, SOFT)
    c.stroke([(800, 300), (760, 340), (840, 980)], 3, PALE)
    # 铅笔
    c.capsule([(560, 1100), (860, 900)], 30, 5, WHITE, INK)
    c.d.polygon(c._p([(860, 900), (900, 876), (872, 916)]), fill=INK)
    # 小芽
    c.stroke([(180, 1120), (180, 1036)], 5, INK)
    c.blob([(180, 1030), (146, 1012), (150, 976), (184, 992),
            (206, 1016)], outline=4.5, fill=WHITE, color=INK)
    c.blob([(184, 1010), (212, 990), (230, 1012), (206, 1032)],
           outline=4.5, fill=WHITE, color=INK)
    # 小云
    c.blob([(744, 226), (758, 190), (796, 184), (826, 204),
            (816, 234), (768, 240)], outline=4, fill=WHITE, color=SOFT)
    return c.save(_out(out, "g2_empty_phrase.png"))


def g3(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    cx, cy = 512, 720
    c.ring((cx, cy), 300, 4, HAIR)
    for a in range(198, 344, 10):
        a0, a1 = math.radians(a), math.radians(a + 5)
        c.stroke([(cx + math.cos(a0) * 300, cy + math.sin(a0) * 300),
                  (cx + math.cos(a1) * 300, cy + math.sin(a1) * 300)], 4, SOFT)
    # 手掌
    c.blob([(cx - 124, cy - 136), (cx + 124, cy - 136), (cx + 154, cy + 34),
            (cx + 92, cy + 168), (cx - 92, cy + 168), (cx - 154, cy + 34)],
           outline=7, fill=WHITE, color=INK)
    # 食指
    c.capsule([(cx - 12, cy - 120), (cx - 12, cy - 356)], 64, 6.5, WHITE, INK)
    c.circle((cx - 12, cy - 368), 32, outline=6, fill=WHITE)
    # 另外三指蜷起
    for k in range(3):
        x = cx + 64 + k * 54
        c.circle((x, cy - 100), 27, outline=5, fill=WHITE)
        c.stroke([(x - 22, cy - 74), (x - 18, cy - 36)], 3, SOFT)
    # 拇指
    c.capsule([(cx - 124, cy - 4), (cx - 232, cy - 66)], 46, 6, WHITE, INK)
    return c.save(_out(out, "g3_hand_check_icon.png"))


REGISTRY = {
    "a1": a1, "a2": a2, "a3": a3, "a4": a4, "a5": a5,
    "b1": b1, "b2": b2, "b3": b3,
    "c1": c1, "c2": c2, "c3": c3, "c4": c4, "c5": c5,
    "c6": c6, "c7": c7, "c8": c8,
    "d1": d1, "d2": d2, "d3": d3, "d4": d4, "d5": d5,
    "e1": e1, "e2": e2, "e3": e3,
    "f1": f1, "f2": f2,
    "g1": g1, "g2": g2, "g3": g3,
}
