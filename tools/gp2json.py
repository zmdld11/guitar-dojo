#!/usr/bin/env python3
"""把 Guitar Pro 7/8 的 .gp 谱转成 guitar-dojo 的乐句 JSON（schema v1）。

只依赖标准库。转换产物只能写到 local_phrases/（gitignore），
脚本会拒绝写入 app/src/main/assets/，避免版权谱进仓库。

用法：
    python tools/gp2json.py "D:/.../红莲华.gp"
    python tools/gp2json.py song.gp --track 1 --out local_phrases/song.json
    python tools/gp2json.py song.gp --list-tracks
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

PPQ = 960
NOTE_VALUE_QUARTERS = {
    "Long": 16.0, "DoubleWhole": 8.0, "Whole": 4.0, "Half": 2.0,
    "Quarter": 1.0, "Eighth": 0.5, "16th": 0.25, "32nd": 0.125,
    "64th": 0.0625, "128th": 0.03125,
}
DYNAMIC_VELOCITY = {
    "PPP": 0.30, "PP": 0.40, "P": 0.50, "MP": 0.65,
    "MF": 0.80, "F": 0.90, "FF": 0.97, "FFF": 1.0,
}
STANDARD_TUNING = [40, 45, 50, 55, 59, 64]
MAX_FRET = 24
REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = (REPO_ROOT / "app/src/main/assets").resolve()
DEFAULT_OUT_DIR = REPO_ROOT / "local_phrases"


@dataclass
class RawNote:
    tick: int
    duration: int
    midi: int
    string: int | None
    fret: int | None
    velocity: float = 0.8
    techniques: list[str] = field(default_factory=list)
    tie_origin: bool = False
    tie_destination: bool = False


@dataclass
class TrackInfo:
    index: int
    name: str
    tuning: list[int]
    capo: int
    program: int
    note_count: int
    is_drum: bool


class ConversionError(Exception):
    pass


def element_text(node: ET.Element | None, path: str, default: str = "") -> str:
    if node is None:
        return default
    found = node.find(path)
    if found is None or found.text is None:
        return default
    return found.text.strip()


def note_properties(note: ET.Element) -> dict[str, ET.Element]:
    return {
        p.attrib.get("name", ""): p
        for p in note.findall("./Properties/Property")
    }


def note_value_ticks(rhythm: ET.Element, ppq: int) -> int:
    value = rhythm.findtext("NoteValue", "Quarter")
    quarters = NOTE_VALUE_QUARTERS.get(value)
    if quarters is None:
        raise ConversionError(f"不支持的节奏型：{value}")
    dot = rhythm.find("AugmentationDot")
    if dot is not None:
        quarters *= 1.0 + 0.5 * int(dot.attrib.get("count", "1"))
    tuplet = rhythm.find("Tuplet")
    if tuplet is not None:
        numerator = float(tuplet.findtext("Numerator", "3"))
        denominator = float(tuplet.findtext("Denominator", "2"))
        if numerator <= 0 or denominator <= 0:
            raise ConversionError("连音比例非法")
        quarters *= numerator / denominator
    return int(round(quarters * ppq))


def parse_tuning(track: ET.Element) -> list[int]:
    pitches = element_text(
        track,
        "./Staves/Staff/Properties/Property[@name='Tuning']/Pitches",
    )
    if not pitches:
        return list(STANDARD_TUNING)
    values = [int(x) for x in pitches.split()]
    return values or list(STANDARD_TUNING)


def parse_capo(track: ET.Element) -> int:
    value = element_text(track, "./Staves/Staff/Properties/Property[@name='CapoFret']/Fret", "0")
    try:
        return max(0, int(value))
    except ValueError:
        return 0


def load_root(path: Path) -> ET.Element:
    if not zipfile.is_zipfile(path):
        raise ConversionError("只支持 GP7/8 的 .gp（ZIP + score.gpif）；旧版 .gp5 请先用 Guitar Pro 另存为 .gp")
    with zipfile.ZipFile(path) as archive:
        try:
            data = archive.read("Content/score.gpif")
        except KeyError as error:
            raise ConversionError("压缩包里没有 Content/score.gpif，可能不是 GP7/8 文件") from error
    return ET.fromstring(data)


def read_tempo_map(root: ET.Element, bar_count: int) -> list[float]:
    tempo_by_bar = {}
    for automation in root.findall("./MasterTrack/Automations/Automation"):
        if automation.findtext("Type") != "Tempo":
            continue
        bar = int(automation.findtext("Bar", "0"))
        value = (automation.findtext("Value", "") or "").split()
        if value:
            tempo_by_bar[bar] = float(value[0])
    tempos = []
    current = tempo_by_bar.get(0, 120.0)
    for index in range(bar_count):
        current = tempo_by_bar.get(index, current)
        tempos.append(current)
    return tempos


def expand_track(root: ET.Element, track_index: int, ppq: int) -> tuple[list[RawNote], list[dict]]:
    """把某条轨道的 Beat/Note 展开成绝对 tick 的音符。"""
    rhythms = {r.attrib["id"]: r for r in root.find("Rhythms")}
    beats = {b.attrib["id"]: b for b in root.find("Beats")}
    voices = {v.attrib["id"]: v for v in root.find("Voices")}
    bars = {b.attrib["id"]: b for b in root.find("Bars")}
    notes = {n.attrib["id"]: n for n in root.iter("Note")}
    master_bars = root.find("MasterBars")
    if master_bars is None:
        raise ConversionError("找不到 MasterBars")
    master_bar_nodes = master_bars.findall("MasterBar")
    tempos = read_tempo_map(root, len(master_bar_nodes))

    expanded: list[RawNote] = []
    bar_infos: list[dict] = []
    cursor = 0

    for bar_index, master_bar in enumerate(master_bar_nodes):
        signature = master_bar.findtext("Time", "4/4")
        numerator, denominator = (int(x) for x in signature.split("/"))
        length = int(round(numerator * 4.0 / denominator * ppq))
        bar_ids = (master_bar.findtext("Bars", "") or "").split()
        if track_index < len(bar_ids):
            bar = bars.get(bar_ids[track_index])
            if bar is not None:
                for voice_id in (bar.findtext("Voices", "") or "").split():
                    if voice_id == "-1":
                        continue
                    voice = voices.get(voice_id)
                    if voice is None:
                        continue
                    beat_cursor = cursor
                    for beat_id in (voice.findtext("Beats", "") or "").split():
                        beat = beats.get(beat_id)
                        if beat is None:
                            continue
                        rhythm_ref = beat.find("Rhythm")
                        if rhythm_ref is None:
                            continue
                        duration = note_value_ticks(rhythms[rhythm_ref.attrib["ref"]], ppq)
                        velocity = DYNAMIC_VELOCITY.get(beat.findtext("Dynamic", "MF"), 0.8)
                        for note_id in (beat.findtext("Notes", "") or "").split():
                            note = notes.get(note_id)
                            if note is None:
                                continue
                            parsed = parse_note(note, beat_cursor, duration, velocity)
                            if parsed is not None:
                                expanded.append(parsed)
                        beat_cursor += duration
        bar_infos.append({
            "index": bar_index,
            "startTick": cursor,
            "lengthTicks": length,
            "timeSignature": [numerator, denominator],
            "tempoBpm": tempos[bar_index],
        })
        cursor += length

    expanded.sort(key=lambda n: (n.tick, n.string or 0, n.midi))
    merged = merge_ties(expanded)
    return merged, bar_infos


def parse_note(note: ET.Element, tick: int, duration: int, velocity: float) -> RawNote | None:
    props = note_properties(note)
    midi_node = props.get("Midi")
    midi_text = midi_node.findtext("Number") if midi_node is not None else None
    string_node = props.get("String")
    fret_node = props.get("Fret")
    string = int(string_node.findtext("String")) if string_node is not None else None
    fret = int(fret_node.findtext("Fret")) if fret_node is not None else None
    if midi_text is None:
        return None
    tie = note.find("Tie")
    return RawNote(
        tick=tick,
        duration=max(1, duration),
        midi=int(midi_text),
        string=string,
        fret=fret,
        velocity=velocity,
        techniques=extract_techniques(note, props),
        tie_origin=tie is not None and tie.attrib.get("origin") == "true",
        tie_destination=tie is not None and tie.attrib.get("destination") == "true",
    )


def extract_techniques(note: ET.Element, props: dict[str, ET.Element]) -> list[str]:
    found: list[str] = []
    if note.find("Vibrato") is not None:
        found.append("vibrato")
    if note.find("Accent") is not None:
        found.append("accent")
    if "PalmMuted" in props:
        found.append("palm_mute")
    if "Slide" in props:
        found.append("slide")
    if "Bended" in props:
        found.append("bend")
    if "HarmonicType" in props:
        found.append("harmonic")
    return found


def merge_ties(notes: list[RawNote]) -> list[RawNote]:
    """把 tie 链合并成一个长音；只保留链头。"""
    merged: list[RawNote] = []
    open_notes: dict[tuple[int, int], RawNote] = {}
    for note in notes:
        key = (note.string or -1, note.midi)
        head = open_notes.get(key)
        if (
            note.tie_destination
            and head is not None
            and head.tie_origin
            and head.tick + head.duration == note.tick
        ):
            head.duration += note.duration
            head.tie_origin = note.tie_origin
            if not note.tie_origin:
                open_notes.pop(key, None)
            continue
        merged.append(note)
        if note.tie_origin:
            open_notes[key] = note
        else:
            open_notes.pop(key, None)
    return merged


def configure_fingering(
    notes: list[RawNote],
    tuning: list[int],
    capo: int,
    force: bool,
) -> tuple[list[RawNote], bool, int]:
    """检查弦品与音高是否一致；必要时整轨重排。"""
    strings = len(tuning)
    consistent = 0
    total = 0
    for note in notes:
        total += 1
        if note.string and note.fret is not None:
            index = strings - note.string
            if 0 <= index < strings and tuning[index] + capo + note.fret == note.midi:
                consistent += 1
    ratio = consistent / total if total else 0.0
    rewrite = force or ratio < 0.98
    if not rewrite:
        for note in notes:
            if note.string and note.fret is not None:
                index = strings - note.string
                if 0 <= index < strings and tuning[index] + capo + note.fret == note.midi:
                    continue
            candidates = candidates_for(note.midi, tuning, capo)
            if candidates:
                note.string, note.fret = candidates[0]
        return notes, False, total - consistent

    assign_fingering(notes, tuning, capo)
    return notes, True, total - consistent


def candidates_for(midi: int, tuning: list[int], capo: int) -> list[tuple[int, int]]:
    strings = len(tuning)
    result = []
    for string in range(1, strings + 1):
        index = strings - string
        fret = midi - tuning[index] - capo
        if 0 <= fret <= MAX_FRET:
            result.append((string, fret))
    return result


def assign_fingering(notes: list[RawNote], tuning: list[int], capo: int) -> None:
    """按列做局部最优排指：同列同弦互斥、非空弦跨度 <=4 品。"""
    by_tick: dict[int, list[RawNote]] = {}
    for note in notes:
        by_tick.setdefault(note.tick, []).append(note)

    prev_center = 5.0
    prev_strings: dict[int, int] = {}
    for tick in sorted(by_tick):
        column = [n for n in by_tick[tick] if candidates_for(n.midi, tuning, capo)][:6]
        for note in by_tick[tick]:
            if note not in column:
                # 音域外或第七个音：标记为不可弹，交给校验报错
                note.string, note.fret = 1, 0
        if not column:
            continue

        candidate_lists = []
        for note in column:
            candidates = candidates_for(note.midi, tuning, capo)
            candidates.sort(key=lambda sf: (abs(sf[1] - prev_center), sf[1] > 0, sf[1]))
            candidate_lists.append(candidates[:4])

        best_cost = math.inf
        best_combo: list[tuple[int, int]] | None = None
        for combo in product_limited(candidate_lists):
            if len({s for s, _ in combo}) != len(combo):
                continue
            fretted = [f for _, f in combo if f > 0]
            if fretted and max(fretted) - min(fretted) > 4:
                continue
            center = sum(fretted) / len(fretted) if fretted else 0.0
            cost = abs(center - prev_center) + 0.1 * center
            cost -= 0.15 * sum(1 for f in fretted if f == 0)
            cost -= 0.2 * sum(
                1 for note, (s, _) in zip(column, combo) if prev_strings.get(note.midi) == s
            )
            if cost < best_cost:
                best_cost = cost
                best_combo = list(combo)

        if best_combo is None:
            # 退化成逐音最近，至少保证音高与弦品自洽
            best_combo = [candidates[0] for candidates in candidate_lists]
        for note, (string, fret) in zip(column, best_combo):
            note.string, note.fret = string, fret
        fretted = [f for _, f in best_combo if f > 0]
        if fretted:
            prev_center = sum(fretted) / len(fretted)
        prev_strings = {note.midi: string for note, (string, _) in zip(column, best_combo)}


def product_limited(lists: list[list[tuple[int, int]]], limit: int = 4096):
    """小型笛卡尔积，避免 itertools.product 在极端列上爆炸。"""
    combos: list[tuple[tuple[int, int], ...]] = [()]
    for candidates in lists:
        next_combos = []
        for combo in combos:
            for candidate in candidates:
                next_combos.append(combo + (candidate,))
                if len(next_combos) >= limit:
                    break
            if len(next_combos) >= limit:
                break
        combos = next_combos
    return combos


def build_track_infos(root: ET.Element) -> list[TrackInfo]:
    info = []
    for index, track in enumerate(root.findall("./Tracks/Track")):
        instrument_type = track.findtext("./InstrumentSet/Type", "")
        info.append(TrackInfo(
            index=index,
            name=track.findtext("Name", f"Track {index}"),
            tuning=parse_tuning(track),
            capo=parse_capo(track),
            program=int(track.findtext("./Sounds/Sound/MIDI/Program", "25") or 25),
            note_count=0,
            is_drum=instrument_type.lower() in ("drumkit", "drums"),
        ))
    return info


def choose_track(infos: list[TrackInfo], root: ET.Element) -> int:
    candidates = [
        info for info in infos
        if not info.is_drum and len(info.tuning) >= 4
    ]
    # 优先吉他轨；Songsterr 导出里常把主唱/长笛放在 0 号轨，不能直接取第一条
    guitar_like = [
        info for info in candidates
        if "guitar" in info.name.lower() or "吉他" in info.name
    ]
    for info in guitar_like + candidates:
        notes, _ = expand_track(root, info.index, PPQ)
        if notes:
            return info.index
    raise ConversionError("找不到可用的弦乐器轨道")


def slugify(text: str) -> str:
    ascii_part = re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()
    if ascii_part:
        return ascii_part[:40]
    return "gp-" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:8]


def convert(gp_path: Path, track_index: int | None, ppq: int = PPQ) -> tuple[dict, dict]:
    root = load_root(gp_path)
    infos = build_track_infos(root)
    if not infos:
        raise ConversionError("谱子里没有轨道")

    if track_index is None:
        track_index = choose_track(infos, root)
    if not (0 <= track_index < len(infos)):
        raise ConversionError(f"轨道序号 {track_index} 超出范围 0..{len(infos) - 1}")
    info = infos[track_index]

    raw_notes, bars = expand_track(root, track_index, ppq)
    if not raw_notes:
        raise ConversionError(f"轨道 {track_index}（{info.name}）没有音符")

    rewritten = False
    mismatched = 0
    if info.is_drum:
        raise ConversionError(f"轨道 {track_index} 是鼓轨，不能作为乐句")
    raw_notes, rewritten, mismatched = configure_fingering(
        raw_notes, info.tuning, info.capo, force=False
    )

    # 去掉无法排指/同弦冲突的音，保证输出能通过 schema 校验
    total_ticks = bars[-1]["startTick"] + bars[-1]["lengthTicks"]
    seen: set[tuple[int, int]] = set()
    notes = []
    dropped = 0
    for note in raw_notes:
        if note.string is None or note.fret is None:
            dropped += 1
            continue
        index = len(info.tuning) - note.string
        if not (0 <= index < len(info.tuning)):
            dropped += 1
            continue
        if info.tuning[index] + info.capo + note.fret != note.midi:
            dropped += 1
            continue
        if note.tick < 0 or note.tick + note.duration > total_ticks:
            dropped += 1
            continue
        key = (note.string, note.tick)
        if key in seen:
            dropped += 1
            continue
        seen.add(key)
        notes.append({
            "bar": bar_index_of(bars, note.tick),
            "tick": note.tick,
            "durationTicks": note.duration,
            "midi": note.midi,
            "string": note.string,
            "fret": note.fret,
            "velocity": round(note.velocity, 2),
            "techniques": note.techniques,
        })
    notes.sort(key=lambda n: (n["tick"], n["string"]))

    title = gp_path.stem
    phrase = {
        "schemaVersion": 1,
        "id": f"local.{slugify(title)}",
        "title": title,
        "description": f"本地转换：{gp_path.name} · 轨道「{info.name}」",
        "difficulty": 3,
        "tags": ["本地", "GP 转换"],
        "origin": "local",
        "tuning": info.tuning,
        "capo": info.capo,
        "ppq": ppq,
        "bars": bars,
        "notes": notes,
        "trackName": info.name,
        "source": {
            "generator": "gp2json.py 1.0",
            "file": gp_path.name,
            "trackIndex": track_index,
            "fingeringRewritten": rewritten,
            "exportedAt": date.today().isoformat(),
        },
    }

    report = {
        "track": info.name,
        "trackIndex": track_index,
        "tracksTotal": len(infos),
        "notes": len(notes),
        "bars": len(bars),
        "durationTicks": total_ticks,
        "fingeringRewritten": rewritten,
        "fingeringMismatched": mismatched,
        "dropped": dropped,
        "capo": info.capo,
        "tuning": info.tuning,
    }
    return phrase, report


def bar_index_of(bars: list[dict], tick: int) -> int:
    for bar in bars:
        if bar["startTick"] <= tick < bar["startTick"] + bar["lengthTicks"]:
            return bar["index"]
    return bars[-1]["index"]


def safe_output_path(out: Path) -> Path:
    resolved = out.resolve()
    if resolved == ASSETS_DIR or ASSETS_DIR in resolved.parents:
        raise ConversionError("拒绝写入 app/src/main/assets：版权谱的转换产物只能留在 local_phrases/")
    if resolved.suffix.lower() != ".json":
        resolved = resolved.with_suffix(".json")
    return resolved


def main() -> int:
    parser = argparse.ArgumentParser(description="GP7/8 谱转 guitar-dojo 乐句 JSON")
    parser.add_argument("input", type=Path, help=".gp 文件路径")
    parser.add_argument("--track", type=int, default=None, help="轨道序号（从 0 开始）")
    parser.add_argument("--out", type=Path, default=None, help="输出路径，默认 local_phrases/<文件名>.json")
    parser.add_argument("--ppq", type=int, default=PPQ, help="每四分音符 tick 数，默认 960")
    parser.add_argument("--list-tracks", action="store_true", help="只列出轨道，不转换")
    args = parser.parse_args()

    try:
        if args.list_tracks:
            root = load_root(args.input)
            for info in build_track_infos(root):
                notes, _ = expand_track(root, info.index, args.ppq)
                print(f"track {info.index}: {info.name} | tuning={info.tuning} | capo={info.capo} "
                      f"| notes={len(notes)} | drum={info.is_drum}")
            return 0

        phrase, report = convert(args.input, args.track, args.ppq)
        out = safe_output_path(args.out or DEFAULT_OUT_DIR / f"{args.input.stem}.json")
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(phrase, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        print(f"[ok] {args.input.name} -> {out}")
        print(f"     轨道 {report['trackIndex']}「{report['track']}」 "
              f"音符 {report['notes']} / 小节 {report['bars']}")
        print(f"     排指重写={report['fingeringRewritten']} "
              f"原有标注不一致={report['fingeringMismatched']} 丢弃={report['dropped']}")
        print(f"     tuning={report['tuning']} capo={report['capo']}")
        return 0 if report["notes"] else 1
    except (ConversionError, OSError, ET.ParseError) as error:
        print(f"[error] {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
