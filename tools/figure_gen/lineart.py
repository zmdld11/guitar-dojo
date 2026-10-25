"""黑白线稿绘图工具。

输出与 `docs/画图提示词.md` 约定的风格一致：纯白底、粗圆头深色线条、
无渐变、少量排线，3:4 或 4:3 画布，适合直接作为 App 插图。

所有坐标都使用最终像素坐标，内部按 `scale` 倍超采样后缩回，保证曲线干净。
"""

from __future__ import annotations

import math
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

INK = (24, 24, 24)
MID = (105, 105, 105)
SOFT = (160, 160, 160)
HAIR = (200, 200, 200)
PALE = (232, 232, 232)
WHITE = (255, 255, 255)

FONT_PATH = "C:/Windows/Fonts/arialbd.ttf"


def _dist(a, b):
    return math.hypot(b[0] - a[0], b[1] - a[1])


def catmull_rom(points, closed: bool = False, samples: int = 16):
    """Catmull-Rom 样条采样，用来把少量控制点变成顺滑曲线。"""
    pts = [tuple(p) for p in points]
    if len(pts) < 3:
        return pts
    if closed:
        pts = [pts[-1]] + pts + [pts[0], pts[1]]
    else:
        pts = [pts[0]] + pts + [pts[-1]]

    out = []
    for i in range(len(pts) - 3):
        p0, p1, p2, p3 = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
        for j in range(samples):
            t = j / samples
            t2, t3 = t * t, t * t * t
            x = 0.5 * (
                (2 * p1[0])
                + (-p0[0] + p2[0]) * t
                + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3
            )
            y = 0.5 * (
                (2 * p1[1])
                + (-p0[1] + p2[1]) * t
                + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3
            )
            out.append((x, y))
    if not closed:
        out.append(pts[-2])
    return out


class Canvas:
    def __init__(self, w: int, h: int, scale: int = 2, margin: int = 60):
        self.w, self.h, self.scale, self.margin = w, h, scale, margin
        self.img = Image.new("RGB", (w * scale, h * scale), WHITE)
        self.d = ImageDraw.Draw(self.img)
        self._font_cache = {}

    # ── 基础 ──────────────────────────────────────────────
    def _p(self, pts):
        s = self.scale
        return [(float(x) * s, float(y) * s) for x, y in pts]

    def _r(self, v):
        return float(v) * self.scale

    def stroke(self, pts, width: float = 6, color=INK):
        if len(pts) < 2:
            return
        P = self._p(pts)
        W = max(1, int(round(self._r(width))))
        self.d.line(P, fill=color, width=W, joint="curve")
        r = W / 2.0
        for x, y in P:
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=color)

    def line(self, a, b, width: float = 6, color=INK):
        self.stroke([a, b], width, color)

    def dashed(self, pts, width: float = 4, color=MID, dash: float = 16, gap: float = 11):
        """沿折线画虚线，圆头。"""
        if len(pts) < 2:
            return
        pattern = (dash, gap)
        idx, remain = 0, pattern[0]
        drawing = True
        for a, b in zip(pts[:-1], pts[1:]):
            seg = _dist(a, b)
            if seg == 0:
                continue
            ux, uy = (b[0] - a[0]) / seg, (b[1] - a[1]) / seg
            pos = 0.0
            while pos < seg:
                step = min(remain, seg - pos)
                if drawing:
                    p0 = (a[0] + ux * pos, a[1] + uy * pos)
                    p1 = (a[0] + ux * (pos + step), a[1] + uy * (pos + step))
                    self.stroke([p0, p1], width, color)
                pos += step
                remain -= step
                if remain <= 1e-6:
                    idx = (idx + 1) % 2
                    drawing = idx == 0
                    remain = pattern[idx]

    def capsule(self, pts, width: float = 34, outline: float = 7, fill=WHITE, color=INK):
        """带描边的圆头胶囊折线，用来画手指、手臂、铅笔。"""
        self.stroke(pts, width + outline * 2, color)
        self.stroke(pts, width, fill)

    def ghost_capsule(self, pts, width: float = 34, outline: float = 5, color=SOFT):
        self.stroke(pts, width, color)
        self.stroke(pts, width - outline * 2, WHITE)

    def blob(self, pts, outline: float = 7, fill=WHITE, color=INK, closed: bool = True, samples: int = 18):
        curve = catmull_rom(pts, closed=closed, samples=samples)
        if closed:
            self.d.polygon(self._p(curve), fill=fill)
            self.stroke(curve + [curve[0]], outline, color)
        else:
            self.stroke(curve, outline, color)

    def circle(self, c, r, outline: float = 6, fill=WHITE, color=INK, samples: int = 72):
        x, y = c
        rr = r + outline / 2
        box = [self._r(x - rr), self._r(y - rr), self._r(x + rr), self._r(y + rr)]
        self.d.ellipse(box, fill=color)
        box2 = [self._r(x - r + outline / 2), self._r(y - r + outline / 2),
                self._r(x + r - outline / 2), self._r(y + r - outline / 2)]
        self.d.ellipse(box2, fill=fill)

    def solid_circle(self, c, r, color=INK):
        x, y = c
        self.d.ellipse([self._r(x - r), self._r(y - r), self._r(x + r), self._r(y + r)], fill=color)

    def ring(self, c, r, width: float = 4, color=INK):
        x, y = c
        self.d.ellipse([self._r(x - r), self._r(y - r), self._r(x + r), self._r(y + r)],
                       outline=color, width=max(1, int(round(self._r(width)))))

    def rounded_rect(self, box, radius: float = 24, width: float = 4, fill=None, color=INK):
        x0, y0, x1, y1 = box
        P = [self._r(x0), self._r(y0), self._r(x1), self._r(y1)]
        W = max(1, int(round(self._r(width))))
        self.d.rounded_rectangle(P, radius=self._r(radius), fill=fill, outline=color, width=W)

    def panel(self, box, radius: float = 30, width: float = 3, color=HAIR):
        self.rounded_rect(box, radius=radius, width=width, fill=None, color=color)

    def hatch(self, pts, spacing: float = 15, angle: float = 45, width: float = 3, color=SOFT):
        """在多边形内画斜线排线，模拟手绘明暗。"""
        mask = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(mask).polygon(self._p(pts), fill=255)
        layer = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
        span = math.hypot(max(xs) - min(xs), max(ys) - min(ys)) * 0.75 + spacing
        rad = math.radians(angle)
        ux, uy = math.cos(rad), math.sin(rad)
        W = max(1, int(round(self._r(width))))
        for k in range(-int(span / spacing), int(span / spacing) + 1):
            ox, oy = cx - uy * (k * spacing), cy + ux * (k * spacing)
            p0 = (ox - ux * span, oy - uy * span)
            p1 = (ox + ux * span, oy + uy * span)
            ld.line([self._r(p0[0]), self._r(p0[1]), self._r(p1[0]), self._r(p1[1])], fill=color + (255,), width=W)
        self.img.paste(layer, (0, 0), mask)

    def arrow(self, a, b, width: float = 5, color=INK, head: float = 20, both: bool = False):
        L = _dist(a, b)
        if L < 1:
            return
        ux, uy = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        end = (b[0] - ux * head * 0.75, b[1] - uy * head * 0.75)
        start = a
        if both:
            start = (a[0] + ux * head * 0.75, a[1] + uy * head * 0.75)
        self.stroke([start, end], width, color)
        px, py = -uy, ux
        for tip, back, u in ((b, (b[0] - ux * head, b[1] - uy * head), (ux, uy)),):
            tri = [tip,
                   (back[0] + px * head * 0.42, back[1] + py * head * 0.42),
                   (back[0] - px * head * 0.42, back[1] - py * head * 0.42)]
            self.d.polygon(self._p(tri), fill=color)
        if both:
            tip = a
            back = (a[0] + ux * head, a[1] + uy * head)
            tri = [tip,
                   (back[0] + px * head * 0.42, back[1] + py * head * 0.42),
                   (back[0] - px * head * 0.42, back[1] - py * head * 0.42)]
            self.d.polygon(self._p(tri), fill=color)

    def _font(self, size: float):
        key = int(round(size))
        if key not in self._font_cache:
            try:
                self._font_cache[key] = ImageFont.truetype(FONT_PATH, int(self._r(size)))
            except OSError:
                self._font_cache[key] = ImageFont.load_default()
        return self._font_cache[key]

    def text(self, s, center, size: float = 30, color=INK, anchor: str = "mm"):
        self.d.text(self._p([center])[0], s, font=self._font(size), fill=color, anchor=anchor)

    def digit(self, n, center, size: float = 30, color=WHITE):
        """按弦指法数字，白字压在深色圆点上。"""
        self.text(str(n), center, size=size, color=color)

    def save(self, path: str | Path):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        img = self.img.resize((self.w, self.h), Image.LANCZOS)
        tmp = path.with_suffix(".tmp.png")
        img.save(tmp, optimize=True)
        for attempt in range(6):
            try:
                tmp.replace(path)
                return path
            except OSError:
                # Windows 上文件可能被杀毒/预览短暂占用
                time.sleep(0.4)
        tmp.replace(path)
        return path


# ── 常用部件 ────────────────────────────────────────────────

STRING_GAP = 26


def neck_geometry(box, nfrets: int = 5, fret_scale: float = 0.86):
    """横向琴颈几何：nut 在左，弦水平，品丝竖直且间距递减。

    返回 dict：string_ys / fret_xs / nut_x / x1 / string_gap。
    """
    x0, y0, x1, y1 = box
    h = y1 - y0
    gap = h / 5.0
    ys = [y0 + i * gap for i in range(6)]
    usable = (x1 - x0) * 0.92
    widths, acc = [], 0.0
    for i in range(nfrets):
        w = usable * (1 - fret_scale) * (fret_scale ** i) / (1 - fret_scale ** nfrets)
        widths.append(w)
        acc += w
    scale = usable / acc if acc else 1
    xs, cursor = [x0], x0
    for w in widths:
        cursor += w * scale
        xs.append(cursor)
    return {
        "box": box, "string_ys": ys, "fret_xs": xs, "nut_x": x0,
        "x1": xs[-1], "gap": gap, "nfrets": nfrets,
    }


def draw_neck_h(c: Canvas, geo, string_color=INK, fret_color=INK, width: float = 3.4,
                nut_width: float = 12, inlays: bool = True):
    x0, y0, x1, y1 = geo["box"]
    # 指板外框
    c.rounded_rect((x0, y0 - 8, x1, y1 + 8), radius=10, width=5, fill=WHITE, color=INK)
    # 品丝
    for x in geo["fret_xs"][1:]:
        c.line((x, y0 - 8), (x, y1 + 8), width, fret_color)
    # 弦
    for y in geo["string_ys"]:
        c.line((x0, y), (x1, y), width * 0.85, string_color)
    # 琴枕
    c.line((x0, y0 - 8), (x0, y1 + 8), nut_width, INK)
    # 品记点
    if inlays:
        mid = (geo["string_ys"][2] + geo["string_ys"][3]) / 2
        for i in range(1, geo["nfrets"] + 1):
            if i % 2 == 1:
                cx = (geo["fret_xs"][i - 1] + geo["fret_xs"][i]) / 2
                c.solid_circle((cx, mid), 5.5, SOFT)


def fret_center(geo, fret: int):
    return (geo["fret_xs"][fret - 1] + geo["fret_xs"][fret]) / 2


def chord_chart(c: Canvas, box, frets: int = 5, spec=None, dot_r: float = 15):
    """竖版和弦图：nut 在上，低音 E 在左，高音 e 在右。

    spec: {"mute": [6], "open": [3], "dots": [(string, fret, finger)]}
    string 1 = 高音 e（最右），6 = 低音 E（最左）。
    """
    x0, y0, x1, y1 = box
    gap = (x1 - x0) / 5.0
    xs = [x0 + i * gap for i in range(6)]

    # 琴颈
    c.rounded_rect((x0 - 16, y0, x1 + 16, y1), radius=8, width=4.5, fill=WHITE, color=INK)
    c.line((x0 - 16, y0), (x1 + 16, y0), 14, INK)  # 琴枕
    fret_gap = (y1 - y0) / frets
    for i in range(1, frets + 1):
        y = y0 + i * fret_gap
        c.line((x0 - 16, y), (x1 + 16, y), 3.2, INK)
    for x in xs:
        c.line((x, y0), (x, y1), 2.4, INK)

    spec = spec or {}
    for s in spec.get("mute", []):
        x = xs[6 - s]
        yy = y0 - 30
        c.line((x - 9, yy - 9), (x + 9, yy + 9), 4.2, MID)
        c.line((x - 9, yy + 9), (x + 9, yy - 9), 4.2, MID)
    for s in spec.get("open", []):
        x = xs[6 - s]
        c.circle((x, y0 - 30), 8, outline=4, fill=WHITE)
    for s_from, s_to, f, finger in spec.get("barres", []):
        xa, xb = xs[6 - s_from], xs[6 - s_to]
        y = y0 + (f - 0.5) * fret_gap
        c.rounded_rect((min(xa, xb) - dot_r * 0.55, y - dot_r * 0.95,
                        max(xa, xb) + dot_r * 0.55, y + dot_r * 0.95),
                       radius=dot_r, width=0, fill=INK, color=INK)
        if finger:
            c.digit(finger, ((xa + xb) / 2, y), size=dot_r * 1.2)
    for s, f, finger in spec.get("dots", []):
        x = xs[6 - s]
        y = y0 + (f - 0.5) * fret_gap
        c.solid_circle((x, y), dot_r)
        if finger:
            c.digit(finger, (x, y - 1), size=dot_r * 1.25)
    return {"xs": xs, "y0": y0, "y1": y1, "fret_gap": fret_gap}


def pick_shape(c: Canvas, center, size: float = 74, fill=INK):
    """圆角三角拨片，尖端朝下。"""
    x, y = center
    pts = [
        (x - size * 0.54, y - size * 0.28),
        (x + size * 0.54, y - size * 0.28),
        (x, y + size * 0.50),
    ]
    c.d.polygon(c._p(pts), fill=fill)
    c.stroke(pts + [pts[0]], size * 0.24, fill)


def staff(c: Canvas, box, notes=3, width: float = 2.4, color=MID, note_r: float = 9):
    """五线谱片段，notes 个音符。"""
    x0, y0, x1, y1 = box
    gap = (y1 - y0) / 4
    for i in range(5):
        c.line((x0, y0 + i * gap), (x1, y0 + i * gap), width, color)
    if notes:
        step = (x1 - x0) / (notes + 1)
        for k in range(notes):
            nx = x0 + step * (k + 1)
            ny = y0 + gap * (1 + (k % 3))
            c.solid_circle((nx, ny), note_r, color)
            c.line((nx + note_r * 0.8, ny), (nx + note_r * 0.8, ny - gap * 1.6), width + 1, color)


def sweat_drop(c: Canvas, center, r: float = 16, color=INK):
    x, y = center
    pts = [(x, y - r * 1.8), (x + r * 0.9, y + r * 0.3), (x, y + r * 1.2),
           (x - r * 0.9, y + r * 0.3)]
    curve = catmull_rom(pts, closed=True, samples=14)
    c.d.polygon(c._p(curve), fill=color)
    c.stroke(curve + [curve[0]], 3, color)


def star(c: Canvas, center, r: float = 22, width: float = 4):
    x, y = center
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((x + math.cos(ang) * rr, y + math.sin(ang) * rr))
    c.stroke(pts + [pts[0]], width, INK)


def eighth_note(c: Canvas, center, size: float = 46, color=INK):
    x, y = center
    c.solid_circle((x - size * 0.22, y + size * 0.30), size * 0.20, color)
    c.line((x - size * 0.04, y + size * 0.26), (x - size * 0.04, y - size * 0.44), 6, color)
    c.stroke([(x - size * 0.04, y - size * 0.44),
              (x + size * 0.34, y - size * 0.30),
              (x + size * 0.30, y - size * 0.06)], 6, color)


def sparkle(c: Canvas, center, r: float = 18, width: float = 4):
    x, y = center
    c.line((x - r, y), (x + r, y), width)
    c.line((x, y - r), (x, y + r), width)


def check(c: Canvas, center, r: float = 20, color=INK):
    x, y = center
    c.stroke([(x - r, y), (x - r * 0.25, y + r * 0.7), (x + r, y - r * 0.8)], 6, color)


def cross(c: Canvas, center, r: float = 17, color=INK):
    x, y = center
    c.line((x - r, y - r), (x + r, y + r), 6, color)
    c.line((x - r, y + r), (x + r, y - r), 6, color)
