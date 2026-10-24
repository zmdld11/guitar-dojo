#!/usr/bin/env python3
"""生成 App 内置乐句（全部自编，无版权内容）。

事实来源就是这个脚本：改乐句改这里，然后重新生成 assets。
用法：
    python tools/build_builtin_phrases.py           # 写 assets
    python tools/build_builtin_phrases.py --check   # 只校验产物是否最新
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PPQ = 960
BEATS_PER_BAR = 4
BAR_TICKS = PPQ * BEATS_PER_BAR
TUNING = [40, 45, 50, 55, 59, 64]  # 6 弦 -> 1 弦
OUT_DIR = Path(__file__).resolve().parents[1] / "app/src/main/assets/phrases"


def midi_of(string: int, fret: int) -> int:
    return TUNING[6 - string] + fret


def make_bars(count: int, tempo: float) -> list[dict]:
    return [
        {
            "index": i,
            "startTick": i * BAR_TICKS,
            "lengthTicks": BAR_TICKS,
            "timeSignature": [4, 4],
            "tempoBpm": tempo,
        }
        for i in range(count)
    ]


class Builder:
    def __init__(self, phrase_id: str, title: str, description: str, difficulty: int,
                 tags: list[str], tempo: float, bars: int):
        self.phrase = {
            "schemaVersion": 1,
            "id": phrase_id,
            "title": title,
            "description": description,
            "difficulty": difficulty,
            "tags": tags,
            "origin": "builtin",
            "tuning": TUNING,
            "capo": 0,
            "ppq": PPQ,
            "bars": make_bars(bars, tempo),
            "notes": [],
            "trackName": "Guitar",
        }

    def add(self, bar: int, beat: float, duration_beats: float, string: int, fret: int,
            techniques: list[str] | None = None, velocity: float = 0.8) -> "Builder":
        return self.add_tick(bar * BAR_TICKS + round(beat * PPQ),
                             round(duration_beats * PPQ), string, fret, techniques, velocity)

    def add_tick(self, tick: int, duration_ticks: int, string: int, fret: int,
                 techniques: list[str] | None = None, velocity: float = 0.8) -> "Builder":
        self.phrase["notes"].append({
            "bar": tick // BAR_TICKS,
            "tick": tick,
            "durationTicks": duration_ticks,
            "midi": midi_of(string, fret),
            "string": string,
            "fret": fret,
            "velocity": velocity,
            "techniques": techniques or [],
        })
        return self

    def add_chord(self, bar: int, beat: float, duration_beats: float,
                  positions: list[tuple[int, int]], techniques: list[str] | None = None) -> "Builder":
        for string, fret in positions:
            self.add(bar, beat, duration_beats, string, fret, list(techniques or []))
        return self

    def build(self) -> dict:
        self.phrase["notes"].sort(key=lambda n: (n["tick"], n["string"]))
        return self.phrase


def add_eighths(b: Builder, positions: list[tuple[int, int]]) -> None:
    for index, (string, fret) in enumerate(positions):
        b.add_tick(index * (PPQ // 2), PPQ // 2, string, fret)


def chromatic_basic() -> dict:
    b = Builder("builtin.chromatic.basic", "爬格子 · 基础四连音",
                "1-2-3-4 指序，先上后下。慢速也要让每个音干净地响出来。",
                1, ["热身", "爬格子"], 80, 6)
    add_eighths(b, [(s, f) for s in range(6, 0, -1) for f in (1, 2, 3, 4)])
    add_eighths(b, [(s, f) for s in range(1, 7) for f in (4, 3, 2, 1)])
    return b.build()


def chromatic_reverse() -> dict:
    b = Builder("builtin.chromatic.reverse", "爬格子 · 反向四连音",
                "4-3-2-1 指序。反向时最容易糊音，注意抬指不要带出声。",
                2, ["热身", "爬格子"], 90, 6)
    add_eighths(b, [(s, f) for s in range(1, 7) for f in (4, 3, 2, 1)])
    add_eighths(b, [(s, f) for s in range(6, 0, -1) for f in (1, 2, 3, 4)])
    return b.build()


def chromatic_triplet() -> dict:
    b = Builder("builtin.chromatic.triplet", "爬格子 · 三连音",
                "每弦三连音 1-2-3，上下行连成一条线。三连音要均匀，不要赶最后一拍。",
                3, ["热身", "爬格子", "三连音"], 100, 3)
    third = PPQ // 3  # 三分之一个四分音符 = 320 tick
    positions = [(s, f) for s in range(6, 0, -1) for f in (1, 2, 3)]
    positions += [(s, f) for s in range(1, 7) for f in (3, 2, 1)]
    for index, (string, fret) in enumerate(positions):
        tick = index * third
        b.phrase["notes"].append({
            "bar": tick // BAR_TICKS,
            "tick": tick,
            "durationTicks": third,
            "midi": midi_of(string, fret),
            "string": string,
            "fret": fret,
            "velocity": 0.8,
            "techniques": [],
        })
    b.phrase["notes"].sort(key=lambda n: (n["tick"], n["string"]))
    return b.phrase


def c_major_scale() -> dict:
    b = Builder("builtin.scale.c-major", "C 大调音阶 · 两个八度",
                "从 5 弦 3 品 C3 到 1 弦 8 品 C5。先上行再下行，注意跨弦时左手的落点。",
                2, ["音阶", "大调"], 90, 4)
    ascending = [
        (5, 3), (5, 5), (5, 7),
        (4, 2), (4, 3), (4, 5), (4, 7),
        (3, 2), (3, 4), (3, 5), (3, 7), (3, 9),
        (2, 10), (2, 12), (1, 8),
    ]
    add_eighths(b, ascending + list(reversed(ascending))[1:])
    return b.build()


def g_major_scale() -> dict:
    b = Builder("builtin.scale.g-major", "G 大调音阶 · 两个八度",
                "从 6 弦 3 品 G2 到 2 弦 8 品 G4，包含 F#。把位移动时保持手型。",
                2, ["音阶", "大调"], 88, 4)
    ascending = [
        (6, 3), (6, 5), (6, 7),
        (5, 3), (5, 5), (5, 7), (5, 9),
        (4, 5), (4, 7), (4, 9),
        (3, 5), (3, 7), (3, 9),
        (2, 7), (2, 8),
    ]
    add_eighths(b, ascending + list(reversed(ascending))[1:])
    return b.build()


def a_minor_pentatonic() -> dict:
    b = Builder("builtin.scale.a-minor-pentatonic", "A 小调五声 · 第一把位",
                "5 品把位的 Box 1。五声音阶没有半音，先求稳，再求快。",
                2, ["音阶", "五声"], 92, 3)
    ascending = [
        (6, 5), (6, 8), (5, 5), (5, 7), (4, 5), (4, 7),
        (3, 5), (3, 7), (2, 5), (2, 8), (1, 5), (1, 8),
    ]
    add_eighths(b, ascending + list(reversed(ascending))[1:])
    return b.build()


def e5_power_riff() -> dict:
    b = Builder("builtin.riff.e5-power", "E5 强力和弦 Riff",
                "自编 8 小节。闷音与开放音对比，右手保持上下交替。",
                2, ["Riff", "强力和弦"], 100, 8)
    e5 = [(6, 0), (5, 2)]
    g5 = [(6, 3), (5, 5)]
    a5 = [(6, 5), (5, 7)]
    muted = ["palm_mute"]
    for bar in (0, 1):
        for i in range(8):
            b.add_chord(bar, i * 0.5, 0.4, e5, muted)
    for i in range(4):
        b.add_chord(2, i * 0.5, 0.4, g5)
        b.add_chord(2, 2 + i * 0.5, 0.4, a5)
    for i in range(8):
        b.add_chord(3, i * 0.5, 0.4, e5)
    for bar in (4, 5):
        for i in range(8):
            b.add_chord(bar, i * 0.5, 0.4, e5, muted)
    for i in range(4):
        b.add_chord(6, i * 0.5, 0.4, g5)
        b.add_chord(6, 2 + i * 0.5, 0.4, a5)
    b.add_chord(7, 0, 2.0, e5)
    return b.build()


def first_light_melody() -> dict:
    b = Builder("builtin.melody.first-light", "单音旋律 · 晨光",
                "自编 16 小节 C 大调旋律，含 hammer-on 与 pull-off。先唱准，再弹准。",
                3, ["旋律", "连奏"], 72, 16)
    # (bar, beat, duration, string, fret, techniques)
    melody = [
        (0, 0, 2, 2, 1, []), (0, 2, 2, 2, 3, []),
        (1, 0, 2, 2, 5, []), (1, 2, 2, 1, 3, []),
        (2, 0, 3, 1, 5, []), (2, 3, 1, 1, 3, []),
        (3, 0, 4, 2, 5, []),

        (4, 0, 2, 2, 3, []), (4, 2, 2, 2, 5, []),
        (5, 0, 2, 1, 3, []), (5, 2, 2, 1, 5, []),
        (6, 0, 3, 1, 8, []), (6, 3, 1, 1, 5, []),
        (7, 0, 4, 1, 3, []),

        (8, 0, 1, 2, 5, []), (8, 1, 1, 1, 3, []), (8, 2, 1, 1, 5, []), (8, 3, 1, 1, 3, []),
        (9, 0, 2, 2, 5, []), (9, 2, 2, 2, 3, []),
        (10, 0, 3, 2, 1, []), (10, 3, 1, 2, 3, []),
        (11, 0, 4, 2, 5, []),

        (12, 0, 2, 1, 3, []), (12, 2, 2, 1, 5, []),
        (13, 0, 2, 1, 5, []), (13, 2, 1, 1, 7, ["hammer_on"]), (13, 3, 1, 1, 8, []),
        (14, 0, 2, 1, 5, []), (14, 2, 2, 1, 3, ["pull_off"]),
        (15, 0, 4, 1, 8, []),
    ]
    for bar, beat, duration, string, fret, techniques in melody:
        b.add(bar, beat, duration, string, fret, techniques)
    return b.build()


PHRASES = [
    ("chromatic-basic.json", chromatic_basic),
    ("chromatic-reverse.json", chromatic_reverse),
    ("chromatic-triplet.json", chromatic_triplet),
    ("scale-c-major.json", c_major_scale),
    ("scale-g-major.json", g_major_scale),
    ("scale-a-minor-pentatonic.json", a_minor_pentatonic),
    ("riff-e5-power.json", e5_power_riff),
    ("melody-first-light.json", first_light_melody),
]


def render(phrase: dict) -> str:
    return json.dumps(phrase, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="只检查产物是否与脚本一致")
    args = parser.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    failures = 0
    for name, factory in PHRASES:
        text = render(factory())
        path = OUT_DIR / name
        if args.check:
            current = path.read_text(encoding="utf-8") if path.exists() else ""
            if current != text:
                print(f"[stale] {name}")
                failures += 1
            else:
                print(f"[ok] {name}")
        else:
            path.write_text(text, encoding="utf-8")
            print(f"[write] {path.relative_to(OUT_DIR.parents[3])}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
