"""用 Openclipart（公有领域）素材合成插图。

素材来源见 `docs/插图素材来源.md`：
- 开放和弦图：openclipart 214090 等（Public Domain）
- Les Paul 电吉他：openclipart 4444（Public Domain）
- 吉他架：openclipart 2610（Public Domain）

与 `figures.py` 里的纯代码线稿相比，这里保留素材的细节与明暗，
再叠加 App 需要的编号、空白标签位、空白旋钮等。
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageOps

from .hands import hand_pick
from .lineart import (Canvas, INK, MID, SOFT, HAIR, WHITE, star, eighth_note, sparkle,
                       check, cross, sweat_drop, pick_shape)

ASSETS = Path(__file__).resolve().parent / "sources" / "render"
CHORDS = ASSETS / "chords"
PORTRAIT = (1024, 1365)
LANDSCAPE = (1365, 1024)


def _paste(c: Canvas, im: Image.Image, x: float, y: float, w: float,
           h: float | None = None, mask=None) -> float:
    if h is None:
        h = w * im.height / im.width
    size = (max(1, round(w * c.scale)), max(1, round(h * c.scale)))
    im = im.convert("RGBA").resize(size, Image.LANCZOS)
    c.img.paste(im, (round(x * c.scale), round(y * c.scale)), mask if mask is not None else im)
    return h


def _chord(name: str) -> Image.Image:
    return Image.open(CHORDS / f"{name}.png")


def _blank_knob(c: Canvas, center, r: float = 16, width: float = 3.5):
    x, y = center
    c.circle((x, y), r, outline=width, fill=WHITE)
    for t in range(4):
        a = math.radians(t * 90 + 45)
        c.line((x + math.cos(a) * (r + 3), y + math.sin(a) * (r + 3)),
               (x + math.cos(a) * (r + 8), y + math.sin(a) * (r + 8)), 2, SOFT)
    c.line((x, y), (x, y - r + 3), 3.2, MID)


# ── C 组：开放和弦 ─────────────────────────────────────────

def _chord_page(out: Path, filename: str, left: str, right: str,
                sweat: bool = False) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 658, 984), radius=30)
    c.panel((707, 40, 1325, 984), radius=30)
    for i, key in enumerate((left, right)):
        im = _chord(key)
        w = 556
        h = w * im.height / im.width
        x = (69 if i == 0 else 736)
        y = 520 - h / 2
        _paste(c, im, x, y, w)
    if sweat:
        sweat_drop(c, (596, 346), 20)
    return c.save(out / filename)


def c1(out: Path) -> Path:
    return _chord_page(out, "c1_c_g.png", "csimple", "gsimple")


def c2(out: Path) -> Path:
    return _chord_page(out, "c2_d_am.png", "d", "am")


def c3(out: Path) -> Path:
    return _chord_page(out, "c3_em_e.png", "em", "e")


def c4(out: Path) -> Path:
    # Dm7 作为 Dm 的简化替代（手指粗、按 Dm 容易闷弦）
    return _chord_page(out, "c4_a_dm7.png", "a", "dm7")


def c5(out: Path) -> Path:
    return _chord_page(out, "c5_f_simplified.png", "f", "fsimple", sweat=True)


# ── D 组：设备 ──────────────────────────────────────────────

def _lp() -> Image.Image:
    return Image.open(ASSETS / "lp_clean.png")


def d1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    lp = _lp()
    w, x0, y0 = 900, 62, 360
    h = _paste(c, lp, x0, y0, w)
    s = w / lp.width
    parts = [
        (1, (1210, 90)),   # 琴头
        (2, (800, 230)),   # 琴颈
        (3, (475, 235)),   # 拾音器拨杆
        (4, (250, 390)),   # 琴桥
        (5, (150, 520)),   # 音量旋钮
        (6, (195, 575)),   # 音色旋钮
    ]
    for n, (px, py) in parts:
        cx, cy = x0 + px * s, y0 + py * s
        c.solid_circle((cx, cy), 19, WHITE)
        c.solid_circle((cx, cy), 16, INK)
        c.digit(n, (cx, cy - 1), 17, WHITE)
    # 底部图例：编号 + 空白标签位
    for i, n in enumerate((1, 2, 3, 4, 5, 6)):
        col, row = i % 3, i // 3
        bx = 74 + col * 310
        by = 1030 + row * 118
        c.solid_circle((bx, by), 21, INK)
        c.digit(n, (bx, by - 1), 19, WHITE)
        c.rounded_rect((bx + 34, by - 27, bx + 250, by + 27), radius=10,
                       width=3, color=(190, 190, 190))
    return c.save(out / "d1_guitar_overview.png")


def d5(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 800, 984), radius=30)
    c.panel((830, 40, 1325, 984), radius=30)
    lp = _lp()
    w, x0, y0 = 720, 60, 320
    _paste(c, lp, x0, y0, w)
    s = w / lp.width
    # 覆盖为 12 点方向的空白旋钮，供 App 叠加指针
    for kx, ky in ((150, 520), (200, 555), (120, 560), (170, 600)):
        _blank_knob(c, (x0 + kx * s, y0 + ky * s), 16)
    # 拨杆也留空
    sx, sy = x0 + 475 * s, y0 + 235 * s
    c.circle((sx, sy), 12, outline=3.5, fill=WHITE)
    c.line((sx, sy), (sx, sy - 9), 3, MID)
    # 音箱（沿用矢量线稿）
    from .figures import _amp
    _amp(c, (1078, 512), scale=1.20, blank_knobs=True)
    return c.save(out / "d5_today_setting_template.png")


# ── G 组：庆祝 ──────────────────────────────────────────────

def g1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    lp = _lp()
    _paste(c, lp, 82, 500, 860)
    for x, y, r in [(220, 300, 24), (470, 250, 18), (820, 330, 22), (170, 1080, 20)]:
        star(c, (x, y), r)
    for x, y in [(300, 420), (860, 660), (760, 1050)]:
        eighth_note(c, (x, y), 42)
    for x, y in [(600, 300), (930, 450), (250, 1000)]:
        sparkle(c, (x, y), 15)
    check(c, (900, 240), 26, INK)
    return c.save(out / "g1_complete_celebration.png")


# ── E 组：坐姿与归位（复用已渲染好的素材） ──────────────────

def e1(out: Path) -> Path:
    c = Canvas(*PORTRAIT)
    c.line((70, 1180), (954, 1180), 4, HAIR)
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
    c.capsule([(500, 470), (505, 410)], 62, 6, WHITE, INK)
    c.circle((505, 360), 62, outline=7, fill=WHITE)
    c.capsule([(560, 540), (640, 620), (700, 680)], 46, 6, WHITE, INK)
    # 左腿
    c.capsule([(430, 800), (400, 1030)], 44, 5.5, WHITE, INK)
    c.capsule([(400, 1040), (392, 1160)], 40, 5.5, WHITE, INK)
    c.capsule([(392, 1170), (320, 1176)], 34, 5, WHITE, INK)
    # 吉他
    lp = Image.open(ASSETS / "lp_mirror.png")
    _paste(c, lp, 345, 645, 430)
    # 按弦手
    c.blob([(368, 660), (426, 640), (448, 682), (418, 720), (372, 706)],
           outline=5, fill=WHITE, color=INK)
    for k in range(4):
        c.circle((386 + k * 16, 646 + k * 6), 10, outline=3.5, fill=WHITE)
    # 拨弦手 + 拨片
    c.blob([(676, 700), (744, 690), (766, 740), (732, 786), (676, 772)],
           outline=5, fill=WHITE, color=INK)
    pick_shape(c, (708, 724), 30)
    c.dashed([(420, 520), (420, 780)], 3.5, SOFT, 10, 8)
    return c.save(out / "e1_sitting_posture.png")


def e3(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((40, 40, 658, 984), radius=30)
    c.panel((707, 40, 1325, 984), radius=30)

    # 左：立式吉他架（线稿） + 立放的琴
    c.line((120, 880), (600, 880), 4, HAIR)
    c.line((220, 870), (260, 720), 9, INK)
    c.line((480, 870), (440, 720), 9, INK)
    c.line((245, 800), (455, 800), 6, INK)
    c.line((260, 720), (300, 735), 8, INK)
    c.line((440, 720), (400, 735), 8, INK)
    c.line((320, 300), (370, 300), 6, INK)
    up = Image.open(ASSETS / "lp_upright.png")
    _paste(c, up, 250, 125, 200)
    for x in (300, 400):
        c.solid_circle((x, 725), 6, INK)
    check(c, (570, 300), 26, INK)

    # 右：靠墙，重心不稳
    c.line((1290, 180), (1290, 880), 8, INK)
    c.line((760, 880), (1320, 880), 4, HAIR)
    lean = Image.open(ASSETS / "lp_lean.png")
    _paste(c, lean, 920, 215, 404)
    c.dashed([(1030, 280), (1030, 880)], 4, SOFT, 12, 9)
    c.arrow((1160, 520), (1090, 700), 4.5, MID, 16)
    cross(c, (1140, 300), 26, INK)
    return c.save(out / "e3_storage.png")


# ── B 组：右手（用已渲染的吉他素材） ────────────────────────

def b3(out: Path) -> Path:
    c = Canvas(*LANDSCAPE)
    c.panel((60, 60, 1305, 964), radius=34)
    lp = Image.open(ASSETS / "lp_noshadow.png")
    _paste(c, lp, 180, 250, 1000)
    # 掌心压弦的接触区
    c.dashed([(300, 590), (420, 590)], 4, SOFT, 10, 8)
    # 手
    hand_pick(c, (330, 470), tilt=math.radians(6), scale=0.62)
    # 前臂搭在琴身上
    c.capsule([(360, 390), (520, 320)], 70, 5.5, WHITE, INK)
    for k in range(2):
        c.arrow((400 + k * 60, 650), (412 + k * 60, 600), 4, MID, 13)
    return c.save(out / "b3_right_hand_rest.png")


SOURCED = {
    "b3": b3,
    "c1": c1, "c2": c2, "c3": c3, "c4": c4, "c5": c5,
    "d1": d1, "g1": g1, "e1": e1, "e3": e3,
}
