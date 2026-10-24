#!/usr/bin/env python3
"""分析节拍器漂移日志。

用法：
    adb logcat -s MetronomeDrift > drift.txt      # 采集
    python tools/analyze_drift.py drift.txt --sample-rate 48000

日志每行形如：
    10-24 09:30:00.123  1234  1250 D MetronomeDrift: 123456,9876543210000

两列分别是拍点帧号与系统 elapsedRealtimeNanos。帧号是音频时钟，纳秒是系统时钟；
两者差值的变化量就是「节奏相对于系统时间」的漂移。
"""

from __future__ import annotations

import argparse
import re
import statistics

LINE_RE = re.compile(r"MetronomeDrift:\s*(\d+),(\d+)")


def parse(path: str) -> list[tuple[int, int]]:
    samples: list[tuple[int, int]] = []
    with open(path, encoding="utf-8", errors="ignore") as handle:
        for line in handle:
            match = LINE_RE.search(line)
            if match:
                samples.append((int(match.group(1)), int(match.group(2))))
    return samples


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", help="logcat 输出文件")
    parser.add_argument("--sample-rate", type=int, default=48_000)
    args = parser.parse_args()

    samples = parse(args.log)
    if len(samples) < 10:
        raise SystemExit(f"有效拍点太少（{len(samples)} 行），确认 debug 构建且 tag 正确")

    first_frame, first_ns = samples[0]
    drifts_ms: list[float] = []
    intervals: list[float] = []

    for frame, nanos in samples:
        audio_ms = (frame - first_frame) / args.sample_rate * 1000.0
        system_ms = (nanos - first_ns) / 1_000_000.0
        drifts_ms.append(system_ms - audio_ms)

    for (_, prev_ns), (_, next_ns) in zip(samples, samples[1:]):
        intervals.append((next_ns - prev_ns) / 1_000_000.0)

    peak = max(drifts_ms, key=abs)
    span_ms = (samples[-1][1] - samples[0][1]) / 1_000_000.0
    slope_ppm = (drifts_ms[-1] / span_ms * 1e6) if span_ms > 0 else 0.0

    print(f"拍点数        : {len(samples)}")
    print(f"总时长        : {span_ms / 1000:.1f} s")
    print(f"间隔均值      : {statistics.mean(intervals):.2f} ms")
    print(f"间隔标准差    : {statistics.pstdev(intervals):.2f} ms")
    print(f"最大间隔      : {max(intervals):.2f} ms")
    print(f"累计漂移末值  : {drifts_ms[-1]:+.2f} ms")
    print(f"累计漂移峰值  : {peak:+.2f} ms")
    print(f"漂移斜率      : {slope_ppm:+.1f} ppm（{slope_ppm / 1e4:+.3f}%）")
    print()
    print("判定参考：斜率绝对值 < 100 ppm（0.01%）且峰值 < 20 ms 视为听感不可辨。")
    print("若斜率显著非零，说明音频时钟与系统时钟不同源，这属于硬件特性，不是调度漂移；")
    print("真正要盯的是「相邻拍点间隔标准差」与 AudioTrack 欠载次数。")


if __name__ == "__main__":
    main()
